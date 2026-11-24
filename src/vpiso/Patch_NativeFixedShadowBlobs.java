package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Original fixed blobs must not stack with VP's dynamic caster shadows. */
@Patch(className="zombie.iso.fboRenderChunk.FBORenderShadows",methodName="renderMain",strictMatch=true)
public final class Patch_NativeFixedShadowBlobs {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This Object renderer,@Patch.Argument(0) int player) {
        return NativeSpriteShadows.suppressBlobs(renderer);
    }
}
