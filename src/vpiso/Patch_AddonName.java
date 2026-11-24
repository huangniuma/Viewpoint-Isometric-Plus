package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.addons.Descriptor;

@Patch(className="viewpoint.addons.Descriptor", methodName="name", strictMatch=true)
public final class Patch_AddonName {
    @Patch.OnExit
    public static void exit(@Patch.This Descriptor descriptor, @Patch.Return(readOnly=false) String name) {
        if (ManagedAddon.ID.equals(descriptor.id())) name = IsometricText.localize(IsometricControls.PAGE);
    }
}
