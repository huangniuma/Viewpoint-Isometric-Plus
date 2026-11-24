import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import static net.bytebuddy.matcher.ElementMatchers.named;

/** Actual VP bytecode and production advice, without launching or writing a game world. */
public final class SkeletonCompatibilityProbe {
    private static final String PORT = "viewpoint.sim.SkeletonPort";
    private static final String[] PATCHES = {"Patch_SkeletonAnimated", "Patch_SkeletonUpdating", "Patch_SkeletonUnmodelled"};
    private static final String[] METHODS = {"animated", "updating", "updatingUnmodelled"};

    public static void main(String[] args) throws Exception {
        Map<String, byte[]> vp = readJar(Path.of(args[0]), "viewpoint/sim/");
        Loader baseline = new Loader(vp);
        reserve(baseline);
        expectNullSlot(baseline);
        System.out.println("030 baseline reproduced: actual SkeletonPort missing-slot NullPointerException.");

        Map<String, byte[]> previous = readJar(Path.of(args[2]), "vpiso/");
        Loader old = weave(vp, previous,
                new String[]{PATCHES[0], PATCHES[1], PATCHES[2], "Patch_SkeletonClear"},
                new String[]{METHODS[0], METHODS[1], METHODS[2], "clear"});
        field(old.loadClass("vpiso.Main"), "compatible").setBoolean(null, true);
        reserve(old);
        invoke(old, "animated");
        Method fallback = old.loadClass("vpiso.SkeletonBatchRecovery").getMethod("nativeAnimation");
        require((Boolean) fallback.invoke(null), "031 fixture failed to enter recovery");
        invoke(old, "clear"); // FP.viewOff calls Characters.forget / clear every native frame.
        require(!(Boolean) fallback.invoke(null), "031 clear did not reproduce the live reset flaw");
        System.out.println("031 regression reproduced: ordinary native-frame clear re-enables the failed skeleton port.");

        Map<String, byte[]> production = new HashMap<>();
        for (String name : new String[]{"Main", PATCHES[0], PATCHES[1], PATCHES[2]})
            production.put("vpiso." + name, Files.readAllBytes(Path.of(args[1], "vpiso", name + ".class")));
        Loader fixed = weave(vp, production, PATCHES, METHODS);
        Class<?> main = fixed.loadClass("vpiso.Main");
        field(main, "compatible").setBoolean(null, true);
        Class<?> port = fixed.loadClass(PORT);
        require(count(fixed) == 0 && get(port, "pending", null) == null, "Dirty startup state");
        // Even if VP's saved optimization switch was already on, neither producer may acquire data.
        field(port, "on").setBoolean(null, true);
        Class<?> playerType = Class.forName("zombie.core.skinnedmodel.animation.AnimationPlayer");
        Object player = allocate(playerType);
        field(playerType, "updateBones").setBoolean(player, true);
        Object zombie = allocate(Class.forName("zombie.characters.IsoZombie"));
        Class<?> modelSlot = Class.forName("zombie.core.skinnedmodel.ModelManager$ModelSlot");
        Class<?> matrix = Class.forName("org.lwjgl.util.vector.Matrix4f");
        Object transform = matrix.getConstructor().newInstance();
        float[] rows = {7f, 8f, 9f};
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread updating = new Thread(() -> {
            try {
                for (int i = 0; i < 10000; i++) {
                    // A null model slot would crash inside VP if its acquisition advice did not skip.
                    invoke(fixed, "updating", new Class<?>[]{modelSlot}, new Object[]{null});
                    invoke(fixed, "updatingUnmodelled", new Class<?>[]{Object.class, playerType}, new Object[]{zombie, player});
                    invoke(fixed, "updated");
                    invoke(fixed, "animated");
                    invoke(fixed, "read", new Class<?>[]{playerType}, new Object[]{player});
                    invoke(fixed, "modelsRead", new Class<?>[]{playerType}, new Object[]{player});
                    require(!(Boolean) invoke(fixed, "rows", new Class<?>[]{playerType, float[].class}, new Object[]{player, rows}), "VP took native bone rows");
                    require(!(Boolean) invoke(fixed, "model", new Class<?>[]{playerType, int.class, matrix}, new Object[]{player, 0, transform}), "VP took native model matrices");
                }
            } catch (Throwable error) { failure.compareAndSet(null, error); }
        }, "animation-steps");
        Thread clearing = new Thread(() -> {
            try { for (int i = 0; i < 10000; i++) invoke(fixed, "clear"); }
            catch (Throwable error) { failure.compareAndSet(null, error); }
        }, "native-frame-clears");
        updating.start(); clearing.start();
        updating.join(20000); clearing.join(20000);
        require(!updating.isAlive() && !clearing.isAlive(), "Interleaving test exceeded its bound");
        if (failure.get() != null) throw new AssertionError("Concurrent native-animation compatibility failed", failure.get());
        require(count(fixed) == 0, "VP reserved a skeleton batch");
        Object slots = get(port, "slots", null);
        Method size = slots.getClass().getDeclaredMethod("size"); size.setAccessible(true);
        require((Integer) size.invoke(slots) == 0, "VP acquired native animation ownership");
        require(get(port, "pending", null) == null && get(port, "thinned", null) == null, "VP retained an animation reservation");
        require(field(playerType, "updateBones").getBoolean(player), "Native bone updates were disabled");
        require(java.util.Arrays.equals(rows, new float[]{7f, 8f, 9f}), "Native rows were overwritten");
        require((Long) get(port, "step", null) == 0, "Optional animation job ran");
        System.out.println("Native skeleton compatibility PASS: 10000 animation steps plus 10000 concurrent native-frame clears; no slots, pending poses, batch jobs or native-bone suppression; native row/model fallback preserved.");

        // As elsewhere in this addon, unsupported binaries retain original behavior.
        field(main, "compatible").setBoolean(null, false);
        reserve(fixed);
        expectNullSlot(fixed);
        System.out.println("Unsupported fingerprint passthrough PASS; no world/settings writes.");
    }

