package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Optional reflection overlay follows the current native source visibility. */
@Patch(className="prv2.Mirrors$Gpu", methodName="composite", strictMatch=true)
public final class Patch_ReflectionsV2Composite {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) Object context, @Patch.Argument(2) Object surface,
            @Patch.Argument(3) boolean panes, @Patch.Argument(value=4, readOnly=false) float opacity) {
        float alpha = ReflectionVisibility.alpha(context, surface);
        // The pane branch forwards its opacity to the separately patched pane body.
        if (!panes) opacity *= alpha;
        return alpha <= 0f;
    }
}
