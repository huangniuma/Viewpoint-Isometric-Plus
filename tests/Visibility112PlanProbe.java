import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.sun.management.ThreadMXBean;
import viewpoint.core.Frame;
import viewpoint.render.*;
import viewpoint.world.TileMesh;
import vpiso.*;
import zombie.core.textures.*;

/** Exact shipped VP WorldMesher.op/Cook metadata, production makePlan, no game/GL. */
public final class Visibility112PlanProbe {
 static final class Hidden extends HashSet<Integer> {
  long contains;
  @Override public boolean contains(Object key){contains++;return super.contains(key);}
 }
 static MethodHandle makePlan;
 static Constructor<?> masks;
 static volatile long consumed;
 static Object construct(String name,Class<?>[] types,Object... args)throws Exception {
  Constructor<?> c=GameAccess.type(name).getDeclaredConstructor(types);c.setAccessible(true);return c.newInstance(args);
 }
 @SuppressWarnings("unchecked")
 static ChunkMeshData cook(boolean owned,int objects,int pages)throws Exception {
  Object recipe=construct("viewpoint.world.Recipe",new Class<?>[]{int.class},1);
  Class<?> batchType=GameAccess.type("viewpoint.world.Recipe$Batch");
  TileMesh tile=(TileMesh)construct("viewpoint.world.TileMesh",new Class<?>[]{float[].class},(Object)new float[]{0,1,0,0,0,0,1,0,1,1,0,1,0,0,1,0,0,1,1,0,1,0,1,0});
  for(int page=0;page<pages;page++) {
   Object batch=construct("viewpoint.world.Recipe$Batch",new Class<?>[0]);
   ((List<Object>)GameAccess.field("viewpoint.world.Recipe","batches",recipe)).add(batch);
   Texture texture=(Texture)ModelCutawayProbe.allocate(Texture.class);
   Map<Texture,float[]> mappings=(Map<Texture,float[]>)GameAccess.field("viewpoint.world.WorldMesher","mappings",null);mappings.put(texture,new float[]{0,0,1,1,0,0,1,1,1,1,1});
   try {
    for(int square=0;square<64;square++)for(int object=0;object<objects;object++) {
     GameAccess.set("viewpoint.world.WorldMesher","owner",null,owned?square<<2|1:-1);
     GameAccess.set("viewpoint.world.WorldMesher","object",null,square<<16|object);
     GameAccess.call("viewpoint.world.WorldMesher","op",null,new Class<?>[]{batchType,int.class,TileMesh.class,Texture.class,float.class,float.class,float.class},batch,0,tile,texture,(float)(square%8),(float)(square/8),2.4494896f);
    }
   }finally{mappings.remove(texture);}
  }
  return (ChunkMeshData)GameAccess.call("viewpoint.world.Cook","mesh",null,new Class<?>[]{GameAccess.type("viewpoint.world.Recipe")},recipe);
 }
 static Frame frame(ChunkMeshData mesh){Frame f=new Frame();f.scene.addMesh(mesh,0,0,0,(byte)(SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW),0);return f;}
 static void plan(Frame f,ChunkMeshData mesh,Object mask)throws Throwable{f.scene.planEntries=0;f.scene.meshPlan[0]=-1;f.scene.meshPlanLength[0]=0;makePlan.invokeExact(f.scene,0,mesh,mask);}
 public static void main(String[] args)throws Throwable {
  Class<?> type=Class.forName("vpiso.IsometricVisibility$Masks");masks=type.getDeclaredConstructor(long.class,Set.class);masks.setAccessible(true);
  Method method=IsometricVisibility.class.getDeclaredMethod("makePlan",SceneData.class,int.class,ChunkMeshData.class,type);method.setAccessible(true);
  makePlan=MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(void.class,SceneData.class,int.class,ChunkMeshData.class,Object.class));
  if(args.length>0&&args[0].equals("bench"))bench();else parity();
 }
 static void parity()throws Throwable {
  StringBuilder result=new StringBuilder();long calls=0;int cases=0;
  Random random=new Random(112);
  for(boolean owned:new boolean[]{false,true})for(int objects:new int[]{1,6})for(int pages:new int[]{1,2}) {
   ChunkMeshData mesh=cook(owned,objects,pages);Frame f=frame(mesh);
   int[] spans=(int[])GameAccess.field("viewpoint.render.ChunkMeshData","spans",mesh),pageSpans=(int[])GameAccess.field("viewpoint.render.ChunkMeshData","pageSpans",mesh);
   if(spans.length!=64*objects*pages*3||pageSpans.length!=pages+1)throw new AssertionError("actual Cook spans shape");
   int[] original=spans.clone(),runs=mesh.runs==null?null:mesh.runs.clone();
   for(int phase=0;phase<64;phase++) {
    long visible=phase==0?-1L:phase==1?0:random.nextLong();Hidden hidden=new Hidden();
    for(int square=0;square<64;square++)for(int object=0;object<objects;object++)if((square+object+phase)%3==0)hidden.add(square<<16|object);
    Object mask=masks.newInstance(visible,hidden);plan(f,mesh,mask);calls+=hidden.contains;
    result.append(owned).append('/').append(objects).append('/').append(pages).append('/').append(phase).append('/').append(f.scene.meshPlan[0]).append('/').append(f.scene.meshPlanLength[0]).append('/').append(f.scene.planEntries).append('/');
    for(int i=0;i<f.scene.planEntries*3;i++)result.append(f.scene.plan[i]).append(',');result.append(';');
    if(!Arrays.equals(original,spans)||!Arrays.equals(runs,mesh.runs))throw new AssertionError("mutated cached metadata");cases++;
   }
  }
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.toString().getBytes(StandardCharsets.UTF_8)));
  System.out.println("PLAN112 {\"cases\":"+cases+",\"output\":\""+hash+"\",\"hiddenLookups\":"+calls+",\"actualCook\":true,\"gpu\":false}");
 }
 static void bench()throws Throwable {
  ChunkMeshData mesh=cook(true,6,2);Frame f=frame(mesh);Set<Integer> hidden=new HashSet<>();
  for(int square=0;square<64;square++)for(int object=0;object<6;object++)if((square+object)%3==0)hidden.add(square<<16|object);
  Object mask=masks.newInstance(0xaaaaaaaaaaaaaaaaL,hidden);
  for(int i=0;i<500;i++){plan(f,mesh,mask);consumed+=f.scene.planEntries;}
  ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);bean.setThreadCpuTimeEnabled(true);
  int runs=5000;long[] times=new long[runs];
  long id=Thread.currentThread().getId(),allocated=bean.getThreadAllocatedBytes(id),cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();
  for(int i=0;i<runs;i++){long at=System.nanoTime();plan(f,mesh,mask);consumed+=f.scene.planEntries;times[i]=System.nanoTime()-at;}
  long wall=System.nanoTime()-start;cpu=bean.getCurrentThreadCpuTime()-cpu;allocated=bean.getThreadAllocatedBytes(id)-allocated;
  Arrays.sort(times);
  System.out.println("PLANBENCH112 {\"runs\":"+runs+",\"wallNs\":"+wall+",\"cpuNs\":"+cpu+",\"allocatedBytes\":"+allocated+",\"medianNs\":"+times[runs/2]+",\"p99Ns\":"+times[(int)(runs*.99)-1]+",\"countingSetUsed\":false,\"checksum\":"+consumed+"}");
 }
}
