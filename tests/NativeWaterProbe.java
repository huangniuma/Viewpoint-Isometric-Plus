import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.NativeWater;

/** Actual original water producers/queues and geometry consumer, with a controlled world fixture. */
public final class NativeWaterProbe {
    private static final Class<?>[] NONE = new Class<?>[0];
    private static Object unsafe, water, chunk, renderer, queue, states, body, shore, bodyData, shoreData;
    private static Method allocate;
    private static int mainIndex;

    public static void main(String[] args) throws Exception {
        Field f = type("sun.misc.Unsafe").getDeclaredField("theUnsafe"); f.setAccessible(true); unsafe = f.get(null);
        allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        fixture();
        NativeWater.queue(renderer, 0);
        equal("body and shore commands", count(), 2);
        equal("original body copied", integer(bodyData, "numSquares"), 1);
        equal("original shore copied", integer(shoreData, "numSquares"), 1);
        Object first = command(0), second = command(1);
        equal("body type", get(first, "type").toString(), "DrawWater");
        equal("body range start", integer(first, "c"), 0); equal("body range size", integer(first, "d"), 1);
        equal("shore type", get(second, "type").toString(), "DrawWater");
        yes("body flag", !(Boolean) get(first, "flipped")); yes("shore flag", (Boolean) get(second, "flipped"));
        near("real WaterShader wind snapshot", number(first, "u0"), .2f);
        near("real WaterShader time snapshot", number(first, "v0"), 17f);
        near("real WaterShader rain snapshot", number(first, "u3"), .3f);
        float[] data = (float[]) get(bodyData, "data");
        for (int i = 0; i < 4; i++) {
            float expected = nativeDepth(1 + (i == 1 || i == 2 ? 1 : 0), 1 + (i >= 2 ? 1 : 0));
            near("native body depth " + i, data[i * 8 + 7], expected + .001f);
        }
        equal("native light corner order", Float.floatToRawIntBits(data[6]), 0xff6080a0);
        equal("native light corner order 3", Float.floatToRawIntBits(data[14]), 0xff90b0d0);
        equal("other SpriteRenderer state retained", integer(Array.get(Array.get(get(water, "renderData"), (mainIndex + 1) % 3), 0), "numSquares"), 7);
        // A later ordinary native call keeps its own original bias; our correction is range scoped.
        invoke(water, "render", new Class<?>[] { ArrayList.class, int.class }, new ArrayList<>(java.util.List.of(body)), 0);
        near("ordinary later producer keeps +.001", ((float[]) get(bodyData, "data"))[39], nativeDepth(1, 1) + .001f);
        near("earlier body range stable", ((float[]) get(bodyData, "data"))[7], nativeDepth(1, 1) + .001f);
        near("shore scoped corrected depth", ((float[]) get(shoreData, "data"))[7], nativeDepth(2, 1) - .001f);
        set(bodyData, "numSquares", 1);
        cpuGuards();
        gpu();
        System.out.println("Native water PASS: production NativeWater raw-square scan despite empty native caches; actual IsoWater.render/renderShore -> WaterShader weather snapshot -> SpriteRenderer DrawWater ranges, native light/depth data, scoped bias and triple-state isolation; actual native waterGeometry GPU GL_LESS occlusion, native depthMask(false), body/shore coverage and original-bias negative control. Controlled map/lighting/geometry inputs; no live save or complete native optics acceptance.");
    }

