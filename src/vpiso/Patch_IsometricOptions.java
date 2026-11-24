package vpiso;

import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.SettingsPages", methodName="section", strictMatch=true)
public final class Patch_IsometricOptions {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) String name,
                             @Patch.Argument(value=1, readOnly=false) List<?> pages) {
        if (Main.compatible) pages = IsometricSettingsPage.section(name, pages);
    }
}
