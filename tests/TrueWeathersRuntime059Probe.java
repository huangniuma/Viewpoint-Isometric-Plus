import java.nio.FloatBuffer;
import java.nio.file.*;
import java.util.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.ShaderPacks;
import vpiso.*;

/** Actual TW advice + addon advice on actual VP reads, in both transformer orders. */
public final class TrueWeathersRuntime059Probe {
    static final String SHADERS = "viewpoint.platform.Shaders";
    static final String TOKEN = "uShadowMaps[c].z";
    static final String SCALE = "(vpIsoModels > 0.5 ? vpIsoShadowSoftness : 1.0)";
    static void weave(Path target, boolean twFirst) throws Exception {
        var convert = PatchWeavingProbe.class.getDeclaredMethod("convert", byte[].class);
        convert.setAccessible(true);
        var matcher = PatchWeavingProbe.class.getDeclaredMethod("matcher", Class.class, String.class, boolean.class);
        matcher.setAccessible(true);
        Map<String,byte[]> advice = new HashMap<>();
        for (String name : List.of("vptw.Patches$SnowShader", "vpiso.Patch_ShaderRead")) {
            try (var in = ClassLoader.getSystemResourceAsStream(name.replace('.', '/')+".class")) {
                advice.put(name, (byte[])convert.invoke(null, (Object)in.readAllBytes()));
            }
        }
        ClassLoader loader = new ClassLoader(TrueWeathersRuntime059Probe.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String n, boolean resolve) throws ClassNotFoundException {
                if (!advice.containsKey(n)) return super.loadClass(n, resolve);
                Class<?> c = findLoadedClass(n);
                if (c == null) c = defineClass(n, advice.get(n), 0, advice.get(n).length);
                if (resolve) resolveClass(c);
                return c;
            }
        };
        byte[] raw;
        try (var in = ClassLoader.getSystemResourceAsStream(SHADERS.replace('.', '/')+".class")) { raw = in.readAllBytes(); }
        List<String> order = new ArrayList<>(List.of("vptw.Patches$SnowShader", "vpiso.Patch_ShaderRead"));
        if (!twFirst) Collections.reverse(order);
        for (String name : order) {
            var loc = new ClassFileLocator.Compound(new ClassFileLocator.Simple(Map.of(SHADERS, raw)),
                    new ClassFileLocator.Simple(advice), ClassFileLocator.ForClassLoader.ofSystemLoader());
            Class<?> a = loader.loadClass(name);
            @SuppressWarnings("unchecked")
            var match = (ElementMatcher.Junction<MethodDescription>)matcher.invoke(null, a, "read", false);
            var type = TypePool.Default.of(loc).describe(SHADERS).resolve();
            long count = type.getDeclaredMethods().stream().filter(match::matches).count();
            if (count != (name.startsWith("vptw") ? 2 : 1)) throw new AssertionError("Actual advice matching: "+name+" "+count);
            raw = new ByteBuddy().redefine(type, loc).visit(Advice.to(a, loc).on(match)).make().getBytes();
        }
        Path out = target.resolve(SHADERS.replace('.', '/')+".class");
        Files.createDirectories(out.getParent()); Files.write(out, raw);
        System.out.println("059 actual cross-mod weave PASS: "+order+"; TW covers both VP read overloads.");
    }
    static String read(String path) {
        return (String)GameAccess.call(SHADERS, "read", null,
                new Class<?>[]{String.class, viewpoint.platform.ShaderPack.class, boolean[].class},
                path, ShaderPacks.active().pack, new boolean[1]);
    }
    static int count(String source, String token) { return (source.length()-source.replace(token, "").length())/token.length(); }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("weave")) { weave(Path.of(args[1]), args[2].equals("tw-first")); return; }
        boolean old = args[0].equals("old"); String pack = args[1];
        boolean expectError = old && args[2].equals("expected-error");
        TestShaderPacks.select(pack, pack.equals("somber") ? "quality" : "high", Map.of("upscaling", "off", "reflections", "off"));
        Main.compatible = false;
        String reference = read("lib/sun_filter.glsl");
        if (!reference.contains("True Weathers: shadows") || count(reference, TOKEN) != 2)
            throw new AssertionError("Actual TW runtime filter not exercised: "+pack);
        Main.compatible = true;
        String adapted;
        try { adapted = read("lib/sun_filter.glsl"); }
        catch (IllegalStateException e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!expectError || !cause.getMessage().equals("Unsupported VP shader layout in lib/sun_filter.glsl")) throw e;
            System.out.println("058 actual TW runtime negative control PASS: "+pack+"; reproduced reported preparation failure."); return;
        }
        if (old) {
            if (expectError) throw new AssertionError("058 did not reproduce preparation failure");
            System.out.println("058 alternate-order nonfailure control PASS: "+pack+"; TW wrapper added after addon advice."); return;
        }
        int scaled = count(adapted, SCALE);
        if ((scaled != 1 && scaled != 2) || !adapted.contains("return vpSunFilter(c, uv, z);") || !adapted.contains("return twDisc("))
            throw new AssertionError("Fallback or weather filter lost");
        if (!IsometricShaders.rewrite("lib/sun_filter.glsl", adapted).equals(adapted)) throw new AssertionError("Not idempotent");
        // Ensure the only edits to the actual weather filter are the header and both scale expressions.
        String base = (String)GameAccess.field("vpiso.IsometricShaders", "HEADER", null);
        String restored = adapted.replace(base, "").replace("("+TOKEN+" * "+SCALE+")", TOKEN);
        if (!restored.equals(reference)) throw new AssertionError("Unrelated TW filter content changed");
        gpu(reference, adapted, pack, scaled == 2);
        ShaderCompileProbe.main(new String[]{args[2], args[3], pack, "C:/Users/Win10/Zomboid/viewpoint-programs/programs.list"});
        System.out.println("059 actual TW runtime shaders PASS: "+pack+"; original weather filter retained, footprints present at addon read="+scaled+", ordinary VP unchanged.");
    }
    static int shader(int type, String source) {
        int id = GL20.glCreateShader(type); GL20.glShaderSource(id, source); GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(id));
        return id;
    }
    static int program(String filter) {
        var includes = java.util.regex.Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"\\s*$").matcher(filter);
        filter = includes.replaceAll(m -> java.util.regex.Matcher.quoteReplacement(read(m.group(1))));
        String vertex = "#version 450 compatibility\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}";
        String fragment = "#version 450 compatibility\n#define TW_SHADOW 1\n#define FIRST_FAR_MAP 2\n"
            + "uniform vec4 uTWShadow; uniform float uTWShadowQ; uniform vec4 uShadowMaps[4]; uniform int cascade;\n"
            + "float shadowTap(int c,vec3 p){vec2 d=p.xy-vec2(0.5);return dot(d,d)*100.;}\n"
            + filter + "\nout vec4 fragColor; void main(){fragColor=vec4(vec3(sunFilter(cascade,vec2(0.5),0.5)),1.);}";
        int v = shader(GL20.GL_VERTEX_SHADER, vertex), f = shader(GL20.GL_FRAGMENT_SHADER, fragment);
        int p = GL20.glCreateProgram(); GL20.glAttachShader(p,v); GL20.glAttachShader(p,f); GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0) throw new AssertionError(GL20.glGetProgramInfoLog(p));
        GL20.glDeleteShader(v); GL20.glDeleteShader(f); return p;
    }
    static float draw(int p, int c, float spread, float quality, float models, float softness) {
        GL20.glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p,"cascade"), c);
        GL20.glUniform1f(GL20.glGetUniformLocation(p,"uShadowPhase"), .37f);
        GL20.glUniform4f(GL20.glGetUniformLocation(p,"uTWShadow"), spread, .2f, .1f, 80f);
        GL20.glUniform1f(GL20.glGetUniformLocation(p,"uTWShadowQ"), quality);
        GL20.glUniform1f(GL20.glGetUniformLocation(p,"vpIsoModels"), models);
        GL20.glUniform1f(GL20.glGetUniformLocation(p,"vpIsoShadowSoftness"), softness);
        for(int i=0;i<4;i++) GL20.glUniform4f(GL20.glGetUniformLocation(p,"uShadowMaps["+i+"]"), .1f, 1f, .002f, 0f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
        GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
        return pixel.get(0);
    }
    static void near(float actual, float expected) {
        if (!Float.isFinite(actual) || Math.abs(actual-expected)>Math.max(1e-6f,Math.abs(expected)*1e-4f))
            throw new AssertionError("Filter footprint: "+actual+" expected "+expected);
    }
    static void gpu(String reference, String adapted, String pack, boolean weatherScaled) {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long w=GLFW.glfwCreateWindow(16,16,"TW runtime filter regression",0,0);
        if(w==0)throw new AssertionError("Hidden GL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(w); GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            int texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,GL30.glGenFramebuffers());
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
            GL11.glViewport(0,0,1,1); int original=program(reference), fixed=program(adapted), cases=0;
            for(int c:new int[]{0,2})for(float spread:new float[]{0,.18f})for(float quality:new float[]{0,2}) {
                float expected=draw(original,c,spread,quality,0,1);
                if(expected<=0)throw new AssertionError("Empty filter footprint fixture");
                for(float softness:new float[]{0,.5f,1,2}) {
                    near(draw(fixed,c,spread,quality,0,softness),expected);
                    float factor = weatherScaled || spread <= 0 || quality < .5f ? softness*softness : 1;
                    near(draw(fixed,c,spread,quality,1,softness),expected*factor); cases+=2;
                }
            }
            GL20.glDeleteProgram(original); GL20.glDeleteProgram(fixed);
            System.out.println("059 actual TW filter GPU PASS: "+pack+"; cases="+cases+"; weather/fallback, near/far, independent softness and ordinary VP equivalence. Controlled shadowTap.");
        } finally { GLFW.glfwDestroyWindow(w); GLFW.glfwTerminate(); }
    }
}
