package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.Hooks", methodName="gameLoaded", strictMatch=true)
public final class Patch_ClientSessionLoaded {
    @Patch.OnExit
    public static void exit() { if (Main.compatible) IsometricBridge.newSession(); }
}
