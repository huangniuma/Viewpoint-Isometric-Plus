package vpiso;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;

/** Supplies posed-object motion at native-art pixels without drawing model colour. */
public final class NativeFrameMotion {
    private static final int DEPTH_UNIT=140;
    private static final Class<?>[] NONE=new Class<?>[0];
    private static GlProgram model,vehicle,resolve;
    private static Object owner;
    private static Object modelOwner,motionModels;
    private static int depth,motion,target,vao,width,height;
    private static final String OUTPUT="""
        in vec4 vClip, vPrevClip;
        flat in int vMotion;
        uniform sampler2D vpIsoMotionDepth;
        uniform mat4 vpIsoMotionInverse;
        uniform vec3 vpIsoMotionOrigin;
        uniform vec2 vpIsoMotionSize;
        layout(location=0) out vec4 vpIsoObject;
        void main() {
          vpIsoMotionOriginal();
          vec2 uv=gl_FragCoord.xy/vpIsoMotionSize;
          float d=texture(vpIsoMotionDepth,uv).r;
          if(d>=1.0 || d<=0.0) discard;
          vec3 delta=(vpIsoMotionInverse*vec4(vec3(uv,d)-vpIsoMotionOrigin,0.0)).xyz;
          vec3 nativePos=vec3(-delta.x,delta.z*2.4494896,-delta.y);
          if(distance(nativePos,vPos)>1.25) discard;
          if(vMotion==0) discard;
          // Model history is already in its previous camera-relative space.
          // Do not apply the world's camera-origin translation a second time.
          vpIsoObject=vec4((vPrevClip.xy/vPrevClip.w-vClip.xy/vClip.w)*0.5,
              dot(vec3(0.61237246,-0.5,0.61237246),vPos),1);
        }
        """;

    private static final String RESOLVE="""
        #version 330
        in vec2 uv;
        uniform sampler2D objects;
        uniform vec2 clips;
        layout(location=0) out float depth;
        layout(location=1) out vec2 motion;
        void main() {
          ivec2 size=textureSize(objects,0),pixel=ivec2(uv*vec2(size));
          vec4 best=vec4(0);
          // Native sprite antialiasing extends beyond its opaque model core.
          // Keep the nearest moving surface's vector across that narrow edge.
          for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++) {
            vec4 value=texelFetch(objects,clamp(pixel+ivec2(x,y),ivec2(0),size-1),0);
            if(value.w>.5 && (best.w<.5 || value.z<best.z))best=value;
          }
          if(best.w<.5)discard;
          float linear=6000.0+best.z;
          depth=clamp(clips.y*(linear-clips.x)/(linear*(clips.y-clips.x)),0.0,1.0);
          motion=best.xy;
        }
        """;

