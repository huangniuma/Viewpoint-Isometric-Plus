package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.models.Characters", methodName="inRange")
public final class Patch_CharacterVisibility {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object moving, @Patch.Return(readOnly=false) boolean result) {
        if (result && IsometricBridge.characterHidden(moving)) result = false;
    }
}
