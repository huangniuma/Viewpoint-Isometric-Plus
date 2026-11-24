package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.PlayerCamera", methodName="XToScreenExact", strictMatch=true)
public final class Patch_RotationVisionX {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) float x, @Patch.Argument(1) float y,
                            @Patch.Argument(2) float z, @Patch.Return(readOnly=false) float result) {
        Float projected = RotationLayers.projectAxis(x, y, z, 0);
        if (projected != null) result = projected;
    }
}
