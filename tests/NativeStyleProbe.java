import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import vpiso.NativeStyle;

/** GPU evaluation of the production native-color helper, without a game/map or VP renderer. */
public final class NativeStyleProbe {
    private static final String VERTEX = """
        #version 330
        void main() {
          vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
          gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(8, 8, "VPISO native style fixture", 0, 0);
        if (window == 0) { GLFW.glfwTerminate(); throw new AssertionError("Hidden OpenGL context unavailable"); }
        int program = 0, vao = 0, fbo = 0, color = 0;
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            program = program();
            vao = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            fbo = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            color = GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, 1, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete style FBO");
            GL11.glViewport(0, 0, 1, 1);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
            GL20.glUseProgram(program);
            // New GL uniforms default to zero: an unconfigured helper must not alter the picture.
            float[] sample = {.18f, .56f, .97f, .37f};
            assertPixel(draw(program, sample), sample, 0f, "unconfigured default");
            options(program, 0, 1, 2, 1.8f, .7f);
            assertPixel(draw(program, sample), sample, 0f, "disabled style");
            options(program, 1, 0, 2, 1.8f, .7f);
            assertPixel(draw(program, sample), sample, 0f, "zero strength");
            options(program, 1, 1, 1, 1, 1);
            float[] shoulder = draw(program, sample);
            if (Math.abs(shoulder[0] - sample[0]) > 1e-6f || !(shoulder[1] > sample[1] + .01f) || !(shoulder[2] < 1f)) {
                throw new AssertionError("Default native style crushes native low light or loses brighter midtone shaping");
            }
            // Black, middle gray and white stay anchored before the actual VP shoulder.
            for (float value : new float[] {0, .5f, 1f}) {
                float[] curveSample = {value, value, value, .37f};
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "reference"), 1);
                float[] expected = draw(program, curveSample);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "reference"), 0);
                assertPixel(draw(program, curveSample), expected, 1e-6f, "actual VP shoulder reference");
            }
            float[] quarter = draw(program, new float[] {.25f, .5f, .75f, .37f});
            assertPixel(quarter, new float[] {.25f, .5f, .796875f, .37f}, 1e-6f, "native low-light protection reference");
            float previous = -1f;
            for (int i = 0; i <= 255; i++) {
                float value = i / 255f;
                float current = draw(program, new float[] {value, value, value, .37f})[0];
                if (current < previous) throw new AssertionError("Native style reversed a gray-ramp step");
                if (value <= .5f && Math.abs(current - value) > 1e-6f) throw new AssertionError("Native dark-room ramp was altered: " + value + " -> " + current);
                previous = current;
            }
            options(program, 1, 1, 1.4f, 1.35f, 1.15f);
            float[] graded = draw(program, sample);
            if (Math.abs(graded[0] - sample[0]) + Math.abs(graded[1] - sample[1]) + Math.abs(graded[2] - sample[2]) < .01f) {
                throw new AssertionError("Enabled style has no visible color effect");
            }
            options(program, 1, .5f, 1.4f, 1.35f, 1.15f);
            float[] half = draw(program, sample);
            for (int i = 0; i < 3; i++) if (Math.abs(half[i] - (sample[i] + graded[i]) * .5f) > 1e-6f) {
                throw new AssertionError("Style strength did not interpolate the native picture");
            }
            options(program, 1, 1, 1, 0, 1);
            float[] desaturated = draw(program, sample);
            if (Math.abs(desaturated[0] - desaturated[1]) > 1e-6f || Math.abs(desaturated[1] - desaturated[2]) > 1e-6f) {
                throw new AssertionError("Saturation zero is not neutral gray");
            }
            int cases = 0;
            for (float exposure : new float[] {0, .25f, 1, 2, 8, 20}) {
                for (float saturation : new float[] {0, 1, 2, 5}) {
                    for (float contrast : new float[] {0, .25f, 1, 3, 8}) {
                        options(program, 1, 1, exposure, saturation, contrast);
                        float[] black = {0, 0, 0, .37f};
                        assertPixel(draw(program, black), black, 0f, "unseen black");
                        draw(program, sample);
                        float[] transparent = {.18f, .56f, .97f, 0};
                        if (draw(program, transparent)[3] != 0) throw new AssertionError("Transparent alpha changed");
                        cases++;
                    }
                }
            }
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("Style shader OpenGL error");
            System.out.println("Native style GPU PASS: unconfigured/disabled/zero-strength exact identity; visible default SDR midtone contrast, monotonic 256-step ramp, black/middle-gray/white anchors with actual shipped VP shoulder, independent exposure/saturation/contrast, strength interpolation, alpha and unseen-black preservation; "
                    + cases + " bounded/extreme parameter combinations finite on " + GL11.glGetString(GL11.GL_RENDERER) + "; no game/map.");
        } finally {
            if (program != 0) GL20.glDeleteProgram(program);
            if (color != 0) GL11.glDeleteTextures(color);
            if (fbo != 0) GL30.glDeleteFramebuffers(fbo);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    private static void options(int program, float on, float strength, float exposure, float saturation, float contrast) {
        uniform(program, "vpIsoStyleOn", on); uniform(program, "vpIsoStyleStrength", strength);
        uniform(program, "vpIsoStyleExposure", exposure); uniform(program, "vpIsoStyleSaturation", saturation);
        uniform(program, "vpIsoStyleContrast", contrast);
    }

    private static void uniform(int program, String name, float value) { GL20.glUniform1f(GL20.glGetUniformLocation(program, name), value); }

    private static float[] draw(int program, float[] input) {
        GL20.glUniform4f(GL20.glGetUniformLocation(program, "fixture"), input[0], input[1], input[2], input[3]);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
        GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
        float[] result = new float[4]; pixel.get(result);
        for (int i = 0; i < 4; i++) if (!Float.isFinite(result[i]) || result[i] < 0 || result[i] > 1) {
            throw new AssertionError("Style produced a non-finite/out-of-range native LDR pixel: " + java.util.Arrays.toString(result));
        }
        if (result[3] != input[3]) throw new AssertionError("Style changed native alpha");
        return result;
    }

    private static void assertPixel(float[] actual, float[] expected, float tolerance, String label) {
        for (int i = 0; i < 4; i++) if (Math.abs(actual[i] - expected[i]) > tolerance) {
            throw new AssertionError(label + " changed channel " + i + ": " + actual[i] + " vs " + expected[i]);
        }
    }

    private static int program() throws Exception {
        String vpCommon;
        try (var input = ClassLoader.getSystemResourceAsStream("viewpoint/shaders/lib/common.glsl")) {
            if (input == null) throw new AssertionError("Actual VP common.glsl resource unavailable");
            vpCommon = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String source = "#version 330\nuniform vec4 fixture;\nuniform int reference;\nout vec4 result;\n"
                + vpCommon + NativeStyle.GLSL
                + "\nvoid main() { result = reference != 0 ? vec4(shoulder(fixture.rgb), fixture.a) : vpIsoStyle(fixture); }\n";
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX), fragment = compile(GL20.GL_FRAGMENT_SHADER, source);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment); GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(GL20.glGetProgramInfoLog(program));
        return program;
    }

    private static int compile(int kind, String source) {
        int shader = GL20.glCreateShader(kind); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}
