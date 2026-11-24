package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.input.Look", methodName="onUpdateMouseCursor", strictMatch=true)
public final class Patch_RotationMouseCapture {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Local("vpIsoDrag") boolean handled) {
        handled = Main.compatible && RotationDrag.onMouseCursor();
        return handled;
    }
    @Patch.OnExit
    public static void exit(@Patch.Local("vpIsoDrag") boolean handled,
                            @Patch.Return(readOnly=false) boolean result) {
        if (handled) result = true;
    }
}
