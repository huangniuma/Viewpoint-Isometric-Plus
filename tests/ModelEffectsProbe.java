import java.io.InputStream;
import java.nio.FloatBuffer;
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
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import vpiso.*;

/** Actual rewritten VP post/sun shaders, production uniform snapshots and real ShadowPass calls. */
public final class ModelEffectsProbe {
    private static final String VERTEX = """
        #version 330
        out vec2 vUv;
        void main() {
          vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
          vUv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static String defines;
    private static String pack = "library";
    private static int fbo, output, vao;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) pack = args[0];
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(16, 16, "VPISO model effects", 0, 0);
        if (window == 0) throw new AssertionError("Hidden GL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            Main.compatible = true;
            GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null,
                    new Class<?>[] { java.io.File.class }, (Object) null);
            TestShaderPacks.select(pack, pack.equals("somber") ? "quality" : "high", Map.of("upscaling", "off", "reflections", "off"));
            defines = (String) GameAccess.field("viewpoint.platform.ShaderPacks$Active", "defines", ShaderPacks.active());
            GlProgram.onUse(IsometricShaders::onUse);
            vao = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            output = texture2D(GL30.GL_RGBA32F, new float[] {0, 0, 0, 0});
            fbo = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, output, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("FBO incomplete");
            checkPost();
            checkSun();
            checkMaps();
            IsometricBridge.rendering(null);
            System.out.println("Model effects GPU PASS: pack=" + pack + "; actual rewritten VP HDR post and sun shaders; independent shadows/strength/softness/distance and style/strength/exposure/saturation/contrast; queued snapshots; ordinary VP/native-overlay isolation; actual ShadowPass resize/cascades/off path retains drawLights and immutable Pipeline; no game/map or settings writes.");
        } finally {
            IsometricBridge.rendering(null);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    private static void checkPost() throws Exception {
        GlProgram program = program("model-post", expand("post.frag", new HashSet<>()));
        int color = texture2D(GL30.GL_RGBA32F, new float[] {.18f, .56f, .97f, 1f});
        int depth = texture2D(GL30.GL_RGBA32F, new float[] {1, 1, 1, 1});
        program.use(); program.setInt("uColor", 0); program.setInt("uDepth", 1);
        program.setInt("uVolume", 2); program.setInt("uBloom", 2);
        // VP 0.1.7 composites translucent layers here. Supply its empty-layer
        // identity instead of letting the new samplers alias the scene color.
        int clear = texture2D(GL30.GL_RGBA32F, new float[] {0, 0, 0, 0});
        int reveal = texture2D(GL30.GL_RGBA32F, new float[] {1, 1, 1, 1});
        program.setInt("uTranslucentSum", 3); program.setInt("uTranslucentDistance", 3);
        program.setInt("uTranslucentReveal", 4);
        if (program.has("uSunShafts")) program.setInt("uSunShafts", 2);
        program.set("uExposure", 1f); program.set("uVolumeOn", 0f); program.set("uBloomStrength", 0f);
        program.set("uInvViewProjection", new Matrix4f());
        bind2D(0, color); bind2D(1, depth); bind2D(2, color);
        bind2D(3, clear); bind2D(4, reveal);
        IsometricBridge.rendering(null);
        float[] ordinary = draw(program);
        IsometricBridge.modelRendering(new Frame(), effects(false, 0, 1, 64, false, 1, 1, 1, 1));
        float[] off = draw(program);
        if (pack.equals("library")) near(off[2] - ordinary[2], .97f - shoulder(.97f), 2e-5f, "model style off neutralizes highlight curve");
        IsometricBridge.modelRendering(new Frame(), effects(false, 0, 1, 64, true, 1, 1, 1, 1));
        float[] on = draw(program);
        near(on[2], ordinary[2], 2e-5f, "default model style preserves VP curve");
        IsometricBridge.modelRendering(new Frame(), effects(false, 0, 1, 64, true, .5f, 1, 1, 1));
        near(draw(program)[2], (off[2] + on[2]) * .5f, 2e-5f, "style strength");
        for (int control = 0; control < 3; control++) {
            IsometricBridge.modelRendering(new Frame(), effects(false, 0, 1, 64, true, 1,
                    control == 0 ? 1.7f : 1f, control == 1 ? 0f : 1f, control == 2 ? 1.5f : 1f));
            float[] changed = draw(program);
            if (Math.abs(changed[0] - on[0]) + Math.abs(changed[1] - on[1]) + Math.abs(changed[2] - on[2]) < .02f)
                throw new AssertionError("Inactive model style control " + control);
            if (control == 1) near(changed[0], changed[1], 2e-5f, "saturation zero");
        }
        // Switch to a native-map render scope after model uniforms have been populated.
        IsometricBridge.rendering(new Frame());
        near(draw(program)[2], ordinary[2], 2e-5f, "native overlay does not inherit model style");
        IsometricBridge.rendering(null);
        near(draw(program)[2], ordinary[2], 2e-5f, "normal VP camera does not inherit model style");
        program.delete(); GL11.glDeleteTextures(color); GL11.glDeleteTextures(depth);
        GL11.glDeleteTextures(clear); GL11.glDeleteTextures(reveal);
    }

    private static void checkSun() throws Exception {
        String fragment = "#version 330\n" + expand("lib/sun.glsl", new HashSet<>()) + """
            uniform vec3 testPosition;
            out vec4 fragColor;
            void main() {
              fragColor = vec4(sunlit(testPosition, vec3(0,1,0), vec3(0,2,0), false), sunAt(testPosition), sunMap(2) ? 1 : 0, 1);
            }
            """;
        GlProgram program = program("model-sun", fragment);
        int clearCloud = texture2D(GL30.GL_RGBA32F, new float[] {1, 1, 1, 1});
        bind2D(2, clearCloud);
        program.use();
        if (program.has("uCloudShadow")) program.setInt("uCloudShadow", 2);
        int maps = GL11.glGenTextures(); GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, maps);
        FloatBuffer values = BufferUtils.createFloatBuffer(4 * 4 * 3);
        for (int layer = 0; layer < 3; layer++) for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) values.put(x < 2 ? .25f : 1f);
        values.flip(); GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_DEPTH_COMPONENT32F, 4, 4, 3, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, values);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        program.use(); program.setInt("uShadowCascades", 3); program.setInt("uFarShadowMap0", 4); program.setInt("uFarShadowMap1", 5);
        program.set("uSunDir", 0, 1, 0); program.set("uSunStrength", 1f); program.set("uShadowsOff", 0f);
        program.set("uShadowMatrix[0]", new Matrix4f().set(new float[] {0,0,0,0, 0,0,0,0, 0,0,0,0, .35f,.5f,.75f,1}));
        GL20.glUniform4f(program.location("uShadowMaps[0]"), .001f, 399f, .25f, 0f);
        GL20.glUniform4f(program.location("uShadowMaps[2]"), .001f, 399f, .25f, 0f);
        program.set("testPosition", 0, 0, 0);
        IsometricBridge.modelRendering(new Frame(), effects(true, 1, 0, 64, false, 1, 1, 1, 1));
        float[] dark = draw(program); near(dark[0], 0f, 1e-6f, "hard real shadow sampler"); near(dark[1], 0f, 1e-6f, "GI sampler");
        IsometricBridge.modelRendering(new Frame(), effects(true, .4f, 0, 64, false, 1, 1, 1, 1));
        float[] partial = draw(program); near(partial[0], .6f, 1e-6f, "shadow strength"); near(partial[1], .6f, 1e-6f, "GI shadow strength");
        IsometricBridge.modelRendering(new Frame(), effects(false, 1, 0, 64, false, 1, 1, 1, 1));
        float[] disabled = draw(program); near(disabled[0], 1f, 1e-6f, "shadow off"); near(disabled[1], 1f, 1e-6f, "GI shadow off");
        IsometricBridge.modelRendering(new Frame(), effects(true, 1, 3, 64, false, 1, 1, 1, 1));
        if (draw(program)[0] <= dark[0] + .1f) throw new AssertionError("Shadow softness did not change actual PCF samples");
        IsometricBridge.modelRendering(new Frame(), effects(true, 1, 0, 16, false, 1, 1, 1, 1));
        program.set("testPosition", 16, 0, 0);
        float[] far = draw(program); near(far[0], 1, 1e-6f, "shadow distance"); near(far[1], 1, 1e-6f, "GI shadow distance");
        near(far[2], 0, 1e-6f, "unproduced outer cascades excluded");
        program.set("testPosition", 0, 0, 0);
        IsometricBridge.rendering(new Frame());
        float[] nativeScope = draw(program);
        if (nativeScope[0] >= 1f || nativeScope[2] != 1f) throw new AssertionError("Model options leaked into native-map VP shaders");
        program.delete(); GL11.glDeleteTextures(maps); GL11.glDeleteTextures(clearCloud);
    }

    private static void checkMaps() {
        IsometricBridge.rendering(null);
        Frame frame = new Frame(); frame.scene.sunX = .6f; frame.scene.sunY = .8f; frame.scene.sunStrength = 1f; frame.scene.shadowReach = 64f;
        if (!NativeShadowMaps.draw(frame.scene, 256, 2)) throw new AssertionError("VP preparation failed");
        Object shadows = GameAccess.field("viewpoint.render.WorldRenderer", "shadows", null);
        FrameContext context = new FrameContext(); context.scene = frame.scene; context.sun = true; context.pipeline = ShaderPacks.active().pipeline;
        int originalResolution = context.pipeline.shadowResolution, originalLayers = context.pipeline.shadowCascades;
        if (Patch_ModelShadows.enter(shadows, context)) throw new AssertionError("Normal VP shadow method was intercepted");
        for (int size : new int[] {256, 512}) {
            IsometricBridge.modelRendering(frame, new IsometricEffects(true, 1f, 1f, size == 256 ? 16f : 96f, size, false, 1f, 1f, 1f, 1f));
            if (!Patch_ModelShadows.enter(shadows, context)) throw new AssertionError("Model shadow method was not intercepted");
            if (integer(shadows, "size") != size || integer(shadows, "drawnCount") != (size == 256 ? 1 : 3))
                throw new AssertionError("Actual resize/cascade controls ignored");
        }
        long count = ((Number) GameAccess.field("viewpoint.render.ShadowPass", "frameCount", shadows)).longValue();
        Object spots = GameAccess.field("viewpoint.render.ShadowPass", "spots", shadows);
        GameAccess.set("viewpoint.render.Spots", "count", spots, 99);
        IsometricBridge.modelRendering(frame, effects(false, 1, 1, 64, false, 1, 1, 1, 1));
        Patch_ModelShadows.enter(shadows, context);
        if (((Number) GameAccess.field("viewpoint.render.ShadowPass", "frameCount", shadows)).longValue() != count)
            throw new AssertionError("Disabled sun shadows still produced cascades");
        if (((Number) GameAccess.field("viewpoint.render.Spots", "count", spots)).intValue() != 0) throw new AssertionError("drawLights was skipped");
        if (context.pipeline.shadowResolution != originalResolution || context.pipeline.shadowCascades != originalLayers)
            throw new AssertionError("Shared VP Pipeline was modified");
        IsometricBridge.rendering(frame);
        if (Patch_ModelShadows.enter(shadows, context)) throw new AssertionError("Native overlay shadow method was intercepted");
    }

    private static int integer(Object shadows, String name) { return ((Number) GameAccess.field("viewpoint.render.ShadowPass", name, shadows)).intValue(); }
    private static IsometricEffects effects(boolean shadows, float strength, float softness, float distance, boolean style, float amount, float exposure, float saturation, float contrast) {
        return new IsometricEffects(shadows, strength, softness, distance, 256, style, amount, exposure, saturation, contrast);
    }
    private static float shoulder(float x) { return (float) (Math.min(x, .8f) + .2f * (1 - Math.exp(-Math.max(x - .8f, 0) / .2f))); }
    private static void near(float actual, float expected, float tolerance, String message) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > tolerance) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static float[] draw(GlProgram program) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo); GL30.glBindVertexArray(vao);
        GL11.glViewport(0, 0, 1, 1); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        program.use(); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        FloatBuffer result = BufferUtils.createFloatBuffer(4); GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, result);
        int error = GL11.glGetError(); if (error != 0) throw new AssertionError("GL draw error " + error);
        return new float[] { result.get(0), result.get(1), result.get(2), result.get(3) };
    }
    private static int texture2D(int format, float[] rgba) {
        int texture = GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        FloatBuffer data = BufferUtils.createFloatBuffer(rgba.length); data.put(rgba).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, 1, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return texture;
    }
    private static void bind2D(int unit, int texture) { GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
    private static GlProgram program(String name, String fragment) {
        int at = fragment.indexOf('\n'); fragment = fragment.substring(0, at + 1) + "#extension GL_ARB_shader_storage_buffer_object : enable\n#extension GL_ARB_shader_image_load_store : enable\n" + defines + fragment.substring(at + 1);
        return GlProgram.fromSources(name, new GlProgram.Sources(VERTEX, null, null, null, fragment), Map.of(), Map.of("fragColor", 0), java.util.List.of());
    }
    private static String expand(String path, HashSet<String> stack) throws Exception {
        if (!stack.add(path)) throw new AssertionError("Include cycle " + path);
        String source;
        if (path.equals("constants.glsl")) source = (String) GameAccess.staticCall("viewpoint.platform.Shaders", "constants");
        else if (!pack.equals("library")) source = (String) GameAccess.call("viewpoint.platform.Shaders", "read", null,
                new Class<?>[] {String.class, viewpoint.platform.ShaderPack.class, boolean[].class}, path, ShaderPacks.active().pack, new boolean[1]);
        else try (InputStream input = ClassLoader.getSystemResourceAsStream("viewpoint/shaders/" + path)) {
            if (input == null) throw new AssertionError("Missing actual VP resource " + path);
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        source = IsometricShaders.rewrite(path, source);
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            var match = INCLUDE.matcher(line);
            if (match.matches()) result.append(expand(match.group(1), stack)); else result.append(line).append('\n');
        }
        stack.remove(path); return result.toString();
    }
}
