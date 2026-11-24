import java.lang.reflect.*;
import java.util.*;
import vpiso.*;

/** Independent stable comparator oracle for complete signed32-bit keys and equal-key insertion order. */
public final class RangeOrder107Probe {
    record Item(int kind,int first,int end,float alpha,int id) {}
    public static void main(String[] args)throws Exception {
        NativeObjectState state=new NativeObjectState();Class<?> sample=Class.forName("vpiso.NativeObjectState$Sample");
        Constructor<?> make=sample.getDeclaredConstructor(float.class,int.class);make.setAccessible(true);
        Method add=NativeObjectState.class.getDeclaredMethod("addRange",int.class,int.class,int.class,sample);add.setAccessible(true);
        Random random=new Random(107);int cases=0,entries=0;
        for(int pass=0;pass<120;pass++) {
            GameAccess.set("vpiso.NativeObjectState","rangeCount",state,0);
            int count=new int[]{0,1,17,1024,8192,3}[pass%6];ArrayList<Item> expected=new ArrayList<>();
            for(int i=0;i<count;i++) {
                int first=pass%3==0?new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE}[i%5]:pass%3==1?i%4:random.nextInt();
                Item item=new Item(random.nextInt(3),first,random.nextInt(),Float.intBitsToFloat(random.nextInt()),i);
                expected.add(item);add.invoke(state,item.kind,item.first,item.end,make.newInstance(item.alpha,item.id));
            }
            expected.sort(Comparator.comparingInt(Item::kind).thenComparingInt(Item::first));
            long[] keys=(long[])GameAccess.field("vpiso.NativeObjectState","rangeKeys",state);
            int[] payload=(int[])GameAccess.field("vpiso.NativeObjectState","rangePayload",state);Arrays.sort(keys,0,count);
            for(int i=0;i<count;i++) {
                long key=keys[i];int at=(int)(key&0x3fffffffL)*3;Item item=expected.get(i);
                if((int)((key^Long.MIN_VALUE)>>>62)!=item.kind || ((int)(key>>>30)^Integer.MIN_VALUE)!=item.first
                        || payload[at]!=item.end || payload[at+1]!=Float.floatToIntBits(item.alpha) || payload[at+2]!=item.id)
                    throw new AssertionError("Stable key/payload differs pass="+pass+" index="+i);
            }
            cases++;entries+=count;
        }
        System.out.println("RANGEORDER107 PASS cases="+cases+" entries="+entries+"; full signed first/int edges, all kinds, independent stable Comparator, exact original tie order and float payload, growth/shrink.");
    }
}
