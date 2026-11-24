package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.Cook$Cooker", methodName="models")
public final class Patch_PlantModelBounds {
    @Patch.OnExit
    public static void exit(@Patch.This Object cooker,@Patch.Argument(0) java.util.List<?> ops) {
        if(Main.compatible)PlantModelBounds.cooked(cooker,ops);
    }
}
