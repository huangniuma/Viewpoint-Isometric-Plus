package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoObject;
import zombie.iso.IsoDirections;

/** Walls stay foreground; their floor contribution cannot be reused as ground. */
@Patch(className="zombie.iso.IsoObject", methodName="renderWallTileOnly", strictMatch=true)
public final class Patch_NativeGroundWall {
    @Patch.OnEnter
    public static void enter(@Patch.This IsoObject object, @Patch.Argument(0) IsoDirections direction,
                             @Patch.Argument(6) Object shaper,
                             @Patch.Local("vpIsoWallReceiver") boolean active) {
        active=IsometricBridge.nativeGroundActive() && !IsometricBridge.capturing() && !IsometricBridge.rendering();
        if(active) NativeGround.queueWall(object,direction,shaper);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoWallReceiver") boolean active) {
        if(active) NativeGround.queueFloor(false);
    }
}
