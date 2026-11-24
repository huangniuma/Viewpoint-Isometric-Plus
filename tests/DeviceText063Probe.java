import java.lang.reflect.*;
import java.lang.Math;
import org.joml.*;
import vpiso.IsoCameraMath;

/** Interleave the real display-input camera invalidation with native subtitle queuing. */
public final class DeviceText063Probe {
    public static void main(String[] args)throws Exception {
        DeviceText061Probe.main(new String[]{args[0]});
        DeviceText061Probe.setStatic("vpiso.IsometricBridge","submitted",true);
        boolean baseline=args.length>1&&args[1].equals("baseline062");
        Object device=DeviceText061Probe.fresh("zombie.iso.objects.IsoWaveSignal");
        Object square=DeviceText061Probe.fresh("zombie.iso.IsoGridSquare");
        DeviceText061Probe.set(square,"x",103);DeviceText061Probe.set(square,"y",198);DeviceText061Probe.set(square,"z",2);
        DeviceText061Probe.set(device,"square",square);
        Object chat=DeviceText061Probe.fresh("zombie.chat.ChatElement");
        DeviceText061Probe.set(chat,"owner",device);DeviceText061Probe.set(chat,"hasChatToDisplay",true);
        DeviceText061Probe.set(device,"chatElement",chat);
        Object lines=Array.newInstance(DeviceText061Probe.type("zombie.chat.ChatElement$PlayerLines"),4);
        for(int i=0;i<4;i++)Array.set(lines,i,DeviceText061Probe.fresh("zombie.chat.ChatElement$PlayerLines"));
        DeviceText061Probe.set(chat,"playerLines",lines);
        Class<?> cameraType=DeviceText061Probe.type("vpiso.RotationCamera");
        Class<?> snapshotType=DeviceText061Probe.type("vpiso.RotationCamera$Snapshot");
        Class<?> layers=DeviceText061Probe.type("vpiso.RotationLayers");
        Method configure=cameraType.getMethod("configureYaw",boolean.class,float.class);
        Method begin=layers.getMethod("begin",snapshotType),end=layers.getMethod("end",snapshotType);
        int failures=0,cases=0;
        for(float zoom:new float[]{.5f,1f,2f})for(int step=0;step<32;step++) {
            float yaw=(step*11.25f+.125f)%360f;
            configure.invoke(null,true,yaw);
            Matrix4f projection=new Matrix4f(),view=new Matrix4f();
            IsoCameraMath.matrices(new IsoCameraMath.Fit(100,200,12*zoom),100,200,1280,720,projection,view,new float[3],yaw);
            Object world=snapshotType.getConstructor(Matrix4f.class,Matrix4f.class,float.class,float.class,float.class,int.class,int.class,float.class,float.class)
                .newInstance(projection,view,100f,200f,2f,1280,720,zoom,yaw);
            DeviceText061Probe.setStatic("vpiso.RotationCamera","latest",world);
            Object previous=begin.invoke(null,world);
            try {
                Throwable[] error={null};
                Thread input=new Thread(()->{try{configure.invoke(null,true,yaw+3f);}catch(Throwable t){error[0]=t;}},"display-input063");
                input.start();input.join();if(error[0]!=null)throw new AssertionError(error[0]);
                DeviceText061Probe.require(cameraType.getMethod("snapshot").invoke(null)==null,"actual camera invalidation not exercised");
                for(int update=0;update<2;update++) {
                    if(update==1) {
                        Matrix4f newer=new Matrix4f(view).rotateY(.25f);
                        Object future=snapshotType.getConstructor(Matrix4f.class,Matrix4f.class,float.class,float.class,float.class,int.class,int.class,float.class,float.class)
                            .newInstance(projection,newer,100f,200f,2f,1280,720,zoom,yaw+3f);
                        DeviceText061Probe.setStatic("vpiso.RotationCamera","latest",future);
                    }
                    float height=((Number)DeviceText061Probe.getStatic("viewpoint.platform.Tuning","deviceTextHeight")).floatValue();
                    Vector4f clip=new Matrix4f(projection).mul(view).transform(new Vector4f(-3.5f,height,1.5f,1));
                    int expectedX=(int)((clip.x/clip.w+1)*640),expectedY=(int)((1-clip.y/clip.w)*360);
                    DeviceText061Probe.type("zombie.iso.objects.IsoWaveSignal").getMethod("renderlast").invoke(device);
                    Object line=Array.get(lines,0);
                    boolean correct=Math.abs((int)DeviceText061Probe.get(line,"renderX")-expectedX)<=1
                        &&Math.abs((int)DeviceText061Probe.get(line,"renderY")-expectedY)<=1;
                    if(!correct)failures++;
                    if(!baseline)DeviceText061Probe.require(correct,"moving device anchor drift step="+step+" zoom="+zoom+" update="+update);
                    DeviceText061Probe.clearBatch();cases++;
                }
            } finally {end.invoke(null,previous);}
            DeviceText061Probe.require(layers.getMethod("current").invoke(null)==null,"subtitle camera scope leaked");
        }
        if(baseline)DeviceText061Probe.require(failures>0,"062 moving-input negative control did not reproduce");
        System.out.println((baseline?"Pinned062 moving device negative control PASS: ":"Moving device text regression063 PASS: ")+cases+" cases; real configureYaw invalidation on separate display-input thread, newer global camera, native device renderlast + woven ChatElement queue, 32 consecutive angles/three zooms, exact queued-world anchor and scope cleanup; mismatches="+failures);
    }
}