    private static Loader weave(Map<String, byte[]> vp, Map<String, byte[]> addon, String[] patches, String[] methods) throws Exception {
        Map<String, byte[]> converted = new HashMap<>();
        Method convert = PatchWeavingProbe.class.getDeclaredMethod("convert", byte[].class); convert.setAccessible(true);
        for (String name : patches) converted.put("vpiso." + name, (byte[]) convert.invoke(null, (Object) addon.get("vpiso." + name)));
        Loader advice = new Loader(converted);
        var locator = new ClassFileLocator.Compound(new ClassFileLocator.Simple(converted), ClassFileLocator.ForClassLoader.of(SkeletonCompatibilityProbe.class.getClassLoader()));
        var woven = new ByteBuddy().redefine(Class.forName(PORT), ClassFileLocator.ForClassLoader.of(SkeletonCompatibilityProbe.class.getClassLoader()));
        for (int i = 0; i < patches.length; i++) woven = woven.visit(Advice.to(advice.loadClass("vpiso." + patches[i]), locator).on(named(methods[i])));
        Map<String, byte[]> all = new HashMap<>(vp); all.putAll(addon); all.put(PORT, woven.make().getBytes());
        return new Loader(all);
    }
    private static Map<String, byte[]> readJar(Path path, String prefix) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            for (var entry : zip.stream().filter(e -> e.getName().startsWith(prefix) && e.getName().endsWith(".class")).toList())
                result.put(entry.getName().replace('/', '.').replaceAll("\\.class$", ""), zip.getInputStream(entry).readAllBytes());
        }
        return result;
    }
    private static void expectNullSlot(Loader loader) throws Exception {
        try { invoke(loader, "animated"); throw new AssertionError("Missing-slot baseline did not throw"); }
        catch (InvocationTargetException error) {
            require(error.getCause() instanceof NullPointerException && error.getCause().getMessage().contains("player"), "Unexpected baseline error: " + error.getCause());
        }
    }
    private static void reserve(Loader loader) throws Exception {
        Object batch = get(loader.loadClass(PORT), "batch", null);
        Method character = batch.getClass().getDeclaredMethod("character", int.class, int.class, int.class, int.class, int.class);
        character.setAccessible(true); character.invoke(batch, 0, 0, 0, 0, -1);
    }
    private static int count(Loader loader) throws Exception {
        Object batch = get(loader.loadClass(PORT), "batch", null);
        return (Integer) get(batch.getClass(), "count", batch);
    }
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type);
    }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static Object get(Class<?> type, String name, Object self) throws Exception { return field(type, name).get(self); }
    private static Object invoke(Loader loader, String name) throws Exception { return invoke(loader, name, new Class<?>[0], new Object[0]); }
    private static Object invoke(Loader loader, String name, Class<?>[] types, Object[] args) throws Exception {
        Method method = loader.loadClass(PORT).getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(null, args);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Loader extends ClassLoader {
        private final Map<String, byte[]> bytes;
        Loader(Map<String, byte[]> bytes) { super(SkeletonCompatibilityProbe.class.getClassLoader()); this.bytes = bytes; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) type = bytes.containsKey(name) ? defineClass(name, bytes.get(name), 0, bytes.get(name).length) : super.loadClass(name, false);
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
