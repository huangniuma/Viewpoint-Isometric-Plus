package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
/** Cache/session switches request collection on the owning GL context, never on the game thread. */
@Patch(className="viewpoint.FP", methodName="resetCaches", strictMatch=true)
public final class Patch_RetirementBudgetReset {
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit() {RetirementBudget.cachesReset();}
}
