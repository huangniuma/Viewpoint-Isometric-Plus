package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderChunk", methodName="endRenderThread", strictMatch=true)
public final class Patch_NativeGroundChunkEnd {
    @Patch.OnEnter
    public static void enter() { NativeGround.chunkEnd(); }
}
