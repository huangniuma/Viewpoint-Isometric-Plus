package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.MouseLua", methodName="worldY", strictMatch=true)
public final class Patch_NativeMouseY {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) Double result) {
        if (IsometricBridge.active() && !RotationCamera.active()) result = null;
    }
}
