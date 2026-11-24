import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.LiveSettings;
import vpiso.*;

/** Real addon settings -> native-world hook -> SpriteRenderer command -> production drawer. */
public final class NativeStylePipelineProbe {
    private static final String STATE = "zombie.core.sprite.GenericSpriteRenderState";
    private static final int W = 32, H = 24;
    private static final int[] SOURCE = {64, 112, 176, 127};
    private static Object queue;

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(W, H, "VPISO native style route", 0, 0);
        if (window == 0) throw new AssertionError("Hidden GL unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            fixture();
            int color = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, W, H, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            int fbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete FBO");
            GL11.glViewport(0, 0, W, H);
            // Style must operate on ordinary SDR midtones, with no depth or VP world renderer.
            int[] normal = render(fbo, true, 1f, 1f, 1f);
            if (difference(normal, SOURCE) < 8) throw new AssertionError("Default standalone style is invisible on ordinary midtones: " + java.util.Arrays.toString(normal));
            toggle("style").set(false); IsometricControls.tick();
            if (countAfterHook() != 0) throw new AssertionError("Both effects off still queued an overlay");
            int[] exposed = render(fbo, true, 2f, 1f, 1f);
            if (difference(exposed, normal) < 30) throw new AssertionError("Standalone exposure setting is inactive");
            int[] gray = render(fbo, true, 1f, 0f, 1f);
            if (Math.abs(gray[0] - gray[1]) > 1 || Math.abs(gray[1] - gray[2]) > 1) throw new AssertionError("Standalone saturation zero is not gray");
            int[] contrasted = render(fbo, true, 1f, 1f, 1.5f);
            if (difference(contrasted, normal) < 15) throw new AssertionError("Standalone contrast setting is inactive");
            number("styleStrength").set(0f); IsometricControls.tick();
            if (countAfterHook() != 0) throw new AssertionError("Style strength zero still queued an overlay");
            number("styleStrength").set(1f); toggle("models").set(true); IsometricControls.tick();
            if (countAfterHook() != 0) throw new AssertionError("Model rendering also queued the native overlay");
            Object targets = GameAccess.field("viewpoint.render.WorldRenderer", "targets", null);
            if ((targets != null && ((Number) GameAccess.field("viewpoint.render.Targets", "gbufferFbo", targets)).intValue() != 0)
                    || GameAccess.field("vpiso.IsometricBridge", "captureFit", null) != null) throw new AssertionError("Standalone style used VP world rendering");
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("Native style route GL error");
            System.out.println("Native style pipeline PASS: real LiveSettings -> afterNativeWorld -> actual SpriteRenderer/TextureDraw.run -> production NativeOverlayDrawer; models/shadows off, default midtones "
                    + java.util.Arrays.toString(SOURCE) + " -> " + java.util.Arrays.toString(normal)
                    + "; exposure/saturation/contrast live, alpha preserved, no depth/VP GBuffer/scene capture; off/zero/model guards. Controlled native FBO, no game/map or settings writes.");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }

    private static void fixture() throws Exception {
        var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object unsafe = field.get(null);
        Object player = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, GameAccess.type("zombie.characters.IsoPlayer"));
        Array.set(GameAccess.field("zombie.characters.IsoPlayer", "players", null), 0, player);
        GameAccess.set("zombie.iso.IsoCamera", "isoCameraGameCharacter", null, player);
        GameAccess.set("zombie.iso.IsoCamera$FrameState", "playerIndex", GameAccess.field("zombie.iso.IsoCamera", "frameState", null), 0);
        Object sprites = GameAccess.field("zombie.core.SpriteRenderer", "instance", null);
        Object states = GameAccess.field("zombie.core.SpriteRenderer", "states", sprites);
        queue = GameAccess.call("zombie.core.sprite.SpriteRendererStates", "getPopulatingActiveState", states, new Class<?>[0]);
        Main.compatible = true;
        viewpoint.core.View.enabled = false;
        IsometricControls.tick();
        toggle("enabledSetting").set(true); toggle("models").set(false); toggle("shadows").set(false);
        number("styleStrength").set(1f);
        IsometricControls.tick();
    }

    private static int countAfterHook() {
        GameAccess.set(STATE, "numSprites", queue, 0);
        IsometricBridge.afterNativeWorld(null); // A style-only frame needs neither a cell nor world geometry.
        if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "failed", null)) throw new AssertionError("Native hook caught an error");
        return ((Number) GameAccess.field(STATE, "numSprites", queue)).intValue();
    }

    private static int[] render(int fbo, boolean style, float exposure, float saturation, float contrast) {
        toggle("style").set(style); number("exposure").set(exposure);
        number("saturation").set(saturation); number("contrast").set(contrast); IsometricControls.tick();
        if (countAfterHook() != 1) throw new AssertionError("Standalone style did not queue exactly one command");
        Object command = Array.get(GameAccess.field(STATE, "sprite", queue), 0);
        Object drawer = GameAccess.field("zombie.core.textures.TextureDraw", "drawer", command);
        if (!(drawer instanceof NativeOverlayDrawer)) throw new AssertionError("Unexpected native style command");
        // Changing the UI snapshot after queueing must not change this queued frame.
        number("exposure").set(.25f); IsometricControls.tick();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(SOURCE[0] / 255f, SOURCE[1] / 255f, SOURCE[2] / 255f, SOURCE[3] / 255f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GameAccess.call("zombie.core.textures.TextureDraw", "run", command, new Class<?>[0]);
        GameAccess.call("zombie.core.textures.TextureDraw", "postRender", command, new Class<?>[0]);
        if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "failed", null)) throw new AssertionError("Queued native drawer caught an error");
        ByteBuffer bytes = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(W / 2, H / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        int[] result = new int[4]; for (int i = 0; i < 4; i++) result[i] = bytes.get(i) & 255;
        if (result[3] != SOURCE[3]) throw new AssertionError("Native style changed alpha");
        return result;
    }

    private static LiveSettings.Toggle toggle(String field) { return (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", field, null); }
    private static LiveSettings.Number number(String field) { return (LiveSettings.Number) GameAccess.field("vpiso.IsometricControls", field, null); }
    private static int difference(int[] a, int[] b) { return Math.abs(a[0]-b[0]) + Math.abs(a[1]-b[1]) + Math.abs(a[2]-b[2]); }
}
