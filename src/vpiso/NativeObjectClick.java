package vpiso;

import viewpoint.interact.MouseTargets;
import viewpoint.platform.SettingsWindow;
import zombie.input.Mouse;
import zombie.iso.IsoObject;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObjectPicker;
import zombie.ui.UIManager;

/** Refresh native click dispatch at its actual input pixel; preserve one physical press. */
public final class NativeObjectClick {
    private static IsoObject pressed;
    private static int pressX, pressY;
    private static float pressYaw;
    private static long pressTime;
    private static final IsoObjectPicker.ClickObject click = new IsoObjectPicker.ClickObject();

    public static void prepare() {
        if (!IsometricBridge.active()) { reset(); return; }
        if (RotationInteraction.nativeCursorActive() || SettingsWindow.shown()
                || (Boolean) GameAccess.field("viewpoint.platform.ImGuiFrame", "wantsMouse", null)
                || (Boolean) GameAccess.staticCall("zombie.ui.UIManager", "isModalVisible")
                || ((boolean[]) GameAccess.field("zombie.input.Mouse", "uiCaptured", null))[0]) {
            reset(); return;
        }
        int x = Mouse.getXA(), y = Mouse.getYA();
        MouseTargets.frame(true);
        if (pressed != null && (System.nanoTime() - pressTime > 2_000_000_000L
                || Math.abs(x - pressX) > 12 || Math.abs(y - pressY) > 12
                || pressYaw != RotationCamera.yawDegrees()
                || !ClientSession.currentObject(pressed))) reset();
        if (pressed != null) {
            click.tile = pressed;
            click.square = (IsoGridSquare) GameAccess.call("zombie.iso.IsoObject", "getSquare", pressed, new Class<?>[0]);
            click.x = x; click.y = y; click.lx = click.ly = 0;
            click.width = click.height = 1; click.scaleX = click.scaleY = 1f;
            click.flip = false; click.score = 0; click.z = click.square.getZ();
            UIManager.setPicked(click);
        } else UIManager.setPicked(MouseTargets.contextPick(x, y));
    }

    public static void event(String name, Object source, Object x, Object y, boolean after) {
        if (name == null) return;
        if (after) {
            if (name.equals("OnObjectLeftMouseButtonUp")) reset();
        } else if (name.equals("OnObjectLeftMouseButtonDown") && IsometricBridge.active()
                && source instanceof IsoObject object && !RotationInteraction.nativeCursorActive()) {
            pressed = object; pressX = ((Number) x).intValue(); pressY = ((Number) y).intValue();
            pressYaw = RotationCamera.yawDegrees(); pressTime = System.nanoTime();
        }
    }

    public static void reset() { pressed = null; }
    private NativeObjectClick() {}
}
