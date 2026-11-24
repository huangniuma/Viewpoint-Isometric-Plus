import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.GameAccess;
import vpiso.NativeSpriteShadows;
import vpiso.NativeShadowMasks;

/** Actual source crops and native Texture/TextureID creation; never rewrites a game atlas. */
public final class NativeSpriteShadowsProbe {
    private static final String TEX = "zombie.core.textures.Texture", ID = "zombie.core.textures.TextureID";
    private static final String CELL = "zombie.iso.fboRenderChunk.FBORenderCell", LEVELS = "zombie.iso.fboRenderChunk.FBORenderLevels";
    private static final String[] META = { "name", "width", "height", "widthOrig", "heightOrig", "realWidth", "realHeight",
            "offsetX", "offsetY", "xStart", "yStart", "xEnd", "yEnd", "flip", "bindAlways", "solid", "mask",
            "splitX", "splitY", "splitW", "splitH", "subTexture", "assetParams" };
    private static Object levels, renderer, previousCell;
    private static Method clear, dirty;

    public static void main(String[] args) throws Exception {
        // PZO branches below use shipped overrides in this isolated JVM only.
        System.setProperty("pzopt.enabled", "true");
        Path evidence = args.length > 0 ? Path.of(args[0]) : Path.of("research", "build018-shadows");
        if (!Files.isDirectory(evidence)) evidence = Path.of("VP原版视角光影", "research", "build018-shadows");
        BufferedImage hydrant = ImageIO.read(evidence.resolve("street_decoration_01_12.png").toFile());
        BufferedImage pole = ImageIO.read(evidence.resolve("lighting_outdoor_01_16.png").toFile());
        checkMask(hydrant, "street_decoration_01_12", 53, 309);
        checkMask(pole, "lighting_outdoor_01_16", 213, 15);
        Path chairs = evidence.resolveSibling("build029-fixed");
        int[] chairPixels = { 1133, 1047, 1088, 1060 };
        for (int i = 0; i < 4; i++) {
            String name = "furniture_seating_indoor_01_" + (60 + i);
            BufferedImage chair = ImageIO.read(chairs.resolve(name + ".png").toFile());
            checkMask(chair, name, 0, chairPixels[i]);
        }
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(64, 64, "VPISO verified sprite shadow masks", 0, 0);
        if (window == 0) throw new AssertionError("Hidden GL window unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            GameAccess.set("org.lwjglx.opengl.Display", "capabilities", null, GL.getCapabilities());
            GameAccess.set("zombie.core.opengl.RenderThread", "isInitialized", null, true);
            GameAccess.set("zombie.core.opengl.RenderThread", "contextThread", null, Thread.currentThread());
            cacheFixture();
            // No atlas exists yet: mode changes must not collapse onto READY == 0.
            NativeSpriteShadows.prepare(false); assertDirty(true, "initial native cache");
            NativeSpriteShadows.prepare(true); assertDirty(true, "initial enable before any copy");
            NativeSpriteShadows.prepare(true); assertDirty(false, "initial enable idle");
            NativeSpriteShadows.prepare(false); assertDirty(true, "disable before any copy");
            NativeSpriteShadows.prepare(false); assertDirty(false, "initial disable idle");
            NativeSpriteShadows.prepare(true);
            assertDirty(true, "reenable before any copy");
            checkCopy(hydrant, "street_decoration_01_12");
            checkCopy(pole, "lighting_outdoor_01_16");
            checkCopy(pole, "lighting_outdoor_01_16_on");
            checkCopy(hydrant, "street_decoration_01_12", true);
            checkCopy(pole, "lighting_outdoor_01_16", true);
            checkGeneralMasks(evidence.resolveSibling("build019-fixed"));
            checkReviewedFurniture(evidence.resolveSibling("build029-furniture-audit"));
            for (int i = 0; i < 4; i++) {
                String name = "furniture_seating_indoor_01_" + (60 + i);
                BufferedImage chair = ImageIO.read(chairs.resolve(name + ".png").toFile());
                checkCopy(chair, name);
                checkCopy(chair, name, true);
            }
            System.out.println("Folding chair shadows PASS: all four original orientations remove 1133/1047/1088/1060 ground-shadow pixels; body and legs unchanged; actual uncompressed/compressed native GPU copies and off/on restoration.");
            checkSharedAtlas(evidence.resolveSibling("build019-fixed"));
            checkSharedAtlas(chairs, new String[] { "furniture_seating_indoor_01_60", "furniture_seating_indoor_01_61",
                    "furniture_seating_indoor_01_62", "furniture_seating_indoor_01_63" });
            checkWovenPipeline();
            checkPzoScopes();
            checkWovenDraw();
            checkWovenBlobs();
            checkBridgeGates();
            NativeSpriteShadows.prepare(false);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error");
            System.out.println("Native sprite shadows PASS: 1111 generated ground-shadow masks plus exact 309 hydrant / 15 pole pixels; three actual furniture crops, RGB pollution and actual compressed native atlases; same-page textures share one GPU atlas; body/custom opaque/other sprites, source atlas, native UV/metadata, GL state, mode/thread/capture scopes retained; native cache invalidated once per copy/mode transition.");
        } finally {
            if (renderer != null) GameAccess.set(CELL, "cell", renderer, previousCell);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    private static void checkGeneralMasks(Path evidence) throws Exception {
        if(NativeShadowMasks.size()!=1111)throw new AssertionError("Generated mask inventory");
        if(NativeShadowMasks.get("advertising_01_on_7")!=null || NativeShadowMasks.get("lighting_outdoor_01_on_40")!=null
                || NativeShadowMasks.get("fixtures_windows_curtains_0")!=null || NativeShadowMasks.get("vegetation_trees_01_0")!=null)
            throw new AssertionError("Non-ground/foliage artwork matched generated masks");
        String[] names={"furniture_seating_outdoor_01_0","furniture_tables_high_01_0","furniture_storage_01_1"};
        int[] expected={2042,3309,1138};
        for(int i=0;i<names.length;i++) {
            BufferedImage image=ImageIO.read(evidence.resolve(names[i]+".png").toFile());
            ByteBuffer source=rgba(image),copy=BufferUtils.createByteBuffer(source.capacity());copy.put(source.duplicate()).clear();
            int changed=NativeSpriteShadows.mask(names[i],image.getWidth(),image.getHeight(),copy,image.getWidth(),0,0);
            if(changed!=expected[i])throw new AssertionError("Actual furniture mask count "+names[i]+": "+changed);
            for(int p=0;p<source.capacity();p+=4) {
                if(copy.get(p+3)!=source.get(p+3)) {
                    if(source.get(p)!=0||source.get(p+1)!=0||source.get(p+2)!=0||(source.get(p+3)&255)>128)
                        throw new AssertionError("Furniture mask deleted source material");
                } else for(int c=0;c<4;c++)if(copy.get(p+c)!=source.get(p+c))throw new AssertionError("Other furniture material changed");
            }
            checkCopy(image,names[i]);
        }
    }

    private static void checkReviewedFurniture(Path evidence) throws Exception {
        String[] names={ "furniture_tables_high_01_1", "furniture_tables_high_01_2", "furniture_seating_indoor_03_145",
                "furniture_seating_outdoor_01_25", "furniture_storage_01_54", "furniture_shelving_01_25" };
        int[] expected={ 2653, 2627, 1189, 1224, 884, 337 };
        for(int i=0;i<names.length;i++) {
            BufferedImage image=ImageIO.read(evidence.resolve(names[i]+".png").toFile());
            checkMask(image,names[i],0,expected[i]);
            checkCopy(image,names[i]);
        }
        System.out.println("Reviewed furniture shadows PASS: original table, rocking-chair, deck-chair, nightstand and shelf crops; all body/leg bytes retained, source-coordinate/tinted masks, private GPU copies, unknown/different-size/opaque safeguards and off/on restoration.");
    }

    private static void checkSharedAtlas(Path evidence) throws Exception {
        checkSharedAtlas(evidence, new String[] { "furniture_seating_outdoor_01_0", "furniture_tables_high_01_0" });
    }

    @SuppressWarnings("unchecked") private static void checkSharedAtlas(Path evidence, String[] names) throws Exception {
        int aw=128*names.length,ah=256;
        ByteBuffer pixels=BufferUtils.createByteBuffer(aw*ah*4);
        BufferedImage[] images=new BufferedImage[names.length];
        int[][] origins=new int[names.length][2];
        for(int i=0;i<names.length;i++) {
            origins[i][0]=i*128;
            images[i]=ImageIO.read(evidence.resolve(names[i]+".png").toFile());ByteBuffer crop=rgba(images[i]);
            for(int y=0;y<images[i].getHeight();y++)for(int x=0;x<images[i].getWidth();x++)for(int c=0;c<4;c++)
                pixels.put(((origins[i][1]+y)*aw+origins[i][0]+x)*4+c,crop.get((y*images[i].getWidth()+x)*4+c));
        }
        Object id=type(ID).getConstructor(int.class,int.class,ByteBuffer.class).newInstance(aw,ah,pixels);
        int sourceId=((Number)GameAccess.call(ID,"getID",id,new Class<?>[0])).intValue();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,sourceId);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,aw,ah,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        Object[] sources=new Object[names.length];
        Map<Object,Object> textures=(Map<Object,Object>)GameAccess.field("zombie.core.textures.TexturePackPage","subTextureMap",null);
        Object[] original=Arrays.stream(names).map(textures::get).toArray();
        ByteBuffer expected=BufferUtils.createByteBuffer(pixels.capacity());expected.put(pixels.duplicate()).clear();
        for(int i=0;i<names.length;i++) {
            sources[i]=type(TEX).getConstructor(type(ID),String.class).newInstance(id,names[i]);
            set(TEX,sources[i],"width",images[i].getWidth());set(TEX,sources[i],"height",images[i].getHeight());
            set(TEX,sources[i],"xStart",origins[i][0]/(float)aw);set(TEX,sources[i],"yStart",origins[i][1]/(float)ah);
            set(TEX,sources[i],"xEnd",(origins[i][0]+images[i].getWidth())/(float)aw);set(TEX,sources[i],"yEnd",(origins[i][1]+images[i].getHeight())/(float)ah);
            textures.put(names[i],sources[i]);
            NativeSpriteShadows.mask(names[i],images[i].getWidth(),images[i].getHeight(),expected,aw,origins[i][0],origins[i][1]);
        }
        int before=((Map<?,?>)GameAccess.field("vpiso.NativeSpriteShadows","ATLASES",null)).size();
        int scope=NativeSpriteShadows.enter();
        try {
            if(NativeSpriteShadows.texture(sources[0])!=sources[0])throw new AssertionError("Shared atlas creation must remain asynchronous");
            NativeSpriteShadows.prepare(true);assertDirty(true,"shared atlas generation");
            NativeSpriteShadows.prepare(true);assertDirty(false,"shared atlas idle");
            Object first=NativeSpriteShadows.texture(sources[0]);
            Object firstId=GameAccess.call(TEX,"getTextureId",first,new Class<?>[0]);
            if(firstId==id||((Map<?,?>)GameAccess.field("vpiso.NativeSpriteShadows","ATLASES",null)).size()!=before+1)
                throw new AssertionError("Native sprites allocated separate atlas copies");
            if(!Arrays.equals(bytes(expected),read(integer(first,"getID"),aw,ah)) || !Arrays.equals(bytes(pixels),read(sourceId,aw,ah)))
                throw new AssertionError("Shared atlas masks/source integrity");
            for(int i=0;i<names.length;i++) {
                Object result=NativeSpriteShadows.texture(sources[i]);
                if(result==sources[i]||firstId!=GameAccess.call(TEX,"getTextureId",result,new Class<?>[0]))
                    throw new AssertionError("Missing private sibling atlas "+names[i]);
                for(String field:META)if(!java.util.Objects.equals(GameAccess.field(TEX,field,sources[i]),GameAccess.field(TEX,field,result)))
                    throw new AssertionError("Shared metadata "+field);
            }
        } finally {
            NativeSpriteShadows.leave(scope);
            for(int i=0;i<names.length;i++)if(original[i]==null)textures.remove(names[i]);else textures.put(names[i],original[i]);
        }
    }

    /** Exercise the real getter and scope advice after the shipped ZB annotation conversion. */
    @SuppressWarnings("unchecked")
    private static void checkWovenPipeline() throws Exception {
        Class<?> probe = Class.forName("PatchWeavingProbe");
        Method convert = probe.getDeclaredMethod("convert", byte[].class); convert.setAccessible(true);
        Method matcher = probe.getDeclaredMethod("matcher", Class.class, String.class, boolean.class); matcher.setAccessible(true);
        Class<?> loader = Class.forName("PatchWeavingProbe$BytesLoader");
        var constructor = loader.getDeclaredConstructor(Map.class, boolean.class); constructor.setAccessible(true);
        Method verify = loader.getDeclaredMethod("verify", String.class); verify.setAccessible(true);
        Map<String, byte[]> converted = new java.util.HashMap<>();
        for (String name : new String[] { "vpiso.Patch_NativeSpriteShadowScope", "vpiso.Patch_NativeSpriteShadowTexture" }) {
            try (var stream = NativeSpriteShadowsProbe.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
                converted.put(name, (byte[]) convert.invoke(null, stream.readAllBytes()));
            }
        }
        var locator = new net.bytebuddy.dynamic.ClassFileLocator.Compound(new net.bytebuddy.dynamic.ClassFileLocator.Simple(converted),
                net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
        ClassLoader adviceLoader = (ClassLoader) constructor.newInstance(converted, false);
        Class<?> scopeAdvice = adviceLoader.loadClass("vpiso.Patch_NativeSpriteShadowScope");
        Class<?> textureAdvice = adviceLoader.loadClass("vpiso.Patch_NativeSpriteShadowTexture");
        var pool = net.bytebuddy.pool.TypePool.Default.of(locator);
        var target = pool.describe("zombie.iso.sprite.IsoSprite").resolve();
        var match = (net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription>) matcher.invoke(null, textureAdvice, "getTextureForFrame", true);
        byte[] spriteBytes = new net.bytebuddy.ByteBuddy().redefine(target, locator)
                .visit(net.bytebuddy.asm.Advice.to(textureAdvice, locator).on(match)).make().getBytes();
        byte[] operationBytes = new net.bytebuddy.ByteBuddy().redefine(ScopeTarget.class)
                .visit(net.bytebuddy.asm.Advice.to(scopeAdvice, locator).on(net.bytebuddy.matcher.ElementMatchers.named("get"))).make().getBytes();
        Map<String, byte[]> wovenClasses = new java.util.HashMap<>();
        wovenClasses.put("zombie.iso.sprite.IsoSprite", spriteBytes);
        wovenClasses.put(ScopeTarget.class.getName(), operationBytes);
        for (String inner : new String[] { "zombie.iso.sprite.IsoSprite$AndThen" }) {
            try (var stream = NativeSpriteShadowsProbe.class.getClassLoader().getResourceAsStream(inner.replace('.', '/') + ".class")) {
                wovenClasses.put(inner, stream.readAllBytes());
            }
        }
        ClassLoader woven = (ClassLoader) constructor.newInstance(wovenClasses, false);
        Class<?> spriteClass = (Class<?>) verify.invoke(woven, "zombie.iso.sprite.IsoSprite");
        Class<?> direction = type("zombie.iso.IsoDirections");
        Object north = Enum.valueOf((Class) direction, "N");
        Object source = ((Map<?, ?>) GameAccess.field("vpiso.NativeSpriteShadows", "COPIES", null)).keySet().iterator().next();
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Object sprite = ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(spriteClass);
        var texture = spriteClass.getDeclaredField("texture"); texture.setAccessible(true); texture.set(sprite, source);
        Method getter = spriteClass.getMethod("getTextureForFrame", int.class, direction, boolean.class);
        if (getter.invoke(sprite, 0, north, false) != source) throw new AssertionError("Woven getter changed texture outside native scope");
        Class<?> operationClass = (Class<?>) verify.invoke(woven, ScopeTarget.class.getName());
        Object operation = operationClass.getConstructor().newInstance();
        Method get = operationClass.getMethod("get", Object.class, Method.class, Object.class, boolean.class);
        if (get.invoke(operation, sprite, getter, north, false) == source) throw new AssertionError("Woven native scope/getter did not replace texture");
        if (getter.invoke(sprite, 0, north, false) != source) throw new AssertionError("Woven normal return leaked scope");
        try { get.invoke(operation, sprite, getter, north, true); throw new AssertionError("Fixture exception absent"); }
        catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
        }
        if (getter.invoke(sprite, 0, north, false) != source) throw new AssertionError("Woven exceptional return leaked scope");
        NativeSpriteShadows.prepare(false);
        if (get.invoke(operation, sprite, getter, north, false) != source) throw new AssertionError("Woven disabled scope retained copy");
        NativeSpriteShadows.prepare(true);
        System.out.println("Woven native sprite PASS: original IsoSprite.getTextureForFrame dynamically returns verified copy only in native scope; normal/throw exits restore scope and disabled mode returns original.");
    }

    public static final class ScopeTarget {
        public Object get(Object sprite, Method getter, Object direction, boolean fail) throws Exception {
            Object result = getter.invoke(sprite, 0, direction, false);
            if (fail) throw new IllegalArgumentException("native sprite scope fixture");
            return result;
        }
    }

    public static final class PzoScopeTarget {
        public static Object seen;
        public Object record(int unit, Object source, boolean fail) {
            seen = NativeSpriteShadows.texture(source);
            if (fail) throw new IllegalArgumentException("PZO worker fixture");
            return seen;
        }
    }

    /** Follow the real Texture producers through both native and PZO queues, including the 029 gap. */
    @SuppressWarnings("unchecked") private static void checkWovenDraw() throws Exception {
        Class<?> probe = Class.forName("PatchWeavingProbe");
        Method convert = probe.getDeclaredMethod("convert", byte[].class); convert.setAccessible(true);
        Method matcher = probe.getDeclaredMethod("matcher", Class.class, String.class, boolean.class); matcher.setAccessible(true);
        var constructor = Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class, boolean.class); constructor.setAccessible(true);
        String name = "vpiso.Patch_NativeSpriteShadowDraw";
        byte[] adviceBytes;
        try (var in = NativeSpriteShadowsProbe.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            adviceBytes = (byte[]) convert.invoke(null, in.readAllBytes());
        }
        ClassLoader adviceLoader = (ClassLoader) constructor.newInstance(Map.of(name, adviceBytes), false);
        Class<?> advice = adviceLoader.loadClass(name);
        byte[] baselineBytes;
        try (var zip = new java.util.zip.ZipFile("build/checkpoints/029/VPIsometricLighting.jar")) {
            try (var input = zip.getInputStream(zip.getEntry(name.replace('.','/') + ".class"))) {
                baselineBytes = (byte[]) convert.invoke(null, input.readAllBytes());
            }
        }
        ClassLoader baselineLoader = (ClassLoader) constructor.newInstance(Map.of(name, baselineBytes), false);
        Class<?> baselineAdvice = baselineLoader.loadClass(name);
        var match = (net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription>) matcher.invoke(null, advice, "render", false);
        Path pzo = Path.of(System.getProperty("vpiso.testPzo", "D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3805285544/mods/PZ_Optimization/42/pzopt-classes"));
        String previousOverrides = System.getProperty("vpiso.testOverrides");
        try {
            for (boolean optimized : new boolean[] { false, true }) {
                if (optimized) System.setProperty("vpiso.testOverrides", pzo.toString());
                else System.clearProperty("vpiso.testOverrides");
                for (boolean fixed : new boolean[] { false, true }) {
                    var locators = new java.util.ArrayList<net.bytebuddy.dynamic.ClassFileLocator>();
                    locators.add(new net.bytebuddy.dynamic.ClassFileLocator.Simple(Map.of(name, fixed ? adviceBytes : baselineBytes)));
                    if (optimized) locators.add(new net.bytebuddy.dynamic.ClassFileLocator.ForFolder(pzo.toFile()));
                    locators.add(net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
                    var runtimeLocator = new net.bytebuddy.dynamic.ClassFileLocator.Compound(locators);
                    String targetName = fixed ? "zombie.core.sprite.GenericSpriteRenderState" : "zombie.core.SpriteRenderer";
                    var target = net.bytebuddy.pool.TypePool.Default.of(runtimeLocator).describe(targetName).resolve();
                    if (target.getDeclaredMethods().filter(match).size() != (fixed ? 9 : 8))
                        throw new AssertionError("Texture queue overload ABI changed: " + targetName);
                    byte[] woven = new net.bytebuddy.ByteBuddy().redefine(target, runtimeLocator)
                            .visit(net.bytebuddy.asm.Advice.to(fixed ? advice : baselineAdvice, runtimeLocator).on(match)).make().getBytes();
                    ClassLoader runtime = (ClassLoader) constructor.newInstance(Map.of(targetName, woven), true);
                    checkDrawProducers(runtime, fixed, optimized);
                }
            }
        } finally {
            if (previousOverrides == null) System.clearProperty("vpiso.testOverrides");
            else System.setProperty("vpiso.testOverrides", previousOverrides);
        }
        System.out.println("Native sprite draw PASS: 9 real GenericSpriteRenderState texture overloads weave; 029 bypass reproduced for actual Texture.renderstrip / SpriteRenderer.renderflipped / PZO RecState; 030 replaces queued TextureDraw for all producers only in selected native scope; source, capture, VP rendering and disabled scope restored.");
    }

    @SuppressWarnings("unchecked") private static void checkDrawProducers(ClassLoader runtime, boolean fixed, boolean optimized) throws Exception {
        Class<?> shadows = runtime.loadClass("vpiso.NativeSpriteShadows");
        if (optimized && !(Boolean) runtime.loadClass("pzopt.Overrides").getMethod("enabled").invoke(null))
            throw new AssertionError("PZO draw fixture must use active shipped overrides and metadata");
        Class<?> textureType = runtime.loadClass(TEX), idType = runtime.loadClass(ID);
        Class<?> queueType = runtime.loadClass("zombie.core.sprite.GenericSpriteRenderState");
        Class<?> renderThread = runtime.loadClass("zombie.core.opengl.RenderThread");
        localField(renderThread, "isInitialized").set(null, true);
        localField(renderThread, "contextThread").set(null, Thread.currentThread());
        ByteBuffer pixels = BufferUtils.createByteBuffer(4 * 4 * 4);
        for (int i = 0; i < pixels.capacity(); i++) pixels.put(i, (byte) 255);
        Object id = idType.getConstructor(int.class, int.class, ByteBuffer.class).newInstance(4, 4, pixels);
        Object source = textureType.getConstructor(idType, String.class).newInstance(id, "furniture_seating_indoor_01_60");
        ByteBuffer masked = BufferUtils.createByteBuffer(pixels.capacity()); masked.put(pixels.duplicate()).clear();
        for (int i = 3; i < masked.capacity(); i += 4) masked.put(i, (byte) 0);
        Object copiedId = idType.getConstructor(int.class, int.class, ByteBuffer.class).newInstance(4, 4, masked);
        Object replacement = textureType.getConstructor(idType, String.class).newInstance(copiedId, "furniture_seating_indoor_01_60");
        Class<?> copyType = runtime.loadClass("vpiso.NativeSpriteShadows$Copy");
        var copyConstructor = copyType.getDeclaredConstructor(); copyConstructor.setAccessible(true);
        Object copy = copyConstructor.newInstance(); localField(copyType, "texture").set(copy, replacement);
        localField(copyType, "source").set(copy, source);
        ((Map<Object,Object>) localField(shadows, "COPIES").get(null)).put(source, copy);
        ((Map<Object,Object>) localField(shadows, "PRIVATE_COPIES").get(null)).put(replacement, copy);
        Class<?> rendererType = runtime.loadClass("zombie.core.SpriteRenderer");
        Object sprites = localField(rendererType, "instance").get(null);
        Object states = localField(rendererType, "states").get(sprites);
        Object queue = states.getClass().getMethod("getPopulatingActiveState").invoke(states);
        Method enter = shadows.getMethod("enter"), leave = shadows.getMethod("leave", int.class);
        String[] paths = optimized ? new String[] { "render", "renderstrip", "renderflipped", "recorder" }
                : new String[] { "render", "renderstrip", "renderflipped" };
        for (String path : paths) for (String gate : new String[] { "outside", "native", "disabled", "capture", "vp", "restored" }) {
            boolean inScope = !gate.equals("outside") && !gate.equals("restored");
            localField(shadows, "selected").set(null, !gate.equals("disabled"));
            Class<?> bridge = runtime.loadClass("vpiso.IsometricBridge");
            ThreadLocal<Boolean> capturing = (ThreadLocal<Boolean>) localField(bridge, "CAPTURING").get(null);
            ThreadLocal<Object> rendering = (ThreadLocal<Object>) localField(bridge, "RENDERING").get(null);
            capturing.set(gate.equals("capture")); rendering.set(gate.equals("vp") ? new Object() : null);
            Object destination = queue;
            if (path.equals("recorder")) {
                var recorder = runtime.loadClass("pzopt.DrawRecorder$RecState").getDeclaredConstructor(); recorder.setAccessible(true);
                destination = recorder.newInstance();
                if (destination.getClass().getSuperclass() != queueType) throw new AssertionError("PZO recorder no longer inherits native queue");
            }
            int before = localField(queueType, "numSprites").getInt(destination);
            int previous = inScope ? (Integer) enter.invoke(null) : -1;
            try {
                if (path.equals("renderstrip")) textureType.getMethod(path, int.class, int.class, int.class, int.class,
                        float.class, float.class, float.class, float.class, java.util.function.Consumer.class)
                        .invoke(source, 1, 1, 4, 4, 1f, 1f, 1f, 1f, null);
                else if (path.equals("renderflipped")) rendererType.getMethod(path, textureType, float.class, float.class, float.class, float.class,
                        float.class, float.class, float.class, float.class, java.util.function.Consumer.class)
                        .invoke(sprites, source, 1f, 1f, 4f, 4f, 1f, 1f, 1f, 1f, null);
                else if (path.equals("recorder")) queueType.getMethod("render", textureType, float.class, float.class, float.class, float.class,
                        float.class, float.class, float.class, float.class, java.util.function.Consumer.class)
                        .invoke(destination, source, 1f, 1f, 4f, 4f, 1f, 1f, 1f, 1f, null);
                else textureType.getMethod("render", float.class, float.class, float.class, float.class,
                        float.class, float.class, float.class, float.class, java.util.function.Consumer.class)
                        .invoke(source, 1f, 1f, 4f, 4f, 1f, 1f, 1f, 1f, null);
            } finally {
                if (inScope) leave.invoke(null, previous);
                capturing.remove(); rendering.remove();
            }
            if (localField(queueType, "numSprites").getInt(destination) != before + 1)
                throw new AssertionError("Real producer did not queue: " + path + "/" + gate);
            Object draw = Array.get(localField(queueType, "sprite").get(destination), before);
            Object actual = localField(runtime.loadClass("zombie.core.textures.TextureDraw"), "tex").get(draw);
            boolean replaced = gate.equals("native") && (fixed || path.equals("render"));
            if (actual != (replaced ? replacement : source))
                throw new AssertionError((fixed ? "030" : "029") + " queued wrong texture: " + optimized + "/" + path + "/" + gate);
        }
        if (((java.util.Set<?>) localField(shadows,"SUBMISSIONS").get(null)).size() != (fixed ? 5 : 0))
            throw new AssertionError("Chair submission diagnostics repeated per draw or omitted a scope state");
    }

    private static java.lang.reflect.Field localField(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static void checkBridgeGates() throws Exception {
        vpiso.Main.compatible = true;
        GameAccess.set("viewpoint.platform.BuildPin", "supported", null, Boolean.TRUE);
        GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null, new Class<?>[] { java.io.File.class }, (Object) null);
        viewpoint.platform.Graphics.FRAME_GENERATION.set(false);
        GameAccess.set("zombie.core.PerformanceSettings", "fboRenderChunk", null, true);
        Object source = ((Map<?,?>) GameAccess.field("vpiso.NativeSpriteShadows", "COPIES", null)).keySet().iterator().next();
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Object player = ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(GameAccess.type("zombie.characters.IsoPlayer"));
        Object players = GameAccess.field("zombie.characters.IsoPlayer", "players", null);
        Object[] previousPlayers = ((Object[]) players).clone();
        Object state = GameAccess.field("zombie.iso.IsoCamera", "frameState", null);
        Object previousCharacter = GameAccess.field("zombie.iso.IsoCamera", "isoCameraGameCharacter", null);
        Object previousIndex = GameAccess.field("zombie.iso.IsoCamera$FrameState", "playerIndex", state);
        vpiso.IsometricEffects effects = vpiso.IsometricEffects.DEFAULT;
        try {
        Array.set(players, 0, player);
        for (String gate : new String[] { "unready", "ready", "normal", "shadows", "master", "models", "vp", "failed", "unsupported", "nativeFbo", "splitscreen", "playerIndex", "cameraCharacter" }) {
            vpiso.IsometricBridge.setEnabled(true);
            vpiso.IsometricBridge.setModelsEnabled(false);
            GameAccess.set("vpiso.IsometricBridge", "failed", null, false);
            viewpoint.core.View.enabled = false;
            vpiso.Main.compatible = true;
            GameAccess.set("zombie.core.PerformanceSettings", "fboRenderChunk", null, true);
            GameAccess.set("viewpoint.platform.BuildPin", "supported", null, Boolean.TRUE);
            Array.set(players, 1, null);
            GameAccess.set("zombie.iso.IsoCamera$FrameState", "playerIndex", state, 0);
            GameAccess.set("zombie.iso.IsoCamera", "isoCameraGameCharacter", null, player);
            GameAccess.set("vpiso.IsometricControls", "effects", null, effects);
            viewpoint.platform.ShaderPacks.useBuiltIn("library", "high", Map.of("upscaling", "off", "reflections", "off"));
            vpiso.IsometricBridge.nativeShadowStatus(true, true, true);
            switch (gate) {
                case "unready" -> vpiso.IsometricBridge.nativeShadowStatus(true, true, false);
                case "normal" -> viewpoint.platform.ShaderPacks.useBuiltIn("normal", "high", Map.of("upscaling", "off"));
                case "shadows" -> GameAccess.set("vpiso.IsometricControls", "effects", null,
                        new vpiso.IsometricEffects(false, .55f, 1f, 64f, 2048, true, 1f, 1f, 1f, 1f));
                case "master" -> vpiso.IsometricBridge.setEnabled(false);
                case "models" -> vpiso.IsometricBridge.setModelsEnabled(true);
                case "vp" -> viewpoint.core.View.enabled = true;
                case "failed" -> GameAccess.set("vpiso.IsometricBridge", "failed", null, true);
                case "unsupported" -> GameAccess.set("viewpoint.platform.BuildPin", "supported", null, Boolean.FALSE);
                case "nativeFbo" -> GameAccess.set("zombie.core.PerformanceSettings", "fboRenderChunk", null, false);
                case "splitscreen" -> Array.set(players, 1, player);
                case "playerIndex" -> GameAccess.set("zombie.iso.IsoCamera$FrameState", "playerIndex", state, 1);
                case "cameraCharacter" -> GameAccess.set("zombie.iso.IsoCamera", "isoCameraGameCharacter", null, null);
            }
            vpiso.IsometricBridge.beforeNativeWorld();
            int scope = NativeSpriteShadows.enter();
            try {
                if ((NativeSpriteShadows.texture(source) != source) != gate.equals("ready"))
                    throw new AssertionError("Actual bridge gate failed: " + gate);
                if (!gate.equals("ready") && (Boolean) GameAccess.field("vpiso.IsometricBridge", "nativeShadowsReady", null))
                    throw new AssertionError("Blocked bridge retained ready state: " + gate);
            } finally { NativeSpriteShadows.leave(scope); vpiso.IsometricBridge.finishNativeWorld(); }
        }
        vpiso.IsometricBridge.setModelsEnabled(false); vpiso.IsometricBridge.setEnabled(true);
        GameAccess.set("vpiso.IsometricBridge", "failed", null, false); viewpoint.core.View.enabled = false;
        GameAccess.set("zombie.core.PerformanceSettings", "fboRenderChunk", null, true);
        GameAccess.set("viewpoint.platform.BuildPin", "supported", null, Boolean.TRUE);
        GameAccess.set("vpiso.IsometricControls", "effects", null, effects);
        viewpoint.platform.ShaderPacks.useBuiltIn("library", "high", Map.of("upscaling", "off", "reflections", "off"));
        GameAccess.set("zombie.iso.IsoCamera$FrameState", "playerIndex", state, 0);
        GameAccess.set("zombie.iso.IsoCamera", "isoCameraGameCharacter", null, player);
        System.out.println("Native shadow gates PASS: real beforeNativeWorld preserves originals until maps ready; Library+ready masks; Normal, shadows/master/models/VP/failure/build/FBO/splitscreen/player-index/missing-camera guards restore originals and clear stale readiness.");
        } finally {
            System.arraycopy(previousPlayers, 0, players, 0, previousPlayers.length);
            GameAccess.set("zombie.iso.IsoCamera", "isoCameraGameCharacter", null, previousCharacter);
            GameAccess.set("zombie.iso.IsoCamera$FrameState", "playerIndex", state, previousIndex);
        }
    }

    /** Weave actual PZO record methods, then execute each scope advice on a reused worker. */
    @SuppressWarnings("unchecked") private static void checkPzoScopes() throws Exception {
        Path pzo = Path.of(System.getProperty("vpiso.testPzo", "D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3805285544/mods/PZ_Optimization/42/pzopt-classes"));
        Class<?> probe = Class.forName("PatchWeavingProbe");
        Method convert = probe.getDeclaredMethod("convert", byte[].class); convert.setAccessible(true);
        var loaderConstructor = Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class, boolean.class);
        loaderConstructor.setAccessible(true);
        Object source = ((Map<?,?>) GameAccess.field("vpiso.NativeSpriteShadows", "COPIES", null)).keySet().iterator().next();
        for (String suffix : new String[] { "Bake", "Tiles" }) {
            String adviceName = "vpiso.Patch_NativeSpriteShadowPZO" + suffix;
            byte[] converted;
            try (var in = NativeSpriteShadowsProbe.class.getClassLoader().getResourceAsStream(adviceName.replace('.', '/') + ".class")) {
                converted = (byte[]) convert.invoke(null, in.readAllBytes());
            }
            Map<String,byte[]> definitions = Map.of(adviceName, converted);
            var locator = new net.bytebuddy.dynamic.ClassFileLocator.Compound(
                    new net.bytebuddy.dynamic.ClassFileLocator.Simple(definitions),
                    new net.bytebuddy.dynamic.ClassFileLocator.ForFolder(pzo.toFile()),
                    net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
            ClassLoader adviceLoader = (ClassLoader) loaderConstructor.newInstance(definitions, false);
            Class<?> advice = adviceLoader.loadClass(adviceName);
            var scope = net.bytebuddy.asm.Advice.to(advice, locator);
            String targetName = suffix.equals("Bake") ? "pzopt.BakeRecord" : "pzopt.TileRecord";
            var target = net.bytebuddy.pool.TypePool.Default.of(locator).describe(targetName).resolve();
            long count = target.getDeclaredMethods().filter(net.bytebuddy.matcher.ElementMatchers.named("record")).size();
            if (count != 1) throw new AssertionError("PZO record ABI changed: " + targetName + " " + count);
            byte[] actual = new net.bytebuddy.ByteBuddy().redefine(target, locator)
                    .visit(scope.on(net.bytebuddy.matcher.ElementMatchers.named("record"))).make().getBytes();
            if (actual.length == 0) throw new AssertionError("Empty woven PZO class");
            byte[] fixture = new net.bytebuddy.ByteBuddy().redefine(PzoScopeTarget.class)
                    .visit(scope.on(net.bytebuddy.matcher.ElementMatchers.named("record"))).make().getBytes();
            ClassLoader woven = (ClassLoader) loaderConstructor.newInstance(Map.of(PzoScopeTarget.class.getName(), fixture), false);
            Class<?> type = woven.loadClass(PzoScopeTarget.class.getName());
            Object instance = type.getConstructor().newInstance();
            Method record = type.getMethod("record", int.class, Object.class, boolean.class);
            final Throwable[] failure = new Throwable[1];
            Thread worker = new Thread(() -> {
                try {
                    if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("Worker inherited world scope");
                    if (record.invoke(instance, 0, source, false) == source) throw new AssertionError("PZO worker did not receive masked texture");
                    if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("PZO normal return leaked scope");
                    try { record.invoke(instance, 0, source, true); throw new AssertionError("PZO exception absent"); }
                    catch (java.lang.reflect.InvocationTargetException expected) {
                        if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
                    }
                    if (type.getField("seen").get(null) == source) throw new AssertionError("PZO throwing worker used original texture");
                    if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("PZO exception leaked worker scope");
                } catch (Throwable error) { failure[0] = error; }
            }, "VPISO isolated PZO worker");
            worker.start(); worker.join();
            if (failure[0] != null) throw new AssertionError(targetName + " worker scope", failure[0]);
        }
        System.out.println("PZO sprite masks PASS: actual BakeRecord.record(int) and TileRecord.record(10 arguments) weave; both worker scopes replace known textures and restore on normal/exceptional returns without inherited world state.");
    }

    /** Real native blob consumer, woven to prove it queues outside the addon and skips/releases inside. */
    @SuppressWarnings("unchecked") private static void checkWovenBlobs() throws Exception {
        Class<?> probe=Class.forName("PatchWeavingProbe");
        Method convert=probe.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
        Method matcher=probe.getDeclaredMethod("matcher",Class.class,String.class,boolean.class);matcher.setAccessible(true);
        String adviceName="vpiso.Patch_NativeFixedShadowBlobs",targetName="zombie.iso.fboRenderChunk.FBORenderShadows";
        byte[] adviceBytes;
        try(var input=NativeSpriteShadowsProbe.class.getClassLoader().getResourceAsStream(adviceName.replace('.','/')+".class")) { adviceBytes=(byte[])convert.invoke(null,input.readAllBytes()); }
        Class<?> loaderType=Class.forName("PatchWeavingProbe$BytesLoader");
        var constructor=loaderType.getDeclaredConstructor(Map.class,boolean.class);constructor.setAccessible(true);
        ClassLoader adviceLoader=(ClassLoader)constructor.newInstance(Map.of(adviceName,adviceBytes),false);
        Class<?> advice=adviceLoader.loadClass(adviceName);
        var locator=new net.bytebuddy.dynamic.ClassFileLocator.Compound(new net.bytebuddy.dynamic.ClassFileLocator.Simple(Map.of(adviceName,adviceBytes)),
                net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
        var target=net.bytebuddy.pool.TypePool.Default.of(locator).describe(targetName).resolve();
        var match=(net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription>)matcher.invoke(null,advice,"renderMain",true);
        byte[] woven=new net.bytebuddy.ByteBuddy().redefine(target,locator).visit(net.bytebuddy.asm.Advice.to(advice,locator).on(match)).make().getBytes();
        Map<String,byte[]> definitions=new java.util.HashMap<>();definitions.put(targetName,woven);
        for(String suffix:new String[]{"$Drawer","$Shadow"}) {
            String name=targetName+suffix;
            try(var input=NativeSpriteShadowsProbe.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){definitions.put(name,input.readAllBytes());}
        }
        ClassLoader loader=(ClassLoader)constructor.newInstance(definitions,false);
        Class<?> type=loader.loadClass(targetName),recordType=loader.loadClass(targetName+"$Shadow");
        var rendererConstructor=type.getDeclaredConstructor();rendererConstructor.setAccessible(true);Object blobs=rendererConstructor.newInstance();
        var recordConstructor=recordType.getDeclaredConstructor();recordConstructor.setAccessible(true);
        var recordsField=type.getDeclaredField("shadows");recordsField.setAccessible(true);
        var poolField=type.getDeclaredField("shadowPool");poolField.setAccessible(true);
        java.util.List<Object> records=(java.util.List<Object>)recordsField.get(blobs);Object pool=poolField.get(blobs);
        var poolRecords=pool.getClass().getDeclaredField("pool");poolRecords.setAccessible(true);
        Method render=type.getMethod("renderMain",int.class);
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        Object states=GameAccess.field("zombie.core.SpriteRenderer","states",sprites);
        Object queue=GameAccess.call("zombie.core.sprite.SpriteRendererStates","getPopulatingActiveState",states,new Class<?>[0]);
        int before=((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue();
        records.add(recordConstructor.newInstance());render.invoke(blobs,0);
        int submitted=((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue();
        if(submitted!=before+1||!records.isEmpty())throw new AssertionError("Native blob control did not queue the real consumer");
        int scope=NativeSpriteShadows.enter();
        try {
            records.add(recordConstructor.newInstance());int pooled=((java.util.Collection<?>)poolRecords.get(pool)).size();
            render.invoke(blobs,0);
            if(!records.isEmpty()||((java.util.Collection<?>)poolRecords.get(pool)).size()!=pooled+1
                    ||((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue()!=submitted)
                throw new AssertionError("Native fixed blobs were queued or records not released");
            NativeSpriteShadows.prepare(false);records.add(recordConstructor.newInstance());render.invoke(blobs,0);
            if(((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue()!=submitted+1)
                throw new AssertionError("Disabling dynamic shadows did not restore native blobs");
            NativeSpriteShadows.prepare(true);
            vpiso.IsometricBridge.rendering(new viewpoint.core.Frame());
            try {
                records.add(recordConstructor.newInstance());render.invoke(blobs,0);
                if(((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue()!=submitted+2)
                    throw new AssertionError("VP rendering lost its native shadow behavior");
            } finally {vpiso.IsometricBridge.rendering(null);}
        } finally {NativeSpriteShadows.leave(scope);}
        System.out.println("Native fixed blob shadows PASS: actual woven FBORenderShadows.renderMain queues outside native scope, suppresses all blob records and returns them to the pool under VP dynamic shadows, restores when off/VP rendering; original visibility consumer untouched.");
    }

    private static void checkMask(BufferedImage image, String name, int firstRow, int expected) {
        ByteBuffer original = rgba(image), copy = BufferUtils.createByteBuffer(original.capacity());
        copy.put(original.duplicate()).clear();
        int changed = NativeSpriteShadows.mask(name, image.getWidth(), image.getHeight(), copy, image.getWidth(), 0, 0);
        if (changed != expected) throw new AssertionError("Unverified mask count " + name + ": " + changed);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int p = (y * image.getWidth() + x) * 4;
            boolean removed = y >= firstRow && image.getRGB(x, y) != 0 && (image.getRGB(x, y) & 0xFFFFFF) == 0
                    && (original.get(p + 3) & 255) < 255 && original.get(p + 3) != 0;
            for (int c = 0; c < 4; c++) if (copy.get(p + c) != (removed && c == 3 ? 0 : original.get(p + c)))
                throw new AssertionError("Changed material/body byte " + name + " " + x + "," + y);
        }
        ByteBuffer unknown = rgba(image);
        if (NativeSpriteShadows.mask("another_sprite", image.getWidth(), image.getHeight(), unknown, image.getWidth(), 0, 0) != 0
                || !Arrays.equals(bytes(original), bytes(unknown))) throw new AssertionError("Other sprite altered");
        ByteBuffer tinted = rgba(image), tintedExpected = rgba(image), opaque = rgba(image), opaqueExpected = rgba(image);
        for (int p = 0; p < original.capacity(); p += 4) {
            if (original.get(p + 3) != copy.get(p + 3)) {
                tinted.put(p, (byte) 4).put(p + 1, (byte) 7).put(p + 2, (byte) 2);
                tintedExpected.put(p, (byte) 4).put(p + 1, (byte) 7).put(p + 2, (byte) 2).put(p + 3, (byte) 0);
                opaque.put(p + 3, (byte) 255); opaqueExpected.put(p + 3, (byte) 255);
            } else {
                // A custom dark partial-alpha body pixel outside the known mask must survive.
                int y = p / 4 / image.getWidth();
                if (y < firstRow && original.get(p + 3) != 0) {
                    tinted.put(p, (byte) 4).put(p + 1, (byte) 7).put(p + 2, (byte) 2).put(p + 3, (byte) 127);
                    tintedExpected.put(p, (byte) 4).put(p + 1, (byte) 7).put(p + 2, (byte) 2).put(p + 3, (byte) 127);
                }
            }
        }
        if (NativeSpriteShadows.mask(name, image.getWidth(), image.getHeight(), tinted, image.getWidth(), 0, 0) != expected
                || !Arrays.equals(bytes(tinted), bytes(tintedExpected))) throw new AssertionError("Compressed/tinted shadow coordinates or custom body pixels lost " + name);
        if (NativeSpriteShadows.mask(name, image.getWidth(), image.getHeight(), opaque, image.getWidth(), 0, 0) != 0
                || !Arrays.equals(bytes(opaque), bytes(opaqueExpected))) throw new AssertionError("Opaque custom material was erased " + name);
        ByteBuffer wrongSize = rgba(image);
        if (NativeSpriteShadows.mask(name, image.getWidth() - 1, image.getHeight(), wrongSize, image.getWidth(), 0, 0) != 0
                || !Arrays.equals(bytes(wrongSize), bytes(original))) throw new AssertionError("Unexpected sprite size was modified " + name);
    }

    private static void checkCopy(BufferedImage image, String name) throws Exception {
        checkCopy(image, name, false);
    }
    private static void checkCopy(BufferedImage image, String name, boolean compressed) throws Exception {
        int aw = image.getWidth()+22>128?256:128, ah = 256, left = 11, top = 13;
        ByteBuffer atlas = BufferUtils.createByteBuffer(aw * ah * 4);
        for (int p = 0; p < atlas.capacity(); p += 4) atlas.put(p, (byte) 23).put(p + 1, (byte) 47).put(p + 2, (byte) 91).put(p + 3, (byte) 255);
        ByteBuffer crop = rgba(image);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth() * 4; x++)
            atlas.put(((top + y) * aw + left) * 4 + x, crop.get(y * image.getWidth() * 4 + x));
        Object id = type(ID).getConstructor(int.class, int.class, ByteBuffer.class).newInstance(aw, ah, atlas);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((Number) GameAccess.call(ID, "getID", id, new Class<?>[0])).intValue());
        if (!compressed) GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, aw, ah, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, atlas);
        else if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL13.GL_TEXTURE_COMPRESSED) == 0)
            throw new AssertionError("Native compressed texture fixture did not compress");
        Object source = type(TEX).getConstructor(type(ID), String.class).newInstance(id, name);
        set(TEX, source, "width", image.getWidth()); set(TEX, source, "height", image.getHeight());
        set(TEX, source, "widthOrig", 128); set(TEX, source, "heightOrig", 256);
        set(TEX, source, "offsetX", 42.5f); set(TEX, source, "offsetY", 162.25f);
        set(TEX, source, "realWidth", 123); set(TEX, source, "realHeight", 234);
        set(TEX, source, "xStart", left / (float) aw); set(TEX, source, "yStart", top / (float) ah);
        set(TEX, source, "xEnd", (left + image.getWidth()) / (float) aw); set(TEX, source, "yEnd", (top + image.getHeight()) / (float) ah);
        int sourceId = integer(source, "getID");
        byte[] before = read(sourceId, aw, ah);
        if (!compressed && !Arrays.equals(bytes(atlas), before)) {
            int p=Arrays.mismatch(bytes(atlas),before);
            throw new AssertionError("Native texture upload changed original atlas " + name + " at " + p + " expected=" + (atlas.get(p)&255) + " actual=" + (before[p]&255));
        }
        if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("Texture changed outside native world");
        int previous = NativeSpriteShadows.enter();
        try {
            int sentinel = GL11.glGenTextures(); GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3); GL11.glBindTexture(GL11.GL_TEXTURE_2D, sentinel);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 8); GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 8);
            Object result = NativeSpriteShadows.texture(source);
            if (result != source) throw new AssertionError("First source request must remain asynchronous");
            if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != GL13.GL_TEXTURE0 + 3 || GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != sentinel
                    || GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT) != 8 || GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT) != 8)
                throw new AssertionError("Private texture creation leaked GL state");
            NativeSpriteShadows.prepare(true); assertDirty(true, "copy ready " + name + " generation=" + GameAccess.field("vpiso.NativeSpriteShadows", "READY", null));
            NativeSpriteShadows.prepare(true); assertDirty(false, "idle copy");
            result = NativeSpriteShadows.texture(source);
            if (result == source) throw new AssertionError("Native texture did not switch to the verified copy: " + name);
            int atlasCount = ((Map<?,?>) GameAccess.field("vpiso.NativeSpriteShadows", "ATLASES", null)).size();
            int copyCount = ((Map<?,?>) GameAccess.field("vpiso.NativeSpriteShadows", "COPIES", null)).size();
            for (int i = 0; i < 4; i++) if (NativeSpriteShadows.texture(result) != result)
                throw new AssertionError("Private texture copy was not idempotent");
            if (atlasCount != ((Map<?,?>) GameAccess.field("vpiso.NativeSpriteShadows", "ATLASES", null)).size()
                    || copyCount != ((Map<?,?>) GameAccess.field("vpiso.NativeSpriteShadows", "COPIES", null)).size())
                throw new AssertionError("Private wrapper created another pending copy/atlas");
            for (String field : META) if (!java.util.Objects.equals(GameAccess.field(TEX, field, source), GameAccess.field(TEX, field, result)))
                throw new AssertionError("Native texture metadata lost: " + field);
            ByteBuffer expected = BufferUtils.createByteBuffer(before.length); expected.put(before).clear();
            NativeSpriteShadows.mask(name, image.getWidth(), image.getHeight(), expected, aw, left, top);
            if (!Arrays.equals(bytes(expected), read(integer(result, "getID"), aw, ah))) throw new AssertionError("Copied GPU material differs from mask");
            if (!Arrays.equals(before, read(sourceId, aw, ah))) throw new AssertionError("Shared source atlas mutated");
            viewpoint.core.Frame frame = new viewpoint.core.Frame();
            vpiso.IsometricBridge.rendering(frame);
            try { if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("VP render received native copy"); }
            finally { vpiso.IsometricBridge.rendering(null); }
            @SuppressWarnings("unchecked") ThreadLocal<Boolean> capturing = (ThreadLocal<Boolean>) GameAccess.field("vpiso.IsometricBridge", "CAPTURING", null);
            capturing.set(true);
            try { if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("VP capture received native copy"); }
            finally { capturing.remove(); }
            final Object[] other = new Object[1]; Thread t = new Thread(() -> other[0] = NativeSpriteShadows.texture(source)); t.start(); t.join();
            if (other[0] != source) throw new AssertionError("Native scope leaked across threads");
            NativeSpriteShadows.prepare(false); assertDirty(true, "off transition");
            if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("Disabled mode retained altered material");
            NativeSpriteShadows.prepare(false); assertDirty(false, "idle off");
            NativeSpriteShadows.prepare(true); assertDirty(true, "on transition");
            NativeSpriteShadows.prepare(true); assertDirty(false, "idle on");
            GL11.glDeleteTextures(sentinel);
        } finally { NativeSpriteShadows.leave(previous); }
        if (NativeSpriteShadows.texture(source) != source) throw new AssertionError("Native scope not restored");
    }

