import viewpoint.core.Frame;
import vpiso.*;

/** Optional classpath arms execute the real queued model scope without GL/game startup. */
public final class PoolPitMode120Probe {
    public static void main(String[] args) {
        boolean present=Boolean.parseBoolean(args[0]);
        if(PoolPitCompatibility.loaded()!=present)throw new AssertionError("Optional dependency detection");
        for(boolean originalAvailable:new boolean[]{false,true}) {
            Frame frame=new Frame();
            IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,originalAvailable);
            if(IsometricBridge.renderFrame()!=frame || !IsometricBridge.modelRendering())throw new AssertionError("Model scope lost");
            if(IsometricBridge.usesNativeWater()!=(originalAvailable&&!present))throw new AssertionError("PoolPit surface still replaced by native water");
            if(IsometricBridge.waterRendering())throw new AssertionError("Native optional water overlay leaked into world scope");
            IsometricBridge.rendering(null);
            if(IsometricBridge.usesNativeWater() || IsometricBridge.rendering())throw new AssertionError("Water scope leaked");
        }
        System.out.println("PoolPit120 mode PASS present="+present+"; real model draw scope, native/PoolPit water ownership and restoration; no GL or game startup.");
    }
}
