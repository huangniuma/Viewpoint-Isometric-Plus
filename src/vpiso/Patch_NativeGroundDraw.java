package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** Reasserts the auxiliary output after PZO's draw-buffer setup, immediately before native sprites. */
@Patch(className="zombie.core.SpriteRenderer$RingBuffer", methodName="drawElements", strictMatch=true)
public final class Patch_NativeGroundDraw {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) int start) { NativeGround.beforeDraw(); }
}
