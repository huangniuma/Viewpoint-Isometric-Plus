package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoObject;

@Patch(className="zombie.iso.objects.IsoTree",methodName="render",strictMatch=true)
public final class Patch_NativeSourceTreeCapture {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(6) zombie.core.opengl.Shader shader,
            @Patch.Local("vpIsoSourceBatch") Object batch,@Patch.Local("vpIsoSourceStart") int start) {
        if(IsometricBridge.nativeGroundActive() && !IsometricBridge.capturing() && !IsometricBridge.rendering()) {
            batch=NativeTreeSources.batch();start=NativeTreeSources.size(batch);
        }
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.This IsoObject tree,@Patch.Local("vpIsoSourceBatch") Object batch,@Patch.Local("vpIsoSourceStart") int start) {
        if(batch!=null)NativeTreeSources.captured(batch,start,tree);
    }
}
