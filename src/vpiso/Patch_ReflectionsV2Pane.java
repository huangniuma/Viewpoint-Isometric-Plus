package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Optional reflection overlay follows the current native source visibility. */
@Patch(className="prv2.Mirrors$Gpu", methodName="pane", strictMatch=true)
public final class Patch_ReflectionsV2Pane {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) Object context, @Patch.Argument(2) Object surface,
            @Patch.Argument(value=3, readOnly=false) float opacity, @Patch.Argument(4) boolean depth) {
        float alpha = ReflectionVisibility.alpha(context, surface);
        opacity *= alpha;
        return alpha <= 0f;
    }
}
