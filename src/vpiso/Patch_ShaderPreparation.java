package vpiso;

import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.AheadPlan", methodName="settings", strictMatch=true)
public final class Patch_ShaderPreparation {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) String requested,
                            @Patch.Return(readOnly=false) List<?> settings) {
        settings = ShaderPreparation.current(requested, settings);
    }
}
