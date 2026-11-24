package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.Cook$Cooker", methodName="run", strictMatch=true)
public final class Patch_LightweightOperation {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object op) { LightweightShadows.operation(op); }
}
