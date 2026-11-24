import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.SceneData;
import vpiso.NativeViewData;

/** Actual native square getters, snapshot packing, and hidden-context GPU readback. */
public final class NativeViewProbe {
    private static final Map<String, Object> SQUARES = new HashMap<>();
    private static final Map<Object, Float> BLACKOUT = new HashMap<>();
    private static final Class<?> SQUARE;
    private static final Object UNSAFE;
    private static final Method ALLOCATE;
    private static int lightingReads, legacyDarkReads;

    static {
        try {
            SQUARE = Class.forName("zombie.iso.IsoGridSquare");
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafe.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            UNSAFE = singleton.get(null);
            ALLOCATE = unsafe.getMethod("allocateInstance", Class.class);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }

    public static void main(String[] args) throws Exception {
        NativeViewData data = nativeSnapshot();
        nativeColumns();
        gpuReadback(data);
        System.out.println("Native view: real square cache refresh/capability getters, no extra unseen/darkMulti tile mask, native endpoint branches, blacked-out upper-floor preservation, missing-floor fallback, negative coordinates, blackout packing, and GL-state recovery passed.");
    }

    private static NativeViewData nativeSnapshot() throws Exception {
        Object bindings = field(Class.forName("vpiso.NativeViewData$BindingsHolder"), "API").get(null);
        Class<?> bindingType = bindings.getClass();
        Field getSquare = field(bindingType, "square");
        Field blacked = field(bindingType, "blacked");
        Field fade = field(bindingType, "fade");
        Object oldSquare = getSquare.get(bindings), oldBlacked = blacked.get(bindings), oldFade = fade.get(bindings);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        getSquare.set(bindings, lookup.findStatic(NativeViewProbe.class, "squareAt",
                MethodType.methodType(Object.class, Object.class, int.class, int.class, int.class)));
        blacked.set(bindings, lookup.findStatic(NativeViewProbe.class, "isBlack",
                MethodType.methodType(boolean.class, Object.class, Object.class)));
        fade.set(bindings, lookup.findStatic(NativeViewProbe.class, "fadeOf",
                MethodType.methodType(float.class, Object.class, Object.class)));
        try {
            SQUARES.put(key(-8,-8,-1), square(true, 1f, 1, "WindowN"));
            SQUARES.put(key(-7,-8,-1), square(true, .25f, 0, "WindowN"));
            SQUARES.put(key(-8,-7,-1), square(false, 0f, 2, "WindowW"));
            Object unseenBuilding = square(false, 0f, 0);
            SQUARES.put(key(-8,-8,0), unseenBuilding);
            BLACKOUT.put(unseenBuilding, 1f);
            SQUARES.put(key(-7,-8,1), square(true, .5f, 2, "WindowW"));
            SQUARES.put(key(-6,-8,-1), square(true, 1.7f, 0));
            Object black = square(true, .8f, 0);
            SQUARES.put(key(-5,-8,-1), black);
            BLACKOUT.put(black, .5f);
            SQUARES.put(key(-4,-8,-1), square(true, -.5f, 0));
            SQUARES.put(key(-3,-8,-1), square(true, Float.NaN, 0));
            SQUARES.put(key(-2,-8,-1), square(true, 1f, 0, "WindowN", "WindowW", "WallSE"));

            Frame frame = new Frame();
            frame.camX = -6.5f; frame.camY = -7.25f; frame.camZ = -1f;
            mesh(frame, 0, -8,-8,-1, SceneData.VISIBLE);
            mesh(frame, 1, 0,-8,1, SceneData.NEAR);
            mesh(frame, 2, 20000,20000,31, SceneData.CASTS_SHADOW);
            frame.scene.meshCount = 3;
            NativeViewData data = new NativeViewData();
            data.capture(frame, new Object(), 0, new Object());
            equal("native square reads", lightingReads, 10);
            equal("no legacy tile darkness reads", legacyDarkReads, 0);
            equal("main-view minX", integer(data,"minX"), -9);
            equal("main-view minY", integer(data,"minY"), -9);
            equal("main-view width", integer(data,"width"), 18);
            equal("main-view height", integer(data,"height"), 10);
            equal("basement layer", integer(data,"minLevel"), -1);
            equal("main-view levels", integer(data,"layers"), 3);
            tile(data,-8,-8,-1,255,111,128,192);
            tile(data,-7,-8,-1,255,207,224,192);
            tile(data,-8,-7,-1,255,207,128,160);
            tile(data,-8,-8,0,0,207,224,128);
            tile(data,-7,-8,0,0,0,0,0);
            tile(data,-6,-8,-1,255,207,224,128);
            tile(data,-5,-8,-1,128,207,224,128);
            tile(data,-4,-8,-1,255,207,224,128);
            tile(data,-3,-8,-1,255,207,224,128);
            tile(data,-2,-8,-1,255,207,224,128);
            // Building blackouts and missing-square markers must survive reuse of the buffer.
            data.capture(frame, new Object(), 0, new Object());
            tile(data,-7,-8,0,0,0,0,0);
            // Shadow-only geometry cannot inflate CPU/GPU visibility coverage.
            mesh(frame, 3, -800,-800,-32, SceneData.VISIBLE);
            frame.scene.meshCount = 4;
            NativeViewData bounded = new NativeViewData();
            bounded.capture(frame, new Object(), 0, new Object());
            if (integer(bounded,"width") > 192 || integer(bounded,"height") > 192 || integer(bounded,"layers") > 64)
                throw new AssertionError("Snapshot allocation is unbounded");
            return data;
        } finally {
            getSquare.set(bindings, oldSquare);
            blacked.set(bindings, oldBlacked);
            fade.set(bindings, oldFade);
        }
    }

    public static Object squareAt(Object ignored, int x, int y, int z) { return SQUARES.get(key(x,y,z)); }
    public static boolean isBlack(Object ignored, Object square) { return BLACKOUT.containsKey(square); }
    public static float fadeOf(Object ignored, Object square) { return BLACKOUT.get(square); }

    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    private static Object square(boolean seen, float dark, int flags, String... wallFlags) throws Exception {
        Object square = allocate(SQUARE);
        Class<?> lightingType = Class.forName("zombie.iso.IsoGridSquare$ILighting");
        Object light = Proxy.newProxyInstance(lightingType.getClassLoader(), new Class<?>[] {lightingType}, (proxy, method, args) -> {
            if (method.getName().equals("bSeen") && method.getParameterCount() == 0) {
                lightingReads++;
                return seen;
            }
            if (method.getName().equals("darkMulti") && method.getParameterCount() == 0) {
                legacyDarkReads++;
                return dark;
            }
            throw new AssertionError("Unexpected lighting getter: " + method);
        });
        Object lights = Array.newInstance(lightingType,4);
        for (int i = 0; i < 4; i++) Array.set(lights,i,light);
        field(SQUARE,"lighting").set(square,lights);
        field(SQUARE,"targetPlayerCutawayFlags").set(square,new byte[] {(byte)flags,0,0,0});
        field(SQUARE,"playerCutawayFlags").set(square,new byte[] {(byte)flags,0,0,0});
        field(SQUARE,"playerCutawayFlagLockUntilTimes").set(square,new long[4]);
        field(SQUARE,"objects").set(square,new java.util.ArrayList<>());
        Class<?> props = Class.forName("zombie.core.properties.PropertyContainer");
        Object properties = props.getConstructor().newInstance();
        Class<?> flagType = Class.forName("zombie.iso.SpriteDetails.IsoFlagType");
        Method set = props.getMethod("set",flagType);
        for (String wallFlag : wallFlags) set.invoke(properties,flagType.getField(wallFlag).get(null));
        field(SQUARE,"properties").set(square,properties);
        return square;
    }

    private static void mesh(Frame frame, int i, int x, int y, int z, byte flags) throws Exception {
        ChunkMeshData mesh = (ChunkMeshData) allocate(ChunkMeshData.class);
        field(ChunkMeshData.class,"level").setInt(mesh,z);
        frame.scene.meshes[i] = mesh;
        frame.scene.meshFlags[i] = flags;
        frame.scene.meshOffsets[i*3] = frame.camX - x;
        frame.scene.meshOffsets[i*3+2] = frame.camY - y;
    }

    private static void nativeColumns() throws Exception {
        Method north = NativeViewData.class.getDeclaredMethod("northColumn",int.class,int.class,int.class,int.class);
        Method west = NativeViewData.class.getDeclaredMethod("westColumn",int.class,int.class,int.class,int.class);
        north.setAccessible(true); west.setAccessible(true);
        // Tuples are (C,E,W,N,expected) or (C,S,N,W,expected), matching the native ABI.
        for (int[] c : new int[][] {{1,1,1,2,700},{1,4,1,2,444},{1,4,4,2,956},{1,4,1,8,956},{0,0,0,0,828},{0,1,0,0,956}})
            equal("native N endpoint", (Integer) north.invoke(null,c[0],c[1],c[2],c[3]),c[4]);
        for (int[] c : new int[][] {{1,2,2,1,512},{1,2,8,1,768},{1,2,2,4,768},{4,2,2,1,768},{0,0,0,0,896},{2,0,8,1,768},{2,8,2,1,256},{2,0,2,4,768}})
            equal("native W endpoint", (Integer) west.invoke(null,c[0],c[1],c[2],c[3]),c[4]);
    }

    private static void gpuReadback(NativeViewData data) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(16,16,"VPISO native visibility readback",0,0);
        if (window == 0) { GLFW.glfwTerminate(); throw new IllegalStateException("Hidden GL context unavailable"); }
        GlProgram program = null;
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            if (GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) <= 142)
                throw new AssertionError("Native visibility unit142 is unavailable");
            String vertex = """
                #version 450 compatibility
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(p*2.0-1.0,0.0,1.0);
                }
                """;
            String fragment = "#version 450 compatibility\n" + NativeViewData.GLSL + """
                uniform vec3 sampleWorld;
                uniform vec3 sampleScene;
                uniform float mode;
                uniform sampler2D otherType;
                out vec4 result;
                void main() {
                    result = mode < 0.5 ? vec4(vpIsoNativeDark(sampleScene)) : vpIsoNativeRule(sampleWorld);
                    result += texture(otherType,vec2(0.5))*0.000001;
                }
                """;
            program = GlProgram.fromSources("VPISO native visibility fixture",
                    new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of("result",0),List.of("native visibility fixture"));
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            int fbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            int color = texture2D(new byte[] {0,0,0,0});
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new AssertionError("Readback framebuffer incomplete");
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture2D(new byte[] {0,0,0,(byte)255});
            GL11.glViewport(0,0,1,1);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            program.use(); NativeViewData.onUse(program);
            equal("disabled array sampler assignment",GL20.glGetUniformi(program.id(),program.location("vpIsoNativeTiles")),142);
            sample(program,0,-8,-8,0,255,255,255,255);

