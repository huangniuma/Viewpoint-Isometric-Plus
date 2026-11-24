package vpiso;

import org.joml.Matrix4f;

/** Fits the camera to the game's actual projected tile basis, including camera panning. */
public final class IsoCameraMath {
    public static final float DISTANCE = 6000f;
    public static final float NEAR = DISTANCE - 256f;
    public static final float FAR = DISTANCE + 256f;
    public static final float YAW = (float) Math.toRadians(-135);
    public static final float PITCH = (float) Math.toRadians(-30);
    public static final float DX = (float) Math.sqrt(3.0 / 8.0);
    public static final float DY = -0.5f;
    public static final float DZ = DX;

    public record Fit(double centreX, double centreY, float halfHeight) {}

    public static Fit fit(double x, double y, double px, double py,
                          double ex, double ey, double sx, double sy, int width, int height) {
        double det = ex * sy - sx * ey;
        if (Math.abs(det) < 1e-7 || width < 1 || height < 1) {
            throw new IllegalArgumentException("Invalid original isometric projection");
        }
        double ox = width * .5 - px, oy = height * .5 - py;
        double cx = x + (ox * sy - sx * oy) / det;
        double cy = y + (ex * oy - ox * ey) / det;
        double pixels = Math.hypot(ex - sx, ey - sy) / Math.sqrt(2);
        return new Fit(cx, cy, (float) (height * .5 / pixels));
    }

    public static void matrices(Fit fit, float x, float y, int width, int height,
                                Matrix4f projection, Matrix4f view, float[] eye) {
        matrices(fit, x, y, width, height, projection, view, eye, 0);
    }

    public static void matrices(Fit fit, float x, float y, int width, int height,
                                Matrix4f projection, Matrix4f view, float[] eye, int quarterTurns) {
        matrices(fit, x, y, width, height, projection, view, eye, Math.floorMod(quarterTurns, 4) * 90f);
    }

    public static void matrices(Fit fit, float x, float y, int width, int height,
                                Matrix4f projection, Matrix4f view, float[] eye, float yawDegrees) {
        double angle = Math.toRadians(RotationCamera.normalizeYaw(yawDegrees));
        float dx = (float) (DX * Math.cos(angle) - DZ * Math.sin(angle));
        float dz = (float) (DX * Math.sin(angle) + DZ * Math.cos(angle));
        float tx = (float) -(fit.centreX - x), tz = (float) -(fit.centreY - y);
        eye[0] = tx - dx * DISTANCE;
        eye[1] = -DY * DISTANCE;
        eye[2] = tz - dz * DISTANCE;
        projection.setPerspective(2f * (float) Math.atan(fit.halfHeight / DISTANCE),
                (float) width / height, NEAR, FAR);
        view.setLookAt(eye[0], eye[1], eye[2], tx, 0f, tz, 0f, 1f, 0f);
    }

    /** Conservative sphere/frustum test in the same scene space as matrices(). */
    public static boolean visible(Fit fit, float camX, float camY, int width, int height,
                                  float x, float y, float z, float radius) {
        return visible(fit, camX, camY, width, height, x, y, z, radius, 0);
    }

    public static boolean visible(Fit fit, float camX, float camY, int width, int height,
                                  float x, float y, float z, float radius, int quarterTurns) {
        return visible(fit, camX, camY, width, height, x, y, z, radius, Math.floorMod(quarterTurns, 4) * 90f);
    }

    public static boolean visible(Fit fit, float camX, float camY, int width, int height,
                                  float x, float y, float z, float radius, float yawDegrees) {
        x += (float) (fit.centreX - camX);
        z += (float) (fit.centreY - camY);
        double angle = Math.toRadians(RotationCamera.normalizeYaw(yawDegrees));
        float rx = (float) (Math.cos(angle) * x + Math.sin(angle) * z);
        z = (float) (-Math.sin(angle) * x + Math.cos(angle) * z);
        x = rx;
        double depth = DISTANCE + DX * x + DY * y + DZ * z;
        if (depth + radius < NEAR || depth - radius > FAR) return false;
        double horizontal = (-x + z) / Math.sqrt(2);
        double vertical = (x + z) / Math.sqrt(8) + Math.sqrt(3) * .5 * y;
        double halfH = fit.halfHeight * depth / DISTANCE;
        double halfW = halfH * width / height;
        return Math.abs(horizontal) <= halfW + radius * 1.01
                && Math.abs(vertical) <= halfH + radius * 1.01;
    }

    private IsoCameraMath() {}
}
