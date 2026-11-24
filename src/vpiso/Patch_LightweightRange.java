package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.Cook$Cooker", methodName="claim", strictMatch=true)
public final class Patch_LightweightRange {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(1) int first, @Patch.Argument(2) int end) {
        LightweightShadows.vertices(first, end);
    }
}
