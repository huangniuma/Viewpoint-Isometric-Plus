package vpiso;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** Adapts native tree fill to the selected world light; native geometry and alpha stay native. */
public final class NativeTreeLighting {
    private static final Class<?>[] PROGRAM={GlProgram.class};
    private static final String VERTEX="""
        #version 330
        layout(location=0) in vec3 aPosition;
        layout(location=1) in vec4 aColor;
        layout(location=2) in vec2 aUv;
        uniform mat4 vpTreeMvp, vpTreeInverse;
        uniform float vpTreeDepth;
        out vec2 vTreeUv;
        out vec4 vColor;
        out vec3 vTreePos;
        void main() {
          vec4 clip=vpTreeMvp*vec4(aPosition,1.0);
          vec4 pos=vpTreeInverse*clip;
          vTreePos=pos.xyz/pos.w; vTreeUv=aUv; vColor=aColor;
          // Match the original VBO vertex shader, including 095's stable offsets.
          float depth=(clip.z+1.0)*0.5+vpTreeDepth;
          clip.z=depth*2.0-1.0; gl_Position=clip;
        }
        """;
    private static final String MAIN="""
        uniform sampler2D vpTreeTexture;
        uniform int vpTreeLevel, vpTreeHighlight;
        uniform vec3 vpTreeOrigin;
        uniform vec2 vpTreeSize;
        uniform mat4 vpTreeProjection, vpTreeInverse;
        uniform float vpTreeDepth;
        uniform float vpTreeGroundDepth;
        in vec2 vTreeUv;
        in vec4 vColor;
        in vec3 vTreePos;
        vec3 vPos;
        vec4 vpTreeArt, vpTreeEngine;
        float vpTreeOutdoors;
        vec4 vpTreeGrid() {
          vec2 square=-vPos.xz-vpTreeOrigin.xz;
          ivec2 chunk=ivec2(floor(square/8.0));
          ivec4 t=texelFetch(uNearLight,ivec3(chunk&31,(vpTreeLevel+32)&15),0);
          bool found=t.x!=0 && t.yzw==ivec3(chunk,vpTreeLevel);
          if(!found && uNearLightElse==0) {vpTreeOutdoors=1.0;return vec4(uSkyLevel,1.0);}
          float slot=float(found?t.x-1:uNearLightElse-1);
          vec2 origin=vec2(mod(slot,LIGHT_COLUMNS),floor(slot/LIGHT_COLUMNS))*LIGHT_GRID;
          vec2 p=clamp(square-vec2(chunk)*8.0,0.01,7.99)+1.0;
          vec4 grid=gridLight(origin,p);vpTreeOutdoors=grid.a;
          return vec4(grid.rgb,gridSky(origin,p));
        }
        """;
    private static final String COMPOSE="""
        void main() {
          vpTreeArt=texture(vpTreeTexture,vTreeUv);
          if(vpTreeArt.a<0.01)discard;
          vPos=vTreePos;
          gl_FragDepth=gl_FragCoord.z;
          float floorY=float(vpTreeLevel)*2.4494896-vpTreeOrigin.y;
          if(vPos.y<floorY) {
            // The illustration spreads below the upright trunk. Lay that skirt
            // onto its own floor along the same screen ray, preserving its art
            // and native order while allowing foreground objects to occlude it.
            vec2 screen=gl_FragCoord.xy/vpTreeSize*2.0-1.0;
            vec4 nearPoint=vpTreeInverse*vec4(screen,-1.0,1.0);
            vec4 farPoint=vpTreeInverse*vec4(screen,1.0,1.0);
            vec3 start=nearPoint.xyz/nearPoint.w;
            vec3 ray=farPoint.xyz/farPoint.w-start;
            if(abs(ray.y)>0.001) {
              vPos=start+ray*((floorY+0.002-start.y)/ray.y);
              vec4 ground=vpTreeProjection*vec4(vPos,1.0);
              // Identical floor pixels must have identical depth for every root.
              // Carry only the native stable ownership/leaf offset into that plane.
              gl_FragDepth=ground.z/ground.w*0.5+0.5+vpTreeDepth/ground.w+vpTreeGroundDepth;
            }
          }
          vUv=gl_FragCoord.xy/vpTreeSize;
          vpTreeEngine=vpTreeGrid();
          vpTreeSelectedLight();
          if(vpTreeHighlight!=0)fragColor.rgb=vpTreeArt.rgb*vColor.rgb;
          fragColor.a=vpTreeArt.a*vColor.a;
        }
        """;
    private static GlProgram program;
    private static int materialId;
    private static Object pack;
    private static FrameContext active;
    private static Set<Object> highlighted;
    private static Object tree;
    private static final Matrix4f mvp=new Matrix4f();
    private static Object projectionStack,modelStack;
    private static boolean mvpReady;

