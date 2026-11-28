package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.GlProgram;
import viewpoint.render.SceneData;

/** Only fading far meshes join the existing forward draw; flags restore at its boundary. */
@Patch(className="viewpoint.render.Meshes",methodName="drawPlanned",strictMatch=true)
public final class Patch_SmoothMeshFade {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) SceneData scene,@Patch.Argument(1) GlProgram program,
            @Patch.Argument(2) byte flags,@Patch.Argument(3) boolean plants,
            @Patch.Local("vpIsoSmoothMeshes") Object scope) {
        scope=NativeObjectState.beginMeshFade(scene,flags);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoSmoothMeshes") Object scope) {
        NativeObjectState.endMeshFade(scope);
    }
}
