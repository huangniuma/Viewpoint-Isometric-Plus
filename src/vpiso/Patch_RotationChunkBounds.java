package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.IsoChunk", methodName="IsOnScreen", strictMatch=true)
public final class Patch_RotationChunkBounds {
    @Patch.OnExit
    public static void exit(@Patch.This Object chunk, @Patch.Argument(0) boolean spare,
                            @Patch.Return(readOnly=false) boolean result) {
        result = RotationBounds.chunk(chunk, result);
    }
}
