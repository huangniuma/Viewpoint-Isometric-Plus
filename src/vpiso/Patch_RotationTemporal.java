package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Matrix4f;
import viewpoint.render.FrameContext;

/** Native water/visibility use the queued plain camera, including at raster silhouettes. */
@Patch(className="viewpoint.render.TemporalPass", methodName="begin")
public final class Patch_RotationTemporal {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) FrameContext context) {
        if (!Main.compatible || !IsometricBridge.modelRendering()
                || IsometricBridge.renderFrame().scene != context.scene || RotationLayers.current() == null
                || (Boolean) GameAccess.field("viewpoint.render.FrameContext", "upscaled", context)) return;
        // Keep the original history, motion and noise lifecycle; align the current world
        // raster before FrameUniforms uploads it and native water/vision consume its depth.
        GameAccess.set("viewpoint.render.FrameContext", "jitterX", context, 0f);
        GameAccess.set("viewpoint.render.FrameContext", "jitterY", context, 0f);
        context.jitteredProjection.set(context.scene.projection);
        context.viewProjection.set((Matrix4f) GameAccess.field("viewpoint.render.FrameContext", "plainViewProjection", context));
        context.invViewProjection.set((Matrix4f) GameAccess.field("viewpoint.render.FrameContext", "invPlainViewProjection", context));
    }

    private Patch_RotationTemporal() {}
}
