import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.util.*;
import org.joml.Matrix4f;
import viewpoint.core.Frame;
import viewpoint.render.ModelDraws;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;

/** Exact VP temporal consumers under clock jumps; CPU-only and isolated. */
public final class TemporalJump113Probe {
    static final long NOW = 5_000_000_000_000L, STEP = 100_000_000L;
    public static void main(String[] args) throws Exception {
        run("normal", NOW - 2 * STEP, NOW - STEP, NOW);
        run("current-to-zero", NOW - STEP, NOW, 0);
        run("zero-to-current", 0, 0, NOW);
        run("zero-then-two-current", 0, NOW - STEP, NOW);
        run("paused-current", NOW, NOW, NOW);
        pace("normal", NOW - STEP, NOW);
        pace("current-to-zero", NOW, 0);
        pace("zero-to-current", 0, NOW);
        System.out.println("TemporalJump113 PASS: actual interval positions, clip rows and fractions remain bounded; no geometry spike reproduced.");
    }

    static void run(String label, long a, long b, long c) throws Exception {
        Class<?> rate = Class.forName("viewpoint.models.CharacterRate");
        Class<?> entryType = Class.forName("viewpoint.models.CharacterRate$Entry");
        Object entry = construct(entryType);
        Object trail = field(entryType, "trail").get(entry);
        Method add = method(trail.getClass(), "add", long.class, long.class,
                float.class, float.class, float.class, float.class);
        add.invoke(trail, 20L, a, 0f, 0f, 0f, 0f);
        add.invoke(trail, 22L, b, .1f, 0f, 0f, 0f);
        add.invoke(trail, 24L, c, .2f, 0f, 0f, 0f);
        field(entryType, "arrivedAt").setLong(entry, -100L);
        field(entryType, "updatedIn").setLong(entry, 24L);
        field(rate, "frame").setLong(null, 24L);
        @SuppressWarnings("unchecked") Map<Object, Object> entries =
                (Map<Object, Object>) field(rate, "entries").get(null);
        entries.clear();
        Object subject = allocate(IsoZombie.class);
        entries.put(subject, entry);
        Frame frame = new Frame(); frame.scene.reset(); frame.simNanos = c;
        frame.scene.models.add(null, null, null, new Matrix4f().translation(-.2f, 0, 0), -1, -1, 0, 3);
        Object slot = allocate(ModelSlotRenderData.class);
        field(ModelSlotRenderData.class, "finalScale").setFloat(slot, 1);
        Matrix4f place = (Matrix4f) field(Class.forName("viewpoint.models.ModelCapture"), "place").get(null);
        place.translation(-.2f, 0, 0).scale(-1.5f, 1.5f, 1.5f).rotateY((float) Math.PI);
        method(rate, "intervals", Frame.class, ModelSlotRenderData.class,
                zombie.characters.IsoGameCharacter.class, int.class,
                float.class, float.class, float.class, float.class)
                .invoke(null, frame, slot, subject, 0, .2f, 0f, 0f, 0f);

        Object intervals = frame.scene.models.intervals;
        float[] relative = new float[3];
        Method time = method(intervals.getClass(), "time", int.class, int.class);
        for (int i = 0; i < 3; i++) relative[i] = (Float) time.invoke(intervals, 0, i);
        float delay = (Float) method(intervals.getClass(), "delay", int.class).invoke(intervals, 0);
        setRenderClock(c);
        Class<?> clock = Class.forName("viewpoint.render.RenderClock");
        float share = (Float) method(clock, "across", float.class, float.class, float.class, float.class)
                .invoke(null, relative[0], relative[1], relative[2], delay);
        require(Float.isNaN(share) || Float.isFinite(share) && Math.abs(share) <= 1, "Unbounded interval share");
        Class<?> blend = Class.forName("viewpoint.render.ModelBlend");
        ModelDraws drawn = (ModelDraws) method(blend, "drawn", ModelDraws.class, long.class,
                float.class, float.class, float.class, float.class)
                .invoke(construct(blend), frame.scene.models, 24L, 0f, 0f, 0f, 1f);
        float[] values = (float[]) field(ModelDraws.class, "values").get(drawn);
        double max = 0;
        for (int i = 0; i < 16; i++) {
            require(Float.isFinite(values[i]), "Nonfinite final placement");
            max = Math.max(max, Math.abs(values[i]));
        }
        require(max < 3, "Fixture produced an unbounded final placement");

        Object baked = frame.scene.models.baked;
        Class<?> jobs = baked.getClass();
        method(jobs, "pose", int.class, int.class).invoke(baked, 1, 0);
        method(jobs, "clock", float.class, float.class, boolean.class).invoke(baked, 0f, delay, false);
        method(jobs, "track", int.class, int.class, float.class, float.class, float[].class, int.class)
                .invoke(baked, 0, 2, .5f, 1f, null, 0);
        method(jobs, "timed", int.class, int.class, int.class, float.class, float.class, float.class, boolean.class)
                .invoke(baked, 0, 2, 16, 1f, .25f, 1f, true);
        IntBuffer gpuJobs = IntBuffer.allocate(9);
        int[] poses = (int[]) field(jobs, "poses").get(baked);
        int[] tracks = (int[]) field(jobs, "tracks").get(baked);
        gpuJobs.put(poses, 0, 4).put(tracks, 0, 5);
        method(Class.forName("viewpoint.render.BakedClock"), "time", jobs, IntBuffer.class, int.class)
                .invoke(null, baked, gpuJobs, 4);
        int from = gpuJobs.get(4), to = gpuJobs.get(5);
        float fraction = Float.intBitsToFloat(gpuJobs.get(6));
        require(from >= 0 && from <= 30 && to >= 0 && to <= 30, "Clip row outside fixture's baked clip");
        require(Float.isFinite(fraction) && fraction >= 0 && fraction <= 1, "Unbounded baked mix fraction");
        System.out.printf(Locale.ROOT, "%s delay=%.6fs times=%s intervalShare=%s placementMax=%.3f clipRows=%d,%d clipShare=%.6f%n",
                label, delay, Arrays.toString(relative), share, max, from, to, fraction);
    }

