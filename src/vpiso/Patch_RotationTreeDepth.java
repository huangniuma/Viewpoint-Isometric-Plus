package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees", methodName="calculateDepth", strictMatch=true)
public final class Patch_RotationTreeDepth {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) Object tree) { return RotationTrees.active(); }
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object tree, @Patch.Argument(1) boolean leaves,
                            @Patch.Return(readOnly=false) float result) {
        if (RotationTrees.active()) result = RotationTrees.treeDepth(tree) + (leaves ? RotationTrees.leafDepth(tree) : 0f);
    }
}