    @SuppressWarnings("unchecked")
    private static void fixture() throws Exception {
        water = fresh("zombie.iso.IsoWater"); setStatic("zombie.iso.IsoWater", "instance", water);
        setStatic("zombie.iso.IsoWater", "isShaderEnable", true);
        set(water, "waterParamWindInt", type("org.joml.Vector2f").getConstructor(float.class, float.class).newInstance(.2f, .4f)); set(water, "waterTime", 17f);
        for (String name : new String[] { "renderData", "renderDataShore" }) {
            Class<?> c = type("zombie.iso.IsoWater$RenderData");
            Object all = Array.newInstance(c, 3, 4);
            Constructor<?> ctor = c.getDeclaredConstructor(); ctor.setAccessible(true);
            for (int i = 0; i < 3; i++) for (int p = 0; p < 4; p++) Array.set(Array.get(all, i), p, ctor.newInstance());
            set(water, name, all);
        }
        Object shader = fresh("zombie.iso.WaterShader"), specific = fresh("zombie.core.opengl.Shader$ShaderProgramSpecific");
        Object program = fresh("zombie.core.opengl.ShaderProgram"); set(program, "shaderId", 19);
        set(specific, "shaderProgram", program); set(shader, "shaderProgramSpecific", specific); set(shader, "shaderProgramInitialized", true);
        set(water, "effect", shader);
        Object puddles = fresh("zombie.iso.IsoPuddles"), rain = fresh("zombie.iso.IsoPuddles$PuddlesFloat");
        set(rain, "finalValue", .3f); set(puddles, "rainIntensity", rain); setStatic("zombie.iso.IsoPuddles", "instance", puddles);
        Object sky = fresh("zombie.iso.sprite.SkyBox");
        set(sky, "renderLimit", type("zombie.core.utils.UpdateLimit").getConstructor(long.class).newInstance(1000L));
        setStatic("zombie.iso.sprite.SkyBox", "instance", sky);
        setStatic("zombie.GameTime", "instance", fresh("zombie.GameTime"));
        Object frame = staticGet("zombie.iso.IsoCamera", "frameState");
        set(frame, "playerIndex", 0); set(frame, "camCharacterX", 1f); set(frame, "camCharacterY", 1f);
        set(frame, "camCharacterZ", 0f); set(frame, "offscreenWidth", 640); set(frame, "offscreenHeight", 480);
        Object camera = Array.get(staticGet("zombie.iso.IsoCamera", "cameras"), 0); set(camera, "zoom", 1f);
        chunk = fresh("zombie.iso.IsoChunk"); set(chunk, "lightingNeverDone", new boolean[4]); set(chunk, "renderLevels", Array.newInstance(type("zombie.iso.fboRenderChunk.FBORenderLevels"), 4));
        Object level = type("zombie.iso.fboRenderChunk.FBORenderLevels").getConstructor(int.class, chunk.getClass()).newInstance(0, chunk);
        Array.set(get(chunk, "renderLevels"), 0, level); invoke(level, "setOnScreen", new Class<?>[] { int.class, boolean.class }, 0, true);
        Object cutaways = type("zombie.iso.fboRenderChunk.FBORenderCutaways$ChunkLevelsData").getConstructor(chunk.getClass()).newInstance(chunk);
        set(chunk, "cutawayData", cutaways);
        Object cutaway = invoke(cutaways, "getDataForLevel", new Class<?>[] { int.class }, 0);
        byte[][] flags = (byte[][]) get(cutaway, "squareFlags"); Arrays.fill(flags[0], (byte) 1);
        body = square(1, 1, false, true); shore = square(2, 1, true, false);
        Object invalid = square(3, 1, false, false), hidden = square(4, 1, false, true);
        flags[0][4 + 8] = 0;
        Object squares = Array.newInstance(type("zombie.iso.IsoGridSquare"), 1, 64);
        Array.set(Array.get(squares, 0), 9, body); Array.set(Array.get(squares, 0), 10, shore);
        Array.set(Array.get(squares, 0), 11, invalid); Array.set(Array.get(squares, 0), 12, hidden); set(chunk, "squares", squares);
        renderer = staticGet("zombie.iso.fboRenderChunk.FBORenderCell", "instance");
        Object state = Array.get(get(renderer, "perPlayerData"), 0); ((ArrayList<Object>) get(state, "onScreenChunks")).add(chunk);
        Object sprites = staticGet("zombie.core.SpriteRenderer", "instance"); states = get(sprites, "states");
        queue = invoke(states, "getPopulatingActiveState", NONE); mainIndex = ((Number) invoke(sprites, "getMainStateIndex", NONE)).intValue();
        bodyData = Array.get(Array.get(get(water, "renderData"), mainIndex), 0); shoreData = Array.get(Array.get(get(water, "renderDataShore"), mainIndex), 0);
        set(Array.get(Array.get(get(water, "renderData"), (mainIndex + 1) % 3), 0), "numSquares", 7);
        set(queue, "numSprites", 0);
    }

