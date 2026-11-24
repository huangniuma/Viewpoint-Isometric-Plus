package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.render.WorldRenderer", methodName="off")
public final class Patch_RenderEffects {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) int effect, @Patch.Return(readOnly=false) boolean result) {
        if (IsometricBridge.rendering() && effect == 4) result = true;
        if (IsometricBridge.modelRendering() && effect == 5 && !IsometricBridge.renderEffects().needsShadows()) result = true;
    }
}
