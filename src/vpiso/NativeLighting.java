package vpiso;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPack;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** Selected VP light functions evaluated over an owned native receiver, without a world colour pass. */
public final class NativeLighting {
    private static final String FRAME="viewpoint.render.FrameUniforms", CONTEXT="viewpoint.render.FrameContext";
    private static final String RENDERER="viewpoint.render.WorldRenderer", TARGETS="viewpoint.render.Targets";
    private static final Class<?>[] NONE={};
    private static final Pattern INCLUDE=Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final Pattern LAMP_VISIBILITY=Pattern.compile("\\bfloat\\s+lampVisibility\\s*\\(");
    private static final Pattern SHADE_SHARED=Pattern.compile("\\bvec3\\s+shadeShared\\s*\\(([^)]*)\\)");
    private static final int[] UNITS={0,5,6,14,18,21,30,49,95,96,97,98,101,102,103,110,111,130,131,152};
    private static final int[] PIXEL_STATE={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,
            GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,
            GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_UNPACK_ALIGNMENT,
            GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,
            GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES};
    private static long sequence;

    public static String fragment(String base) {
        ShaderPacks.Active active=GlProgram.pack();
        Expansion expansion=new Expansion(active.pack);
        String shade=expansion.read("lib/shade.glsl");
        var signature=SHADE_SHARED.matcher(shade);
        int arguments=signature.find()?signature.group(1).split(",").length:0;
        if(arguments!=0 && arguments!=6 && arguments!=7)
            throw new IllegalStateException("Unsupported VP shadeShared signature: "+arguments);
        String shadeCall=arguments==7
                ?"shadeShared(vec3(1.0),light,skyShare,n,lit,vec4(n,1.0),vec3(0.0))"
                :arguments==6?"shadeShared(vec3(1.0),light,skyShare,n,lit,vec4(n,1.0))"
                :"shade(vec3(1.0),light,n,lit)";
        if(arguments==0 && !shade.contains("vec3 shade("))
            throw new IllegalStateException("Unsupported VP shade function");
        String composition=arguments!=0?"""
              vec3 sky=skyPartOf(light.rgb,skyShare);
              vec2 parts;
              vec3 shaped=lampsShadowed(pos,n,light.rgb-sky,outdoors,parts);
              float keep=1.0-parts.x*(1.0-parts.y);
              light.rgb=sky+(light.rgb-sky)*keep;
              light.rgb=max(light.rgb,min(shaped,light.rgb*1.35+0.02));
            """:"""
              light.rgb*=lampShadowKeep(light.rgb,pos,n,outdoors);
              light.rgb=max(light.rgb,min(lamps(pos,n),light.rgb*1.35+0.02));
            """;
        String helpers="""
            #define VPISO_NATIVE_LIGHTING 1
            uniform int shadowsOn;
            uniform float shadowStrength;
            uniform int vpIsoLampsOn;
            uniform float vpIsoLampStrength;
            uniform int vpIsoSpotsOn;
            uniform float vpIsoNativeShadowSoftness;
            uniform vec3 vpIsoLightingEye;
            uniform sampler2D vpIsoNativeRegions;
            uniform ivec2 vpIsoNativeRegionOrigin;
            uniform int vpIsoNativeRegionLevel, vpIsoNativeRegionsOn;
            #ifndef VIEWPOINT_SCENE_ORIGIN
            #define VIEWPOINT_SCENE_ORIGIN
            uniform vec3 uSceneOrigin;
            #endif
            float vpIsoLampEvaluation=0.0;
            float vpIsoReceiverOutdoors=0.0;
            float lampVisibility(int i,vec3 pos,vec3 n);
            """+expansion.expand("constants.glsl")+expansion.expand("lib/sun.glsl")
                +expansion.expand("lib/spots.glsl")+expansion.expand("lib/shade.glsl")+expansion.expand("lib/near_light.glsl")+"""
            vec3 vpIsoSpotLight(vec3 pos,vec3 n,float amount) {
              vec3 sum=vec3(0.0);
              if(vpIsoSpotsOn==0)return sum;
              for(int i=0;i<uSpotCount;i++) {
                Spot s=spotAt(i);
                vec3 mapped=spotLight(s,pos,n,false);
                s.map=-1;
                sum+=mix(spotLight(s,pos,n,false),mapped,amount);
              }
              return sum;
            }
            float lampVisibility(int i,vec3 pos,vec3 n) {
            #if PIPELINE_LAMP_SHADOWS
              // Keep real wall/window transmission for other-room lamps in both
              // evaluations: their unoccluded light is absent from native light.
              if((uLightOutdoors[i]>0.5)!=(vpIsoReceiverOutdoors>0.5))
                return vpIsoMappedLampVisibility(i,pos,n);
            #endif
              return mix(1.0,vpIsoMappedLampVisibility(i,pos,n),vpIsoLampEvaluation);
            }
            bool vpIsoGrid(vec3 pos,vec3 n,float ownerBase,out vec4 grid,out float skyShare) {
              vec2 at=-(uSceneOrigin.xz+pos.xz)-n.xz*0.3;
              int level=int(floor((ownerBase+uSceneOrigin.y)/SQRT6+1.0e-4));
              ivec2 chunk=ivec2(floor(at/8.0));
              ivec3 place=ivec3(chunk&(NEAR_LIGHT_WINDOW-1),(level+NEAR_LIGHT_LIFT)&(NEAR_LIGHT_LEVELS-1));
              ivec4 t=texelFetch(uNearLight,place,0);
              if(t.x==0 || any(notEqual(t.yzw,ivec3(chunk,level))))return false;
              float slot=float(t.x-1);
              vec2 origin=vec2(mod(slot,LIGHT_COLUMNS),floor(slot/LIGHT_COLUMNS))*LIGHT_GRID;
              vec2 p=clamp(at-vec2(chunk)*8.0,0.01,7.99)+1.0;
              grid=gridLight(origin,p);skyShare=gridSky(origin,p);
              return true;
            }
            vec3 vpIsoIllumination(vec3 pos,vec3 n,vec3 grid,float outdoors,float skyShare,
                                    float lit,bool lampsOn,float lampAmount) {
              vec4 light=vec4(grid,outdoors);
              if(lampsOn) {
                vpIsoReceiverOutdoors=outdoors;
                vpIsoLampEvaluation=lampAmount;
            """+composition+"""
              }
              return VPISO_SELECTED_SHADE;
            }
            bool vpIsoReceivesSun(vec3 pos,vec3 n,float outdoors,float ownerBase) {
              if(outdoors>.5)return true;
              if(vpIsoNativeRegionsOn==0)return false;
              int level=int(floor((ownerBase+uSceneOrigin.y)/SQRT6+1.0e-4));
              if(level!=vpIsoNativeRegionLevel)return false;
              ivec2 square=ivec2(floor(-(uSceneOrigin.xz+pos.xz)-n.xz*.3))-vpIsoNativeRegionOrigin;
              if(any(lessThan(square,ivec2(0))) || any(greaterThanEqual(square,ivec2(64))))return false;
              return texelFetch(vpIsoNativeRegions,square,0).g>.5;
            }
            vec3 vpIsoLightingRatio(vec3 pos,vec3 n,float outdoors,float ownerBase,vec3 solarPos) {
              float sunAmount=shadowsOn!=0?clamp(shadowStrength,0.0,1.0):0.0;
              float lampAmount=vpIsoLampsOn!=0?clamp(vpIsoLampStrength,0.0,1.0):0.0;
              float spotAmount=vpIsoSpotsOn!=0?clamp(shadowStrength,0.0,1.0):0.0;
              if(sunAmount==0.0 && lampAmount==0.0 && spotAmount==0.0)return vec3(1.0);
              if(dot(n,n)<0.5)return vec3(1.0);
              n=normalize(n);
              vec4 grid=vec4(uSkyLevel,1.0);float skyShare=outdoors>0.5?1.0:0.0;
              bool found=vpIsoGrid(pos,n,ownerBase,grid,skyShare);
              if(!found) {grid=vec4(uSkyLevel,1.0);skyShare=outdoors>0.5?1.0:0.0;}
              // Floors have a native level but take their room flag from the actual grid.
              if(outdoors<0.0) { if(!found)return vec3(1.0);outdoors=grid.a; }
              // Foreground uses its validated owner, not the sky-share alpha from nearLight.
              outdoors=outdoors>0.5?1.0:0.0;
              bool lampsOn=found && lampAmount>0.0;
              float referenceLit=0.0,desiredLit=0.0;
              if(vpIsoReceivesSun(pos,n,outdoors,ownerBase) && uSunStrength>0.0 && dot(n,uSunDir)>0.0) {
                referenceLit=smoothstep(0.0,0.3,dot(n,uSunDir))*cloudShadow(solarPos);
                desiredLit=sunAmount>0.0?mix(referenceLit,sunlit(solarPos,n,vpIsoLightingEye,false),sunAmount):referenceLit;
              }
              vec3 reference=vpIsoIllumination(pos,n,grid.rgb,outdoors,skyShare,referenceLit,lampsOn,0.0);
              vec3 desired=vpIsoIllumination(pos,n,grid.rgb,outdoors,skyShare,desiredLit,lampsOn,lampAmount);
              // Compare each beam against its own unoccluded illumination. Other
              // lamps and ambient light remain in the denominator when a beam is blocked.
              spotOpen=skyShare;
              reference+=vpIsoSpotLight(pos,n,0.0);
              desired+=vpIsoSpotLight(pos,n,spotAmount);
              vec3 ratio=vec3(1.0);
              for(int c=0;c<3;c++) {
                if(reference[c]>1.0e-6) {
                  float value=desired[c]/reference[c];
                  if(!isnan(value) && !isinf(value))ratio[c]=max(value,0.0);
                }
              }
              return ratio;
            }
            vec3 vpIsoLightingRatio(vec3 pos,vec3 n,float outdoors,float ownerBase) {
              return vpIsoLightingRatio(pos,n,outdoors,ownerBase,pos);
            }
            vec3 vpIsoLightingRatio(vec3 pos,vec3 n,float outdoors) {
              return vpIsoLightingRatio(pos,n,outdoors,pos.y);
            }
            vec3 vpIsoRelightFloor(vec3 original,vec3 base,vec3 pos) {
              vec4 grid;float skyShare;
              if(!vpIsoGrid(pos,vec3(0,1,0),pos.y,grid,skyShare))return original;
              float sunAmount=shadowsOn!=0?clamp(shadowStrength,0.0,1.0):0.0;
              float lampAmount=vpIsoLampsOn!=0 && uLightCount>0?clamp(vpIsoLampStrength,0.0,1.0):0.0;
              float spotAmount=vpIsoSpotsOn!=0?clamp(shadowStrength,0.0,1.0):0.0;
              float outdoors=grid.a>.5?1.0:0.0;
              bool receivesSun=vpIsoReceivesSun(pos,vec3(0,1,0),outdoors,pos.y);
              if(!receivesSun)sunAmount=0.0;
              if(outdoors<.5) {
                bool ownedLamp=false;
            #if PIPELINE_LAMP_SHADOWS
                for(int i=0;i<uLightCount;i++)
                  if(uLightOutdoors[i]<.5 && length(uLightPos[i].xyz-pos)<uLightPos[i].w)ownedLamp=true;
            #endif
                if(!ownedLamp)lampAmount=0.0;
              }
              float amount=max(max(sunAmount,lampAmount),spotAmount);
              if(amount<=0.0)return original;
              float lit=receivesSun?sunlit(pos,vec3(0,1,0),vpIsoLightingEye,false):0.0;
              vec3 desired=vpIsoIllumination(pos,vec3(0,1,0),grid.rgb,outdoors,skyShare,
                                            lit,lampAmount>0.0,1.0);
              spotOpen=skyShare;
              desired+=vpIsoSpotLight(pos,vec3(0,1,0),1.0);
              // Strength blends complete illumination once; native tint/blackout
              // lives in base, and visibility edges never enter this path.
              return mix(original,base*desired,amount);
            }
            """;
        helpers=helpers.replace("VPISO_SELECTED_SHADE",shadeCall);
        // These controls must be declared before the injected functions, exactly once.
        base=base.replaceAll("\\buniform\\s+int\\s+shadowsOn\\s*;","")
                .replaceAll("\\buniform\\s+float\\s+shadowStrength\\s*;","")
                .replaceAll("\\buniform\\s+int\\s+vpIsoLampsOn\\s*;","")
                .replaceAll("\\buniform\\s+float\\s+vpIsoLampStrength\\s*;","");
        int at=base.indexOf('\n')+1;
        if(at==0 || !base.substring(0,at).contains("#version"))
            throw new IllegalStateException("Native lighting shader requires a version header");
        String header=base.substring(0,at)+"#extension GL_ARB_shader_storage_buffer_object : enable\n"
                +"#extension GL_ARB_shader_image_load_store : enable\n";
        return options(header+helpers+base.substring(at),header.length(),active);
    }

