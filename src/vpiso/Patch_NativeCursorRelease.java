package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.characters.component.CharacterInputComponent", methodName="isBuildButtonReleased", strictMatch=true)
public final class Patch_NativeCursorRelease {
    @Patch.OnExit
    public static void exit(@Patch.This Object component, @Patch.Return(readOnly=false) boolean released) {
        released = NativeCursorInput.released(component, released);
    }
}
