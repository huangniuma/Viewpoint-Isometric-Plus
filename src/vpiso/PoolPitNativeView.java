package vpiso;

import java.lang.reflect.Method;

/** Native cutaways use the actual water's surface floor, without moving the swimmer or camera. */
public final class PoolPitNativeView implements AutoCloseable {
    private static final ThreadLocal<PoolPitNativeView> ACTIVE=new ThreadLocal<>();
    private static Method column;
    private static boolean unavailable,reported;
    private final PoolPitNativeView previous;
    private final Object state;
    private final float before,height;

    private PoolPitNativeView(Object state,float before,float height) {
        this.previous=ACTIVE.get();this.state=state;this.before=before;this.height=height;
        GameAccess.set("zombie.iso.IsoCamera$FrameState","camCharacterZ",state,height);
        ACTIVE.set(this);
    }

    public static PoolPitNativeView begin(Object cell,Object player) {
        if(!PoolPitCompatibility.loaded() || unavailable)return null;
        if(!(Boolean)GameAccess.call("zombie.characters.IsoGameCharacter","getVariableBoolean",player,
                new Class<?>[]{String.class},"PoolPitNoFall"))return null;
        try {
            if(column==null) {
                Class<?> lakes=Class.forName("poolpitvp.Lakes",false,PoolPitNativeView.class.getClassLoader());
                column=lakes.getDeclaredMethod("column",GameAccess.type("zombie.iso.IsoCell"),float.class,float.class,float.class,float[].class);
                column.setAccessible(true);
            }
            float z=GameAccess.coordinate(player,"getZ");
            float[] water=(float[])column.invoke(null,cell,GameAccess.coordinate(player,"getX"),GameAccess.coordinate(player,"getY"),z,new float[3]);
            if(water==null || water.length<3 || !Float.isFinite(water[0]) || !Float.isFinite(water[1])
                    || water[1]<=0f || z>water[0]+.001f || z<water[0]-water[1]-.05f
                    || Math.floor(z)>=water[0])return null;
            Object state=GameAccess.field("zombie.iso.IsoCamera","frameState",null);
            float before=((Number)GameAccess.field("zombie.iso.IsoCamera$FrameState","camCharacterZ",state)).floatValue();
            return new PoolPitNativeView(state,before,water[0]);
        } catch(ReflectiveOperationException error) {
            unavailable=true;
            if(!reported){reported=true;System.out.println("[VPISO] PoolPit native visibility adapter unavailable: "+error);}
            return null;
        }
    }

    public static float height(float physical) {
        PoolPitNativeView scope=ACTIVE.get();return scope==null?physical:scope.height;
    }

    @Override public void close() {
        try {GameAccess.set("zombie.iso.IsoCamera$FrameState","camCharacterZ",state,before);}
        finally {if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }
}
