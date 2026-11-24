package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.ShaderPack;

@Patch(className="viewpoint.platform.Shaders", methodName="read")
public final class Patch_ShaderRead {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) String path, @Patch.Argument(1) Object active,
                            @Patch.Argument(2) Object fromPack, @Patch.Return(readOnly=false) String source) {
        if (Main.compatible && active instanceof ShaderPack pack
                && IsometricBridge.blockedReason(pack.id, false, 0, false, false) == null) {
            source = IsometricShaders.rewrite(path, source);
        }
    }
}
