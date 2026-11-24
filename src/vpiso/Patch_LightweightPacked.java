package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.render.PackDraws", methodName="collect", strictMatch=true)
public final class Patch_LightweightPacked {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) viewpoint.render.SceneData scene, @Patch.Argument(1) int index,
                             @Patch.Local("vpIsoLightRuns") int[] previous) {
        if (LightweightShadows.active(scene)) {
            Object mesh = scene.meshes[index];
            previous = (int[])GameAccess.field("viewpoint.render.ChunkMeshData", "modelRuns", mesh);
            GameAccess.set("viewpoint.render.ChunkMeshData", "modelRuns", mesh, LightweightShadows.models(scene.meshes[index]));
        }
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Argument(0) viewpoint.render.SceneData scene, @Patch.Argument(1) int index,
                            @Patch.Local("vpIsoLightRuns") int[] previous) {
        if (LightweightShadows.active(scene)) GameAccess.set("viewpoint.render.ChunkMeshData", "modelRuns", scene.meshes[index], previous);
    }
}
