package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.IsoUpkeep", methodName="frame", strictMatch=true)
public final class Patch_ShadowPreloadScope {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(1) Object character,
                             @Patch.Local("vpIsoPreloadCamera") ShadowPreload.Camera previous) {
        previous = ShadowPreload.begin(character);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoPreloadCamera") ShadowPreload.Camera previous) {
        ShadowPreload.restore(previous);
    }
}
