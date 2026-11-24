package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.FrameContext;
import viewpoint.upscale.UpscaleFrame;

/** Vendor dispatch must describe the same remote camera as the rendered depth. */
@Patch(className="viewpoint.render.VendorInputs", methodName="describe")
public final class Patch_UpscaleCamera {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) FrameContext context,
                            @Patch.Return(readOnly=false) UpscaleFrame frame) {
        if (!Main.compatible || !IsometricBridge.modelRendering()
                || IsometricBridge.renderFrame().scene != context.scene) return;
        frame = new UpscaleFrame(frame.jitterX(), frame.jitterY(), frame.motionScaleX(), frame.motionScaleY(),
                frame.reset(), frame.frameMs(), IsometricBridge.NEAR, IsometricBridge.FAR, frame.fovY(),
                frame.metresPerUnit(), frame.sharpness(), frame.matrices(), frame.camera());
    }

    private Patch_UpscaleCamera() {}
}
