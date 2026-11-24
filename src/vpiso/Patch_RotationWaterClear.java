package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.iso.IsoWater$RenderData", methodName="clear", strictMatch=true)
public final class Patch_RotationWaterClear {
    @Patch.OnEnter
    public static void enter(@Patch.This Object data) { RotationWater.clear(data); }
}
