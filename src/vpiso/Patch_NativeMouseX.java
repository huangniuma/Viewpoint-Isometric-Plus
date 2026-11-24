package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.MouseLua", methodName="worldX", strictMatch=true)
public final class Patch_NativeMouseX {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) Double result) {
        if (IsometricBridge.active() && !RotationCamera.active()) result = null;
    }
}
