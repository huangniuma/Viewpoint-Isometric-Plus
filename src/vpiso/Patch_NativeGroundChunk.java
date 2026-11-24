package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderChunk", methodName="beginRenderThread", strictMatch=true)
public final class Patch_NativeGroundChunk {
    @Patch.OnExit
    public static void exit(@Patch.This Object chunk,@Patch.Argument(0) boolean clear) { NativeGround.chunkBegin(chunk,clear); }
}
