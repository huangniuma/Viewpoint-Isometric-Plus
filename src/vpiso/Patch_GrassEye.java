package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Vector3f;

/** Grass density/ground sampling follows the player, while vertices use the actual camera. */
@Patch(className="viewpoint.render.GrassPass", methodName="grownFor")
public final class Patch_GrassEye {
    @Patch.OnExit
    public static void exit(@Patch.Return Vector3f eye) {
        if (IsometricBridge.rendering()) eye.set(0f, 1.5f, 0f);
    }
}
