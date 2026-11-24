package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Native isometric capture sees cars all around the player, independent of VP's camera cone. */
@Patch(className="viewpoint.light.Headlights",methodName="seen",strictMatch=true)
public final class Patch_NativeHeadlights {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) float x,@Patch.Argument(1) float y,@Patch.Argument(2) float reach,
                            @Patch.Return(readOnly=false) boolean result) {
        if(IsometricBridge.nativeShadowCapture())result=true;
    }
}
