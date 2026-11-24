package vpiso;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoObject;
import zombie.iso.IsoDirections;
import zombie.iso.sprite.IsoSprite;

@Patch(className="zombie.iso.sprite.IsoSprite",methodName="renderCurrentAnim_FBORender",strictMatch=true)
public final class Patch_NativeSourceSprite {
    @Patch.OnEnter
    public static void enter(@Patch.This IsoSprite sprite,@Patch.Argument(0) Object instance,@Patch.Argument(1) IsoObject object,
            @Patch.Argument(2) float x,@Patch.Argument(3) float y,@Patch.Argument(4) float z,
            @Patch.Argument(5) IsoDirections direction,@Patch.Local("vpIsoSource") boolean active) {
        active=IsometricBridge.nativeGroundActive() && !IsometricBridge.capturing() && !IsometricBridge.rendering();
        if(active)NativeGround.queueSource(NativeSource.sprite(sprite,instance,object,x,y,z,direction));
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoSource") boolean active) {if(active)NativeGround.queueSourceEnd();}
}
