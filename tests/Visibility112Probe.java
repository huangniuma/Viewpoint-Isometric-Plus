import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import vpiso.*;
import zombie.iso.*;

/** Extend retained109 native method-chain parity with cache reuse/player/replacement cases. */
public final class Visibility112Probe extends ReadMasks112Base {
 public static void main(String[] args)throws Throwable {
  setup(args.length>1?Integer.parseInt(args[1]):4,args.length>2?Integer.parseInt(args[2]):6);
  try {
   if(args.length==0||args[0].equals("parity")){parity();lifetimes();}
   else if(args[0].equals("bench"))bench(args.length>3?Integer.parseInt(args[3]):150,args.length>4?Integer.parseInt(args[4]):300);
   else throw new IllegalArgumentException(args[0]);
  }finally{Visibility109Bindings.set(api,"square",oldSquare);NativeObjectState.endCapture();}
 }
 static void lifetimes()throws Throwable {
  counted(true);
  try {
   for(int phase=0;phase<8;phase++) {
    reset(phase);ModelCutawayProbe.camera(phase%3-1);FRAME.camZ=phase%3-1;
    // Replace an actual square at the same coordinates; the old identity must never win.
    IsoGridSquare old=grid[1];
    if(phase==4) {
     Method factory=UpperObjects064Probe.class.getDeclaredMethod("square",int.class,int.class,int.class,boolean.class);factory.setAccessible(true);
     IsoGridSquare replacement=(IsoGridSquare)factory.invoke(null,old.x,old.y,old.z,true);
     replacement.chunk.wx=Math.floorDiv(replacement.x,8);replacement.chunk.wy=Math.floorDiv(replacement.y,8);
     GameAccess.set("zombie.iso.IsoGridSquare","lighting",replacement,null);
     for(IsoObject source:old.getObjects()){GameAccess.set("zombie.iso.IsoObject","square",source,replacement);replacement.getObjects().add(source);}
     grid[1]=replacement;
    }
    trace=0xcbf29ce484222325L;lookups=buildingCalls=forcedCalls=infoCalls=alphaCalls=0;traceOn=true;
    StringBuilder out=new StringBuilder();STATE.beginCapture(FRAME);
    try {
     for(int player:new int[]{0,1,0,2,3,0})for(int k=0;k<keys.length;k+=Math.max(1,keys.length/4)) {
      Object mask=masks(keys[k],player);out.append((long)squares.invokeExact(mask)).append(':');
      @SuppressWarnings("unchecked") Set<Integer> h=(Set<Integer>)hidden.invokeExact(mask);
      ArrayList<Integer> ordered=new ArrayList<>(h);Collections.sort(ordered);out.append(ordered).append(';');
     }
    }finally{NativeObjectState.endCapture();traceOn=false;}
    for(Source source:sources)for(int player=0;player<4;player++) {
     var info=source.getRenderInfo(player);out.append(source.id).append('/').append(player).append('/').append(Float.floatToRawIntBits(source.getAlpha(player))).append('/').append(info.layer).append('/').append(Float.floatToRawIntBits(info.targetAlpha)).append(';');
    }
    // Candidate pool retains reusable data holders, never the native square identities.
    Field visibility=NativeObjectState.class.getDeclaredField("visibility");visibility.setAccessible(true);
    Object cache=visibility.get(STATE);
    for(String name:new String[]{"squares","objects"}){Field f=cache.getClass().getDeclaredField(name);f.setAccessible(true);require(((Map<?,?>)f.get(cache)).isEmpty(),"capture map retained keys");}
    try {
     Field pool=cache.getClass().getDeclaredField("objectPool");pool.setAccessible(true);
     for(Object value:(List<?>)pool.get(cache)){Field f=value.getClass().getDeclaredField("square");f.setAccessible(true);require(f.get(value)==null,"pool retained native square");}
    }catch(NoSuchFieldException baseline){}
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(out.toString().getBytes(StandardCharsets.UTF_8)));
    System.out.printf(Locale.ROOT,"LIFE112 {\"phase\":%d,\"output\":\"%s\",\"order\":\"%s\",\"lookups\":%d,\"building\":%d,\"forced\":%d,\"classifications\":%d,\"alpha\":%d}%n",phase,hash,Long.toUnsignedString(trace,16),lookups,buildingCalls,forcedCalls,infoCalls,alphaCalls);
   }
  }finally{traceOn=false;counted(false);}
 }
}
