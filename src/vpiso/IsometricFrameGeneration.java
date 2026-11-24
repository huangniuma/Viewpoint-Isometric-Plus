package vpiso;

import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.core.View;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameGenPass;
import viewpoint.upscale.UpscaleFrame;
import zombie.core.textures.TextureDraw;

/** Frame-owned native inputs; Viewpoint retains generation, HUD composition and presentation. */
public final class IsometricFrameGeneration {
    private static final String PASS="viewpoint.render.FrameGenPass";
    private static final Class<?>[] NONE=new Class<?>[0], INTS={int.class,int.class,int.class,int.class};
    private static final NativeDrawer[] DRAWERS={new NativeDrawer(),new NativeDrawer(),new NativeDrawer()};
    private static final Hudless HUDLESS=new Hudless();
    private static Object nativePass,producer;
    private static volatile int source=-1;
    private static int depthTexture,vao,width,height;
    private static GlProgram program;
    private static boolean failed,history;
    private static volatile long lastReadyNanos;
    private static long lastNumber=-2,lastNanos;
    private static volatile long epoch;
    private static long usedEpoch=-1;
    private static float lastX,lastY,lastZ,lastScale;
    private static final Matrix4f previous=new Matrix4f(),reprojection=new Matrix4f();
    private static final int[] viewport=new int[4];

    public static final String FRAGMENT="""
        #version 330
        in vec2 uv;
        uniform sampler2D nativeDepth;
        uniform mat4 inverseNative,previousNative;
        uniform vec3 nativeOrigin;
        uniform vec2 clips;
        uniform float distanceToEye;
        layout(location=0) out float depth;
        layout(location=1) out vec2 motion;
        void main() {
          float d=texture(nativeDepth,uv).r;
          if(d>=1.0) {depth=1.0;motion=vec2(0);return;}
          vec3 delta=(inverseNative*vec4(vec3(uv,d)-nativeOrigin,0.0)).xyz;
          vec3 scene=vec3(-delta.x,delta.z*2.4494896,-delta.y);
          float linear=distanceToEye+dot(vec3(0.61237246,-0.5,0.61237246),scene);
          depth=clamp(clips.y*(linear-clips.x)/(linear*(clips.y-clips.x)),0.0,1.0);
          vec4 old=previousNative*vec4(scene,1);
          motion=old.xy/old.w*0.5+0.5-uv;
        }
        """;

    public static boolean rotationCut(float before,float after,boolean generation) {
        if(!generation)return before!=after;
        float delta=RotationCamera.normalizeYaw(after-before);
        return Math.min(delta,360f-delta)>10f;
    }

    public static void newSession() {epoch++;lastReadyNanos=0;}
    public static boolean available() {return !failed;}
    public static void failed(Object pass,Throwable error) {
        failed=true;lastReadyNanos=0;clear(pass);
        System.out.println("[VPISO] frame generation stopped; original rendering remains active:");error.printStackTrace(System.out);
    }

    /** Publish only successful real capture, never the user's toggle alone. */
    public static void captured(Object pass) {
        if(source>0 && !failed && IsometricControls.frameGeneration()
                && GameAccess.field(PASS,"generation",pass)!=null
                && (Boolean)GameAccess.field(PASS,"drawn",pass)) lastReadyNanos=System.nanoTime();
        else if(source>0)lastReadyNanos=0;
    }

    public static int baseLimit(int original,boolean active,int selected) {
        return !active || selected<=0?original:original>0?Math.min(original,selected):selected;
    }

    private static boolean pacing() {
        long ready=lastReadyNanos;
        return ready!=0 && System.nanoTime()-ready<1_000_000_000L && !failed && !View.enabled
                && IsometricControls.frameGeneration() && IsometricControls.frameGenerationRate()>0
                && !(Boolean)GameAccess.field("viewpoint.game.FrameCaps","writingOptions",null)
                && zombie.GameWindow.isIngameState() && !zombie.GameTime.isGamePaused()
                && IsometricBridge.frameGenerationAllowed();
    }
    public static int lockFps(int original) {return baseLimit(original,pacing(),IsometricControls.frameGenerationRate());}
    public static boolean uncapped(boolean original) {return pacing()?false:original;}

