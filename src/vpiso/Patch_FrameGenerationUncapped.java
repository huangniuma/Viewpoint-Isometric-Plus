package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.Hooks",methodName="framerateUncapped",strictMatch=true)
public final class Patch_FrameGenerationUncapped {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) boolean original,@Patch.Return(readOnly=false) boolean value) {
        if(Main.compatible)value=IsometricFrameGeneration.uncapped(value);
    }
}
