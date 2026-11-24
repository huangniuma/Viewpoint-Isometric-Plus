package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.core.opengl.VBORenderer",methodName="renderRun",strictMatch=true)
public final class Patch_NativeGroundVBODraw {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object run,@Patch.Local("vpIsoTreeProgram") int previous) {
        NativeGround.beforeDraw();
        previous=NativeTreeLighting.beforeDraw(run);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoTreeProgram") int previous) {NativeTreeLighting.afterDraw(previous);}
}
