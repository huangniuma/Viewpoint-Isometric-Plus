package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.ChunkWalk", methodName="inView")
public final class Patch_ChunkCone {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) float x, @Patch.Argument(1) float y,
                            @Patch.Argument(2) float z, @Patch.Return(readOnly=false) boolean result) {
        if (IsometricBridge.capturing()) result = IsometricBridge.inView(-x, z + 1.5f, -y);
    }
}
