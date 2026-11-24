package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.characters.IsoPlayer", methodName="updateInternal2", strictMatch=true)
public final class Patch_RotationPlayerInput {
    @Patch.OnEnter
    public static void enter(@Patch.This Object player, @Patch.Local("vpIsoRotationPlayer") boolean previous) {
        previous = RotationInteraction.enterPlayer(player);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoRotationPlayer") boolean previous) {
        RotationInteraction.restorePlayer(previous);
    }
}
