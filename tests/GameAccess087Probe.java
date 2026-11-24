import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import vpiso.GameAccess;
/** Independent reflection semantics, concurrent misses and warmed allocation benchmark. */
public final class GameAccess087Probe {
 static volatile long sink;
 public static class Parent {public int inherited(int v){return v+3;}}
 public static class Owner extends Parent {
  private int value=7;private static int fixed=11;
  private int method(int v){return value+v;}
  private String method(String v){return v+value;}
  private static int zero(){return fixed;}
  private int fails(){throw new IllegalArgumentException("original failure");}
  private boolean coldHoldsCacheLock() throws Exception {
   try {
    Field owners=GameAccess.class.getDeclaredField("OWNERS");owners.setAccessible(true);Object bucket=((Map<?,?>)owners.get(null)).get(Owner.class.getName());
    Field methods=bucket.getClass().getDeclaredField("methods");methods.setAccessible(true);return Thread.holdsLock(methods.get(bucket));
   }catch(NoSuchFieldException oldBaseline){return false;}
  }
 }
 public static class Recursive {private static int value=init();static int init(){return ((Number)GameAccess.field(Recursive.class.getName(),"other",null)).intValue()+1;}private static int other=2;}
 static final String OWNER=Owner.class.getName();static final Class<?>[] INT={int.class},STRING={String.class},NONE={};
 public static void main(String[] args)throws Exception{
  require(((Number)GameAccess.field(Recursive.class.getName(),"value",null)).intValue()==1,"Recursive class initialisation changed");
  Owner owner=new Owner();require(Boolean.FALSE.equals(GameAccess.call(OWNER,"coldHoldsCacheLock",owner,NONE)),"Target executed while accessor cache lock held");require(GameAccess.call(OWNER,"method",owner,INT,2).equals(9),"private invocation");
  require(GameAccess.call(OWNER,"method",owner,STRING,"a").equals("a7"),"overload");
  require(GameAccess.call(OWNER,"inherited",owner,INT,4).equals(7),"inherited invocation");
  require(GameAccess.staticCall(OWNER,"zero").equals(11),"static invocation");
  try{GameAccess.call(OWNER,"fails",owner,NONE);throw new AssertionError("exception lost");}catch(IllegalStateException e){require(e.getCause() instanceof InvocationTargetException&&e.getCause().getCause() instanceof IllegalArgumentException,"original cause lost");}
  for(int i=0;i<200000;i++)step(owner,i&15);
  com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
  long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id),time=System.nanoTime(),sum=0;int loops=600000;
  for(int i=0;i<loops;i++)sum+=step(owner,i&15);
  long nanos=System.nanoTime()-time,bytes=bean.getThreadAllocatedBytes(id)-before;sink=sum;
  CountDownLatch start=new CountDownLatch(1);List<Future<?>> jobs=new ArrayList<>();ExecutorService threads=Executors.newFixedThreadPool(6);
  for(int t=0;t<6;t++)jobs.add(threads.submit(()->{try{start.await();Owner local=new Owner();for(int i=0;i<12000;i++){GameAccess.set(OWNER,"value",local,i&15);require(GameAccess.call(OWNER,"method",local,INT,1).equals((i&15)+1),"concurrent data mismatch");require(GameAccess.field(OWNER,"fixed",null).equals(11),"static field corruption");}}catch(Exception e){throw new RuntimeException(e);}}));
  start.countDown();for(Future<?> job:jobs)job.get();threads.shutdown();
  System.out.println("GameAccess087 semantics PASS: private/static/overloads/inheritance/recursive initialisation, exception cause, six concurrent callers.");
  System.out.printf(Locale.ROOT,"BENCH087 {\"loops\":%d,\"nanoseconds\":%d,\"allocatedBytes\":%d,\"nsPerTriplet\":%.2f,\"bytesPerTriplet\":%.2f}%n",loops,nanos,bytes,(double)nanos/loops,(double)bytes/loops);
 }
 static int step(Owner o,int v){GameAccess.set(OWNER,"value",o,v);return ((Number)GameAccess.field(OWNER,"value",o)).intValue()+((Number)GameAccess.call(OWNER,"method",o,INT,1)).intValue();}
 static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
