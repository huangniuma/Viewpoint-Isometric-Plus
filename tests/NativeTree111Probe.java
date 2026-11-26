import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.*;
import vpiso.*;

/** Independent hidden-context regression; no game attach, settings, saves or automatic sampling. */
public final class NativeTree111Probe {
    static final String[] PACKS={"default","library","vivid","somber"};
    static final Map<String,String> OPTIONS=Map.ofEntries(
            Map.entry("upscaling","off"),Map.entry("antialiasing","off"),Map.entry("renderScale","100"),
            Map.entry("reflections","off"),Map.entry("volumetrics","false"),Map.entry("bloom","false"),
            Map.entry("clouds","false"),Map.entry("bounceLight","false"),Map.entry("sunShafts","false"),
            Map.entry("depthOfField","false"),Map.entry("motionBlur","false"),Map.entry("filmGrain","false"));
    static final String HOOK="""
        // Test-only addon fixture: helpers declared before main, retaining material reads.
        // This matches the declaration placement used by real shader-source hooks.
        vec4 vpTreeHookAlbedo111() { return texture(uAlbedo, vUv); }
        vec4 vpTreeHookEngine111() { return texture(uEngineLight, vUv); }
        vec4 vpTreeHookNormal111() { return gNormalUnpack(texture(uNormal, vUv)); }
        vec3 vpTreeHookPosition111(float depth) { return scenePos(vUv, depth); }
        """;
    record Linked(boolean ok,String log) {}

