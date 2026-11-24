package vpiso;
import java.util.ArrayList;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.core.opengl.ShaderUnit", methodName="loadShaderFile", strictMatch=true)
public final class Patch_RotationWaterSource {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) String path, @Patch.Argument(1) ArrayList<?> includes,
                            @Patch.Return(readOnly=false) String source) {
        if (Main.compatible) source = RotationWater.rewrite(path, source);
    }
}
