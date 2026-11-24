package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="viewpoint.game.WorldText", methodName="snapshot")
public final class Patch_WorldText {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter() { return IsometricBridge.capturing(); }
}
