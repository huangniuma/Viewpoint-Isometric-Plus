package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.IsoGridSquare", methodName="IsOnScreen", strictMatch=true)
public final class Patch_RotationSquareBounds {
    @Patch.OnExit
    public static void exit(@Patch.This Object square, @Patch.Argument(0) boolean spare,
                            @Patch.Return(readOnly=false) boolean result) {
        result = RotationBounds.square(square, result);
    }
}
