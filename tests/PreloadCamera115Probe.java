import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import vpiso.*;

/** Calls the actual woven ChunkWalk.inView, checking the isometric preload scope against its camera geometry. */
public final class PreloadCamera115Probe {
    public static void main(String[] args)throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of(args[0],"viewpoint/world/ChunkWalk.class"));
        ClassLoader loader=new ClassLoader(PreloadCamera115Probe.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    if(!name.equals("viewpoint.world.ChunkWalk"))return super.loadClass(name,resolve);
                    Class<?> type=findLoadedClass(name);if(type==null)type=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(type);return type;
                }
            }
        };
        Class<?> type=loader.loadClass("viewpoint.world.ChunkWalk");Object walk=ModelCutawayProbe.allocate(type);
        Method cone=type.getDeclaredMethod("inView",float.class,float.class,float.class,float.class,float.class,float.class);cone.setAccessible(true);
        Field coneWidth=type.getDeclaredField("cone");coneWidth.setAccessible(true);coneWidth.setDouble(walk,Math.toRadians(45));
        ThreadLocal<ShadowPreload.Camera> current=(ThreadLocal<ShadowPreload.Camera>)GameAccess.field("vpiso.ShadowPreload","CAMERA",null);
        int count=0,haloOnly=0,changed=0;
        try {
            for(float zoom:new float[]{.25f,.5f,1f,2.5f})for(float yaw:new float[]{0,37,90,180,270})for(float origin:new float[]{-10780,0,10780}) {
                IsoCameraMath.Fit fit=new IsoCameraMath.Fit(origin,origin,12*zoom);
                current.set(new ShadowPreload.Camera(fit,origin,origin,1920,1080,yaw));
                for(int x=-120;x<=120;x+=8)for(int y=-120;y<=120;y+=8)for(int level:new int[]{-2,0,3}) {
                    float up=level*2.4494896f;
                    boolean normal=IsoCameraMath.visible(fit,origin,origin,1920,1080,-x,up+1.5f,-y,12f,yaw);
                    boolean expected=IsoCameraMath.visible(fit,origin,origin,1920,1080,-x,up+1.5f,-y,20f,yaw);
                    boolean actual=(Boolean)cone.invoke(walk,(float)x,(float)y,up,1f,0f,0f);
                    if(actual!=expected)throw new AssertionError("Woven cone differed from actual isometric halo");
                    if(normal&&!actual)throw new AssertionError("Visible chunk deprioritized");
                    if(expected&&!normal)haloOnly++;
                    current.remove();boolean ordinary=(Boolean)cone.invoke(walk,(float)x,(float)y,up,1f,0f,0f);
                    if(actual!=ordinary)changed++;
                    current.set(new ShadowPreload.Camera(fit,origin,origin,1920,1080,yaw));count++;
                }
            }
            if(haloOnly==0||changed==0)throw new AssertionError("No meaningful preload difference");
            ShadowPreload.Camera saved=current.get();ShadowPreload.restore(null);
            if(current.get()!=null||ShadowPreload.visible(100,100,0,true)!=true||ShadowPreload.visible(100,100,0,false)!=false)throw new AssertionError("Scope passthrough leaked");
            ShadowPreload.restore(saved);if(current.get()!=saved)throw new AssertionError("Outer camera not restored");
            System.out.println("PRELOADCAMERA115 PASS samples="+count+" haloOnly="+haloOnly+" changedPriority="+changed+"; real woven ChunkWalk cone, 25-250 zoom, five rotations, signed origins/levels, visible superset, scope restore and ordinary passthrough. CPU fixture only.");
        }finally{current.remove();}
    }
}
