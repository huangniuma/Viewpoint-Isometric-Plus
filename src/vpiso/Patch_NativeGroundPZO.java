package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** PZO's actual ShaderUnit override calls this after all native source replacements. */
@Patch(className="pzopt.EntityShadow", methodName="devFinal", strictMatch=true)
public final class Patch_NativeGroundPZO {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) String path, @Patch.Return(readOnly=false) String source) {
        if (Main.compatible || NativeGroundShaders.bootstrap(path)) source = NativeGroundShaders.rewrite(path, source);
    }
}
