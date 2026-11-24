package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** Use PZO's existing native tree fallback, whose source carries foreground coverage. */
@Patch(className="pzopt.Sway",methodName="drawTrees",strictMatch=true)
public final class Patch_NativeGroundPZOTrees {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(2) int count) { return NativeGround.active(); }
}