    public static void producer(Object pass,int kind) {
        if(pass!=producer || kind!=source || usedEpoch!=epoch) {
            if(producer!=null) {
                GameAccess.set(PASS,"joined",producer,false);
                if(source>0 && producer!=pass) clear(producer);
            }
            GameAccess.set(PASS,"joined",pass,false);
            history=false;
        }
        producer=pass;source=kind;usedEpoch=epoch;
        GameAccess.set(PASS,"made",null,pass);
    }

    public static void queueNative(Frame frame,NativeReceiver receiver,Object sprites,int index) {
        NativeDrawer drawer=DRAWERS[index];drawer.frame=frame;
        drawer.receiver.inverse.set(receiver.inverse);
        System.arraycopy(receiver.origin,0,drawer.receiver.origin,0,3);
        drawer.receiver.worldZ=receiver.worldZ;
        GameAccess.call("zombie.core.SpriteRenderer","drawGeneric",sprites,
                new Class<?>[]{GameAccess.type("zombie.core.textures.TextureDraw$GenericDrawer")},drawer);
    }

    public static void queueHudless() {
        if(View.enabled || (!IsometricControls.frameGeneration() && source<=0))return;
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        GameAccess.call("zombie.core.SpriteRenderer","drawGeneric",sprites,
                new Class<?>[]{GameAccess.type("zombie.core.textures.TextureDraw$GenericDrawer")},HUDLESS);
    }

    private static void clear(Object pass) {
        lastReadyNanos=0;
        GameAccess.call(PASS,"release",pass,NONE);
        GameAccess.set(PASS,"drawn",pass,false);GameAccess.set(PASS,"copied",pass,false);
    }

