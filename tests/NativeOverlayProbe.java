import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Actual production color/depth copy and compositor on a native-like depth/stencil FBO. */
public final class NativeOverlayProbe {
    private static final int W=48,H=40,X=5,Y=7,VW=32,VH=24;
    private static int fbo, color, depth, map;
    public static void main(String[] args) {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(96,96,"VPISO native image compositor",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            setup();
            checkReceiver();
            NativeReceiver receiver=new NativeReceiver();
            receiver.fit(new double[][]{{.5,.5,.5},{.6,.5,.5},{.5,.6,.5},{.5,.5,.6}});
            NativeOverlay overlay=new NativeOverlay();
            Matrix4f constant=new Matrix4f().set(new float[]{0,0,0,0,0,0,0,0,0,0,0,0,.5f,.5f,.6f,1});
            Matrix4f[] matrices={constant,constant,constant};
            float[] texels={.01f,.01f,.01f};
            IsometricEffects off=options(false,false,0f);
            reset(); ground(overlay); overlay.capture(true);
            if (!overlay.hasDepth()) throw new AssertionError("Actual depth/stencil FBO depth was not copied");
            overlay.draw(receiver,off,0,matrices,texels,100f,8,0,1f);
            check(.0f,false);
            // A fake populated map without a prepared scene cannot invent a lighting ratio.
            // Real selected-pack sun/lamp projection is exercised by NativeShadow068Probe.
            reset(); ground(overlay); overlay.capture(true);
            overlay.draw(receiver,options(true,false,1f),map,matrices,texels,100f,8,1,1f);
            check(.0f,false);
            // Independent color style works with no shadow maps or copied native depth.
            reset(); overlay.capture(false);
            overlay.draw(receiver,options(false,true,1f),0,matrices,texels,100f,8,0,0f);
            check(.0f,true);
            // Unprepared lighting remains identity even with a previously populated map.
            reset(); overlay.capture(true);
            overlay.draw(receiver,options(true,false,1f),map,matrices,texels,100f,8,1,0f);
            check(.0f,false);
            // A shadow request with no copied depth preserves original RGBA as well.
            reset(); ground(overlay); overlay.capture(false);
            overlay.draw(receiver,options(true,false,1f),map,matrices,texels,100f,8,1,1f);
            check(.0f,false);
            if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("Production compositor left a GL error");
            System.out.println("Native overlay GPU PASS: actual color/depth copy from DEPTH24_STENCIL8; independent style, unprepared-map/no-depth guards; identity, original RGBA/alpha, depth/stencil and offset viewport preserved; no VP world color.");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void checkReceiver() {
        // Actual native projection/depth helpers, with the origin placed at screen centre.
        double worst=0;
        for(int cx : new int[]{-17,0,4205,11000}) for(int level : new int[]{-2,0,3}) {
            float camX=cx+.25f,camY=5211.5f,camZ=level+.2f;
            double[][] samples=new double[4][3];
            double baseX=screen("XToScreen",camX,camY,camZ),baseY=screen("YToScreen",camX,camY,camZ);
            for(int i=0;i<4;i++) {
                float x=camX+(i==1?1:0),y=camY+(i==2?1:0),z=camZ+(i==3?1:0);
                samples[i][0]=.5+(screen("XToScreen",x,y,z)-baseX)/1920.0;
                samples[i][1]=.5-(screen("YToScreen",x,y,z)-baseY)/1080.0;
                samples[i][2]=sample((int)camX,(int)camY,x,y,z);
            }
            NativeReceiver receiver=new NativeReceiver(); receiver.fit(samples);
            for(int dx=-12;dx<=12;dx+=4) for(int dy=-12;dy<=12;dy+=4) {
                float x=camX+dx,y=camY+dy,z=camZ+1;
                float[] v={(float)((screen("XToScreen",x,y,z)-baseX)/1920.0),
                        (float)(-(screen("YToScreen",x,y,z)-baseY)/1080.0),
                        (float)(sample((int)camX,(int)camY,x,y,z)-receiver.origin[2]),0f};
                Object destination=vector();
                GameAccess.call("org.joml.Matrix4f","transform",receiver.inverse,
                        new Class<?>[]{float.class,float.class,float.class,float.class,GameAccess.type("org.joml.Vector4f")},
                        v[0],v[1],v[2],v[3], destination);
                double error=Math.max(Math.abs(((Number)GameAccess.field("org.joml.Vector4f","x",destination)).floatValue()-dx),
                        Math.max(Math.abs(((Number)GameAccess.field("org.joml.Vector4f","y",destination)).floatValue()-dy),
                        Math.abs(((Number)GameAccess.field("org.joml.Vector4f","z",destination)).floatValue()-1)));
                worst=Math.max(worst,error);
            }
        }
        if(worst>.002) throw new AssertionError("Native receiver inversion error: "+worst);
        System.out.println("Native receiver PASS: actual native screen/depth helper inversion; worst "+worst+" tiles across negative/chunk/level coordinates.");
    }
    private static void ground(NativeOverlay overlay) {
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,W,H,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+7,GL11.GL_TEXTURE_2D,texture,0);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,0,0,0,0,GL30.GL_COLOR_ATTACHMENT0+7});
        GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{80f/255,120f/255,160f/255,127f/255});
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        overlay.ground(texture);
    }
    private static Object vector() {
        try { return GameAccess.type("org.joml.Vector4f").getConstructor().newInstance(); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static double screen(String method,float x,float y,float z) {
        return ((Number)GameAccess.call("zombie.iso.IsoUtils",method,null,
                new Class<?>[]{float.class,float.class,float.class,int.class},x,y,z,0)).doubleValue();
    }
    private static float sample(int sx,int sy,float x,float y,float z) {
        return ((Number)GameAccess.call("vpiso.NativeVision","sample",null,
                new Class<?>[]{int.class,int.class,float.class,float.class,float.class},sx,sy,x,y,z)).floatValue();
    }
    private static IsometricEffects options(boolean shadows,boolean style,float strength) {
        return new IsometricEffects(shadows,.5f,0f,128f,1024,style,strength,1.5f,1f,1f);
    }
    private static void setup() {
        color=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,W,H,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        depth=GL30.glGenRenderbuffers(); GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,H);
        fbo=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Fixture FBO incomplete");
        map=GL11.glGenTextures(); GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,map);
        FloatBuffer depths=BufferUtils.createFloatBuffer(8*8*3);
        for(int i=0;i<8*8*3;i++) depths.put(.2f); depths.flip();
        GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL30.GL_DEPTH_COMPONENT32F,8,8,3,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL14.GL_TEXTURE_COMPARE_MODE,GL30.GL_COMPARE_REF_TO_TEXTURE);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL14.GL_TEXTURE_COMPARE_FUNC,GL11.GL_LEQUAL);
    }
    private static void reset() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDepthMask(true); GL11.glStencilMask(255); GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(80f/255f,120f/255f,160f/255f,127f/255f); GL11.glClearDepth(.5); GL11.glClearStencil(37);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glViewport(X,Y,VW,VH);
    }
    private static void check(float dark,boolean styled) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);
        ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);
        FloatBuffer depths=BufferUtils.createFloatBuffer(W*H);
        ByteBuffer stencils=BufferUtils.createByteBuffer(W*H);
        GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
        GL11.glReadPixels(0,0,W,H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencils);
        int changed=0;
        for(int y=0;y<H;y++) for(int x=0;x<W;x++) {
            int i=y*W+x; boolean inside=x>=X&&x<X+VW&&y>=Y&&y<Y+VH;
            if ((pixels.get(i*4+3)&255)!=127 || Math.abs(depths.get(i)-.5f)>1e-6f || (stencils.get(i)&255)!=37) {
                throw new AssertionError("Native alpha/depth/stencil changed at "+x+","+y);
            }
            int[] original={80,120,160};
            for(int c=0;c<3;c++) {
                int value=pixels.get(i*4+c)&255;
                if (!inside || !styled) {
                    int expected=Math.round(original[c]*(inside?1f-dark:1f));
                    if (Math.abs(value-expected)>1) throw new AssertionError("Unexpected RGB "+value+" vs "+expected+" at "+x+","+y);
                } else if(value!=original[c]) changed++;
            }
        }
        if(styled&&changed==0) throw new AssertionError("Style switch had no effect");
    }
}
