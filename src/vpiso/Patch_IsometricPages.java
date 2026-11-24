package vpiso;

import java.util.List;
import java.util.Map;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.SettingsPages", methodName="more", strictMatch=true)
public final class Patch_IsometricPages {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Map<?, ?> tabs, @Patch.Return List<Object> pages) {
        if (Main.compatible) IsometricSettingsPage.localizePage(pages);
    }
}
