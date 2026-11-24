package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.GlProgram;

@Patch(className="viewpoint.platform.GlProgram", methodName="use", strictMatch=true)
public final class Patch_ProgramUse {
    @Patch.OnExit
    public static void exit(@Patch.This GlProgram program) { IsometricShaders.onUse(program); }
}
