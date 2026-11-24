package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** Reuse the game's limiter; never rewrite stored game or Viewpoint caps. */
@Patch(className="viewpoint.Hooks",methodName="lockFps",strictMatch=true)
public final class Patch_FrameGenerationRate {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) int original,@Patch.Return(readOnly=false) int value) {
        if(Main.compatible)value=IsometricFrameGeneration.lockFps(value);
    }
}
