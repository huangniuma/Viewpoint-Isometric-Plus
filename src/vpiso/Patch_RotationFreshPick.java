package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.MousePick;

@Patch(className="viewpoint.interact.MouseTargets", methodName="fresh", strictMatch=true)
public final class Patch_RotationFreshPick {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) MousePick.Hit result) {
        result = RotationInteraction.currentHit(result);
    }
}
