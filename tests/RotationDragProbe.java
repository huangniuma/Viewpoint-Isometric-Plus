import java.util.Properties;
import org.lwjgl.glfw.GLFW;
import vpiso.*;

/** Production mouse motion, VP persistence migration, and real GLFW cursor capture/restore. */
public final class RotationDragProbe {
    public static void main(String[] args) {
        put("vpiso.direction", "90");
        RotationDrag.initialize();
        near(RotationDrag.yawDegrees(), 90, "015 direction migration");
        put("vpiso.yaw", "123.75");
        RotationDrag.initialize();
        near(RotationDrag.yawDegrees(), 123.75f, "precise yaw takes priority");
        float before = RotationDrag.yawDegrees();
        RotationDrag.move(0, .016, 1, 1);
        near(RotationDrag.yawDegrees(), before, "stationary mouse");
        RotationDrag.move(120, .016, 1, 1);
        if (RotationDrag.yawDegrees() <= before || RotationDrag.yawDegrees() >= before + 16)
            throw new AssertionError("Mouse displacement does not turn with OldPoint sensitivity");
        float after = RotationDrag.yawDegrees();
        RotationDrag.move(-120, .1, 1, 1);
        near(RotationDrag.yawDegrees(), before, "reverse drag");
        RotationDrag.move(Double.NaN, .016, 1, 1);
        near(RotationDrag.yawDegrees(), before, "invalid mouse sample");
        RotationDrag.move(100000, .1, 1, 1);
        if (!(RotationDrag.yawDegrees() >= 0 && RotationDrag.yawDegrees() < 360)) throw new AssertionError("Yaw wrap");
        RotationDrag.reset();
        near(RotationDrag.yawDegrees(), 0, "restore original direction");

        if (!GLFW.glfwInit()) throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(320, 240, "VPISO middle mouse verification", 0, 0);
        if (window == 0) throw new AssertionError("Hidden test window");
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        try {
            GameAccess.call("zombie.core.Core", "setDisplayCursor", core, new Class<?>[] { boolean.class }, true);
            // An unfocused normal cursor follows the user's desktop mouse between reads.
            // GLFW's disabled virtual coordinates make the exact restore assertion deterministic.
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            GLFW.glfwSetCursorPos(window, 80, 90);
            double[] x = new double[1], y = new double[1];
            GLFW.glfwGetCursorPos(window, x, y);
            double originalX = x[0], originalY = y[0];
            GameAccess.call("vpiso.RotationDrag", "capture", null, new Class<?>[] { long.class }, window);
            if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED)
                throw new AssertionError("Held mouse was not captured");
            if ((Boolean) GameAccess.call("zombie.core.Core", "isDisplayCursor", core, new Class<?>[0]))
                throw new AssertionError("Game cursor remains visible while orbiting");
            RotationDrag.move(250, .016, 1, 1);
            float releasedYaw = RotationDrag.yawDegrees();
            GameAccess.call("vpiso.RotationDrag", "release", null, new Class<?>[] { long.class }, window);
            if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED)
                throw new AssertionError("Previous virtual cursor mode was not restored");
            GLFW.glfwGetCursorPos(window, x, y);
            near((float)x[0], (float)originalX, "original cursor X");
            near((float)y[0], (float)originalY, "original cursor Y");
            if (!(Boolean) GameAccess.call("zombie.core.Core", "isDisplayCursor", core, new Class<?>[0]))
                throw new AssertionError("Native cursor visibility not restored");
            near(RotationDrag.yawDegrees(), releasedYaw, "release keeps angle");
            near(Float.parseFloat((String) GameAccess.call("viewpoint.platform.LiveSettings", "value", null,
                    new Class<?>[] { String.class }, "vpiso.yaw")), releasedYaw, "release persists precise yaw");
            // Exercise the usual NORMAL -> DISABLED -> NORMAL path too. Its live desktop
            // position is not a deterministic input; the virtual case above checks exact restore.
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            GameAccess.call("vpiso.RotationDrag", "capture", null, new Class<?>[] { long.class }, window);
            if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED)
                throw new AssertionError("Normal cursor was not captured");
            GameAccess.call("vpiso.RotationDrag", "release", null, new Class<?>[] { long.class }, window);
            if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL)
                throw new AssertionError("Normal cursor mode was not restored");
            // A hidden, unfocused test window must never start an orbit or touch the VP look flags.
            long previous = ((Number) GameAccess.field("org.lwjglx.opengl.Display$Window", "handle", null)).longValue();
            try {
                GameAccess.set("org.lwjglx.opengl.Display$Window", "handle", null, window);
                if (RotationDrag.onMouseCursor()) throw new AssertionError("Inactive input context captured mouse");
                near(RotationDrag.yawDegrees(), releasedYaw, "inactive input keeps angle");
            } finally { GameAccess.set("org.lwjglx.opengl.Display$Window", "handle", null, previous); }
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
        System.out.println("Middle mouse orbit PASS: OldPoint-style horizontal displacement, stationary/reverse/wrap guards, precise-angle migration/persistence, actual hidden GLFW capture, cursor visibility/position restore and inactive passthrough. No game/map test.");
    }
    private static void put(String key, String value) {
        GameAccess.call("viewpoint.platform.LiveSettings", "put", null, new Class<?>[] { String.class, String.class }, key, value);
    }
    private static void near(float actual, float expected, String name) {
        if (Math.abs(actual - expected) > .001f) throw new AssertionError(name + ": " + actual + " != " + expected);
    }
}
