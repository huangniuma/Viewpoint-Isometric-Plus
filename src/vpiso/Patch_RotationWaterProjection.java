package vpiso;
import org.joml.Matrix4f;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.IsoWater", methodName="waterProjection", strictMatch=true)
public final class Patch_RotationWaterProjection {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Matrix4f matrix) {
        if (Main.compatible) RotationWater.projection(matrix);
    }
}
