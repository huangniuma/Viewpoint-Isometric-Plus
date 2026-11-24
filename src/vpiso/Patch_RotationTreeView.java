package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees", methodName="pushModelViewMatrix", strictMatch=true)
public final class Patch_RotationTreeView {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) float x, @Patch.Argument(1) float y,
                                @Patch.Argument(2) float z) { return RotationTrees.modelView(x, y, z); }
}
