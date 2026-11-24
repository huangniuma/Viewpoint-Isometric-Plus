package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.SettingsPages", methodName="header", strictMatch=true)
public final class Patch_IsometricPageHeader {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) Object page, @Patch.Argument(1) float x,
                                @Patch.Argument(2) float y, @Patch.Argument(3) float width,
                                @Patch.Argument(4) boolean minimized) {
        if (!Main.compatible || !IsometricSettingsPage.ours(page)) return false;
        IsometricSettingsPage.header(x, y, width, minimized);
        return true;
    }
}
