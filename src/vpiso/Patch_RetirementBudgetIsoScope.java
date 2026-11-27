package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
@Patch(className="vpiso.IsoDrawer", methodName="render", strictMatch=true)
public final class Patch_RetirementBudgetIsoScope {
    @Patch.OnEnter
    public static void enter(@Patch.This Object drawer,@Patch.Local("retirePrevious") Frame previous) {previous=RetirementBudget.enter(drawer);}
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("retirePrevious") Frame previous) {RetirementBudget.leave(previous);}
}
