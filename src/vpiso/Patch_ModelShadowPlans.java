package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.SceneData;

/** Camera cutaway plans must not remove physical solar or local-light occluders. */
@Patch(className="viewpoint.render.Meshes", methodName="draw")
public final class Patch_ModelShadowPlans {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) SceneData scene, @Patch.Argument(2) byte mask,
                             @Patch.Argument(value=4, readOnly=false) boolean planned,
                             @Patch.Argument(5) boolean plants,
                             @Patch.Local("vpIsoSolarGeometry") SceneData previous,
                             @Patch.Local("vpIsoLightMask") SceneData previousLight) {
        previousLight = LightweightShadows.forMask(mask);
        previous = ModelSolarGeometry.enter(scene, mask);
        if (IsometricBridge.modelRendering() && IsometricBridge.renderFrame().scene == scene
                && (mask & (SceneData.CASTS_SHADOW | SceneData.CASTS_MID | SceneData.CASTS_FAR)) != 0)
            planned = false;
    }

    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Argument(5) boolean plants,
                            @Patch.Local("vpIsoSolarGeometry") SceneData previous,
                            @Patch.Local("vpIsoLightMask") SceneData previousLight) {
        ModelSolarGeometry.restore(previous);
        LightweightShadows.restore(previousLight);
    }

    private Patch_ModelShadowPlans() {}
}
