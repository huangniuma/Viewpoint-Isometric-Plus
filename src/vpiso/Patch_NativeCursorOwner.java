package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** One cursor evaluation belongs to the model frame, rather than both UI and render tails. */
@Patch(className="zombie.iso.IsoCell", methodName="DoBuilding", strictMatch=true)
public final class Patch_NativeCursorOwner {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) int player, @Patch.Argument(1) boolean render) {
        return player == 0 && !NativeCursorPreview.capturing() && IsometricBridge.rotationAllowed();
    }
}
