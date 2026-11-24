import java.lang.reflect.*;
import java.lang.Math;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.joml.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import static net.bytebuddy.matcher.ElementMatchers.*;
import vpiso.IsoCameraMath;

/** Actual native device subtitle producer and woven ChatElement batching; no text or GL substitute. */
public final class DeviceText061Probe {
    static ClassLoader loader;
    static Object unsafe;
    static Method allocate;
    static boolean old;
    static int cases;
    public static void main(String[] args)throws Exception {
        old=args.length>1&&args[1].equals("baseline060");
        Map<String,byte[]> entries=new HashMap<>();
        try(JarFile jar=new JarFile(args[0])) {
            for(JarEntry e:jar.stream().filter(e->e.getName().startsWith("vpiso/")&&e.getName().endsWith(".class")).toList())
                entries.put(e.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(e).readAllBytes());
        }
        var ctor=Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class,boolean.class);ctor.setAccessible(true);
        if(!old) {
            Method convert=Class.forName("PatchWeavingProbe").getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
            String patch="vpiso.Patch_RotationDeviceText";
            Map<String,byte[]> converted=Map.of(patch,(byte[])convert.invoke(null,(Object)entries.get(patch)));
            ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(converted),ClassFileLocator.ForClassLoader.ofSystemLoader());
            ClassLoader advice=(ClassLoader)ctor.newInstance(converted,false);
            entries.put("zombie.chat.ChatElement",new ByteBuddy().redefine(TypePool.Default.of(locator).describe("zombie.chat.ChatElement").resolve(),locator)
                    .visit(Advice.to(advice.loadClass(patch),locator).on(named("renderBatched").and(takesArguments(int.class,int.class,int.class,boolean.class)))).make().getBytes());
        }
        loader=(ClassLoader)ctor.newInstance(entries,true);
        // The isolated loader has no CodeSource; validate the actual parent game JAR
        // through VP's own hash checker before supplying the cached result.
        Method pin=type("viewpoint.platform.BuildPin").getDeclaredMethod("check",java.io.File.class);pin.setAccessible(true);
        boolean supported=(boolean)pin.invoke(null,new java.io.File(Class.forName("zombie.core.Core").getProtectionDomain().getCodeSource().getLocation().toURI()));
        require(supported,"Actual game fingerprint refused");setStatic("viewpoint.platform.BuildPin","supported",supported);
        Field uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=uf.get(null);allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
        setStatic("vpiso.Main","compatible",true);setStatic("viewpoint.core.View","enabled",false);
        type("vpiso.IsometricBridge").getMethod("setEnabled",boolean.class).invoke(null,true);
        type("vpiso.IsometricBridge").getMethod("setModelsEnabled",boolean.class).invoke(null,true);
        setStatic("vpiso.IsometricBridge","submitted",true);
        Object device=fresh("zombie.iso.objects.IsoWaveSignal"),square=fresh("zombie.iso.IsoGridSquare");
        set(square,"x",103);set(square,"y",198);set(square,"z",2);set(device,"square",square);
        Object chat=fresh("zombie.chat.ChatElement");set(chat,"owner",device);set(chat,"hasChatToDisplay",true);set(device,"chatElement",chat);
        Object lines=Array.newInstance(type("zombie.chat.ChatElement$PlayerLines"),4);
        for(int i=0;i<4;i++)Array.set(lines,i,fresh("zombie.chat.ChatElement$PlayerLines"));set(chat,"playerLines",lines);
        Object state=getStatic("zombie.iso.IsoCamera","frameState");set(state,"playerIndex",0);
        boolean mismatch=false;
        for(float zoom:new float[]{.5f,1f,2f})for(float yaw:new float[]{17.25f,45f,90f,137f,180f,225f,270f,359.75f})for(float rise:new float[]{0,48}) {
            type("vpiso.RotationCamera").getMethod("configureYaw",boolean.class,float.class).invoke(null,true,yaw);
            Matrix4f projection=new Matrix4f(),view=new Matrix4f();
            IsoCameraMath.matrices(new IsoCameraMath.Fit(100,200,12*zoom),100,200,1280,720,projection,view,new float[3],yaw);
            Object camera=type("vpiso.RotationCamera$Snapshot").getConstructor(Matrix4f.class,Matrix4f.class,float.class,float.class,float.class,int.class,int.class,float.class,float.class)
                    .newInstance(projection,view,100f,200f,2f,1280,720,zoom,yaw);
            setStatic("vpiso.RotationCamera","latest",camera);set(device,"renderYOffset",rise);
            float height=rise/96f*2.4494896f+((Number)getStatic("viewpoint.platform.Tuning","deviceTextHeight")).floatValue();
            Vector4f clip=new Matrix4f(projection).mul(view).transform(new Vector4f(100-103.5f,height,200-198.5f,1));
            int expectedX=(int)((clip.x/clip.w+1)*.5f*1280),expectedY=(int)((1-clip.y/clip.w)*.5f*720);
            type("zombie.iso.objects.IsoWaveSignal").getMethod("renderlast").invoke(device);
            Object line=Array.get(lines,0);int x=(int)get(line,"renderX"),y=(int)get(line,"renderY");
            if(old)mismatch|=Math.abs(x-expectedX)>3||Math.abs(y-expectedY)>3;
            else require(Math.abs(x-expectedX)<=1&&Math.abs(y-expectedY)<=1,"device anchor mismatch yaw="+yaw+" zoom="+zoom+": "+x+","+y+" vs "+expectedX+","+expectedY);
            clearBatch();cases++;
        }
        if(old){require(mismatch,"060 drift negative control did not reproduce");System.out.println("Pinned060 device text negative control PASS: native device producer ignores rotated camera, batching anchor drifts.");return;}
        Method render=type("zombie.chat.ChatElement").getMethod("renderBatched",int.class,int.class,int.class,boolean.class);
        Object lastCamera=getStatic("vpiso.RotationCamera","latest");
        int lastX=(int)get(Array.get(lines,0),"renderX"),lastY=(int)get(Array.get(lines,0),"renderY");
        for(String deviceType:List.of("zombie.iso.objects.IsoRadio","zombie.iso.objects.IsoTelevision")) {
            Object other=fresh(deviceType);set(other,"square",square);set(other,"renderYOffset",48f);set(chat,"owner",other);
            render.invoke(chat,0,77,88,false);Object line=Array.get(lines,0);
            require((int)get(line,"renderX")==lastX&&(int)get(line,"renderY")==lastY,"radio/TV owner anchor changed");clearBatch();cases++;
        }
        // Other owners, other players and inactive views keep original coordinates and flags.
        for(String gate:List.of("other-owner","other-player","no-rotation","vp-view","disabled","unsupported")) {
            set(chat,"owner",device);setStatic("vpiso.RotationCamera","latest",lastCamera);int player=0;
            if(gate.equals("other-owner"))set(chat,"owner",fresh("zombie.characters.IsoPlayer"));
            if(gate.equals("other-player"))player=1;
            if(gate.equals("no-rotation"))setStatic("vpiso.RotationCamera","latest",null);
            if(gate.equals("vp-view"))setStatic("viewpoint.core.View","enabled",true);
            if(gate.equals("disabled"))type("vpiso.IsometricBridge").getMethod("setEnabled",boolean.class).invoke(null,false);
            if(gate.equals("unsupported"))setStatic("vpiso.Main","compatible",false);
            render.invoke(chat,player,77,88,true);Object line=Array.get(lines,player);
            require((int)get(line,"renderX")==77&&(int)get(line,"renderY")==88&&(boolean)get(line,"ignoreRadioLines"),"unrelated text gate changed: "+gate);
            clearBatch();setStatic("viewpoint.core.View","enabled",false);setStatic("vpiso.Main","compatible",true);
            type("vpiso.IsometricBridge").getMethod("setEnabled",boolean.class).invoke(null,true);cases++;
        }
        System.out.println("Rotated device text regression061 PASS: "+cases+" cases; actual native IsoWaveSignal.renderlast and woven ChatElement batch, eight yaws/three zooms/two device rises, radio/TV owners, independent matrix anchor, original line/ignore-radio data and independent ordinary/player/view gates. No game or GL launched.");
    }
    static void clearBatch()throws Exception {Object batches=getStatic("zombie.chat.ChatElement","renderBatch");for(int i=0;i<Array.getLength(batches);i++)if(Array.get(batches,i)!=null)((List<?>)Array.get(batches,i)).clear();}
    static Class<?> type(String n)throws Exception{return Class.forName(n,true,loader);}
    static Object fresh(String n)throws Exception{return allocate.invoke(unsafe,type(n));}
    static Field field(Class<?> c,String n)throws Exception{for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
    static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
    static Object getStatic(String c,String n)throws Exception{return field(type(c),n).get(null);}
    static void setStatic(String c,String n,Object v)throws Exception{field(type(c),n).set(null,v);}
    static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
}
