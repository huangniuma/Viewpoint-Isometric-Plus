package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import org.lwjgl.opengl.GL42;

/** Shared stream ranges can contain GPU-written palettes before their next API update. */
@Patch(className="viewpoint.render.FrameStream", methodName="reserve", strictMatch=true)
public final class Patch_FrameStreamUpdates {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) long bytes, @Patch.Argument(1) int alignment) {
        if (Main.compatible) GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
    }
}
