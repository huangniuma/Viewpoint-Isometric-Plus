package vpiso;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.ChunkMeshData;

/** Let the original bucket allocator submit baked physical depth without a colour bake. */
@Patch(className="viewpoint.render.Meshes", methodName="bucket", strictMatch=true)
public final class Patch_ModelSolarBucket {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(value=0, readOnly=false) ChunkMeshData mesh,
                             @Patch.Argument(1) int index,
                             @Patch.Argument(value=2, readOnly=false) int page) {
        if (ModelSolarGeometry.physicalPage(mesh, index, page)) {
            mesh = ModelSolarGeometry.depthPage();
            page = 0;
        }
    }

    private Patch_ModelSolarBucket() {}
}
