/** Adapts legacy fixture inputs; production metadata writers are never passed through here. */
public final class NativeMetadata074 {
    private static final boolean V2=constant("METADATA_FORMAT",1)>=2;

    private NativeMetadata074() {}

    public static boolean v2() {return V2;}
    public static int sourceOwners() {return constant("SOURCE_OWNERS",1024);}

    /** A wall fixture already encoded for the production version on the class path. */
    public static float[] wall(int id,float weight) {
        float square=(float)id*id;
        return new float[]{-weight,id*weight,(V2?1+square:-square)*weight,weight};
    }

    /**
     * Floor/generic mixtures and wall-only mixtures have an exact linear conversion.
     * Source moments can be re-encoded only when they identify one pure source.
     * Mixed source/other provenance cannot be recovered; keep those fixtures invalid.
     * Already encoded negative-X wall/source fixtures are accepted unchanged. This is
     * deliberately not a general idempotent converter for nonnegative-X metadata.
     */
    public static float[] convert(float[] legacy) {
        if(!V2)return legacy;
        if(legacy==null || legacy.length!=4)throw new IllegalArgumentException("Expected RGBA metadata");
        float r=legacy[0],g=legacy[1],b=legacy[2],a=legacy[3];
        if(!Float.isFinite(r) || !Float.isFinite(g) || !Float.isFinite(b) || !Float.isFinite(a) || a<0)
            return invalid(1);
        if(a==0)return r==0 && g==0 && b==0?legacy:invalid(1);
        if(r>=0) {
            // Old generic B is zero; its new paraboloid contribution is its R.
            // This also preserves deliberately mixed floor-level moments.
            if(r>a+.00001f || b<0)return invalid(a);
            return new float[]{r,g,b+r,a};
        }
        // New wall/source metadata has positive Q. Legacy malformed tuples with
        // this sign retain their original invalid coordinates rather than an owner guess.
        if(b>=0) {
            if(b==0 && Math.abs(g)<.000001f && near(r,-a))
                return new float[]{r,g,a,a}; // legacy blade sentinel
            return legacy;
        }
        if(near(r,-a))return new float[]{r,g,a-b,a};
        if(near(r,-2*a)) {
            double mean=(double)g/a;
            int id=(int)Math.round(mean);
            double variance=Math.abs(-(double)b/a-mean*mean);
            if(id>=0 && id<sourceOwners() && Math.abs(mean-id)<=.002 && variance<.25) {
                float x=-(2+(id&255)),y=id>>>8;
                return new float[]{x*a,y*a,(x*x+y*y)*a,a};
            }
        }
        return invalid(a);
    }

    private static boolean near(float a,float b) {return Math.abs(a-b)<=.00001f;}
    private static float[] invalid(float weight) {return new float[]{-weight,0,2*weight,weight};}
    private static int constant(String name,int fallback) {
        try {
            Class<?> ground=Class.forName("vpiso.NativeGround",false,NativeMetadata074.class.getClassLoader());
            return ground.getField(name).getInt(null);
        }catch(ClassNotFoundException | NoSuchFieldException absent) {
            return fallback;
        }catch(ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read native metadata constant "+name,failure);
        }
    }
}
