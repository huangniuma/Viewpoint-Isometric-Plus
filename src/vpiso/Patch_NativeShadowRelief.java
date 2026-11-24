package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;

/** Isometric native layers and interactions share the original game's terrain height. */
@Patch(className="viewpoint.far.Relief", methodName="frame", strictMatch=true)
public final class Patch_NativeShadowRelief {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Frame frame) {
        // Keep VP's loading/bookkeeping; clear both scene and model terrain before capture.
        // Native visibility, water, trees and interactions still use the original flat world.
        if (IsometricBridge.nativeShadowCapture() || Main.compatible && IsometricBridge.capturing()) frame.scene.relief(null);
    }
}
