import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.Random;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import vpiso.NativeVision;

/** Checks the production depth shader against the game's real native depth helper. No map is loaded. */
public final class NativeDepthProbe {
    private static final int FW=96,FH=72,X=11,Y=13,W=64,H=40,SW=4,SH=3;
    private static final float[] SOURCE={.12f,.87f,1f,.39f,.63f,.22f,.98f,.48f,1f,.74f,.31f,.56f};
    // Column-major, deliberately including perspective division and cross-axis terms.
    private static final float[] INVERSE={16,1,2,.09f,2,7,1,-.05f,1,.6f,10,.04f,-3,1.5f,4,1.2f};
    private static Method nativeSample,bridgeSample;
    private static Field depthStart;

    public static void main(String[] args) throws Exception {
        Class<?> helper=Class.forName("zombie.iso.IsoDepthHelper");
        nativeSample=helper.getMethod("getSquareDepthData",int.class,int.class,float.class,float.class,float.class);
        depthStart=Class.forName("zombie.iso.IsoDepthHelper$Results").getField("depthStart");
        bridgeSample=NativeVision.class.getDeclaredMethod("sample",int.class,int.class,float.class,float.class,float.class);
        bridgeSample.setAccessible(true);
        affine();
        gpu();
        System.out.println("Native depth regression PASS: actual native mapping, production GLSL, sky, color preservation, offset viewport and GL_LEQUAL; no map test.");
    }

    private static float nativeDepth(int sx,int sy,float x,float y,float z) throws Exception {
        // Results is a reused ThreadLocal value; read it before another native invocation.
        return depthStart.getFloat(nativeSample.invoke(null,sx,sy,x,y,z));
    }

    private static float[] coefficients(float x,float y,float z) throws Exception {
        int sx=(int)Math.floor(x),sy=(int)Math.floor(y);
        float base=nativeDepth(sx,sy,x,y,z);
        float[] value={base,nativeDepth(sx,sy,x+1,y,z)-base,nativeDepth(sx,sy,x,y+1,z)-base,
                nativeDepth(sx,sy,x,y,z+1)-base};
        float wrapped=(Float)bridgeSample.invoke(null,sx,sy,x,y,z);
        if (Float.floatToIntBits(wrapped)!=Float.floatToIntBits(base))
            throw new AssertionError("Native helper bridge changed depth");
        if (!(value[1]<0 && value[2]<0 && value[3]<0))
            throw new AssertionError("Native near/far direction changed");
        return value;
    }

    private static void affine() throws Exception {
        Random random=new Random(0x565049534fL);
        float[] edges={-32768.125f,-8000.125f,-16.001f,-16,-8.001f,-8,-.001f,0,.001f,7.999f,8,8.001f,8192.375f,65536.125f};
        double worstLocal=0,worstFar=0;
        int cases=0;
        for (int i=0;i<300;i++) {
            float x=i<edges.length?edges[i]:random.nextFloat()*131072-65536;
            float y=i<edges.length?edges[edges.length-1-i]:random.nextFloat()*131072-65536;
            // Probe both sides of every integer level, including every level-group boundary.
            float z=(i%65)-32+(i%3==0?-.001f:i%3==1?0:.001f);
            float[] c=coefficients(x,y,z);
            for (int j=0;j<48;j++) {
                boolean far=j>=32;
                float dx=(random.nextFloat()*2-1)*(far?8192:160);
                float dy=(random.nextFloat()*2-1)*(far?8192:160);
                float dz=(random.nextFloat()*2-1)*40;
                float tx=x+dx,ty=y+dy,tz=z+dz;
                // Account for the actual float world-coordinate addition at distant locations.
                float predicted=c[0]+c[1]*(tx-x)+c[2]*(ty-y)+c[3]*(tz-z);
                float expected=nativeDepth((int)Math.floor(x),(int)Math.floor(y),tx,ty,tz);
                double error=Math.abs(predicted-expected);
                if (far) worstFar=Math.max(worstFar,error); else worstLocal=Math.max(worstLocal,error);
                if (error>(far?.001:3e-5))
                    throw new AssertionError("Native affine mismatch "+error+" camera="+x+","+y+","+z+" delta="+dx+","+dy+","+dz);
                cases++;
            }
        }
        System.out.printf(Locale.ROOT,"Native affine: %d samples, negative/chunk/level boundaries and distant XY; worst local %.9f, far %.9f.%n",cases,worstLocal,worstFar);
    }

