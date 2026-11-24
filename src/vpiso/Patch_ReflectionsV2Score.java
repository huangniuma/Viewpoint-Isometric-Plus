package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Optional reflection overlay follows the current native source visibility. */
@Patch(className="prv2.Mirrors", methodName="score", strictMatch=true)
public final class Patch_ReflectionsV2Score {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) Object context, @Patch.Argument(1) Object surface,
            @Patch.Argument(2) float reach, @Patch.Argument(3) Object corners, @Patch.Argument(4) Object normal) {
        return ReflectionVisibility.alpha(context, surface) <= 0f;
    }
}
