package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.vispoly.VisibilityPolygon2", methodName="renderMain", strictMatch=true)
public final class Patch_RotationVisionQueue {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) int player) { RotationLayers.beforeVision(player); }
}
