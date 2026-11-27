package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="viewpoint.render.Retirement", methodName="freePassed", strictMatch=true)
public final class Patch_RetirementBudgetCollect {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This Object owner) { return RetirementBudget.collect(owner); }
}
