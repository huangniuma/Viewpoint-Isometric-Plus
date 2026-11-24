import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.sun.management.ThreadMXBean;
import vpiso.*;

/** Delayed queue consumers must see immutable original payloads. */
public final class CommandQueue107Probe {
    static final ArrayList<Object> queue=new ArrayList<>();
    public static void accept(Object value){queue.add(value);}
    public static void main(String[] args)throws Exception {
        boolean baseline=args[0].equals("baseline");
        var bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
        long id=Thread.currentThread().getId();int count=4096;queue.ensureCapacity(count*2);
        for(int i=0;i<count;i++){NativeGround.queueFloor((i%64)-32f);NativeGround.queueSourceEnd();}
        queue.clear();
        long start=bean.getThreadAllocatedBytes(id);
        for(int i=0;i<count;i++){NativeGround.queueFloor((i%64)-32f);NativeGround.queueSourceEnd();}
        long bytes=bean.getThreadAllocatedBytes(id)-start;
        MessageDigest hash=MessageDigest.getInstance("SHA-256");Set<Object> unique=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=0;i<queue.size();i++) {
            Object command=queue.get(i);unique.add(command);String type=command.getClass().getName();
            int operation=((Number)GameAccess.field(type,"operation",command)).intValue();
            float level=((Number)GameAccess.field(type,"level",command)).floatValue();
            require(operation==(i%2==0?6:9),"Command order changed");
            require(Float.floatToRawIntBits(level)==Float.floatToRawIntBits(i%2==0?((i/2)%64)-32f:0f),"Queued value changed before consumption");
            hash.update((operation+":"+Float.floatToRawIntBits(level)+";").getBytes(StandardCharsets.UTF_8));
        }
        int expected=baseline?count*2:65;require(unique.size()==expected,"Immutable command identity count="+unique.size());
        queue.clear();for(float f:new float[]{-32.5f,-33,32,33,-0f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MIN_VALUE})NativeGround.queueFloor(f);
        float[] values={-32.5f,-33,32,33,-0f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MIN_VALUE};
        for(int i=0;i<values.length;i++)require(Float.floatToRawIntBits(((Number)GameAccess.field(queue.get(i).getClass().getName(),"level",queue.get(i))).floatValue())==Float.floatToRawIntBits(values[i]),"Uncached level changed");
        System.out.println("COMMAND107 PASS entries="+count*2+" sha256="+HexFormat.of().formatHex(hash.digest())+"; exact delayed floor/end payloads, signed zero/noninteger/nonfinite fallbacks.");
        System.out.println("COMMAND107 ALLOC mode="+args[0]+" bytes="+bytes+" immutableObjects="+unique.size()+"; queue interception only, no game FPS.");
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
