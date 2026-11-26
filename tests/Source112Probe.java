import java.io.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import viewpoint.packs.*;
import viewpoint.render.PackModel;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

/** CPU-only exact private NativeSource.packed comparison; actual Viewpoint model/bind classes. */
public final class Source112Probe {
    static final sun.misc.Unsafe UNSAFE;
    static final Field MODEL_BOUNDS, BOUND;
    static volatile float[] sink;
    static long digest=112;
    static int cases, valid, nulls;
    static final ArrayList<String> events=new ArrayList<>();
    static boolean tracing;
    static {
        try {
            Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);UNSAFE=(sun.misc.Unsafe)u.get(null);
            MODEL_BOUNDS=PackModel.class.getDeclaredField("bounds");MODEL_BOUNDS.setAccessible(true);
            BOUND=ModelPacks.class.getDeclaredField("bound");BOUND.setAccessible(true);
        } catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    static final class Feed implements ModelFeed {
        boolean named;
        PackBind value;
        Throwable failure;
        boolean failNames;
        int names,binds;
        public boolean any(){return true;}
        void fail(){if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;}
        public boolean names(IsoSprite sprite){names++;if(tracing)events.add("names");if(failNames)fail();return named;}
        public PackBind bind(IsoObject object,IsoSprite sprite){binds++;if(tracing)events.add("bind");if(!failNames)fail();return value;}
        public void refresh(){}
        public int generation(){return 1;}
        void reset(){names=binds=0;events.clear();}
    }
    static final class Target implements AutoCloseable {
        final URLClassLoader loader;
        final Class<?> source;
        final MethodHandle packed;
        final Constructor<?> constructor;
        Target(Path file)throws Exception {
            loader=new URLClassLoader(new URL[]{file.toUri().toURL()},Source112Probe.class.getClassLoader()) {
                @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                    if(!name.equals("vpiso.NativeSource"))return super.loadClass(name,resolve);
                    Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
                }
            };
            source=loader.loadClass("vpiso.NativeSource");
            Method method=source.getDeclaredMethod("packed",IsoObject.class,IsoSprite.class);method.setAccessible(true);
            packed=MethodHandles.lookup().unreflect(method);
            constructor=source.getConstructor(float.class,float.class,float.class,int.class,float[].class,int.class);
        }
        float[] packed(IsoObject object,IsoSprite sprite)throws Throwable{return (float[])packed.invokeExact(object,sprite);}
        String source(float[] bounds)throws Exception {
            Object object=constructor.newInstance(103.5f,207.5f,-2f,3,bounds,42);
            float[] actual=(float[])source.getField("bounds").get(object);
            if(actual==bounds)throw new AssertionError("NativeSource bounds lost immutable copy");
            return source.getField("key").get(object)+":"+Arrays.toString((float[])source.getField("owner").get(object));
        }
        public void close()throws IOException{loader.close();}
    }
    static PackModel model(float[] bounds)throws Exception {
        Constructor<PackModel> ctor=PackModel.class.getDeclaredConstructor(int.class,File.class,int.class);ctor.setAccessible(true);
        PackModel model=ctor.newInstance(0,new File("Source112-does-not-read.obj"),0);
        MODEL_BOUNDS.set(model,bounds);return model;
    }
    @SuppressWarnings("unchecked") static HashMap<String,PackBind> bound()throws Exception{return (HashMap<String,PackBind>)BOUND.get(null);}
    static IsoSprite sprite(IsoObjectType type)throws Exception {
        IsoSprite sprite=(IsoSprite)UNSAFE.allocateInstance(IsoSprite.class);sprite.name="source112";sprite.setType(type);return sprite;
    }
    static void compare(Target old,Target now,Feed feed,IsoObject object,IsoSprite sprite,String label)throws Throwable {
        feed.reset();float[] a=old.packed(object,sprite);int names=feed.names,binds=feed.binds;List<String> before=List.copyOf(events);
        feed.reset();float[] b=now.packed(object,sprite);
        if(names!=feed.names || binds!=feed.binds || !before.equals(events))throw new AssertionError("provider order drift "+label);
        if(names!=1 || binds!=(feed.named?1:0))throw new AssertionError("provider call count "+label);
        if((a==null)!=(b==null))throw new AssertionError("null drift "+label);
        if(a==null){nulls++;digest=digest*31;}
        else {
            if(a.length!=6 || b.length!=6)throw new AssertionError("length "+label);
            for(int n=0;n<6;n++){
                int bits=Float.floatToRawIntBits(a[n]);
                if(bits!=Float.floatToRawIntBits(b[n]))throw new AssertionError("raw float drift "+label+" "+Arrays.toString(a)+" "+Arrays.toString(b));
                digest=digest*31+bits;
            }
            if(!old.source(a).equals(now.source(b)))throw new AssertionError("source key/owner drift "+label);
            valid++;
        }
        cases++;
    }
    static void parity(Path baseline,Path candidate)throws Throwable {
        Feed feed=new Feed();ModelPacks.feed(feed);tracing=true;
        try(Target old=new Target(baseline);Target now=new Target(candidate)) {
            IsoObject object=(IsoObject)UNSAFE.allocateInstance(IsoObject.class);IsoSprite sprite=sprite(IsoObjectType.normal);
            HashMap<String,PackBind> bound=bound();bound.clear();
            PackModel model=model(null);PackBind bind=new PackBind(model,37.5f,1.25f,-.75f,.125f,1.5f);
            compare(old,now,feed,object,sprite,"missing");bound.put(sprite.name,bind);
            compare(old,now,feed,object,sprite,"unready");
            MODEL_BOUNDS.set(model,new float[]{-1,-2,-3,4,5,6});compare(old,now,feed,object,sprite,"ready-without-rebind");
            bound.clear();compare(old,now,feed,object,sprite,"removed");bound.put(sprite.name,bind);
            compare(old,now,feed,object,sprite,"restored-without-name-change");
            float[][] special={null,new float[0],new float[5],new float[7],new float[]{0,0,0,0,1,1},
                new float[]{0,0,0,.001f,.001f,.001f},new float[]{0,0,0,.0009f,1,1},
                new float[]{-0f,-0f,-0f,1,1,1},new float[]{Float.NaN,0,0,1,1,1},
                new float[]{0,0,0,Float.POSITIVE_INFINITY,1,1},new float[]{Float.NEGATIVE_INFINITY,0,0,1,1,1},
                new float[]{-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE}};
            for(int i=0;i<special.length;i++){MODEL_BOUNDS.set(model,special[i]);compare(old,now,feed,object,sprite,"special"+i);}
            Random random=new Random(112);IsoObjectType[] types={IsoObjectType.normal,IsoObjectType.stairsBN,IsoObjectType.stairsMN,IsoObjectType.stairsTN,IsoObjectType.stairsBW,IsoObjectType.stairsMW,IsoObjectType.stairsTW};
            for(int i=0;i<1200;i++) {
                float[] bounds=new float[6];for(int axis=0;axis<3;axis++){bounds[axis]=(random.nextFloat()-.5f)*20;bounds[axis+3]=bounds[axis]+.01f+random.nextFloat()*10;}
                MODEL_BOUNDS.set(model,bounds);sprite.setType(types[i%types.length]);
                bind=new PackBind(model,(random.nextFloat()-.5f)*1440,(random.nextFloat()-.5f)*5,(random.nextFloat()-.5f)*5,(random.nextFloat()-.5f)*5,(i%31==0?0:1)*(random.nextFloat()-.5f)*8);
                bound.put(sprite.name,bind);feed.named=i%3!=0;feed.value=i%11==0?ModelFeed.PASS:i%13==0?null:bind;
                float[] snapshot=bounds.clone();
                compare(old,now,feed,object,sprite,"random"+i);
                if(!Arrays.equals(snapshot,model.bounds()))throw new AssertionError("shared model bounds changed");
            }
            feed.named=true;feed.value=bind;
            for(float value:new float[]{0,-0f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MAX_VALUE,Float.MIN_VALUE}) {
                MODEL_BOUNDS.set(model,new float[]{-.5f,0,-.5f,.5f,2,.5f});
                for(int field=0;field<5;field++) {
                    float[] params={33.75f,.25f,-.25f,1,1};params[field]=value;
                    feed.value=new PackBind(model,params[0],params[1],params[2],params[3],params[4]);
                    compare(old,now,feed,object,sprite,"bind-nonfinite"+field+"/"+value);
                }
            }
            System.out.println("PARITY112 PASS cases="+cases+" valid="+valid+" nulls="+nulls+" digest="+Long.toUnsignedString(digest)+"; raw exact bounds, key/owner, immutable copy, feed calls/order, all stair types, invalid geometry, missing/unready/ready/remove/restore, live bounds and binding replacement.");
        } finally {tracing=false;ModelPacks.feed(ModelFeed.NONE);bound().clear();}
    }
    static Throwable thrown(Target target,IsoObject object,IsoSprite sprite)throws Throwable {
        try {target.packed(object,sprite);}catch(Throwable error){return error;}
        throw new AssertionError("provider failure did not propagate");
    }
    static String chain(Throwable error){return error.getClass().getName()+":"+error.getMessage()+(error.getCause()==null?"":" -> "+chain(error.getCause()));}
    static void exceptions(Path baseline,Path candidate)throws Throwable {
        Feed feed=new Feed();feed.named=true;ModelPacks.feed(feed);int checked=0;
        try(Target old=new Target(baseline);Target now=new Target(candidate)) {
            IsoObject object=(IsoObject)UNSAFE.allocateInstance(IsoObject.class);IsoSprite sprite=sprite(IsoObjectType.normal);
            for(boolean names:List.of(false,true))for(Throwable cause:List.of(new IllegalArgumentException("source112-provider"),new AssertionError("source112-provider-error"))) {
                feed.failNames=names;feed.failure=cause;feed.reset();Throwable a=thrown(old,object,sprite);int namesCalls=feed.names,bindCalls=feed.binds;
                feed.reset();Throwable b=thrown(now,object,sprite);
                if(!(a instanceof IllegalStateException) || !(a.getCause() instanceof InvocationTargetException) || a.getCause().getCause()!=cause)throw new AssertionError("baseline wrapper drift");
                if(!(b instanceof IllegalStateException) || !(b.getCause() instanceof InvocationTargetException) || b.getCause().getCause()!=cause)throw new AssertionError("candidate wrapper drift");
                if(!chain(a).equals(chain(b)) || namesCalls!=feed.names || bindCalls!=feed.binds || namesCalls!=1 || bindCalls!=(names?0:1))throw new AssertionError("provider exception/count drift");
                checked++;
            }
            System.out.println("EXCEPTION112 PASS cases="+checked+"; provider names/bind RuntimeException/Error keep exact IllegalStateException/InvocationTargetException/cause identity and call counts.");
        } finally {ModelPacks.feed(ModelFeed.NONE);}
    }
    static long run(Target target,IsoObject[] objects,IsoSprite[] sprites,int calls)throws Throwable {
        long start=System.nanoTime();for(int i=0;i<calls;i++)sink=target.packed(objects[i&127],sprites[i&127]);return System.nanoTime()-start;
    }
    static void bench(Path classes,String label,int warm,int calls)throws Throwable {
        Feed feed=new Feed();ModelPacks.feed(feed);HashMap<String,PackBind> map=bound();map.clear();
        IsoObject[] objects=new IsoObject[128];IsoSprite[] sprites=new IsoSprite[128];
        for(int i=0;i<128;i++) {
            objects[i]=(IsoObject)UNSAFE.allocateInstance(IsoObject.class);IsoSprite sprite=sprite(i%2==0?IsoObjectType.normal:IsoObjectType.stairsBN);sprite.name="source112-"+i;sprites[i]=sprite;
            PackModel model=model(new float[]{-.3f-i*.001f,-.2f,-.75f,.5f,1.5f+i*.01f,.6f});
            map.put(sprite.name,new PackBind(model,i*11.25f,(i-64)*.01f,(64-i)*.005f,i*.002f,.5f+i*.01f));
        }
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long tid=Thread.currentThread().getId();
        try(Target target=new Target(classes)) {
            for(String scenario:List.of("map-ready","feed-ready","map-unready","map-missing")) {
                feed.named=scenario.equals("feed-ready");feed.value=map.get(sprites[0].name);
                if(scenario.equals("map-unready"))for(PackBind bind:map.values())MODEL_BOUNDS.set(bind.model,null);
                if(scenario.equals("map-missing"))map.clear();
                for(int n=0;n<8;n++)run(target,objects,sprites,warm);
                double[] ns=new double[9],cpu=new double[9],bytes=new double[9];
                long totalCpu=bean.getCurrentThreadCpuTime(),totalAllocated=bean.getThreadAllocatedBytes(tid);
                for(int n=0;n<ns.length;n++) {
                    long alloc=bean.getThreadAllocatedBytes(tid),ct=bean.getCurrentThreadCpuTime();
                    ns[n]=run(target,objects,sprites,calls)/(double)calls;
                    cpu[n]=(bean.getCurrentThreadCpuTime()-ct)/(double)calls;bytes[n]=(bean.getThreadAllocatedBytes(tid)-alloc)/(double)calls;
                }
                double aggregateCpu=(bean.getCurrentThreadCpuTime()-totalCpu)/(9.*calls),aggregateBytes=(bean.getThreadAllocatedBytes(tid)-totalAllocated)/(9.*calls);
                Arrays.sort(ns);Arrays.sort(cpu);Arrays.sort(bytes);
                System.out.printf(Locale.ROOT,"BENCH112 {\"label\":\"%s\",\"case\":\"%s\",\"callsPerSample\":%d,\"samples\":9,\"warmupCalls\":%d,\"wallNs\":%.3f,\"cpuNs\":%.3f,\"bytes\":%.3f,\"aggregateCpuNs\":%.3f,\"aggregateBytes\":%.3f}%n",label,scenario,calls,8*warm,ns[4],cpu[4],bytes[4],aggregateCpu,aggregateBytes);
            }
        } finally {ModelPacks.feed(ModelFeed.NONE);map.clear();}
    }
    public static void main(String[] args)throws Throwable {
        if(args[0].equals("parity"))parity(Path.of(args[1]),Path.of(args[2]));
        else if(args[0].equals("exceptions"))exceptions(Path.of(args[1]),Path.of(args[2]));
        else if(args[0].equals("bench"))bench(Path.of(args[1]),args[2],Integer.parseInt(args[3]),Integer.parseInt(args[4]));
        else throw new IllegalArgumentException("parity baselineJar candidateClasses | bench classes label warmupPerRound callsPerSample");
    }
}

