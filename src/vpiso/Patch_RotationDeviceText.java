package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.chat.ChatElement",methodName="renderBatched",strictMatch=true)
public final class Patch_RotationDeviceText {
    @Patch.OnEnter
    public static void enter(@Patch.This Object chat,@Patch.Argument(0) int player,
            @Patch.Argument(value=1,readOnly=false) int x,@Patch.Argument(value=2,readOnly=false) int y,
            @Patch.Argument(3) boolean ignoreRadio) {
        float[] point=RotationDeviceText.project(chat,player);
        if(point!=null){x=(int)point[0];y=(int)point[1];}
    }
}
