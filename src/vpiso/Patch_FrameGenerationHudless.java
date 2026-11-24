package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** VP normally queues this boundary only while its first-person view is enabled. */
@Patch(className="viewpoint.Hooks",methodName="cursorRendering",strictMatch=true)
public final class Patch_FrameGenerationHudless {
    @Patch.OnExit
    public static void exit() { if(Main.compatible) IsometricFrameGeneration.queueHudless(); }
}
