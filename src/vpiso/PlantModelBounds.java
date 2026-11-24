package vpiso;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/** Cached packed vegetation's own bounds; only unused instance metadata is written. */
public final class PlantModelBounds {
    private record Owner(int object,int x,int y,int z) {}
    public static void cooked(Object cooker,List<?> ops) {
        float[] data=(float[])GameAccess.field("viewpoint.world.Cook$Cooker","instances",cooker);
        int count=((Number)GameAccess.field("viewpoint.world.Cook$Cooker","modelFloats",cooker)).intValue()/14;
        int[] objects=(int[])GameAccess.field("viewpoint.world.Cook$Cooker","modelObjects",cooker);
        // Cook can run before PackModels publishes its render-thread index.
        // The recipe already holds each real model, so retain that ownership.
        Map<Integer,float[]> models=new HashMap<>();
        if(ops!=null)for(Object op:ops) {
            int flags=((Number)GameAccess.field("viewpoint.world.Recipe$Op","flags",op)).intValue();
            if((flags&(TreeModelScope.TREE|TreeModelScope.BUSH))==0)continue;
            Object model=GameAccess.field("viewpoint.world.Recipe$Op","model",op);
            if(model!=null)models.put(((Number)GameAccess.field("viewpoint.render.PackModel","index",model)).intValue(),
                    (float[])GameAccess.call("viewpoint.render.PackModel","bounds",model,new Class<?>[0]));
        }
        Map<Owner,float[]> groups=new HashMap<>();
        Owner[] owners=new Owner[count];
        for(int i=0;i<count;i++) {
            int t=i*14,flags=Float.floatToRawIntBits(data[t+6]);
            if((flags&(TreeModelScope.TREE|TreeModelScope.BUSH))==0 || (flags&512)!=0)continue;
            float[] bounds=models.get(Float.floatToRawIntBits(data[t+8]));
            if(bounds==null || bounds.length!=6)continue;
            // ObjectSpans supplies the native owner. When it is unavailable, only
            // exactly colocated plant parts share bounds; never group neighbours.
            int object=objects==null || i>=objects.length?-1:objects[i];
            Owner owner=object>=0?new Owner(object,0,0,0):new Owner(-1,
                    Float.floatToIntBits(data[t]),Float.floatToIntBits(data[t+1]),Float.floatToIntBits(data[t+2]));
            owners[i]=owner;
            float[] union=groups.computeIfAbsent(owner,k->new float[]{Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY});
            for(int corner=0;corner<8;corner++) {
                float x=bounds[(corner&1)==0?0:3]*data[t+5],y=bounds[(corner&2)==0?1:4]*data[t+5],z=bounds[(corner&4)==0?2:5]*data[t+5];
                float[] p={data[t]+x*data[t+3]-z*data[t+4],data[t+1]+y,data[t+2]+x*data[t+4]+z*data[t+3]};
                for(int axis=0;axis<3;axis++){union[axis]=Math.min(union[axis],p[axis]);union[axis+3]=Math.max(union[axis+3],p[axis]);}
            }
        }
        for(int i=0;i<count;i++)if(owners[i]!=null) {
            int t=i*14;float[] b=groups.get(owners[i]);
            float x=Math.max(Math.abs(b[0]-data[t]),Math.abs(b[3]-data[t]));
            float z=Math.max(Math.abs(b[2]-data[t+2]),Math.abs(b[5]-data[t+2]));
            float r=(float)Math.hypot(x,z),lo=b[1]-data[t+1],hi=b[4]-data[t+1];
            if(Float.isFinite(r)&&Float.isFinite(lo)&&Float.isFinite(hi)&&r>1.0e-4f&&hi-lo>1.0e-4f) {
                data[t+10]=r;data[t+11]=lo;data[t+12]=hi;data[t+13]=1;
            }
        }
        // The14-float ABI already reserves this metadata field. Its integer part
        // remains the crown-bounds flag; the exact binary fraction transports the
        // original ObjectSpans token without changing any geometry or material.
        for(int i=0;i<count;i++)if(objects!=null && i<objects.length && objects[i]>=0 && objects[i]<0x400000)
            data[i*14+13]+=(objects[i]+1)/8388608f;
    }
    private PlantModelBounds() {}
}