    static void pace(String label, long oldTime, long newTime) throws Exception {
        Class<?> shownType = Class.forName("viewpoint.models.BakedPoses$Shown");
        Class<?> blendType = Class.forName("viewpoint.models.BakedBlend");
        Class<?> bakeType = Class.forName("viewpoint.models.BakedClips$Bake");
        Object shown = construct(shownType), previous = field(shownType, "now").get(shown);
        Object next = construct(blendType);
        Constructor<?> ctor = bakeType.getDeclaredConstructor(int.class, int.class, int.class, float.class, float[].class);
        ctor.setAccessible(true); Object bake = ctor.newInstance(0, 16, 1, 1f, new float[128]);
        for (Object blend : List.of(previous, next)) {
            field(blendType, "count").setInt(blend, 1);
            ((Object[]) field(blendType, "bakes").get(blend))[0] = bake;
            ((boolean[]) field(blendType, "loops").get(blend))[0] = true;
            ((float[]) field(blendType, "paces").get(blend))[0] = 1f;
        }
        ((float[]) field(blendType, "times").get(previous))[0] = .2f;
        ((float[]) field(blendType, "times").get(next))[0] = .3f;
        field(shownType, "shown").setBoolean(shown, true);
        field(shownType, "posedAt").setLong(shown, oldTime);
        method(shownType, "pace", blendType, long.class).invoke(shown, next, newTime);
        float speed = ((float[]) field(blendType, "paces").get(next))[0];
        require(Float.isFinite(speed) && speed >= 0 && speed <= 4, "Unbounded Shown pace");
        System.out.printf(Locale.ROOT, "pace %s delta=%.6fs speed=%.6f%n", label, (newTime-oldTime)*1e-9, speed);
    }

    static void setRenderClock(long time) throws Exception {
        Class<?> type = Class.forName("viewpoint.render.RenderClock");
        Object clock = field(type, "ON_RENDER").get(null);
        field(type, "running").setBoolean(clock, true);
        field(type, "stateNanos").setLong(clock, time);
        field(type, "renderNanos").setLong(clock, time - 50_000_000L);
        field(type, "drawnBefore").setLong(clock, time - 100_000_000L);
        field(type, "step").setDouble(clock, 100_000_000d);
    }
    static Object construct(Class<?> type) throws Exception { Constructor<?> c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance(); }
    static Object allocate(Class<?> type) throws Exception { Class<?> u=Class.forName("sun.misc.Unsafe");return u.getMethod("allocateInstance",Class.class).invoke(field(u,"theUnsafe").get(null),type); }
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static Method method(Class<?> type,String name,Class<?>... args)throws Exception{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
