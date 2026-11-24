package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.platform.Graphics", methodName="declarePack", strictMatch=true)
public final class Patch_GraphicsPackList {
    @Patch.OnEnter
    public static void enter() {
        if (Main.compatible) IsometricControls.exposeLibrary();
    }
}
