package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** IsoDrawer submits these effects once before the rain veil in model mode. */
@Patch(className="vptw.AmbientDraw",methodName="draw")
public final class Patch_TrueWeatherDetails {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() {return TrueWeatherCompat.skipLate();}
}
