import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import vpiso.IsometricShaders;
import vpiso.GameAccess;
import viewpoint.platform.ShaderPacks;

/** Compiles the real VP Library shader stages with the addon edits on a hidden GL context. */
public final class ShaderCompileProbe {
    private static ZipFile archive;
    private static String constants, defines;
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");

    public static void main(String[] args) throws Exception {
        archive = new ZipFile(args[0]);
        String pack = args.length > 2 ? args[2] : "library";
        vpiso.Main.compatible = true;
        GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null,
                new Class<?>[]{java.io.File.class}, (Object) null);
        TestShaderPacks.select(pack, pack.equals("somber") ? "quality" : "high", java.util.Map.of("upscaling", "off", "reflections", "off"));
        Class<?> shaders = Class.forName("viewpoint.platform.Shaders");
        Method generated = shaders.getDeclaredMethod("constants");
        generated.setAccessible(true);
        constants = (String) generated.invoke(null);
        Object active = Class.forName("viewpoint.platform.ShaderPacks").getMethod("active").invoke(null);
        Field macros = active.getClass().getDeclaredField("defines");
        macros.setAccessible(true);
        defines = (String) macros.get(active);
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(32,32,"VPISO shader verification",0,0);
        if (window == 0) throw new IllegalStateException("Hidden OpenGL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("OpenGL " + GL11.glGetString(GL11.GL_VERSION) + " / " + GL11.glGetString(GL11.GL_RENDERER));
            int count = 0;
            var compiled = new HashMap<String,Integer>();
            var paths = new java.util.ArrayList<>(archive.stream().map(e -> e.getName()).filter(n -> n.startsWith("viewpoint/shaders/") &&
                (n.endsWith(".vert") || n.endsWith(".frag") || args.length > 3 &&
                    (n.endsWith(".tesc") || n.endsWith(".tese") || n.endsWith(".geom") || n.endsWith(".comp")))).sorted().toList());
            for(var pass:ShaderPacks.active().passes)for(String extra:new String[]{pass.vertex,pass.fragment})
                if(!paths.contains("viewpoint/shaders/"+extra))paths.add("viewpoint/shaders/"+extra);
            for (String entry : paths) {
                String path = entry.substring("viewpoint/shaders/".length());
                String expanded;
                if (args.length > 2) {
                Object loaded = GameAccess.call("viewpoint.platform.Shaders", "load", null,
                        new Class<?>[]{String.class}, path);
                expanded = (String) GameAccess.field("viewpoint.platform.Shaders$Source", "text", loaded);
                if (path.equals("post.frag") && !expanded.contains("VP_ISOMETRIC_SHADER_HELPERS"))
                    throw new AssertionError("Actual Shaders.read advice missing for " + pack);
                } else {
                    expanded = expand(path,new HashSet<>());
                    int first = expanded.indexOf('\n');
                    expanded = expanded.substring(0,first+1)
                        + "#extension GL_ARB_shader_storage_buffer_object : enable\n#extension GL_ARB_shader_image_load_store : enable\n"
                        + defines + expanded.substring(first+1);
                }
                int shader = GL20.glCreateShader(shaderType(path));
                GL20.glShaderSource(shader,expanded);
                GL20.glCompileShader(shader);
                if (GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS) == 0) {
                    Path failure = Path.of(args[1],path.replace('/','_') + ".failed.glsl");
                    Files.createDirectories(failure.getParent());
                    Files.writeString(failure,expanded);
                    throw new AssertionError(path + ": " + GL20.glGetShaderInfoLog(shader));
                }
                compiled.put(path,shader);
                count++;
            }
            for (String fragment : new String[] {"gbuffer.frag","forward.frag","shadow.frag","model_gbuffer.frag","model_glass.frag"}) {
                int program = GL20.glCreateProgram();
                int vertex = compiled.get(fragment.equals("shadow.frag") ? "shadow.vert" : fragment.startsWith("model_")?"model.vert":"mesh.vert");
                GL20.glAttachShader(program,vertex);
                GL20.glAttachShader(program,compiled.get(fragment));
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program,GL20.GL_LINK_STATUS) == 0)
                    throw new AssertionError(fragment+" program link: "+GL20.glGetProgramInfoLog(program));
                GL20.glDeleteProgram(program);
            }
            for(var pass:ShaderPacks.active().passes) {
                int program=GL20.glCreateProgram();
                GL20.glAttachShader(program,compiled.get(pass.vertex));
                GL20.glAttachShader(program,compiled.get(pass.fragment));
                GL20.glLinkProgram(program);
                if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)
                    throw new AssertionError("Custom pass link "+pass.id+": "+GL20.glGetProgramInfoLog(program));
                GL20.glDeleteProgram(program);
            }
            if (args.length > 3) {
                int links = 0;
                for (String line : Files.readAllLines(Path.of(args[3]))) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 7) throw new AssertionError("Invalid actual program list");
                    int program = GL20.glCreateProgram();
                    for (int i = 1; i < fields.length; i++) if (!fields[i].isEmpty()) {
                        Integer stage = compiled.get(fields[i]);
                        if (stage == null) throw new AssertionError("Missing stage: " + fields[i]);
                        GL20.glAttachShader(program, stage);
                    }
                    GL20.glLinkProgram(program);
                    if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                        throw new AssertionError(fields[0] + " program link: " + GL20.glGetProgramInfoLog(program));
                    GL20.glDeleteProgram(program); links++;
                }
                System.out.println("Actual VP recorded program GPU links PASS: " + pack + "; programs=" + links);
            }
            compiled.values().forEach(GL20::glDeleteShader);
            System.out.println("Built-in shader GPU PASS: " + pack + "; stages=" + count + "; loader=" + (args.length > 2 ? "actual woven VP" : "manual shared-resource regression"));
            System.out.println("Actual VP mesh/gbuffer, mesh/forward, model/gbuffer, model/glass and shadow program links passed.");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
            archive.close();
        }
    }

    private static int shaderType(String path) {
        if (path.endsWith(".vert")) return GL20.GL_VERTEX_SHADER;
        if (path.endsWith(".frag")) return GL20.GL_FRAGMENT_SHADER;
        if (path.endsWith(".tesc")) return 0x8E88;
        if (path.endsWith(".tese")) return 0x8E87;
        if (path.endsWith(".geom")) return 0x8DD9;
        if (path.endsWith(".comp")) return 0x91B9;
        throw new AssertionError("Unknown shader type: " + path);
    }

    private static String expand(String path, HashSet<String> stack) throws Exception {
        if (!stack.add(path)) throw new IllegalStateException("Include cycle: " + path);
        String source;
        if (path.equals("constants.glsl")) source = constants;
        else {
            var entry = archive.getEntry("viewpoint/shaders/" + path);
            if (entry == null) throw new IllegalStateException("Missing resource: " + path);
            try (var input = archive.getInputStream(entry)) {
                source = new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
            }
        }
        source = IsometricShaders.rewrite(path,source);
        StringBuilder out = new StringBuilder();
        for (String line : source.split("\n",-1)) {
            var include = INCLUDE.matcher(line);
            if (include.matches()) out.append(expand(include.group(1),stack));
            else out.append(line).append('\n');
        }
        stack.remove(path);
        return out.toString();
    }
}
