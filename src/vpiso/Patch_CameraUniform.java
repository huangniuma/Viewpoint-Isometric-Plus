package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.GlProgram", methodName="set")
public final class Patch_CameraUniform {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) String name,
                              @Patch.Argument(value=1, readOnly=false) float x,
                              @Patch.Argument(value=2, readOnly=false) float y,
                              @Patch.Argument(3) float z, @Patch.Argument(4) float w) {
        if (IsometricBridge.rendering() && name.equals("uCamera")) {
            x = IsometricBridge.NEAR;
            y = IsometricBridge.FAR;
        }
    }
}
