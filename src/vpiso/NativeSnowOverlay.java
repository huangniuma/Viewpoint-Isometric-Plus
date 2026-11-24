package vpiso;

import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;

/** Draws VP's real accumulated snow and contact tracks into native colour/depth, without replacing the native world. */
public final class NativeSnowOverlay {
    private static final String PASS="viewpoint.render.SnowPass";
    private static final Class<?>[] NONE={};
    private static final int BASE_UNIT=143;
    private static final int[] UNITS={0,5,9,10,11,12,16,17,18,21,30,41,48,49,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,84,95,96,97,98,101,102,103,109,110,111,113,123,130,131,133,134,135,136,137,138,139,140,141,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159};
    private static NativeSnowOverlay active;
    private static NativeSnowOverlay preparing;
    public static final String GLSL="""
        #include "lib/floor_plan.glsl"
        uniform float vpIsoNativeSnow;
        uniform sampler2D vpIsoSnowBase, vpIsoSnowFloor, vpIsoSnowOcclusion;
        uniform sampler2D vpIsoSnowLevels;
        uniform sampler2D vpIsoSnowActors,vpIsoSnowRegistry,vpIsoSnowOriginalDepth;
        uniform sampler2D vpIsoSnowActorFloors;
        uniform float vpIsoSnowDepthWorld;
        uniform mat4 vpIsoSnowProjection,vpIsoSnowInverse;
        uniform float vpIsoSnowLevelsOn;
        uniform vec4 vpIsoSnowScreen;
        uniform vec2 vpIsoSnowRaster;
        layout(location=7) out vec4 vpIsoSnowGround;
        layout(location=6) out vec4 vpIsoSnowMetadata;
        bool vpIsoSnowBelow(vec4 point,vec3 top,vec3 under) {
          // Ownership already supplies the floor. Sprite skirts and model
          // soles may sit just below it; they still belong to that floor.
          if(abs(point.a)<.5 || point.y>=top.y-.003)return false;
          vec4 clip=vpIsoSnowProjection*vec4(point.xyz,1);
          return clip.z/clip.w*.5+.5>gl_FragCoord.z+1.0e-6;
        }
        bool vpIsoSnowArtBelow(vec4 source,vec2 uv,vec3 top,vec3 under) {
          if(source.w<3.0 || source.w>5.0)return false;
          vec3 root=vec3(-source.x-uSceneOrigin.x,source.z*2.4494896-uSceneOrigin.y,-source.y-uSceneOrigin.z);
          // The visible native pixel lies on its own camera-facing sprite
          // plane. Intersect that ray exactly; never clamp to a mesh bounding
          // box or borrow a neighbouring object's geometry.
          vec4 rayPoint=vpIsoSnowInverse*vec4(uv*2.0-1.0,0,1);
          vec3 at=rayPoint.xyz/rayPoint.w;
          vec3 ray=normalize(vpIsoSnowInverse[2].xyz);
          float divisor=dot(ray.xz,ray.xz);
          if(divisor<1.0e-8)return false;
          at+=ray*dot(root.xz-at.xz,ray.xz)/divisor;
          return vpIsoSnowBelow(vec4(at,1),top,under);
        }
        bool vpIsoSnowBuried(vec4 owner,vec2 uv,vec3 top,vec3 under) {
          // Model-only receivers cannot be supplied by a nearby tile mesh.
          // Limit anonymous native actor pixels to the actual posed model.
          vec2 mean=owner.rg/max(owner.a,1.0e-6),code=round(mean);
          vec4 actor=texture(vpIsoSnowActors,uv);
          if(owner.a>.999 && all(lessThan(abs(mean-vec2(1,0)),vec2(.002)))
              && abs(owner.b/owner.a-1.0)<.002
              && texture(vpIsoSnowActorFloors,uv).a==float(planLevelAt(under))
              && vpIsoSnowBelow(actor,top,under)) {
            vec4 clip=vpIsoSnowProjection*vec4(actor.xyz,1);
            // Native ModelInstance.targetDepth biases posed models forward by
            // 1e-4; compare the same depth rather than expanding the tolerance.
            float difference=abs(clip.z/clip.w*.5+.5-1.0e-4-texture(vpIsoSnowOriginalDepth,uv).r);
            if(difference*vpIsoSnowDepthWorld<.04)return true;
          }
          if(any(greaterThan(abs(mean-code),vec2(.002)))
              || abs(owner.b/max(owner.a,1.0e-6)-dot(code,code))>.25
              || code.x> -2.0 || code.x< -257.0 || code.y<0.0 || code.y>255.0)return false;
          int id=int(-code.x-2.0+256.0*code.y);
          if(id<2)return false;
          vec4 source=texelFetch(vpIsoSnowRegistry,ivec2(id&1023,1+(id>>10)),0);
          if(source.z!=float(planLevelAt(under)))return false;
          // The displayed shape is native sprite art. Its height comes from
          // that sprite's own plane, including illustration beyond a VP proxy.
          return vpIsoSnowArtBelow(source,uv,top,under);
        }
        void vpIsoSnowOutput(vec3 normal, vec4 light, vec3 under) {
          if (vpIsoNativeSnow < 0.5) return;
          vec2 uv=(gl_FragCoord.xy-vpIsoSnowRaster)/vpIsoSnowScreen.zw;
          vec4 base=texture(vpIsoSnowBase,uv),owner=texture(vpIsoSnowOcclusion,uv);
          vec3 levels=vec3(0);
          float floorCoverage=-1.0;
          if(vpIsoSnowLevelsOn>.5) {
            // This mask stays attached to the native FBO, whose allocation can
            // exceed its viewport. The colour/owner copies above are cropped.
            levels=texelFetch(vpIsoSnowLevels,ivec2(gl_FragCoord.xy-vpIsoSnowRaster+vpIsoSnowScreen.xy),0).rgb;
            floorCoverage=clamp(levels.z,0.0,1.0);
          }
          // Remove only the known floor contribution. A remaining single
          // source can then be identified even along its antialiased edge.
          vec4 subject=owner;
          if(owner.r< -1.0 && floorCoverage>0.0)
            subject-=vec4(0,levels.x,levels.y,floorCoverage);
          bool buried=subject.a>.003 && vpIsoSnowBuried(subject,uv,vPos,under);
          if (max(base.r,max(base.g,base.b)) < .004 && !buried) discard;
          if(vpIsoSnowLevelsOn>.5) {
            if(levels.z<.004 && !buried)discard;
            float level=levels.x/max(levels.z,1.0e-6);
            if(levels.z>=.004 && (abs(level-round(level))>.01 || abs(levels.y/levels.z-level*level)>.01
                || abs(float(planLevelAt(under))-level)>.01))discard;
          }
          // The original artwork's floor and foreground can share exactly the
          // same depth. Retain opaque furniture, actors, walls and alpha edges.
          float blocked=clamp(owner.r,0.0,1.0);
          if(owner.r<0.0) {
            vec2 mean=owner.rg/max(owner.a,1.0e-6),point=round(mean);
            float variance=abs(owner.b/max(owner.a,1.0e-6)-dot(mean,mean));
            bool pure=all(lessThan(abs(mean-point),vec2(.002))) && variance<.25;
            blocked=pure && point!=vec2(-1,0)?owner.a:1.0;
            float weight=-owner.r,wall=owner.g/max(weight,1.0e-6);
            if(!pure && weight<=owner.a && abs(wall-round(wall))<.002
                && wall>=1.0 && wall<1024.0
                && abs(owner.b/weight-1.0-wall*wall)<.25)blocked=weight;
          }
          float open=1.0-blocked;
          // Floor moments retain the actual background share through native
          // alpha blending even when several foreground owner codes are mixed.
          if(floorCoverage>=0.0)open=floorCoverage;
          if(buried)open=1.0;
          if(open<.004)discard;
          vec3 floorColor=buried?base.rgb:texture(vpIsoSnowFloor,uv).rgb;
          if(vpIsoNativeSnow>1.5){vpIsoSnowGround=vec4(floorColor,open);return;}
          vec3 n=normalize(normal);
          float facing=.72+.28*max(n.y,0.0);
          vec3 snow=gAlbedo.rgb*clamp(light.rgb,0.0,1.0)*facing*open;
          gAlbedo=vec4(max(base.rgb-floorColor,vec3(0.0))+snow,base.a);
          vpIsoSnowGround=vec4(snow,base.a);
          vpIsoSnowMetadata=vec4(-1.0,0.0,1.0,1.0);
        }
        """;
    private final FrameContext context=new FrameContext();
    private final NativeSnowMaterial material=new NativeSnowMaterial();
    private final NativeSurfaces actors=new NativeSurfaces(true);
    private final java.nio.FloatBuffer sourcePixels=org.lwjgl.BufferUtils.createFloatBuffer(NativeGround.WALL_OWNERS*4+NativeGround.SOURCE_OWNERS*12);
    private long sourceRevision=-1;
    private final int[] viewport=new int[4];
    private int color,ground,occlusion,width,height,levels,nativeDepth,sourceRegistry;
    private boolean masked;
    private NativeReceiver receiver;
    private long previousDraw;

