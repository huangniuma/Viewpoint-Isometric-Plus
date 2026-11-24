package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

/** Retain each mirror object independently even when its reflection key is a sprite. */
@Patch(className="projectreflections.Scan", methodName="mirror", strictMatch=true)
public final class Patch_ReflectionsSource {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object source, @Patch.Argument(1) Object sprite,
            @Patch.Argument(2) int x, @Patch.Argument(3) int y, @Patch.Argument(4) int level,
            @Patch.Return Object surface) {
        ReflectionVisibility.remember(surface, source);
    }
}
