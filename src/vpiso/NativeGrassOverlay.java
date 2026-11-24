package vpiso;

import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;

/** Draws VP's real generated blades into native colour/depth, without replacing the native world. */
public final class NativeGrassOverlay {
    private static final String PASS="viewpoint.render.GrassPass";
    private static final Class<?>[] NONE={};
    private static final int BASE_UNIT=143;
    private static final int[] UNITS={BASE_UNIT,144,145};
    private static NativeGrassOverlay active;
    public static final String GLSL="""
        uniform float vpIsoNativeGrass;
        uniform sampler2D vpIsoGrassBase;
        uniform sampler2D vpIsoGrassFloor, vpIsoGrassOcclusion;
        uniform float vpIsoGrassMasked;
        uniform vec4 vpIsoGrassScreen;
        layout(location=7) out vec4 vpIsoGrassGround;
        layout(location=6) out vec4 vpIsoGrassMetadata;
        """;
    public static final String OUTPUT="""
        if (vpIsoNativeGrass > 0.5) {
          vec2 uv=(gl_FragCoord.xy-vpIsoGrassScreen.xy)/vpIsoGrassScreen.zw;
          vec4 base=texture(vpIsoGrassBase,uv);
          // The displayed pixel includes the floor's material detail, not just light.
          // Use VP's captured root light; sample native colour only for blackout.
          if (max(base.r,max(base.g,base.b)) < .004) discard;
          vec3 grass=gAlbedo.rgb * clamp(vLight.rgb,0.0,1.0);
          if(vpIsoGrassMasked > .5) {
            vec4 owner=texture(vpIsoGrassOcclusion,uv);
            vec2 mean=owner.rg/max(owner.a,1.0e-6),point=round(mean);
            float variance=abs(owner.b/max(owner.a,1.0e-6)-dot(mean,mean));
            bool pure=all(lessThan(abs(mean-point),vec2(.002))) && variance<.25;
            bool blade=pure && point==vec2(-1,0);
            float blocked=owner.r;
            if(owner.r<0.0) {
              // Pure source over transparency has a known coverage. Mixed
              // foreground has no safe split for inserting another grass layer.
              blocked=blade?1.0:pure?owner.a:1.0;
              // Preserve the native level-zero wall/floor fade. Its X=-1
              // identifies coverage; both remaining moments must match that
              // wall. A source with a different X cannot pass this check.
              float weight=-owner.r,wall=owner.g/max(weight,1.0e-6);
              if(!pure && weight<=owner.a && abs(wall-round(wall))<.002
                  && wall>=1.0 && wall<1024.0
                  && abs(owner.b/weight-1.0-wall*wall)<.25)blocked=weight;
            }
            // The native ground contribution now contains floors only. Walls and
            // furniture remain in base-floor, including their translucent edges.
            float open=1.0-clamp(blocked,0.0,1.0);
            if(open < .004) discard;
            // Native sprites may share the floor's depth. Keep their original
            // premultiplied RGB, including translucent leaf/furniture edges.
            vec3 floor=texture(vpIsoGrassFloor,uv).rgb;
            grass*=open;
            gAlbedo=vec4(max(base.rgb-floor,vec3(0.0))+grass,base.a);
            // The existing native shadow compositor must receive the grass
            // colour at its actual blade depth, after this geometry pass.
            vpIsoGrassGround=vec4(grass,base.a);
            // Blades have real native depth, rather than a flat floor owner.
            vpIsoGrassMetadata=vec4(-1.0,0.0,1.0,1.0);
          } else gAlbedo=vec4(grass,base.a);
        }
        """;
    private final FrameContext context=new FrameContext();
    private final int[] viewport=new int[4];
    private int color,ground,occlusion,width,height;
    private boolean masked;
    private long previousDraw;

