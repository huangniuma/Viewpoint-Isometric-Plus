package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="vptw.AmbientDraw",methodName="init",strictMatch=true)
public final class Patch_TrueWeatherUniforms {
    @Patch.OnExit
    public static void exit() {TrueWeatherCompat.uniforms();}
}
