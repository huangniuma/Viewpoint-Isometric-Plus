package vpiso;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import viewpoint.core.Frame;

/** Main-thread camera state. Queued drawers retain their own immutable matrix snapshot. */
public final class RotationCamera {
    private static volatile float yaw;
    private static volatile Snapshot latest;
    private static volatile boolean requested;
    private static float savedYaw;

    public static void configure(boolean enabled, int direction) {
        configureYaw(enabled, Math.floorMod(direction, 4) * 90f);
    }

    public static void configureYaw(boolean enabled, float degrees) {
        requested = enabled;
        savedYaw = normalizeYaw(degrees);
        float next = enabled && IsometricBridge.rotationAllowed()
                ? savedYaw : 0f;
        if (next != yaw) {
            yaw = next;
            latest = null;
            RotationInteraction.cameraChanged();
        }
    }

    public static boolean requested() { return requested; }
    public static void refresh() { configureYaw(requested, savedYaw); }
    public static boolean active() { return yaw != 0f && IsometricBridge.rotationAllowed(); }
    public static float yawDegrees() { return active() ? yaw : 0f; }
    public static float savedYawDegrees() { return savedYaw; }
    public static int quarterTurns() { return Math.floorMod(Math.round(yawDegrees() / 90f), 4); }
    public static Snapshot snapshot() { return active() ? latest : null; }
    public static void clearSnapshot() { latest = null; }

    /** MovePose recreates its native start direction before every interpolation. */
    public static void transitionStart(float[] direction) {
        if (!requested || savedYaw == 0f || !IsometricBridge.rotationConfigured()) return;
        double angle = Math.toRadians(savedYaw);
        float x = direction[0], z = direction[2];
        direction[0] = (float) (Math.cos(angle) * x - Math.sin(angle) * z);
        direction[2] = (float) (Math.sin(angle) * x + Math.cos(angle) * z);
    }

    public static float normalizeYaw(float degrees) {
        if (!Float.isFinite(degrees)) return 0f;
        float result = degrees % 360f;
        if (result < 0f) result += 360f;
        return result == 0f ? 0f : result;
    }

    public static IsoCameraMath.Fit rotateFit(IsoCameraMath.Fit fit, float x, float y, int turns) {
        return rotateFit(fit, x, y, Math.floorMod(turns, 4) * 90f);
    }

    public static IsoCameraMath.Fit rotateFit(IsoCameraMath.Fit fit, float x, float y, float degrees) {
        double angle = Math.toRadians(normalizeYaw(degrees));
        double dx = fit.centreX() - x, dy = fit.centreY() - y;
        return new IsoCameraMath.Fit(x + Math.cos(angle) * dx - Math.sin(angle) * dy,
                y + Math.sin(angle) * dx + Math.cos(angle) * dy, fit.halfHeight());
    }

    public static Snapshot capture(Frame frame, Matrix4f view, int width, int height, float zoom) {
        if (!active()) { latest = null; return null; }
        Snapshot result = new Snapshot(frame.scene.projection, view, frame.camX, frame.camY,
                frame.camZ, width, height, zoom, yawDegrees());
        latest = result;
        return result;
    }

    public static boolean project(double x, double y, double z, float[] screen) {
        Snapshot state = snapshot();
        return state != null && state.project(x, y, z, screen);
    }

    public static boolean ground(double x, double y, double z, double[] world) {
        Snapshot state = snapshot();
        return state != null && state.ground(x, y, z, world);
    }

    public static final class Snapshot {
        private final Matrix4f projection, view, combined, inverse;
        private final float camX, camY, camZ, zoom;
        private final int width, height;
        private final float yawDegrees;

        public Snapshot(Matrix4f projection, Matrix4f view, float x, float y, float z,
                        int width, int height, float zoom, int quarterTurns) {
            this(projection, view, x, y, z, width, height, zoom, Math.floorMod(quarterTurns, 4) * 90f);
        }

        public Snapshot(Matrix4f projection, Matrix4f view, float x, float y, float z,
                        int width, int height, float zoom, float degrees) {
            this.projection = new Matrix4f(projection);
            this.view = new Matrix4f(view);
            this.combined = new Matrix4f(projection).mul(view);
            this.inverse = new Matrix4f(combined).invert();
            camX = x; camY = y; camZ = z;
            this.width = width; this.height = height; this.zoom = zoom;
            this.yawDegrees = normalizeYaw(degrees);
        }

        public Matrix4f projection() { return new Matrix4f(projection); }
        public Matrix4f view() { return new Matrix4f(view); }
        public Matrix4f inverse() { return new Matrix4f(inverse); }
        public float camX() { return camX; }
        public float camY() { return camY; }
        public float camZ() { return camZ; }
        public int width() { return width; }
        public int height() { return height; }
        public float zoom() { return zoom; }
        public int quarterTurns() { return Math.floorMod(Math.round(yawDegrees / 90f), 4); }
        public float yawDegrees() { return yawDegrees; }

        float clipW(float x, float y, float z) {
            return combined.m03() * (camX - x) + combined.m13() * ((z - camZ) * 2.4494896f)
                    + combined.m23() * (camY - y) + combined.m33();
        }

        public boolean project(double x, double y, double z, float[] out) {
            Vector4f p = new Vector4f((float) (camX - x), (float) ((z - camZ) * 2.4494896),
                    (float) (camY - y), 1f);
            combined.transform(p);
            if (!(p.w > 0f) || !Float.isFinite(p.w)) return false;
            out[0] = (p.x / p.w + 1f) * .5f * width;
            out[1] = (1f - p.y / p.w) * .5f * height;
            if (out.length > 2) out[2] = (p.z / p.w + 1f) * .5f;
            return Float.isFinite(out[0]) && Float.isFinite(out[1]);
        }

        public boolean ground(double x, double y, double z, double[] out) {
            if (width < 1 || height < 1) return false;
            float nx = (float) (x / width * 2 - 1), ny = (float) (1 - y / height * 2);
            Vector4f near = inverse.transform(new Vector4f(nx, ny, -1f, 1f));
            Vector4f far = inverse.transform(new Vector4f(nx, ny, 1f, 1f));
            near.div(near.w); far.div(far.w);
            double dy = far.y - near.y;
            if (Math.abs(dy) < 1e-8) return false;
            double t = ((z - camZ) * 2.4494896 - near.y) / dy;
            out[0] = camX - (near.x + t * (far.x - near.x));
            out[1] = camY - (near.z + t * (far.z - near.z));
            return Double.isFinite(out[0]) && Double.isFinite(out[1]);
        }
    }

    private RotationCamera() {}
}
