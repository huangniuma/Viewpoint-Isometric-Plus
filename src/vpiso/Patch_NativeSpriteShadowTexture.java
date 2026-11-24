package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@Patch(className="zombie.iso.sprite.IsoSprite", methodName="getTextureForFrame", strictMatch=true)
public final class Patch_NativeSpriteShadowTexture {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) int frame, @Patch.Argument(1) Object direction, @Patch.Argument(2) boolean snow,
                            @Advice.Return(readOnly=false, typing=Assigner.Typing.DYNAMIC) Object result) {
        result = NativeSpriteShadows.texture(result);
    }
}
