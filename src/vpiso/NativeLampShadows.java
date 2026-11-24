package vpiso;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** VP's existing lamp maps and captured light field, without its world colour pass. */
public final class NativeLampShadows {
    private static final String LAMPS="viewpoint.render.LampShadows", NEAR="viewpoint.render.NearLight";
    private static final Class<?>[] NONE={};
    private static final FrameContext context=new FrameContext();
    private static final Pattern INCLUDE=Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final int[] UNITS={5,49,110,111,152};
    private static final int[] TARGETS={GL11.GL_TEXTURE_2D,GL30.GL_TEXTURE_2D_ARRAY,GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_3D};
    private static final int[] BINDINGS={GL11.GL_TEXTURE_BINDING_2D,GL30.GL_TEXTURE_BINDING_2D_ARRAY,GL30.GL_TEXTURE_BINDING_2D_ARRAY,GL11.GL_TEXTURE_BINDING_2D,GL12.GL_TEXTURE_BINDING_3D};
    private static Object lamps,near,fields;
    private static SceneData scene;
    private static boolean ready;

    public static void reset() { ready=false; scene=null; }
    public static boolean ready() { return ready; }
    public static void invalidate(Object shadows) {
        Object pass=GameAccess.field("viewpoint.render.ShadowPass","lamps",shadows);
        java.util.Arrays.fill((boolean[])GameAccess.field(LAMPS,"drawn",pass),false);
    }

    /** Called within the native shadow producer's prepared model scope. */
    public static boolean draw(Object shadows,SceneData value) {
        reset();
        context.pipeline=GlProgram.pack().pipeline;
        if(value.lampShadowCount==0 || context.pipeline.lampShadows==0) return false;
        lamps=GameAccess.field("viewpoint.render.ShadowPass","lamps",shadows);
        near=GameAccess.field("viewpoint.render.WorldRenderer","nearLight",null);
        Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
        Object plan=GameAccess.field("viewpoint.render.Targets","floorPlan",targets);
        fields=GameAccess.field("viewpoint.render.Targets","lampFields",targets);
        FrameContext ordinary=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        var previous=ordinary.pipeline;
        int[] saved=save();
        try {
            ordinary.pipeline=context.pipeline;context.scene=value;
            GameAccess.call(NEAR,"update",near,new Class<?>[]{SceneData.class},value);
            GameAccess.call("viewpoint.render.FloorPlan","update",plan,
                    new Class<?>[]{GameAccess.type("viewpoint.render.PlanSnapshot")},value.floorPlan);
            GameAccess.call("viewpoint.render.FloorPlan","bind",plan,NONE);
            GlProgram fieldProgram=(GlProgram)GameAccess.field("viewpoint.render.LampFields","program",fields);
            fieldProgram.use();origin(fieldProgram,value);
            GameAccess.call("viewpoint.render.LampFields","draw",fields,new Class<?>[]{SceneData.class},value);
            GameAccess.call(LAMPS,"draw",lamps,new Class<?>[]{FrameContext.class},context);
            ready=((Number)GameAccess.field(LAMPS,"count",lamps)).intValue()>0;
            if(ready)scene=value;
            return ready;
        } finally {
            ordinary.pipeline=previous;context.scene=null;restore(saved);
        }
    }

