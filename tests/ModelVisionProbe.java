import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Actual model depth -> native bridge -> installed PZO memory shader. Controlled mask, no map. */
public final class ModelVisionProbe {
    static final int W=128;
    static final IsometricEffects NO_SUN=new IsometricEffects(false,0,0,64,512,false,0,1,1,1);
    public static void main(String[] args)throws Exception {
        boolean baseline=args.length>0;
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"model/native memory depth",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));
            Method weave=NativeShadowReliefProbe.class.getDeclaredMethod("wovenRelief");weave.setAccessible(true);
            Method relief=(Method)weave.invoke(null);
            @SuppressWarnings("unchecked") var capture=(ThreadLocal<Boolean>)GameAccess.field("vpiso.IsometricBridge","CAPTURING",null);
            int output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
            int color=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,null),depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("output");
            Method frag=Class.forName("pzopt.Darkness").getDeclaredMethod("frag",int.class);frag.setAccessible(true);
            String actual=(String)frag.invoke(null,0);
            if(!actual.contains("gl_FragDepth = textureLod(depth, screenUV") || !actual.contains("* pzMemTint"))throw new AssertionError("PZO shader identity");
            GlProgram memory=GlProgram.fromSources("actual installed PZO memory",new GlProgram.Sources(NativeVision.VERTEX,null,null,null,actual),Map.of(),Map.of(),List.of());
            int mask=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,filled(1,1,1,1));
            int world=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,filled(.7f,.55f,.4f,1));
            int nativeMaskDepth=texture(GL30.GL_DEPTH_COMPONENT16,GL11.GL_DEPTH_COMPONENT,null);
            int vao=GL30.glGenVertexArrays();
            Frame frame=NativeSurfaceReceiverProbe.frame();frame.camX=128;frame.camY=128;
            float[][] points=ModelShadowMotionProbe.tiledFloor();float[] verts=new float[points.length*14];
            for(int i=0;i<points.length;i++){System.arraycopy(points[i],0,verts,i*14,3);verts[i*14+7]=verts[i*14+8]=1;verts[i*14+10]=1;}
            var pages=new zombie.core.textures.TextureID[1];
            Object floor=new ChunkMeshData(pages,new int[]{0},new int[]{points.length},verts,0);
            frame.scene.meshes[0]=(ChunkMeshData)floor;frame.scene.meshCount=1;
            frame.scene.meshFlags[0]=1;frame.scene.meshPlan[0]=-1;frame.scene.meshModelSquares[0]=-1L;
            frame.scene.meshFade[0]=0;frame.scene.meshFade[1]=2;
            frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
            float[] h=new float[96*96];for(int y=0;y<96;y++)for(int x=0;x<96;x++)h[y*96+x]=12+(x-32)*.16f+(y-32)*.08f;
            FarRelief.Field grid=FarRelief.Field.of(96,96,h,h);
            Matrix4f view=new Matrix4f();float[] eye=new float[3];
            long wrong=0,legitimate=0,checked=0;
            for(int step=0;step<24;step++) {
                frame.camX=128+step*.15f;frame.camY=128-step*.1f;
                frame.scene.originX=-frame.camX;frame.scene.originZ=-frame.camY;
                frame.scene.meshOffsets[0]=frame.camX-128;frame.scene.meshOffsets[2]=frame.camY-128;
                IsoCameraMath.matrices(new IsoCameraMath.Fit(128,128,12),frame.camX,frame.camY,W,W,frame.scene.projection,view,eye,0);
                frame.scene.relief(null);
                render(frame,view,eye,output);
                FloatBuffer flat=readDepth();
                // A real foreground/background ordering discriminator: same native mask, one side in
                // front of the native reference surface, the other behind it. Preserve 16-bit native mask precision.
                FloatBuffer candidates=BufferUtils.createFloatBuffer(W*W);
                for(int y=0;y<W;y++)for(int x=0;x<W;x++)candidates.put(flat.get(y*W+x)+(x<W/2?-.002f:.002f));
                candidates.flip();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,nativeMaskDepth);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,candidates);
                frame.scene.relief(new FarRelief.Lift(grid,.5f,grid.metresAt(frame.camX,frame.camY),frame.camX,frame.camY));
                capture.set(true);try{relief.invoke(null,frame);}finally{capture.remove();}
                if(!baseline && (frame.scene.relief!=null || GameAccess.field("viewpoint.render.ModelDraws","relief",frame.scene.models)!=null))throw new AssertionError("model height basis");
                render(frame,view,eye,output);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,W);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glColorMask(true,true,true,true);GL11.glClearColor(.7f,.55f,.4f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(false);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
                for(int i=0;i<3;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,new int[]{mask,nativeMaskDepth,world}[i]);GL33.glBindSampler(i,0);}
                memory.use();memory.setInt("tex",0);memory.setInt("depth",1);memory.setInt("world",2);
                memory.set("screenSize",W,W);memory.set("displayOrigin",0f,0f);memory.set("displaySize",W,W);
                memory.set("texSize",W,W);memory.set("TextureSize",W,W);
                memory.set("pzMem",.4f,.9f,.78f,0);memory.set("pzMemTint",.9f,.97f,1.15f);
                GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                for(int y=32;y<96;y++)for(int x=32;x<96;x++) {
                    boolean tint=pixels.get((y*W+x)*4)<.68f;
                    if(x>=W/2){checked++;if(tint)wrong++;}else if(tint)legitimate++;
                }
                GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);
            }
            NativeSurfaceReceiverProbe.retire(floor);
            if(baseline) {
                if(wrong==0)throw new AssertionError("Pinned037 did not reproduce false native tint");
                System.out.println("Model vision negative control PASS: pinned037 solar OFF false blue/gray pixels="+wrong+"/"+checked+"; actual model gbuffer, NativeVision depth and installed PZO memory shader.");
            }else {
                if(wrong!=0 || legitimate!=checked)throw new AssertionError("Native memory order wrong="+wrong+" legitimate="+legitimate+" expected="+checked);
                System.out.println("Model vision GPU PASS: 24 moving solar-OFF frames; false blue/gray pixels=0/"+checked+"; legitimate memory tint="+legitimate+"; actual model gbuffer, production Relief/NativeVision and installed PZO shader, 16-bit mask+LEQUAL. Controlled depth-order mask; no user map acceptance.");
            }
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("GL error");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void render(Frame frame,Matrix4f view,float[] eye,int output) {
        frame.number++;frame.scene.cameraCut=true;
        IsometricBridge.modelWorldRendering(frame,NO_SUN,true);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,W);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        if(!WorldRenderer.begin(frame.scene,frame.number,1,view,eye[0],eye[1],eye[2]))throw new AssertionError("begin");
        WorldRenderer.drawTranslucent(frame.scene);WorldRenderer.finish(frame.scene);
        NativeVision vision=new NativeVision();vision.capture(frame);vision.writeDepth();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }
    static FloatBuffer readDepth(){FloatBuffer d=BufferUtils.createFloatBuffer(W*W);GL11.glReadPixels(0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,d);return d;}
    static FloatBuffer filled(float r,float g,float b,float a){FloatBuffer d=BufferUtils.createFloatBuffer(W*W*4);for(int i=0;i<W*W;i++)d.put(r).put(g).put(b).put(a);return d.flip();}
    static int texture(int internal,int format,FloatBuffer data){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,W,W,0,format,GL11.GL_FLOAT,data);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return t;}
}
