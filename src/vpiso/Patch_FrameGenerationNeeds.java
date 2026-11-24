package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Reuse VP's existing native-library availability/install UI for the independent option. */
@Patch(className="viewpoint.upscale.UpscaleNeeds",methodName="of",strictMatch=true)
public final class Patch_FrameGenerationNeeds {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(value=0,readOnly=false) String key) {
        if(Main.compatible && "vpiso.frameGeneration".equals(key))key="graphics.frameGeneration";
    }
}
