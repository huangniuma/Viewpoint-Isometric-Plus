package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.world.Cook$Cooker",methodName="plant",strictMatch=true)
public final class Patch_NativeSourcePlant {
    @Patch.OnEnter
    public static void enter(@Patch.This Object cooker,@Patch.Local("vpIsoPlantSourceStart") int start) {
        start=((Number)GameAccess.field("viewpoint.world.Cook$Cooker","plantFloats",cooker)).intValue();
    }
    @Patch.OnExit
    public static void exit(@Patch.This Object cooker,@Patch.Argument(0) Object op,@Patch.Local("vpIsoPlantSourceStart") int start) {
        if(Main.compatible)NativeSource.plant(cooker,op,start);
        LightweightShadows.plant(op,start,((Number)GameAccess.field("viewpoint.world.Cook$Cooker","plantFloats",cooker)).intValue());
    }
}
