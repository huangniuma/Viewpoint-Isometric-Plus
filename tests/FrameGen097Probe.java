package viewpoint.render;

import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.upscale.*;
import vpiso.*;

/** Real SDK frame generation plus native/model producers and HUD/reset lifecycle. */
public final class FrameGen097Probe {
    static int checks,generated,output,screenColor,drawIndex;
    static final int W=Integer.getInteger("framegen097.width",256),H=Integer.getInteger("framegen097.height",160);
    static final Frame frame=new Frame();
    static FrameContext context;
    static Targets targets;
    static FrameGenPass pass;
    static NativeReceiver receiver;
    static Object nativeDrawer;
    static long clock=1_000_000_000L;

    public static void main(String[] args)throws Exception {
        check(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,H,"framegen097 isolated native SDK",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            Main.compatible=true;GameAccess.set("viewpoint.platform.BuildPin","supported",null,Boolean.TRUE);
            if(args.length>0 && args[0].equals("baseline096")) {
                check(IsometricBridge.blockedReason("default",false,1,false,true)!=null,"096 FG gate missing");
                System.out.println("Previous096 frame generation negative PASS: global frame generation blocks both isometric model modes.");return;
            }
            IsometricControls.tick();((LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null)).set(true);
            Graphics.FRAME_GENERATION.set(false);ShaderPacks.useBuiltIn("default","high",Map.of("upscaling","off","reflections","off"));
            check(FrameGeneration.unavailable()==null,"FG unavailable: "+FrameGeneration.unavailable());
            context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
            targets=new Targets();GameAccess.set("viewpoint.render.WorldRenderer","targets",null,targets);
            pass=new FrameGenPass(new VendorInputs(targets));FrameGenPass.fixedScreen=new int[]{W,H};
            output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
            screenColor=texture(GL11.GL_RGBA8,GL11.GL_RGBA,W,H);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,screenColor,0);
            int depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,W,H);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            setup();options();
            if(Boolean.getBoolean("framegen097.nativeOnly"))nativeMode();
            else {model();nativeMode();switching();failure();}
            check(GL11.glGetError()==0,"GL error");
            System.out.println("FrameGen097 GPU PASS: checks="+checks+" generated="+generated+"; actual AMD SDK, both producers, independent setting, depth/motion, HUD reference, camera cuts, resize, switch and original rendering. No live-save acceptance.");
        } finally {
            if(pass!=null) {GameAccess.call("viewpoint.render.FrameGenPass","release",pass,new Class<?>[0]);Object n=GameAccess.field("vpiso.IsometricFrameGeneration","nativePass",null);if(n!=null)GameAccess.call("viewpoint.render.FrameGenPass","release",n,new Class<?>[0]);}
            IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
    static void setup() {
        context.scene=frame.scene;context.pipeline=ShaderPacks.active().pipeline;context.width=W;context.height=H;context.outputWidth=W;context.outputHeight=H;
        context.viewport[0]=context.viewport[1]=0;context.viewport[2]=W;context.viewport[3]=H;context.outputDrawFbo=context.outputReadFbo=output;
        frame.scene.projection.setPerspective(.006f,(float)W/H,IsoCameraMath.NEAR,IsoCameraMath.FAR);context.view=new Matrix4f().setLookAt(0,3000,5196,0,0,0,0,1,0);
        frame.scene.projection.mul(context.view,context.plainViewProjection);context.invPlainViewProjection.set(context.plainViewProjection).invert();context.reprojection.set(context.plainViewProjection);
        context.frameSeconds=1f/60;context.eyeY=3000;context.eyeZ=5196;
        targets.depth=texture(GL30.GL_R32F,GL11.GL_RED,W,H);targets.velocity=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,W,H);targets.farDepth=texture(GL30.GL_R32F,GL11.GL_RED,1,1);
        upload(targets.farDepth,1,1,GL11.GL_RED,1,new float[]{1});
        upload(targets.depth,W,H,GL11.GL_RED,1,new float[]{.5f});upload(targets.velocity,W,H,GL11.GL_RGBA,4,new float[4]);
    }
    static void options() {
        Object ownNeed=GameAccess.call("viewpoint.upscale.UpscaleNeeds","of",null,new Class<?>[]{String.class,String.class},"vpiso.frameGeneration","true");
        Object vpNeed=GameAccess.call("viewpoint.upscale.UpscaleNeeds","of",null,new Class<?>[]{String.class,String.class},"graphics.frameGeneration","true");
        check(ownNeed!=null && ownNeed.equals(vpNeed),"Independent option lost VP's native-library availability/install UI");
        check(GameAccess.call("viewpoint.upscale.UpscaleNeeds","of",null,new Class<?>[]{String.class,String.class},"vpiso.frameGeneration","false")==null,"Off requests native-library setup");
        IsometricBridge.rendering(null);check(!Graphics.FRAME_GENERATION.get(),"Other-view option changed");
        IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);check(Graphics.FRAME_GENERATION.get(),"Independent FG option not used by model frame");
        var toggle=(LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null);toggle.set(false);check(!Graphics.FRAME_GENERATION.get(),"Independent FG off ignored");
        Graphics.FRAME_GENERATION.set(true);check(!Graphics.FRAME_GENERATION.get(),"Global VP option overrides isometric switch");
        toggle.set(true);IsometricBridge.rendering(null);check(Graphics.FRAME_GENERATION.get(),"Global option erased");Graphics.FRAME_GENERATION.set(false);
        for(float[] yaw:new float[][]{{0,1},{1,0},{359,0},{0,359},{90,95},{95,90}})check(!IsometricFrameGeneration.rotationCut(yaw[0],yaw[1],true),"Smooth rotation resets FG");
        check(IsometricFrameGeneration.rotationCut(0,90,true),"Large rotation did not reset");check(IsometricFrameGeneration.rotationCut(1,0,false),"FG-off behavior changed");
        System.out.println("FrameGen097 options PASS: independent global/isometric values and bounded wrap-aware camera cuts.");
    }
    static void model() {
        for(int scale:new int[]{50,100}) {
            context.width=W*scale/100;context.height=H*scale/100;context.previousValid=false;
            targets.depth=texture(GL30.GL_R32F,GL11.GL_RED,context.width,context.height);targets.velocity=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,context.width,context.height);
            upload(targets.depth,context.width,context.height,GL11.GL_RED,1,new float[]{.5f});upload(targets.velocity,context.width,context.height,GL11.GL_RGBA,4,new float[4]);
            for(int i=0;i<5;i++) {
                IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);context.previousValid=i!=0&&i!=4;
                drawScreen(i);pass.capture(context);
                UpscaleFrame described=(UpscaleFrame)GameAccess.field("viewpoint.render.FrameGenPass","described",pass);near(described.near(),IsoCameraMath.NEAR,.001f);
                check(FrameGenPass.made()==pass,"Model pass not selected");copyAndGenerate(pass,i!=0&&i!=4);
            }
            System.out.println("FrameGen097 model GPU PASS: renderScale="+scale+" first/cut suppressed, subsequent generated, shared camera clips.");
        }
        context.width=W;context.height=H;
    }
    static void nativeMode()throws Exception {
        IsometricBridge.rendering(null);
        Object[] drawers=(Object[])GameAccess.field("vpiso.IsometricFrameGeneration","DRAWERS",null);nativeDrawer=drawers[0];
        GameAccess.set(nativeDrawer.getClass().getName(),"frame",nativeDrawer,frame);
        receiver=(NativeReceiver)GameAccess.field(nativeDrawer.getClass().getName(),"receiver",nativeDrawer);
        receiver.fit(new double[][]{{.5,.5,.5},{.55,.525,.501},{.45,.525,.501},{.5,.4,.502}});
        for(int i=0;i<5;i++) {
            frame.number++;frame.camX=i*.01f;drawScreen(i);nativeDepth();
            if(i>=2)GL11.glViewport(7,9,W-16,H-18);
            if(i==4)IsometricFrameGeneration.newSession();
            int pbo=GL15.glGenBuffers();GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER,16L,GL15.GL_STATIC_DRAW);
            ((zombie.core.textures.TextureDraw.GenericDrawer)nativeDrawer).render();
            check(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)==pbo,"Native drawer changed caller unpack PBO");GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL15.glDeleteBuffers(pbo);
            Object selected=GameAccess.field("vpiso.IsometricFrameGeneration","nativePass",null);check(selected!=null,"Native producer absent");
            check(!(Boolean)GameAccess.field("vpiso.IsometricFrameGeneration","failed",null),"Native producer failed");
            UpscaleFrame described=(UpscaleFrame)GameAccess.field("viewpoint.render.FrameGenPass","described",selected);check(described!=null,"Native description missing");
            boolean reset=i==0||i==2||i==4;
            near(described.near(),IsoCameraMath.NEAR,.001f);check(described.reset()==reset,"Native reset decision");
            FrameGeneration generation=(FrameGeneration)GameAccess.field("viewpoint.render.FrameGenPass","generation",selected);
            int width=generation.frameWidth,height=generation.frameHeight;
            float[] d=readTexture(generation.depth(),width*height,GL11.GL_RED),m=readTexture(generation.motion(),width*height*2,GL30.GL_RG);
            for(int y=20;y<height-20;y+=19)for(int x=20;x<width-20;x+=23){int at=y*width+x;check(Float.isFinite(d[at])&&d[at]>.1&&d[at]<.9,"Native projected depth");if(reset)near(m[at*2],0,.00001f);else near(m[at*2],.0005f,.000002f);}
            copyAndGenerate((FrameGenPass)selected,!reset);
        }
        System.out.println("FrameGen097 native GPU PASS: real native drawer/depth, mapped perspective clips, camera motion, warmup/session cuts and native SDK generation.");
    }
    static void switching() {
        IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);context.previousValid=true;drawScreen(1);pass.capture(context);copyAndGenerate(pass,false);
        drawScreen(2);pass.capture(context);copyAndGenerate(pass,true);
        ((LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null)).set(false);pass.capture(context);
        check(!pass.generate(output),"Disabled isometric FG generated");check(GameAccess.field("viewpoint.render.FrameGenPass","generation",pass)==null,"Off retained model generation");
        IsometricBridge.rendering(null);Graphics.FRAME_GENERATION.set(true);context.previousValid=true;drawScreen(1);pass.capture(context);copyAndGenerate(pass,false);drawScreen(2);pass.capture(context);copyAndGenerate(pass,true);
        Graphics.FRAME_GENERATION.set(false);pass.capture(context);check(!pass.generate(output),"Global disabled generated");
        System.out.println("FrameGen097 lifecycle PASS: native/model/stock mode boundaries reset; independent off releases and does not leak into stock VP.");
    }
    static void failure() {
        ((LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null)).set(true);
        IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);
        pass.capture(null); // Deliberately invalid FG input, within the owned capture boundary.
        check(!IsometricFrameGeneration.available(),"FG error did not stop its own producer");
        check(!(Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null),"FG input error stopped all rendering");
        check(!Graphics.FRAME_GENERATION.get(),"Failed FG continues dispatching");
        IsometricBridge.rendering(null);Graphics.FRAME_GENERATION.set(true);
        check(Graphics.FRAME_GENERATION.get(),"FG failure changed the stock VP setting");Graphics.FRAME_GENERATION.set(false);
        System.out.println("FrameGen097 failure isolation PASS: injected capture error suppresses FG, preserves isometric rendering and stock option.");
    }
    static void copyAndGenerate(FrameGenPass selected,boolean expected) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if(IsometricControls.frameGeneration() && !Boolean.getBoolean("framegen097.nativeOnly")) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,output);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);
            GL30.glBlitFramebuffer(0,0,W,H,0,0,W,H,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            ((zombie.core.textures.TextureDraw.GenericDrawer)GameAccess.field("vpiso.IsometricFrameGeneration","HUDLESS",null)).render();
            check((Boolean)GameAccess.field("viewpoint.render.FrameGenPass","copied",selected),"Production HUD boundary did not capture");
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
        } else selected.copyHudless(output);
        hud();
        boolean accepted=selected.generate(output);check(accepted==expected,"Generated eligibility expected="+expected+" actual="+accepted);
        if(accepted) {
            generated++;FrameGeneration generation=(FrameGeneration)GameAccess.field("viewpoint.render.FrameGenPass","generation",selected);
            float[] pixels=readTexture(generation.generated(),W*H*4,GL11.GL_RGBA);
            double centre=0;int moving=0;
            for(int y=35;y<110;y++)for(int x=15;x<130;x++) {int at=(y*W+x)*4;if(pixels[at]>.6f&&pixels[at+1]<.3f&&pixels[at+2]<.3f){centre+=x;moving++;}}
            check(moving>500,"Moving sprite disappeared in generated frame");centre/=moving;
            check(Math.abs(centre-(56.5+4*drawIndex))<.75,"Generated sprite is not at the intermediate position");
            System.out.println("FrameGen097 moving input="+drawIndex+" generatedCentre="+centre+" currentCentre="+(58.5+4*drawIndex));
            for(int y=20;y<H-20;y+=15)for(int x=35;x<W-30;x+=17){int at=(y*W+x)*4;check(pixels[at+2]>.1f&&pixels[at+2]<.9f,"Generated world blank or corrupt");}
            for(int y=3;y<9;y++)for(int x=3;x<9;x++){int at=(y*W+x)*4;near(pixels[at],1,.025f);near(pixels[at+1],0,.025f);near(pixels[at+2],1,.025f);}
        }
    }
    static void drawScreen(int frameNo) {
        drawIndex=frameNo;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,H);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glClearColor(.2f+frameNo*.005f,.4f,.6f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(40+frameNo*4,50,38,40);GL11.glClearColor(.8f,.2f,.2f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    static void hud(){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,12,12);GL11.glClearColor(1,0,1,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDisable(GL11.GL_SCISSOR_TEST);}
    static void nativeDepth(){GL11.glDepthMask(true);GL11.glClearDepth(.5);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);}
    static int texture(int internal,int format,int w,int h){int t=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    static void upload(int t,int w,int h,int format,int channels,float[] value){float[] data=new float[w*h*channels];for(int i=0;i<w*h;i++)System.arraycopy(value,0,data,i*channels,channels);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,format,GL11.GL_FLOAT,data);}
    static float[] readTexture(int t,int size,int format){GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);float[] p=new float[size];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,format,GL11.GL_FLOAT,p);return p;}
    static void near(float a,float b,float e){check(Float.isFinite(a)&&Math.abs(a-b)<=e,a+" != "+b);}
    static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
}
