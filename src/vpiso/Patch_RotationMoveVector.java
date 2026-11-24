package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.Vector2;

@Patch(className="zombie.characters.IsoPlayer", methodName="getInputMoveVector", strictMatch=true)
public final class Patch_RotationMoveVector {
    @Patch.OnExit
    public static void exit(@Patch.This Object player, @Patch.Argument(0) Vector2 direction) {
        RotationInteraction.movement(player, direction);
    }
}
