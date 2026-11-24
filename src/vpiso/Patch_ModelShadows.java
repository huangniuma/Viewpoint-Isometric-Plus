package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.GlDebug;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** Changes only this addon's queued 3D frame, without editing shared VP Pipeline settings. */
@Patch(className="viewpoint.render.ShadowPass", methodName="draw")
public final class Patch_ModelShadows {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This Object shadows, @Patch.Argument(0) FrameContext context) {
        if (!IsometricBridge.modelRendering()) {
            if (Main.compatible) NativeShadowMaps.prepareBasis(shadows, context.scene);
            return false;
        }
        draw(shadows, context, IsometricBridge.renderEffects());
        return true;
    }

    public static void draw(Object shadows, FrameContext context, IsometricEffects effects) {
        SceneData previousCasters = LightweightShadows.enter(context.scene,effects);
        try {
            NativeShadowMaps.prepareBasis(shadows, context.scene);
            if (context.sun && context.pipeline.shadows && effects.needsShadows()) {
                GlDebug.push("VPISO model sun shadows");
                try {
                    context.scene.shadowReach = Math.max(64f, effects.distance());
                    GameAccess.call("viewpoint.render.ShadowPass", "resize", shadows,
                            new Class<?>[] { int.class }, effects.resolution());
                    int layers = effects.distance() <= 22f ? 1 : effects.distance() <= 64f ? 2 : 3;
                    GameAccess.call("viewpoint.render.ShadowPass", "drawCascades", shadows,
                            new Class<?>[] { SceneData.class, int.class }, context.scene, layers);
                } finally { GlDebug.pop(); }
            }
            // Local lamps, vehicle lamps, flashlight and rain retain VP's normal model routines.
            GameAccess.call("viewpoint.render.ShadowPass", "drawLights", shadows,
                    new Class<?>[] { FrameContext.class }, context);
        } finally { LightweightShadows.restore(previousCasters); }
    }

    private Patch_ModelShadows() {}
}
