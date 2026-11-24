package vpiso;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Snapshot pooled batch entries before publication; retain no native world objects. */
public final class NativeTreeSources {
    private static final String BATCH="zombie.iso.fboRenderChunk.FBORenderTrees";
    private static final String TREE=BATCH+"$Tree";
    private record Entry(Object texture,Object second,float x,float y,float z,NativeSource source) {}
    private static final Map<Object,Entry> SOURCES=Collections.synchronizedMap(new WeakHashMap<>());
    public static Object batch() {return GameAccess.field(BATCH,"current",null);}
    public static int size(Object batch) {return batch==null?0:records(batch).size();}
    @SuppressWarnings("unchecked")
    private static List<Object> records(Object batch) {return (List<Object>)GameAccess.field(BATCH,"trees",batch);}
    public static void captured(Object batch,int start,zombie.iso.IsoObject object) {
        if(batch==null || batch()!=batch)return;
        Object square=GameAccess.call("zombie.iso.IsoObject","getSquare",object,new Class<?>[0]);
        if(square==null)return;
        int index=((List<?>)GameAccess.call("zombie.iso.IsoGridSquare","getObjects",square,new Class<?>[0])).indexOf(object);
        float[] key=NativeSource.ownerKey((int)number("zombie.iso.IsoGridSquare","x",square),
                (int)number("zombie.iso.IsoGridSquare","y",square),(int)number("zombie.iso.IsoGridSquare","z",square),index);
        List<Object> records=records(batch);
        for(int i=start;i<records.size();i++) {
            Object record=records.get(i);NativeSource source=NativeSource.tree(record);
            if(source==null)continue;
            source=source.withOwner(key);
            SOURCES.put(record,new Entry(GameAccess.field(TREE,"texture",record),GameAccess.field(TREE,"texture2",record),
                    number(TREE,"x",record),number(TREE,"y",record),number(TREE,"z",record),source));
        }
    }
    public static NativeSource source(Object record) {
        Entry entry=SOURCES.get(record);
        if(entry!=null && entry.texture==GameAccess.field(TREE,"texture",record) && entry.second==GameAccess.field(TREE,"texture2",record)
                && entry.x==number(TREE,"x",record) && entry.y==number(TREE,"y",record) && entry.z==number(TREE,"z",record))return entry.source;
        return NativeSource.tree(record);
    }
    private static float number(String type,String name,Object object) {return ((Number)GameAccess.field(type,name,object)).floatValue();}
    private NativeTreeSources() {}
}