    private static Object square(int x, int y, boolean bank, boolean wet) throws Exception {
        Object square = fresh("zombie.iso.IsoGridSquare"); set(square, "x", x); set(square, "y", y); set(square, "chunk", chunk);
        set(square, "objects", new ArrayList<>()); set(square, "cachedScreenValue", 2); set(square, "cachedScreenX", 32f); set(square, "cachedScreenY", 32f);
        Class<?> lighting = type("zombie.iso.IsoGridSquare$ILighting");
        Object light = Proxy.newProxyInstance(lighting.getClassLoader(), new Class<?>[] { lighting }, (p, m, a) -> {
            if (m.getName().equals("lightverts")) return new int[] { 0xff6080a0, 0xff7090b0, 0xff80a0c0, 0xff90b0d0 }[(Integer) a[0]];
            throw new AssertionError("Unexpected water lighting request " + m);
        });
        Object lights = Array.newInstance(lighting, 4); for (int p = 0; p < 4; p++) Array.set(lights, p, light); set(square, "lighting", lights);
        Object geometry = type("zombie.iso.IsoWaterGeometry").getConstructor().newInstance(); set(geometry, "square", square);
        set(geometry, "renderInit", true); set(geometry, "hasWater", wet); set(geometry, "shore", bank);
        set(geometry, "depth", bank ? new float[] { 0, 1, 1, 0 } : new float[] { 1, 1, 1, 1 });
        set(geometry, "x", new float[] { -.8f, .8f, .8f, -.8f }); set(geometry, "y", new float[] { -.8f, -.8f, .8f, .8f });
        set(square, "water", geometry); return square;
    }

    private static void cpuGuards() throws Exception {
        int old = count(); setStatic("zombie.iso.IsoWater", "isShaderEnable", false); yes("shader off availability",!NativeWater.available()); NativeWater.queue(renderer, 0); equal("shader off", count(), old);
        setStatic("zombie.iso.IsoWater", "isShaderEnable", true);
        Object frame = staticGet("zombie.iso.IsoCamera", "frameState"); set(frame, "camCharacterZ", -1f); NativeWater.queue(renderer, 0); equal("underground", count(), old); set(frame, "camCharacterZ", 0f);
        Object effect = get(water, "effect"); set(water, "effect", null); yes("pending availability",!NativeWater.available()); NativeWater.queue(renderer, 0); equal("lazy shader pending", count(), old); set(water, "effect", effect);
        yes("initialized shader availability", NativeWater.available());
        boolean debug = (Boolean) staticGet("zombie.core.Core", "debug"); setStatic("zombie.core.Core", "debug", true);
        Object tiles = get(get(staticGet("zombie.debug.DebugOptions", "instance"), "terrain"), "renderTiles");
        invoke(get(tiles, "waterBody"), "setValue", new Class<?>[] { boolean.class }, false);
        set(queue, "numSprites", 0); NativeWater.queue(renderer, 0); equal("native body debug switch", count(), 1); yes("only shore command", (Boolean) get(command(0), "flipped"));
        invoke(get(tiles, "waterBody"), "setValue", new Class<?>[] { boolean.class }, true);
        invoke(get(tiles, "waterShore"), "setValue", new Class<?>[] { boolean.class }, false);
        set(queue, "numSprites", 0); NativeWater.queue(renderer, 0); equal("native shore debug switch", count(), 1); yes("only body command", !(Boolean) get(command(0), "flipped"));
        invoke(get(tiles, "waterShore"), "setValue", new Class<?>[] { boolean.class }, true);
        set(queue, "numSprites", 0); NativeWater.queue(renderer, 0); equal("both native switches restored", count(), 2);
        setStatic("zombie.core.Core", "debug", debug);
    }

