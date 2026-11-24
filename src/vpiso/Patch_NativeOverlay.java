package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Original world, cutaways, trees, visibility and interaction finish before the overlay. */
@Patch(className="zombie.iso.IsoCell", methodName="render", strictMatch=true)
public final class Patch_NativeOverlay {
    @Patch.OnEnter
    public static void enter() { IsometricBridge.beforeNativeWorld(); }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.This Object cell) {
        try { if (Main.compatible) IsometricBridge.afterNativeWorld(cell); }
        finally { IsometricBridge.finishNativeWorld(); }
    }
}
