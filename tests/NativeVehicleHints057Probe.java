import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.pool.TypePool;
import static net.bytebuddy.matcher.ElementMatchers.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Controlled CPU native producer/queue probe. No game launch, native physics, or GPU drawing. */
public class NativeVehicleHints057Probe {
    public static Object candidate;
    public static final List<float[]> output = new ArrayList<>();
    public static int shaders, depths, blends;
    private static ClassLoader loader;
    private static Object unsafe;
    private static Method allocate;
    private static List<Object> lines;
    private static ArrayDeque<Object> pool;

    public static class SelectedPart {
        @Advice.OnMethodEnter(skipOn=Advice.OnNonDefaultValue.class)
        public static boolean enter() { return true; }
        @Advice.OnMethodExit
        public static void exit(@Advice.Return(readOnly=false,typing=Assigner.Typing.DYNAMIC) Object value) {
            value = NativeVehicleHints057Probe.candidate;
        }
    }
    public static class ScreenSink {
        @Advice.OnMethodEnter(skipOn=Advice.OnNonDefaultValue.class)
        public static boolean enter(@Advice.AllArguments Object[] args) {
            float[] record = new float[args.length];
            for(int i=0;i<args.length;i++) record[i]=((Number)args[i]).floatValue();
            NativeVehicleHints057Probe.output.add(record);
            return true;
        }
    }
    public static class QueueStateSink {
        @Advice.OnMethodEnter(skipOn=Advice.OnNonDefaultValue.class)
        public static boolean enter(@Advice.Origin("#m") String name) {
            if(name.equals("StartShader")) NativeVehicleHints057Probe.shaders++;
            if(name.equals("disableDepthTest")) NativeVehicleHints057Probe.depths++;
            if(name.equals("glBlendFunc")) NativeVehicleHints057Probe.blends++;
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("addon JAR required");
        Map<String,byte[]> entries=new HashMap<>();
        try(JarFile jar=new JarFile(args[0])) {
            for(JarEntry e:jar.stream().filter(e->e.getName().startsWith("vpiso/")&&e.getName().endsWith(".class")).toList())
                entries.put(e.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(e).readAllBytes());
        }
        boolean fixed=entries.containsKey("vpiso.NativeVehicleHints");
        if(!fixed) {
            String bridge=new String(entries.get("vpiso.IsometricBridge"),java.nio.charset.StandardCharsets.ISO_8859_1);
            require(!bridge.contains("NativeVehicleHints"),"Baseline has an unexpected producer call");
            System.out.println("PINNED 056 NEGATIVE: no NativeVehicleHints class or bridge call; omitted producer is structurally confirmed. Native producer checks run below, not a full bridge/GPU execution.");
        }
        var locator=ClassFileLocator.ForClassLoader.ofSystemLoader();
        var types=TypePool.Default.of(locator);
        entries.put("zombie.vehicles.BaseVehicle",new ByteBuddy().redefine(types.describe("zombie.vehicles.BaseVehicle").resolve(),locator)
                .visit(Advice.to(SelectedPart.class).on(named("getUseablePart").and(takesArguments(1)))).make().getBytes());
        entries.put("zombie.debug.LineDrawer",new ByteBuddy().redefine(types.describe("zombie.debug.LineDrawer").resolve(),locator)
                .visit(Advice.to(ScreenSink.class).on(named("drawLine").and(takesArguments(9)))).make().getBytes());
        entries.put("zombie.IndieGL",new ByteBuddy().redefine(types.describe("zombie.IndieGL").resolve(),locator)
                .visit(Advice.to(QueueStateSink.class).on(named("StartShader").and(takesArguments(int.class))
                        .or(named("disableDepthTest").and(takesArguments(0)))
                        .or(named("glBlendFunc").and(takesArguments(int.class,int.class))))).make().getBytes());
        var ct=Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class,boolean.class);
        ct.setAccessible(true);loader=(ClassLoader)ct.newInstance(entries,true);
        Field uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=uf.get(null);
        allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
        Object vehicle=fresh("zombie.vehicles.BaseVehicle");
        set(vehicle,"alpha",new float[]{1,1,1,1});set(vehicle,"targetAlpha",new float[]{1,1,1,1});
        set(vehicle,"x",100f);set(vehicle,"y",200f);set(vehicle,"z",2f);
        Object transform=type("zombie.core.physics.Transform").getConstructor().newInstance();
        ((Vector3f)get(transform,"origin")).set(100f,2f*2.44949f,200f);
        set(vehicle,"jniTransform",transform);
        Object physics=fresh("zombie.core.physics.CarController");
        set(physics,"vehicleObject",vehicle);set(physics,"tempVec3f3",new Vector3f());set(vehicle,"physics",physics);
        Object simulation=staticGet("zombie.core.physics.WorldSimulation","instance");
        set(simulation,"offsetX",0f);set(simulation,"offsetY",0f);
        Object script=type("zombie.scripting.objects.VehicleScript").getConstructor().newInstance();set(vehicle,"script",script);
        Object area=type("zombie.scripting.objects.VehicleScript$Area").getConstructor().newInstance();
        set(area,"id","DoorFrontLeft");set(area,"x",1f);set(area,"y",2f);set(area,"w",2f);set(area,"h",4f);
        ((List<Object>)get(script,"areas")).add(area);
        Object partScript=type("zombie.scripting.objects.VehicleScript$Part").getConstructor().newInstance();
        set(partScript,"id","DoorFrontLeft");set(partScript,"area","DoorFrontLeft");
        candidate=fresh("zombie.vehicles.VehiclePart");set(candidate,"scriptPart",partScript);
        set(candidate,"door",fresh("zombie.vehicles.VehicleDoor"));
        Object player=fresh("zombie.characters.IsoPlayer");set(player,"ecsComponentMap",new HashMap<>());
        field(type("zombie.characters.IsoPlayer"),"instance").set(null,player);
        Object state=staticGet("zombie.iso.IsoCamera","frameState");set(state,"playerIndex",0);
        field(type("zombie.ui.UIManager"),"visibleAllUi").set(null,true);
        lines=(List<Object>)staticGet("zombie.debug.LineDrawer","lines");
        pool=(ArrayDeque<Object>)staticGet("zombie.debug.LineDrawer","pool");lines.clear();pool.clear();
        Object cell=fresh("zombie.iso.IsoCell");set(cell,"vehicles",new LinkedHashSet<>(List.of(vehicle)));
        invoke(vehicle,"renderUsableArea",new Class<?>[0]);
        require(lines.size()==8,"Actual native door producer must emit 8 lines: "+lines.size());
        List<float[]> expected=copyLines();
        Object core=type("zombie.core.Core").getMethod("getInstance").invoke(null);
        Object color=core.getClass().getMethod("getGoodHighlitedColor").invoke(core);
        for(float[] line:expected) {near(line[6],number(color,"r"));near(line[7],number(color,"g"));near(line[8],number(color,"b"));near(line[9],1f);near(line[2],2f);near(line[5],2f);}
        System.out.println("NATIVE PRODUCER PASS: unchanged private producer, vehicle transform, area, door strip, native configured RGB, native alpha and 8 world queue lines.");
        recycle();
        if(!fixed) return;
        Object sentinel=type("zombie.debug.LineDrawer$DrawableLine").getConstructor().newInstance();
        set(sentinel,"alpha",.37f);set(sentinel,"xstart",-133f);lines.add(sentinel);
        List<Object> decaying=(List<Object>)staticGet("zombie.debug.LineDrawer","alphaDecayingLines");
        Object fading=type("zombie.debug.LineDrawer$DrawableLine").getConstructor().newInstance();
        set(fading,"alpha",.51f);decaying.add(fading);
        int poolBefore=pool.size();
        render(cell,null);
        require(lines.size()==9&&lines.get(0)==sentinel,"Fixed view must preserve sentinel and defer 8 appended lines");
        require(output.isEmpty(),"Fixed view must not consume lines early");
        near(number(sentinel,"alpha"),.37f);near(number(sentinel,"xstart"),-133f);
        compare(expected,copyLines().subList(1,9));
        while(lines.size()>1) pool.push(lines.remove(lines.size()-1));
        require(pool.size()==poolBefore,"Fixed view pool must balance after native-style recycle");
        Class<?> snapshot=type("vpiso.RotationCamera$Snapshot");
        Object rotation=snapshot.getConstructor(Matrix4f.class,Matrix4f.class,float.class,float.class,float.class,int.class,int.class,float.class,float.class)
                .newInstance(new Matrix4f().ortho(-20,20,-20,20,-20,20),new Matrix4f().rotateY((float)Math.PI/2),100f,200f,2f,800,600,1.5f,90f);
        for(int frame=0;frame<3;frame++) {
            output.clear();shaders=depths=blends=0;render(cell,rotation);
            require(lines.size()==1&&lines.get(0)==sentinel,"Rotation must remove only appended lines");
            require(pool.size()==poolBefore,"Rotation must return all 8 records to the original pool");
            require(pool.stream().noneMatch(p->p==sentinel||p==fading),"Unrelated lines must never enter the temporary hint pool");
            require(decaying.size()==1&&decaying.get(0)==fading,"Fading global lines must stay untouched");near(number(fading,"alpha"),.51f);
            require(output.size()==8,"Rotation emits exactly 8 screen lines, without previous/global lines");
            require(shaders==1&&depths==1&&blends==1,"Native line queue state must be submitted once");
            near(number(sentinel,"alpha"),.37f);near(number(sentinel,"xstart"),-133f);
            for(int i=0;i<8;i++) {
                float[] from=new float[3],to=new float[3],world=expected.get(i),draw=output.get(i);
                snapshot.getMethod("project",double.class,double.class,double.class,float[].class).invoke(rotation,(double)world[0],(double)world[1],(double)world[2],from);
                snapshot.getMethod("project",double.class,double.class,double.class,float[].class).invoke(rotation,(double)world[3],(double)world[4],(double)world[5],to);
                near(draw[0],from[0]*1.5f);near(draw[1],from[1]*1.5f);near(draw[2],to[0]*1.5f);near(draw[3],to[1]*1.5f);
                for(int c=0;c<4;c++)near(draw[4+c],world[6+c]);near(draw[8],1f);
            }
        }
        Object clipped=snapshot.getConstructor(Matrix4f.class,Matrix4f.class,float.class,float.class,float.class,int.class,int.class,float.class,float.class)
                .newInstance(new Matrix4f().zero(),new Matrix4f(),100f,200f,2f,800,600,1.5f,90f);
        output.clear();render(cell,clipped);
        require(output.isEmpty()&&lines.size()==1&&lines.get(0)==sentinel&&pool.size()==poolBefore,"Rejected projections must still recycle only the appended records");
        for(String gate:List.of("alpha","ui","script")) {
            if(gate.equals("alpha")) ((float[])get(vehicle,"alpha"))[0]=0;
            if(gate.equals("ui")) field(type("zombie.ui.UIManager"),"visibleAllUi").set(null,false);
            if(gate.equals("script")) set(vehicle,"script",null);
            output.clear();render(cell,rotation);
            require(lines.size()==1&&output.isEmpty()&&pool.size()==poolBefore,"Original "+gate+" gate must emit nothing");
            ((float[])get(vehicle,"alpha"))[0]=1;field(type("zombie.ui.UIManager"),"visibleAllUi").set(null,true);set(vehicle,"script",script);
        }
        System.out.println("STAGED 057 PASS: fixed view defers the 8 actual native lines; 3 rotated frames project exactly once, preserve sentinel/global and fading lines/alpha, balance pool including rejected projections, native UI/alpha/script gates. Candidate selection and screen GPU sink are controlled boundaries.");
    }
    private static void render(Object cell,Object rotation)throws Exception {
        type("vpiso.NativeVehicleHints").getMethod("render",Object.class,type("vpiso.RotationCamera$Snapshot")).invoke(null,cell,rotation);
    }
    private static List<float[]> copyLines()throws Exception {
        List<float[]> out=new ArrayList<>();for(Object line:lines) {float[] v=new float[10];int i=0;for(String n:List.of("xstart","ystart","zstart","xend","yend","zend","red","green","blue","alpha"))v[i++]=number(line,n);out.add(v);}return out;
    }
    private static void compare(List<float[]> a,List<float[]> b){require(a.size()==b.size(),"Line count mismatch");for(int i=0;i<a.size();i++)for(int j=0;j<10;j++)near(a.get(i)[j],b.get(i)[j]);}
    private static void recycle(){while(!lines.isEmpty())pool.push(lines.remove(lines.size()-1));}
    private static Class<?> type(String n)throws Exception{return Class.forName(n,true,loader);}
    private static Object fresh(String n)throws Exception{return allocate.invoke(unsafe,type(n));}
    private static Field field(Class<?> c,String n)throws Exception{for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    private static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
    private static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
    private static Object staticGet(String c,String n)throws Exception{return field(type(c),n).get(null);}
    private static float number(Object o,String n)throws Exception{return ((Number)get(o,n)).floatValue();}
    private static Object invoke(Object o,String n,Class<?>[] p,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,p);m.setAccessible(true);try{return m.invoke(o,a);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
    private static void near(float a,float b){if(!Float.isFinite(a)||Math.abs(a-b)>.0001f)throw new AssertionError(a+" expected "+b);}
    private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
