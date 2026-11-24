package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.models.Models", methodName="own", strictMatch=true)
public final class Patch_LightweightRigid {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) viewpoint.core.Frame frame, @Patch.Local("vpIsoLightRigid") int first) {
        first = Main.compatible && IsometricBridge.capturing() ? frame.scene.models.count() : -1;
    }
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) viewpoint.core.Frame frame, @Patch.Argument(3) Object object,
                            @Patch.Argument(4) Object item, @Patch.Local("vpIsoLightRigid") int first) {
        LightweightShadows.objectModels(frame, object, item, first);
    }
}
