package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;

/** Both native-shadow and model captures use the original character alpha. */
@Patch(className="viewpoint.models.Characters", methodName="flag", strictMatch=true)
public final class Patch_CharacterFade {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Frame frame, @Patch.Argument(2) Object character,
                            @Patch.Argument(4) int first) {
        IsometricBridge.characterFade(frame, character, first);
    }
}