    private static void gpu() throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5); GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(32, 32, "Native IsoWater fixture", 0, 0); if (window == 0) throw new AssertionError("Hidden GL unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            type("zombie.core.VBO.GLVertexBufferObject").getMethod("init").invoke(null);
            invoke(staticGet("zombie.iso.IsoPuddles", "VBOs"), "startFrame", NONE);
            set(states, "rendering", queue);
            int program = program(); GL20.glUseProgram(program); GL30.glBindVertexArray(GL30.glGenVertexArrays());
            for (int a = 0; a < 4; a++) GL20.glEnableVertexAttribArray(a);
            int fbo = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            int color = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE), depth = texture(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
            equal("FBO", GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER), GL30.GL_FRAMEBUFFER_COMPLETE); GL11.glViewport(0, 0, 32, 32);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glEnable(GL11.GL_BLEND);
            float plane = nativeDepth(1.5f, 1.5f);
            yes("native body visible with VP water removed", draw(1f, false) > 90);
            yes("elevated geometry occludes native body", draw(plane - .01f, false) == 0);
            yes("body does not override land at same depth", draw(plane, false) == 0);
            float bankPlane = nativeDepth(2.5f, 1.5f);
            yes("native shore visible over unbiased mapped land", draw(bankPlane, true) > 20);
            float[] data = (float[]) get(shoreData, "data"); for (int i = 7; i < 32; i += 8) data[i] += .002f;
            yes("original shore positive bias rejected by mapped land", draw(bankPlane, true) == 0);
            for (int i = 7; i < 32; i += 8) data[i] -= .002f;
            yes("elevated geometry occludes native shore", draw(bankPlane - .01f, true) == 0);
            equal("native GL depth function", GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.GL_LESS);
            yes("native depth writes stay off", !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
            equal("GPU error", GL11.glGetError(), GL11.GL_NO_ERROR);
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }

    private static int draw(float storedDepth, boolean shore) throws Exception {
        GL11.glDepthMask(true); GL11.glClearDepth(storedDepth); GL11.glClearColor(0, 0, 0, 0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        invoke(water, "waterGeometry", new Class<?>[] { int.class, int.class, boolean.class }, 0, 1, shore);
        ByteBuffer pixel = BufferUtils.createByteBuffer(4); GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        FloatBuffer depth = BufferUtils.createFloatBuffer(1); GL11.glReadPixels(16, 16, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        near("water preserves receiver depth", depth.get(0), storedDepth); return pixel.get(2) & 255;
    }
    private static int texture(int internal, int format, int type) {
        int result = GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D, result);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, 32, 32, 0, format, type, 0L); return result;
    }
    private static int program() {
        int result = GL20.glCreateProgram();
        String[] source = { "#version 330\nlayout(location=0) in vec2 p; layout(location=2) in float wet; layout(location=6) in float d; out float depth; out float a; void main(){gl_Position=vec4(p,0,1);depth=d;a=wet;}",
                "#version 330\nin float depth;in float a;out vec4 color;void main(){gl_FragDepth=depth;color=vec4(.1,.4,.8,a);}" };
        for (int i = 0; i < 2; i++) { int shader = GL20.glCreateShader(i == 0 ? GL20.GL_VERTEX_SHADER : GL20.GL_FRAGMENT_SHADER); GL20.glShaderSource(shader, source[i]); GL20.glCompileShader(shader); if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(shader)); GL20.glAttachShader(result, shader); }
        GL20.glLinkProgram(result); if (GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(GL20.glGetProgramInfoLog(result)); return result;
    }
    private static float nativeDepth(float x, float y) throws Exception { Object result = type("zombie.iso.IsoDepthHelper").getMethod("getSquareDepthData", int.class, int.class, float.class, float.class, float.class).invoke(null, 1, 1, x, y, 0f); return number(result, "depthStart"); }
    private static Object command(int i) throws Exception { return Array.get(get(queue, "sprite"), i); }
    private static int count() throws Exception { return integer(queue, "numSprites"); }
    private static Class<?> type(String n) throws ClassNotFoundException { return Class.forName(n); }
    private static Object fresh(String n) throws Exception { return allocate.invoke(unsafe, type(n)); }
    private static Field field(Class<?> c, String n) throws Exception { for (; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(n); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) {} throw new NoSuchFieldException(n); }
    private static Object get(Object o, String n) throws Exception { return field(o.getClass(), n).get(o); }
    private static void set(Object o, String n, Object v) throws Exception { field(o.getClass(), n).set(o, v); }
    private static Object staticGet(String c, String n) throws Exception { return field(type(c), n).get(null); }
    private static void setStatic(String c, String n, Object v) throws Exception { field(type(c), n).set(null, v); }
    private static int integer(Object o, String n) throws Exception { return ((Number) get(o, n)).intValue(); }
    private static float number(Object o, String n) throws Exception { return ((Number) get(o, n)).floatValue(); }
    private static Object invoke(Object o, String n, Class<?>[] s, Object... a) throws Exception { return o.getClass().getMethod(n, s).invoke(o, a); }
    private static void equal(String n, Object a, Object b) { if (!a.equals(b)) throw new AssertionError(n + ": " + a + " expected " + b); }
    private static void yes(String n, boolean v) { if (!v) throw new AssertionError(n); }
    private static void near(String n, float a, float b) { if (!Float.isFinite(a) || Math.abs(a - b) > .00001f) throw new AssertionError(n + ": " + a + " expected " + b); }
}
