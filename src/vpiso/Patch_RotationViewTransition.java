package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.camera.MovePose", methodName="turn", strictMatch=true)
public final class Patch_RotationViewTransition {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object direction) {
        RotationCamera.transitionStart((float[]) direction);
    }
}