    private static void nativeFrame(Frame frame,NativeReceiver receiver) throws ReflectiveOperationException {
        FrameGenPass.midframe();
        if(nativePass==null) {
            var constructor=GameAccess.type(PASS).getDeclaredConstructor(GameAccess.type("viewpoint.render.VendorInputs"));
            constructor.setAccessible(true);nativePass=constructor.newInstance(new Object[]{null});
        }
        producer(nativePass,2);
        GameAccess.set(PASS,"drawn",nativePass,false);GameAccess.set(PASS,"copied",nativePass,false);
        if(!(Boolean)GameAccess.call(PASS,"runs",nativePass,NONE))return;
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if(viewport[2]<=0 || viewport[3]<=0 || GL11.glGetInteger(GL11.GL_DEPTH_BITS)==0) {clear(nativePass);history=false;return;}
        int[] screen=(int[])GameAccess.call(PASS,"screenSize",nativePass,NONE);
        if(screen[0]<=0 || screen[1]<=0)return;
        int output=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GameAccess.call(PASS,"fit",nativePass,INTS,screen[0],screen[1],viewport[2],viewport[3]);
        if(depthTexture==0) depthTexture=GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthTexture);GL33.glBindSampler(0,0);
        boolean resized=width!=viewport[2] || height!=viewport[3];
        if(resized) {
            width=viewport[2];height=viewport[3];
            int unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            try {GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH_COMPONENT32F,width,height,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,0L);}
            finally {GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);}
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,output);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
        Matrix4f current=NativeSurfaces.projection(receiver);
        float scale=(float)Math.sqrt(current.m01()*current.m01()+current.m11()*current.m11()+current.m21()*current.m21());
        long now=System.nanoTime();
        boolean reset=!history || resized || frame.number!=lastNumber+1 || now-lastNanos>250_000_000L
                || Math.abs(frame.camX-lastX)+Math.abs(frame.camY-lastY)+Math.abs(frame.camZ-lastZ)>4f
                || Math.abs(scale-lastScale)>.005f*scale;
        if(reset)reprojection.set(current);
        else reprojection.set(previous).translate(lastX-frame.camX,(frame.camZ-lastZ)*2.4494896f,lastY-frame.camY);
        if(program==null) {
            program=GlProgram.fromSources("Isometric frame generation inputs",new GlProgram.Sources(NativeVision.VERTEX,null,null,null,FRAGMENT),Map.of(),Map.of(),List.of("native depth and camera motion"));
            vao=GL30.glGenVertexArrays();
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,((Number)GameAccess.field(PASS,"inputsFbo",nativePass)).intValue());
        GL11.glViewport(0,0,width,height);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);GL11.glColorMask(true,true,true,true);
        program.use();program.setInt("nativeDepth",0);program.set("inverseNative",receiver.inverse);
        program.set("nativeOrigin",receiver.origin[0],receiver.origin[1],receiver.origin[2]);program.set("previousNative",reprojection);
        program.set("distanceToEye",IsoCameraMath.DISTANCE);program.set("clips",IsoCameraMath.NEAR,IsoCameraMath.FAR);
        GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        NativeFrameMotion.draw(frame,receiver,depthTexture,((Number)GameAccess.field(PASS,"inputsFbo",nativePass)).intValue(),
                width,height,current,previous,reset);
        float frameMs=lastNanos==0?1000f/60f:Math.max(1f,Math.min(250f,(now-lastNanos)/1_000_000f));
        float[] eye=new float[3];Matrix4f lens=new Matrix4f(),view=new Matrix4f();
        IsoCameraMath.matrices(new IsoCameraMath.Fit(frame.camX,frame.camY,1f/scale),frame.camX,frame.camY,width,height,lens,view,eye);
        float[] camera={eye[0],eye[1],eye[2],view.m01(),view.m11(),view.m21(),view.m00(),view.m10(),view.m20(),-view.m02(),-view.m12(),-view.m22()};
        UpscaleFrame described=new UpscaleFrame(0,0,width,height,reset,frameMs,IsoCameraMath.NEAR,IsoCameraMath.FAR,
                2f*(float)Math.atan(1f/lens.m11()),1.2247449f,0,new float[64],camera);
        GameAccess.set(PASS,"described",nativePass,described);GameAccess.set(PASS,"drawn",nativePass,true);
        captured(nativePass);
        previous.set(current);lastX=frame.camX;lastY=frame.camY;lastZ=frame.camZ;lastScale=scale;
        lastNumber=frame.number;lastNanos=now;history=true;
    }

    private static final class Hudless extends TextureDraw.GenericDrawer {
        @Override public void render() {
            if(failed || !IsometricControls.frameGeneration() || !IsometricBridge.frameGenerationAllowed()) {
                if(source>0 && producer!=null) {clear(producer);producer=null;source=-1;}
                history=false;return;
            }
            FrameGenPass.hudless();
        }
    }

    private static final class NativeDrawer extends TextureDraw.GenericDrawer {
        private Frame frame;
        private final NativeReceiver receiver=new NativeReceiver();
        @Override public void render() {
            if(failed || !IsometricControls.frameGeneration() || !IsometricBridge.frameGenerationAllowed())return;
            int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int currentProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0);
            Object cached=GameAccess.field("zombie.core.ShaderHelper","currentlyBound",null);
            Object lastTexture=GameAccess.field("zombie.core.textures.Texture","lastTextureID",null);
            boolean defaultShader=(Boolean)GameAccess.field("zombie.core.DefaultShader","isActive",null);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
            try {nativeFrame(frame,receiver);}
            catch(Throwable error) {
                if(nativePass!=null)failed(nativePass,error);
                else {failed=true;System.out.println("[VPISO] frame generation stopped: "+error);}
            } finally {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
                GL30.glBindVertexArray(array);GL11.glPopClientAttrib();GL11.glPopAttrib();GL20.glUseProgram(currentProgram);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL33.glBindSampler(0,sampler);GL13.glActiveTexture(active);
                GameAccess.set("zombie.core.ShaderHelper","currentlyBound",null,cached);
                GameAccess.set("zombie.core.DefaultShader","isActive",null,defaultShader);
                GameAccess.set("zombie.core.textures.Texture","lastTextureID",null,lastTexture);
                Object ring=GameAccess.field("zombie.core.SpriteRenderer","ringBuffer",null);
                GameAccess.set(ring.getClass().getName(),"restoreVbos",ring,true);
                GameAccess.set(ring.getClass().getName(),"restoreBoundTextures",ring,true);
                GameAccess.staticCall("zombie.core.opengl.GLStateRenderThread","startFrame");
            }
        }
    }
    private IsometricFrameGeneration() {}
}
