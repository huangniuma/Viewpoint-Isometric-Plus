import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import me.zed_0xff.zombie_buddy.Patch;
import me.zed_0xff.zombie_buddy.SyntaxSugar;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

/** Offline weaving verification plus isolated production tree-scope execution; no world is loaded. */
public final class PatchWeavingProbe {
    private static final String PATCH = "Lme/zed_0xff/zombie_buddy/Patch$";
    private static final String BB = "Lnet/bytebuddy/asm/Advice$";
    private static final Map<String, String> ANNOTATIONS = new LinkedHashMap<>();
    private static final Map<Type, Type> TYPES = new LinkedHashMap<>();
    private static final Set<Class<? extends Annotation>> SPECIAL = Set.of(
        Advice.AllArguments.class, Advice.Local.class, Advice.Return.class,
        Advice.This.class, Advice.Thrown.class);

    static {
        ANNOTATIONS.put(PATCH + "OnEnter;", BB + "OnMethodEnter;");
        ANNOTATIONS.put(PATCH + "OnExit;", BB + "OnMethodExit;");
        for (String name : List.of("Return", "This", "Argument", "AllArguments", "Thrown", "Local")) {
            ANNOTATIONS.put(PATCH + name + ";", BB + name + ";");
        }
        for (String name : List.of("RuntimeType", "SuperMethod", "SuperCall")) {
            ANNOTATIONS.put(PATCH + name + ";", "Lnet/bytebuddy/implementation/bind/annotation/" + name + ";");
        }
        TYPES.put(Type.getType(PATCH + "NoException;"), Type.getType(BB + "NoExceptionHandler;"));
        TYPES.put(Type.getType(PATCH + "OnDefaultValue;"), Type.getType(BB + "OnDefaultValue;"));
        TYPES.put(Type.getType(PATCH + "OnNonDefaultValue;"), Type.getType(BB + "OnNonDefaultValue;"));
    }

