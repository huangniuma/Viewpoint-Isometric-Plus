package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.FrameContext;

/** Model-mode inventory highlights use the original game's colour and options. */
@Patch(className="viewpoint.render.TargetOutline", methodName="mask", strictMatch=true)
public final class Patch_NativeContainerMask {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This Object self, @Patch.Argument(0) FrameContext frame,
            @Patch.Argument(1) Object models) {
        if (!IsometricBridge.modelRendering() || !NativeContainerHighlight.ownsMask()) return false;
        GameAccess.set("viewpoint.render.TargetOutline", "masked", self, false);
        return true;
    }
}