    public static void begin(FrameContext context,Set<Object> highlights) {
        if(active!=null)throw new IllegalStateException("Nested native tree light");
        Object surfaces=GameAccess.field("viewpoint.render.WorldRenderer","surfaces",null);
        int selectedId=((GlProgram)GameAccess.field("viewpoint.render.SurfacePass","light",surfaces)).id();
        if(program==null || pack!=GlProgram.pack() || materialId!=selectedId) {
            GlProgram next=GlProgram.fromSources("VPISO native tree light",
                    new GlProgram.Sources(VERTEX,null,null,null,source()),
                    Map.of(),Map.of(),List.of("VPISO selected native tree light"));
            if(program!=null)program.delete();program=next;pack=GlProgram.pack();materialId=selectedId;
        }
        active=context;highlighted=highlights;
        Object core=GameAccess.staticCall("zombie.core.Core","getInstance");
        projectionStack=GameAccess.field("zombie.core.Core","projectionMatrixStack",core);
        modelStack=GameAccess.field("zombie.core.Core","modelViewMatrixStack",core);
        mvpReady=false;
        program.use();
        GameAccess.call("viewpoint.render.FrameUniforms","apply",null,new Class<?>[]{GlProgram.class,FrameContext.class},program,context);
        GameAccess.call("viewpoint.render.FrameUniforms","units",null,PROGRAM,program);
        Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
        GameAccess.call("viewpoint.render.FrameUniforms","bind",null,new Class<?>[]{targets.getClass()},targets);
        Object shadows=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        GameAccess.call("viewpoint.render.ShadowPass","samplers",null,PROGRAM,program);
        GameAccess.call("viewpoint.render.Spots","sampler",null,PROGRAM,program);
        GameAccess.call("viewpoint.render.LampShadows","sampler",null,PROGRAM,program);
        GameAccess.call("viewpoint.render.ShadowPass","sunUniforms",shadows,new Class<?>[]{GlProgram.class,FrameContext.class},program,context);
        GameAccess.call("viewpoint.render.SurfacePass","lamps",null,new Class<?>[]{GlProgram.class,SceneData.class},program,context.scene);
        GameAccess.call("viewpoint.render.LampShadows","uniforms",GameAccess.field("viewpoint.render.ShadowPass","lamps",shadows),
                new Class<?>[]{GlProgram.class,SceneData.class},program,context.scene);
        GameAccess.call("viewpoint.render.SurfacePass","torches",null,new Class<?>[]{GlProgram.class,FrameContext.class},program,context);
        GameAccess.call("viewpoint.render.MuzzleShadow","uniforms",GameAccess.field("viewpoint.render.ShadowPass","muzzle",shadows),PROGRAM,program);
        GameAccess.call("viewpoint.render.Spots","uniforms",GameAccess.field("viewpoint.render.ShadowPass","spots",shadows),PROGRAM,program);
        GameAccess.call("viewpoint.render.ShadowPass","bind",shadows,new Class<?>[]{boolean.class},true);
        GameAccess.call("viewpoint.render.NearLight","uniforms",GameAccess.field("viewpoint.render.WorldRenderer","nearLight",null),PROGRAM,program);
        program.set("uEye",context.eyeX,context.eyeY,context.eyeZ);
        program.set("uFogColor",context.scene.fogR,context.scene.fogG,context.scene.fogB);
        program.set("vpTreeOrigin",(float)context.scene.originX,(float)context.scene.originY,(float)context.scene.originZ);
        program.set("vpTreeInverse",context.invViewProjection);
        program.set("vpTreeProjection",context.viewProjection);
        program.set("vpTreeSize",(float)context.width,(float)context.height);
        program.setInt("vpTreeTexture",0);
        GameAccess.staticCall("zombie.core.ShaderHelper","forgetCurrentlyBound");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    public static void end() {
        try {
            if(active!=null)GameAccess.call("viewpoint.render.ShadowPass","bind",
                    GameAccess.field("viewpoint.render.WorldRenderer","shadows",null),new Class<?>[]{boolean.class},false);
        } finally {active=null;highlighted=null;tree=null;projectionStack=modelStack=null;mvpReady=false;}
    }
    public static Object tree(Object current) {
        Object previous=tree;
        if(active!=null) {tree=current;mvpReady=false;}
        return previous;
    }

    /** Runs immediately around the original VBO draw, after it binds textures and matrices. */
    public static int beforeDraw(Object run) {
        if(active==null || tree==null)return -1;
        Object outline=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderTrees","shader",null);
        Object nativeProgram=GameAccess.field("zombie.core.opengl.VBORenderer$Run","shaderProgram",run);
        if(outline!=null && nativeProgram
                ==GameAccess.call("zombie.core.skinnedmodel.shader.Shader","getShaderProgram",outline,new Class<?>[0]))return -1;
        // startRun binds this run's program immediately before renderRun. Keep a
        // fallback for external producers without a native program owner.
        int previous=nativeProgram==null?GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                :((Number)GameAccess.call("zombie.core.opengl.ShaderProgram","getShaderID",nativeProgram,new Class<?>[0])).intValue();
        // renderTree pushes its root once; bark, crown and stencil draws share it.
        if(!mvpReady) {
            mvp.set((Matrix4f)GameAccess.call("zombie.core.opengl.MatrixStack","peek",projectionStack,new Class<?>[0]))
                    .mul((Matrix4f)GameAccess.call("zombie.core.opengl.MatrixStack","peek",modelStack,new Class<?>[0]));
            mvpReady=true;
        }
        // Uniforms were bound once for this batch. Avoid per-tree frame setup or GPU reads.
        GL20.glUseProgram(program.id());
        program.set("vpTreeMvp",mvp);
        Object vbo=GameAccess.staticCall("zombie.core.opengl.VBORenderer","getInstance");
        Float forced=(Float)GameAccess.field("zombie.core.opengl.VBORenderer","forceUserDepth",vbo);
        program.set("vpTreeDepth",forced!=null?forced:((Number)GameAccess.field("zombie.core.opengl.VBORenderer$Run","userDepth",run)).floatValue());
        program.set("vpTreeGroundDepth",RotationTrees.groundDepth(tree));
        program.setInt("vpTreeLevel",(int)((Number)GameAccess.field("zombie.iso.fboRenderChunk.FBORenderTrees$Tree","z",tree)).floatValue());
        program.setInt("vpTreeHighlight",highlighted.contains(tree)?1:0);
        return previous;
    }
    public static void afterDraw(int previous) {if(previous>=0)GL20.glUseProgram(previous);}

    private static String source() {
        var selected=GlProgram.pack();
        Object body=GameAccess.call("viewpoint.platform.Shaders","expand",null,
                new Class<?>[]{String.class,viewpoint.platform.ShaderPack.class},"light.frag",selected.pack);
        String source=(String)GameAccess.field("viewpoint.platform.Shaders$Body","head",body);
        String tail=(String)GameAccess.field("viewpoint.platform.Shaders$Body","tail",body);
        if(tail!=null)source+=tail;
        // The opaque tile list was built before these alpha trees supplied depth.
        // Select VP's existing bounded full-list implementation at compile time.
        source=source.replace("#define VIEWPOINT_LAMP_LIST","// Native trees use the existing bounded full lamp list.");
        // These cards retain native alpha and are absent from the opaque G-buffer.
        // Supply the same foliage material/grid inputs to the pack's actual light code.
        source=source.replace("in vec2 vUv;","vec2 vUv;")
                .replace("void main()","void vpTreeSelectedLight()")
                .replace("texture(uDepth, vUv).r","gl_FragCoord.z")
                .replace("texture(uAlbedo, vUv)","vec4(vpTreeArt.rgb,1.0)")
                .replace("gNormalUnpack(texture(uNormal, vUv))","vec4(0.0,1.0,0.0,vpTreeOutdoors)")
                .replace("texture(uEngineLight, vUv)","vpTreeEngine")
                .replace("scenePos(vUv, depth)","vPos")
                .replace("occlusionAt(ivec2(gl_FragCoord.xy), n)","vec4(n,1.0)")
                .replace("contactShadowAt(ivec2(gl_FragCoord.xy), length(pos - uEye))","1.0")
                .replace("bounceAt(pos, n)","vec3(0.0)").replace("flashBounceAt(pos, n)","vec3(0.0)");
        Set<String> seen=new HashSet<>();seen.add("constants.glsl");
        if(source.contains("#define VIEWPOINT_LIGHT_GRID"))seen.add("lib/light_grid.glsl");
        String grid=source.contains("vec4 nearLight(")?"":NativeWaterShader.expandActive("lib/near_light.glsl",seen,selected);
        int main=source.indexOf("void vpTreeSelectedLight()");
        if(main<0)throw new IllegalStateException("Unsupported selected tree light entry");
        source=source.substring(0,main)+grid+MAIN+source.substring(main)+COMPOSE;
        int version=source.indexOf('\n')+1;
        source=source.substring(0,version)+"#undef PIPELINE_WATER_FFT\n"+source.substring(version);
        return NativeWaterShader.overlayOptions(source,selected);
    }
    private NativeTreeLighting() {}
}
