package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** Reuse VP's existing adoption budget, urgent path and multiplayer maximum-wait rules. */
@Patch(className="viewpoint.game.ChunkQueue", methodName="entered", strictMatch=true)
public final class Patch_ShadowPreloadLoads {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(value=0, readOnly=false) boolean view) {
        if (!view && ShadowPreload.loadingAllowed()) view = true;
    }
}
