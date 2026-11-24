package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** PZO also records translucent/floor objects on its reusable worker lanes. */
@Patch(className="pzopt.TileRecord", methodName="record", strictMatch=false)
public final class Patch_NativeSpriteShadowPZOTiles {
    @Patch.OnEnter
    public static void enter(@Patch.Local("vpIsoSpriteShadows") int previous) { previous = NativeSpriteShadows.enter(); }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoSpriteShadows") int previous) { NativeSpriteShadows.leave(previous); }
}
