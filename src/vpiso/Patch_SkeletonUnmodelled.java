package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.sim.SkeletonPort", methodName="updatingUnmodelled", strictMatch=false)
public final class Patch_SkeletonUnmodelled {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() { return Main.compatible; }
}
