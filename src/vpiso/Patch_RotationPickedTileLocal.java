package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.Vector2;

@Patch(className="zombie.ui.UIManager", methodName="getPickedTileLocal", strictMatch=true)
public final class Patch_RotationPickedTileLocal {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) Vector2 result) {
        result = RotationInteraction.pickedTile(result, true);
    }
}
