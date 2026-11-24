import java.lang.reflect.Array;
import java.lang.reflect.Field;
import org.joml.Matrix4f;
import viewpoint.core.Frame;
import viewpoint.core.View;
import viewpoint.render.MousePick;
import vpiso.GameAccess;
import vpiso.IsoCameraMath;
import vpiso.IsometricBridge;
import vpiso.RotationCamera;
import vpiso.RotationInteraction;
import zombie.iso.Vector2;

/** Real game cursor storage and VP pick records; isolated world, no game launch or GL draw. */
public final class RotationInteractionProbe {
    private static final sun.misc.Unsafe UNSAFE;
    static {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) field.get(null);
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    public static void main(String[] args) throws Exception {
        Class<?> worldType = Class.forName("zombie.iso.IsoWorld");
        Object previousWorld = GameAccess.field("zombie.iso.IsoWorld", "instance", null);
        Object[] players = (Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null);
        Object previousPlayer = players[0];
        Object world = UNSAFE.allocateInstance(worldType);
        Object cell = UNSAFE.allocateInstance(Class.forName("zombie.iso.IsoCell"));
        Class<?> tableType = Class.forName("se.krka.kahlua.vm.KahluaTable");
        Object drag = Array.newInstance(tableType, 4);
        GameAccess.set("zombie.iso.IsoWorld", "currentCell", world, cell);
        GameAccess.set("zombie.iso.IsoCell", "drag", cell, drag);
        Object actor = UNSAFE.allocateInstance(Class.forName("zombie.characters.IsoPlayer"));
        players[0] = actor;
        GameAccess.set("zombie.iso.IsoWorld", "instance", null, world);
        vpiso.Main.compatible = true;
        View.enabled = false;
        IsometricBridge.setEnabled(true);
        IsometricBridge.setModelsEnabled(true);
        GameAccess.set("vpiso.IsometricBridge", "submitted", null, true);
        try {
            RotationCamera.configure(true, 1);
            require(RotationCamera.quarterTurns() == 1, "rotation did not activate without native cursor");
            camera(1);
            float[] projected = new float[2];
            require(RotationCamera.project(102.25, 198.75, 0, projected), "fixture point did not project");
            close(RotationInteraction.screenToWorld(0, projected[0], projected[1], 0, true, -99f), 102.25);
            close(RotationInteraction.screenToWorld(0, projected[0], projected[1], 0, false, -99f), 198.75);
            close(RotationInteraction.worldToScreen(0, 102.25f, 198.75f, 0, true, -99f), projected[0]);
            close(RotationInteraction.screenToWorld(1, projected[0], projected[1], 0, true, -99f), -99);
            close(RotationInteraction.aimCoordinate(0, projected[0], projected[1], 0, true, -99f), -99);

            boolean outer = RotationInteraction.enterPlayer(actor);
            Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
            float zoom = ((Number) GameAccess.call("zombie.core.Core", "getZoom", core,
                    new Class<?>[] { int.class }, 0)).floatValue();
            require(zoom > 0, "native zoom fixture is invalid");
            close(RotationInteraction.aimCoordinate(0, projected[0] * zoom, projected[1] * zoom,
                    0, true, -99f), 102.25);
            close(RotationInteraction.aimCoordinate(0, projected[0] * zoom, projected[1] * zoom,
                    0, false, -99f), 198.75);
            boolean nested = RotationInteraction.enterPlayer(new Object());
            RotationInteraction.restorePlayer(nested);
            RotationInteraction.restorePlayer(outer);
            close(RotationInteraction.aimCoordinate(0, 100, 200, 0, true, -99f), -99);

            for (int quarter = 1; quarter <= 3; quarter++) {
                RotationCamera.configure(true, quarter);
                camera(quarter);
                Vector2 direction = new Vector2().set(1, 0);
                RotationInteraction.movement(actor, direction);
                float expectedX = quarter == 2 ? -1 : 0;
                float expectedY = quarter == 1 ? 1 : quarter == 3 ? -1 : 0;
                close(direction.x, expectedX);
                close(direction.y, expectedY);
                Vector2 other = new Vector2().set(1, 0);
                RotationInteraction.movement(new Object(), other);
                close(other.x, 1); close(other.y, 0);
                // Native updateMovementFromInput converts the input basis to world
                // (x+y, y-x). The W key must still project upward after rotation.
                Vector2 up = new Vector2().set(0, -1);
                RotationInteraction.movement(actor, up);
                float[] centre = new float[2], toward = new float[2];
                require(RotationCamera.project(100, 200, 0, centre)
                        && RotationCamera.project(100 + up.x + up.y, 200 + up.y - up.x, 0, toward),
                        "movement fixture did not project");
                require(Math.abs(toward[0] - centre[0]) < .1 && toward[1] < centre[1],
                        "W key no longer moves up on screen at quarter " + quarter);
            }

            for (float yaw : new float[] { .25f, 17.25f, 44.9f, 45f, 89.5f, 123.75f, 225f, 359.75f }) {
                RotationCamera.configureYaw(true, yaw);
                camera(yaw);
                close(RotationCamera.yawDegrees(), yaw);
                Vector2 up = new Vector2().set(0, -1);
                RotationInteraction.movement(actor, up);
                close(Math.hypot(up.x, up.y), 1);
                float[] centre = new float[2], toward = new float[2];
                require(RotationCamera.project(100, 200, 0, centre)
                        && RotationCamera.project(100 + up.x + up.y, 200 + up.y - up.x, 0, toward),
                        "continuous movement fixture did not project");
                require(Math.abs(toward[0] - centre[0]) < .1 && toward[1] < centre[1],
                        "W key no longer moves up on screen at yaw " + yaw);
            }

            RotationCamera.configure(true, 1);
            RotationInteraction.prepareFrame();
            MousePick.Ask firstAsk = MousePick.ask;
            MousePick.Hit firstHit = hit(firstAsk);
            require(RotationInteraction.currentHit(firstHit) == firstHit, "current VP GPU pick was rejected");
            RotationCamera.configure(true, 2);
            require(RotationInteraction.currentHit(firstHit) == null, "camera switch accepted pending old GPU pick");
            RotationInteraction.prepareFrame();
            require(firstAsk != MousePick.ask, "camera switch reused pick identity");
            require(RotationInteraction.currentHit(firstHit) == null, "same mouse pixels accepted old camera hit");
            require(RotationInteraction.currentHit(hit(MousePick.ask)) != null, "new camera pick was rejected");

            RotationCamera.configureYaw(true, 31.5f);
            RotationInteraction.prepareFrame();
            MousePick.Hit sameQuarterHit = hit(MousePick.ask);
            RotationCamera.configureYaw(true, 31.75f);
            RotationInteraction.prepareFrame();
            require(RotationInteraction.currentHit(sameQuarterHit) == null,
                    "sub-degree camera change accepted the prior GPU pick");

            Object cursor = Class.forName("se.krka.kahlua.j2se.KahluaTableImpl")
                    .getConstructor(java.util.Map.class).newInstance(new java.util.HashMap<>());
            Array.set(drag, 0, cursor);
            RotationCamera.configureYaw(true, 123.75f);
            require(RotationCamera.yawDegrees() == 123.75f,
                    "Y/native placement cursor reset the selected camera angle");
            require(Array.get(drag, 0) == cursor, "rotation replaced the native cursor");
            camera(123.75f);
            require(RotationCamera.project(102.25, 198.75, 0, projected), "cursor target did not project");
            close(RotationInteraction.screenToWorld(0, projected[0], projected[1], 0, true, -99f), 102.25);
            close(RotationInteraction.screenToWorld(0, projected[0], projected[1], 0, false, -99f), 198.75);
            Array.set(drag, 0, null);
            RotationCamera.refresh();
            require(RotationCamera.yawDegrees() == 123.75f,
                    "ending native cursor changed the requested angle");
            require(Array.get(drag, 0) == null, "ending native cursor changed cursor storage");
            RotationCamera.configureYaw(false, 123.75f);
            require(!RotationCamera.active() && RotationCamera.savedYawDegrees() == 123.75f,
                    "disabling rotation lost the saved continuous direction");
            RotationCamera.configureYaw(true, RotationCamera.savedYawDegrees());
            require(RotationCamera.yawDegrees() == 123.75f, "saved continuous direction did not restore");
            System.out.println("Rotation interaction PASS: actual native cursor retains selected yaw and screen/world target, including Y Walk To; camera-specific VP pick identity including sub-degree changes; inactive/player guards; nested input scope; continuous direction and speed preservation. No world/GPU draw.");
        } finally {
            RotationCamera.configure(false, 0);
            IsometricBridge.setModelsEnabled(false);
            players[0] = previousPlayer;
            GameAccess.set("zombie.iso.IsoWorld", "instance", null, previousWorld);
        }
    }

    private static void camera(int quarter) {
        camera(Math.floorMod(quarter, 4) * 90f);
    }

    private static void camera(float yaw) {
        Frame frame = new Frame();
        frame.camX = 100; frame.camY = 200; frame.camZ = 0;
        Matrix4f view = new Matrix4f();
        IsoCameraMath.matrices(new IsoCameraMath.Fit(100, 200, 12), 100, 200,
                1280, 720, frame.scene.projection, view, new float[3], yaw);
        RotationCamera.capture(frame, view, 1280, 720, 1);
    }

    private static MousePick.Hit hit(MousePick.Ask ask) {
        return new MousePick.Hit(ask, 102.25, 198.75, 0, 0, 1, 0, null);
    }

    private static void close(double actual, double expected) {
        if (Math.abs(actual - expected) > .03) throw new AssertionError(actual + " != " + expected);
    }
    private static void require(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }
}
