package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.far.FarWorld", methodName="update")
public final class Patch_FarWorld {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() { return IsometricBridge.capturing(); }
}
