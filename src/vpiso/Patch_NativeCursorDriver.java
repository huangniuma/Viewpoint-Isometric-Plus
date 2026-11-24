package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;

/** Scene capture must not run VP's crosshair controller over a native mouse cursor. */
@Patch(className="viewpoint.building.BuildDriver", methodName="frame", strictMatch=true)
public final class Patch_NativeCursorDriver {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) Frame frame, @Patch.Argument(1) Object character) {
        return IsometricBridge.capturing();
    }
}
