package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.viewCone.ChunkRenderShader", methodName="startRenderThread", strictMatch=true)
public final class Patch_NativeGroundComposite {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object draw) { NativeGround.chunkShader(draw); }
}
