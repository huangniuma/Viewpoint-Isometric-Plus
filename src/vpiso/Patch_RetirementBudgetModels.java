package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="viewpoint.render.PackModels", methodName="upload", strictMatch=true)
public final class Patch_RetirementBudgetModels {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) long now) {RetirementBudget.beforeModels();}
}