    /** One graph, with preprocessor guards even for VP files that have no own guard. */
    private static final class Expansion {
        private final ShaderPack pack;
        private final Map<String,String> sources=new HashMap<>(),expanded=new HashMap<>();
        private final Set<String> stack=new HashSet<>();
        Expansion(ShaderPack pack) {this.pack=pack;}
        String read(String path) {
            return sources.computeIfAbsent(path,key->(String)GameAccess.call("viewpoint.platform.Shaders","read",null,
                    new Class<?>[]{String.class,ShaderPack.class,boolean[].class},key,pack,new boolean[1]));
        }
        String expand(String path) {
            String cached=expanded.get(path);if(cached!=null)return cached;
            if(!stack.add(path))throw new IllegalStateException("Cyclic native lighting include: "+path);
            String guard="VPISO_LIGHT_RESOURCE_"+path.replaceAll("[^A-Za-z0-9]","_").toUpperCase(java.util.Locale.ROOT);
            StringBuilder out=new StringBuilder("\n#ifndef "+guard+"\n#define "+guard+"\n");
            String source=read(path);
            if(path.equals("lib/lamp_shadow.glsl")) {
                var definition=LAMP_VISIBILITY.matcher(source);
                if(!definition.find())throw new IllegalStateException("Unsupported VP lamp visibility function");
                source=definition.replaceFirst("float vpIsoMappedLampVisibility(");
            }
            if(path.equals("lib/sun_filter.glsl"))
                source=source.replace("uShadowMaps[c].z","(uShadowMaps[c].z*vpIsoNativeShadowSoftness)");
            // The unoccluded beam uses map=-1. NVIDIA can inspect its inlined
            // array accesses even after spotShadow's negative-map early return.
            // Keep that return and make every potentially folded index legal.
            if(path.equals("lib/spots.glsl"))
                source=source.replace("[s.map]","[max(s.map,0)]");
            for(String line:source.split("\n",-1)) {
                var include=INCLUDE.matcher(line);
                out.append(include.matches()?expand(include.group(1)):line+"\n");
            }
            stack.remove(path);out.append("\n#endif\n");
            String result=out.toString();expanded.put(path,result);return result;
        }
    }

