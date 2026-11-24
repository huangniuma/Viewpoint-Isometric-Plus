import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.platform.Pipeline;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import vpiso.*;

/** Actual VP wave producer and rewritten Library lighting on a hidden GPU context. */
public final class WaterShaderProbe {
    private static final int SIDE = 32;
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final IsometricEffects WATER = new IsometricEffects(false,0,0,64,1024,false,0,1,1,1,true);
    private static String defines;
    private static int fbo, vao, waves, grid, albedo;
    private static GlProgram program;
    private static Frame frame;
    private static FrameContext context;

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(SIDE, SIDE, "VPISO water regression", 0, 0);
        if (window == 0) throw new AssertionError("Hidden GL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null,
                    new Class<?>[] {java.io.File.class}, (Object) null);
            ShaderPacks.useBuiltIn("library", "high", Map.of("upscaling", "off"));
            defines = (String) GameAccess.field("viewpoint.platform.ShaderPacks$Active", "defines", ShaderPacks.active());
            GlProgram.onUse(IsometricShaders::onUse);
            viewpoint.platform.Gl.init();
            frame = new Frame(); environment(true); frame.scene.cloudTime = 30f; frame.scene.windX = 2.4f; frame.scene.windZ = .8f;
            context = new FrameContext(); context.scene = frame.scene; context.pipeline = ShaderPacks.active().pipeline;
            checkProducer();
            setupLighting();
            checkWater();
            checkProductionLighting();
            System.out.println("Water shader GPU PASS: actual VP WaveTile creates non-flat 128x128x4 waves; Library model-only water lighting changes over time and shore distance; actual Atmosphere/Daylight/FrameUniforms/SkyPass/SurfacePass day/night without atmospheric LUTs; near/remote-camera footprint; original ground, VP/native-overlay isolation; complete Pipeline reference/knob restoration; no game/settings/map writes.");
        } finally {
            IsometricBridge.rendering(null);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    private static void checkProducer() throws Exception {
        Constructor<?> constructor = GameAccess.type("viewpoint.render.WaveTile").getDeclaredConstructor();
        constructor.setAccessible(true); Object producer = constructor.newInstance();
        Pipeline original = context.pipeline;
        if (original.water || original.waterFft) throw new AssertionError("Fixture must use real Library water gates");
        IsometricBridge.rendering(null);
        if (Patch_ModelWater.begin(context) != null) throw new AssertionError("Ordinary VP gate changed");
        waveDraw(producer);
        if (texture(producer) != 0) throw new AssertionError("Ordinary Library water allocated waves");
        IsometricBridge.rendering(frame);
        if (Patch_ModelWater.begin(context) != null) throw new AssertionError("Native overlay gate changed");
        IsometricBridge.modelRendering(frame, WATER);
        Pipeline saved = Patch_ModelWater.begin(context);
        try {
            if (saved != original || context.pipeline == original || !context.pipeline.water)
                throw new AssertionError("Model wave gate inactive");
            for (Field field : Pipeline.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive()
                        || field.getName().equals("water")) continue;
                field.setAccessible(true);
                if (!field.get(original).equals(field.get(context.pipeline)))
                    throw new AssertionError("Non-water Pipeline knob changed: " + field.getName());
            }
            waveDraw(producer);
        } finally { Patch_ModelWater.restore(context, saved); }
        if (context.pipeline != original || original.water) throw new AssertionError("Shared Pipeline changed");
        waves = texture(producer);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 14); GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, waves);
        if (GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_TEXTURE_WIDTH) != 128
                || GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY, 0, GL12.GL_TEXTURE_DEPTH) != 4)
            throw new AssertionError("Actual wave tile missing");
        FloatBuffer data = BufferUtils.createFloatBuffer(128 * 128 * 4 * 4);
        GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA, GL11.GL_FLOAT, data);
        float least = Float.POSITIVE_INFINITY, most = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 128 * 128 * 4; i += 4) { least = Math.min(least, data.get(i)); most = Math.max(most, data.get(i)); }
        if (!Float.isFinite(least) || most - least < 1e-4f) throw new AssertionError("Wave producer is flat: " + least + ".." + most);
        // The exit helper is also called by onThrowable advice; an exception must restore it.
        saved = Patch_ModelWater.begin(context);
        try { throw new IllegalStateException("fixture wave failure"); }
        catch (IllegalStateException expected) { }
        finally { Patch_ModelWater.restore(context, saved); }
        if (context.pipeline != original) throw new AssertionError("Failed wave draw leaked Pipeline");
        IsometricBridge.rendering(null);
    }

    private static void waveDraw(Object producer) {
        context.cloudTime = frame.scene.cloudTime;
        GameAccess.call("viewpoint.render.WaveTile", "draw", producer, new Class<?>[] {FrameContext.class}, context);
    }
    private static int texture(Object producer) {
        return ((Number) GameAccess.call("viewpoint.render.WaveTile", "texture", producer, new Class<?>[0])).intValue();
    }

    private static void setupLighting() throws Exception {
        String fragment = expand("light.frag", new HashSet<>());
        int version = fragment.indexOf('\n'); fragment = fragment.substring(0, version + 1)
                + "#extension GL_ARB_shader_storage_buffer_object : enable\n#extension GL_ARB_shader_image_load_store : enable\n"
                + defines + fragment.substring(version + 1);
        String vertex = "#version 330\nout vec2 vUv;\nvoid main(){ vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); vUv=p; gl_Position=vec4(p*2-1,0,1); }\n";
        program = GlProgram.fromSources("water-library", new GlProgram.Sources(vertex, null, null, null, fragment), Map.of(), Map.of("fragColor", 0), java.util.List.of());
        program.use();
        // Assign every sampler type a valid, distinct texture unit before rendering the actual light program.
        IntBuffer size = BufferUtils.createIntBuffer(1), type = BufferUtils.createIntBuffer(1);
        for (int i = 0; i < GL20.glGetProgrami(program.id(), GL20.GL_ACTIVE_UNIFORMS); i++) {
            String name = GL20.glGetActiveUniform(program.id(), i, 256, size, type);
            int unit = samplerUnit(type.get(0));
            if (unit >= 0) for (int k = 0; k < size.get(0); k++) program.setInt(name.replace("[0]", "[" + k + "]"), unit);
        }
        bind(8, GL11.GL_TEXTURE_2D, texture2D(16, 16, new float[] {1,1,1,1}));
        bind(10, GL11.GL_TEXTURE_2D, depth(GL11.GL_TEXTURE_2D));
        bind(11, GL30.GL_TEXTURE_2D_ARRAY, depth(GL30.GL_TEXTURE_2D_ARRAY));
        int blankArray = GL11.glGenTextures(); bind(12, GL30.GL_TEXTURE_2D_ARRAY, blankArray);
        GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_RGBA32F, 1, 1, 2, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
        filter(GL30.GL_TEXTURE_2D_ARRAY);
        // Buffer samplers are declared but not read with zero spot/torch counts.
        for (int unit : new int[] {13, 15}) {
            int buffer = GL15.glGenBuffers(); GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, 256, GL15.GL_STATIC_DRAW);
            bind(unit, GL31.GL_TEXTURE_BUFFER, GL11.glGenTextures());
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, unit == 13 ? GL30.GL_RGBA32F : GL30.GL_RGBA32UI, buffer);
        }
        albedo = texture2D(1,1,new float[] {.17f,.3f,.21f,.75f}); bind(0,GL11.GL_TEXTURE_2D,albedo); program.setInt("uAlbedo",0);
        bind(1,GL11.GL_TEXTURE_2D,texture2D(1,1,new float[]{.5f,1,1,1})); program.setInt("uNormal",1);
        bind(2,GL11.GL_TEXTURE_2D,texture2D(1,1,new float[]{.7f,.75f,.8f,1})); program.setInt("uEngineLight",2);
        bind(3,GL11.GL_TEXTURE_2D,texture2D(SIDE,SIDE,new float[]{.5f,.5f,.5f,1})); program.setInt("uDepth",3);
        grid=texture2D(1,1,new float[]{1,0,0,1}); bind(4,GL11.GL_TEXTURE_2D,grid); program.setInt("uWaterGrid",4);
        bind(14,GL30.GL_TEXTURE_2D_ARRAY,waves); program.setInt("uWaves",14);
        program.set("uExposureA",32,32); program.set("uExposureSize",64f);
        program.set("uSunDir",0,1,0); program.set("uSunColor",1,1,.95f); program.set("uSunStrength",1f); program.set("uShadowsOff",1f);
        program.set("uSkyLevel",frame.scene.skyR,frame.scene.skyG,frame.scene.skyB);
        program.set("uFogColor",frame.scene.fogR,frame.scene.fogG,frame.scene.fogB);
        if(program.has("uHorizon")) program.set("uHorizon",frame.scene.fogR,frame.scene.fogG,frame.scene.fogB);
        if(program.has("uZenith")) program.set("uZenith",frame.scene.zenithR,frame.scene.zenithG,frame.scene.zenithB);
        if(program.has("uSunTrue")) program.set("uSunTrue",0,1,0);
        if(program.has("uWind")) program.set("uWind",frame.scene.windX,frame.scene.windZ);
        program.set("uInvViewProjection", plane(32));
        camera(6000f);
        vao=GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
        int output=texture2D(SIDE,SIDE,new float[]{0,0,0,0});
        fbo=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Water FBO incomplete");
    }

    private static void checkWater() {
        IsometricBridge.rendering(null); drift(100); float[] ordinary=draw();
        IsometricBridge.modelRendering(frame,WATER); float[] model=draw();
        difference(model,ordinary,1e-3f,"Model water path inactive");
        float variation=0;
        for(int i=4;i<model.length;i+=4) variation=Math.max(variation,Math.abs(model[i]-model[0])+Math.abs(model[i+1]-model[1])+Math.abs(model[i+2]-model[2]));
        if(variation<1e-3f) throw new AssertionError("Actual model water remains flat");
        drift(102); float[] animated=draw(); difference(model,animated,1e-4f,"Actual wave drift does not animate water");
        update(grid,new float[]{0,0,0,1}); float[] shore=draw(); difference(animated,shore,1e-2f,"Shore-distance texture has no effect");
        IsometricBridge.rendering(frame); near(draw(),ordinary,2e-5f,"Water leaked to native overlay");
        IsometricBridge.rendering(null); near(draw(),ordinary,2e-5f,"Water leaked to normal VP");
        // Equal world footprint, same eye direction and same water point for both render distances.
        IsometricBridge.modelRendering(frame,WATER); update(grid,new float[]{1,0,0,1});
        program.set("uInvViewProjection",plane(0)); camera(600); float[] close=draw();
        camera(6000); near(draw(),close,2e-4f,"Remote isometric eye changes matched water footprint");
        // The matched-footprint comparison must not pass merely because both eyes disable filtering.
        program.set("uCamera",6000-256f,6000+256f,1f,64f/6000f);
        difference(draw(),close,1e-6f,"Water footprint does not respond to projection scale");
        // Non-water pixels continue through the existing Library lighting even in model scope.
        update(albedo,new float[]{.17f,.3f,.21f,0}); IsometricBridge.rendering(null); float[] ground=draw();
        IsometricBridge.modelRendering(frame,WATER); near(draw(),ground,2e-5f,"Water adaptation changed ordinary ground");
    }

    private static void drift(double time) {
        frame.scene.cloudTime=(float)time;
        GameAccess.call("viewpoint.render.WaveDrift","advance",null,new Class<?>[]{double.class,float.class,float.class},time,frame.scene.windX,frame.scene.windZ);
        FrameContext waveContext=new FrameContext(); waveContext.scene=frame.scene; waveContext.cloudTime=frame.scene.cloudTime;
        GameAccess.call("viewpoint.render.FrameUniforms","waves",null,new Class<?>[]{GlProgram.class,FrameContext.class},program,waveContext);
    }
    private static void camera(float distance) {
        program.set("uEye",-.61237246f*distance,.5f*distance,-.61237246f*distance);
        program.set("uCamera",distance-256,distance+256,1f,16f/distance);
    }
    private static Matrix4f plane(float scale) { return new Matrix4f().set(new float[]{scale,0,0,0,0,0,scale,0,0,0,0,0,0,0,0,1}); }
    private static float[] draw() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo); GL30.glBindVertexArray(vao);
        GL11.glViewport(0,0,SIDE,SIDE); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        program.use(); GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer data=BufferUtils.createFloatBuffer(SIDE*SIDE*4); GL11.glReadPixels(0,0,SIDE,SIDE,GL11.GL_RGBA,GL11.GL_FLOAT,data);
        int error=GL11.glGetError(); if(error!=0) throw new AssertionError("Water GL draw error "+error);
        float[] result=new float[data.remaining()]; data.get(result);
        for(float value:result) if(!Float.isFinite(value)) throw new AssertionError("Non-finite water result");
        return result;
    }

    /** Weather inputs are controlled; all sky colours come from VP's production producer. */
    private static void environment(boolean day) {
        frame.scene.sunTrueY=day?1f:-1f; frame.scene.sunY=1f; frame.scene.skySunY=1f;
        frame.scene.sunR=1f; frame.scene.sunG=1f; frame.scene.sunB=.95f;
        frame.scene.sunStrength=day?1f:.001f; frame.scene.skyGlow=day?1f:.001f;
        frame.scene.nightLight=.6666667f;
        GameAccess.call("viewpoint.environment.Atmosphere","skyColours",null,
                new Class<?>[]{viewpoint.render.SceneData.class,float.class,float.class,float.class},frame.scene,day?1f:0f,0f,0f);
        GameAccess.call("viewpoint.environment.Atmosphere","airLight",null,
                new Class<?>[]{viewpoint.render.SceneData.class},frame.scene);
        // This is the synthetic game/JNI skylight input, through the real Daylight snapshot.
        GameAccess.set("viewpoint.light.Daylight","fromAir",null,false);
        GameAccess.set("viewpoint.light.Daylight","level",null,day?0xCFBFAB:0x202028);
        viewpoint.light.Daylight.snapshot(frame.scene);
    }

    @SuppressWarnings("unchecked")
    private static void checkProductionLighting() throws Exception {
        viewpoint.render.WorldRenderer.prepare(Long.MAX_VALUE);
        viewpoint.platform.Gl.resetState();
        Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
        Object surfaces=GameAccess.field("viewpoint.render.WorldRenderer","surfaces",null);
        Object indirect=GameAccess.field("viewpoint.render.WorldRenderer","indirect",null);
        Object sky=GameAccess.field("viewpoint.render.WorldRenderer","sky",null);
        GlProgram original=(GlProgram)GameAccess.field("viewpoint.render.SurfacePass","light",surfaces);
        context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        context.pipeline=ShaderPacks.active().pipeline; context.scene=frame.scene;
        if(context.pipeline.atmosphere) throw new AssertionError("Fixture must retain real Library atmosphere=false");
        context.width=SIDE;context.height=SIDE;context.halfWidth=SIDE/2;context.halfHeight=SIDE/2;
        context.view=new Matrix4f(); context.invViewProjection.set(plane(0));
        context.eyeX=-3674.2347f;context.eyeY=3000f;context.eyeZ=-3674.2347f;
        frame.scene.projection.setPerspective(2f*(float)Math.atan(16f/6000f),1f,5744f,6256f);
        GameAccess.call("viewpoint.render.Targets","ensure",targets,
                new Class<?>[]{int.class,int.class,int.class,int.class,int.class},SIDE,SIDE,SIDE,SIDE,50);
        program.sampler("uAlbedo",9);program.sampler("uNormal",10);program.sampler("uEngineLight",11);program.sampler("uDepth",12);
        for(String owner:new String[]{"ShadowPass","Spots","LampShadows"})
            GameAccess.call("viewpoint.render."+owner,owner.equals("ShadowPass")?"samplers":"sampler",null,new Class<?>[]{GlProgram.class},program);
        java.util.List<GlProgram> all=(java.util.List<GlProgram>)GameAccess.field("viewpoint.platform.GlProgram","all",null);
        all.add(program);
        GameAccess.set("viewpoint.render.SurfacePass","light",surfaces,program);
        java.util.Arrays.fill(frame.scene.water,(byte)255);
        GameAccess.set("viewpoint.render.CloudNoise","waits",null,true);
        float dayWater=0f, dayMirror=0f;
        try {
            for(boolean day:new boolean[]{true,false}) {
                environment(day); context.sun=frame.scene.sunStrength>.02f;
                IsometricBridge.modelRendering(frame,WATER);
                // A freshly allocated GL texture has unspecified contents. Use a zero sentinel
                // in the actual LUT targets and prove Library's real producer leaves it untouched.
                Object air=GameAccess.field("viewpoint.render.Targets","air",targets);
                for(String name:new String[]{"fbo","lightFbo"}) {
                    int target=((Number)GameAccess.field("viewpoint.render.AirLuts",name,air)).intValue();
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);
                    GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,0,0});
                }
                GameAccess.call("viewpoint.render.FrameUniforms","set",null,
                        new Class<?>[]{FrameContext.class,GameAccess.type("viewpoint.render.Targets")},context,targets);
                GameAccess.call("viewpoint.render.SkyPass","prepare",sky,new Class<?>[]{FrameContext.class},context);
                GameAccess.call("viewpoint.render.IndirectPass","draw",indirect,new Class<?>[]{FrameContext.class,boolean.class},context,false);
                Object producer=GameAccess.field("viewpoint.render.IndirectPass","waves",indirect);
                Pipeline previous=Patch_ModelWater.begin(context);
                try{waveDraw(producer);}finally{Patch_ModelWater.restore(context,previous);}
                fillGbuffer(targets,day);
                float[] water=surfaceLight(surfaces,targets,0);
                float[] mirror=surfaceLight(surfaces,targets,12);
                float waterLuma=luma(water), mirrorLuma=luma(mirror);
                System.out.println("Water production "+(day?"day":"night")+": atmosphere="+context.pipeline.atmosphere
                        +" baseZenith="+java.util.Arrays.toString(frame.scene.skyBaseZenith)
                        +" fog="+frame.scene.fogR+","+frame.scene.fogG+","+frame.scene.fogB
                        +" rawWater="+java.util.Arrays.toString(java.util.Arrays.copyOf(water,4))
                        +" rawMirror="+java.util.Arrays.toString(java.util.Arrays.copyOf(mirror,4)));
                if(day) {
                    dayWater=waterLuma;dayMirror=mirrorLuma;
                    if(waterLuma<.02f || mirrorLuma<.1f)
                        throw new AssertionError("Clear-day Library water depends on unproduced AirLuts: water="+waterLuma+", mirror="+mirrorLuma);
                    // A production AirLuts object exists, but Library must not render atmospheric maps.
                    int skyView=((Number)GameAccess.field("viewpoint.render.AirLuts","skyView",air)).intValue();
                    bind(103,GL11.GL_TEXTURE_2D,skyView);
                    FloatBuffer raw=BufferUtils.createFloatBuffer(192*108*4);
                    GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,raw);
                    float most=0f;for(int i=0;i<raw.capacity();i++) most=Math.max(most,Math.abs(raw.get(i)));
                    if(most>.001f) throw new AssertionError("Fixture unexpectedly contains a rendered atmospheric sky");
                } else if(waterLuma>=dayWater*.5f || mirrorLuma>=dayMirror*.25f)
                    throw new AssertionError("Water adaptation forces daylight at night");
            }
            // Compare the real SurfacePass entry with the unmodified VP Library program.
            IsometricBridge.rendering(null);
            float[] ordinary=surfaceLight(surfaces,targets,0);
            GameAccess.set("viewpoint.render.SurfacePass","light",surfaces,original);
            near(surfaceLight(surfaces,targets,0),ordinary,2e-5f,"Production adaptation changed ordinary VP water");
            GameAccess.set("viewpoint.render.SurfacePass","light",surfaces,program);
            IsometricBridge.rendering(frame);
            near(surfaceLight(surfaces,targets,0),ordinary,2e-5f,"Production adaptation leaked to native overlay");
            checkNativeMaterialAppearance(surfaces,targets,original);
        } finally {
            GameAccess.set("viewpoint.render.SurfacePass","light",surfaces,original);
            all.remove(program);GameAccess.set("viewpoint.render.WorldRenderer","debugView",null,0);
            IsometricBridge.rendering(null);
        }
    }

    /** Compare against unmodified VP SurfacePass + PostPass, not a copy of our new formula. */
    private static void checkNativeMaterialAppearance(Object surfaces,Object targets,GlProgram original) throws Exception {
        environment(true);context.sun=true;IsometricBridge.rendering(null);
        GameAccess.set("viewpoint.render.SurfacePass","light",surfaces,original);
        GameAccess.call("viewpoint.render.FrameUniforms","set",null,
                new Class<?>[]{FrameContext.class,GameAccess.type("viewpoint.render.Targets")},context,targets);
        fillGbuffer(targets,true);
        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{.1f,.22f,.21f,.75f});
        GL30.glClearBufferfv(GL11.GL_COLOR,2,new float[]{frame.scene.skyR,frame.scene.skyG,frame.scene.skyB,1});
        float[] rawMaterial=surfaceLight(surfaces,targets,0);
        System.out.println("Library reference material RGBA="+java.util.Arrays.toString(java.util.Arrays.copyOf(rawMaterial,4)));
        int image=texture2D(SIDE,SIDE,new float[]{0,0,0,1}),output=GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,image,0);
        int depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,SIDE,SIDE);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
        Object post=GameAccess.field("viewpoint.render.WorldRenderer","post",null);
        GameAccess.call("viewpoint.render.PostPass","inputs",post,new Class<?>[]{FrameContext.class},context);
        GlProgram actualPost=(GlProgram)GameAccess.field("viewpoint.render.PostPass","post",post);
        int emptyLayers=texture2D(1,1,new float[]{0,0,0,0});
        int clearReveal=texture2D(1,1,new float[]{1,1,1,1});
        bind(3,GL11.GL_TEXTURE_2D,emptyLayers);bind(4,GL11.GL_TEXTURE_2D,clearReveal);
        actualPost.setInt("uTranslucentSum",3);actualPost.setInt("uTranslucentDistance",3);
        actualPost.setInt("uTranslucentReveal",4);
        actualPost.set("uVolumeOn",0f);actualPost.set("uBloomStrength",0f);
        actualPost.set("uFogRange",1e8f,2e8f);actualPost.set("uHaze",1e8f,2e8f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        float[] reference=readImage();
        // Uniform original water material, while the native backdrop is deliberately grey.
        Object page=NativeVPWaterProbe.materialPage();
        bind(7,GL11.GL_TEXTURE_2D,((Number)GameAccess.call("zombie.core.textures.TextureID","getID",page,new Class<?>[0])).intValue());
        FloatBuffer pixels=BufferUtils.createFloatBuffer(64*128*4);
        for(int i=0;i<64*128;i++)pixels.put(.1f).put(.22f).put(.21f).put(1f);
        pixels.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,64,128,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        NativeVPWaterOverlay.Snapshot water=new NativeVPWaterOverlay.Snapshot();float[] grid=new float[8*8*4];
        for(int i=0;i<64;i++){grid[i*4]=1;grid[i*4+2]=5;grid[i*4+3]=1;}
        water.fit(-4,-4,8,8,0,0,0,grid);
        water.material(page,new float[]{0,0,1,1,0,0,1f/64,1f/128},new float[]{frame.scene.skyR,frame.scene.skyG,frame.scene.skyB});
        NativeReceiver receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.5,.5},{.625,.5,.5},{.5,.625,.5},{.5,.5,.6}});
        GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClearColor(.45f,.45f,.45f,1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        new NativeVPWaterOverlay().draw(frame,receiver,water,true);
        float[] overlay=readImage();
        System.out.println("Library appearance first RGBA: native="+java.util.Arrays.toString(java.util.Arrays.copyOf(reference,4))+" overlay="+java.util.Arrays.toString(java.util.Arrays.copyOf(overlay,4)));
        near(overlay,reference,.006f,"Library independent water differs from actual VP material+light+post");
        if(luma(overlay)>.25f)throw new AssertionError("Library water whitened");
        System.out.println("Library water appearance PASS: unmodified installed SurfacePass.light + PostPass inputs/program versus standalone overlay, matching controlled albedo/daylight and isolated empty scene, max channel error <=.006. No Reinhard/gamma or 28% grey native residue. Actual map lighting/visibility/TAA are outside this comparison.");
    }
    private static float[] readImage(){float[] result=new float[SIDE*SIDE*4];GL11.glReadPixels(0,0,SIDE,SIDE,GL11.GL_RGBA,GL11.GL_FLOAT,result);return result;}

    private static void fillGbuffer(Object targets,boolean day) {
        int gbuffer=((Number)GameAccess.field("viewpoint.render.Targets","gbufferFbo",targets)).intValue();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,gbuffer);
        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{.17f,.3f,.21f,.75f});
        // VP 0.1.7 uses octahedral XY, outdoors in Z, normal magnitude in W.
        GL30.glClearBufferfv(GL11.GL_COLOR,1,new float[]{.5f,1,1,1});
        GL30.glClearBufferfv(GL11.GL_COLOR,2,day?new float[]{.7f,.75f,.8f,1}:new float[]{.07f,.075f,.08f,1});
        GL30.glClearBufferfi(GL30.GL_DEPTH_STENCIL,0,.5f,0);
    }
    private static float[] surfaceLight(Object surfaces,Object targets,int debug) {
        int target=((Number)GameAccess.field("viewpoint.render.Targets","lightFbo",targets)).intValue();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);GL11.glViewport(0,0,SIDE,SIDE);
        GameAccess.set("viewpoint.render.WorldRenderer","debugView",null,debug);
        GameAccess.call("viewpoint.render.SurfacePass","light",surfaces,new Class<?>[]{FrameContext.class},context);
        float[] pixels=new float[SIDE*SIDE*4];GL11.glReadPixels(0,0,SIDE,SIDE,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        int error=GL11.glGetError();if(error!=0) throw new AssertionError("Production water GL error "+error);
        for(float value:pixels) if(!Float.isFinite(value)) throw new AssertionError("Non-finite production water");
        return pixels;
    }
    private static float luma(float[] rgba) { return rgba[0]*.2126f+rgba[1]*.7152f+rgba[2]*.0722f; }

    private static int samplerUnit(int type) {
        return switch(type) { case GL20.GL_SAMPLER_2D -> 8; case GL20.GL_SAMPLER_2D_SHADOW -> 10;
            case GL30.GL_SAMPLER_2D_ARRAY_SHADOW -> 11; case GL30.GL_SAMPLER_2D_ARRAY -> 12;
            case GL31.GL_SAMPLER_BUFFER -> 13; case GL31.GL_UNSIGNED_INT_SAMPLER_BUFFER -> 15; default -> -1; };
    }
    private static int texture2D(int width,int height,float[] rgba) {
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        FloatBuffer data=BufferUtils.createFloatBuffer(width*height*4); for(int i=0;i<width*height;i++) data.put(rgba); data.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,width,height,0,GL11.GL_RGBA,GL11.GL_FLOAT,data); filter(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous); return texture;
    }
    private static int depth(int target) {
        int previous=GL11.glGetInteger(target==GL11.GL_TEXTURE_2D?GL11.GL_TEXTURE_BINDING_2D:GL30.GL_TEXTURE_BINDING_2D_ARRAY);
        int texture=GL11.glGenTextures(); GL11.glBindTexture(target,texture); FloatBuffer value=BufferUtils.createFloatBuffer(1).put(1f).flip();
        if(target==GL11.GL_TEXTURE_2D) GL11.glTexImage2D(target,0,GL30.GL_DEPTH_COMPONENT32F,1,1,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,value);
        else GL12.glTexImage3D(target,0,GL30.GL_DEPTH_COMPONENT32F,1,1,1,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,value);
        filter(target); GL11.glTexParameteri(target,GL14.GL_TEXTURE_COMPARE_MODE,GL30.GL_COMPARE_REF_TO_TEXTURE);
        GL11.glBindTexture(target,previous); return texture;
    }
    private static void filter(int target) { GL11.glTexParameteri(target,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST); GL11.glTexParameteri(target,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST); }
    private static void bind(int unit,int target,int texture) { GL13.glActiveTexture(GL13.GL_TEXTURE0+unit); GL11.glBindTexture(target,texture); }
    private static void update(int texture,float[] rgba) { bind(texture==grid?4:0,GL11.GL_TEXTURE_2D,texture); FloatBuffer data=BufferUtils.createFloatBuffer(4).put(rgba).flip(); GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,data); }
    private static void near(float[] a,float[] b,float tolerance,String message) { float delta=delta(a,b); if(delta>tolerance) throw new AssertionError(message+": "+delta); }
    private static void difference(float[] a,float[] b,float minimum,String message) { float delta=delta(a,b); if(delta<minimum) throw new AssertionError(message+": "+delta); }
    private static float delta(float[] a,float[] b) { float greatest=0; for(int i=0;i<a.length;i++) greatest=Math.max(greatest,Math.abs(a[i]-b[i])); return greatest; }
    private static String expand(String path,HashSet<String> stack) throws Exception {
        if(!stack.add(path)) throw new AssertionError("Include cycle "+path); String source;
        if(path.equals("constants.glsl")) source=(String)GameAccess.staticCall("viewpoint.platform.Shaders","constants");
        else try(InputStream input=GlProgram.class.getResourceAsStream("/viewpoint/shaders/"+path)) {
            if(input==null) throw new AssertionError("Missing VP resource "+path); source=new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }
        source=IsometricShaders.rewrite(path,source); StringBuilder result=new StringBuilder();
        for(String line:source.split("\n",-1)) { var match=INCLUDE.matcher(line); if(match.matches()) result.append(expand(match.group(1),stack)); else result.append(line).append('\n'); }
        stack.remove(path); return result.toString();
    }
}
