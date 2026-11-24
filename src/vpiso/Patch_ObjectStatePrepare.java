package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.SceneData;

@Patch(className="viewpoint.render.Meshes", methodName="prepare", strictMatch=true)
public final class Patch_ObjectStatePrepare {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) SceneData scene) { NativeObjectState.prepared(scene); }
}