    /** Called for the selected pack's snow layer and vehicle wake programs. */
    public static void onUse(GlProgram program) {
        if(preparing!=null) {
            GameAccess.call("viewpoint.render.FrameUniforms","apply",null,
                    new Class<?>[]{GlProgram.class,FrameContext.class},program,preparing.context);
            GameAccess.call("viewpoint.render.FrameUniforms","units",null,new Class<?>[]{GlProgram.class},program);
        }
        if (!program.has("vpIsoNativeSnow")) return;
        program.set("vpIsoNativeSnow",active==null?0f:2f);
        program.setInt("vpIsoSnowBase",143);program.setInt("vpIsoSnowFloor",144);program.setInt("vpIsoSnowOcclusion",145);
        program.setInt("vpIsoSnowLevels",146);
        program.setInt("vpIsoSnowActorFloors",133);
        program.setInt("vpIsoSnowActors",137);program.setInt("vpIsoSnowRegistry",138);program.setInt("vpIsoSnowOriginalDepth",139);
        program.set("vpIsoSnowLevelsOn",active!=null && active.levels!=0?1f:0f);
        if(active!=null) {
            int unit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int[] textures={active.color,active.ground,active.occlusion,active.levels};
            for(int i=0;i<textures.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+143+i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);GL33.glBindSampler(143+i,0);
            }
            GL13.glActiveTexture(unit);
            int[] extra={active.actors.texture(),active.sourceRegistry,active.nativeDepth};
            int[] slots={137,138,139};
            for(int i=0;i<slots.length;i++) {GL13.glActiveTexture(GL13.GL_TEXTURE0+slots[i]);GL11.glBindTexture(GL11.GL_TEXTURE_2D,extra[i]);GL33.glBindSampler(slots[i],0);}
            GL13.glActiveTexture(unit);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+133);GL11.glBindTexture(GL11.GL_TEXTURE_2D,active.actors.normals());GL33.glBindSampler(133,0);GL13.glActiveTexture(unit);
            program.set("vpIsoSnowProjection",active.context.viewProjection);
            program.set("vpIsoSnowInverse",active.context.invViewProjection);
            float[] inverse=active.receiver.inverse.get(new float[16]);
            program.set("vpIsoSnowDepthWorld",(float)Math.sqrt(inverse[8]*inverse[8]+inverse[9]*inverse[9]+inverse[10]*inverse[10]*6));
            GameAccess.call("viewpoint.render.FrameUniforms","apply",null,
                    new Class<?>[]{GlProgram.class,FrameContext.class},program,active.context);
            program.set("vpIsoSnowScreen",(float)active.viewport[0],(float)active.viewport[1],
                    (float)active.width,(float)active.height);
            program.set("vpIsoSnowRaster",0f,0f);
        }
    }

    /** Caller owns native GL attributes, caches, and retirement. */
    public void draw(Frame frame,NativeReceiver receiver) {
        draw(frame,receiver,0,0);
    }
    public void draw(Frame frame,NativeReceiver receiver,int floorTexture,int occlusionTexture) {
        draw(frame,receiver,floorTexture,occlusionTexture,0);
    }
    public void draw(Frame frame,NativeReceiver receiver,int floorTexture,int occlusionTexture,int floorLevels) {
        if (!enabled(frame.scene)) return;
        this.receiver=receiver;
        if (floorTexture==0 || occlusionTexture==0) return;
        if (!IsometricShaders.supports(ShaderPacks.active().pack.id)) return;
        WorldRenderer.prepare(Long.MAX_VALUE);
        if (!(Boolean)GameAccess.field("viewpoint.render.WorldRenderer","ready",null)) return;
        GameAccess.staticCall("viewpoint.render.PackLinks","follow");
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if (viewport[2]<=0 || viewport[3]<=0 || GL11.glGetInteger(GL11.GL_DEPTH_BITS)==0) return;
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[][] previousTextures=new int[UNITS.length][4];int[] previousSamplers=new int[UNITS.length];
        for(int i=0;i<UNITS.length;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
            previousTextures[i][0]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            previousTextures[i][1]=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
            previousTextures[i][2]=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            previousTextures[i][3]=GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
            previousSamplers[i]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,UNITS[i]);
        }
        int[] buffers=new int[Math.min(8,GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS))];
        for(int i=0;i<buffers.length;i++)buffers[i]=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i);
        int ssbo=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int[] storage=new int[11];long[] storageStart=new long[11],storageSize=new long[11];
        for(int i=0;i<storage.length;i++) {
            storage[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);
            storageStart[i]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);
            storageSize[i]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);
        }
        int indirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int copyRead=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER),copyWrite=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        int[] image=new int[6];
        int[] keys={GL42.GL_IMAGE_BINDING_NAME,GL42.GL_IMAGE_BINDING_LEVEL,GL42.GL_IMAGE_BINDING_LAYERED,
                GL42.GL_IMAGE_BINDING_LAYER,GL42.GL_IMAGE_BINDING_ACCESS,GL42.GL_IMAGE_BINDING_FORMAT};
        for(int i=0;i<keys.length;i++)image[i]=GL30.glGetIntegeri(keys[i],0);
        boolean previousCull=WorldRenderer.cullFromPlayer;
        Object previousContext=GameAccess.field("viewpoint.render.FrameUniforms","current",null);
        int unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] unpackKeys={GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES};
        int[] unpackValues=new int[unpackKeys.length];
        for(int i=0;i<unpackKeys.length;i++)unpackValues[i]=GL11.glGetInteger(unpackKeys[i]);
        Object models=GameAccess.field("viewpoint.render.WorldRenderer","models",null);
        boolean prepared=false;
        IsometricBridge.rendering(frame);

        masked=floorTexture!=0 && occlusionTexture!=0;
        levels=floorLevels;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            for(int key:unpackKeys)GL11.glPixelStorei(key,key==GL11.GL_UNPACK_ALIGNMENT?4:0);
            for(int unit:UNITS)GL33.glBindSampler(unit,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            ensure();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);
            int buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            try {
                if (draw!=0) GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,nativeDepth);
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
            GameAccess.set("viewpoint.render.FrameContext","jitterX",context,0f);
            GameAccess.set("viewpoint.render.FrameContext","jitterY",context,0f);
            ((Matrix4f)GameAccess.field("viewpoint.render.FrameContext","plainViewProjection",context)).set(context.viewProjection);
            ((Matrix4f)GameAccess.field("viewpoint.render.FrameContext","invPlainViewProjection",context)).set(context.invViewProjection);
            GameAccess.set("viewpoint.render.FrameUniforms","current",null,context);
            preparing=this;
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
            actors.prepare(receiver,nativeDepth,width,height);actors.drawModels(frame.scene,models);
            if(sourceRegistry==0) {
                sourceRegistry=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,sourceRegistry);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,NativeGround.WALL_OWNERS,193,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            }
            if(sourceRevision!=NativeGround.wallRevision()) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,sourceRegistry);sourcePixels.clear();NativeGround.ownerData(sourcePixels);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,NativeGround.WALL_OWNERS,193,GL11.GL_RGBA,GL11.GL_FLOAT,sourcePixels);sourceRevision=NativeGround.wallRevision();
            }
            Object vpTargets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
            GameAccess.call("viewpoint.render.Targets","uploadGrids",vpTargets,
                    new Class<?>[]{SceneData.class,long.class},frame.scene,frame.number);
            GameAccess.call("viewpoint.render.FloorPlan","update",GameAccess.field("viewpoint.render.Targets","floorPlan",vpTargets),
                    new Class<?>[]{GameAccess.type("viewpoint.render.PlanSnapshot")},frame.scene.floorPlan);
            GameAccess.call("viewpoint.render.FloorPlan","bind",GameAccess.field("viewpoint.render.Targets","floorPlan",vpTargets),NONE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+149);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,(Integer)GameAccess.field("viewpoint.render.Targets","snowGrid",vpTargets));
            Object snow=GameAccess.field("viewpoint.render.WorldRenderer","snow",null);
            GameAccess.call(PASS,"prepare",snow,new Class<?>[]{FrameContext.class,GameAccess.type("viewpoint.render.ModelPass")},context,models);
            material.begin(width,height);
            GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);
            GL11.glColorMask(true,true,true,true);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+BASE_UNIT);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL33.glBindSampler(BASE_UNIT,0);
            active=this;
            GameAccess.call(PASS,"layer",snow,new Class<?>[]{FrameContext.class},context);
            active=null;preparing=null;
            material.finish(context,receiver,draw,color,viewport);
        } finally {
            if (prepared) GameAccess.call("viewpoint.render.ModelPass","endFrame",models,NONE);
            active=null;preparing=null;IsometricBridge.rendering(null);WorldRenderer.cullFromPlayer=previousCull;
            GameAccess.set("viewpoint.render.FrameUniforms","current",null,previousContext);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
            for(int i=0;i<unpackKeys.length;i++)GL11.glPixelStorei(unpackKeys[i],unpackValues[i]);
            for(int i=0;i<storage.length;i++) {
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
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,previousTextures[i][0]);
                GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,previousTextures[i][1]);
                GL11.glBindTexture(GL12.GL_TEXTURE_3D,previousTextures[i][2]);
                GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,previousTextures[i][3]);
                GL33.glBindSampler(UNITS[i],previousSamplers[i]);
            }
            GL13.glActiveTexture(previousActive);
        }
    }

    public static boolean enabled(SceneData scene) {
        return scene.snowLying.depth>0f && ShaderPacks.active().pipeline.snow;
    }

    private void ensure() {
        if(color==0) color=GL11.glGenTextures();
        if(ground==0) ground=GL11.glGenTextures();
        if(occlusion==0) occlusion=GL11.glGenTextures();
        if(nativeDepth==0)nativeDepth=GL11.glGenTextures();
        if(width==viewport[2] && height==viewport[3])return;
        width=viewport[2];height=viewport[3];
        for(int texture:new int[]{color,ground,occlusion,nativeDepth}) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,texture==nativeDepth?GL30.GL_DEPTH_COMPONENT32F:texture==occlusion?GL30.GL_RGBA32F:GL11.GL_RGBA8,
                    width,height,0,texture==nativeDepth?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,texture==occlusion || texture==nativeDepth?GL11.GL_FLOAT:GL11.GL_UNSIGNED_BYTE,0L);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }
    }
}
