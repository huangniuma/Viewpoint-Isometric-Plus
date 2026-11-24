package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.Vector2;

@Patch(className="zombie.ui.UIManager", methodName="getPickedTile", strictMatch=true)
public final class Patch_RotationPickedTile {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) Vector2 result) {
        result = RotationInteraction.pickedTile(result, false);
    }
}
