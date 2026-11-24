package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.FrameContext;

/** Select the actual producer again after switching between native and model frames. */
@Patch(className="viewpoint.render.FrameGenPass",methodName="capture",strictMatch=true)
public final class Patch_FrameGenerationCapture {
    @Patch.OnEnter
    public static void enter(@Patch.This Object pass,@Patch.Argument(0) FrameContext context) {
        if(Main.compatible) IsometricFrameGeneration.producer(pass,IsometricBridge.modelRendering()?1:0);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.This Object pass,@Patch.Thrown(readOnly=false) Throwable error) {
        if(error!=null && Main.compatible && IsometricBridge.modelRendering()) {
            IsometricFrameGeneration.failed(pass,error);
            viewpoint.render.WorldRenderer.restoreOutput();
            error=null;
        }
        if(error==null && Main.compatible)IsometricFrameGeneration.captured(pass);
    }
}
