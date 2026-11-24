package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.textures.TextureFBO;

/** Native thumbnail rendering backs up and restores the same visible color attachment. */
@Patch(className="zombie.savefile.SavefileThumbnail$CopyTextureDrawer", methodName="blitFramebuffer", strictMatch=true)
public final class Patch_SavePictureSource {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) TextureFBO source,
                             @Patch.Local("vpIsoPictureSource") long saved) {
        saved = NativePictureSource.begin(source.getBufferId());
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoPictureSource") long saved) { NativePictureSource.end(saved); }
}