            int sentinelTexture = GL11.glGenTextures();
            int sentinelSampler = GL33.glGenSamplers();
            GL13.glActiveTexture(GL13.GL_TEXTURE0+142);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,sentinelTexture);
            GL33.glBindSampler(142,sentinelSampler);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+7);
            int[] names = {GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL12.GL_UNPACK_IMAGE_HEIGHT,
                    GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS,GL12.GL_UNPACK_SKIP_IMAGES};
            int[] values = {8,11,13,2,3,1};
            for (int i = 0; i < names.length; i++) GL11.glPixelStorei(names[i],values[i]);
            int pbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER,128L,GL15.GL_DYNAMIC_DRAW);
            data.begin();
            try {
                for (int i = 0; i < names.length; i++) equal("unpack state restored after upload",GL11.glGetInteger(names[i]),values[i]);
                equal("PBO restored after upload",GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),pbo);
                equal("snapshot sampler override",GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,142),0);
                program.use(); NativeViewData.onUse(program);
                sample(program,0,-8,-8,-1,255,255,255,255);
                sample(program,0,-7,-8,-1,255,255,255,255);
                sample(program,0,-8,-7,-1,255,255,255,255);
                sample(program,0,-8,-8,0,0,0,0,0);
                sample(program,0,-8,-8,1,0,0,0,0);
                sample(program,0,-7,-8,0,255,255,255,255);
                sample(program,0,-7,-8,1,255,255,255,255);
                sample(program,0,-7,-8,20,255,255,255,255);
                sample(program,0,-6,-8,0,255,255,255,255);
                sample(program,0,-5,-8,-1,128,128,128,128);
                sample(program,0,-200,-200,0,255,255,255,255);
                sample(program,1,-8,-8,-1,255,111,128,192);
                sample(program,1,-8,-8,0,0,207,224,128);
                sample(program,1,-8,-7,-1,255,207,128,160);
                sample(program,1,-7,-8,0,0,0,0,0);
            } finally { data.end(); }
            equal("active texture restored",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),GL13.GL_TEXTURE0+7);
            equal("array sampler restored",GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,142),sentinelSampler);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+142);
            equal("array texture restored",GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY),sentinelTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+7);
            // Rebinding the same snapshot exercises the no-reallocation path.
            data.begin(); data.end();
            equal("active texture restored on reuse",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),GL13.GL_TEXTURE0+7);
            program.use(); NativeViewData.onUse(program);
            sample(program,0,-8,-8,0,255,255,255,255);
            equal("OpenGL error",GL11.glGetError(),GL11.GL_NO_ERROR);
            System.out.println("Native visibility GPU readback passed on " + GL11.glGetString(GL11.GL_RENDERER));
        } finally {
            data.end();
            if (program != null) program.delete();
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static int texture2D(byte[] bytes) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        ByteBuffer pixels = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,1,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        return texture;
    }

    private static void sample(GlProgram program, int mode, float x, float y, float z, int... expected) {
        // Samples the interior of negative world tiles, using the actual scene/world convention.
        float worldX = x+.25f, worldY = y+.25f, worldZ = z+.125f;
        program.set("mode",(float)mode);
        program.set("sampleWorld",worldX,worldY,worldZ);
        program.set("sampleScene",-6.5f-worldX,(worldZ+1f)*2.4494896f,-7.25f-worldY);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
        for (int i = 0; i < 4; i++) {
            int actual = pixel.get(i)&255;
            if (Math.abs(actual-expected[i]) > 1)
                throw new AssertionError("GPU " + (mode == 0 ? "darkness" : "rule") + " at " + key((int)x,(int)y,(int)z)
                        + " channel" + i + " expected=" + expected[i] + " actual=" + actual);
        }
    }

    private static void tile(NativeViewData data, int x, int y, int z, int... expected) throws Exception {
        int index = (((z-integer(data,"minLevel"))*integer(data,"height")+y-integer(data,"minY"))*integer(data,"width")+x-integer(data,"minX"))*4;
        ByteBuffer pixels = (ByteBuffer) field(NativeViewData.class,"pixels").get(data);
        for (int i = 0; i < 4; i++) equal("native tile " + key(x,y,z) + " channel" + i,pixels.get(index+i)&255,expected[i]);
    }

    private static Object allocate(Class<?> type) throws Exception { return ALLOCATE.invoke(UNSAFE,type); }
    private static int integer(Object target, String name) throws Exception { return field(target.getClass(),name).getInt(target); }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void equal(String label, int actual, int expected) {
        if (actual != expected) throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }
}
