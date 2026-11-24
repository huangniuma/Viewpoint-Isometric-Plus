package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.render.PackDraws", methodName="collected", strictMatch=true)
public final class Patch_LightweightPackCache {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) viewpoint.render.SceneData scene,
        @net.bytebuddy.asm.Advice.Return(readOnly=false, typing=net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC) Object result) {
        if (LightweightShadows.active(scene)) result = null;
    }
}
