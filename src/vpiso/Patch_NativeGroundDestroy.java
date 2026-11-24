package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.core.textures.TextureFBO",methodName="destroy",strictMatch=true)
public final class Patch_NativeGroundDestroy {
    @Patch.OnEnter
    public static void enter(@Patch.This Object framebuffer) { NativeGround.destroy(framebuffer); }
}
