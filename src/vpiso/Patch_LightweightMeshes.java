package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.render.Meshes", methodName="collect", strictMatch=true)
public final class Patch_LightweightMeshes {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) viewpoint.render.SceneData scene, @Patch.Argument(1) int index) {
        return NativeReceiverCull.skip(scene, index) || LightweightShadows.collect(scene, index);
    }
}
