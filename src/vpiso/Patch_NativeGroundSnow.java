package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** Keep original winter snow in the native floor contribution and storey mask. */
@Patch(className="zombie.iso.fboRenderChunk.FBORenderSnow", methodName="RenderSnow", strictMatch=true)
public final class Patch_NativeGroundSnow {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(1) int level, @Patch.Local("vpIsoGroundSnow") boolean active) {
        active=IsometricBridge.nativeGroundActive() && !IsometricBridge.capturing() && !IsometricBridge.rendering();
        if(active) NativeGround.queueFloor((float)level);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoGroundSnow") boolean active) {
        if(active) NativeGround.queueFloor(false);
    }
}