    private static String options(String source,int at,ShaderPacks.Active active) {
        try {
            Class<?> body=GameAccess.type("viewpoint.platform.Shaders$Body");
            Constructor<?> constructor=body.getDeclaredConstructor(String.class,int.class,List.class,boolean.class);
            constructor.setAccessible(true);
            Object resolved=GameAccess.call("viewpoint.platform.Shaders","given",null,
                    new Class<?>[]{body,ShaderPacks.Active.class},
                    constructor.newInstance(source,at,List.of("VPISO native lighting"),true),active);
            return (String)GameAccess.field("viewpoint.platform.Shaders$Source","text",resolved);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Cannot resolve VP lighting options",error);}
    }

    public static Scope open(SceneData scene,NativeReceiver receiver) {
        Scope scope=new Scope(scene,receiver);
        try {scope.prepare();return scope;} catch(RuntimeException|Error error) {scope.close();throw error;}
    }

    /** Render-thread scratch only. No scene/world object is retained after close. */
    private static final java.util.ArrayDeque<Scratch> FREE=new java.util.ArrayDeque<>();
    private static final class Scratch {
        final FrameContext context=new FrameContext();
        final int[][] textures=new int[UNITS.length][4];
        final int[] pixel=new int[PIXEL_STATE.length],ubo=new int[3];
        final long[] uboStart=new long[3],uboSize=new long[3];
        final float[] facingEye=new float[3],inverse=new float[16];
    }
    private static Scratch acquire() {return FREE.isEmpty()?new Scratch():FREE.removeFirst();}

    /** Render-thread resource scope; preparation restores raster state before returning. */
    public static final class Scope implements AutoCloseable {
        private final Scratch scratch;
        private final FrameContext context;
        private final SceneData scene;
        private final Object previousContext;
        private final int active,uniformBuffer,packBuffer,unpackBuffer;
        private static final int[] UBO_BINDINGS={1,2,4};
        private final int[][] textures;
        private final int[] pixel,ubo;
        private final long[] uboStart,uboSize;
        private final float[] facingEye;
        private Object air,cloudNoise,weather,near,plan,shadows;
        private boolean ready,closed;

        private Scope(SceneData scene,NativeReceiver receiver) {
            this.scene=scene;
            scratch=scene==null?null:acquire();
            context=scratch==null?null:scratch.context;
            textures=scratch==null?null:scratch.textures;
            pixel=scratch==null?null:scratch.pixel;ubo=scratch==null?null:scratch.ubo;
            uboStart=scratch==null?null:scratch.uboStart;uboSize=scratch==null?null:scratch.uboSize;
            facingEye=scratch==null?null:scratch.facingEye;
            if(scene==null) {
                active=uniformBuffer=packBuffer=unpackBuffer=0;previousContext=null;
                return;
            }
            active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            uniformBuffer=GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
            packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            unpackBuffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            previousContext=GameAccess.field(FRAME,"current",null);
            for(int i=0;i<UNITS.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
                textures[i][0]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                textures[i][1]=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
                textures[i][2]=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
                textures[i][3]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,UNITS[i]);
            }
            GL13.glActiveTexture(active);
            for(int i=0;i<PIXEL_STATE.length;i++)pixel[i]=GL11.glGetInteger(PIXEL_STATE[i]);
            for(int i=0;i<ubo.length;i++) {
                ubo[i]=GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING,UBO_BINDINGS[i]);
                uboStart[i]=GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START,UBO_BINDINGS[i]);
                uboSize[i]=GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE,UBO_BINDINGS[i]);
            }
            context.scene=scene;context.pipeline=GlProgram.pack().pipeline;
            context.sun=scene.sunStrength>.02f;
            GameAccess.set(CONTEXT,"index",context,++sequence);
            GameAccess.call(CONTEXT,"carry",context,new Class<?>[]{long.class},System.nanoTime());
            float[] inverse=receiver.inverse.get(scratch.inverse);
            java.util.Arrays.fill(facingEye,0f);
            float x=inverse[8],y=-inverse[10]*2.4494896f,z=inverse[9];
            float length=(float)Math.sqrt(x*x+y*y+z*z);
            if(length>1e-8f) {facingEye[0]=x/length*10000f;facingEye[1]=y/length*10000f;facingEye[2]=z/length*10000f;}
            // The remote orthographic eye defines facing, not the atmosphere's altitude.
            // Native gameplay captures its logical eye 1.5 scene units above scene origin.
            context.eyeY=1.5f;
        }

        private void prepare() {
            if(scene==null)return;
            int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
            try {
                GameAccess.set(FRAME,"current",null,context);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                for(int unit:UNITS)GL33.glBindSampler(unit,0);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
                for(int state:PIXEL_STATE)GL11.glPixelStorei(state,state==GL11.GL_PACK_ALIGNMENT || state==GL11.GL_UNPACK_ALIGNMENT?4:0);
                GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(false);
                Object targets=GameAccess.field(RENDERER,"targets",null);
                near=GameAccess.field(RENDERER,"nearLight",null);
                plan=GameAccess.field(TARGETS,"floorPlan",targets);
                shadows=GameAccess.field(RENDERER,"shadows",null);
                weather=GameAccess.field(RENDERER,"weatherMap",null);
                air=GameAccess.field(TARGETS,"air",targets);
                cloudNoise=GameAccess.field(TARGETS,"cloudNoise",targets);
                // No lamp count prerequisite: the grid is also the sun receiver's engine light.
                GameAccess.call("viewpoint.render.NearLight","update",near,new Class<?>[]{SceneData.class},scene);
                GameAccess.call("viewpoint.render.FloorPlan","update",plan,
                        new Class<?>[]{GameAccess.type("viewpoint.render.PlanSnapshot")},scene.floorPlan);
                GameAccess.staticCall("viewpoint.render.LiveOptions","frame");
                if(context.pipeline.clouds) {
                    if(!uploaded(cloudNoise,"viewpoint.render.CloudNoise","read","failed"))return;
                    call("viewpoint.render.CloudNoise","bind",cloudNoise);
                }
                if(context.pipeline.atmosphere) {
                    if(!uploaded(air,"viewpoint.render.AirLuts","made","failed"))return;
                    call("viewpoint.render.AirLuts","bind",air);
                    for(String field:new String[]{"program","directionsProgram","ringsProgram","lightProgram"})
                        helper(air,"viewpoint.render.AirLuts",field);
                    GameAccess.call("viewpoint.render.AirLuts","draw",air,new Class<?>[]{FrameContext.class},context);
                }
                if(context.pipeline.clouds) {
                    helper(weather,"viewpoint.render.WeatherMap","program");
                    Object shadow=GameAccess.field("viewpoint.render.WeatherMap","shadow",weather);
                    helper(shadow,"viewpoint.render.CloudShadowMap","program");
                    GameAccess.call("viewpoint.render.WeatherMap","draw",weather,new Class<?>[]{FrameContext.class},context);
                }
                ready=true;
            } finally {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
                GL11.glPopClientAttrib();GL11.glPopAttrib();
                GL30.glBindVertexArray(vao);GL20.glUseProgram(program);GL13.glActiveTexture(active);
            }
        }

        private boolean uploaded(Object target,String type,String data,String failure) {
            if((Boolean)GameAccess.field(type,"uploaded",target))return true;
            // Avoid update() joining a still-running worker when VP's waits flag is enabled.
            if(GameAccess.field(type,data,target)==null && GameAccess.field(type,failure,target)==null)return false;
            call(type,"update",target);
            return (Boolean)GameAccess.field(type,"uploaded",target);
        }
        private void helper(Object target,String type,String field) {
            GlProgram program=(GlProgram)GameAccess.field(type,field,target);
            program.use();apply(program);
        }
        private void apply(GlProgram program) {
            GameAccess.call(FRAME,"apply",null,new Class<?>[]{GlProgram.class,FrameContext.class},program,context);
            if(program.has("uEye"))program.set("uEye",context.eyeX,context.eyeY,context.eyeZ);
            if(program.has("uEyeSky"))program.set("uEyeSky",scene.eyeSky);
            if(program.has("uFogColor"))program.set("uFogColor",scene.fogR,scene.fogG,scene.fogB);
            // The standalone compositor owns strength, rather than the model-path shader hook.
            if(program.has("vpIsoModels"))program.set("vpIsoModels",0f);
        }
        public boolean ready() {return ready && !closed;}

        public void uniforms(GlProgram program,int map,int count) {
            if(program.has("vpIsoNativeShadowSoftness"))program.set("vpIsoNativeShadowSoftness",1f);
            // Sampler types must have distinct units even while LUTs/scene are
            // unavailable and the shader takes only its native/style branch.
            GameAccess.call(FRAME,"units",null,new Class<?>[]{GlProgram.class},program);
            GameAccess.call("viewpoint.render.ShadowPass","samplers",null,new Class<?>[]{GlProgram.class},program);
            GameAccess.call("viewpoint.render.LampShadows","sampler",null,new Class<?>[]{GlProgram.class},program);
            GameAccess.call("viewpoint.render.Spots","sampler",null,new Class<?>[]{GlProgram.class},program);
            program.setInt("uSpotCount",0);program.setInt("vpIsoSpotsOn",0);
            if(program.has("uNearLight"))program.sampler("uNearLight",152);
            if(program.has("uLight"))program.sampler("uLight",5);
            if(program.has("uCloudShadow"))program.sampler("uCloudShadow",130);
            if(program.has("uWeatherNoise"))program.sampler("uWeatherNoise",131);
            if(!ready())return;
            apply(program);
            GameAccess.call("viewpoint.render.ShadowPass","sunUniforms",shadows,
                    new Class<?>[]{GlProgram.class,FrameContext.class},program,context);
            for(int layer=Math.max(0,Math.min(count,3));layer<5;layer++)program.set("uShadowMaps["+layer+"]",0f,1f,1f,0f);
            if(map==0)for(int layer=0;layer<3;layer++)program.set("uShadowMaps["+layer+"]",0f,1f,1f,0f);
            if(program.has("uShadowPhase"))program.set("uShadowPhase",0f);
            if(program.has("vpIsoLightingEye"))program.set("vpIsoLightingEye",facingEye[0],facingEye[1],facingEye[2]);
            if(program.has("uTorchCount"))program.setInt("uTorchCount",0);
            NativeSpotShadows.uniforms(program);
            if(context.pipeline.atmosphere)call("viewpoint.render.AirLuts","bind",air);
            if(context.pipeline.clouds)call("viewpoint.render.CloudNoise","bind",cloudNoise);
            GameAccess.call("viewpoint.render.WeatherMap","bind",weather,new Class<?>[]{boolean.class},true);
            GameAccess.call("viewpoint.render.FloorPlan","bind",plan,NONE);
            GameAccess.call("viewpoint.render.NearLight","uniforms",near,new Class<?>[]{GlProgram.class},program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+6);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,map);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+30);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,0);
            for(int unit:UNITS)GL33.glBindSampler(unit,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }

        @Override public void close() {
            if(closed)return;closed=true;
            if(scene==null)return;
            GameAccess.set(FRAME,"current",null,previousContext);
            for(int i=0;i<ubo.length;i++) {
                if(ubo[i]!=0 && uboSize[i]>0)GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER,UBO_BINDINGS[i],ubo[i],uboStart[i],uboSize[i]);
                else GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,UBO_BINDINGS[i],ubo[i]);
            }
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,uniformBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpackBuffer);
            for(int i=0;i<PIXEL_STATE.length;i++)GL11.glPixelStorei(PIXEL_STATE[i],pixel[i]);
            for(int i=0;i<UNITS.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i][0]);
                GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,textures[i][1]);
                GL11.glBindTexture(GL12.GL_TEXTURE_3D,textures[i][2]);GL33.glBindSampler(UNITS[i],textures[i][3]);
            }
            GL13.glActiveTexture(active);context.scene=null;
            FREE.addFirst(scratch);
        }
    }

    private static void call(String type,String method,Object target) {GameAccess.call(type,method,target,NONE);}
    private NativeLighting() {}
}
