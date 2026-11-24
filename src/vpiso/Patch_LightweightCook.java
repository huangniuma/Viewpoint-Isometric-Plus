package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.Cook", methodName="mesh", strictMatch=true)
public final class Patch_LightweightCook {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object recipe, @Patch.Local("vpIsoLightCook") LightweightShadows.Builder previous) {
        previous = LightweightShadows.begin(recipe);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Return viewpoint.render.ChunkMeshData mesh,
                            @Patch.Local("vpIsoLightCook") LightweightShadows.Builder previous) {
        Object cooker = ((ThreadLocal<?>)GameAccess.field("viewpoint.world.Cook", "COOKER", null)).get();
        LightweightShadows.finish(cooker, mesh, previous);
        NativeReceiverCull.cooked(mesh);
    }
}
