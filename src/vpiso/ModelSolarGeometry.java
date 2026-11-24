package vpiso;

import viewpoint.render.ChunkMeshData;
import viewpoint.render.SceneData;
import zombie.core.textures.TextureID;

/** Physical baked slabs cast depth even when their camera-only artwork is not resident. */
public final class ModelSolarGeometry {
    private static final ThreadLocal<SceneData> ACTIVE = new ThreadLocal<>();

    public static SceneData enter(SceneData scene, byte mask) {
        SceneData previous = ACTIVE.get();
        if (IsometricBridge.renderFrame() != null && IsometricBridge.renderFrame().scene == scene
                && (mask & (SceneData.CASTS_SHADOW | SceneData.CASTS_MID | SceneData.CASTS_FAR)) != 0)
            ACTIVE.set(scene);
        else ACTIVE.remove();
        return previous;
    }

    public static void restore(SceneData previous) {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }

    public static boolean physicalPage(ChunkMeshData mesh, int index, int page) {
        SceneData scene = ACTIVE.get();
        if (scene == null || index < 0 || index >= scene.meshCount || scene.meshes[index] != mesh)
            return false;
        return page == mesh.floorPage || mesh.ceilingGroups != null && mesh.ceilingGroups[page] >= 0;
    }

    /** Bucket selection metadata only: actual vertices, runs and offsets stay in the scene. */
    public static ChunkMeshData depthPage() { return DepthPage.MESH; }

    private static final class DepthPage {
        private static final ChunkMeshData MESH = new ChunkMeshData(
                new TextureID[] { null }, new int[] { 0 }, new int[] { 0 }, new float[0], 0);
    }

    private ModelSolarGeometry() {}
}
