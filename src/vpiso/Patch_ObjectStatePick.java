package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoObject;

@Patch(className="viewpoint.interact.MouseTargets", methodName="object", strictMatch=true)
public final class Patch_ObjectStatePick {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly=false) IsoObject result) { result = (IsoObject) NativeObjectState.picked(result); }
}
