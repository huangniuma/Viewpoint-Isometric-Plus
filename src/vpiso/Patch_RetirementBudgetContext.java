package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.core.opengl.RenderThread", methodName="releaseContextReentrantInternal", strictMatch=true)
public final class Patch_RetirementBudgetContext {
    @Patch.OnEnter
    public static void enter() {RetirementBudget.contextEnding();}
}
