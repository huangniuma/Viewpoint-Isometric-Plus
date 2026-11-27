import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import static net.bytebuddy.matcher.ElementMatchers.named;

/** Executes production scope Advice and ShadowPreload/GameAccess against a CPU-only game fixture. */
public final class PreloadScope115Probe {
    private static final String TARGET = "viewpoint.IsoUpkeep";
    private static final String PATCH = "vpiso.Patch_ShadowPreloadScope";
    private static final ThreadLocal<Plan> PLAN = new ThreadLocal<>();
    private static ThreadLocal<Object> cameras;
    private static ClassLoader loader;
    private static Method frame;
    private static int cases;

    private static final class Plan {
        boolean fallback;
        Object seen;
        int bodies, tails;
        Runnable during;
    }
    public static final class BodyFailure extends RuntimeException {}
    public static final class BodyError extends Error {}

    /** Called by the substituted body, after the actual production enter advice. */
    public static void body(Object character, long mode) {
        Plan plan = PLAN.get();
        if (plan == null) throw new AssertionError("Missing body fixture plan");
        plan.bodies++;
        plan.seen = cameras.get();
        require(plan.fallback == (plan.seen == null), "Body did not see expected active/fallback scope");
        if (plan.during != null) plan.during.run();
        if (mode == 2) throw new BodyFailure();
        if (mode == 3) throw new BodyError();
    }
    public static void tail() { PLAN.get().tails++; }

