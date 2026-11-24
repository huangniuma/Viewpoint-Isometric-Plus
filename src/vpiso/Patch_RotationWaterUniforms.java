package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.WaterShader", methodName="updateWaterParams", strictMatch=true)
public final class Patch_RotationWaterUniforms {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object draw, @Patch.Argument(1) int player) {
        RotationWater.uniforms();
    }
}
