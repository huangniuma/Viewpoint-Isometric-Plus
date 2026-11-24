package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** PZO records chunk textures on worker threads outside FBORenderCell's thread-local scope. */
@Patch(className="pzopt.BakeRecord", methodName="record", strictMatch=true)
public final class Patch_NativeSpriteShadowPZOBake {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) int unit, @Patch.Local("vpIsoSpriteShadows") int previous) {
        previous = NativeSpriteShadows.enter();
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoSpriteShadows") int previous) { NativeSpriteShadows.leave(previous); }
}
