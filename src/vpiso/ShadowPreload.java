package vpiso;

import viewpoint.platform.LiveSettings;

/** Prioritizes the existing VP preloader; never captures another world or stores shadow depth. */
public final class ShadowPreload {
    private static final ThreadLocal<Camera> CAMERA = new ThreadLocal<>();
    public record Camera(IsoCameraMath.Fit fit, float x, float y, int width, int height, float yaw) {}

    /** The same local-client gate is used before map adoption and before existing upkeep. */
    public static boolean loadingAllowed() {
        if (zombie.network.GameServer.server || !IsometricBridge.shadowPreloadAllowed()) return false;
        Object[] players = (Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null);
        return players != null && players.length > 0 && players[0] != null
                && GameAccess.staticCall("zombie.iso.IsoCamera", "getCameraCharacter") == players[0];
    }

    public static Camera begin(Object character) {
        Camera previous = CAMERA.get();
        CAMERA.remove();
        try {
            Camera next = camera(character);
            if (next != null) CAMERA.set(next);
        } catch (RuntimeException | LinkageError unavailable) {
            // This advice runs before VP's own try block. A camera/provider
            // failure must fall back to original upkeep, not abort world drawing.
        }
        return previous;
    }

    private static Camera camera(Object character) {
        // Preserve VP's own preload switch, readiness, budget, job ring and freshness.
        if (character == null || !loadingAllowed()
                || (Boolean) GameAccess.field("viewpoint.IsoUpkeep", "broken", null)
                || !((LiveSettings.Toggle) GameAccess.field("viewpoint.IsoUpkeep", "ON", null)).get()
                || !(Boolean) GameAccess.staticCall("viewpoint.assets.Warmup", "rendererReady")) return null;
        int width = ((Number) GameAccess.call("zombie.iso.IsoCamera", "getScreenWidth", null,
                new Class<?>[] { int.class }, 0)).intValue();
        int height = ((Number) GameAccess.call("zombie.iso.IsoCamera", "getScreenHeight", null,
                new Class<?>[] { int.class }, 0)).intValue();
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        float zoom = ((Number) GameAccess.call("zombie.core.Core", "getZoom", core,
                new Class<?>[] { int.class }, 0)).floatValue();
        if (width <= 0 || height <= 0 || !Float.isFinite(zoom) || zoom <= 0f) return null;
        float x = GameAccess.coordinate(character, "getX"), y = GameAccess.coordinate(character, "getY");
        float z = GameAccess.coordinate(character, "getZ");
        double px = screen("XToScreenExact", x, y, z) / zoom, py = screen("YToScreenExact", x, y, z) / zoom;
        double ex = screen("XToScreenExact", x+1, y, z) / zoom-px, ey = screen("YToScreenExact", x+1, y, z) / zoom-py;
        double sx = screen("XToScreenExact", x, y+1, z) / zoom-px, sy = screen("YToScreenExact", x, y+1, z) / zoom-py;
        if (!Double.isFinite(ex*sy-sx*ey) || Math.abs(ex*sy-sx*ey) < 1e-7) return null;
        float yaw = IsometricBridge.shadowPreloadYaw();
        IsoCameraMath.Fit fit = RotationCamera.rotateFit(IsoCameraMath.fit(x,y,px,py,ex,ey,sx,sy,width,height),x,y,yaw);
        return new Camera(fit,x,y,width,height,yaw);
    }

    public static void restore(Camera previous) {
        if (previous == null) CAMERA.remove(); else CAMERA.set(previous);
    }

    /** Only ChunkWalk.keep's queue priority changes; normal colour/caster draw lists are untouched. */
    public static boolean visible(float x, float y, float up, boolean original) {
        Camera camera = CAMERA.get();
        return camera == null ? original : IsoCameraMath.visible(camera.fit, camera.x, camera.y,
                camera.width, camera.height, -x, up+1.5f, -y, 20f, camera.yaw);
    }

    private static double screen(String method, float x, float y, float z) {
        return ((Number) GameAccess.call("zombie.iso.IsoUtils", method, null,
                new Class<?>[] { float.class, float.class, float.class, int.class }, x,y,z,0)).doubleValue();
    }
    private ShadowPreload() {}
}