    private static void cacheFixture() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        Object cell = unsafe.allocateInstance(type("zombie.iso.IsoCell"));
        Object map = unsafe.allocateInstance(type("zombie.iso.IsoChunkMap"));
        Object chunk = unsafe.allocateInstance(type("zombie.iso.IsoChunk"));
        Object maps = Array.newInstance(map.getClass(), 4); Array.set(maps, 0, map); set("zombie.iso.IsoCell", cell, "chunkMap", maps);
        int width = (Integer) GameAccess.field("zombie.iso.IsoChunkMap", "chunkGridWidth", null);
        Object chunks = Array.newInstance(chunk.getClass(), width * width); Array.set(chunks, 0, chunk); set("zombie.iso.IsoChunkMap", map, "chunksSwapB", chunks);
        set("zombie.iso.IsoChunk", chunk, "lightingNeverDone", new boolean[4]);
        levels = type(LEVELS).getConstructor(int.class, chunk.getClass()).newInstance(0, chunk);
        Object buckets = Array.newInstance(type(LEVELS), 4); Array.set(buckets, 0, levels); set("zombie.iso.IsoChunk", chunk, "renderLevels", buckets);
        renderer = GameAccess.field(CELL, "instance", null); previousCell = GameAccess.field(CELL, "cell", renderer); set(CELL, renderer, "cell", cell);
        clear = type(LEVELS).getMethod("clearDirty", int.class, float.class); dirty = type(LEVELS).getMethod("isDirty", int.class, long.class, float.class);
        clear.invoke(levels, 0, 1f);
    }
    private static void assertDirty(boolean expected, String stage) throws Exception {
        if ((Boolean) dirty.invoke(levels, 0, 2048L, 1f) != expected) throw new AssertionError("Cache redraw at " + stage);
        clear.invoke(levels, 0, 1f);
    }
    private static byte[] read(int texture, int width, int height) {
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        ByteBuffer out = BufferUtils.createByteBuffer(width * height * 4); GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, out);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); return bytes(out);
    }
    private static ByteBuffer rgba(BufferedImage image) {
        ByteBuffer out = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int p = image.getRGB(x, y); out.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >> 24));
        }
        return out.flip();
    }
    private static byte[] bytes(ByteBuffer data) { byte[] out = new byte[data.capacity()]; data.duplicate().clear().get(out); return out; }
    private static int integer(Object texture, String method) { return ((Number) GameAccess.call(TEX, method, texture, new Class<?>[0])).intValue(); }
    private static Class<?> type(String name) { return GameAccess.type(name); }
    private static void set(String owner, Object object, String field, Object value) { GameAccess.set(owner, field, object, value); }
}
