package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** PZO also compiles native world helpers directly, outside ShaderUnit. */
@Patch(className="pzopt.Shaders",methodName="compile",strictMatch=true)
public final class Patch_NativeGroundPZOProgram {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) String path,@Patch.Argument(1) int type,
                             @Patch.Argument(value=2,readOnly=false) String source) {
        if(Main.compatible && type==org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER)
            source=NativeGroundShaders.rewrite(path.endsWith(".frag")?path:path+".frag",source);
    }
}
