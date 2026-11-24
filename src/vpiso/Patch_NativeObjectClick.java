package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.ui.UIManager", methodName="checkPicked", strictMatch=true)
public final class Patch_NativeObjectClick {
    @Patch.OnEnter
    public static void enter() { NativeObjectClick.prepare(); }
}
