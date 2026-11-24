import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.WorldRenderer;
import vpiso.*;

/** Real VP grass compute/storage/draw on a native receiver, isolated from the user's world/settings. */
public final class NativeGrassProbe {
    static final int W=128;
    static int fbo;
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(W,W,"native VP grass",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
            TestShaderPacks.select(args[0],args[0].equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off"));
            Gl.init();
            GameAccess.call("viewpoint.platform.LiveSettings","put",null,new Class<?>[]{String.class,String.class},"grass.quality","Medium");
            GameAccess.call("viewpoint.platform.LiveSettings","put",null,new Class<?>[]{String.class,String.class},"grass.style","Realistic");
            Frame frame=NativeSurfaceReceiverProbe.frame();frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
            frame.scene.windX=2;frame.scene.windZ=1;frame.scene.time=1;
            for(int y=-1;y<1;y++)for(int x=-1;x<1;x++) {
                int c=frame.scene.grass.chunks++;frame.scene.grass.chunkX[c]=x;frame.scene.grass.chunkY[c]=y;
                int value=(Integer)GameAccess.call("viewpoint.light.GrassMap","value",null,new Class<?>[]{int.class,int.class,boolean.class,boolean.class,int.class,int.class},1,2,true,false,x,y);
                Arrays.fill(frame.scene.grass.values,c*64,c*64+64,value);
            }
            NativeReceiver receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.32,.5},{.62,.26,.4985566458},{.38,.26,.4985566458},{.5,.68,.4971132916}});
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            for(boolean depth:new boolean[]{false,true}) {
                int tex=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,depth?GL30.GL_DEPTH_COMPONENT32F:GL11.GL_RGBA8,W,W,0,depth?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,depth?GL30.GL_DEPTH_ATTACHMENT:GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,tex,0);
            }
            GameAccess.staticCall("viewpoint.render.TilePalette","update");
            int palette=(Integer)GameAccess.field("viewpoint.render.TilePalette","texture",null);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,palette);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,1,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{.2f,.5f,.1f,1});
            NativeGrassOverlay overlay=new NativeGrassOverlay();
            clear(.2f,.3f,.1f,1);byte[] baseline=image();
            frame.number++;overlay.draw(frame,receiver);byte[] grass=image();
            int changed=changed(baseline,grass);if(changed<100)throw new AssertionError("No native grass pixels: "+changed+" quality="+viewpoint.render.Grass.QUALITY.get()+" gl="+GL11.glGetError());
            frame.scene.grass.chunks=0; // VP sends incremental changes, not a complete grass map every frame.
            clear(.2f,.3f,.1f,1);frame.number++;overlay.draw(frame,receiver);
            if(changed(baseline,image())<100)throw new AssertionError("Grass vanished between incremental updates");
            clear(.2f,.3f,.1f,0);byte[] front=image();frame.number++;overlay.draw(frame,receiver);
            if(changed(front,image())!=0)throw new AssertionError("Grass covered native foreground depth");
            clear(0,0,0,1);byte[] black=image();frame.number++;overlay.draw(frame,receiver);
            if(changed(black,image())!=0)throw new AssertionError("Grass revealed black native vision");
            // The real generator must change its blades for VP's time/wind, then preserve output ownership.
            clear(.2f,.3f,.1f,1);frame.scene.time=9;frame.scene.windX=12;frame.number++;overlay.draw(frame,receiver);
            if(changed(grass,image())<10)throw new AssertionError("Grass time/wind frozen");
            // Controlled opaque atlas: exercises VP's painted cards without claiming to load game art.
            GameAccess.call("viewpoint.render.GrassAtlas","make",null,new Class<?>[0]);
            int atlas=(Integer)GameAccess.field("viewpoint.render.GrassAtlas","texture",null);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,atlas);
            ByteBuffer solid=BufferUtils.createByteBuffer(1024*1024*4);while(solid.hasRemaining())solid.put((byte)255);solid.flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1024,1024,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,solid);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GameAccess.call("viewpoint.platform.LiveSettings","put",null,new Class<?>[]{String.class,String.class},"grass.style","Painted");
            clear(.2f,.3f,.1f,1);frame.number++;overlay.draw(frame,receiver);
            if(changed(baseline,image())<100)throw new AssertionError("Painted cards unavailable");
            GameAccess.call("viewpoint.platform.LiveSettings","put",null,new Class<?>[]{String.class,String.class},"grass.style","Realistic");
            Object pass=GameAccess.field("viewpoint.render.SurfacePass","grass",GameAccess.field("viewpoint.render.WorldRenderer","surfaces",null));
            var context=new viewpoint.render.FrameContext();context.scene=frame.scene;context.eyeX=5000;context.eyeY=5000;context.eyeZ=5000;
            org.joml.Vector3f ordinary=(org.joml.Vector3f)GameAccess.call("viewpoint.render.GrassPass","grownFor",pass,new Class<?>[]{viewpoint.render.FrameContext.class},context);
            if(ordinary.x!=5000)throw new AssertionError("Ordinary VP grass eye changed");
            IsometricBridge.modelRendering(frame,new IsometricEffects(false,0,1,64,1024,false,0,1,1,1,false,true));
            org.joml.Vector3f logical=(org.joml.Vector3f)GameAccess.call("viewpoint.render.GrassPass","grownFor",pass,new Class<?>[]{viewpoint.render.FrameContext.class},context);
            if(logical.x!=0||logical.y!=1.5f||logical.z!=0)throw new AssertionError("Isometric grass still grows around remote camera");
            IsometricBridge.modelRendering(frame,IsometricEffects.DEFAULT);
            // With the switch off, the woven real pass exits before touching an invalid context.
            GameAccess.call("viewpoint.render.GrassPass","draw",pass,new Class<?>[]{viewpoint.render.FrameContext.class,GameAccess.type("viewpoint.render.Targets")},null,null);
            IsometricBridge.rendering(null);
            // The complete model path must also keep grass near the player, with the remote camera.
            org.joml.Matrix4f view=new org.joml.Matrix4f();float[] eye=new float[3];
            IsoCameraMath.matrices(new IsoCameraMath.Fit(0,0,6),0,0,W,W,frame.scene.projection,view,eye,0);
            for(boolean on:new boolean[]{false,true}) {
                clear(.2f,.3f,.1f,1);frame.number++;
                IsometricBridge.modelRendering(frame,new IsometricEffects(false,0,1,64,1024,false,0,1,1,1,false,on));
                WorldRenderer.hidden=false;WorldRenderer.cullFromPlayer=false;
                if(!WorldRenderer.begin(frame.scene,frame.number,1,view,eye[0],eye[1],eye[2]))throw new AssertionError("Model grass begin");
                Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,(Integer)GameAccess.field("viewpoint.render.Targets","gbufferFbo",targets));
                float[] depths=new float[W*W];GL11.glReadPixels(0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
                int covered=0;for(float d:depths)if(d<.999999f)covered++;
                WorldRenderer.finish(frame.scene);IsometricBridge.rendering(null);
                if(on?covered<100:covered!=0)throw new AssertionError("Model grass switch/remote camera failed: on="+on+" covered="+covered);
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=fbo||IsometricBridge.rendering())throw new AssertionError("Grass output/scope leaked");
            if(GL11.glGetError()!=0)throw new AssertionError("Grass GL error");
            System.out.println("Native VP grass GPU PASS: "+args[0]+" pixels="+changed+"; real compute/storage/draw; independent native receiver, depth/blackout, time/wind, incremental map, painted cards, full model switch and remote eye, ordinary VP isolation.");
        } finally {GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static void clear(float r,float g,float b,double depth){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glClearColor(r,g,b,1);GL11.glClearDepth(depth);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);}
    static byte[] image(){ByteBuffer b=BufferUtils.createByteBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[] out=new byte[b.remaining()];b.get(out);return out;}
    static int changed(byte[] a,byte[] b){int n=0;for(int i=0;i<a.length;i+=4)if(a[i]!=b[i]||a[i+1]!=b[i+1]||a[i+2]!=b[i+2])n++;return n;}
}
