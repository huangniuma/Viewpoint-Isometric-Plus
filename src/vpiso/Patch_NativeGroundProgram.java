package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.core.ShaderHelper", methodName="glUseProgramObjectARB", strictMatch=true)
public final class Patch_NativeGroundProgram {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) int program) { NativeGround.program(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM)); }
}
