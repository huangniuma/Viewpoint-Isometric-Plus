package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.FrameContext;

/** Existing glass material selection is extended only inside our owned model frame. */
@Patch(className="viewpoint.render.ModelPass",methodName="glass",strictMatch=true)
public final class Patch_SmoothModelFade {
    @Patch.OnEnter
    public static void enter(@Patch.This Object pass,@Patch.Argument(0) FrameContext context,
            @Patch.Argument(1) Object shadows,@Patch.Argument(2) Object water,
            @Patch.Local("vpIsoSmoothModels") Object scope) {
        scope=NativeObjectState.beginModelFade(pass);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoSmoothModels") Object scope) {
        NativeObjectState.endModelFade(scope);
    }
}
