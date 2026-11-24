package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.characters.IsoPlayer", methodName="calculateAimVector", strictMatch=true)
public final class Patch_RotationAimScope {
    @Patch.OnEnter
    public static void enter(@Patch.This Object player, @Patch.Argument(0) Object vector,
            @Patch.Local("vpIsoRotationAim") boolean previous) {
        previous = RotationInteraction.enterPlayer(player);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoRotationAim") boolean previous) {
        RotationInteraction.restorePlayer(previous);
    }
}