    static void draw(Frame frame,NativeReceiver receiver,int nativeDepth,int fbo,int w,int h,
                     Matrix4f current,Matrix4f previous,boolean reset) {
        if(frame.scene.models.count()==0)return;
        WorldRenderer.prepare(Long.MAX_VALUE);
        if(!(Boolean)GameAccess.field("viewpoint.render.WorldRenderer","ready",null))return;
        GameAccess.staticCall("viewpoint.render.PackLinks","follow");
        Object worldModels=GameAccess.field("viewpoint.render.WorldRenderer","models",null);
        // Shadow/snow passes may prepare the same frame repeatedly. Own history
        // keeps those passes from replacing previous poses with the current pose.
        if(modelOwner!=worldModels) {
            try {
                var constructor=GameAccess.type("viewpoint.render.ModelPass").getDeclaredConstructor(GameAccess.type("viewpoint.render.Targets"));
                constructor.setAccessible(true);
                motionModels=constructor.newInstance(GameAccess.field("viewpoint.render.WorldRenderer","targets",null));
                modelOwner=worldModels;
            } catch(ReflectiveOperationException error) {throw new IllegalStateException("Native motion model pass",error);}
        }
        Object models=motionModels;
        boolean prepared=false;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] units=new int[40],samplers=new int[40];
        for(int i=0;i<38;i++)units[i]=i<19?i:i+45;
        units[38]=DEPTH_UNIT;units[39]=DEPTH_UNIT+1;
        for(int i=0;i<units.length;i++)samplers[i]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,units[i]);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH_UNIT);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH_UNIT+1);
        int objectBinding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(active);
        IsometricBridge.rendering(frame);
        try {
            GameAccess.staticCall("viewpoint.render.FrameStream","frameStarted");
            GameAccess.call("viewpoint.render.FarRelief","frame",null,new Class<?>[]{SceneData.class},frame.scene);
            GameAccess.call("viewpoint.render.ModelPass","prepare",models,
                    new Class<?>[]{SceneData.class,long.class,float.class,float.class,float.class,float.class},
                    frame.scene,frame.number,1f,0f,0f,0f);
            prepared=true;
            ensure(models,w,h);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0,0,w,h);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClearColor(0,0,0,0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_COLOR_BUFFER_BIT);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,nativeDepth);GL33.glBindSampler(DEPTH_UNIT,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            for(GlProgram program:new GlProgram[]{model,vehicle}) {
                program.use();program.set("uViewProjection",current);program.set("uPlainViewProjection",current);
                program.set("uPrevViewProjection",reset?current:previous);program.setInt("uMotionPass",reset?0:1);
                program.set("uFadeNoise",0f);program.set("vpIsoMotionInverse",receiver.inverse);
                program.set("vpIsoMotionOrigin",receiver.origin[0],receiver.origin[1],receiver.origin[2]);
                program.set("vpIsoMotionSize",(float)w,(float)h);
                if(program==model)for(boolean rigid:new boolean[]{false,true})
                    GameAccess.call("viewpoint.render.ModelPass","iris",models,
                            new Class<?>[]{GlProgram.class,Matrix4f.class,float.class,boolean.class},program,current,0f,rigid);
                else GameAccess.call("viewpoint.render.ModelPass","irisVehicles",models,
                        new Class<?>[]{GlProgram.class,boolean.class},program,false);
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1});
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH_UNIT+1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,motion);GL33.glBindSampler(DEPTH_UNIT+1,0);
            resolve.use();resolve.set("clips",IsoCameraMath.NEAR,IsoCameraMath.FAR);
            if(vao==0)vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1});
            if(prepared)GameAccess.call("viewpoint.render.ModelPass","endFrame",models,NONE);
            for(int i=0;i<units.length;i++)GL33.glBindSampler(units[i],samplers[i]);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH_UNIT);GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH_UNIT+1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,objectBinding);
            GL13.glActiveTexture(active);
            IsometricBridge.rendering(null);
        }
    }

    private static void ensure(Object models,int w,int h) {
        if(owner!=GlProgram.pack()) {
            if(model!=null){model.delete();vehicle.delete();resolve.delete();}
            model=program("model_shadow.frag",GameAccess.field("viewpoint.render.ModelPass","shadow",models));
            Object cars=GameAccess.field("viewpoint.render.ModelPass","vehicles",models);
            vehicle=program("vehicle_shadow.frag",GameAccess.field("viewpoint.render.VehiclePass","shadow",cars));
            resolve=GlProgram.fromSources("Native motion edges",new GlProgram.Sources(NativeVision.VERTEX,null,null,null,RESOLVE),Map.of(),Map.of(),List.of("native motion edges"));
            resolve.sampler("objects",DEPTH_UNIT+1);
            owner=GlProgram.pack();
        }
        if(depth==0)depth=GL11.glGenTextures();
        if(width!=w || height!=h) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
            int unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            try {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH_COMPONENT32F,w,h,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,0L);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                if(motion==0)motion=GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,motion);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                if(target==0)target=GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,motion,0);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Native motion framebuffer incomplete");
                width=w;height=h;
            } finally {GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);}
        }
    }

    @SuppressWarnings("unchecked")
    private static GlProgram program(String fragment,Object original) {
        String vertex=expand("model.vert");
        String source=expand(fragment).replace("void main()","void vpIsoMotionOriginal()")+OUTPUT;
        if(fragment.equals("vehicle_shadow.frag"))source=source.replace("in vec4 vClip, vPrevClip;","in vec3 vPos;\nin vec4 vClip, vPrevClip;");
        GlProgram program=GlProgram.fromSources("Native frame motion "+fragment,
                new GlProgram.Sources(vertex,null,null,null,source),Map.of(),Map.of(),List.of("native posed-object motion"));
        ((Map<String,Integer>)GameAccess.field("viewpoint.platform.GlProgram","samplers",original)).forEach(program::sampler);
        program.sampler("vpIsoMotionDepth",DEPTH_UNIT);
        return program;
    }
    private static String expand(String path) {
        return (String)GameAccess.call("vpiso.NativeSurfaces","expand",null,new Class<?>[]{String.class,java.util.Set.class},path,new HashSet<>());
    }
    private NativeFrameMotion() {}
}
