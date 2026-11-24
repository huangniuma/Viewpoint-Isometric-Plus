package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees", methodName="pushProjectionMatrix", strictMatch=true)
public final class Patch_RotationTreeProjection {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() { return RotationTrees.projection(); }
}
