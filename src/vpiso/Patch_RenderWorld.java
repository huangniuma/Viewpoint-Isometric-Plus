package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.FP", methodName="renderWorld")
public final class Patch_RenderWorld {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object cell, @Patch.Return(readOnly=false) boolean result) {
        result = IsometricBridge.afterWorld(cell, result);
    }
}
