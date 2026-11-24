package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.core.opengl.ShaderUnit", methodName="loadShaderFile", strictMatch=true)
public final class Patch_NativeGroundSource {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) String path, @Patch.Return(readOnly=false) String source) {
        if ((Main.compatible && !NativeGroundShaders.PZO_FINAL) || NativeGroundShaders.bootstrap(path)) source = NativeGroundShaders.rewrite(path, source);
    }
}
