package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.addons.Descriptor;

@Patch(className="viewpoint.addons.Descriptor", methodName="get", strictMatch=true)
public final class Patch_AddonDescription {
    @Patch.OnExit
    public static void exit(@Patch.This Descriptor descriptor, @Patch.Argument(0) String key,
                            @Patch.Return(readOnly=false) String value) {
        if (key.equals("description") && ManagedAddon.ID.equals(descriptor.id()))
            value = IsometricText.localize(ManagedAddon.DESCRIPTION);
    }
}
