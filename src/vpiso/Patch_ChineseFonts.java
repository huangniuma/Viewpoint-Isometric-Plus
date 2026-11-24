package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.Theme", methodName="fonts", strictMatch=true)
public final class Patch_ChineseFonts {
    @Patch.OnExit
    public static void exit() {
        if (Main.compatible) IsometricText.afterThemeFonts();
    }
}