    /** The real VP Toggle getter is used; no settings registry or disk save is initialized. */
    public static Object fixtureToggle() {
        try {
            Class<?> type = Class.forName("viewpoint.platform.LiveSettings$Toggle");
            var constructor = type.getDeclaredConstructor(String.class, String.class, String.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance("scope115.fixture", "Fixture", "Fixture", true);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class Loader extends ClassLoader {
        private final Map<String, byte[]> definitions;
        Loader(Map<String, byte[]> definitions) {
            super(PreloadScope115Probe.class.getClassLoader());
            this.definitions = definitions;
        }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                byte[] bytes = definitions.get(name);
                type = bytes == null ? super.loadClass(name, false) : defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(type);
            return type;
        }
        @Override public InputStream getResourceAsStream(String name) {
            byte[] bytes = name.endsWith(".class")
                    ? definitions.get(name.substring(0, name.length() - 6).replace('/', '.')) : null;
            return bytes == null ? super.getResourceAsStream(name) : new ByteArrayInputStream(bytes);
        }
    }

    /** Preserve the installed target's class/method identity and descriptor; replace all world work. */
    private static byte[] cpuTarget(byte[] original) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return name.equals("ON") || name.equals("broken")
                        ? super.visitField(access, name, descriptor, signature, value) : null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("frame")) return null;
                require(descriptor.equals("(Lzombie/iso/IsoCell;Lzombie/characters/IsoGameCharacter;J)V"),
                        "Installed IsoUpkeep.frame ABI changed: " + descriptor);
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                method.visitCode();
                method.visitVarInsn(Opcodes.ALOAD, 1);
                method.visitVarInsn(Opcodes.LLOAD, 2);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "PreloadScope115Probe", "body", "(Ljava/lang/Object;J)V", false);
                method.visitVarInsn(Opcodes.LLOAD, 2);
                method.visitInsn(Opcodes.LCONST_1);
                method.visitInsn(Opcodes.LCMP);
                Label normal = new Label();
                method.visitJumpInsn(Opcodes.IFNE, normal);
                method.visitInsn(Opcodes.RETURN);
                method.visitLabel(normal);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "PreloadScope115Probe", "tail", "()V", false);
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                return null;
            }
            @Override public void visitEnd() {
                MethodVisitor method = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                method.visitCode();
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "PreloadScope115Probe", "fixtureToggle", "()Ljava/lang/Object;", false);
                method.visitTypeInsn(Opcodes.CHECKCAST, "viewpoint/platform/LiveSettings$Toggle");
                method.visitFieldInsn(Opcodes.PUTSTATIC, "viewpoint/IsoUpkeep", "ON", "Lviewpoint/platform/LiveSettings$Toggle;");
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                super.visitEnd();
            }
        }, ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }

    private static Class<?> type(String name) throws ClassNotFoundException { return loader.loadClass(name); }
    private static Field field(String name, String member) throws ReflectiveOperationException {
        Field field = type(name).getDeclaredField(member);
        field.setAccessible(true);
        return field;
    }
    private static void set(String name, String member, Object value) throws ReflectiveOperationException {
        field(name, member).set(null, value);
    }
    private static Object previousCamera() {
        try {
            Class<?> fitType = type("vpiso.IsoCameraMath$Fit");
            Object fit = fitType.getConstructor(double.class, double.class, float.class).newInstance(1d, 2d, 20f);
            return type("vpiso.ShadowPreload$Camera").getConstructor(fitType, float.class, float.class, int.class, int.class, float.class)
                    .newInstance(fit, 1f, 2f, 1280, 720, 0f);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void invoke(Object character, long mode) {
        try { frame.invoke(null, null, character, mode); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Plan call(Object character, Object previous, long mode, boolean fallback, Class<? extends Throwable> failure) {
        Plan plan = new Plan();
        plan.fallback = fallback;
        PLAN.set(plan);
        cameras.set(previous);
        try {
            invoke(character, mode);
            require(failure == null, "Expected body throwable: " + failure);
        } catch (RuntimeException | Error thrown) {
            if (failure == null || !failure.isInstance(thrown)) throw thrown;
        } finally { PLAN.remove(); }
        require(plan.bodies == 1, "Body did not run exactly once");
        require(plan.tails == (mode == 1 || failure != null ? 0 : 1), "Early-return/tail behavior changed");
        require(cameras.get() == previous, "Woven exit did not restore previous camera identity");
        if (!fallback) require(plan.seen != previous, "Enter did not replace previous camera");
        cases++;
        return plan;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("artifact.jar Viewpoint.jar fixture-classes output-directory");
        Path output = Path.of(args[3]);
        Files.createDirectories(output);
        Map<String, byte[]> definitions = new HashMap<>();
        try (JarFile jar = new JarFile(args[0])) {
            for (var entry : jar.stream().filter(e -> e.getName().startsWith("vpiso/") && e.getName().endsWith(".class")).toList())
                definitions.put(entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.'), jar.getInputStream(entry).readAllBytes());
        }
        try (var files = Files.walk(Path.of(args[2]))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = Path.of(args[2]).relativize(path).toString().replace('\\', '/');
                definitions.put(name.substring(0, name.length() - 6).replace('/', '.'), Files.readAllBytes(path));
            }
        }
        byte[] original;
        try (JarFile jar = new JarFile(args[1])) { original = jar.getInputStream(jar.getJarEntry(TARGET.replace('.', '/') + ".class")).readAllBytes(); }
        byte[] fixture = cpuTarget(original);
        byte[] patch = definitions.get(PATCH);
        require(patch != null, "Artifact has no production scope advice");
        for (String name : new String[] { PATCH, "vpiso.ShadowPreload", "vpiso.ShadowPreload$Camera", "vpiso.GameAccess" })
            Files.write(output.resolve(name + ".class"), definitions.get(name));
        Method convert = PatchWeavingProbe.class.getDeclaredMethod("convert", byte[].class);
        convert.setAccessible(true);
        byte[] advice = (byte[]) convert.invoke(null, (Object) patch);
        definitions.put(PATCH, advice);
        definitions.put(TARGET, fixture);
        ClassFileLocator locator = new ClassFileLocator.Compound(new ClassFileLocator.Simple(definitions), ClassFileLocator.ForClassLoader.ofSystemLoader());
        Class<?> adviceClass = new Loader(definitions).loadClass(PATCH);
        require(adviceClass.getDeclaredMethod("exit", adviceClass.getClassLoader().loadClass("vpiso.ShadowPreload$Camera"))
                .getAnnotation(Advice.OnMethodExit.class).onThrowable() == Throwable.class, "Production exit does not handle all body throwables");
        var description = TypePool.Default.of(locator).describe(TARGET).resolve();
        require(description.getDeclaredMethods().filter(named("frame")).size() == 1, "Target match is not unique");
        byte[] woven = new ByteBuddy().redefine(description, locator).visit(Advice.to(adviceClass, locator).on(named("frame"))).make().getBytes();
        Files.write(output.resolve("IsoUpkeep-original.class"), original);
        Files.write(output.resolve("IsoUpkeep-cpu-fixture.class"), fixture);
        Files.write(output.resolve("Patch_ShadowPreloadScope-converted.class"), advice);
        Files.write(output.resolve("IsoUpkeep-woven.class"), woven);
        definitions.put(TARGET, woven);
        loader = new Loader(definitions);
        Field cameraField = field("vpiso.ShadowPreload", "CAMERA");
        @SuppressWarnings("unchecked") ThreadLocal<Object> local = (ThreadLocal<Object>) cameraField.get(null);
        cameras = local;
        frame = type(TARGET).getDeclaredMethod("frame", type("zombie.iso.IsoCell"), type("zombie.characters.IsoGameCharacter"), long.class);
        frame.setAccessible(true);
        Class<?> playerType = type("zombie.characters.IsoPlayer");
        Object player = playerType.getConstructor(float.class, float.class, float.class).newInstance(100f, 200f, 0f);
        Object other = playerType.getConstructor(float.class, float.class, float.class).newInstance(-12f, 37f, 2f);
        Object players = java.lang.reflect.Array.newInstance(playerType, 1);
        java.lang.reflect.Array.set(players, 0, player);
        set("zombie.characters.IsoPlayer", "players", players);
        set("zombie.iso.IsoCamera", "character", player);

        Object prior = previousCamera();
        call(player, null, 0, false, null);
        call(player, prior, 1, false, null);
        call(player, prior, 2, false, BodyFailure.class);
        call(player, prior, 3, false, BodyError.class);
        set("zombie.iso.IsoCamera", "failSetup", true);
        int reads = field("zombie.iso.IsoCamera", "widthCalls").getInt(null);
        call(player, prior, 0, true, null);
        require(field("zombie.iso.IsoCamera", "widthCalls").getInt(null) == reads + 1, "RuntimeException injection never reached camera setup");
        set("zombie.iso.IsoCamera", "failSetup", false);
        set("vpiso.IsometricBridge", "failLinkage", true);
        call(player, prior, 0, true, null);
        set("vpiso.IsometricBridge", "failLinkage", false);
        set("vpiso.IsometricBridge", "allowed", false);
        call(player, prior, 1, true, null);
        set("vpiso.IsometricBridge", "allowed", true);

        Plan outer = new Plan();
        PLAN.set(outer);
        cameras.set(prior);
        outer.during = () -> {
            Object active = cameras.get();
            try {
                call(other, active, 0, false, null);
                call(other, active, 2, false, BodyFailure.class);
            } finally { PLAN.set(outer); }
            require(cameras.get() == active, "Nested calls did not restore outer active camera");
        };
        invoke(player, 0);
        require(outer.bodies == 1 && outer.tails == 1 && cameras.get() == prior, "Outer nested call did not restore preexisting scope");
        PLAN.remove();
        cases++;

        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        AtomicReference<Object> firstCamera = new AtomicReference<>(), secondCamera = new AtomicReference<>();
        Thread first = worker(player, entered, release, firstCamera, threadFailure);
        Thread second = worker(other, entered, release, secondCamera, threadFailure);
        first.start(); second.start();
        require(entered.await(10, TimeUnit.SECONDS), "Workers never reached woven body");
        require(cameras.get() == prior, "Worker changed main-thread camera");
        require(firstCamera.get() != null && secondCamera.get() != null && firstCamera.get() != secondCamera.get(), "Workers share an active camera");
        release.countDown();
        first.join(10000); second.join(10000);
        require(!first.isAlive() && !second.isAlive(), "Worker did not finish");
        if (threadFailure.get() != null) throw new AssertionError("Thread fixture failed", threadFailure.get());
        cases++;
        cameras.remove();
        System.out.println("PASS PreloadScope115Probe: " + cases + " cases; production advice actually woven onto installed frame ABI; actual begin/restore/GameAccess; early return, RuntimeException, Error, setup fallback, nested identity restoration, thread isolation.");
        System.out.println("BOUNDARY: original target body/clinit and game/camera/provider environment are synthetic CPU fixtures; no original world update, graphics, attach, map/network, settings save, or visual claim.");
    }

    private static Thread worker(Object character, CountDownLatch entered, CountDownLatch release,
                                 AtomicReference<Object> active, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            Object previous = previousCamera();
            Plan plan = new Plan();
            plan.during = () -> {
                active.set(cameras.get()); entered.countDown();
                try { require(release.await(10, TimeUnit.SECONDS), "Thread release timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                require(cameras.get() == active.get(), "Other thread replaced active camera");
            };
            cameras.set(previous); PLAN.set(plan);
            try {
                invoke(character, 0);
                require(plan.bodies == 1 && plan.tails == 1 && cameras.get() == previous, "Thread did not restore its own previous scope");
            } catch (Throwable thrown) { failure.compareAndSet(null, thrown); entered.countDown(); }
            finally { cameras.remove(); PLAN.remove(); }
        }, "preload115-scope");
    }
}
