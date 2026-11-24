package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.Cook$Cooker", methodName="page", strictMatch=true)
public final class Patch_LightweightPage {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object batch, @Patch.Argument(1) int page) {
        LightweightShadows.page(batch, page);
    }
}
