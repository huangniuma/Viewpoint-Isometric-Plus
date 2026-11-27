import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.util.*;
import org.lwjgl.util.vector.Matrix4f;
import viewpoint.core.Frame;
import viewpoint.game.SimClock;
import viewpoint.render.ModelDraws;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningData;

/** CPU-only actual VP pose recording/job fixture. Does not create a GL context or attach to a game. */
public final class CharacterClock113Probe {
    private static final int BONES = 4;
    private static final long FIRST = 1_000_000_000L, SECOND = 1_100_000_000L;

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "stale" : args[0];
        require(Set.of("stale", "control", "candidate").contains(mode), "Unknown mode");
        AnimationPlayer player = (AnimationPlayer) allocate(AnimationPlayer.class);
        SkinningData skin = (SkinningData) allocate(SkinningData.class);
        field(SkinningData.class, "skeletonHierarchy").set(skin, List.of(-1, 0, 1, 2));
        field(AnimationPlayer.class, "skinningData").set(player, skin);
        IsoZombie subject = (IsoZombie) allocate(IsoZombie.class);
        Class<?> live = Class.forName("viewpoint.models.LivePoses");
        Method begin = method(Class.forName("viewpoint.models.CharacterRate"), "begin", long.class);
        Method pose = method(live, "pose", Frame.class, zombie.characters.IsoGameCharacter.class,
                AnimationPlayer.class, boolean.class);
        Frame first = frame(mode, FIRST), second = frame(mode, SECOND);
        record(first, player, subject, begin, pose, .2f);
        record(second, player, subject, begin, pose, .6f);

        Object renderClock = field(Class.forName("viewpoint.render.RenderClock"), "ON_RENDER").get(null);
        field(renderClock.getClass(), "running").setBoolean(renderClock, true);
        field(renderClock.getClass(), "stateNanos").setLong(renderClock, SECOND);
        field(renderClock.getClass(), "renderNanos").setLong(renderClock, FIRST + 50_000_000L);
        field(renderClock.getClass(), "drawnBefore").setLong(renderClock, FIRST);
        field(renderClock.getClass(), "step").setDouble(renderClock, 100_000_000d);
        Object baked = second.scene.models.baked;
        int count = field(baked.getClass(), "liveCount").getInt(baked);
        int[] lives = (int[]) field(baked.getClass(), "lives").get(baked);
        require(count == 1 && lives[1] == BONES, "No nonzero-bone live pose job");
        IntBuffer job = IntBuffer.allocate(8);
        Class<?> gpu = Class.forName("viewpoint.render.LivePoseGpu");
        method(gpu, "job", IntBuffer.class, int[].class, int.class, int.class, int.class, boolean.class)
                .invoke(null, job, lives, 0, lives[0], BONES, false);
        float share = Float.intBitsToFloat(job.get(4));
        require(Float.isFinite(share) && share >= 0 && share <= 1, "Unbounded live pose share");

        Map<Integer, float[]> records = records(gpu);
        float[] from = records.get(job.get(2)), to = records.get(job.get(3));
        require(from != null && to != null && from.length == BONES * 12, "Missing actual queued records");
        // Replay precisely live_blend.comp's row-wise mix, without executing GPU code.
        float[] blended = new float[BONES * 12];
        float maximum = 0;
        for (int i = 0; i < blended.length; i++) {
            blended[i] = from[i] * (1 - share) + to[i] * share;
            require(Float.isFinite(blended[i]), "Nonfinite bone field");
            maximum = Math.max(maximum, Math.abs(blended[i]));
        }
        float expectedShare = mode.equals("stale") ? 1 : .5f;
        float expectedX = mode.equals("stale") ? .6f : .4f;
        require(Math.abs(share - expectedShare) < .00001f, "Unexpected actual VP share: " + share);
        require(Math.abs(blended[3] - expectedX) < .00001f, "Unexpected interpolated root");
        require(maximum < 2, "Fixture produced a spike");
        System.out.printf(Locale.ROOT,
                "Clock113 %s PASS: bones=%d sim=%d,%d liveTimes=%.9f,%.9f share=%.6f rootX=%.6f maximum=%.6f%n",
                mode, BONES, first.simNanos, second.simNanos,
                Float.intBitsToFloat(lives[5]), Float.intBitsToFloat(lives[6]), share, blended[3], maximum);
        if (mode.equals("stale")) System.out.println(
                "Stale clock selects the latest pose; this fixture does not reproduce geometry spikes.");
        else System.out.println("Actual live-pose recording and jobs preserve the intended halfway pose.");
    }

    private static Frame frame(String mode, long now) throws Exception {
        // In the isolated JVM only, supply deterministic game frame-start state.
        Class<?> clock = SimClock.class;
        Object tracking = ((Field) field(clock, "TRACKING").get(null)).get(null);
        ((Field) field(clock, "START").get(null)).setLong(tracking, now);
        Frame frame = new Frame();
        frame.scene.reset();
        frame.number = now / 100_000_000L;
        if (mode.equals("control")) {
            frame.simNanos = SimClock.simulated();
            frame.startNanos = SimClock.frameStart();
            frame.lostNanos = SimClock.lost();
            frame.carryNanos = SimClock.reach();
        } else if (mode.equals("candidate")) {
            method(Class.forName("vpiso.IsometricBridge"), "captureClock", Frame.class).invoke(null, frame);
        }
        if (!mode.equals("stale")) {
            require(frame.simNanos == SimClock.simulated() && frame.simNanos == now, "Simulation clock stale");
            require(frame.startNanos == SimClock.frameStart(), "Frame-start clock stale");
            require(frame.lostNanos == SimClock.lost(), "Lost clock stale");
            require(frame.carryNanos == SimClock.reach(), "Carry clock stale");
        }
        return frame;
    }

    private static void record(Frame frame, AnimationPlayer player, IsoZombie subject,
                               Method begin, Method pose, float x) throws Exception {
        Matrix4f[] matrices = new Matrix4f[BONES];
        for (int i = 0; i < BONES; i++) {
            Matrix4f matrix = matrices[i] = new Matrix4f();
            matrix.m03 = x + i * .05f;
            matrix.m13 = i * .1f;
            matrix.m23 = -i * .02f;
        }
        field(AnimationPlayer.class, "modelTransforms").set(player, matrices);
        begin.invoke(null, frame.simNanos);
        int row = (Integer) pose.invoke(null, frame, subject, player, true);
        require(row >= 0, "Actual LivePoses.pose refused fixture");
        require(frame.scene.models.baked.count() == 0, "Unexpected skin expansion fixture");
    }

    private static Map<Integer, float[]> records(Class<?> gpu) throws Exception {
        Collection<?> uploads = (Collection<?>) field(gpu, "UPLOADS").get(null);
        Map<Integer, float[]> result = new HashMap<>();
        for (Object upload : uploads) {
            int row = field(upload.getClass(), "row").getInt(upload);
            int size = field(upload.getClass(), "floats").getInt(upload);
            float[] values = (float[]) field(upload.getClass(), "rows").get(upload);
            result.put(row, Arrays.copyOf(values, size));
        }
        require(result.size() == 2, "Expected two actual pose records, got " + result.size());
        return result;
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field(unsafe, "theUnsafe").get(null), type);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
