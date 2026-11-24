package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.IsoDepthHelper", methodName="getSquareDepthData", strictMatch=true)
public final class Patch_RotationVisionDepth {
    @Patch.OnExit
    public static void exit(@Patch.Argument(2) float x, @Patch.Argument(3) float y,
                            @Patch.Argument(4) float z, @Patch.Return Object result) {
        RotationLayers.depth(result, x, y, z);
    }
}
