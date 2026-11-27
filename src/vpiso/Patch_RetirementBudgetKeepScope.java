package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
/** VP's queued upload job carries the same captured FP frame number as both addon drawers. */
@Patch(className="viewpoint.IsoUpkeep$Job", methodName="render", strictMatch=true)
public final class Patch_RetirementBudgetKeepScope {
    @Patch.OnEnter
    public static void enter(@Patch.This Object job,@Patch.Local("retireKeepPrevious") Long previous) {previous=RetirementBudget.enterKeep(job);}
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("retireKeepPrevious") Long previous) {RetirementBudget.leaveKeep(previous);}
}
