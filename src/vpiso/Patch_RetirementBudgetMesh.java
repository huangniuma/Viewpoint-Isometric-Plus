package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="viewpoint.render.MeshArena", methodName="allocate", strictMatch=true)
public final class Patch_RetirementBudgetMesh {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) int vertices) {RetirementBudget.beforeMesh(vertices);}
}
