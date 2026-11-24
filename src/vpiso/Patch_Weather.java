package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.Hooks", methodName="skipWeatherFx", strictMatch=true)
public final class Patch_Weather {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) boolean result) {
        if (IsometricBridge.suppressWeather()) result = true;
    }
}
