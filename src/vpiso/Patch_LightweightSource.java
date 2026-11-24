package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.WorldMesher", methodName="drawnElsewhere", strictMatch=true)
public final class Patch_LightweightSource {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(1) Object object) { LightweightShadows.source(object); }
}
