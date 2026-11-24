package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** VP's optional eye-ray dispatch bounds assume perspective; the shader still tests the real frustum. */
@Patch(className="viewpoint.render.GrassPass", methodName="viewing")
public final class Patch_GrassView {
    @Patch.OnExit
    public static void exit(@Patch.This Object pass) {
        if (IsometricBridge.rendering()) GameAccess.set("viewpoint.render.GrassPass", "viewing", pass, false);
    }
}
