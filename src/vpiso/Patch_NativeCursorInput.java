package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.characters.component.CharacterInputComponent", methodName="updateToggleButtons", strictMatch=true)
public final class Patch_NativeCursorInput {
    @Patch.OnExit
    public static void exit(@Patch.This Object component) { NativeCursorInput.sample(component); }
}
