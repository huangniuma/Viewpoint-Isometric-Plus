package vpiso;

import org.lwjgl.glfw.GLFW;
import viewpoint.platform.SettingsWindow;

/** Hold middle mouse to orbit; owns only its own cursor capture, never VP's look state. */
public final class RotationDrag {
    private static final double[] X = new double[1], Y = new double[1];
    private static volatile float yaw;
    private static float target;
    private static long window, readAt;
    private static double originX, originY, previousX;
    private static int previousMode;
    private static boolean previousCursor, changed, waitForRelease;

    public static void initialize() {
        String value = value("vpiso.yaw");
        if (value == null) value = value("vpiso.direction"); // Migrate 015's stored choice.
        try { yaw = RotationCamera.normalizeYaw(Float.parseFloat(value)); }
        catch (NumberFormatException | NullPointerException ignored) { yaw = 0f; }
        target = yaw;
    }

    public static float yawDegrees() { return yaw; }

    public static void reset() {
        yaw = target = 0f;
        store();
    }

    /** Called by the actual mouse-cursor producer, before native input consumes the frame. */
    public static boolean onMouseCursor() {
        long current = ((Number) GameAccess.staticCall("org.lwjglx.opengl.Display", "getWindow")).longValue();
        if (window != 0L && window != current) { release(current); waitForRelease = true; }
        if (current == 0L) return false;
        boolean held = GLFW.glfwGetMouseButton(current, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (!held) waitForRelease = false;
        RotationCamera.refresh();
        boolean allowed = RotationCamera.requested() && IsometricBridge.rotationAllowed()
                && !SettingsWindow.shown() && !RotationInteraction.nativeCursorActive()
                && GLFW.glfwGetWindowAttrib(current, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE
                && GameAccess.staticCall("zombie.iso.IsoCamera", "getCameraCharacter") != null
                && !(Boolean) GameAccess.staticCall("zombie.GameTime", "isGamePaused")
                && !(Boolean) GameAccess.staticCall("zombie.ui.UIManager", "isModalVisible")
                && !(Boolean) GameAccess.call("zombie.core.Core", "isDoingTextEntry",
                        GameAccess.staticCall("zombie.core.Core", "getInstance"), new Class<?>[0])
                && !(Boolean) GameAccess.field("viewpoint.platform.ImGuiFrame", "wantsMouse", null);
        if (!held || !allowed || waitForRelease) {
            if (window != 0L) release(current);
            if (held && !allowed) waitForRelease = true;
            return false;
        }
        if (window == 0L) {
            if ((Boolean) GameAccess.field("viewpoint.input.Look", "captured", null)
                    || !(Boolean) GameAccess.call("zombie.input.Mouse", "isButtonDownUICheck", null,
                            new Class<?>[] { int.class }, 2)) return false;
            capture(current);
        }
        GLFW.glfwGetCursorPos(current, X, Y);
        long now = System.nanoTime();
        double elapsed = readAt == 0L ? 1.0 / 60.0 : Math.max(0, Math.min(.1, (now - readAt) * 1e-9));
        readAt = now;
        float speed = ((viewpoint.platform.LiveSettings.Number) GameAccess.field("viewpoint.input.Look", "SPEED", null)).get();
        float zoom = viewpoint.input.Look.zoom;
        move(X[0] - previousX, elapsed, speed, zoom);
        previousX = X[0];
        RotationCamera.configureYaw(true, yaw);
        return true;
    }

    /** Same horizontal sensitivity and short smoothing interval as OldPoint's mouse orbit. */
    public static void move(double dx, double elapsed, float sensitivity, float zoom) {
        if (!Double.isFinite(dx) || !Float.isFinite(sensitivity)) return;
        if (!(zoom > 0f) || !Float.isFinite(zoom)) zoom = 1f;
        target = RotationCamera.normalizeYaw(target + (float) Math.toDegrees(dx * sensitivity * .0022 * zoom));
        float difference = (target - yaw + 540f) % 360f - 180f;
        float lag = (float) (difference * Math.exp(-Math.max(0, Math.min(.1, elapsed)) / .008));
        float maxLag = (float) Math.toDegrees(.006);
        float next = RotationCamera.normalizeYaw(target - Math.max(-maxLag, Math.min(maxLag, lag)));
        changed |= next != yaw;
        yaw = next;
    }

    private static void capture(long current) {
        GLFW.glfwGetCursorPos(current, X, Y);
        originX = X[0]; originY = Y[0];
        previousMode = GLFW.glfwGetInputMode(current, GLFW.GLFW_CURSOR);
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        previousCursor = (Boolean) GameAccess.call("zombie.core.Core", "isDisplayCursor", core, new Class<?>[0]);
        GLFW.glfwSetInputMode(current, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwGetCursorPos(current, X, Y);
        previousX = X[0]; window = current; readAt = 0L; target = yaw;
        GameAccess.call("zombie.core.Core", "setDisplayCursor", core, new Class<?>[] { boolean.class }, false);
        SettingsWindow.cursorHeld(true);
    }

    private static void release(long current) {
        if (window == current) {
            GLFW.glfwSetInputMode(current, GLFW.GLFW_CURSOR, previousMode);
            GLFW.glfwSetCursorPos(current, originX, originY);
        }
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        GameAccess.call("zombie.core.Core", "setDisplayCursor", core, new Class<?>[] { boolean.class }, previousCursor);
        SettingsWindow.cursorHeld(false);
        window = 0L; readAt = 0L;
        if (changed) store();
    }

    private static String value(String key) {
        return (String) GameAccess.call("viewpoint.platform.LiveSettings", "value", null,
                new Class<?>[] { String.class }, key);
    }

    private static void store() {
        GameAccess.call("viewpoint.platform.LiveSettings", "put", null,
                new Class<?>[] { String.class, String.class }, "vpiso.yaw", Float.toString(yaw));
        changed = false;
        SettingsWindow.edited();
    }

    private RotationDrag() {}
}
