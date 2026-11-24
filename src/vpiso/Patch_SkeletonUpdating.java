package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.sim.SkeletonPort", methodName="updating", strictMatch=false)
public final class Patch_SkeletonUpdating {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() { return Main.compatible; }
}
