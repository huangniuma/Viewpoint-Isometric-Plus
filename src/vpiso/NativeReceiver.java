package vpiso;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import viewpoint.core.Frame;

/** Inverts the game's own screen/depth map, without using a VP surface or model. */
public final class NativeReceiver {
    public final Matrix4f inverse = new Matrix4f();
    public final float[] origin = new float[3];
    public float worldZ;

    public void capture(Frame frame, int player) {
        capture(frame, player, false);
    }

    /** Match emitted native chunks; water/visibility use the unshifted capture above. */
    public void captureWorld(Frame frame, int player) {
        capture(frame, player, true);
    }

    private void capture(Frame frame, int player, boolean nativeWorld) {
        worldZ=frame.camZ;
        int w = size("getOffscreenWidth", player), h = size("getOffscreenHeight", player);
        int sx = (int) Math.floor(frame.camX), sy = (int) Math.floor(frame.camY);
        float shiftX = 0f, shiftY = 0f, shiftDepth = 0f;
        if (nativeWorld) {
            Object state = GameAccess.field("zombie.iso.IsoCamera", "frameState", null);
            sx = (int) Math.floor(number(state, "camCharacterX"));
            sy = (int) Math.floor(number(state, "camCharacterY"));
            Object camera = ((Object[]) GameAccess.field("zombie.iso.IsoCamera", "cameras", null))[player];
            float zoom = number(state, "zoom");
            shiftX = number(camera, "fixJigglyModelsX") * zoom;
            shiftY = number(camera, "fixJigglyModelsY") * zoom;
            Object debug = GameAccess.field("zombie.debug.DebugOptions", "instance", null);
            Object options = GameAccess.field(debug.getClass().getName(), "fboRenderChunk", debug);
            Object combined = GameAccess.field(options.getClass().getName(), "combinedFbo", options);
            if (!(Boolean) GameAccess.call("zombie.debug.BooleanDebugOption", "getValue", combined, new Class<?>[0]))
                shiftDepth = -(number(camera, "fixJigglyModelsSquareX") + number(camera, "fixJigglyModelsSquareY"))
                        * (.46187335f / 8f / 40f);
        }
        double[][] samples = new double[4][3];
        for (int i = 0; i < 4; i++) {
            float x = frame.camX + (i == 1 ? 1 : 0), y = frame.camY + (i == 2 ? 1 : 0), z = frame.camZ + (i == 3 ? 1 : 0);
            samples[i][0] = (screen("XToScreenExact", x, y, z, player) + shiftX) / w;
            samples[i][1] = 1.0 - (screen("YToScreenExact", x, y, z, player) + shiftY) / h;
            samples[i][2] = NativeVision.sample(sx, sy, x, y, z) + shiftDepth;
        }
        fit(samples);
    }

    /** Each sample is normalized screen X, bottom-up screen Y, native window depth. */
    public void fit(double[][] samples) {
        for (int r = 0; r < 3; r++) origin[r] = (float) samples[0][r];
        double[] values = new double[16];
        values[15] = 1;
        for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++) values[c * 4 + r] = samples[c + 1][r] - samples[0][r];
        // Array set recomputes JOML's matrix properties; indexed set retains identity flags.
        Matrix4d forward = new Matrix4d().set(values);
        if (!Double.isFinite(forward.determinant()) || Math.abs(forward.determinant()) < 1e-16) {
            throw new IllegalStateException("Native screen/depth transform is singular");
        }
        inverse.set(forward.invert().get(new float[16]));
    }

    private static int size(String method, int player) {
        return ((Number) GameAccess.call("zombie.iso.IsoCamera", method, null, new Class<?>[] { int.class }, player)).intValue();
    }
    private static float number(Object owner, String field) {
        return ((Number) GameAccess.field(owner.getClass().getName(), field, owner)).floatValue();
    }
    private static double screen(String method, float x, float y, float z, int player) {
        return ((Number) GameAccess.call("zombie.iso.IsoUtils", method, null,
                new Class<?>[] { float.class, float.class, float.class, int.class }, x, y, z, player)).doubleValue();
    }
}
