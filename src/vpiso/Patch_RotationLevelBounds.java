package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderLevels", methodName="calculateOnScreen", strictMatch=true)
public final class Patch_RotationLevelBounds {
    @Patch.OnExit
    public static void exit(@Patch.This Object levels, @Patch.Argument(0) int z,
                            @Patch.Return(readOnly=false) boolean result) {
        result = RotationBounds.level(levels, z, result);
    }
}
