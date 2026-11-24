package vpiso;

import java.util.IdentityHashMap;
import java.util.Map;

/** Associates native visibility drawers with the camera that queued them. */
public final class RotationLayers {
    private static final String VISION = "zombie.vispoly.VisibilityPolygon2";
    private static final Map<Object, RotationCamera.Snapshot> QUEUED = new IdentityHashMap<>();
    private static final ThreadLocal<RotationCamera.Snapshot> DRAWING = new ThreadLocal<>();
    private static final ThreadLocal<RotationCamera.Snapshot> QUEUING = new ThreadLocal<>();
    private static final ThreadLocal<float[]> POINT = ThreadLocal.withInitial(() -> new float[3]);

    public static RotationCamera.Snapshot begin(RotationCamera.Snapshot snapshot) {
        RotationCamera.Snapshot previous = DRAWING.get();
        if (snapshot == null) DRAWING.remove(); else DRAWING.set(snapshot);
        return previous;
    }

    public static RotationCamera.Snapshot current() { return DRAWING.get(); }

    public static void queueVision(int player, RotationCamera.Snapshot snapshot) {
        Object vision = GameAccess.staticCall(VISION, "getInstance");
        RotationCamera.Snapshot previous = QUEUING.get();
        if (snapshot == null) QUEUING.remove(); else QUEUING.set(snapshot);
        try { GameAccess.call(VISION, "renderMain", vision, new Class<?>[] { int.class }, player); }
        finally { if (previous == null) QUEUING.remove(); else QUEUING.set(previous); }
    }

    /** Runs for native frames too, so an unconsumed mapping cannot leak to a later camera. */
    public static void beforeVision(int player) {
        Object vision = GameAccess.staticCall(VISION, "getInstance");
        Object sprites = GameAccess.field("zombie.core.SpriteRenderer", "instance", null);
        int index = ((Number) GameAccess.call("zombie.core.SpriteRenderer", "getMainStateIndex", sprites,
                new Class<?>[0])).intValue();
        Object drawer = ((Object[][]) GameAccess.field(VISION, "drawers", vision))[player][index];
        NativeVision.beforeVision(drawer);
        synchronized (QUEUED) {
            RotationCamera.Snapshot snapshot = QUEUING.get();
            if (snapshot == null) QUEUED.remove(drawer); else QUEUED.put(drawer, snapshot);
        }
    }

    public static RotationCamera.Snapshot enterVision(Object drawer) {
        RotationCamera.Snapshot previous = DRAWING.get();
        RotationCamera.Snapshot next;
        synchronized (QUEUED) { next = QUEUED.remove(drawer); }
        if (next == null) DRAWING.remove(); else DRAWING.set(next);
        return previous;
    }

    public static void end(RotationCamera.Snapshot previous) {
        if (previous == null) DRAWING.remove(); else DRAWING.set(previous);
    }

    public static Float projectAxis(float x, float y, float z, int axis) {
        RotationCamera.Snapshot snapshot = DRAWING.get();
        float[] point = POINT.get();
        if (snapshot == null || !snapshot.project(x, y, z, point)) return null;
        // Native PlayerCamera returns pixels in the offscreen (zoomed) viewport.
        return point[axis] * snapshot.zoom();
    }

    public static void depth(Object result, float x, float y, float z) {
        RotationCamera.Snapshot snapshot = DRAWING.get();
        float[] point = POINT.get();
        if (snapshot != null && snapshot.project(x, y, z, point)) {
            GameAccess.set("zombie.iso.IsoDepthHelper$Results", "depthStart", result, point[2]);
        }
    }

    private RotationLayers() {}
}
