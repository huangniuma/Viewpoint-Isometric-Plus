package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.Graphics;

/** The isometric option is independent of Viewpoint's other-view preference. */
@Patch(className="viewpoint.platform.LiveSettings$Toggle",methodName="get",strictMatch=true)
public final class Patch_FrameGenerationOption {
    @Patch.OnExit
    public static void exit(@Patch.This Object option,@Patch.Return(readOnly=false) boolean value) {
        if(Main.compatible && option==Graphics.FRAME_GENERATION && IsometricBridge.modelRendering())
            value=IsometricControls.frameGeneration() && IsometricFrameGeneration.available();
    }
}
