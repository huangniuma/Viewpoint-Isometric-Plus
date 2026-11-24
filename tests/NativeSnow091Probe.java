import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Real VP snow clipmap/state/contact and native occlusion, with no game launch. */
public final class NativeSnow091Probe {
    static final int W=128;
    static Frame frame;
    static NativeReceiver receiver;
    static NativeSnowOverlay overlay=new NativeSnowOverlay();
    static int fbo,color,ground,owner,depth,levels;
    static float floorLevel;
    record Sample(byte[] rgba,float[] z) {}
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"native snow",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GL43.glDebugMessageCallback((source,type,id,severity,len,message,user)->{
                if(type==GL43.GL_DEBUG_TYPE_ERROR){System.err.println(GLDebugMessageCallback.getMessage(len,message));Thread.dumpStack();}
            },0L);GL11.glEnable(GL43.GL_DEBUG_OUTPUT);GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            Main.compatible=true;
            String quality=args.length>1?args[1]:"medium";
            TestShaderPacks.select(args[0],args[0].equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off","snow",quality));Gl.init();
            if(!ShaderPacks.active().pipeline.snow) {
                Object pipeline=GameAccess.call("viewpoint.platform.Pipeline","of",null,new Class<?>[]{Map.class},Map.of(Pipeline.Knob.SNOW,quality));
                GameAccess.set("viewpoint.platform.ShaderPacks$Active","pipeline",ShaderPacks.active(),pipeline);
            }
            System.out.println("Pipeline snow="+ShaderPacks.active().pipeline.snow+" quality="+ShaderPacks.active().pipeline.snowQuality);
            frame=NativeSurfaceReceiverProbe.frame();frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;frame.scene.time=1;
            frame.scene.snowLying.depth=.18f;frame.scene.snowLying.cover=1;frame.scene.snowLying.hours=100;
            Arrays.fill(frame.scene.snowLying.grid,(byte)255);
            receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.32,.5},{.62,.26,.4985566458},{.38,.26,.4985566458},{.5,.68,.4971132916}});
            NativeGrassAppearanceProbe.receiver=receiver;
            NativeSurfaceReceiverProbe.opaque=64;
            Object mesh=NativeSurfaceReceiverProbe.mesh(frame.scene,0,NativeSurfaceReceiverProbe.horizontal(0,24));frame.scene.meshFlags[0]=(byte)0x80;
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            color=texture(GL11.GL_RGBA8,GL30.GL_COLOR_ATTACHMENT0);ground=texture(GL11.GL_RGBA8,GL30.GL_COLOR_ATTACHMENT7);
            owner=texture(GL30.GL_RGBA32F,GL30.GL_COLOR_ATTACHMENT6);depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL30.GL_DEPTH_ATTACHMENT);
            levels=texture(GL30.GL_RGBA32F,GL30.GL_COLOR_ATTACHMENT3);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");
            Sample snow=null;
            for(int i=0;i<6;i++)snow=draw(0);
            int covered=count(snow);
            Object pass=GameAccess.field("viewpoint.render.WorldRenderer","snow",null);
            Object snowGround=GameAccess.field("viewpoint.render.SnowPass","ground",pass);
            System.out.println("Snow setup: quality="+ShaderPacks.active().pipeline.snowQuality+", ground="+GameAccess.call("viewpoint.render.SnowGround","drawn",snowGround,new Class<?>[0])+", coverage="+covered);
            require(covered>500,"No accumulated snow drawn: "+covered);
            Sample hidden=draw(1);require(count(hidden)==0,"Snow covered opaque native furniture");
            Sample edge=draw(.5f);int edgePixels=0;
            for(int i=0;i<W*W;i++)if((snow.rgba[i*4]&255)>100) {
                int expected=Math.round(255*.1f+(snow.rgba[i*4]&255)*.5f);
                require(Math.abs((edge.rgba[i*4]&255)-expected)<=2,"Translucent foreground lost");edgePixels++;
            }
            floorLevel=1;require(count(draw(0))==0,"Lower snow leaked onto upper-storey native floor");floorLevel=0;
            for(double zoom:new double[]{.25,.5,2.5}) {
                receiver.fit(new double[][]{{.5,.32,.5},{.5+.12/zoom,.32-.06/zoom,.4985566458},{.5-.12/zoom,.32-.06/zoom,.4985566458},{.5,.32+.36/zoom,.4971132916}});
                require(count(draw(0))>500,"Snow missing at zoom "+zoom);
            }
            receiver.fit(new double[][]{{.5,.32,.5},{.62,.26,.4985566458},{.38,.26,.4985566458},{.5,.68,.4971132916}});
            frame.scene.snowLying.depth=0;require(count(draw(0))==0,"Snow drawn at zero depth");frame.scene.snowLying.depth=.18f;
            Sample untouched=draw(0);
            byte[] stableBefore=state(pass);for(int i=0;i<3;i++)draw(0);
            require(Arrays.equals(stableBefore,state(pass)),"Empty snow state changed without model contacts");
            // A real posed skinned model contacts the ground; no synthetic footprint brush.
            float[][] feet=NativeSurfaceReceiverProbe.horizontal(.035f,.18f);
            NativeSurfaceReceiverProbe.addModel(frame.scene,feet,true,false);
            byte[] before=state(pass);
            for(int i=0;i<8;i++)draw(0);
            byte[] after=state(pass);int changed=0;for(int i=0;i<after.length;i++)if(before[i]!=after[i])changed++;
            require(changed>0,"Posed character contact did not leave snow tracks");
            Sample tracked=draw(0);int visibleTracks=0;
            for(int i=0;i<W*W;i++)if(Math.abs((untouched.rgba[i*4]&255)-(tracked.rgba[i*4]&255))>1)visibleTracks++;
            require(visibleTracks>0,"Footprint state did not affect visible native snow");
            // Same VP plan rules used by its snow layer: a known room with a ceiling.
            var plan=frame.scene.floorPlan;plan.chunkX=plan.chunkY=-8;
            for(int i=0;i<plan.passes.length;i++){plan.passes[i]=new int[64];plan.flags[i]=new byte[64];Arrays.fill(plan.flags[i],(byte)1);}
            require(count(draw(0))==0,"Accumulated snow appeared inside native room");
            System.out.println("Native snow091 GPU PASS: "+args[0]+" "+quality+"; snow pixels="+covered+", alpha edges="+edgePixels+", native foreground/storeys/indoor shelter/zoom preserved, zero snow skipped, skinned-model footprint state changed bytes="+changed+", visible track pixels="+visibleTracks);
            require(GL11.glGetError()==0,"OpenGL error");NativeSurfaceReceiverProbe.retire(mesh);
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static byte[] state(Object pass) {
        Object state=GameAccess.field("viewpoint.render.SnowPass","state",pass);
        Object fine=GameAccess.field("viewpoint.render.SnowState","fine",state);
        int texture=(Integer)GameAccess.field("viewpoint.render.SnowLevel","texture",fine);
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,texture);
        int w=GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_TEXTURE_WIDTH);
        int layers=GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY,0,GL12.GL_TEXTURE_DEPTH);
        ByteBuffer b=BufferUtils.createByteBuffer(w*w*layers*4);GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
        byte[] out=new byte[b.remaining()];b.get(out);return out;
    }
    static int texture(int format,int attachment) {
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,W,W,0,attachment==GL30.GL_DEPTH_ATTACHMENT?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,GL11.GL_FLOAT,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,attachment,GL11.GL_TEXTURE_2D,id,0);return id;
    }
    static Sample draw(float blocked) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(.2f,.3f,.12f,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        // ClearBuffer addresses draw-buffer indices, so route all native attachments first.
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,GL30.GL_COLOR_ATTACHMENT3,0,0,GL30.GL_COLOR_ATTACHMENT6,GL30.GL_COLOR_ATTACHMENT7});
        GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{blocked,0,0,1});GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{.2f*(1-blocked),.3f*(1-blocked),.12f*(1-blocked),1});
        GL30.glClearBufferfv(GL11.GL_COLOR,3,new float[]{floorLevel*(1-blocked),floorLevel*floorLevel*(1-blocked),1-blocked,1});
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,NativeGrassAppearanceProbe.floorDepth());
        frame.number++;frame.simNanos=frame.number*50_000_000L;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        try{overlay.draw(frame,receiver,ground,owner,levels);}finally{GL11.glPopClientAttrib();GL11.glPopAttrib();}
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);ByteBuffer b=BufferUtils.createByteBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
        byte[] out=new byte[b.remaining()];b.get(out);float[] z=new float[W*W];GL11.glReadPixels(0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,z);return new Sample(out,z);
    }
    static int count(Sample s){int n=0;for(int i=0;i<W*W;i++)if((s.rgba[i*4]&255)>100)n++;return n;}
    static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}
