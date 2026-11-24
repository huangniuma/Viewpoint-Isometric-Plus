package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.Lua.LuaManager$GlobalObject", methodName="isoToScreenX", strictMatch=true)
public final class Patch_RotationWorldToScreenX {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) int player, @Patch.Argument(1) float x,
            @Patch.Argument(2) float y, @Patch.Argument(3) float z, @Patch.Return(readOnly=false) float result) {
        result = RotationInteraction.worldToScreen(player, x, y, z, true, result);
    }
}
