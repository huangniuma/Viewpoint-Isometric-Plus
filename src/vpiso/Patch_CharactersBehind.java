package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.models.Characters", methodName="behind")
public final class Patch_CharactersBehind {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) boolean result) {
        if (IsometricBridge.capturing()) result = false;
    }
}
