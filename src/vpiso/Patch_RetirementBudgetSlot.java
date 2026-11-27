package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="viewpoint.render.MeshArena", methodName="takeSlot", strictMatch=true)
public final class Patch_RetirementBudgetSlot {
    @Patch.OnEnter
    public static void enter() {RetirementBudget.beforeSlot();}
}
