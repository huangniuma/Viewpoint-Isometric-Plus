package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.render.Meshes", methodName="collectPlants", strictMatch=true)
public final class Patch_LightweightPlants {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) viewpoint.render.SceneData scene, @Patch.Argument(1) byte mask, @Patch.Argument(2) int layer) {
        return LightweightShadows.plants(scene, mask, layer);
    }
}
