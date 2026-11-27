import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import viewpoint.game.ChunkLoads;
import viewpoint.platform.LiveSettings;
import viewpoint.platform.ShaderPacks;
import vpiso.*;
import zombie.characters.IsoPlayer;
import zombie.iso.*;

/** CPU-only original VP adoption scheduler and actual woven ChunkQueue boundary; deterministic supplied time. */
public final class PreloadQueue115Probe {
    static final long START=10_000_000_000L, FRAME=16_000_000L, BUDGET=2_000_000L, WAIT=150_000_000L;
    static int checks;
    static Class<?> queueLoads;
    static final Method CLEAR=method(ChunkLoads.class,"clear");
    static final LinkedHashMap<Field,Object> saved=new LinkedHashMap<>();
    static final LinkedHashMap<LiveSettings.Number,Float> tuning=new LinkedHashMap<>();

    public static void main(String[] args)throws Exception {
        boolean schedulerOnly=args.length>0 && args[0].equals("scheduler");
        try {
            tune(ChunkLoads.class);
            scheduler();
            if(!schedulerOnly) {
                if(args.length<2)throw new IllegalArgumentException("all <actual-woven-ChunkQueue.class>");
                woven(Path.of(args[1]));
            }
            System.out.println("PRELOADQUEUE115 CPU PASS checks="+checks+" mode="+(schedulerOnly?"scheduler":"scheduler+actual-woven-queue")
                +"; original credit carry, far splitting, current/predicted urgent, MP age/oldest, SP, gates; supplied nanoseconds, no sleep/GPU/game launch.");
        } finally {
            clear();
            for(var entry:tuning.entrySet())field(LiveSettings.Number.class,"value").setFloat(entry.getKey(),entry.getValue());
            List<Map.Entry<Field,Object>> restore=new ArrayList<>(saved.entrySet());Collections.reverse(restore);
            for(var entry:restore)entry.getKey().set(null,entry.getValue());
        }
    }

