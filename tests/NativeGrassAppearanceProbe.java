import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Reproduces native ground detail leaking into real VP grass, without launching the game. */
public final class NativeGrassAppearanceProbe {
    static final int W=128;
    static int fbo,color,depth;
    static Frame frame;
    static NativeReceiver receiver;
    static NativeGrassOverlay overlay;
    record Sample(byte[] color,float[] depth) {}

    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"grass appearance",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            TestShaderPacks.select(args[0],args[0].equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off"));
            Gl.init();setting("grass.quality","Medium");setting("grass.style","Realistic");
            frame=NativeSurfaceReceiverProbe.frame();frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
            frame.scene.time=1; // Still weather makes pairs deterministic.
            for(int y=-1;y<1;y++)for(int x=-1;x<1;x++) {
                int c=frame.scene.grass.chunks++;frame.scene.grass.chunkX[c]=x;frame.scene.grass.chunkY[c]=y;
                int value=(Integer)GameAccess.call("viewpoint.light.GrassMap","value",null,new Class<?>[]{int.class,int.class,boolean.class,boolean.class,int.class,int.class},1,2,true,false,x,y);
                Arrays.fill(frame.scene.grass.values,c*64,c*64+64,value);
            }
            receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.32,.5},{.62,.26,.4985566458},{.38,.26,.4985566458},{.5,.68,.4971132916}});
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            color=attachment(false);depth=attachment(true);
            GameAccess.staticCall("viewpoint.render.TilePalette","update");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,(Integer)GameAccess.field("viewpoint.render.TilePalette","texture",null));
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,1,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{.2f,.5f,.1f,1});
            overlay=new NativeGrassOverlay();
            int checked=0;
            for(String style:new String[]{"Realistic","Painted"}) {
                if(style.equals("Painted")) {
                    // Cutout and non-uniform detail, rather than 050's opaque white atlas.
                    GameAccess.call("viewpoint.render.GrassAtlas","make",null,new Class<?>[0]);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,(Integer)GameAccess.field("viewpoint.render.GrassAtlas","texture",null));
                    ByteBuffer atlas=BufferUtils.createByteBuffer(1024*1024*4);
                    for(int y=0;y<1024;y++)for(int x=0;x<1024;x++) {
                        int leaf=x%64,top=y%64;
                        int a=(leaf>12+top/5 && leaf<51-top/5)?255:0;
                        int c=110+(x*13+y*7)%100;
                        atlas.put((byte)c).put((byte)c).put((byte)c).put((byte)a);
                    }
                    atlas.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1024,1024,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,atlas);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                }
                setting("grass.style",style);
                Sample flat=draw(false,false),striped=draw(true,false);
                int common=0,leaked=0;double bright=0;
                for(int i=0;i<W*W;i++)if(flat.depth[i]<.99f && striped.depth[i]<.99f) {
                    common++;int error=0;
                    for(int c=0;c<3;c++)error+=Math.abs((flat.color[4*i+c]&255)-(striped.color[4*i+c]&255));
                    if(error>6)leaked++;
                    bright+=(flat.color[4*i+1]&255)/255.0;
                }
                if(common<100)throw new AssertionError("No grass for appearance comparison: "+style+" "+common);
                if(leaked>common/100)throw new AssertionError("Native ground detail leaks into grass: "+style+" "+leaked+"/"+common);
                // Root illumination must react to night even when the displayed material stays bright.
                frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=.1f;
                Sample night=draw(false,false);double dim=0;
                for(int i=0;i<W*W;i++)if(flat.depth[i]<.99f && night.depth[i]<.99f)dim+=(night.color[4*i+1]&255)/255.0;
                if(dim>bright*.3)throw new AssertionError("Captured root light ignored: night/day="+dim/bright);
                frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
                Sample floor=draw(true,true);int visible=0;
                float[] floors=floorDepth();
                for(int i=0;i<W*W;i++)if(floor.depth[i]<floors[i]-1e-6f)visible++;
                if(visible<common*.65)throw new AssertionError("Native ground depth clips grass: "+style+" "+visible+"/"+common);
                checked+=common;
                System.out.println("Grass appearance "+style+": identical material-independent pixels="+common+", leaked="+leaked+", root night/day="+dim/bright+", floor coverage="+visible);
            }
            if(GL11.glGetError()!=0)throw new AssertionError("Grass appearance GL error");
            if(args.length>1)throw new AssertionError("Pinned 050 negative control did not reproduce");
            System.out.println("Native grass appearance GPU PASS: "+args[0]+"; pixels="+checked+"; textured base, captured light, cutout atlas and sloped native depth.");
        } catch(AssertionError e) {
            if(args.length>1 && e.getMessage().startsWith("Native ground detail leaks into grass:"))
                System.out.println("Pinned 050 appearance negative control PASS: "+e.getMessage());
            else throw e;
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void setting(String key,String value){GameAccess.call("viewpoint.platform.LiveSettings","put",null,new Class<?>[]{String.class,String.class},key,value);}
    static int attachment(boolean isDepth) {
        int tex=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,isDepth?GL30.GL_DEPTH_COMPONENT32F:GL11.GL_RGBA8,W,W,0,isDepth?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,isDepth?GL30.GL_DEPTH_ATTACHMENT:GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,tex,0);return tex;
    }
    static Sample draw(boolean stripes,boolean floor) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        ByteBuffer base=BufferUtils.createByteBuffer(W*W*4);
        for(int y=0;y<W;y++)for(int x=0;x<W;x++) {
            int c=stripes?(((x+2*y)/7%2==0)?30:220):96;
            base.put((byte)c).put((byte)c).put((byte)c).put((byte)255);
        }
        base.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,W,W,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,base);
        if(floor){GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,floorDepth());}
        frame.number++;overlay.draw(frame,receiver);frame.scene.grass.chunks=0;
        ByteBuffer rgba=BufferUtils.createByteBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba);
        byte[] out=new byte[rgba.remaining()];rgba.get(out);float[] depths=new float[W*W];GL11.glReadPixels(0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);return new Sample(out,depths);
    }
    static float[] floorDepth() {
        Matrix4f project=NativeSurfaces.projection(receiver),inverse=new Matrix4f(project).invert();
        float[] result=new float[W*W];
        for(int y=0;y<W;y++)for(int x=0;x<W;x++) {
            float u=(x+.5f)/W*2-1,v=(y+.5f)/W*2-1;
            Vector3f p=inverse.transformPosition(new Vector3f(u,v,-1)),q=inverse.transformPosition(new Vector3f(u,v,1));
            q.sub(p);p.fma(-p.y/q.y,q);project.transformPosition(p);
            result[y*W+x]=p.z*.5f+.5f;
        }
        return result;
    }
}
