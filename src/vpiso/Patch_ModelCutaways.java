package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;

/** Hide independent upper objects from the camera while retaining physical light occlusion. */
@Patch(className="viewpoint.models.Models", methodName="own")
public final class Patch_ModelCutaways {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Frame frame, @Patch.Local("vpIsoModelFirst") int first) {
        first = Main.compatible && IsometricBridge.capturing() && !IsometricBridge.nativeShadowCapture()
                ? frame.scene.models.count() : -1;
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Frame frame, @Patch.Argument(2) Object square,
                            @Patch.Argument(3) Object object,
                            @Patch.Local("vpIsoModelFirst") int first) {
        IsometricVisibility.applyModels(frame, square, object, first);
    }

    private Patch_ModelCutaways() {}
}
