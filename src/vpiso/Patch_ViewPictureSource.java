package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Viewpoint's fade keeps the visible world, never the floor-only auxiliary attachment. */
@Patch(className="viewpoint.render.KeptPicture", methodName="keep", strictMatch=true)
public final class Patch_ViewPictureSource {
    @Patch.OnEnter
    public static void enter(@Patch.Local("vpIsoPictureSource") long saved) {
        saved = NativePictureSource.begin(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoPictureSource") long saved) { NativePictureSource.end(saved); }
}
