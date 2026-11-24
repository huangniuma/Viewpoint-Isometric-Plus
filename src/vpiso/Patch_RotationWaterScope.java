package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.core.skinnedmodel.ModelManager", methodName="RenderWater", strictMatch=true)
public final class Patch_RotationWaterScope {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object draw, @Patch.Argument(1) int shader,
                             @Patch.Argument(2) int player, @Patch.Argument(3) int start,
                             @Patch.Argument(4) int count, @Patch.Argument(5) boolean shore,
                             @Patch.Local("vpIsoRotatedWater") Object previous) {
        previous = RotationWater.enter(player, start, shore);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoRotatedWater") Object previous) { RotationWater.leave(previous); }
}
