package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.Recipe", methodName="model")
public final class Patch_TreeRecipeModel {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(1) viewpoint.packs.PackBind bind,
                             @Patch.Argument(value=7, readOnly=false) int flags) {
        if (Main.compatible) flags=TreeModelScope.flags(flags,bind);
    }
    @Patch.OnExit
    public static void exit(@Patch.Argument(1) viewpoint.packs.PackBind bind,
                            @Patch.Return Object op) {
        if(Main.compatible)NearVegetationCompat.source(bind,op);
    }
}