    public static void onUse(GlProgram program) {
        if (!program.has("vpIsoNativeGrass")) return;
        program.set("vpIsoNativeGrass",active==null?0f:1f);
        if (active!=null) {
            int unit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int[] textures={active.color,active.ground,active.occlusion};
            for(int i=0;i<UNITS.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);GL33.glBindSampler(UNITS[i],0);
            }
            GL13.glActiveTexture(unit);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);
            program.setInt("vpIsoGrassBase",BASE_UNIT);
            program.setInt("vpIsoGrassFloor",UNITS[1]);program.setInt("vpIsoGrassOcclusion",UNITS[2]);
            program.set("vpIsoGrassMasked",active.masked?1f:0f);
            program.set("vpIsoGrassScreen",(float)active.viewport[0],(float)active.viewport[1],
                    (float)active.width,(float)active.height);
        }
    }

    /** Caller owns native GL attributes, caches, and retirement. */
    public void draw(Frame frame,NativeReceiver receiver) {
        draw(frame,receiver,0,0);
    }
    public void draw(Frame frame,NativeReceiver receiver,int floorTexture,int occlusionTexture) {
        if (viewpoint.render.Grass.reach()==0) return;
        if (!IsometricShaders.supports(ShaderPacks.active().pack.id)) return;
        WorldRenderer.prepare(Long.MAX_VALUE);
        if (!(Boolean)GameAccess.field("viewpoint.render.WorldRenderer","ready",null)) return;
        GameAccess.staticCall("viewpoint.render.PackLinks","follow");
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if (viewport[2]<=0 || viewport[3]<=0 || GL11.glGetInteger(GL11.GL_DEPTH_BITS)==0) return;
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] previousTextures=new int[UNITS.length],previousSamplers=new int[UNITS.length];
        for(int i=0;i<UNITS.length;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
            previousTextures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            previousSamplers[i]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,UNITS[i]);
        }
        int[] buffers=new int[Math.min(8,GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS))];
        for(int i=0;i<buffers.length;i++)buffers[i]=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i);
        int ssbo=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int[] storage={GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,0),GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,1)};
        long[] storageStart={GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,0),GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,1)};
        long[] storageSize={GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,0),GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,1)};
        int indirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int copyRead=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER),copyWrite=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        int[] image=new int[6];
        int[] keys={GL42.GL_IMAGE_BINDING_NAME,GL42.GL_IMAGE_BINDING_LEVEL,GL42.GL_IMAGE_BINDING_LAYERED,
                GL42.GL_IMAGE_BINDING_LAYER,GL42.GL_IMAGE_BINDING_ACCESS,GL42.GL_IMAGE_BINDING_FORMAT};
        for(int i=0;i<keys.length;i++)image[i]=GL30.glGetIntegeri(keys[i],0);
        boolean previousCull=WorldRenderer.cullFromPlayer;
        Object models=GameAccess.field("viewpoint.render.WorldRenderer","models",null);
        boolean prepared=false;
        IsometricBridge.rendering(frame);
        active=this;
        masked=floorTexture!=0 && occlusionTexture!=0;
        try {
            ensure();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);
            int buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            try {
                if (draw!=0) GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
                if(masked) {
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+7);GL11.glBindTexture(GL11.GL_TEXTURE_2D,ground);
                    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+6);GL11.glBindTexture(GL11.GL_TEXTURE_2D,occlusion);
                    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
                }
            } finally { GL11.glReadBuffer(buffer); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read); }
            context.scene=frame.scene;context.pipeline=ShaderPacks.active().pipeline;
            context.width=context.outputWidth=width;context.height=context.outputHeight=height;
            context.halfWidth=(width+1)/2;context.halfHeight=(height+1)/2;
            context.view=new Matrix4f();context.eyeX=0;context.eyeY=1.5f;context.eyeZ=0;
            context.viewProjection.set(NativeSurfaces.projection(receiver));
            context.invViewProjection.set(context.viewProjection).invert();
            context.jitteredProjection.set(context.viewProjection);
            context.time=frame.scene.time;context.cloudTime=frame.scene.cloudTime;
            GameAccess.set("viewpoint.render.FrameContext","index",context,frame.number);
            long now=System.nanoTime();
            float seconds=previousDraw==0?0f:Math.min(.1f,Math.max(0f,(now-previousDraw)*1e-9f));
            previousDraw=now;
            GameAccess.set("viewpoint.render.FrameContext","frameSeconds",context,seconds);
            WorldRenderer.cullFromPlayer=false;
            GameAccess.staticCall("viewpoint.render.FrameStream","frameStarted");
            GameAccess.call("viewpoint.render.FarRelief","frame",null,new Class<?>[]{SceneData.class},frame.scene);
            GameAccess.call("viewpoint.render.FloorBakes","update",GameAccess.field("viewpoint.render.WorldRenderer","floors",null),
                    new Class<?>[]{SceneData.class,boolean.class},frame.scene,false);
            GameAccess.call("viewpoint.render.PackModels","upload",null,new Class<?>[]{long.class},System.nanoTime());
            GameAccess.call("viewpoint.render.Meshes","prepare",null,new Class<?>[]{SceneData.class},frame.scene);
            GameAccess.call("viewpoint.render.NearLight","update",GameAccess.field("viewpoint.render.WorldRenderer","nearLight",null),
                    new Class<?>[]{SceneData.class},frame.scene);
            GameAccess.staticCall("viewpoint.render.TilePalette","update");
            GameAccess.staticCall("viewpoint.render.TilePalette","bind");
            GameAccess.call("viewpoint.render.ModelPass","prepare",models,
                    new Class<?>[]{SceneData.class,long.class,float.class,float.class,float.class,float.class},frame.scene,frame.number,1f,0f,0f,0f);
            prepared=true;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,draw);
            int[] targets=new int[buffers.length];targets[0]=draw==0?buffers[0]:GL30.GL_COLOR_ATTACHMENT0;
            if(masked) { targets[7]=GL30.GL_COLOR_ATTACHMENT0+7; targets[6]=GL30.GL_COLOR_ATTACHMENT0+6; }
            GL20.glDrawBuffers(targets);
            GL11.glViewport(viewport[0],viewport[1],width,height);
            GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);
            GL11.glColorMask(true,true,true,true);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+BASE_UNIT);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL33.glBindSampler(BASE_UNIT,0);
            Object surfaces=GameAccess.field("viewpoint.render.WorldRenderer","surfaces",null);
            Object grass=GameAccess.field("viewpoint.render.SurfacePass","grass",surfaces);
            GameAccess.call(PASS,"draw",grass,new Class<?>[]{FrameContext.class,GameAccess.type("viewpoint.render.Targets")},
                    context,GameAccess.field("viewpoint.render.WorldRenderer","targets",null));
        } finally {
            if (prepared) GameAccess.call("viewpoint.render.ModelPass","endFrame",models,NONE);
            active=null;IsometricBridge.rendering(null);WorldRenderer.cullFromPlayer=previousCull;
            for(int i=0;i<2;i++) {
                if(storage[i]!=0 && storageSize[i]>0)GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,storage[i],storageStart[i],storageSize[i]);
                else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,storage[i]);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,ssbo);
            GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,indirect);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,copyRead);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,copyWrite);
            GL42.glBindImageTexture(0,image[0],image[1],image[2]!=0,image[3],image[4],image[5]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL20.glDrawBuffers(buffers);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<UNITS.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,previousTextures[i]);GL33.glBindSampler(UNITS[i],previousSamplers[i]);
            }
            GL13.glActiveTexture(previousActive);
        }
    }

    private void ensure() {
        if(color==0) color=GL11.glGenTextures();
        if(ground==0) ground=GL11.glGenTextures();
        if(occlusion==0) occlusion=GL11.glGenTextures();
        if(width==viewport[2] && height==viewport[3])return;
        width=viewport[2];height=viewport[3];
        for(int texture:new int[]{color,ground,occlusion}) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,texture==occlusion?GL30.GL_RGBA32F:GL11.GL_RGBA8,
                    width,height,0,GL11.GL_RGBA,texture==occlusion?GL11.GL_FLOAT:GL11.GL_UNSIGNED_BYTE,0L);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }
    }
}
