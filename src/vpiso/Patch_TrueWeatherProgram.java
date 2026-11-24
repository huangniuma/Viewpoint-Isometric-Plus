package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="vptw.AmbientDraw",methodName="program",strictMatch=true)
public final class Patch_TrueWeatherProgram {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(value=1,readOnly=false) String fragment) {
        fragment=TrueWeatherCompat.fragment(fragment);
    }
}
