package vpiso;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import viewpoint.interact.MouseTargets;
import viewpoint.render.MousePick;
import zombie.iso.Vector2;

/** Camera-coordinate adapters; native cursor actions and their previews remain game-owned. */
public final class RotationInteraction {
    private static final ThreadLocal<Boolean> PLAYER_SCOPE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<double[]> GROUND = ThreadLocal.withInitial(() -> new double[2]);
    private static final ThreadLocal<float[]> SCREEN = ThreadLocal.withInitial(() -> new float[2]);
    private static final ThreadLocal<Vector2> TILE = ThreadLocal.withInitial(Vector2::new);
    private static final ThreadLocal<Vector2> LOCAL_TILE = ThreadLocal.withInitial(Vector2::new);
    private static final IdentityHashMap<MousePick.Ask, Boolean> ACCEPTED_ASKS = new IdentityHashMap<>();
    private static final ArrayDeque<MousePick.Ask> ASK_ORDER = new ArrayDeque<>();
    private static volatile boolean pickingPrepared;
    private static float preparedYaw = Float.NaN;
    private static final Class<?>[] NO_ARGS = new Class<?>[0];
    private static final Class<?>[] INT = { int.class };

    public static boolean nativeCursorActive() {
        Object world = GameAccess.field("zombie.iso.IsoWorld", "instance", null);
        if (world == null) return false;
        Object cell = GameAccess.call("zombie.iso.IsoWorld", "getCell", world, NO_ARGS);
        return cell != null && GameAccess.call("zombie.iso.IsoCell", "getDrag", cell, INT, 0) != null;
    }

    public static void cameraChanged() {
        NativeObjectState.resetPick();
        synchronized (ACCEPTED_ASKS) {
            ACCEPTED_ASKS.clear();
            ASK_ORDER.clear();
            MousePick.ask = null;
            MousePick.hit = null;
            preparedYaw = Float.NaN;
        }
    }

    /** Call after configuring the camera and before submitting its VP world draw. */
    public static void prepareFrame() {
        float yaw = RotationCamera.yawDegrees();
        synchronized (ACCEPTED_ASKS) {
            if (preparedYaw != yaw) {
                // Pending GPU reads retain their original Ask object. Reusing only its pixel
                // coordinates would accept a hit produced by the previous camera direction.
                ACCEPTED_ASKS.clear();
                ASK_ORDER.clear();
                MousePick.ask = null;
                MousePick.hit = null;
                preparedYaw = yaw;
            }
            MouseTargets.frame(true);
            MousePick.Ask ask = MousePick.ask;
            if (ask != null && !ACCEPTED_ASKS.containsKey(ask)) {
                ACCEPTED_ASKS.put(ask, Boolean.TRUE);
                ASK_ORDER.addLast(ask);
                while (ASK_ORDER.size() > 16) ACCEPTED_ASKS.remove(ASK_ORDER.removeFirst());
            }
            pickingPrepared = true;
        }
    }

    public static MousePick.Hit currentHit(MousePick.Hit hit) {
        if (hit == null || !pickingPrepared || !IsometricBridge.active()) return hit;
        synchronized (ACCEPTED_ASKS) {
            return ACCEPTED_ASKS.containsKey(hit.ask()) ? hit : null;
        }
    }

    public static float screenToWorld(int player, float x, float y, float z, boolean axisX, float original) {
        if (player != 0 || !RotationCamera.active()) return original;
        double[] point = GROUND.get();
        return RotationCamera.ground(x, y, z, point) ? (float) point[axisX ? 0 : 1] : original;
    }

    public static float worldToScreen(int player, float x, float y, float z, boolean axisX, float original) {
        if (player != 0 || !RotationCamera.active()) return original;
        float[] point = SCREEN.get();
        return RotationCamera.project(x, y, z, point) ? point[axisX ? 0 : 1] : original;
    }

    public static Vector2 pickedTile(Vector2 original, boolean local) {
        if (!RotationCamera.active()) return original;
        double[] point = GROUND.get();
        int xScreen = ((Number) GameAccess.staticCall("zombie.input.Mouse", "getXA")).intValue();
        int yScreen = ((Number) GameAccess.staticCall("zombie.input.Mouse", "getYA")).intValue();
        float z = ((Number) GameAccess.staticCall("zombie.iso.IsoCamera", "getCameraCharacterZ")).floatValue();
        if (!RotationCamera.ground(xScreen, yScreen, Math.floor(z), point)) return original;
        double x = Math.floor(point[0]), y = Math.floor(point[1]);
        Vector2 result = (local ? LOCAL_TILE : TILE).get();
        return result.set((float) (local ? point[0] - x : x), (float) (local ? point[1] - y : y));
    }

    /** Restrict IsoUtils overrides to the local player's own input/aim calculations. */
    public static boolean enterPlayer(Object player) {
        boolean previous = PLAYER_SCOPE.get();
        PLAYER_SCOPE.set(previous || RotationCamera.active() && localPlayer(player));
        return previous;
    }

    public static void restorePlayer(boolean previous) { PLAYER_SCOPE.set(previous); }

    public static float aimCoordinate(int player, float x, float y, float z, boolean axisX, float original) {
        if (player != 0 || !PLAYER_SCOPE.get() || !RotationCamera.active() || IsometricBridge.capturing()) return original;
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        float zoom = ((Number) GameAccess.call("zombie.core.Core", "getZoom", core, INT, player)).floatValue();
        if (!(zoom > 0f)) return original;
        // AimingReticle.getX/Y and IsoUtils consume offscreen pixels; ground consumes screen pixels.
        return screenToWorld(player, x / zoom, y / zoom, z, axisX, original);
    }

    public static void movement(Object player, Vector2 direction) {
        if (!RotationCamera.active() || direction == null || !localPlayer(player)
                || GameAccess.call("zombie.characters.IsoGameCharacter", "getVehicle", player, NO_ARGS) != null) return;
        float x = direction.x, y = direction.y;
        double angle = Math.toRadians(RotationCamera.yawDegrees());
        direction.set((float) (Math.cos(angle) * x - Math.sin(angle) * y),
                (float) (Math.sin(angle) * x + Math.cos(angle) * y));
    }

    private static boolean localPlayer(Object player) {
        Object[] players = (Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null);
        return players != null && players.length > 0 && player != null && player == players[0];
    }

    private RotationInteraction() {}
}
