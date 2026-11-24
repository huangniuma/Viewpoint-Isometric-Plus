package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Consume settings on VP's update thread, including while its 3D view is active. */
@Patch(className="viewpoint.platform.SettingsWindow", methodName="update", strictMatch=true)
public final class Patch_IsometricSettings {
    @Patch.OnExit
    public static void exit() {
        if (Main.compatible) IsometricControls.tick();
    }
}
