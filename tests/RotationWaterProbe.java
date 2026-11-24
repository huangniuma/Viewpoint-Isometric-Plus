import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.GameAccess;
import vpiso.IsoCameraMath;
import vpiso.RotationCamera;
import vpiso.RotationWater;
import vpiso.Patch_RotationWaterClear;

/** Controlled native producer data and GPU inverse projection; no save or map is opened. */
public final class RotationWaterProbe {
    private static final String GAME = "D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid";
    private static Object unsafe;
    private static java.lang.reflect.Method allocate;

    public static void main(String[] args) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true); unsafe = field.get(null);
        allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(128, 128, "VPISO native rotated water", 0, 0);
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            nativePrograms(args.length == 0 ? Path.of(GAME) : Path.of(args[0]));
            projection();
            if (GL11.glGetError() != 0) throw new AssertionError("GL error");
            System.out.println("Rotated native water PASS: actual water/water_hq shaders compile/link; source rewrite isolation/idempotence; appended native geometry, zoom/offset/corner/depth mapping and retained light/flow attributes; queued snapshot GPU inverse projection at cardinal and arbitrary yaw angles and offset viewport; temporal water coordinates remain world-anchored across changing viewport/zoom/offset uniforms; native default branch and native buffer-reset patch entry restored. Controlled inputs, not map optics/FPS acceptance.");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }

    private static void nativePrograms(Path game) throws Exception {
        for (String name : new String[] { "water", "water_hq" }) {
            String vertex = Files.readString(game.resolve("media/shaders/" + name + ".vert"));
            String original = Files.readString(game.resolve("media/shaders/" + name + ".frag"));
            String changed = RotationWater.rewrite("media/shaders/" + name + ".frag", original);
            if (changed.equals(original) || !changed.contains("VPISO_ROTATED_NATIVE_WATER")) throw new AssertionError("Native shader was not adapted");
            if (!RotationWater.rewrite("media/shaders/" + name + ".frag", changed).equals(changed)) throw new AssertionError("Duplicate shader adaptation");
            if (!RotationWater.rewrite("media/shaders/puddles.frag", original).equals(original)) throw new AssertionError("Unrelated shader changed");
            int baseline = program(vertex, original), patched = program(vertex, changed);
            GL20.glUseProgram(patched); RotationWater.leave(null); RotationWater.uniforms();
            float[] flag = new float[1]; GL20.glGetUniformfv(patched, GL20.glGetUniformLocation(patched, "vpIsoWaterRotation"), flag);
            if (flag[0] != 0f) throw new AssertionError("Unrotated native water inherited rotation");
            GL20.glUseProgram(0); GL20.glDeleteProgram(baseline); GL20.glDeleteProgram(patched);
        }
    }

    @SuppressWarnings("unchecked")
    private static void projection() throws Exception {
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        // Original producer projection uses the camera's absolute offsets and native viewport dimensions.
        Object nativeCamera = ((Object[]) GameAccess.field("zombie.iso.IsoCamera", "cameras", null))[0];
        GameAccess.set("zombie.iso.PlayerCamera", "offX", nativeCamera, 123f);
        GameAccess.set("zombie.iso.PlayerCamera", "offY", nativeCamera, 456f);
        GameAccess.set("zombie.iso.PlayerCamera", "rightClickX", nativeCamera, 0f);
        GameAccess.set("zombie.iso.PlayerCamera", "rightClickY", nativeCamera, 0f);
        GameAccess.set("zombie.iso.PlayerCamera", "zoom", nativeCamera, 1.5f);
        Object data = newData();
        // Order confirmed in the installed IsoWaterGeometry.initRender bytecode, including its shore overlap.
        double[][][] worldCorners = {
            { {10,20}, {10,21}, {11,21}, {11,20} },
            { {10.98,20}, {10.98,21.02}, {12,21.02}, {12,20} }
        };
        Object a = square(10, 20, 0, worldCorners[0]), b = square(11, 20, 0, worldCorners[1]);
        List<?> squares = List.of(a, b);
        float[] original = new float[96];
        for (int i = 0; i < original.length; i++) original[i] = i * .01f;
        String helper = (String) GameAccess.field("vpiso.RotationWater", "GLSL", null);
        // Supporting the previous helper lets the same GPU probe serve as its negative control.
        String expression = helper.contains("vpIsoWaterSurfaceUV")
                ? "vpIsoWaterSurfaceUV(vec2(gl_FragCoord.x/WViewport.z,1.0-gl_FragCoord.y/WViewport.w))"
                : "(vpIsoWaterCoordinate()*WOffset.zw+WOffset.xy)*0.0008";
        String fragment = "#version 330\nuniform vec4 WViewport, WOffset;\nout vec4 colour;\n"
                + helper + "\nvoid main(){ colour=vec4(" + expression + ",0.0,1.0); }";
        int program = program("#version 330\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}", fragment);
        int fbo = GL30.glGenFramebuffers(), texture = GL11.glGenTextures(), vao = GL30.glGenVertexArrays();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, 128, 128, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete framebuffer");
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_CULL_FACE);
        GL30.glBindVertexArray(vao);
        float[] angles = {90f, 180f, 270f, 13.25f, 47.5f, 128.75f, 359.5f};
        for (int sample = 0; sample < angles.length; sample++) {
            float angle = angles[sample];
            Matrix4f projection = new Matrix4f(), view = new Matrix4f();
            // Cardinal cases keep the legacy ABI so the exact 015 JAR can run the negative control.
            if (sample < 3) IsoCameraMath.matrices(new IsoCameraMath.Fit(10, 20, 4f), 10f, 20f, 96, 96,
                    projection, view, new float[3], sample + 1);
            else IsoCameraMath.matrices(new IsoCameraMath.Fit(10, 20, 4f), 10f, 20f, 96, 96,
                    projection, view, new float[3], angle);
            RotationCamera.Snapshot camera = sample < 3
                    ? new RotationCamera.Snapshot(projection, view, 10, 20, 0, 96, 96, 1.5f, sample + 1)
                    : new RotationCamera.Snapshot(projection, view, 10, 20, 0, 96, 96, 1.5f, angle);
            float[] vertices = original.clone();
            GameAccess.set("zombie.iso.IsoWater$RenderData", "data", data, vertices);
            RotationWater.reproject(data, squares, 1, 3, camera);
            float[] firstProjection = vertices.clone();
            // Real RenderData.addSquare copies geometry into another array. Reprojection must
            // always read that cached geometry, even when a native buffer is reused repeatedly.
            for (int repeat = 0; repeat < 8; repeat++) RotationWater.reproject(data, squares, 1, 3, camera);
            if (!Arrays.equals(vertices, firstProjection)) throw new AssertionError("Water geometry accumulated another transform");
            float[] point = new float[3];
            for (int square = 1; square < 3; square++) for (int corner = 0; corner < 4; corner++) {
                double[] expectedWorld = worldCorners[square - 1][corner];
                camera.project(expectedWorld[0], expectedWorld[1], 0, point);
                int offset = square * 32 + corner * 8;
                near(vertices[offset + 4], point[0] * 1.5f, .0001f, "local vertex X");
                near(vertices[offset + 5], point[1] * 1.5f, .0001f, "local vertex Y");
                near(vertices[offset + 7], point[2] + .000001f, .0000001f, "vertex depth");
                for (int attribute : new int[] { 0, 1, 2, 3, 6 })
                    near(vertices[offset + attribute], original[offset + attribute], 0f, "retained native attribute");
            }
            if (!Arrays.equals(Arrays.copyOf(vertices, 32), Arrays.copyOf(original, 32))) throw new AssertionError("Earlier draw range changed");
            Map<Object, List<?>> batches = (Map<Object, List<?>>) GameAccess.field("vpiso.RotationWater", "BATCHES", null);
            Object batch = batches.get(data).get(batches.get(data).size() - 1);
            ThreadLocal<Object> drawing = (ThreadLocal<Object>) GameAccess.field("vpiso.RotationWater", "DRAWING", null);
            drawing.set(batch);
            for (int[] viewport : new int[][] { { 0, 0, 96, 96 }, { 5, 7, 96, 96 } }) {
                GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                GL20.glUseProgram(program);
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "WViewport"), 0, 0, 96, 96);
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "WOffset"),
                        123f - 90000f, 456f - 640000f, size("getOffscreenWidth"), size("getOffscreenHeight"));
                RotationWater.uniforms();
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
                GL11.glReadPixels(viewport[0] + 48, viewport[1] + 48, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                double[] world = new double[2]; camera.ground(48.5, 47.5, 0, world);
                float expectedX = (nativeScreen("XToScreen", world[0], world[1]) - 90000f) * .0008f;
                float expectedY = (nativeScreen("YToScreen", world[0], world[1]) - 640000f) * .0008f;
                near(pixel.get(0), expectedX, .002f, "GPU world-anchored water X");
                near(pixel.get(1), expectedY, .002f, "GPU world-anchored water Y");
                // Queued native uniforms can carry a different zoom/offset from the destination
                // viewport. A fixed world point must retain its water coordinates regardless.
                for (int frame = 0; frame < 36; frame++) {
                    float zoom = new float[] {.5f, .75f, 1f, 1.5f, 2f, 1f}[frame % 6];
                    GL20.glUniform4f(GL20.glGetUniformLocation(program, "WViewport"), frame, -frame,
                            96f / zoom, 96f / zoom);
                    GL20.glUniform4f(GL20.glGetUniformLocation(program, "WOffset"),
                            123f - 90000f, 456f - 640000f,
                            size("getOffscreenWidth"), size("getOffscreenHeight"));
                    RotationWater.uniforms(); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                    GL11.glReadPixels(viewport[0] + 48, viewport[1] + 48, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                    near(pixel.get(0), expectedX, .002f, "temporal water scale X");
                    near(pixel.get(1), expectedY, .002f, "temporal water scale Y");
                }
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "WViewport"), 0, 0, 96, 96);
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "WOffset"), 0, 0, 96, 96);
                RotationWater.leave(null); RotationWater.uniforms(); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                GL11.glReadPixels(viewport[0] + 48, viewport[1] + 48, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                near(pixel.get(0), (viewport[0] + 48.5f) * .0008f, .00001f, "original branch X");
                drawing.set(batch);
            }
            RotationWater.leave(null);
            // Exercise the reset patch entry then the actual native clear, without addon reproject(null).
            Patch_RotationWaterClear.enter(data);
            GameAccess.call("zombie.iso.IsoWater$RenderData", "clear", data, new Class<?>[0]);
            if (batches.containsKey(data)) throw new AssertionError("Reused native buffer retained an old camera");
        }
        GL20.glUseProgram(0); GL20.glDeleteProgram(program); GL30.glDeleteVertexArrays(vao);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0); GL30.glDeleteFramebuffers(fbo); GL11.glDeleteTextures(texture);
    }

    private static Object newData() throws Exception {
        Constructor<?> constructor = Class.forName("zombie.iso.IsoWater$RenderData").getDeclaredConstructor();
        constructor.setAccessible(true); return constructor.newInstance();
    }
    private static Object square(int x, int y, int z, double[][] corners) throws Exception {
        Object square = allocate.invoke(unsafe, Class.forName("zombie.iso.IsoGridSquare"));
        GameAccess.set("zombie.iso.IsoGridSquare", "x", square, x);
        GameAccess.set("zombie.iso.IsoGridSquare", "y", square, y);
        GameAccess.set("zombie.iso.IsoGridSquare", "z", square, z);
        GameAccess.set("zombie.iso.IsoGridSquare", "chunk", square,
                allocate.invoke(unsafe, Class.forName("zombie.iso.IsoChunk")));
        Object geometry = Class.forName("zombie.iso.IsoWaterGeometry").getConstructor().newInstance();
        float[] xs = new float[4], ys = new float[4];
        for (int i = 0; i < 4; i++) {
            xs[i] = ((Number) GameAccess.call("zombie.iso.IsoUtils", "XToScreen", null,
                    new Class<?>[] {float.class,float.class,float.class,int.class},
                    (float)corners[i][0], (float)corners[i][1], (float)z, 0)).floatValue();
            ys[i] = ((Number) GameAccess.call("zombie.iso.IsoUtils", "YToScreen", null,
                    new Class<?>[] {float.class,float.class,float.class,int.class},
                    (float)corners[i][0], (float)corners[i][1], (float)z, 0)).floatValue();
        }
        GameAccess.set("zombie.iso.IsoWaterGeometry", "x", geometry, xs);
        GameAccess.set("zombie.iso.IsoWaterGeometry", "y", geometry, ys);
        GameAccess.set("zombie.iso.IsoWaterGeometry", "square", geometry, square);
        GameAccess.set("zombie.iso.IsoWaterGeometry", "renderInit", geometry, true);
        GameAccess.set("zombie.iso.IsoGridSquare", "water", square, geometry);
        return square;
    }
    private static float nativeScreen(String method, double x, double y) {
        return ((Number) GameAccess.call("zombie.iso.IsoUtils", method, null,
                new Class<?>[] { float.class, float.class, float.class, int.class }, (float)x, (float)y, 0f, 0)).floatValue();
    }
    private static float size(String method) {
        return ((Number) GameAccess.call("zombie.iso.IsoCamera", method, null, new Class<?>[] { int.class }, 0)).floatValue();
    }
    private static int program(String vertex, String fragment) {
        int program = GL20.glCreateProgram(), vs = shader(GL20.GL_VERTEX_SHADER, vertex), fs = shader(GL20.GL_FRAGMENT_SHADER, fragment);
        GL20.glAttachShader(program, vs); GL20.glAttachShader(program, fs); GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(GL20.glGetProgramInfoLog(program));
        GL20.glDeleteShader(vs); GL20.glDeleteShader(fs); return program;
    }
    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(shader));
        return shader;
    }
    private static void near(float actual, float expected, float tolerance, String label) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > tolerance)
            throw new AssertionError(label + ": " + actual + " != " + expected);
    }
}
