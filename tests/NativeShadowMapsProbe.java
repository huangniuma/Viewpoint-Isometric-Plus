import java.util.Map;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import viewpoint.core.Frame;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.SceneData;
import vpiso.GameAccess;
import vpiso.IsometricEffects;
import vpiso.NativeOverlayDrawer;
import vpiso.NativeShadowMaps;

/** Exercises the actual map-only VP entry on GPU without a game world or settings writes. */
public final class NativeShadowMapsProbe {
    public static void main(String[] args) {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(64, 64, "VPISO map-only production probe", 0, 0);
        if (window == 0) throw new AssertionError("Hidden OpenGL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null,
                    new Class<?>[] { java.io.File.class }, (Object) null);
            TestShaderPacks.select(args.length > 0 ? args[0] : "library", args.length > 0 && args[0].equals("somber") ? "quality" : "high", Map.of("upscaling", "off", "reflections", "off"));
            SceneData scene = new SceneData();
            scene.sunX = .6f;
            scene.sunY = .8f;
            scene.sunZ = 0f;
            scene.sunStrength = 1f;
            scene.shadowReach = 96f;
            if (!NativeShadowMaps.draw(scene, 256, 2)) throw new AssertionError("Actual map producer was not ready");
            checkMaps(256, 2);
            // Trigger actual resize and change cascade count, not a synthetic sampler fixture.
            if (!NativeShadowMaps.draw(scene, 512, 3)) throw new AssertionError("Map resize failed");
            checkMaps(512, 3);
            Object caster = installCaster(scene);
            if (!NativeShadowMaps.draw(scene, 512, 3)) throw new AssertionError("Actual mesh caster map failed");
            checkCaster();
            scene.meshCount = 0;
            GameAccess.call("viewpoint.render.ChunkMeshData", "retire", caster, new Class<?>[0]);
            // VP refreshes the outer cascade once every four frames.
            for (int i = 0; i < 5; i++) {
                if (!NativeShadowMaps.draw(scene, 512, 3)) throw new AssertionError("Caster removal map failed");
            }
            checkMaps(512, 3);
            Object targets = GameAccess.field("viewpoint.render.WorldRenderer", "targets", null);
            if (((Number) GameAccess.field("viewpoint.render.Targets", "gbufferFbo", targets)).intValue() != 0) {
                throw new AssertionError("Map-only producer allocated a world GBuffer");
            }
            if (args.length > 0) {
                for (String next : new String[]{"library","default","vivid","somber",args[0]}) {
                    TestShaderPacks.select(next,next.equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off"));
                    if (!NativeShadowMaps.draw(scene,256,2) || !viewpoint.platform.GlProgram.pack().pack.id.equals(next))
                        throw new AssertionError("Native maps retained a stale linked pack: " + next);
                    checkMaps(256,2);
                }
                System.out.println("Built-in shadow relink PASS: library -> default -> vivid -> somber -> " + args[0]);
            }
            scene.sunStrength = 0f;
            if (NativeShadowMaps.draw(scene, 256, 1) || NativeShadowMaps.count() != 0) {
                throw new AssertionError("No-light frame reused stale shadow maps");
            }
            if ((Boolean) GameAccess.field("viewpoint.platform.GlDebug", "listening", null)) {
                throw new AssertionError("Map producer left GL debug listening enabled");
            }
            checkFullDrawer();
            System.out.println("Native shadow maps PASS: actual WorldRenderer resource preparation and map-only FloorBakes/Meshes/ModelPass/cascade entries, real mesh caster depth and removal, GPU empty-map readback, resize, matrices, no-light guard and no world GBuffer; no VP world color draw or game launched.");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void checkFullDrawer() {
        final int width = 64, height = 48, x = 5, y = 7, w = 40, h = 32;
        int color = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        int depth = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new AssertionError("Original-image drawer fixture FBO incomplete");
        }
        Frame frame = new Frame();
        frame.number = 100;
        frame.scene.sunX = .6f;
        frame.scene.sunY = .8f;
        frame.scene.sunStrength = 1f;
        frame.scene.shadowReach = 64f;
        NativeOverlayDrawer drawer = new NativeOverlayDrawer(frame);
        drawer.receiver.fit(new double[][] { { .5, .5, .5 }, { .6, .5, .51 }, { .5, .6, .51 }, { .5, .5, .6 } });
        Object ring = GameAccess.field("zombie.core.SpriteRenderer", "ringBuffer", null);
        if (ring == null) throw new AssertionError("Actual native SpriteRenderer did not create its ring buffer");
        int sampler0 = GL33.glGenSamplers(), sampler64 = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler0, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL33.glSamplerParameteri(sampler64, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        int vao = GL30.glGenVertexArrays();
        for (boolean mapsEnabled : new boolean[] { false, true }) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true); GL11.glDepthMask(true); GL11.glStencilMask(255);
            GL11.glClearColor(80f / 255f, 120f / 255f, 160f / 255f, 127f / 255f);
            GL11.glClearDepth(.5); GL11.glClearStencil(37);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glViewport(x, y, w, h);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL33.glBindSampler(0, sampler0); GL33.glBindSampler(64, sampler64);
            GL30.glBindVertexArray(vao); GL20.glUseProgram(0);
            GL11.glEnable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(1, 2, 3, 4);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_GEQUAL); GL11.glDepthMask(false);
            GL11.glStencilMask(0x35); GL11.glColorMask(true, false, true, false);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + 5);
            GameAccess.set(ring.getClass().getName(), "restoreVbos", ring, false);
            GameAccess.set(ring.getClass().getName(), "restoreBoundTextures", ring, false);
            GameAccess.set("zombie.core.DefaultShader", "isActive", null, true);
            GameAccess.set("zombie.core.textures.Texture", "lastTextureID", null, 12345);
            drawer.effects = new IsometricEffects(true, mapsEnabled ? .55f : 0f, 1f, 64f, 256,
                    false, 0f, 1f, 1f, 1f);
            drawer.render(); // Actual production wrapper, capture, maps, compositor and native-cache restoration.
            if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "failed", null)) {
                throw new AssertionError("Production NativeOverlayDrawer caught a rendering error");
            }
            int[] viewport = new int[4], mask = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, mask);
            if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != fbo
                    || GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != 0
                    || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0
                    || GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != vao
                    || GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != GL13.GL_TEXTURE0 + 5
                    || GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, 0) != sampler0
                    || GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, 64) != sampler64
                    || !java.util.Arrays.equals(viewport, new int[] { x, y, w, h })
                    || !java.util.Arrays.equals(mask, new int[] { 1, 0, 1, 0 })
                    || !GL11.glIsEnabled(GL11.GL_BLEND) || !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
                    || !GL11.glIsEnabled(GL11.GL_DEPTH_TEST) || GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != GL11.GL_GEQUAL
                    || GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) || GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK) != 0x35) {
                throw new AssertionError("Production drawer failed to restore GL framebuffer/viewport/program/VAO/sampler/mask state");
            }
            if (!(Boolean) GameAccess.field(ring.getClass().getName(), "restoreVbos", ring)
                    || !(Boolean) GameAccess.field(ring.getClass().getName(), "restoreBoundTextures", ring)
                    || !(Boolean) GameAccess.field("zombie.core.DefaultShader", "isActive", null)
                    || ((Number) GameAccess.field("zombie.core.textures.Texture", "lastTextureID", null)).intValue() != 12345) {
                throw new AssertionError("Production drawer failed to invalidate/restore native renderer caches");
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(width * height * 4);
            java.nio.FloatBuffer depths = org.lwjgl.BufferUtils.createFloatBuffer(width * height);
            java.nio.ByteBuffer stencils = org.lwjgl.BufferUtils.createByteBuffer(width * height);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depths);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, stencils);
            for (int i = 0; i < width * height; i++) {
                if ((pixels.get(i * 4) & 255) != 80 || (pixels.get(i * 4 + 1) & 255) != 120
                        || (pixels.get(i * 4 + 2) & 255) != 160 || (pixels.get(i * 4 + 3) & 255) != 127
                        || Math.abs(depths.get(i) - .5f) > 1e-6f || (stencils.get(i) & 255) != 37) {
                    throw new AssertionError("Actual drawer changed native RGBA/depth/stencil with empty or zero-strength maps");
                }
            }
            Object targets = GameAccess.field("viewpoint.render.WorldRenderer", "targets", null);
            if (((Number) GameAccess.field("viewpoint.render.Targets", "gbufferFbo", targets)).intValue() != 0) {
                throw new AssertionError("Production drawer entered the VP world GBuffer");
            }
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("Actual drawer GL error");
        }
        System.out.println("Native drawer integration PASS: actual NativeOverlayDrawer.render on original DEPTH24_STENCIL8 FBO; zero-strength and actual empty VP maps preserve RGBA/alpha/depth/stencil; restores offset viewport, distinct read/draw FBOs, program, VAO, sampler0/64, masks and engine caches; world GBuffer remains absent.");
    }

    private static Object installCaster(SceneData scene) {
        float[][] points = { { -2, 2, -2 }, { 2, 2, -2 }, { 2, 2, 2 },
                { -2, 2, -2 }, { 2, 2, 2 }, { -2, 2, 2 } };
        float[] vertices = new float[points.length * 14];
        for (int i = 0; i < points.length; i++) {
            int at = i * 14;
            System.arraycopy(points[i], 0, vertices, at, 3);
            vertices[at + 7] = vertices[at + 8] = 1f;
            vertices[at + 10] = 1f;
            vertices[at + 13] = Float.intBitsToFloat(64); // Opaque floor flag; no atlas fixture needed.
        }
        try {
            Object pages = java.lang.reflect.Array.newInstance(GameAccess.type("zombie.core.textures.TextureID"), 1);
            Object mesh = GameAccess.type("viewpoint.render.ChunkMeshData")
                    .getConstructor(pages.getClass(), int[].class, int[].class, float[].class, int.class)
                    .newInstance(pages, new int[] { 0 }, new int[] { points.length }, vertices, 0);
            ((Object[]) GameAccess.field("viewpoint.render.SceneData", "meshes", scene))[0] = mesh;
            scene.meshCount = 1;
            scene.meshFlags[0] = SceneData.CASTS_SHADOW | SceneData.CASTS_MID | SceneData.CASTS_FAR;
            scene.meshFade[0] = 0f;
            scene.meshFade[1] = 2f;
            return mesh;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot construct the actual VP caster mesh", failure);
        }
    }

    private static void checkCaster() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, NativeShadowMaps.texture());
        int side = NativeShadowMaps.size();
        float[] depth = new float[side * side * 3];
        GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        int shadowed = 0;
        for (int i = 0; i < side * side; i++) if (depth[i] < .99f) shadowed++;
        if (shadowed < 10) throw new AssertionError("Real VP mesh draw did not write the shadow map: " + shadowed);
        System.out.println("Actual VP map-only caster pixels: " + shadowed);
    }

    private static void checkMaps(int side, int count) {
        if (NativeShadowMaps.texture() == 0 || NativeShadowMaps.size() != side || NativeShadowMaps.count() != count) {
            throw new AssertionError("Map metadata does not match produced maps");
        }
        if (Math.abs(NativeShadowMaps.depthRange() - 371f) > .01f) throw new AssertionError("Wrong shadow depth range");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, NativeShadowMaps.texture());
        if (GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_TEXTURE_WIDTH) != side
                || GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY, 0, GL12.GL_TEXTURE_DEPTH) != 3) {
            throw new AssertionError("Expected actual three-layer VP cascade texture");
        }
        float[] depth = new float[side * side * 3];
        GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        for (int layer = 0; layer < count; layer++) {
            for (int i = layer * side * side; i < (layer + 1) * side * side; i++) {
                if (depth[i] < .99999f || depth[i] > 1f) throw new AssertionError("Empty map was not cleared to unoccluded depth");
            }
            Object matrix = NativeShadowMaps.matrix(layer);
            float x = ((Number) GameAccess.call("org.joml.Matrix4f", "m30", matrix, new Class<?>[0])).floatValue();
            float y = ((Number) GameAccess.call("org.joml.Matrix4f", "m31", matrix, new Class<?>[0])).floatValue();
            float z = ((Number) GameAccess.call("org.joml.Matrix4f", "m32", matrix, new Class<?>[0])).floatValue();
            if (!Float.isFinite(z) || Math.abs(x - .5f) > .1f || Math.abs(y - .5f) > .1f
                    || NativeShadowMaps.reach(layer) <= 0f) throw new AssertionError("Invalid actual texture-space shadow matrix");
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) throw new AssertionError("Map production GL error 0x" + Integer.toHexString(error));
    }
}
