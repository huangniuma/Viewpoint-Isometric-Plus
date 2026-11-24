package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.SceneData;

@Patch(className="viewpoint.render.PackDraws", methodName="collect", strictMatch=true)
public final class Patch_PackedCutaways {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) SceneData scene, @Patch.Argument(1) int index,
                                @Patch.Argument(2) long squares) {
        return IsometricVisibility.collectPacked(scene, index, squares);
    }
}