    /** Texture state is owned by the surrounding compositor, including these VP units. */
    public static void uniforms(GlProgram program) {
        program.setInt("vpIsoLampsOn",ready?1:0);
        if(!ready)return;
        origin(program,scene);
        program.set("uSkyLevel",scene.skyR,scene.skyG,scene.skyB);
        GameAccess.call("viewpoint.render.SurfacePass","lamps",null,
                new Class<?>[]{GlProgram.class,SceneData.class},program,scene);
        GameAccess.call(LAMPS,"uniforms",lamps,new Class<?>[]{GlProgram.class,SceneData.class},program,scene);
        GameAccess.call(NEAR,"uniforms",near,new Class<?>[]{GlProgram.class},program);
        GameAccess.call(LAMPS,"bind",lamps,new Class<?>[]{boolean.class},true);
        GameAccess.call("viewpoint.render.LampFields","bind",fields,NONE);
        program.sampler("uLampShadow",49);program.sampler("uLampFields",111);
        for(int unit:UNITS)GL33.glBindSampler(unit,0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void origin(GlProgram program,SceneData value) {
        program.set("uSceneOrigin",(float)value.originX,(float)value.originY,(float)value.originZ);
        Object plan=value.floorPlan;
        program.set("uPlanCorner",((Number)GameAccess.field("viewpoint.render.PlanSnapshot","chunkX",plan)).floatValue()*8f,
                ((Number)GameAccess.field("viewpoint.render.PlanSnapshot","chunkY",plan)).floatValue()*8f,
                ((Number)GameAccess.field("viewpoint.render.PlanSnapshot","level",plan)).floatValue());
    }

    public static int[] save() {
        int[] saved=new int[1+UNITS.length*2];saved[0]=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for(int i=0;i<UNITS.length;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
            saved[1+i*2]=GL11.glGetInteger(BINDINGS[i]);
            saved[2+i*2]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,UNITS[i]);
        }
        GL13.glActiveTexture(saved[0]);return saved;
    }
    public static void restore(int[] saved) {
        for(int i=0;i<UNITS.length;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
            GL11.glBindTexture(TARGETS[i],saved[1+i*2]);GL33.glBindSampler(UNITS[i],saved[2+i*2]);
        }
        GL13.glActiveTexture(saved[0]);
    }

    /** Expand VP's selected resources; the addon does not ship a copy of its filters. */
    public static String fragment(String base) {
        String helpers="uniform int uLightCount; uniform vec4 uLightPos[48]; uniform vec3 uLightColor[48];\n"
                +expand("lib/lamp_shadow.glsl",new HashSet<>())
                +expand("lib/near_light.glsl",new HashSet<>())+"""
            uniform int vpIsoLampsOn;
            uniform float vpIsoLampStrength;
            vec3 vpIsoLampNormal(sampler2D positions,vec2 at,vec4 center) {
              vec4 r=textureOffset(positions,at,ivec2(1,0)),l=textureOffset(positions,at,ivec2(-1,0));
              vec4 u=textureOffset(positions,at,ivec2(0,1)),d=textureOffset(positions,at,ivec2(0,-1));
              float dr=abs(r.a)>.5?distance(r.xyz,center.xyz):1e10;
              float dl=abs(l.a)>.5?distance(l.xyz,center.xyz):1e10;
              float du=abs(u.a)>.5?distance(u.xyz,center.xyz):1e10;
              float dd=abs(d.a)>.5?distance(d.xyz,center.xyz):1e10;
              if(min(dr,dl)>.5 || min(du,dd)>.5)return vec3(0);
              vec3 dx=dr<=dl?r.xyz-center.xyz:center.xyz-l.xyz;
              vec3 dy=du<=dd?u.xyz-center.xyz:center.xyz-d.xyz;
              vec3 n=cross(dx,dy);float area=length(n);
              return area>1e-8?n/area:vec3(0);
            }
            float vpIsoLampKeep(vec3 pos,vec3 normal) {
              if(dot(normal,normal)<.5)return 1.0;
              vec2 square=-(uSceneOrigin.xz+pos.xz)-normal.xz*.3;
              int level=int(floor((pos.y+uSceneOrigin.y)/SQRT6+1.0e-4));
              // Unknown chunks must not turn into fully shadowed native artwork.
              if(!nearLightFound(square,level)) return 1.0;
              vec4 light=nearLight(-(uSceneOrigin.xz+pos.xz),level,normal);
              return lampShadowKeep(light.rgb,pos,normal,light.a);
            }
            """;
        int at=base.indexOf('\n')+1;
        return base.substring(0,at)+"#define VPISO_LAMP_SHADOWS 1\n"
                +"#extension GL_ARB_shader_storage_buffer_object : enable\n#extension GL_ARB_shader_image_load_store : enable\n"
                +(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",GlProgram.pack())
                +helpers+base.substring(at);
    }
    private static String expand(String path,Set<String> seen) {
        if(!seen.add(path))throw new IllegalStateException("Cyclic lamp shader include: "+path);
        String source=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,
                new Class<?>[]{String.class,viewpoint.platform.ShaderPack.class,boolean[].class},
                path,ShaderPacks.active().pack,new boolean[1]);
        StringBuilder out=new StringBuilder();
        for(String line:source.split("\n",-1)) {
            var include=INCLUDE.matcher(line);
            if(include.matches())out.append(expand(include.group(1),seen));else out.append(line).append('\n');
        }
        seen.remove(path);return out.toString();
    }
    private NativeLampShadows() {}
}