    private static void gpu() throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(FW,FH,"VPISO native depth fixture",0,0);
        if (window==0) { GLFW.glfwTerminate(); throw new AssertionError("Hidden GL window unavailable"); }
        int program=0,consumer=0,fbo=0,color=0,outputDepth=0,source=0,vao=0;
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            System.out.println("Native depth GPU: "+GL11.glGetString(GL11.GL_RENDERER));
            program=program(NativeVision.VERTEX,NativeVision.FRAGMENT);
            consumer=program(NativeVision.VERTEX,"""
                #version 330
                uniform float candidateDepth;
                out vec4 result;
                void main() { gl_FragDepth=candidateDepth; result=vec4(1.0,0.0,0.0,1.0); }
                """);
            vao=GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            fbo=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            color=texture(GL11.GL_RGBA8,FW,FH,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            outputDepth=texture(GL30.GL_DEPTH_COMPONENT32F,FW,FH,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,outputDepth,0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new AssertionError("Native depth framebuffer incomplete");
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0,0,FW,FH);
            GL11.glClearColor(.2f,.4f,.6f,.8f); GL11.glClearDepth(.713);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            ByteBuffer before=colors();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            source=texture(GL30.GL_DEPTH_COMPONENT32F,SW,SH,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,
                    BufferUtils.createFloatBuffer(SOURCE.length).put(SOURCE).flip());
            float camX=-12004.25f,camY=257.75f,camZ=-1.25f;
            float[] c=coefficients(camX,camY,camZ);
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"sourceDepth"),0);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"inverseCamera"),false,
                    BufferUtils.createFloatBuffer(16).put(INVERSE).flip());
            GL20.glUniform4f(GL20.glGetUniformLocation(program,"nativeDepth"),c[0],c[1],c[2],c[3]);
            GL11.glViewport(X,Y,W,H);
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_ALWAYS);
            GL11.glDepthMask(true); GL11.glColorMask(false,false,false,false);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            // Erase the synthetic draw: the readback below must come from the actual addon entry.
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            bridgeDepth(camX,camY,camZ,source,fbo);
            FloatBuffer depths=BufferUtils.createFloatBuffer(FW*FH);
            GL11.glReadPixels(0,0,FW,FH,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
            ByteBuffer after=colors();
            for (int i=0;i<FW*FH*4;i++) if (before.get(i)!=after.get(i))
                throw new AssertionError("Depth pass changed color byte "+i);
            double worst=0;
            int sky=0,geometry=0,exterior=0;
            for (int y=0;y<FH;y++) for (int x=0;x<FW;x++) {
                float expected;
                if (x<X || x>=X+W || y<Y || y>=Y+H) { expected=.713f; exterior++; }
                else {
                    double u=(x-X+.5)/W,v=(y-Y+.5)/H;
                    float d=SOURCE[(int)(v*SH)*SW+(int)(u*SW)];
                    if (d==1) { expected=1; sky++; }
                    else {
                        float[] world=world(camX,camY,camZ,u,v,d);
                        expected=Math.max(0,Math.min(1,nativeDepth((int)Math.floor(camX),(int)Math.floor(camY),world[0],world[1],world[2])));
                        geometry++;
                    }
                }
                double error=Math.abs(depths.get(y*FW+x)-expected);
                worst=Math.max(worst,error);
                if (error>5e-6) throw new AssertionError("GPU native depth mismatch "+x+","+y+": "+depths.get(y*FW+x)+" expected "+expected);
            }
            if (sky==0 || geometry==0 || exterior==0) throw new AssertionError("Incomplete GPU coverage");
            // Native rendering uses LEQUAL. A native point one tile nearer must pass; one tile farther must fail.
            int px=X+W/8,py=Y+H/8;
            double u=(px-X+.5)/W,v=(py-Y+.5)/H;
            float[] world=world(camX,camY,camZ,u,v,SOURCE[0]);
            int sx=(int)Math.floor(camX),sy=(int)Math.floor(camY);
            float near=nativeDepth(sx,sy,world[0]+1,world[1],world[2]);
            float far=nativeDepth(sx,sy,world[0]-1,world[1],world[2]);
            float stored=depths.get(py*FW+px);
            if (!(near<stored && far>stored)) throw new AssertionError("Native depth order reversed");
            GL20.glUseProgram(consumer); GL11.glColorMask(true,true,true,true);
            GL11.glDepthMask(false); GL11.glDepthFunc(GL11.GL_LEQUAL);
            if (GL11.glGetInteger(GL11.GL_DEPTH_FUNC)!=GL11.GL_LEQUAL) throw new AssertionError("LEQUAL state unavailable");
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(px,py,1,1);
            candidate(consumer,px,py,near,true,"nearer native tile");
            candidate(consumer,px,py,far,false,"farther native tile");
            candidate(consumer,px,py,stored,true,"equal native depth");
            if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("Native depth GL error");
            System.out.printf(Locale.ROOT,"Native depth GPU: actual capture/writeDepth entry, %d geometry, %d sky and %d exterior pixels; worst error %.9f, all RGBA bytes preserved; native near/equal pass and far reject.%n",geometry,sky,exterior,worst);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDepthMask(true);
            GL11.glClearDepth(.713); GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            bridgeDepth(camX,camY,camZ,source,fbo,true);
            GL11.glReadPixels(0,0,FW,FH,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
            for(int y=Y;y<Y+H;y++) for(int x=X;x<X+W;x++) {
                float expected=SOURCE[(int)((y-Y+.5)/H*SH)*SW+(int)((x-X+.5)/W*SW)];
                if(Math.abs(depths.get(y*FW+x)-expected)>1e-6)
                    throw new AssertionError("Rotated native visibility depth was remapped");
            }
            System.out.println("Rotated visibility depth GPU PASS: production NativeVision.writeDepth copies actual VP depth, including sky, without fixed-angle remapping.");
        } finally {
            GL20.glUseProgram(0);
            if (program!=0) GL20.glDeleteProgram(program);
            if (consumer!=0) GL20.glDeleteProgram(consumer);
            if (fbo!=0) GL30.glDeleteFramebuffers(fbo);
            if (color!=0) GL11.glDeleteTextures(color);
            if (outputDepth!=0) GL11.glDeleteTextures(outputDepth);
            if (source!=0) GL11.glDeleteTextures(source);
            if (vao!=0) GL30.glDeleteVertexArrays(vao);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    private static void bridgeDepth(float x,float y,float z,int source,int output) throws Exception {
        bridgeDepth(x,y,z,source,output,false);
    }

    private static void bridgeDepth(float x,float y,float z,int source,int output,boolean rotated) throws Exception {
        viewpoint.core.Frame frame=new viewpoint.core.Frame(); frame.camX=x; frame.camY=y; frame.camZ=z;
        NativeVision bridge=new NativeVision(); bridge.capture(frame);
        if(rotated) bridge.rotation=new vpiso.RotationCamera.Snapshot(new org.joml.Matrix4f(),
                new org.joml.Matrix4f(),x,y,z,W,H,1,1);
        Class<?> renderer=Class.forName("viewpoint.render.WorldRenderer");
        Field frameField=field(renderer,"frame"),targetsField=field(renderer,"targets");
        Object context=frameField.get(null),oldTargets=targetsField.get(null);
        Object targets=Class.forName("viewpoint.render.Targets").getConstructor().newInstance();
        field(targets.getClass(),"depth").setInt(targets,source);
        Field outputField=field(context.getClass(),"outputDrawFbo"); int oldOutput=outputField.getInt(context);
        int[] viewport=(int[])field(context.getClass(),"viewport").get(context),oldViewport=viewport.clone();
        Object matrix=field(context.getClass(),"invViewProjection").get(context);
        Method getMatrix=matrix.getClass().getMethod("get",float[].class),setMatrix=matrix.getClass().getMethod("set",float[].class);
        float[] oldMatrix=new float[16]; getMatrix.invoke(matrix,(Object)oldMatrix);
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            targetsField.set(null,targets); outputField.setInt(context,output);
            viewport[0]=X; viewport[1]=Y; viewport[2]=W; viewport[3]=H;
            setMatrix.invoke(matrix,(Object)INVERSE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+7);
            bridge.writeDepth();
            if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE0+7)
                throw new AssertionError("Actual depth bridge leaked active texture");
        } finally {
            targetsField.set(null,oldTargets); outputField.setInt(context,oldOutput);
            System.arraycopy(oldViewport,0,viewport,0,4); setMatrix.invoke(matrix,(Object)oldMatrix);
            GL13.glActiveTexture(oldActive);
        }
    }

    private static Field field(Class<?> owner,String name) throws Exception {
        Field field=owner.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static float[] world(float x,float y,float z,double u,double v,float d) {
        double[] clip={u*2-1,v*2-1,d*2-1,1},p=new double[4];
        for (int row=0;row<4;row++) for (int col=0;col<4;col++) p[row]+=INVERSE[col*4+row]*clip[col];
        return new float[]{(float)(x-p[0]/p[3]),(float)(y-p[2]/p[3]),(float)(z+p[1]/p[3]/2.4494896)};
    }

    private static void candidate(int program,int x,int y,float depth,boolean pass,String label) {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"candidateDepth"),depth);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        ByteBuffer pixel=BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x,y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
        if ((pixel.get(0)&255)!=(pass?255:51)) throw new AssertionError("GL_LEQUAL failed: "+label);
    }

    private static ByteBuffer colors() {
        ByteBuffer pixels=BufferUtils.createByteBuffer(FW*FH*4);
        GL11.glReadPixels(0,0,FW,FH,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        return pixels;
    }

    private static int texture(int internal,int width,int height,int format,int type,FloatBuffer data) {
        int texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,width,height,0,format,type,data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int program(String vertex,String fragment) {
        int vs=shader(GL20.GL_VERTEX_SHADER,vertex),fs=shader(GL20.GL_FRAGMENT_SHADER,fragment);
        int program=GL20.glCreateProgram();
        GL20.glAttachShader(program,vs); GL20.glAttachShader(program,fs); GL20.glLinkProgram(program);
        GL20.glDeleteShader(vs); GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)
            throw new AssertionError("Native depth link: "+GL20.glGetProgramInfoLog(program));
        return program;
    }

    private static int shader(int type,String text) {
        int shader=GL20.glCreateShader(type); GL20.glShaderSource(shader,text); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)
            throw new AssertionError("Native depth shader: "+GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}
