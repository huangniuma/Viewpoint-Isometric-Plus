package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.vispoly.VisibilityPolygon2$Drawer", methodName="render", strictMatch=true)
public final class Patch_RotationVisionScope {
    @Patch.OnEnter
    public static void enter(@Patch.This Object drawer,
                             @Patch.Local("vpIsoVisionCamera") RotationCamera.Snapshot previous,
                             @Patch.Local("vpIsoModelVision") boolean previousModel) {
        previousModel = NativeVision.enterVision(drawer);
        previous = RotationLayers.enterVision(drawer);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoVisionCamera") RotationCamera.Snapshot previous,
                            @Patch.Local("vpIsoModelVision") boolean previousModel) {
        RotationLayers.end(previous);
        NativeVision.leaveVision(previousModel);
    }
}
