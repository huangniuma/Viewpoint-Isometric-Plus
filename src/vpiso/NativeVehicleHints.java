package vpiso;

import java.util.ArrayDeque;
import java.util.List;
import org.lwjgl.opengl.GL11;

/** Original vehicle use-area rules, with only their newly emitted lines reprojected when rotated. */
public final class NativeVehicleHints {
    private static final String LINES = "zombie.debug.LineDrawer";
    private static final String LINE = LINES + "$DrawableLine";
    private static final Class<?>[] NONE = new Class<?>[0];

    @SuppressWarnings("unchecked")
    public static void render(Object cell, RotationCamera.Snapshot rotation) {
        List<Object> lines = (List<Object>) GameAccess.field(LINES, "lines", null);
        int first = lines.size();
        try {
            Iterable<?> vehicles = (Iterable<?>) GameAccess.call("zombie.iso.IsoCell", "getVehicles", cell, NONE);
            for (Object vehicle : vehicles)
                GameAccess.call("zombie.vehicles.BaseVehicle", "renderUsableArea", vehicle, NONE);
            if (rotation == null || lines.size() == first) return;
            // Match the native late line consumer, after world/placement drawing has been queued.
            GameAccess.call("zombie.IndieGL", "StartShader", null, new Class<?>[] { int.class }, 0);
            GameAccess.staticCall("zombie.IndieGL", "disableDepthTest");
            GameAccess.call("zombie.IndieGL", "glBlendFunc", null,
                    new Class<?>[] { int.class, int.class }, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float[] from = new float[3], to = new float[3];
            for (int i = first; i < lines.size(); i++) {
                Object line = lines.get(i);
                if (!rotation.project(number(line, "xstart"), number(line, "ystart"), number(line, "zstart"), from)
                        || !rotation.project(number(line, "xend"), number(line, "yend"), number(line, "zend"), to)) continue;
                GameAccess.call(LINES, "drawLine", null,
                        new Class<?>[] { float.class, float.class, float.class, float.class,
                                float.class, float.class, float.class, float.class, int.class },
                        from[0] * rotation.zoom(), from[1] * rotation.zoom(),
                        to[0] * rotation.zoom(), to[1] * rotation.zoom(),
                        number(line, "red"), number(line, "green"), number(line, "blue"), number(line, "alpha"), 1);
            }
        } finally {
            if (rotation != null) {
                ArrayDeque<Object> pool = (ArrayDeque<Object>) GameAccess.field(LINES, "pool", null);
                while (lines.size() > first) pool.push(lines.remove(lines.size() - 1));
            }
        }
    }

    private static float number(Object line, String field) {
        return ((Number) GameAccess.field(LINE, field, line)).floatValue();
    }

    private NativeVehicleHints() {}
}
