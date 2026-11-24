package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoObject;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderCell", methodName="renderFloor", strictMatch=true)
public final class Patch_NativeGroundFloor {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) IsoObject object, @Patch.Local("vpIsoGroundFloor") boolean active) {
        active=IsometricBridge.nativeGroundActive() && !IsometricBridge.capturing() && !IsometricBridge.rendering();
        if(active) NativeGround.queueFloor(object);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoGroundFloor") boolean active) { if(active) NativeGround.queueFloor(false); }
}