    static int next(Object[] objects,int[] x,int[] y,long now) {
        return ChunkLoads.next(objects,x,y,objects.length,now);
    }
    static void clear()throws Exception {CLEAR.invoke(null);}
    static void entered(boolean mp,float x,float y,long now) {ChunkLoads.entered(true,mp,2,x,y,now);}
    static void scheduler()throws Exception {
        Object a=new Object(),b=new Object();
        clear();entered(false,4,4,START);
        require(ChunkLoads.taking(),"Scheduler did not enter");
        require(next(new Object[]{a,b},new int[]{10,12},new int[]{0,0},START)==0,"Initial far order");
        require(next(new Object[]{b},new int[]{12},new int[]{0},START+BUDGET+100)==-1,"Far chunk escaped exhausted frame");
        ChunkLoads.left(START+BUDGET+100);require(!ChunkLoads.taking(),"left did not leave");
        entered(false,4,4,START+FRAME);
        require(next(new Object[]{b},new int[]{12},new int[]{0},START+FRAME)==0,"Far chunk failed to progress next frame");
        ChunkLoads.left(START+FRAME);

        // Admission includes work between takes, and overrun debt carries to the next frame.
        clear();entered(false,4,4,START);
        require(next(new Object[]{a},new int[]{10},new int[]{0},START)==0,"Debt initial take");
        ChunkLoads.left(START+BUDGET*2+100);
        entered(false,4,4,START+FRAME);
        require(next(new Object[]{b},new int[]{12},new int[]{0},START+FRAME)==-1,"Overrun debt was dropped");
        ChunkLoads.left(START+FRAME);
        entered(false,4,4,START+FRAME*2);
        require(next(new Object[]{b},new int[]{12},new int[]{0},START+FRAME*2)==0,"Debt prevented later recovery");
        ChunkLoads.left(START+FRAME*2);

        for(float position:new float[]{4f,-4f}) {
            clear();entered(false,position,position,START);
            require(next(new Object[]{a},new int[]{10},new int[]{10},START)==0,"Near setup burn");
            int chunk=Math.floorDiv((int)Math.floor(position),8);
            require(next(new Object[]{b},new int[]{chunk-1},new int[]{chunk+1},START+BUDGET+100)==0,"Current/negative neighbor urgent delayed");
            ChunkLoads.left(START+BUDGET+100);
        }

        // Four tiles in 250ms predicts the path out to chunk2; chunk3 is beyond current 3x3 but urgent.
        clear();entered(false,4,4,START);ChunkLoads.left(START);
        long moved=START+250_000_000L;entered(false,8,4,moved);
        Object order=field(ChunkLoads.class,"order").get(null);
        require((Boolean)method(order.getClass(),"urgent",int.class,int.class).invoke(order,3,0),"Predicted fixture is not urgent");
        require(Math.abs(3-Math.floorDiv(8,8))>1,"Predicted fixture stayed in immediate ring");
        require(next(new Object[]{a},new int[]{20},new int[]{10},moved)==0,"Predicted setup burn");
        require(next(new Object[]{b},new int[]{3},new int[]{0},moved+BUDGET+100)==0,"Predicted urgent delayed by budget");
        ChunkLoads.left(moved+BUDGET+100);
        entered(false,400,4,moved+FRAME);
        require(!(Boolean)method(order.getClass(),"urgent",int.class,int.class).invoke(order,3,0),"Teleport retained old urgent path");
        ChunkLoads.left(moved+FRAME);

        // Multiplayer deadline is strictly greater-than; the same objects keep their original arrival age.
        clear();entered(true,4,4,START);
        require(next(new Object[]{a,b},new int[]{10,20},new int[]{0,0},START)==0,"MP initial take");
        require(next(new Object[]{b},new int[]{20},new int[]{0},START+BUDGET+100)==-1,"MP ignored ordinary budget");
        require(next(new Object[]{b},new int[]{20},new int[]{0},START+WAIT)==-1,"MP admitted before maximum-wait boundary");
        require(next(new Object[]{b},new int[]{20},new int[]{0},START+WAIT+1)==0,"MP maximum wait failed to release far chunk");
        ChunkLoads.left(START+WAIT+1);

        clear();entered(true,4,4,START);
        Object old=new Object(),recent=new Object();
        require(next(new Object[]{a,old},new int[]{10,20},new int[]{0,0},START)==0,"Oldest initial take");
        require(next(new Object[]{recent,old},new int[]{12,20},new int[]{0,0},START+WAIT/2)==-1,"Oldest setup should defer");
        require(next(new Object[]{recent,old},new int[]{12,20},new int[]{0,0},START+WAIT+1)==1,"MP chose closer newer chunk instead of oldest");
        ChunkLoads.left(START+WAIT+1);

        clear();entered(false,4,4,START);
        require(next(new Object[]{a,b},new int[]{10,20},new int[]{0,0},START)==0,"SP initial take");
        require(next(new Object[]{b},new int[]{20},new int[]{0},START+WAIT*10)==-1,"SP acquired MP overdue bypass");
        ChunkLoads.left(START+WAIT*10);
        // Preserve VP's literal WAIT=0 behavior: no arrival-age override of exhausted credit.
        field(LiveSettings.Number.class,"value").setFloat(field(ChunkLoads.class,"WAIT").get(null),0f);
        clear();entered(true,4,4,START);
        require(next(new Object[]{a,b},new int[]{10,20},new int[]{0,0},START)==0,"Zero-wait initial take");
        require(next(new Object[]{b},new int[]{20},new int[]{0},START+WAIT*10)==-1,"Zero-wait changed original age-override policy");
        ChunkLoads.left(START+WAIT*10);
        field(LiveSettings.Number.class,"value").setFloat(field(ChunkLoads.class,"WAIT").get(null),150f);
        ChunkLoads.entered(false,true,1,4,4,START+WAIT*11);
        require(!ChunkLoads.taking(),"Disabled scheduler remained inside");
        require(((Map<?,?>)field(field(ChunkLoads.class,"arrivals").get(null).getClass(),"times").get(field(ChunkLoads.class,"arrivals").get(null))).isEmpty(),"Disable did not clear arrival ages");
        System.out.println("SCHEDULER115 PASS checks="+checks+"; deterministic original ChunkLoads/ChunkOrder/ChunkArrivals.");
    }

