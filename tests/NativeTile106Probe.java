import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.lang.management.ManagementFactory;
import viewpoint.core.Frame;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.*;
import zombie.iso.sprite.*;
import zombie.iso.SpriteDetails.IsoFlagType;

/** CPU-only actual NativeViewData capture, native getters/cap rules, exact ordered lookup trace. */
public final class NativeTile106Probe {
 static final IsoGridSquare[] SQ=new IsoGridSquare[64];
 static final IdentityHashMap<Object,Integer> IDS=new IdentityHashMap<>();
 static Object unsafe;static Method allocate;
 static long reads, lightingReads, trace=1469598103934665603L;
 static boolean recording;
 static volatile long consume;
 static Object bindings;static Field squareField,blackedField,fadeField;
 static Object oldSquare,oldBlacked,oldFade;
 static int variants;
 public static void main(String[] args)throws Throwable {
  var u=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");u.setAccessible(true);unsafe=u.get(null);
  allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
  setup();
  try {
   if(args.length>0&&args[0].equals("bench")){bench(Integer.parseInt(args[1]));return;}
   for(int shape=0;shape<8;shape++)for(int layers:new int[]{1,3,8})for(int step=0;step<3;step++) {
    variants=step;Frame f=frame(shape,layers);NativeViewData d=new NativeViewData();
    recording=true;reads=lightingReads=0;trace=1469598103934665603L;
    for(int repeat=0;repeat<3;repeat++){f.scene.rain=repeat==1?.8f:0;d.capture(f,new Object(),0,new Object());}
    System.out.println("TILE106 case="+shape+","+layers+","+step+" lookup="+reads+" seen="+lightingReads+" order="+Long.toUnsignedString(trace,16)+" sampled="+d.sampledSquares()+" output="+snapshot(d));
   }
   System.out.println("Native tile106 CPU parity outputs complete: actual source capture order, byte/topology/weather fields, native flags/caps/tree/seen; no GPU.");
  }finally{squareField.set(bindings,oldSquare);blackedField.set(bindings,oldBlacked);fadeField.set(bindings,oldFade);}
 }
 static void setup()throws Throwable {
  zombie.core.random.RandStandard.INSTANCE.init();
  Field b=Class.forName("vpiso.NativeViewData$BindingsHolder").getDeclaredField("API");b.setAccessible(true);bindings=b.get(null);
  squareField=field(bindings.getClass(),"square");blackedField=field(bindings.getClass(),"blacked");fadeField=field(bindings.getClass(),"fade");
  oldSquare=squareField.get(bindings);oldBlacked=blackedField.get(bindings);oldFade=fadeField.get(bindings);
  var lookup=MethodHandles.lookup();squareField.set(bindings,lookup.findStatic(NativeTile106Probe.class,"squareAt",MethodType.methodType(Object.class,Object.class,int.class,int.class,int.class)));
  blackedField.set(bindings,lookup.findStatic(NativeTile106Probe.class,"black",MethodType.methodType(boolean.class,Object.class,Object.class)));
  fadeField.set(bindings,lookup.findStatic(NativeTile106Probe.class,"fade",MethodType.methodType(float.class,Object.class,Object.class)));
  IsoFlagType[] fs={IsoFlagType.WindowN,IsoFlagType.WindowW,IsoFlagType.WallN,IsoFlagType.WallW,IsoFlagType.WallNW,IsoFlagType.WallSE};
  for(int n=0;n<SQ.length;n++) {
   IsoGridSquare s=(IsoGridSquare)allocate.invoke(unsafe,IsoGridSquare.class);SQ[n]=s;IDS.put(s,n);
   field(IsoGridSquare.class,"objects").set(s,new ArrayList<IsoObject>());
   var p=new zombie.core.properties.PropertyContainer();for(int i=0;i<fs.length;i++)if((n&(1<<i))!=0)p.set(fs[i]);
   field(IsoGridSquare.class,"properties").set(s,p);s.haveRoof=(n&1)!=0;
   field(IsoGridSquare.class,"targetPlayerCutawayFlags").set(s,new byte[]{(byte)(n&3),0,0,0});
   field(IsoGridSquare.class,"playerCutawayFlags").set(s,new byte[]{(byte)(n&3),0,0,0});
   field(IsoGridSquare.class,"playerCutawayFlagLockUntilTimes").set(s,new long[4]);
   var lt=Class.forName("zombie.iso.IsoGridSquare$ILighting");Object l=Proxy.newProxyInstance(lt.getClassLoader(),new Class[]{lt},(proxy,m,a)->{if(m.getName().equals("bSeen")){lightingReads++;return true;}throw new AssertionError(m);});
   Object ls=Array.newInstance(lt,4);for(int i=0;i<4;i++)Array.set(ls,i,l);field(IsoGridSquare.class,"lighting").set(s,ls);
   for(int o=0;o<n%8;o++){IsoSprite sp=new IsoSprite();sp.cutN=(o&1)==0;sp.cutW=(o&2)==0;s.getObjects().add(new IsoObject(null,s,sp));}
   if(n%9==0)s.getObjects().add((IsoObject)allocate.invoke(unsafe,zombie.iso.objects.IsoTree.class));
  }
 }
 static Frame frame(int shape,int layers)throws Exception {
  Frame f=new Frame();f.camX=10000.25f;f.camY=10000.75f;f.camZ=shape%3-1;
  int count=0,side=shape<3?4:shape<6?8:16;
  f.scene.meshes=new ChunkMeshData[4096];f.scene.meshFlags=new byte[4096];f.scene.meshOffsets=new float[4096*3];
  for(int z=0;z<layers;z++)for(int y=0;y<side;y++)for(int x=0;x<side;x++) {
   if(shape%3==1&&(x+y)%3==0)continue;
   ChunkMeshData m=(ChunkMeshData)allocate.invoke(unsafe,ChunkMeshData.class);field(ChunkMeshData.class,"level").setInt(m,z-1);
   f.scene.meshes[count]=m;f.scene.meshFlags[count]=(byte)((x+y)%2==0?SceneData.VISIBLE:SceneData.NEAR);
   f.scene.meshOffsets[count*3]=f.camX-(10000-side*4+x*8);f.scene.meshOffsets[count*3+2]=f.camY-(10000-side*4+y*8);count++;
  }
  f.scene.meshCount=count;return f;
 }
 public static Object squareAt(Object ignored,int x,int y,int z) {
  reads++;if(recording){trace=(trace^x)*1099511628211L;trace=(trace^y)*1099511628211L;trace=(trace^z)*1099511628211L;}
  int mix=x*13+y*31+z*19+variants;if((mix&31)==0)return null;
  return SQ[Math.floorMod(mix,SQ.length)];
 }
 public static boolean black(Object ignored,Object square){return (IDS.get(square)&7)==0;}
 public static float fade(Object ignored,Object square){return switch(IDS.get(square)%5){case 0->.5f;case 1->Float.NaN;case 2->-1;case 3->2;default->0;};}
 static String snapshot(NativeViewData d)throws Exception {
  MessageDigest hash=MessageDigest.getInstance("SHA-256");
  for(String name:List.of("minX","minY","minLevel","width","height","layers","sampledSquares")){int value=field(NativeViewData.class,name).getInt(d);hash.update(ByteBuffer.allocate(4).putInt(value).array());}
  ByteBuffer buf=((ByteBuffer)field(NativeViewData.class,"pixels").get(d)).duplicate();byte[] bytes=new byte[buf.limit()];buf.position(0);buf.get(bytes);hash.update(bytes);
  hash.update((byte[])field(NativeViewData.class,"topology").get(d));
  Object w=field(NativeViewData.class,"weather").get(d);Object wp=field(w.getClass(),"pixels").get(w);
  if(wp!=null){buf=((ByteBuffer)wp).duplicate();bytes=new byte[buf.limit()];buf.position(0);buf.get(bytes);hash.update(bytes);}
  return HexFormat.of().formatHex(hash.digest());
 }
 static void bench(int shape)throws Exception {
  variants=0;Frame f=frame(shape,3);NativeViewData d=new NativeViewData();recording=false;
  int warm=60,runs=120;for(int i=0;i<warm;i++)d.capture(f,new Object(),0,new Object());
  com.sun.management.ThreadMXBean mx=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long tid=Thread.currentThread().getId();
  long wall=System.nanoTime(),cpu=mx.getCurrentThreadCpuTime(),alloc=mx.getThreadAllocatedBytes(tid);
  for(int i=0;i<runs;i++){d.capture(f,new Object(),0,new Object());consume+=d.sampledSquares();}
  System.out.println("BENCH106 {\"shape\":"+shape+",\"frames\":"+runs+",\"sampled\":"+d.sampledSquares()+",\"wallNs\":"+(System.nanoTime()-wall)+",\"cpuNs\":"+(mx.getCurrentThreadCpuTime()-cpu)+",\"allocatedBytes\":"+(mx.getThreadAllocatedBytes(tid)-alloc)+"}");
 }
 static Field field(Class<?> owner,String name)throws Exception {Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;}
}