    // Same Advice annotation byte transformation as shipped ZB PatchTransformer.
    private static byte[] convert(byte[] original) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        String mapped = ANNOTATIONS.getOrDefault(descriptor, descriptor);
                        AnnotationVisitor output = super.visitAnnotation(mapped, visible);
                        if (mapped.equals(descriptor)) return output;
                        return new AnnotationVisitor(Opcodes.ASM9, output) {
                            @Override public void visit(String name, Object value) {
                                if (name.equals("skipOn") && value instanceof Boolean skip) {
                                    value = Type.getType(BB + (skip ? "OnNonDefaultValue;" : "NoExceptionHandler;"));
                                } else if (value instanceof Type type) {
                                    value = TYPES.getOrDefault(type, type);
                                }
                                super.visit(name, value);
                            }
                        };
                    }
                    @Override public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                        return super.visitParameterAnnotation(parameter, ANNOTATIONS.getOrDefault(descriptor, descriptor), visible);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    // Mirrors current ZB Advice overload matching, including intentional array-to-component conversion.
    private static ElementMatcher.Junction<MethodDescription> matcher(Class<?> advice, String methodName, boolean strict) {
        List<Map<Integer, Class<?>>> maps = new ArrayList<>();
        List<Boolean> exactMatches = new ArrayList<>();
        boolean allArguments = false;
        boolean noParam = false;
        int minParams = 0;
        for (Method method : advice.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Advice.OnMethodEnter.class) && !method.isAnnotationPresent(Advice.OnMethodExit.class)) continue;
            Annotation[][] annotations = method.getParameterAnnotations();
            for (Annotation[] parameter : annotations) {
                for (Annotation annotation : parameter) {
                    if (annotation instanceof Advice.AllArguments) allArguments = true;
                }
            }
            if (method.getParameterCount() == 0 && !allArguments) noParam = true;
            if (allArguments) continue;
            Class<?>[] types = method.getParameterTypes();
            Map<Integer, Class<?>> arguments = new LinkedHashMap<>();
            boolean hasArguments = false;
            boolean allSpecial = types.length > 0;
            for (int i = 0; i < annotations.length; i++) {
                boolean skip = false;
                boolean argument = false;
                for (Annotation annotation : annotations[i]) {
                    if (annotation instanceof Advice.Argument arg) {
                        hasArguments = true;
                        argument = true;
                        allSpecial = false;
                        Class<?> type = types[i];
                        arguments.put(arg.value(), type.isArray() ? type.getComponentType() : type);
                        break;
                    }
                    if (SPECIAL.contains(annotation.annotationType())) {
                        skip = true;
                        break;
                    }
                }
                if (!skip && !argument) {
                    allSpecial = false;
                    arguments.put(i, types[i]);
                }
            }
            if (allSpecial) noParam = true;
            if (!arguments.isEmpty()) {
                maps.add(arguments);
                exactMatches.add(!hasArguments);
                if (hasArguments) {
                    int required = arguments.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
                    minParams = Math.max(minParams, required);
                }
            }
        }
        ElementMatcher.Junction<MethodDescription> nameMatcher = SyntaxSugar.methodMatcher(methodName);
        if (allArguments || (noParam && !strict && maps.isEmpty())) return nameMatcher;
        final boolean noParamAdvice = noParam;
        final int minimum = minParams;
        return nameMatcher.and(target -> {
            int count = target.getParameters().size();
            if (noParamAdvice && count == 0) return true;
            if (strict && noParamAdvice && maps.isEmpty() && count > 0) return false;
            for (int mapIndex = 0; mapIndex < maps.size(); mapIndex++) {
                Map<Integer, Class<?>> arguments = maps.get(mapIndex);
                boolean exact = exactMatches.get(mapIndex);
                if (exact && count != arguments.size()) continue;
                if (!exact && (count < arguments.size() || count < minimum)) continue;
                boolean match = true;
                for (Map.Entry<Integer, Class<?>> argument : arguments.entrySet()) {
                    int index = argument.getKey();
                    Class<?> expected = argument.getValue();
                    if (index >= count || (expected != Object.class && !target.getParameters().get(index).getType().asErasure().isAssignableTo(expected))) {
                        match = false;
                        break;
                    }
                }
                if (match) return true;
            }
            return noParamAdvice && !strict;
        });
    }

    private static final class BytesLoader extends ClassLoader {
        private final Map<String, byte[]> classes;
        private final boolean isolateGame;
        BytesLoader(Map<String, byte[]> classes) {
            this(classes, false);
        }
        BytesLoader(Map<String, byte[]> classes, boolean isolateGame) {
            super(PatchWeavingProbe.class.getClassLoader());
            this.classes = classes;
            this.isolateGame = isolateGame;
        }
        private boolean ownsPackage(String name) {
            // An offline redefinition must keep the original runtime packages together.
            // In particular, IsoPlayer legally invokes protected IsoGameCharacter members
            // on sibling instances only while both classes share package AND class loader.
            return isolateGame && (name.startsWith("zombie.") || name.startsWith("viewpoint.")
                    || name.startsWith("vpiso.") || name.startsWith("pzopt.") || name.startsWith("fmod."));
        }
        private byte[] parentBytes(String name) throws ClassNotFoundException {
            String overrides=System.getProperty("vpiso.testOverrides");
            if(overrides!=null) {
                Path path=Path.of(overrides,name.replace('.','/')+".class");
                try { if(Files.isRegularFile(path)) return Files.readAllBytes(path); }
                catch(java.io.IOException error) { throw new ClassNotFoundException(name,error); }
            }
            try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (input == null) throw new ClassNotFoundException(name);
                return input.readAllBytes();
            } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
        }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                byte[] bytes = classes.get(name);
                if (bytes == null && ownsPackage(name)) bytes = parentBytes(name);
                loaded = bytes == null ? super.loadClass(name, false) : defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
        @Override public InputStream getResourceAsStream(String name) {
            if (name.endsWith(".class")) {
                byte[] bytes = classes.get(name.substring(0, name.length() - 6).replace('/', '.'));
                if (bytes != null) return new ByteArrayInputStream(bytes);
            }
            String overrides = System.getProperty("vpiso.testOverrides");
            if (overrides != null) {
                Path resource = Path.of(overrides, name);
                try { if (Files.isRegularFile(resource)) return Files.newInputStream(resource); }
                catch (java.io.IOException error) { throw new IllegalStateException("Unreadable override resource: " + name, error); }
            }
            return super.getResourceAsStream(name);
        }
        Class<?> verify(String name) throws ClassNotFoundException {
            Class<?> type = loadClass(name, true);
            type.getDeclaredMethods();
            type.getDeclaredConstructors();
            type.getDeclaredFields();
            return type;
        }
    }

    private static void checkOriginalGameIsolation() throws Exception {
        String player = "zombie.characters.IsoPlayer";
        byte[] original;
        try (InputStream input = ClassLoader.getSystemResourceAsStream(player.replace('.', '/') + ".class")) {
            if (input == null) throw new AssertionError("Original IsoPlayer bytes are unavailable");
            original = input.readAllBytes();
        }
        boolean reproduced = false;
        try { new BytesLoader(Map.of(player, original)).verify(player); }
        catch (VerifyError expected) {
            if (!expected.getMessage().contains("zombie/characters/IsoPlayer.")
                    || !expected.getMessage().contains("protected data")) throw expected;
            reproduced = true;
        }
        if (!reproduced) throw new AssertionError("Original game split-package baseline did not reproduce; recheck loader regression");
        BytesLoader isolated = new BytesLoader(Map.of(player, original), true);
        Class<?> loaded = isolated.verify(player);
        Class<?> base = isolated.verify("zombie.characters.IsoGameCharacter");
        if (loaded.getClassLoader() != base.getClassLoader() || !base.isAssignableFrom(loaded))
            throw new AssertionError("Original game hierarchy still crosses runtime packages");
        System.out.println("LOADER BASELINE PASS: untouched IsoPlayer reproduces protected-access failure with split packages; identical original bytes pass with the complete game hierarchy in one runtime loader");
    }

    public static final class SemanticsTarget {
        public int calls;
        public int original(int input) { calls++; return input * 2; }
    }
    public static final class SemanticsAdvice {
        @Patch.OnEnter(skipOn=true)
        public static boolean enter(@Patch.Argument(value=0, readOnly=false) int input) {
            if (input < 0) return true;
            input += 3;
            return false;
        }
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly=false) int output) { output += 7; }
    }
    public static final class ArrayArgumentAdvice {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) String path, @Patch.Argument(1) Object active,
                                @Patch.Argument(2) boolean[] fromPack, @Patch.Return(readOnly=false) String source) {}
    }

    public static final class TreeScopeTarget {
        public int operation(Object object, boolean fail) {
            int flags=vpiso.TreeModelScope.flags(16384);
            if (fail) throw new IllegalArgumentException("scope fixture");
            return flags;
        }
    }

    public static final class WaterScopeTarget {
        public boolean operation(viewpoint.render.FrameContext context, boolean fail) {
            if (fail) throw new IllegalArgumentException("water scope fixture");
            return context.pipeline.water;
        }
    }

    private static void checkWaterScope(Map<String,byte[]> converted) throws Exception {
        String name = "vpiso.Patch_ModelWater";
        ClassFileLocator locator = new ClassFileLocator.Compound(new ClassFileLocator.Simple(converted),
                ClassFileLocator.ForClassLoader.ofSystemLoader());
        Class<?> advice = new BytesLoader(converted).loadClass(name);
        byte[] woven = new ByteBuddy().redefine(WaterScopeTarget.class)
                .visit(Advice.to(advice,locator).on(matcher(advice,"operation",false))).make().getBytes();
        Class<?> target = new BytesLoader(Map.of(WaterScopeTarget.class.getName(),woven)).verify(WaterScopeTarget.class.getName());
        Object instance = target.getConstructor().newInstance();
        Method operation = target.getMethod("operation",viewpoint.render.FrameContext.class,boolean.class);
        viewpoint.render.FrameContext context = new viewpoint.render.FrameContext();
        viewpoint.platform.Pipeline original = context.pipeline;
        if (original.water || !operation.invoke(instance,context,false).equals(false) || context.pipeline != original)
            throw new AssertionError("Ordinary VP water scope was altered");
        vpiso.IsometricBridge.modelRendering(new viewpoint.core.Frame(), vpiso.IsometricEffects.DEFAULT);
        try {
            if (!operation.invoke(instance,context,false).equals(false) || context.pipeline != original)
                throw new AssertionError("Disabled water setting still enabled model waves");
            vpiso.IsometricBridge.modelRendering(new viewpoint.core.Frame(),
                    new vpiso.IsometricEffects(false, .55f, 1f, 64f, 2048, false, 1f, 1f, 1f, 1f, true));
            if (!operation.invoke(instance,context,false).equals(true) || context.pipeline != original || original.water)
                throw new AssertionError("Model water scope did not restore original pipeline");
            try { operation.invoke(instance,context,true); throw new AssertionError("Expected water fixture exception"); }
            catch (java.lang.reflect.InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
            }
            if (context.pipeline != original || original.water)
                throw new AssertionError("Exceptional exit leaked model water pipeline");
        } finally { vpiso.IsometricBridge.rendering(null); }
        if (!operation.invoke(instance,context,false).equals(false) || context.pipeline != original)
            throw new AssertionError("Model water scope leaked into ordinary VP");
        System.out.println("WATER SCOPE PASS: production Advice/Local, independent model water gate, unchanged shared Pipeline and normal/exceptional restoration; isolated target, no wave GPU draw.");
    }

    private static void checkTreeScope(Map<String,byte[]> converted) throws Exception {
        String name="vpiso.Patch_TreeObjectModel";
        ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(converted),
                ClassFileLocator.ForClassLoader.ofSystemLoader());
        Class<?> advice=new BytesLoader(converted).loadClass(name);
        byte[] woven=new ByteBuddy().redefine(TreeScopeTarget.class)
                .visit(Advice.to(advice,locator).on(matcher(advice,"operation",false))).make().getBytes();
        Class<?> target=new BytesLoader(Map.of(TreeScopeTarget.class.getName(),woven)).verify(TreeScopeTarget.class.getName());
        Object instance=target.getConstructor().newInstance();
        Method operation=target.getMethod("operation",Object.class,boolean.class);
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object tree=((sun.misc.Unsafe)field.get(null)).allocateInstance(Class.forName("zombie.iso.objects.IsoTree"));
        if (!operation.invoke(instance,tree,false).equals(16384|vpiso.TreeModelScope.TREE)
                || vpiso.TreeModelScope.flags(16384)!=16384
                || !operation.invoke(instance,new Object(),false).equals(16384))
            throw new AssertionError("Tree model identity or normal scope restore failed");
        int previous=vpiso.TreeModelScope.enter(tree);
        try {
            if (!operation.invoke(instance,new Object(),false).equals(16384)
                    || vpiso.TreeModelScope.flags(0)!=vpiso.TreeModelScope.TREE)
                throw new AssertionError("Nested non-tree scope did not restore outer tree");
        } finally { vpiso.TreeModelScope.restore(previous); }
        try { operation.invoke(instance,tree,true); throw new AssertionError("Expected fixture exception"); }
        catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
        }
        if (vpiso.TreeModelScope.flags(0)!=0) throw new AssertionError("Exceptional exit leaked tree marker");
        System.out.println("TREE SCOPE PASS: actual production Advice/Local, tree identity, non-tree preservation, nested and exceptional restoration");
    }

    private static void checkSemantics() throws Exception {
        String name = SemanticsAdvice.class.getName();
        byte[] bytes;
        try (InputStream input = PatchWeavingProbe.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            bytes = convert(input.readAllBytes());
        }
        ClassFileLocator locator = new ClassFileLocator.Compound(new ClassFileLocator.Simple(Map.of(name, bytes)),
            ClassFileLocator.ForClassLoader.ofSystemLoader());
        Class<?> advice = new BytesLoader(Map.of(name, bytes)).loadClass(name);
        if (advice.getDeclaredMethod("enter", int.class).getAnnotation(Advice.OnMethodEnter.class).skipOn() != Advice.OnNonDefaultValue.class) {
            throw new AssertionError("skipOn=true was not translated to OnNonDefaultValue");
        }
        byte[] woven = new ByteBuddy().redefine(SemanticsTarget.class)
            .visit(Advice.to(advice, locator).on(matcher(advice, "original", false))).make().getBytes();
        Class<?> target = new BytesLoader(Map.of(SemanticsTarget.class.getName(), woven)).verify(SemanticsTarget.class.getName());
        Object instance = target.getConstructor().newInstance();
        Method method = target.getMethod("original", int.class);
        if (!method.invoke(instance, 2).equals(17) || !method.invoke(instance, -1).equals(7)
                || target.getField("calls").getInt(instance) != 1) {
            throw new AssertionError("Writable Argument/Return or skipOn semantics failed");
        }
        System.out.println("SEMANTICS PASS: writable Argument; writable Return; skipOn true skips body and preserves exit advice");
    }

    private static void writeClass(Path directory, String name, byte[] bytes) throws Exception {
        Path output = directory.resolve(name.replace('.', '/') + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, bytes);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PatchWeavingProbe <addon classes> <output directory>");
        Path addon = Path.of(args[0]);
        Path output = Path.of(args[1]);
        System.out.println("JVM " + System.getProperty("java.version") + "; verification requires caller -Xverify:all");
        System.out.println("ByteBuddy " + ByteBuddy.class.getProtectionDomain().getCodeSource().getLocation());
        checkSemantics();
        checkOriginalGameIsolation();
        Map<String, byte[]> converted = new TreeMap<>();
        Map<String, byte[]> helpers = new TreeMap<>();
        try (var files = Files.list(addon.resolve("vpiso"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".class")).sorted().toList()) {
                String name = "vpiso." + file.getFileName().toString().replaceFirst("\\.class$", "");
                byte[] bytes = Files.readAllBytes(file);
                if (name.startsWith("vpiso.Patch_")) converted.put(name, convert(bytes));
                else helpers.put(name, bytes);
            }
        }
        checkTreeScope(converted);
        checkWaterScope(converted);
        if (converted.isEmpty()) throw new AssertionError("No built addon patch classes found");
        List<ClassFileLocator> locators=new ArrayList<>();
        locators.add(new ClassFileLocator.Simple(converted));
        String overrides=System.getProperty("vpiso.testOverrides");
        if(overrides!=null) locators.add(new ClassFileLocator.ForFolder(Path.of(overrides).toFile()));
        locators.add(ClassFileLocator.ForClassLoader.ofSystemLoader());
        ClassFileLocator locator = new ClassFileLocator.Compound(locators);
        TypePool pool = TypePool.Default.of(locator);
        BytesLoader adviceLoader = new BytesLoader(converted);
        Map<String, DynamicType.Builder<?>> builders = new TreeMap<>();
        int matchedPatches = 0, absentPatches = 0;
        List<String> failures = new ArrayList<>();
        for (String name : converted.keySet()) {
            Class<?> advice = adviceLoader.verify(name);
            Patch annotation = advice.getAnnotation(Patch.class);
            if (annotation == null || !annotation.isAdvice()) throw new AssertionError("Expected Advice @Patch: " + name);
            var resolution = pool.describe(annotation.className());
            if (!resolution.isResolved() && Set.of(System.getProperty("vpiso.optionalAbsent", "").split(",")).contains(annotation.className())) {
                absentPatches++;
                System.out.println("OPTIONAL ABSENT " + name + " -> " + annotation.className());
                continue;
            }
            var target = resolution.resolve();
            var methodMatcher = matcher(advice, annotation.methodName(), annotation.strictMatch());
            var matched = target.getDeclaredMethods().filter(methodMatcher);
            System.out.println("PATCH " + name + " -> " + annotation.className() + "." + annotation.methodName() + ": " + matched.size() + " method(s)");
            for (var method : matched) System.out.println("  " + method.getInternalName() + method.getDescriptor());
            int expected = name.equals("vpiso.Patch_NativeSpriteShadowDraw") ? 9 : 1;
            if (matched.size() != expected) {
                failures.add(name + " matched " + matched.size() + " methods (expected " + expected + ")");
                continue;
            }
            matchedPatches++;
            DynamicType.Builder<?> builder = builders.get(annotation.className());
            if (builder == null) builder = new ByteBuddy().redefine(target, locator);
            builders.put(annotation.className(), builder.visit(Advice.to(advice, locator).on(methodMatcher)));
            writeClass(output.resolve("converted-advice"), name, converted.get(name));
        }

        // Negative regression for ZB's array component inference; Object is needed for this unused parameter.
        String arrayName = ArrayArgumentAdvice.class.getName();
        byte[] arrayBytes;
        try (InputStream input = PatchWeavingProbe.class.getClassLoader().getResourceAsStream(arrayName.replace('.', '/') + ".class")) {
            arrayBytes = convert(input.readAllBytes());
        }
        Class<?> arrayAdvice = new BytesLoader(Map.of(arrayName, arrayBytes)).verify(arrayName);
        int badArrayMatches = pool.describe("viewpoint.platform.Shaders").resolve().getDeclaredMethods()
            .filter(matcher(arrayAdvice, "read", false)).size();
        if (badArrayMatches != 0) throw new AssertionError("ZB array-component matching behavior changed; recheck mirror");
        System.out.println("ARRAY REGRESSION PASS: boolean[] advice argument matches zero; shipped patch uses Object wildcard");

        Map<String, byte[]> woven = new TreeMap<>();
        for (var entry : builders.entrySet()) {
            try {
                var result = entry.getValue().make();
                for (var generated : result.getAllTypes().entrySet()) {
                    woven.put(generated.getKey().getName(), generated.getValue());
                    writeClass(output.resolve("woven"), generated.getKey().getName(), generated.getValue());
                }
                System.out.println("WEAVE PASS " + entry.getKey());
            } catch (Throwable error) {
                failures.add("Weave failed: " + entry.getKey() + ": " + error);
                error.printStackTrace(System.out);
            }
        }
        Map<String, byte[]> verifyBytes = new TreeMap<>(helpers);
        verifyBytes.putAll(woven);
        BytesLoader verification = new BytesLoader(verifyBytes, true);
        int verified = 0;
        for (String name : verifyBytes.keySet()) {
            try {
                verification.verify(name);
                verified++;
                System.out.println("VERIFY PASS " + name);
            } catch (Throwable error) {
                failures.add("JVM verification failed: " + name + ": " + error);
                error.printStackTrace(System.out);
            }
        }
        System.out.println("RESULT patches=" + matchedPatches + "/" + (converted.size() - absentPatches) + "; optionalAbsent=" + absentPatches + "; wovenTargetClasses=" + builders.size()
            + "; verifiedClasses=" + verified + "/" + verifyBytes.size() + "; failures=" + failures.size());
        for (String failure : failures) System.out.println("FAIL " + failure);
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
        System.out.println("OFFLINE PASS; isolated tree identity/scope fixture, no world load, OpenGL, live Instrumentation or visual test");
    }
}
