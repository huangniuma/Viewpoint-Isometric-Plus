package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** False selects the installed Drawer's existing native visibility blur fallback. */
@Patch(className="pzopt.Darkness", methodName="memoryPass", strictMatch=false)
public final class Patch_ModelVisionMemory {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() { return Main.compatible && NativeVision.modelVision(); }
}
