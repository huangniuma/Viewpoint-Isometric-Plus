package vpiso;

import java.util.Map;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.SettingsPages", methodName="body", strictMatch=true)
public final class Patch_IsometricPageBody {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) Object page, @Patch.Argument(2) Map<?, ?> settings,
                                @Patch.Argument(3) float width) {
        if (!Main.compatible || !IsometricSettingsPage.ours(page)) return false;
        IsometricSettingsPage.draw(settings, width);
        return true;
    }
}
