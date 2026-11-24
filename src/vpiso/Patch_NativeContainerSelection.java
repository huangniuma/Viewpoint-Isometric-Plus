package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;

@Patch(className="viewpoint.interact.LootMenu", methodName="snapshot", strictMatch=true)
public final class Patch_NativeContainerSelection {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Frame frame, @Patch.Argument(1) Object character) {
        NativeContainerHighlight.snapshot(frame);
    }
}
