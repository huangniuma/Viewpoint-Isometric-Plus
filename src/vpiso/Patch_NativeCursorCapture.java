package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.LootLua", methodName="viewOn", strictMatch=true)
public final class Patch_NativeCursorCapture {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) boolean result) {
        if (NativeCursorPreview.capturing()) result = true;
    }
}
