package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** Native fading solids use the same layered appearance in both colour passes. */
@Patch(className="viewpoint.render.ModelPass",methodName="<init>",strictMatch=true)
public final class Patch_SmoothModelSamplers {
    @Patch.OnExit
    public static void exit(@Patch.This Object pass,@Patch.Argument(0) Object targets) {NativeObjectState.smoothModelSamplers(pass);}
}
