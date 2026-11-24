package vpiso;

import zombie.iso.objects.IsoWaveSignal;

/** Reproject only device subtitles; native hearing, power and chat batching stay in charge. */
public final class RotationDeviceText {
    private static final ThreadLocal<float[]> POINT=ThreadLocal.withInitial(()->new float[2]);

    public static float[] project(Object chat, int player) {
        // Input runs on the display thread and can advance/clear latest while this
        // frame is queuing subtitles. Use the camera belonging to the queued world.
        RotationCamera.Snapshot camera=RotationLayers.current();
        if(camera==null)camera=RotationCamera.snapshot();
        if(!Main.compatible || player!=0 || camera==null || !IsometricBridge.active())return null;
        Object owner=GameAccess.field("zombie.chat.ChatElement","owner",chat);
        if(!(owner instanceof IsoWaveSignal device) || device.getSquare()==null)return null;
        // Use VP's device anchor: square centre plus object rise and text clearance.
        float height=((Number)GameAccess.call("viewpoint.world.WorldMesher","rise",null,
                new Class<?>[]{zombie.iso.IsoObject.class},device)).floatValue()
                +((Number)GameAccess.field("viewpoint.platform.Tuning","deviceTextHeight",null)).floatValue();
        float[] point=POINT.get();
        return camera.project(device.getX()+.5f,device.getY()+.5f,device.getZ()+height/2.4494896f,point)?point:null;
    }
    private RotationDeviceText() {}
}
