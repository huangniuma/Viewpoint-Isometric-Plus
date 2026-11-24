package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.PlantGather", methodName="plantPart")
public final class Patch_TreePartModel {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object object, @Patch.Local("vpIsoTreeScope") int previous) {
        previous=TreeModelScope.enter(object);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoTreeScope") int previous) { TreeModelScope.restore(previous); }
}
