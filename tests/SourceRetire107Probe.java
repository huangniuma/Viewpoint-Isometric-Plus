import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.sun.management.ThreadMXBean;
import vpiso.*;

/** Actual source retirement queue and tables, CPU only; no GL, game or input capture. */
public final class SourceRetire107Probe {
    static final ArrayDeque<Integer> free=(ArrayDeque<Integer>)GameAccess.field("vpiso.NativeGround","SOURCE_FREE",null);
    static final Map<String,Integer> keys=(Map<String,Integer>)GameAccess.field("vpiso.NativeGround","SOURCE_KEYS",null);
    static final String[] names=(String[])GameAccess.field("vpiso.NativeGround","SOURCE_NAMES",null);
    static final int[] refs=(int[])GameAccess.field("vpiso.NativeGround","SOURCE_REFS",null);
    static final float[] data=(float[])GameAccess.field("vpiso.NativeGround","SOURCE_DATA",null);
    static final MethodHandle collect;
    static final Method release;
    static final Constructor<?> surface;
    static { try {
        var m=NativeGround.class.getDeclaredMethod("collectSources");m.setAccessible(true);collect=MethodHandles.lookup().unreflect(m);
        Class<?> s=Class.forName("vpiso.NativeGround$Surface");surface=s.getDeclaredConstructor();surface.setAccessible(true);
        release=NativeGround.class.getDeclaredMethod("releaseWalls",s);release.setAccessible(true);
    } catch(Exception e){throw new ExceptionInInitializerError(e);} }
    public static void main(String[] args)throws Throwable {
        if(args.length>0 && args[0].equals("bench")){bench(Integer.parseInt(args[1]));return;}
        MessageDigest hash=MessageDigest.getInstance("SHA-256");int cases=0;
        for(int count:new int[]{0,1,32,1024,8192}) {
            reset();for(int i=128;i<128+count;i++)free.add(i);
            for(int i=0;i<5;i++){collect.invokeExact();state(hash);cases++;}
            Random random=new Random(107);
            for(int step=0;step<32;step++) {
                ArrayList<Integer> owned=new ArrayList<>();
                for(int j=0;j<20;j++) {
                    int id=free.isEmpty()?10000+step*20+j:free.removeFirst();
                    require(id>0,"Available queue exposed retirement");
                    names[id]="owner-"+id;keys.put(names[id],id);refs[id]=2;owned.add(id);
                    for(int plane=0;plane<3;plane++)for(int c=0;c<4;c++)data[(plane*65536+id)*4+c]=id+plane*.25f+c*.0625f;
                }
                Object a=surface.newInstance(),b=surface.newInstance();
                ((Set<Integer>)GameAccess.field(a.getClass().getName(),"sources",a)).addAll(owned);
                ((Set<Integer>)GameAccess.field(b.getClass().getName(),"sources",b)).addAll(owned);
                release.invoke(null,a);collect.invokeExact();state(hash);cases++;
                release.invoke(null,b);
                // A cached object can be repainted after clear and before finish.
                for(int id:owned)if(random.nextInt(4)==0)refs[id]++;
                state(hash);collect.invokeExact();state(hash);cases++;
                collect.invokeExact();state(hash);cases++;
                // Retire the repainted references, as a later cached destination does.
                Object c=surface.newInstance();Set<Integer> source=(Set<Integer>)GameAccess.field(c.getClass().getName(),"sources",c);
                for(int id:owned)if(refs[id]>0)source.add(id);
                release.invoke(null,c);collect.invokeExact();state(hash);cases++;
            }
            java.nio.ByteBuffer complete=java.nio.ByteBuffer.allocate(data.length*4);
            for(float value:data)complete.putInt(Float.floatToIntBits(value));
            hash.update(complete.array());
        }
        System.out.println("RETIRE107 PASS cases="+cases+" sha256="+HexFormat.of().formatHex(hash.digest())+"; actual queue order, retained/repainted sources, refs, source keys/data/revisions, deferred collection and reuse; CPU only.");
    }
    static void reset() {
        free.clear();keys.clear();Arrays.fill(names,null);Arrays.fill(refs,0);Arrays.fill(data,0);
        GameAccess.set("vpiso.NativeGround","wallRevision",null,0L);
        Arrays.fill((long[])GameAccess.field("vpiso.NativeGround","OWNER_REVISIONS",null),0L);
        try{GameAccess.set("vpiso.NativeGround","sourcesPending",null,false);}catch(IllegalStateException absent){if(!(absent.getCause() instanceof NoSuchFieldException))throw absent;}
    }
    static void state(MessageDigest hash) {
        StringBuilder b=new StringBuilder();for(int id:free)b.append(id).append(',');
        for(int i=0;i<names.length;i++)if(names[i]!=null || refs[i]!=0) {
            b.append(i).append(':').append(names[i]).append(':').append(refs[i]).append(':').append(keys.get(names[i])).append(';');
            for(int plane=0;plane<3;plane++)for(int c=0;c<4;c++)b.append(Float.floatToIntBits(data[(plane*65536+i)*4+c])).append(',');
        }
        b.append(Arrays.toString((long[])GameAccess.field("vpiso.NativeGround","OWNER_REVISIONS",null)));
        hash.update(b.toString().getBytes(StandardCharsets.UTF_8));
    }
    static void bench(int count)throws Throwable {
        reset();for(int i=128;i<128+count;i++)free.add(i);
        for(int i=0;i<100;i++)collect.invokeExact();
        var bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);bean.setThreadCpuTimeEnabled(true);
        long thread=Thread.currentThread().getId(),alloc=bean.getThreadAllocatedBytes(thread),cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();
        for(int i=0;i<1000;i++)collect.invokeExact();
        long elapsed=System.nanoTime()-start,used=bean.getThreadAllocatedBytes(thread)-alloc,time=bean.getCurrentThreadCpuTime()-cpu;
        require(free.size()==count,"Available count changed");int next=128;for(int id:free)require(id==next++,"Available order changed");
        System.out.println("RETIRE107 BENCH "+count+" ns="+elapsed/1000+" cpu_ns="+time/1000+" alloc="+used/1000+"; available-only,100warmup/1000calls, no live FPS claim.");
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
