package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** The addon's switch owns only its model frames. VP's ordinary cameras are untouched. */
@Patch(className="viewpoint.render.GrassPass", methodName="draw")
public final class Patch_IsometricGrass {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() {
        return IsometricBridge.modelRendering() && !IsometricBridge.renderEffects().grass();
    }
}
