package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.sim.SkeletonPort", methodName="animated")
public final class Patch_SkeletonAnimated {
    // Keep the optional VP port idle from startup; native model updates still run.
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() { return Main.compatible; }
}
