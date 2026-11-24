package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** VP's planned pass is camera colour; physical shadow passes use unplanned instances. */
@Patch(className="viewpoint.render.PackDraws", methodName="draw", strictMatch=true)
public final class Patch_PackedCutawayScope {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(4) boolean planned, @Patch.Local("vpIsoPackedColour") boolean previous) {
        previous = IsometricVisibility.enterPacked(planned);
    }

    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoPackedColour") boolean previous) {
        IsometricVisibility.leavePacked(previous);
    }
}
