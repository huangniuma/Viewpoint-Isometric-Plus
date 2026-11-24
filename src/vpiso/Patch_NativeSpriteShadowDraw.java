package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.textures.Texture;

/** The real queue also receives renderi/renderflipped and PZO recorder draws. */
@Patch(className="zombie.core.sprite.GenericSpriteRenderState", methodName="render", strictMatch=false)
public final class Patch_NativeSpriteShadowDraw {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(value=0, readOnly=false) Texture texture) {
        texture = (Texture) NativeSpriteShadows.submitted(texture);
    }
}
