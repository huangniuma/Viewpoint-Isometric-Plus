package vpiso;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.platform.Pipeline;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FarRelief;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** VP waves on the original image. No VP model, world color, or atmospheric LUT is required. */
public final class NativeVPWaterOverlay {
    private static final int[] UNITS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 122, 125, 126};
    private static final String HEADER = """
        #version 330
        #extension GL_ARB_shader_storage_buffer_object : enable
        #extension GL_ARB_shader_image_load_store : enable
        #undef PIPELINE_WATER_FFT
        #define PIPELINE_WATER_FFT 0
        in vec2 uv;
        out vec4 outputColor;
        uniform sampler2D nativeColor;
        uniform sampler2D nativeTiles;
        uniform sampler2D nativeCorners;
        uniform sampler2D nativeFlow;
        uniform vec4 nativeFlowGrid;
        uniform sampler2D nativeMaterials, nativeAtlas;
        uniform int nativePage;
        uniform vec3 nativeFloorU, nativeFloorV;
        uniform mat4 inverseNative;
        uniform vec3 nativeOrigin;
        uniform vec3 nativeCam;
        uniform vec4 nativeGrid;
        uniform float nativeDepthBias;
        uniform float rotationOn;
        uniform mat4 rotationInverse, rotationProjection;
        uniform vec3 uSunDir, uSunColor, uHorizon, uZenith;
        #define VIEWPOINT_SKY_LEVEL
        uniform vec3 uSkyLevel;
        uniform vec3 uFogColor;
        uniform float uSunStrength, uExposure;
        float nativeShore(vec2 p) {
          vec2 world=nativeCam.xy-p;
          vec2 at=world-nativeGrid.xy-.5, f=fract(at);
          ivec2 cell=ivec2(floor(at)), last=ivec2(nativeGrid.zw)-1;
          float a=texelFetch(nativeTiles,clamp(cell,ivec2(0),last),0).b;
          float b=texelFetch(nativeTiles,clamp(cell+ivec2(1,0),ivec2(0),last),0).b;
          float c=texelFetch(nativeTiles,clamp(cell+ivec2(0,1),ivec2(0),last),0).b;
          float d=texelFetch(nativeTiles,clamp(cell+ivec2(1),ivec2(0),last),0).b;
          return max(mix(mix(a,b,f.x),mix(c,d,f.x),f.y)-.5,0.0);
        }
        vec3 landBody() { return uSkyLevel*uSunColor*uSunStrength; }
        vec3 landSky(vec3 n) { return uSkyLevel; }
        """;
    private static final String MAIN = """
        void main() {
          vec4 original=texture(nativeColor,uv);
          outputColor=original;
          // Water does not write the game's depth. Intersect the real native screen ray
          // with z=0, then use the same depth test as the game's water body draw.
          vec3 delta;
          float d;
          if (rotationOn>0.5) {
            vec4 a=rotationInverse*vec4(uv*2.0-1.0,-1.0,1.0);
            vec4 b=rotationInverse*vec4(uv*2.0-1.0,1.0,1.0);
            vec3 at=a.xyz/a.w, ray=b.xyz/b.w-at;
            if (abs(ray.y)<1e-6) discard;
            vec3 p=at+ray*((-nativeCam.z*2.4494896-at.y)/ray.y);
            delta=vec3(-p.x,-p.z,p.y/2.4494896);
            vec4 clip=rotationProjection*vec4(p,1.0);
            d=clip.z/clip.w*.5+.5;
          } else {
            vec3 at=(inverseNative*vec4(vec3(uv,0.0)-nativeOrigin,0.0)).xyz;
            vec3 ray=(inverseNative*vec4(0.0,0.0,1.0,0.0)).xyz;
            if (abs(ray.z)<1e-6) discard;
            d=(-nativeCam.z-at.z)/ray.z;
            delta=at+ray*d;
          }
          vec3 pos=vec3(-delta.x,-nativeCam.z*2.4494896,-delta.y);
        #if VPISO_OPTICS
          vec4 span=waterSpan(pos);
        #endif
          if (d<0.0 || d>=1.0) discard;
          vec2 world=nativeCam.xy+delta.xy;
          ivec2 tile=ivec2(floor(world-nativeGrid.xy));
          if (any(lessThan(tile,ivec2(0))) || any(greaterThanEqual(tile,ivec2(nativeGrid.zw)))) discard;
          vec4 rule=texelFetch(nativeTiles,tile,0);
          if (rule.r<0.5 || rule.a<=0.0) discard;
          // Model mode removes VP's water body receiver. Only the native shoreline
          // needs the small forward shift that lets its transparent edge cover land.
          float bias=nativeDepthBias<0.0 && rule.g<.5 ? .001 : nativeDepthBias;
          if (d+bias>=texture(uDepth,uv).r) discard;
          vec4 corners=texelFetch(nativeCorners,tile,0);
          vec2 f=fract(world);
          float coverage;
          // The native VBO chooses its diagonal from depths[0]==depths[2].
          if (corners.x==corners.z)
            coverage=f.y>=f.x ? corners.x*(1.0-f.y)+corners.y*(f.y-f.x)+corners.z*f.x
                              : corners.x*(1.0-f.x)+corners.z*f.y+corners.w*(f.x-f.y);
          else
            coverage=f.x+f.y>=1.0 ? corners.y*(1.0-f.x)+corners.z*(f.x+f.y-1.0)+corners.w*(1.0-f.y)
                                  : corners.y*f.y+corners.w*f.x+corners.x*(1.0-f.x-f.y);
          if (coverage<=.001) discard;
          float lum=dot(original.rgb,vec3(.2126,.7152,.0722));
          if (lum<.004) discard;
        #if VPISO_SOMBER
          vec3 v=normalize(uEye-pos);
          float rough=surfaceRoughness(MATERIAL_WATER);
          vec3 n=waterWaves(pos,length(pos-uEye),span,rough);
          vec3 albedo=waterAlbedo(pos,v,n);
          vec3 water=shade(albedo,vec4(uSkyLevel,1.0),n,max(uSunDir.y,0.0));
          water+=specularClamped(skySpecular(n,v,rough,WATER_F0,uSkyLevel,1.0)
              +bodySpecular(n,v,rough,WATER_F0,max(uSunDir.y,0.0)));
        #elif VPISO_OPTICS
          WaterSurface s=waterSurface(pos,length(pos-uEye),span);
          vec3 v=normalize(uEye-pos);
          vec3 white=max(uSkyLevel,vec3(0.0));
          vec3 under=underwater(pos,v,s,white,max(uSunDir.y,0.0));
          vec3 mirror=skyColor(reflect(-v,s.mirror),uHorizon,uZenith,uSunDir,uSunColor,uSunStrength)*SKY_BRIGHTNESS;
          float fresnel=.02+(max(1.0-s.rough,.02)-.02)*pow(1.0-max(dot(v,s.mirror),0.0),5.0);
          vec3 water=mix(under,mirror,fresnel)+specularClamped(bodySpecular(s.normal,v,ROUGH_WATER,.02,1.0));
          water=mix(water,white*.8,waterFoam(pos,s));
        #else
          ivec2 row=ivec2(0,int(nativeGrid.w));
          vec4 mapping=texelFetch(nativeMaterials,tile,0);
          vec4 rect=texelFetch(nativeMaterials,tile+row,0);
          vec4 light=texelFetch(nativeMaterials,tile+row*2,0);
          if (int(light.a)!=nativePage) discard;
          vec3 local=vec3(f-.5,1.0);
          vec2 pixel=vec2(dot(nativeFloorU,local),dot(nativeFloorV,local));
          vec2 materialUv=clamp(mapping.xy+pixel*mapping.zw,rect.xy,rect.zw);
          vec3 albedo=sampleSprite(nativeAtlas,materialUv,rect,FLAG_SOLID_FLOOR).rgb;
          float lit=uSunStrength>0.0 ? smoothstep(0.0,.3,uSunDir.y) : 0.0;
          vec3 water=shade(albedo,vec4(light.rgb,1.0),vec3(0,1,0),lit);
        #endif
          water=nativeGrade(water);
          // Preserve native vision, but never mix its glints/foam into fully covered water.
          float daylight=max(dot(uSkyLevel,vec3(.2126,.7152,.0722)),.025);
          float visible=clamp(lum/max(.08,daylight*.35),0.0,1.0)*rule.a;
          outputColor=vec4(mix(original.rgb,water*visible,clamp(coverage,0.0,1.0)),original.a);
        }
        """;

    private GlProgram program;
    private Object producer;
    private int rain, vao, color, depth, tiles, corners, materials, flow, toneLut, width, height;
    private ShaderPacks.Active activePack;
    private boolean optics;
    private final float[] floorU=new float[3],floorV=new float[3];
    private boolean reported;
    private boolean reportedMissingDepth;
    private final FrameContext context = new FrameContext();

    public void draw(Frame frame, NativeReceiver receiver, Snapshot snapshot, boolean enabled) {
        draw(frame,receiver,snapshot,enabled,.001f);
    }

    public void draw(Frame frame, NativeReceiver receiver, Snapshot snapshot, boolean enabled, float depthBias) {
        draw(frame, receiver, snapshot, enabled, depthBias, null);
    }

    public void draw(Frame frame, NativeReceiver receiver, Snapshot snapshot, boolean enabled, float depthBias,
                     RotationCamera.Snapshot rotation) {
        if (!enabled || !snapshot.ready()) return;
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (viewport[2]<=0 || viewport[3]<=0) return;
        if (GL11.glGetInteger(GL11.GL_DEPTH_BITS)==0) {
            if(!reportedMissingDepth) { reportedMissingDepth=true; System.out.println("[VPISO] VP water overlay skipped: native framebuffer has no depth."); }
            return;
        }
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER), sourceReadBuffer=-1;
        int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), previousVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousUniformBuffer=GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        int optionBinding=viewpoint.platform.Gl.BLOCK_PACK_OPTIONS;
        int previousOptions=GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING,optionBinding);
        long previousOptionsStart=GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START,optionBinding);
        long previousOptionsSize=GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE,optionBinding);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[][] bindings=new int[UNITS.length][4];
        for(int i=0;i<UNITS.length;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
            bindings[i][0]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            bindings[i][1]=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
            bindings[i][2]=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            bindings[i][3]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,UNITS[i]);
            GL33.glBindSampler(UNITS[i],0);
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            // VP's offscreen wave passes expect clean raster state. Native sprites
            // leave alpha testing/scissors active; applying those to FFT intermediates
            // discards signed values and turns otherwise calm water into white noise.
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true,true,true,true);
            // Native water can run without VP's world renderer, which normally uploads these.
            GameAccess.staticCall("viewpoint.render.LiveOptions","frame");
            init(); ensure(viewport[2],viewport[3]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);
            sourceReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            try {
                // An auxiliary MRT attachment may still be selected after the world pass.
                // Water's visibility/tint must come from the displayed color image.
                if(draw!=0) GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
            } finally {
                GL11.glReadBuffer(sourceReadBuffer);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            }
            context.scene=frame.scene; context.cloudTime=frame.scene.cloudTime;
            context.pipeline=Patch_ModelWater.waterPipeline(ShaderPacks.active().pipeline);
            int waves=0;
            if(optics) {
              GameAccess.call("viewpoint.render.WaveDrift","advance",null,
                    new Class<?>[]{double.class,float.class,float.class},(double)frame.scene.cloudTime,frame.scene.windX,frame.scene.windZ);
            GameAccess.call("viewpoint.render.WaveTile","draw",producer,new Class<?>[]{FrameContext.class},context);
              waves=((Number)GameAccess.call("viewpoint.render.WaveTile","texture",producer,new Class<?>[0])).intValue();
              if(waves==0) throw new IllegalStateException("VP wave producer returned no texture");
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0+2); GL11.glBindTexture(GL11.GL_TEXTURE_2D,tiles);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,snapshot.width,snapshot.height,0,GL11.GL_RGBA,GL11.GL_FLOAT,snapshot.pixels());
            bind(5,GL11.GL_TEXTURE_2D,corners);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,snapshot.width,snapshot.height,0,GL11.GL_RGBA,GL11.GL_FLOAT,snapshot.corners());
            if(optics) {
                bind(9,GL11.GL_TEXTURE_2D,flow);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,snapshot.flowWidth,snapshot.flowHeight,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,snapshot.flowPixels());
            }
            if(!optics) {
                bind(6,GL11.GL_TEXTURE_2D,materials);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,snapshot.width,snapshot.height*3,0,GL11.GL_RGBA,GL11.GL_FLOAT,snapshot.materials());
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw); GL11.glViewport(viewport[0],viewport[1],width,height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false); GL11.glColorMask(true,true,true,true);
            bind(0,GL11.GL_TEXTURE_2D,color); bind(1,GL11.GL_TEXTURE_2D,depth); bind(2,GL11.GL_TEXTURE_2D,tiles);
            bind(3,GL30.GL_TEXTURE_2D_ARRAY,waves); bind(4,GL30.GL_TEXTURE_2D_ARRAY,rain);
            if(toneLut!=0) bind(8,GL12.GL_TEXTURE_3D,toneLut);
            program.use(); program.setInt("nativeColor",0); program.setInt("uDepth",1); program.setInt("nativeTiles",2);
            program.setInt("nativeCorners",5);
            program.setInt("nativeFlow",9);
            program.set("nativeFlowGrid",(float)snapshot.flowX,(float)snapshot.flowY,(float)snapshot.flowWidth,(float)snapshot.flowHeight);
            program.setInt("nativeMaterials",6); program.setInt("nativeAtlas",7);
            program.set("nativeFloorU",floorU[0],floorU[1],floorU[2]);
            program.set("nativeFloorV",floorV[0],floorV[1],floorV[2]);
            program.setInt("uWaves",3); program.setInt("uRainTextures",4);
            program.setInt("uToneLut",8); program.set("uAdapted",frame.scene.adapted);
            // Remaining optional samplers share a correctly typed 2D binding, and their branches are disabled.
            program.setInt("uReliefFlow",2); program.setInt("uBedColours",0);
            program.set("inverseNative",receiver.inverse); program.set("nativeOrigin",receiver.origin[0],receiver.origin[1],receiver.origin[2]);
            program.set("nativeCam",frame.camX,frame.camY,frame.camZ);
            program.set("nativeGrid",(float)snapshot.minX,(float)snapshot.minY,(float)snapshot.width,(float)snapshot.height);
            program.set("nativeDepthBias",depthBias);
            program.set("rotationOn",rotation == null ? 0f : 1f);
            if (rotation != null) {
                program.set("rotationInverse",rotation.inverse());
                program.set("rotationProjection",rotation.projection().mul(rotation.view()));
            }
            SceneData scene=frame.scene;
            program.set("uSceneOrigin",-frame.camX,frame.camZ*2.4494896f,-frame.camY);
            program.set("uSunDir",scene.sunX,scene.sunY,scene.sunZ); program.set("uSunColor",scene.sunR,scene.sunG,scene.sunB);
            program.set("uSunStrength",scene.sunStrength); program.set("uSkyLevel",scene.skyR,scene.skyG,scene.skyB);
            program.set("uFogColor",scene.fogR,scene.fogG,scene.fogB);
            program.set("uExposure",((Number)GameAccess.call("viewpoint.render.EyeMeter","exposure",null,
                    new Class<?>[]{FrameContext.class},context)).floatValue());
            program.set("uHorizon",scene.fogR,scene.fogG,scene.fogB); program.set("uZenith",scene.zenithR,scene.zenithG,scene.zenithB);
            program.set("uSunTrue",scene.sunTrueX,scene.sunTrueY,scene.sunTrueZ);
            program.set("uWind",scene.windX,scene.windZ); program.set("uRainStrength",scene.rain); program.set("uRainClock",scene.time%600f);
            program.set("uReliefGrid",snapshot.hasFlow?1f:0f,0f,0f,0f);
            program.set("uReliefCamera",frame.camX,frame.camY);
            program.set("uReliefFlowCells",(float)snapshot.flowCellsX,(float)snapshot.flowCellsY);
            program.set("uBedColoursOn",0f);
            GameAccess.call("viewpoint.render.FrameUniforms","waves",null,new Class<?>[]{GlProgram.class,FrameContext.class},program,context);
            program.set("uEye",-IsoCameraMath.DX*IsoCameraMath.DISTANCE,
                    -IsoCameraMath.DY*IsoCameraMath.DISTANCE,-IsoCameraMath.DZ*IsoCameraMath.DISTANCE);
            if (rotation != null) {
                float[] eye=rotation.view().invert().get(new float[16]);
                program.set("uEye",eye[12],eye[13],eye[14]);
            }
            // Native inverse maps one screen height to world units. Keep its exact footprint.
            float[] m=matrix(receiver);
            float dz=-m[6]/m[10], dx=m[4]+m[8]*dz, dy=m[5]+m[9]*dz;
            // Project the screen-height derivative onto the camera's vertical axis.
            // Its ground-plane length contains a 1/sin(elevation) stretch.
            float span=(float)Math.sqrt(dx*dx+dy*dy)*Math.abs(IsoCameraMath.DY);
            program.set("uCamera",IsoCameraMath.NEAR,IsoCameraMath.FAR,1f,span/(2f*IsoCameraMath.DISTANCE));
            if (rotation != null) {
                float[] projection=rotation.projection().get(new float[16]);
                program.set("uCamera",IsoCameraMath.NEAR,IsoCameraMath.FAR,1f,1f/projection[5]);
            }
            GL30.glBindVertexArray(vao);
            if(optics) GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            else for(int page=0;page<snapshot.pages.size();page++) {
                int id=((Number)GameAccess.call("zombie.core.textures.TextureID","getID",snapshot.pages.get(page),new Class<?>[0])).intValue();
                if(id<=0) continue;
                bind(7,GL11.GL_TEXTURE_2D,id); program.setInt("nativePage",page+1);
                Object filter=GameAccess.field("viewpoint.render.TextureFilter","SPRITES",null);
                GameAccess.call("viewpoint.render.TextureFilter","apply",filter,new Class<?>[]{GlProgram.class},program);
                int sampler=((Number)GameAccess.call("viewpoint.render.TextureFilter","sampler",filter,new Class<?>[]{int.class},id)).intValue();
                GL33.glBindSampler(7,sampler);
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            }
            if(!reported) {
                reported=true;
                System.out.println("[VPISO] VP water overlay: native depth=yes, water tiles="+snapshot.count+", VP optics="+optics+", sprite pages="+snapshot.pages.size()+", VP waves="+waves);
            }
        } finally {
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            if(sourceReadBuffer>=0) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);
                GL11.glReadBuffer(sourceReadBuffer);
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL11.glReadBuffer(previousReadBuffer);
            GL30.glBindVertexArray(previousVao); GL20.glUseProgram(previousProgram);
            if(previousOptions!=0 && previousOptionsSize>0)
                GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER,optionBinding,previousOptions,previousOptionsStart,previousOptionsSize);
            else GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,optionBinding,previousOptions);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,previousUniformBuffer);
            for(int i=0;i<UNITS.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,bindings[i][0]);
                GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,bindings[i][1]);
                GL11.glBindTexture(GL12.GL_TEXTURE_3D,bindings[i][2]); GL33.glBindSampler(UNITS[i],bindings[i][3]);
            }
            GL13.glActiveTexture(active);
        }
    }

    private void init() {
        if(program!=null && activePack==ShaderPacks.active()) return;
        ShaderPacks.Active nextPack=ShaderPacks.active(); boolean nextOptics=nextPack.pipeline.water;
        boolean somber=nextPack.pack.id.equals("somber");
        String header=HEADER.replace("#define PIPELINE_WATER_FFT 0", "#define PIPELINE_WATER_FFT "+(nextPack.pipeline.waterFft?1:0)).replace("in vec2 uv;", "#define VPISO_SOMBER "+(somber?1:0)+"\n#define VPISO_OPTICS "+(nextOptics?1:0)+"\nin vec2 uv;");
        if(!nextOptics) header+="uniform sampler2D uDepth;\n";
        GlProgram next=GlProgram.fromSources("VPISO native water",new GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,
                NativeWaterShader.overlayOptions(header+(somber?NativeWaterShader.overlaySomber(nextPack):nextOptics?NativeWaterShader.overlayLibrary():NativeWaterShader.overlayMaterial())
                +NativeWaterShader.overlayPost(nextPack)+MAIN,nextPack)),Map.of(),Map.of(),List.of("VPISO installed VP water"));
        try {
        if(vao==0) vao=GL30.glGenVertexArrays();
        if(producer==null && nextOptics) try {
            Constructor<?> ctor=GameAccess.type("viewpoint.render.WaveTile").getDeclaredConstructor(); ctor.setAccessible(true);
            producer=ctor.newInstance();
            byte[] layers=(byte[])GameAccess.staticCall("viewpoint.render.RainTextures","layers");
            ctor=GameAccess.type("viewpoint.render.RainTextures").getDeclaredConstructor(byte[].class); ctor.setAccessible(true);
            Object rainTextures=ctor.newInstance((Object)layers);
            rain=((Number)GameAccess.field("viewpoint.render.RainTextures","texture",rainTextures)).intValue();
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot create installed VP water producers",e); }
        if(tiles==0) {
            tiles=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,GL11.GL_FLOAT,1,1);
            corners=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,GL11.GL_FLOAT,1,1);
            materials=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,GL11.GL_FLOAT,1,1);
            flow=texture(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,1,1);
            float[] mesh=(float[])GameAccess.field("viewpoint.world.TileMesh","data",GameAccess.staticCall("viewpoint.world.TileMeshes","floorMesh"));
            for(int axis=0;axis<2;axis++) {
                float[] coefficients=axis==0?floorU:floorV;
                coefficients[0]=mesh[8+3+axis]-mesh[3+axis];
                coefficients[1]=mesh[16+3+axis]-mesh[8+3+axis];
                coefficients[2]=mesh[3+axis]+.5f*(coefficients[0]+coefficients[1]);
            }
        }
        if(next.has("uToneLut") && toneLut==0) {
            try {
                Constructor<?> ctor=GameAccess.type("viewpoint.render.ToneLut").getDeclaredConstructor();ctor.setAccessible(true);
                toneLut=((Number)GameAccess.field("viewpoint.render.ToneLut","texture",ctor.newInstance())).intValue();
            } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot create VP tone table",e); }
        }
        } catch(Throwable e) { next.delete(); throw e; }
        if(program!=null) program.delete();
        program=next; activePack=nextPack; optics=nextOptics;
    }
    private void ensure(int w,int h) {
        if(width==w && height==h && color!=0) return;
        if(color!=0) { GL11.glDeleteTextures(color); GL11.glDeleteTextures(depth); }
        width=w; height=h; color=texture(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,w,h);
        depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,w,h);
    }
    private static int texture(int internal,int format,int type,int w,int h) {
        int texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,type,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }
    private static void bind(int unit,int target,int texture) { GL13.glActiveTexture(GL13.GL_TEXTURE0+unit); GL11.glBindTexture(target,texture); }
    private static float[] matrix(NativeReceiver receiver) {
        return (float[])GameAccess.call("org.joml.Matrix4f","get",receiver.inverse,new Class<?>[]{float[].class},(Object)new float[16]);
    }

    /** Owned by one queued drawer. RGBA = water, shoreline, shore distance, visibility. */
    public static final class Snapshot {
        private static final int MAX_SIDE=192;
        private static boolean reportedCapture;
        private int minX,minY,width,height,count;
        private float[] rgba=new float[0];
        private float[] cornerDepth=new float[0];
        private float[] materialData=new float[0];
        private final List<Object> pages=new ArrayList<>();
        private FloatBuffer upload,cornerUpload,materialUpload;
        private ByteBuffer flowUpload;
        private byte[] flowData=new byte[]{(byte)128,(byte)128,0,0};
        private int flowX,flowY,flowWidth=1,flowHeight=1,flowCellsX=1,flowCellsY=1;
        private boolean hasFlow;
        public boolean ready() { return count>0; }
        public void capture(Frame frame,Object cell,int player) {
            capture(frame, cell, player, null);
        }

        public void capture(Frame frame,Object cell,int player,RotationCamera.Snapshot rotation) {
            count=0;
            NativeReceiver receiver=new NativeReceiver(); receiver.capture(frame,player);
            float[] m=matrix(receiver);
            double loX=Double.POSITIVE_INFINITY,loY=loX,hiX=Double.NEGATIVE_INFINITY,hiY=hiX;
            for(int corner=0;corner<4;corner++) {
                float u=(corner&1)-receiver.origin[0],v=(corner>>1)-receiver.origin[1];
                float dz=(-frame.camZ-m[2]*u-m[6]*v)/m[10];
                double x=frame.camX+m[0]*u+m[4]*v+m[8]*dz;
                double y=frame.camY+m[1]*u+m[5]*v+m[9]*dz;
                loX=Math.min(loX,x); hiX=Math.max(hiX,x); loY=Math.min(loY,y); hiY=Math.max(hiY,y);
            }
            if(rotation!=null) {
                loX=loY=Double.POSITIVE_INFINITY; hiX=hiY=Double.NEGATIVE_INFINITY;
                double[] point=new double[2];
                for(int corner=0;corner<4;corner++) if(rotation.ground((corner&1)*rotation.width(),
                        (corner>>1)*rotation.height(),0,point)) {
                    loX=Math.min(loX,point[0]); hiX=Math.max(hiX,point[0]);
                    loY=Math.min(loY,point[1]); hiY=Math.max(hiY,point[1]);
                }
            }
            if(!Double.isFinite(loX) || !Double.isFinite(loY)) return;
            minX=(int)Math.floor(loX)-2; minY=(int)Math.floor(loY)-2;
            width=Math.min(MAX_SIDE,(int)Math.ceil(hiX)-minX+2); height=Math.min(MAX_SIDE,(int)Math.ceil(hiY)-minY+2);
            if(width<=0 || height<=0) return;
            if(hiX-loX>MAX_SIDE-4) minX=(int)Math.floor(frame.camX)-MAX_SIDE/2;
            if(hiY-loY>MAX_SIDE-4) minY=(int)Math.floor(frame.camY)-MAX_SIDE/2;
            int n=width*height;
            captureFlow(viewpoint.far.Relief.current());
            pages.clear();
            if(materialData.length<n*12) materialData=new float[n*12]; else Arrays.fill(materialData,0,n*12,0);
            var mappings=new IdentityHashMap<Object,float[]>();
            var pageNumbers=new IdentityHashMap<Object,Integer>();
            if(rgba.length<n*4) rgba=new float[n*4]; else Arrays.fill(rgba,0,n*4,0);
            if(cornerDepth.length<n*4) cornerDepth=new float[n*4]; else Arrays.fill(cornerDepth,0,n*4,0);
            Bindings api=Bindings.API;
            try {
                for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                    Object square=(Object)api.square.invokeExact(cell,minX+x,minY+y,0);
                    if(square==null) continue;
                    Object geometry=(Object)api.water.invokeExact(square);
                    if(geometry==null || !(boolean)api.valid.invokeExact(geometry)) continue;
                    float[] depths=(float[])api.depth.invokeExact(geometry);
                    int at=(y*width+x)*4;
                    rgba[at]=1; rgba[at+1]=(boolean)api.shore.invokeExact(geometry)?1f:0f;
                    rgba[at+2]=rgba[at+1]>.5f?0f:16f; rgba[at+3]=1; count++;
                    System.arraycopy(depths,0,cornerDepth,at,4);
                    if(!ShaderPacks.active().pipeline.water) {
                        Object texture=waterTexture(square);
                        if(texture!=null && (Boolean)GameAccess.call("zombie.core.textures.Texture","isReady",texture,new Class<?>[0])) {
                            Object page=GameAccess.call("zombie.core.textures.Texture","getTextureId",texture,new Class<?>[0]);
                            if(page!=null) {
                                Integer number=pageNumbers.get(page);
                                if(number==null) { pages.add(page); number=pages.size(); pageNumbers.put(page,number); }
                                float[] mapping=mappings.get(texture);
                                if(mapping==null) {
                                    mapping=(float[])GameAccess.call("viewpoint.world.WorldMesher","textureMapping",null,
                                            new Class<?>[]{GameAccess.type("zombie.core.textures.Texture")},texture);
                                    mappings.put(texture,mapping);
                                }
                                System.arraycopy(mapping,4,materialData,at,4);
                                System.arraycopy(mapping,0,materialData,n*4+at,4);
                                materialData[n*8+at]=frame.scene.skyR;
                                materialData[n*8+at+1]=frame.scene.skyG;
                                materialData[n*8+at+2]=frame.scene.skyB;
                                materialData[n*8+at+3]=number;
                            }
                        }
                    }
                }
            } catch(Throwable e) { throw new IllegalStateException("Cannot capture native water squares",e); }
            // VP ShoreField's diagonal metric and 1/16-square quantization.
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                relax(x,y,x-1,y,1f); relax(x,y,x,y-1,1f);
                relax(x,y,x-1,y-1,1.41421356f); relax(x,y,x+1,y-1,1.41421356f);
            }
            for(int y=height-1;y>=0;y--) for(int x=width-1;x>=0;x--) {
                relax(x,y,x+1,y,1f); relax(x,y,x,y+1,1f);
                relax(x,y,x+1,y+1,1.41421356f); relax(x,y,x-1,y+1,1.41421356f);
            }
            for(int i=0;i<n;i++) rgba[i*4+2]=Math.min(255,Math.round(rgba[i*4+2]*16f))/16f;
            if(count>0 && !reportedCapture) {
                reportedCapture=true;
                System.out.println("[VPISO] native water snapshot: "+count+" tiles, screen grid="+width+"x"+height+" at "+minX+","+minY);
            }
        }
        private void relax(int x,int y,int fromX,int fromY,float cost) {
            if(fromX<0 || fromY<0 || fromX>=width || fromY>=height) return;
            int at=(y*width+x)*4+2;
            rgba[at]=Math.min(rgba[at],rgba[(fromY*width+fromX)*4+2]+cost);
        }
        public void fit(int x,int y,int w,int h,float camX,float camY,float camZ,float[] values) {
            minX=x; minY=y; width=w; height=h; rgba=values.clone(); count=0;
            captureFlow(null);
            pages.clear(); materialData=new float[w*h*12];
            cornerDepth=new float[w*h*4]; Arrays.fill(cornerDepth,1f);
            for(int i=0;i<w*h;i++) if(rgba[i*4]>.5f) count++;
        }
        /** Copy only this queued frame's coarse water data, never the global relief GPU state. */
        public void captureFlow(FarRelief.Lift lift) {
            hasFlow=lift!=null && lift.grid()!=null;
            flowX=flowY=0;flowWidth=flowHeight=flowCellsX=flowCellsY=1;
            if(!hasFlow || lift.grid().flow()==null) {
                flowData=new byte[]{(byte)128,(byte)128,0,0};
                return;
            }
            FarRelief.Field field=lift.grid();
            flowCellsX=FarRelief.Field.flowCells(field.width());
            flowCellsY=FarRelief.Field.flowCells(field.height());
            flowX=Math.max(0,Math.min(flowCellsX-1,Math.floorDiv(minX,16)));
            flowY=Math.max(0,Math.min(flowCellsY-1,Math.floorDiv(minY,16)));
            int endX=Math.max(flowX,Math.min(flowCellsX-1,Math.floorDiv(minX+width,16)+1));
            int endY=Math.max(flowY,Math.min(flowCellsY-1,Math.floorDiv(minY+height,16)+1));
            flowWidth=endX-flowX+1;flowHeight=endY-flowY+1;
            int bytes=flowWidth*flowHeight*4;
            if(flowData.length<bytes)flowData=new byte[bytes];
            for(int row=0;row<flowHeight;row++)
                System.arraycopy(field.flow(),((flowY+row)*flowCellsX+flowX)*4,flowData,row*flowWidth*4,flowWidth*4);
        }
        private ByteBuffer flowPixels() {
            int n=flowWidth*flowHeight*4;
            if(flowUpload==null || flowUpload.capacity()<n)flowUpload=BufferUtils.createByteBuffer(n);
            flowUpload.clear();flowUpload.put(flowData,0,n).flip();return flowUpload;
        }
        public void cornerDepth(float[] values) { cornerDepth=values.clone(); }
        /** Controlled material fixture; production uses each square's actual current sprite. */
        public void material(Object page,float[] mapping,float[] light) {
            pages.add(page); int n=width*height;
            for(int i=0;i<n;i++) {
                System.arraycopy(mapping,4,materialData,i*4,4);
                System.arraycopy(mapping,0,materialData,n*4+i*4,4);
                System.arraycopy(light,0,materialData,n*8+i*4,3);
                materialData[n*8+i*4+3]=pages.size();
            }
        }
        private static Object waterTexture(Object square) {
            Object floor=GameAccess.call("zombie.iso.IsoGridSquare","getFloor",square,new Class<?>[0]);
            Object sprite=null;
            if(floor!=null && (Boolean)GameAccess.call("zombie.iso.IsoObject","hasProperty",floor,
                    new Class<?>[]{GameAccess.type("zombie.iso.SpriteDetails.IsoFlagType")},
                    GameAccess.field("zombie.iso.SpriteDetails.IsoFlagType","water",null)))
                sprite=GameAccess.call("zombie.iso.IsoObject","getSprite",floor,new Class<?>[0]);
            if(sprite==null) {
                Object manager=GameAccess.field("zombie.iso.sprite.IsoSpriteManager","instance",null);
                if(manager==null) return null;
                sprite=GameAccess.call("zombie.iso.sprite.IsoSpriteManager","getSprite",manager,
                        new Class<?>[]{String.class},"blends_natural_02_0");
                floor=null;
            }
            if(sprite==null) return null;
            Object dir=floor==null?GameAccess.field("zombie.iso.IsoDirections","N",null)
                    :GameAccess.call("zombie.iso.IsoObject","getDir",floor,new Class<?>[0]);
            return GameAccess.call("zombie.iso.sprite.IsoSprite","getTextureForCurrentFrame",sprite,
                    new Class<?>[]{GameAccess.type("zombie.iso.IsoDirections"),GameAccess.type("zombie.iso.IsoObject")},dir,floor);
        }
        private FloatBuffer pixels() {
            int n=width*height*4;
            if(upload==null || upload.capacity()<n) upload=BufferUtils.createFloatBuffer(n);
            upload.clear(); upload.put(rgba,0,n).flip(); return upload;
        }
        private FloatBuffer corners() {
            int n=width*height*4;
            if(cornerUpload==null || cornerUpload.capacity()<n) cornerUpload=BufferUtils.createFloatBuffer(n);
            cornerUpload.clear(); cornerUpload.put(cornerDepth,0,n).flip(); return cornerUpload;
        }
        private FloatBuffer materials() {
            int n=width*height*12;
            if(materialUpload==null || materialUpload.capacity()<n) materialUpload=BufferUtils.createFloatBuffer(n);
            materialUpload.clear(); materialUpload.put(materialData,0,n).flip(); return materialUpload;
        }
    }
    private static final class Bindings {
        static final Bindings API=new Bindings();
        final MethodHandle square,water,valid,shore,depth;
        Bindings() {
            try {
                var lookup=MethodHandles.publicLookup();
                Class<?> sq=GameAccess.type("zombie.iso.IsoGridSquare"),cell=GameAccess.type("zombie.iso.IsoCell"),geometry=GameAccess.type("zombie.iso.IsoWaterGeometry");
                square=lookup.unreflect(cell.getMethod("getGridSquare",int.class,int.class,int.class))
                        .asType(MethodType.methodType(Object.class,Object.class,int.class,int.class,int.class));
                water=lookup.unreflect(sq.getMethod("getWater")).asType(MethodType.methodType(Object.class,Object.class));
                valid=lookup.unreflect(geometry.getMethod("isValid")).asType(MethodType.methodType(boolean.class,Object.class));
                shore=lookup.unreflect(geometry.getMethod("isbShore")).asType(MethodType.methodType(boolean.class,Object.class));
                depth=lookup.unreflectGetter(geometry.getField("depth")).asType(MethodType.methodType(float[].class,Object.class));
            } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot bind native water snapshot",e); }
        }
    }
}