    public static void main(String[] args)throws Exception {
        if(args.length==3 && args[0].equals("compare")) {compare(Path.of(args[1]),Path.of(args[2]));return;}
        if(args.length<2)throw new IllegalArgumentException("shader|dump|render-default <output> [baseline|candidate]");
        String mode=args[0];Path out=Path.of(args[1]);Files.createDirectories(out);
        boolean baseline=args.length>2 && args[2].equals("baseline");
        if(mode.equals("dump")){discover();matrix(out,baseline,false);return;}
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(NativeTreeStyle075Probe.OW,NativeTreeStyle075Probe.OH,"tree111 independent regression",0,0);
        if(window==0)throw new AssertionError("Hidden GL context");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();discover();
            Files.writeString(out.resolve("renderer.txt"),GL11.glGetString(GL11.GL_VENDOR)+"\n"+GL11.glGetString(GL11.GL_RENDERER)+"\n"+GL11.glGetString(GL11.GL_VERSION)+"\n");
            if(mode.equals("shader"))matrix(out,baseline,true);
            else if(mode.equals("render-default"))render(out);
            else throw new IllegalArgumentException(mode);
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void discover() {
        Main.compatible=true;
        GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
    }
    static void select(String pack) {
        TestShaderPacks.selectLive(pack,pack.equals("somber")?"quality":"high",OPTIONS);
        GameAccess.set("viewpoint.platform.GlProgram","pack",null,ShaderPacks.active());ShaderPacks.linked(ShaderPacks.active());
    }
    static void matrix(Path out,boolean baseline,boolean gpu)throws Exception {
        String vertex=field("VERTEX");List<String> rows=new ArrayList<>();rows.add("pack\tfixture\tlinked\tdeclarationBeforeUse");
        int negatives=0,passes=0;
        for(String pack:PACKS) {
            select(pack);String expanded=expanded();Files.writeString(out.resolve(pack+"-selected-expanded.frag"),expanded);
            if(baseline)require(legacy(expanded).equals(source()),"Pinned110 adapter replay drift: "+pack);
            for(String fixture:pack.equals("library")?new String[]{"plain","helper-before-main","poolpit-retained"}:new String[]{"plain","helper-before-main"}) {
                boolean hook=!fixture.equals("plain");
                String raw=fixture.equals("helper-before-main")?hook(expanded):fixture.equals("poolpit-retained")?poolpit(expanded):expanded;
                String fragment=hook?adapt(raw,baseline):source();
                Path stem=out.resolve(pack+"-"+fixture);Files.writeString(Path.of(stem+".frag"),fragment);Files.writeString(Path.of(stem+".vert"),vertex);
                boolean order=declarationsBeforeUse(fragment);Linked linked=gpu?link(vertex,fragment):new Linked(false,"CPU dump only\n");
                Files.writeString(Path.of(stem+".compile.log"),linked.log);
                if(gpu) {
                    if(baseline && hook) {
                        // Some desktop drivers accept these forward globals. Strict frontend checks are in Verify111Shader.ps1.
                        negatives++;require(!order,"Pinned110 ordering defect absent: "+pack+" "+fixture);
                    } else {require(linked.ok,"Tree111 link failed: "+pack+" "+fixture+"\n"+linked.log);require(order,"Tree globals used before declarations: "+pack+" "+fixture);passes++;}
                }
                if(!baseline) {
                    require(count(fragment,"void main()")+count(fragment,"void main ()")==1,"Final main count: "+pack+" "+fixture);
                    require(fragment.contains("vpTreeSelectedLight"),"Selected material lost");
                    require(fragment.contains("fragColor.a=vpTreeArt.a*vColor.a"),"Native alpha lost");
                    require(fragment.contains("vpTreeGroundDepth"),"Native root depth lost");
                    if(fixture.equals("helper-before-main"))for(String token:new String[]{"vpTreeHookAlbedo111","vpTreeHookEngine111","vpTreeHookNormal111","vpTreeHookPosition111"})
                        require(fragment.contains(token),"Source hook helper lost: "+token);
                    if(fixture.equals("poolpit-retained"))require(fragment.contains("void poolpit_light()") && fragment.contains("poolpit_light();"),"Retained actual PoolPit helper or call lost");
                }
                rows.add(pack+"\t"+fixture+"\t"+(gpu?linked.ok:"dump-only")+"\t"+order);
                System.out.println("Tree111 "+(baseline?"pinned110":"candidate")+" pack="+pack+" fixture="+fixture+" link="+(gpu?linked.ok:"dump-only")+" declarationsBeforeUse="+order);
            }
        }
        Files.write(out.resolve("matrix.tsv"),rows);
        if(gpu)System.out.println("Tree111 shader matrix PASS: arm="+(baseline?"pinned110":"candidate")+" linked="+passes+" expectedFailures="+negatives+"; four actual built-in selected material expansions and neutral helper-before-main addon fixture.");
    }
    static String expanded() {
        Object body=GameAccess.call("viewpoint.platform.Shaders","expand",null,new Class<?>[]{String.class,ShaderPack.class},"light.frag",GlProgram.pack().pack);
        String text=(String)GameAccess.field("viewpoint.platform.Shaders$Body","head",body),tail=(String)GameAccess.field("viewpoint.platform.Shaders$Body","tail",body);
        return text+(tail==null?"":tail);
    }
    static String source()throws Exception {
        Method method=NativeTreeLighting.class.getDeclaredMethod("source");method.setAccessible(true);return (String)method.invoke(null);
    }
    static String adapt(String expanded,boolean baseline)throws Exception {
        if(baseline)return legacy(expanded);
        // The production overload is deliberately private: this test exercises the same adapter after a source hook.
        for(String name:new String[]{"adapt","source"})try {
            Method method=NativeTreeLighting.class.getDeclaredMethod(name,String.class);method.setAccessible(true);return (String)method.invoke(null,expanded);
        }catch(NoSuchMethodException noSingleArgument) {
            try {
                Method method=NativeTreeLighting.class.getDeclaredMethod(name,String.class,ShaderPacks.Active.class);method.setAccessible(true);return (String)method.invoke(null,expanded,GlProgram.pack());
            }catch(NoSuchMethodException notThisSignature) {}
        }
        throw new AssertionError("Candidate production adapter overload missing (adapt/source String[, Active])");
    }
    static String hook(String source) {
        int main=source.indexOf("void main()");require(main>=0,"Actual selected entry not found");
        String entry=source.substring(main).replace("texture(uAlbedo, vUv)","vpTreeHookAlbedo111()")
                .replace("texture(uEngineLight, vUv)","vpTreeHookEngine111()")
                .replace("gNormalUnpack(texture(uNormal, vUv))","vpTreeHookNormal111()")
                .replace("scenePos(vUv, depth)","vpTreeHookPosition111(depth)");
        return source.substring(0,main)+HOOK+entry;
    }
    static String poolpit(String expanded)throws IOException {
        // Retained real PoolPit hook output, captured by the earlier read-only ownership probe.
        // Reuse today's selected Library include expansion, retaining that captured helper and wrapper verbatim.
        String retained=Files.readString(Path.of("research/error030-shader-ownership/poolpit-only-light.frag"));
        int current=expanded.indexOf("vec3 lightShare("),captured=retained.indexOf("vec3 lightShare(");
        require(current>=0 && captured>=0,"Retained Library lightShare boundary missing");
        return expanded.substring(0,current)+retained.substring(captured);
    }
    /** Exact pinned110 source adapter, retained so source-hook input can reproduce the old placement. */
    static String legacy(String source)throws Exception {
        source=source.replace("#define VIEWPOINT_LAMP_LIST","// Native trees use the existing bounded full lamp list.")
                .replace("in vec2 vUv;","vec2 vUv;").replace("void main()","void vpTreeSelectedLight()")
                .replace("texture(uDepth, vUv).r","gl_FragCoord.z").replace("texture(uAlbedo, vUv)","vec4(vpTreeArt.rgb,1.0)")
                .replace("gNormalUnpack(texture(uNormal, vUv))","vec4(0.0,1.0,0.0,vpTreeOutdoors)")
                .replace("texture(uEngineLight, vUv)","vpTreeEngine").replace("scenePos(vUv, depth)","vPos")
                .replace("occlusionAt(ivec2(gl_FragCoord.xy), n)","vec4(n,1.0)")
                .replace("contactShadowAt(ivec2(gl_FragCoord.xy), length(pos - uEye))","1.0")
                .replace("bounceAt(pos, n)","vec3(0.0)").replace("flashBounceAt(pos, n)","vec3(0.0)");
        Set<String> seen=new HashSet<>();seen.add("constants.glsl");if(source.contains("#define VIEWPOINT_LIGHT_GRID"))seen.add("lib/light_grid.glsl");
        Method expand=NativeWaterShader.class.getDeclaredMethod("expandActive",String.class,Set.class,ShaderPacks.Active.class);expand.setAccessible(true);
        String grid=source.contains("vec4 nearLight(")?"":(String)expand.invoke(null,"lib/near_light.glsl",seen,GlProgram.pack());
        int main=source.indexOf("void vpTreeSelectedLight()");require(main>=0,"Pinned110 entry missing");
        source=source.substring(0,main)+grid+field("MAIN")+source.substring(main)+field("COMPOSE");
        int version=source.indexOf('\n')+1;source=source.substring(0,version)+"#undef PIPELINE_WATER_FFT\n"+source.substring(version);
        return NativeWaterShader.overlayOptions(source,GlProgram.pack());
    }
    static String field(String name)throws Exception {Field field=NativeTreeLighting.class.getDeclaredField(name);field.setAccessible(true);return (String)field.get(null);}
    static boolean declarationsBeforeUse(String source) {
        for(String symbol:new String[]{"vPos","vpTreeArt","vpTreeEngine","vpTreeOutdoors"}) {
            int declaration=-1;
            var matcher=java.util.regex.Pattern.compile("\\b(?:vec[234]|float)\\s+(?:[A-Za-z_][A-Za-z_0-9]*\\s*,\\s*)*"+symbol+"\\s*[,;=]").matcher(source);
            if(matcher.find())declaration=matcher.start();
            var uses=java.util.regex.Pattern.compile("\\b"+symbol+"\\b").matcher(source);
            if(declaration<0 || !uses.find() || uses.start()<declaration)return false;
        }
        return true;
    }
    static Linked link(String vertex,String fragment) {
        int vs=GL20.glCreateShader(GL20.GL_VERTEX_SHADER),fs=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER),program=0;
        try {
            GL20.glShaderSource(vs,vertex);GL20.glCompileShader(vs);GL20.glShaderSource(fs,fragment);GL20.glCompileShader(fs);
            String log="VERTEX\n"+GL20.glGetShaderInfoLog(vs)+"\nFRAGMENT\n"+GL20.glGetShaderInfoLog(fs)+"\n";
            if(GL20.glGetShaderi(vs,GL20.GL_COMPILE_STATUS)==0 || GL20.glGetShaderi(fs,GL20.GL_COMPILE_STATUS)==0)return new Linked(false,log);
            program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);
            return new Linked(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,log+"LINK\n"+GL20.glGetProgramInfoLog(program)+"\n");
        }finally {if(program!=0)GL20.glDeleteProgram(program);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);}
    }
    static void render(Path out)throws Exception {
        Main.compatible=true;GlProgram.onUse(IsometricShaders::onUse);NativeTreeStyle075Probe.pack="default";
        TestShaderPacks.select("default","high",OPTIONS);NativeTreeStyle075Probe.auditOrdering();NativeTreeStyle075Probe.fixture();
        NativeTreeStyle075Probe.world=NativeTrees.class.getMethod("renderWorld",viewpoint.core.Frame.class);
        int number=0;
        for(float yaw:new float[]{0,37})for(int variant=0;variant<4;variant++) {
            boolean fog=variant==1,style=variant==2,fade=variant==3;float scale=fade?.75f:1f;
            var image=NativeTreeStyle075Probe.render(yaw,fog,style,fade,fade,scale);
            Path file=out.resolve(String.format(Locale.ROOT,"default-%02d.bin",number++));
            try(DataOutputStream data=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
                data.writeInt(image.width());data.writeInt(image.height());data.writeInt(image.pixels().length);for(float value:image.pixels())data.writeFloat(value);
                data.writeInt(image.raw().length);for(float value:image.raw())data.writeFloat(value);
                data.writeInt(image.tree().length);for(boolean value:image.tree())data.writeBoolean(value);
            }
            System.out.println("Tree111 default rendering captured yaw="+yaw+" fog="+fog+" style="+style+" fadedScaled="+fade);
        }
        NativeTreeStyle075Probe.gl("tree111 default snapshot");
    }
    static void compare(Path before,Path after)throws Exception {
        int files=0;double max=0,total=0;long values=0;
        try(var paths=Files.list(before)) {
            for(Path old:paths.filter(path->path.toString().endsWith(".bin")).sorted().toList()) {
                Path next=after.resolve(old.getFileName());require(Files.exists(next),"Missing candidate snapshot "+next);
                try(DataInputStream a=new DataInputStream(Files.newInputStream(old));DataInputStream b=new DataInputStream(Files.newInputStream(next))) {
                    require(a.readInt()==b.readInt() && a.readInt()==b.readInt(),"Snapshot target size changed");
                    for(int array=0;array<2;array++) {int length=a.readInt();require(length==b.readInt(),"Snapshot shape changed");
                        for(int i=0;i<length;i++) {float x=a.readFloat(),y=b.readFloat();require(Float.isFinite(x)&&Float.isFinite(y),"Nonfinite snapshot");double delta=Math.abs(x-y);max=Math.max(max,delta);total+=delta;values++;}}
                    int mask=a.readInt();require(mask==b.readInt(),"Mask shape changed");for(int i=0;i<mask;i++)require(a.readBoolean()==b.readBoolean(),"Native depth mask changed");
                    require(a.read()==-1 && b.read()==-1,"Trailing snapshot data");
                }files++;
            }
        }
        require(files==8,"Incomplete default matrix: "+files);require(max<=.000001,"Default output changed: max="+max+" mean="+total/Math.max(1,values));
        System.out.println("Tree111 default output parity PASS: snapshots="+files+" values="+values+" maxError="+max+" meanError="+total/values+"; actual native producer, HDR and selected final, fog/style/faded scaled stencil, yaw0/37 and unchanged depth masks.");
    }
    static int count(String text,String token){int count=0,at=0;while((at=text.indexOf(token,at))>=0){count++;at+=token.length();}return count;}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
