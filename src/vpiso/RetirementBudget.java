package vpiso;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.LongSupplier;
import viewpoint.core.Frame;
import viewpoint.platform.Pool;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.Retirement;

/** Splits only already signalled batches, in VP's own queue; no early release or slot reuse. */
public final class RetirementBudget {
    private static final int PER_FRAME = 16, MAX_PENDING = 256;
    private static final long BUDGET_NS = 250_000L, MAX_WAIT_NS = 125_000_000L;
    private static final ThreadLocal<Frame> SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<Long> KEEP_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE = ThreadLocal.withInitial(() -> false);
    private static long lastNumber = Long.MIN_VALUE, spent, waitingSince;
    private static int freed;
    private static volatile Object waitingBatch;
    private static boolean inside;
    private static LongSupplier clock = System::nanoTime;
    private static final ClassValue<Api> APIS = new ClassValue<>() {
        protected Api computeValue(Class<?> type) { return new Api(type); }
    };
    public static Frame enter(Object drawer) {
        Frame previous = SCOPE.get();
        if (Main.compatible) SCOPE.set((Frame) GameAccess.field(drawer.getClass().getName(), "frame", drawer));
        return previous;
    }
    public static void leave(Frame previous) {
        if (previous == null) SCOPE.remove(); else SCOPE.set(previous);
    }
    public static Long enterKeep(Object job) {
        Long previous = KEEP_SCOPE.get();
        // This existing readiness predicate has no frame-generation toggle requirement.
        if (Main.compatible && ShaderPacks.active() != null && IsometricBridge.frameGenerationAllowed())
            KEEP_SCOPE.set(((Number)GameAccess.field(job.getClass().getName(), "number", job)).longValue());
        else KEEP_SCOPE.remove();
        return previous;
    }
    public static void leaveKeep(Long previous) {
        if (previous == null) KEEP_SCOPE.remove(); else KEEP_SCOPE.set(previous);
    }
    public static boolean collect(Object owner) {
        if (inside) return true;
        if (FORCE.get()) return false;
        Frame frame = SCOPE.get();
        if (frame == null) frame = IsometricBridge.renderFrame();
        Long kept = KEEP_SCOPE.get();
        if ((frame == null && kept == null) || !Main.compatible) {
            // The original collector will own/drain this queue. Keep no reference
            // to a previously deferred batch after it has been freed there.
            waitingBatch = null; waitingSince = 0;
            return false;
        }
        Api api = APIS.get(owner.getClass());
        if (owner != api.singleton()) return false;
        collect(owner, kept == null ? frame.number : kept, clock.getAsLong(), pressure());
        return true;
    }
    /** CPU oracle entry uses the same private VP queues and fence interface. Render thread only. */
    static void collect(Object owner, Frame frame, long now, boolean force) {
        collect(owner, frame.number, now, force);
    }
    private static void collect(Object owner, long number, long now, boolean force) {
        Api api = APIS.get(owner.getClass());
        if (lastNumber != number) {
            lastNumber = number; freed = 0; spent = 0;
        }
        ArrayDeque<?> batches = api.batches(owner);
        if (batches.size() >= 63 || api.pending(owner) >= MAX_PENDING) force = true;
        if (waitingBatch != batches.peekFirst()) { waitingBatch = batches.peekFirst(); waitingSince = now; }
        if (waitingBatch != null && now - waitingSince >= MAX_WAIT_NS) force = true;
        Object fences = api.fences(owner);
        inside = true;
        try {
            while (!batches.isEmpty()) {
                Object batch = batches.peekFirst();
                if (!api.passed(fences, api.fence(batch))) return;
                ArrayList<?> things = api.things(batch);
                int attempted = 0;
                try {
                    while (attempted < things.size()) {
                        if (!force && (freed >= PER_FRAME || spent >= BUDGET_NS)) return;
                        Object thing = things.get(attempted++);
                        long began = clock.getAsLong();
                        try { ((Retirement.Freeable) thing).free(); }
                        finally { spent += Math.max(0L, clock.getAsLong() - began); freed++; }
                    }
                } finally {
                    // Remove even a throwing attempt: never invoke a partially executed free twice.
                    if (attempted != 0) things.subList(0, attempted).clear();
                    if (things.isEmpty()) {
                        batches.removeFirst(); api.delete(fences, api.fence(batch));
                        waitingBatch = batches.peekFirst(); waitingSince = now;
                    }
                }
            }
        } finally { inside = false; }
    }
    /** Uses the original collector, including its fence test. Never waits for an unsignalled fence. */
    public static void forceReady() {
        if (inside || FORCE.get()) return;
        FORCE.set(true);
        try { Retirement.collect(); }
        finally { FORCE.remove(); waitingBatch = null; waitingSince = 0; }
    }
    public static void beforeMesh(int vertices) {
        if (!Main.compatible || waitingBatch == null) return;
        @SuppressWarnings("unchecked") Map<Integer,Integer> free = (Map<Integer,Integer>)GameAccess.field("viewpoint.render.MeshArena", "free", null);
        for (int count : free.values()) if (count >= vertices) return;
        forceReady();
    }
    public static void beforeSlot() {
        if (!Main.compatible || waitingBatch == null) return;
        var slots = (ArrayDeque<?>)GameAccess.field("viewpoint.render.MeshArena", "freeSlots", null);
        int next = ((Number)GameAccess.field("viewpoint.render.MeshArena", "nextSlot", null)).intValue();
        int rows = ((Number)GameAccess.field("viewpoint.render.MeshArena", "rows", null)).intValue();
        if (slots.isEmpty() && next >= rows * 204) forceReady();
    }
    public static void beforeModels() {
        if (Main.compatible && waitingBatch != null && !((java.util.Queue<?>)GameAccess.field("viewpoint.render.PackModels", "READ", null)).isEmpty()) forceReady();
    }
    public static void contextEnding() {
        if (Main.compatible && waitingBatch != null
                && GameAccess.field("zombie.core.opengl.RenderThread", "contextThread", null) == Thread.currentThread()
                && ((Number)GameAccess.field("zombie.core.opengl.RenderThread", "contextLockReentrantDepth", null)).intValue() == 1)
            forceReady();
    }
    public static void cachesReset() {
        if (Main.compatible && waitingBatch != null)
            zombie.core.opengl.RenderThread.queueInvokeOnRenderContext(RetirementBudget::forceReady);
    }
    private static boolean pressure() {
        return Pool.NEAR_MESHES.over(.9) || Pool.LIGHT_GRIDS.over(.9) || Pool.CPU.over(.9)
                || Pool.PACKS.over(.9) || Pool.MODEL_MESHES.over(.9);
    }
    private static final class Api {
        final Field singleton, batches, fences;
        final MethodHandle fence, things, passed, delete, pending;
        Api(Class<?> type) {
            try {
                singleton = field(type,"ON_GPU"); batches = field(type,"fenced"); fences = field(type,"fences");
                Class<?> batch = Class.forName(type.getName()+"$Fenced", false, type.getClassLoader());
                Class<?> fenceType = Class.forName(type.getName()+"$Fences", false, type.getClassLoader());
                fence = method(batch,"fence"); things = method(batch,"things");
                passed = method(fenceType,"passed",long.class); delete = method(fenceType,"delete",long.class);
                pending = method(type,"pending");
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported VP retirement ABI",error); }
        }
        Object singleton() { try { return singleton.get(null); } catch (IllegalAccessException e) {throw new IllegalStateException(e);} }
        ArrayDeque<?> batches(Object o) { try {return (ArrayDeque<?>)batches.get(o);}catch(IllegalAccessException e){throw new IllegalStateException(e);} }
        Object fences(Object o) {try{return fences.get(o);}catch(IllegalAccessException e){throw new IllegalStateException(e);} }
        long fence(Object o) {try{return (long)fence.invoke(o);}catch(Throwable t){throw rethrow(t);} }
        ArrayList<?> things(Object o) {try{return (ArrayList<?>)things.invoke(o);}catch(Throwable t){throw rethrow(t);} }
        boolean passed(Object o,long f) {try{return (boolean)passed.invoke(o,f);}catch(Throwable t){throw rethrow(t);} }
        void delete(Object o,long f) {try{delete.invoke(o,f);}catch(Throwable t){throw rethrow(t);} }
        int pending(Object o) {try{return (int)pending.invoke(o);}catch(Throwable t){throw rethrow(t);} }
        static Field field(Class<?> c,String n)throws ReflectiveOperationException {Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
        static MethodHandle method(Class<?> c,String n,Class<?>...p)throws ReflectiveOperationException {Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return MethodHandles.lookup().unreflect(m);}
    }
    private static RuntimeException rethrow(Throwable t) {if(t instanceof Error e)throw e;if(t instanceof RuntimeException e)return e;return new IllegalStateException(t);}
    private RetirementBudget() {}
}