    static void woven(Path file)throws Exception {
        zombie.core.random.RandStandard.INSTANCE.init();
        byte[] bytes=Files.readAllBytes(file);
        require(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains("vpiso/ShadowPreload"),"Input queue is not actually woven with candidate hook");
        Map<String,byte[]> definitions=new HashMap<>();definitions.put("viewpoint.game.ChunkQueue",bytes);
        for(String type:List.of("ChunkLoads","ChunkOrder","ChunkArrivals")) {
            String name="viewpoint.game."+type;
            try(var input=PreloadQueue115Probe.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")) {
                if(input==null)throw new IllegalStateException(name);definitions.put(name,input.readAllBytes());
            }
        }
        ClassLoader loader=new BytesLoader(definitions);
        Class<?> queue=loader.loadClass("viewpoint.game.ChunkQueue");
        queueLoads=loader.loadClass("viewpoint.game.ChunkLoads");tune(queueLoads);
        Method enter=method(queue,"entered",boolean.class,boolean.class,long.class),take=method(queue,"next",long.class);
        method(ShaderPacks.class,"discover",java.io.File.class).invoke(null,(Object)null);
        ShaderPacks.useBuiltIn("default","high",Map.of("upscaling","off","reflections","off"));
        require(IsometricBridge.requestedBlockReason()==null,"CPU fixture graphics are blocked");
        Object player=allocate(IsoPlayer.class);field(player.getClass(),"x").setFloat(player,4);field(player.getClass(),"y").setFloat(player,4);
        Object streamer=allocate(WorldStreamer.class);
        field(WorldStreamer.class,"jobQueue").set(streamer,new ConcurrentLinkedQueue<>());
        field(WorldStreamer.class,"jobList").set(streamer,new Stack<>());
        set(WorldStreamer.class,"instance",streamer);
        IsoPlayer[] players=IsoPlayer.players;
        IsoPlayer[] originalPlayers=players.clone();
        List<IsoChunk> originalQueue=new ArrayList<>();IsoChunk.loadGridSquare.copyTo(originalQueue);
        LiveSettings.Toggle preload=LiveSettings.toggle("preload115.fixture","Fixture","Tests",true);
        set(IsometricControls.class,"shadowPreload",preload);
        set(Main.class,"compatible",true);
        set(Class.forName("viewpoint.platform.BuildPin"),"supported",Boolean.TRUE);
        set(IsometricBridge.class,"enabled",true);set(IsometricBridge.class,"failed",false);
        set(viewpoint.core.View.class,"enabled",false);
        set(zombie.core.PerformanceSettings.class,"fboRenderChunk",true);
        set(zombie.network.GameServer.class,"server",false);
        set(IsometricControls.class,"effects",IsometricEffects.DEFAULT);
        set(IsoPlayer.class,"instance",player);set(IsoCamera.class,"isoCameraGameCharacter",player);
        try {
            Arrays.fill(players,null);players[0]=(IsoPlayer)player;
            for(boolean models:new boolean[]{false,true}) {
                set(IsometricBridge.class,"modelsEnabled",models);
                require(ShadowPreload.loadingAllowed(),"Allowed native/model gate did not open");
                boundary(enter,false,true,"candidate native/model");
                // The real queue copies/removes actual IsoChunk.loadGridSquare entries.
                qclear();IsoChunk.loadGridSquare.clear();
                IsoChunk a=chunk(10,0),b=chunk(12,0);IsoChunk.loadGridSquare.add(a);IsoChunk.loadGridSquare.add(b);
                enter.invoke(null,false,false,START);
                require(take.invoke(null,START)==a,"Woven queue did not remove first real far chunk");
                require(take.invoke(null,START+BUDGET+100)==null,"Woven queue failed to defer second real far chunk");
                require(IsoChunk.loadGridSquare.size()==1 && IsoChunk.loadGridSquare.contains(b),"Deferred chunk was removed/lost");
                qleft(START+BUDGET+100);enter.invoke(null,false,false,START+FRAME);
                require(take.invoke(null,START+FRAME)==b && IsoChunk.loadGridSquare.isEmpty(),"Woven queue failed next-frame adoption");
                qleft(START+FRAME);

                qclear();IsoChunk.loadGridSquare.clear();a=chunk(10,0);b=chunk(20,0);
                IsoChunk.loadGridSquare.add(a);IsoChunk.loadGridSquare.add(b);enter.invoke(null,false,true,START);
                require(take.invoke(null,START)==a,"Woven MP initial take");
                require(take.invoke(null,START+BUDGET+100)==null,"Woven MP bypassed ordinary budget");
                require(take.invoke(null,START+WAIT+1)==b,"Woven MP lost maximum-wait escape");qleft(START+WAIT+1);
                qclear();IsoChunk.loadGridSquare.clear();a=chunk(10,0);IsoChunk.loadGridSquare.add(a);enter.invoke(null,false,false,START);
                require(take.invoke(null,START)==a,"Woven urgent setup burn");b=chunk(0,0);IsoChunk.loadGridSquare.add(b);
                require(take.invoke(null,START+BUDGET+100)==b,"Woven current-neighbor urgent was delayed");qleft(START+BUDGET+100);

                // Exercise actual active -> disabled entry without clearing the scheduler first.
                qclear();enter.invoke(null,false,false,START);require((Boolean)method(queueLoads,"taking").invoke(null),"Transition active entry");
                preload.set(false);enter.invoke(null,false,false,START+FRAME);
                require(!(Boolean)method(queueLoads,"taking").invoke(null),"Disable left stale inside=true");
                require(((Number)method(queueLoads,"credit").invoke(null)).doubleValue()==0,"Disable left adoption credit");preload.set(true);

                preload.set(false);boundary(enter,false,false,"preload off");boundary(enter,true,true,"original view preserved with preload off");preload.set(true);
                set(IsometricBridge.class,"enabled",false);boundary(enter,false,false,"master off");set(IsometricBridge.class,"enabled",true);
                set(IsometricBridge.class,"failed",true);boundary(enter,false,false,"failed addon");set(IsometricBridge.class,"failed",false);
                set(Main.class,"compatible",false);boundary(enter,false,false,"unsupported VP");boundary(enter,true,true,"unsupported addon original view preserved");set(Main.class,"compatible",true);
                set(Class.forName("viewpoint.platform.BuildPin"),"supported",Boolean.FALSE);boundary(enter,false,false,"unsupported game");set(Class.forName("viewpoint.platform.BuildPin"),"supported",Boolean.TRUE);
                set(viewpoint.core.View.class,"enabled",true);boundary(enter,false,false,"VP camera does not promote false argument");boundary(enter,true,true,"VP original true preserved");set(viewpoint.core.View.class,"enabled",false);
                set(IsometricControls.class,"effects",new IsometricEffects(false,.55f,1,64,2048,true,1,1,1,1));boundary(enter,false,false,"shadows off");
                set(IsometricControls.class,"effects",new IsometricEffects(true,0,1,64,2048,true,1,1,1,1));boundary(enter,false,false,"zero shadow strength");set(IsometricControls.class,"effects",IsometricEffects.DEFAULT);
                set(IsoCamera.class,"isoCameraGameCharacter",null);boundary(enter,false,false,"no camera");set(IsoCamera.class,"isoCameraGameCharacter",player);
                set(IsoPlayer.class,"instance",null);boundary(enter,false,false,"no player instance despite camera");set(IsoPlayer.class,"instance",player);
                players[0]=null;boundary(enter,false,false,"no player0 despite camera/instance");players[0]=(IsoPlayer)player;
                set(IsoCamera.class,"isoCameraGameCharacter",allocate(IsoPlayer.class));boundary(enter,false,false,"camera not player0");set(IsoCamera.class,"isoCameraGameCharacter",player);
                set(zombie.network.GameServer.class,"server",true);boundary(enter,false,false,"server isolated");set(zombie.network.GameServer.class,"server",false);
                players[1]=(IsoPlayer)allocate(IsoPlayer.class);boundary(enter,false,false,"split screen");players[1]=null;
                set(zombie.core.PerformanceSettings.class,"fboRenderChunk",false);boundary(enter,false,models,"native FBO requirement/model independence");set(zombie.core.PerformanceSettings.class,"fboRenderChunk",true);
            }
        } finally {
            qclear();IsoChunk.loadGridSquare.clear();for(IsoChunk chunk:originalQueue)IsoChunk.loadGridSquare.add(chunk);
            System.arraycopy(originalPlayers,0,players,0,players.length);
        }
        System.out.println("WOVENQUEUE115 PASS checks="+checks+"; actual provided Queue bytes, real chunk queue removal, both model modes, toggle/master/shadows/compatibility/build/camera/player/split/FBO/VP passthrough.");
    }
    static void boundary(Method enter,boolean original,boolean expected,String label)throws Exception {
        qclear();IsoChunk.loadGridSquare.clear();enter.invoke(null,original,false,START);
        require((Boolean)method(queueLoads,"taking").invoke(null)==expected,"Queue argument gate: "+label);
        qleft(START);
    }
    static void qclear()throws Exception {method(queueLoads,"clear").invoke(null);}
    static void qleft(long now)throws Exception {method(queueLoads,"left",long.class).invoke(null,now);}
    static void tune(Class<?> loads)throws Exception {
        for(String key:List.of("BUDGET","PER_WAITING","MOST","WAIT")) {
            LiveSettings.Number value=(LiveSettings.Number)field(loads,key).get(null);
            if(!tuning.containsKey(value))tuning.put(value,value.get());
            field(LiveSettings.Number.class,"value").setFloat(value,key.equals("WAIT")?150f:key.equals("PER_WAITING")?0f:2f);
        }
    }
    static IsoChunk chunk(int x,int y)throws Exception {IsoChunk chunk=(IsoChunk)allocate(IsoChunk.class);chunk.wx=x;chunk.wy=y;return chunk;}
    static void set(Class<?> type,String name,Object value)throws Exception {
        Field field=field(type,name);if(!saved.containsKey(field))saved.put(field,field.get(null));field.set(null,value);
    }
    static Method method(Class<?> type,String name,Class<?>...args) {
        try {Method method=type.getDeclaredMethod(name,args);method.setAccessible(true);return method;}
        catch(ReflectiveOperationException error){throw new IllegalStateException(type.getName()+"."+name,error);}
    }
    static Field field(Class<?> type,String name) {
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try {Field field=current.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        throw new IllegalStateException(type.getName()+"."+name);
    }
    static Object allocate(Class<?> type)throws Exception {Field field=field(Class.forName("sun.misc.Unsafe"),"theUnsafe");Object unsafe=field.get(null);return unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type);}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static final class BytesLoader extends ClassLoader {
        final Map<String,byte[]> definitions;
        BytesLoader(Map<String,byte[]> definitions){super(PreloadQueue115Probe.class.getClassLoader());this.definitions=definitions;}
        @Override protected synchronized Class<?> loadClass(String requested,boolean resolve)throws ClassNotFoundException {
            Class<?> type=findLoadedClass(requested);
            if(type==null){byte[] bytes=definitions.get(requested);type=bytes==null?super.loadClass(requested,false):defineClass(requested,bytes,0,bytes.length);}
            if(resolve)resolveClass(type);return type;
        }
    }
}
