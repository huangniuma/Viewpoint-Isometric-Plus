import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import vpiso.*;

/** Actual VP planner and keys, with all personal inputs opened read-only; no GL required. */
public final class ShaderPreparationProbe {
    private static final String PLAN="viewpoint.platform.AheadPlan";
    private static List<?> settings(String pack, Map<String,Map<String,String>> values) {
        return (List<?>)GameAccess.call(PLAN,"settings",null,new Class<?>[]{String.class,Map.class},pack,values);
    }
    private static List<?> jobs(List<?> programs,List<?> settings,Predicate<String> cached) {
        return (List<?>)GameAccess.call(PLAN,"jobs",null,
                new Class<?>[]{List.class,List.class,String.class,Predicate.class},programs,settings,"controlled-driver",cached);
    }
    private static String key(Object job) { return (String)GameAccess.call(PLAN+"$Job","key",job,new Class<?>[0]); }
    private static void sameSetting(Object expected,Object actual) {
        Object a=GameAccess.call(PLAN+"$Setting","pack",expected,new Class<?>[0]);
        Object b=GameAccess.call(PLAN+"$Setting","pack",actual,new Class<?>[0]);
        String active="viewpoint.platform.ShaderPacks$Active";
        for(String field:List.of("pack","defines","literals"))
            if(!Objects.equals(GameAccess.field(active,field,a),GameAccess.field(active,field,b)))
                throw new AssertionError("Current setting changed: "+field);
        Object pa=GameAccess.field(active,"pipeline",a),pb=GameAccess.field(active,"pipeline",b);
        for(var field:pa.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            field.setAccessible(true);
            try {
                if(!Objects.equals(field.get(pa),field.get(pb)))throw new AssertionError("Pipeline setting changed: "+field.getName());
            } catch(IllegalAccessException e) {throw new AssertionError(e);}
        }
        if(!Objects.equals(GameAccess.call(PLAN+"$Setting","what",expected,new Class<?>[0]),
                           GameAccess.call(PLAN+"$Setting","what",actual,new Class<?>[0])))
            throw new AssertionError("Current setting label changed");
    }
    public static void main(String[] args)throws Exception {
        Properties props=new Properties();
        try(InputStream input=Files.newInputStream(Path.of(args[0]))) {props.load(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8));}
        Map<String,Map<String,String>> saved=new HashMap<>();
        for(String key:props.stringPropertyNames())if(key.startsWith("pack.")) {
            int split=key.indexOf('.',5);if(split<0)continue;
            saved.computeIfAbsent(key.substring(5,split),k->new HashMap<>()).put(key.substring(split+1),props.getProperty(key));
        }
        GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},
                new java.io.File("C:/Users/Win10/Zomboid/viewpoint-shaderpacks"));
        // The installed list is only read. No cache folder is registered or modified.
        List<Object> programs=new ArrayList<>();
        var ctor=GameAccess.type(PLAN+"$Program").getDeclaredConstructor(String.class,String[].class);ctor.setAccessible(true);
        for(String line:Files.readAllLines(Path.of(args[1]))) {
            String[] fields=line.split("\t",-1);if(fields.length!=7)throw new AssertionError("Program list format");
            String[] paths=Arrays.copyOfRange(fields,1,7);for(int i=0;i<paths.length;i++)if(paths[i].isEmpty())paths[i]=null;
            programs.add(ctor.newInstance(fields[0],paths));
        }
        boolean woven=args.length>2 && args[2].equals("woven");
        for(String pack:List.of("library","default","vivid","somber","trueweathers")) {
            Main.compatible=false;List<?> raw=settings(pack,saved);
            Main.compatible=true;List<?> fixed=woven?settings(pack,saved):ShaderPreparation.current(pack,raw);
            if(fixed.size()!=2)
                throw new AssertionError("Current generic/literal settings not retained: "+pack);
            for(int i=0;i<2;i++) {
                sameSetting(raw.get(i),fixed.get(i));
                if(!woven && fixed.get(i)!=raw.get(i))throw new AssertionError("Original setting object replaced");
            }
            // Both sets use the exact same transformed GLSL; the only change is planning.
            long start=System.nanoTime();List<?> before=jobs(programs,raw,k->false);long fullMs=(System.nanoTime()-start)/1_000_000;
            start=System.nanoTime();List<?> after=jobs(programs,fixed,k->false);long currentMs=(System.nanoTime()-start)/1_000_000;
            Set<String> previous=new HashSet<>();for(Object job:before)previous.add(key(job));
            for(Object job:after)if(!previous.contains(key(job)))throw new AssertionError("Shader/cache key changed");
            if(after.isEmpty() || after.size()>=before.size())throw new AssertionError("No useful planning reduction");
            Set<String> warm=new HashSet<>();for(Object job:after)warm.add(key(job));
            if(!jobs(programs,fixed,warm::contains).isEmpty())throw new AssertionError("Warm cache recompiled");
            if(jobs(programs,fixed,k->warm.contains(k)&&!k.equals(key(after.get(0)))).size()!=1)
                throw new AssertionError("Partial cache miss did not prepare exactly one program");
            System.out.println("Shader preparation PASS: pack="+pack+"; configurations="+raw.size()+" -> "+fixed.size()
                +"; cold programs="+before.size()+" -> "+after.size()+"; planning ms="+fullMs+" -> "+currentMs
                +"; warm misses=0; partial misses=1; current keys retained; woven="+woven);
        }
        Main.compatible=false;List<?> normal=settings("normal",saved);
        Main.compatible=true;
        if(ShaderPreparation.current("normal",normal)!=normal || ShaderPreparation.current("third-party",normal)!=normal)
            throw new AssertionError("Unsupported pack plan changed");
        Main.compatible=false;
        if(ShaderPreparation.current("vivid",normal)!=normal)throw new AssertionError("Unsupported VP plan changed");
        System.out.println("Shader preparation isolation PASS: unsupported VP/Normal/third-party unchanged; no settings/cache writes or game launched.");
    }
}
