package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderCell", methodName="renderInternal", strictMatch=true)
public final class Patch_NativeSpriteShadowScope {
    @Patch.OnEnter
    public static void enter(@Patch.Local("vpIsoSpriteShadows") int previous) { previous = NativeSpriteShadows.enter(); }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoSpriteShadows") int previous) { NativeSpriteShadows.leave(previous); }
}
