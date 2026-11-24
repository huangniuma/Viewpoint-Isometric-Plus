import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Path;
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
import viewpoint.platform.GlProgram;
import vpiso.NativeViewData;
import vpiso.NativeWallCutaway;

/** Renders canonical N/W walls with the actual game's mask and the shipped GLSL. */
public final class WallCutawayProbe {
    private static final int SIDE = 128;

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(SIDE, SIDE, "VPISO wall mask fixture", 0, 0);
        if (window == 0) throw new IllegalStateException("Hidden GL context unavailable");
        GlProgram program = null;
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            String vertex = """
                #version 450 compatibility
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(p*2.0-1.0,0.0,1.0);
                }
                """;
            String fragment = "#version 450 compatibility\n" + NativeViewData.GLSL + NativeWallCutaway.GLSL + """
                uniform float north;
                uniform int flags;
                out vec4 result;
                void main() {
                    float t = gl_FragCoord.x / 128.0;
                    float y = gl_FragCoord.y / 128.0 * 2.4494896;
                    vec3 pos = north > 0.5 ? vec3(-t,y,0.0) : vec3(0.0,y,-t);
                    vec3 normal = north > 0.5 ? vec3(0.0,0.0,1.0) : vec3(1.0,0.0,0.0);
                    vpIsoCutWall(pos,vec3(0.0,y,0.0),normal,flags);
                    result = vec4(1.0);
                }
                """;
            program = GlProgram.fromSources("VPISO native wall fixture",
                    new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of("result",0),List.of("wall mask fixture"));
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, GL30.glGenFramebuffers());
            int color = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,SIDE,SIDE,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new AssertionError("Wall framebuffer incomplete");
            GL11.glViewport(0,0,SIDE,SIDE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glClearColor(0,0,0,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + 142);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,GL11.glGenTextures());
            GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA8,1,1,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);

            GL13.glActiveTexture(GL13.GL_TEXTURE0 + NativeWallCutaway.UNIT);
            int sentinel = GL11.glGenTextures(), sampler = GL33.glGenSamplers();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinel);
            GL33.glBindSampler(NativeWallCutaway.UNIT,sampler);
            int[] names = {GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL12.GL_UNPACK_IMAGE_HEIGHT,
                    GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS,GL12.GL_UNPACK_SKIP_IMAGES};
            int[] values = {8,11,13,2,3,1};
            for (int i=0;i<names.length;i++) GL11.glPixelStorei(names[i],values[i]);
            int pbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER,128L,GL15.GL_DYNAMIC_DRAW);
            Method load = NativeWallCutaway.class.getDeclaredMethod("loadMask",Path.class);
            load.setAccessible(true);
            load.invoke(null,Path.of(args[0],"media","wallcutaways.png"));
            for (int i=0;i<names.length;i++) equal("mask upload unpack recovery",GL11.glGetInteger(names[i]),values[i]);
            equal("mask upload PBO recovery",GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),pbo);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            for (int name:names) GL11.glPixelStorei(name,name == GL11.GL_UNPACK_ALIGNMENT ? 4 : 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinel);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+7);
            NativeWallCutaway.begin();
            try {
                equal("begin active unit recovery",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),GL13.GL_TEXTURE0+7);
                program.use(); NativeWallCutaway.onUse(program);
                program.setInt("vpIsoNativeTiles",142);
                program.set("vpIsoNativeOrigin",0f,0f,0f);
                program.set("vpIsoNativeSize",1f,1f,1f);
                program.set("vpIsoNativeCam",0f,0f,0f);
                program.setInt("flags",0);
                program.set("vpIsoNativeOn",1f);
                rule(828,896,224);
                double fullN = coverage(program,true), fullW = coverage(program,false);
                range("uncut north",fullN,.94,1);
                range("uncut west",fullW,.94,1);
                rule(700,512,224);
                double cutN = coverage(program,true), cutW = coverage(program,false);
                range("cut north",cutN,.03,.15);
                range("cut west",cutW,.03,.15);
                program.set("vpIsoNativeOn",0f);
                range("disabled guard",coverage(program,true),1,1);
                program.set("vpIsoNativeOn",1f);
                rule(700,512,128);
                range("non-wall north guard",coverage(program,true),1,1);
                range("non-wall west guard",coverage(program,false),1,1);
                System.out.printf(java.util.Locale.ROOT,
                        "Native wall GPU readback PASS: actual mask, N/W retained %.3f/%.3f uncut and %.3f/%.3f cut; disabled/capability guards and GL recovery. Not a map test.%n",
                        fullN,fullW,cutN,cutW);
            } finally { NativeWallCutaway.end(); }
            equal("end active unit recovery",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),GL13.GL_TEXTURE0+7);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+NativeWallCutaway.UNIT);
            equal("wall texture recovery",GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sentinel);
            equal("wall sampler recovery",GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,NativeWallCutaway.UNIT),sampler);
            equal("OpenGL errors",GL11.glGetError(),GL11.GL_NO_ERROR);
        } finally {
            NativeWallCutaway.end();
            if (program != null) program.delete();
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void rule(int north, int west, int capabilities) {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+142);
        ByteBuffer pixel = BufferUtils.createByteBuffer(4).put((byte)255).put((byte)(north/4)).put((byte)(west/4)).put((byte)capabilities).flip();
        GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,0,0,0,1,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
        GL13.glActiveTexture(active);
    }

    private static double coverage(GlProgram program, boolean north) {
        program.set("north",north?1f:0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIDE*SIDE*4);
        GL11.glReadPixels(0,0,SIDE,SIDE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        int count=0;
        for (int i=0;i<SIDE*SIDE;i++) if ((pixels.get(i*4)&255)>127) count++;
        return count/(double)(SIDE*SIDE);
    }
    private static void range(String name, double value, double min, double max) {
        if (value<min || value>max) throw new AssertionError(name+" coverage="+value+" expected "+min+".."+max);
    }
    private static void equal(String name, int actual, int expected) {
        if (actual != expected) throw new AssertionError(name+": "+actual+" != "+expected);
    }
}
