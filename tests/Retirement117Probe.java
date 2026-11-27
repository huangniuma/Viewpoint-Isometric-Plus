package vpiso;
import java.lang.reflect.*;
import java.util.*;
import viewpoint.core.Frame;
import viewpoint.render.Retirement;

/** Actual VP retirement queues/fence methods, deterministic fake fences; no GL calls. */
public final class Retirement117Probe {
    static int checks;
    static final Class<?> R=Retirement.class,F=type("viewpoint.render.Retirement$Fences");
    static final Method LET=method(R,"letGo",Retirement.Freeable.class),DRAW=method(R,"fenceBefore",long.class),
        PENDING=method(R,"pending"),ORIGINAL=method(R,"freePassed");
    static final class Fixture implements InvocationHandler {
        final Object owner;final Set<Long> passed=new HashSet<>();final List<Integer> calls=new ArrayList<>();
        final List<Long> deletes=new ArrayList<>();long next=1;boolean placeFails;int finishes;
        Fixture()throws Exception {this(null);}
        Fixture(Object existing)throws Exception {
            Object fence=Proxy.newProxyInstance(F.getClassLoader(),new Class<?>[]{F},this);
            if(existing==null){var c=R.getDeclaredConstructor(F);c.setAccessible(true);owner=c.newInstance(fence);}
            else{owner=existing;field(R,"fences").set(owner,fence);}
        }
        public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
            case "place"->placeFails?0L:next++;case "passed"->passed.contains((long)a[0]);
            case "delete"->{deletes.add((long)a[0]);yield null;}case "finish"->{finishes++;yield null;}
            case "await"->null;case "toString"->"CPU fences";default->throw new AssertionError(m);};}
        void batch(int first,int count)throws Exception {for(int i=first;i<first+count;i++){final int n=i;LET.invoke(owner,(Retirement.Freeable)()->calls.add(n));}DRAW.invoke(owner,2L);}
        int pending()throws Exception{return (int)PENDING.invoke(owner);}
        void collect(Frame frame,long now,boolean force){RetirementBudget.collect(owner,frame,now,force);}
        void original()throws Exception{ORIGINAL.invoke(owner);}
    }
    static Frame frame(long n){Frame f=new Frame();f.number=n;return f;}
    static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[]args)throws Exception {
        // Exact frozen ABI is required, not permissive fallback.
        check(R.getDeclaredField("fenced").getType()==ArrayDeque.class,"queue ABI");
        check(method(type("viewpoint.render.Retirement$Fenced"),"things").getReturnType()==ArrayList.class,"batch ABI");
        Fixture a=new Fixture();a.batch(0,40);Frame f=frame(1);
        a.collect(f,1,false);check(a.calls.isEmpty()&&a.pending()==40,"unsignalled fence freed");
        a.passed.add(1L);a.collect(f,2,false);int once=a.calls.size();
        check(once>0&&once<=16,"bounded prefix");check(a.pending()==40-once,"pending exact after prefix");
        check(a.deletes.isEmpty(),"partial fence deleted early");
        a.collect(f,3,false);check(a.calls.size()==once,"same frame reset budget");
        a.collect(frame(f.number),3,false);check(a.calls.size()==once,"another pass frame object reset budget");
        f.number++;a.collect(f,4,false);check(a.calls.size()>once&&a.calls.size()<=once+16,"new frame progress");
        a.original();check(a.pending()==0&&a.calls.size()==40&&a.deletes.equals(List.of(1L)),"ordinary collector drains remainder");
        for(int i=0;i<40;i++)check(a.calls.get(i)==i,"original order/exactly once");

        Fixture b=new Fixture();b.batch(0,64);b.passed.add(1L);Frame bf=frame(10);
        b.collect(bf,1,false);check(b.pending()>0,"expected deferred work");
        b.collect(bf,125_000_002L,false);check(b.pending()==0&&b.calls.size()==64,"deadline drain");

        Fixture c=new Fixture();c.batch(0,256);c.passed.add(1L);c.collect(frame(20),0,false);
        check(c.pending()==0&&c.calls.size()==256,"queue watermark drain");
        Fixture d=new Fixture();d.batch(0,70);d.passed.add(1L);d.collect(frame(30),0,true);
        check(d.pending()==0&&d.calls.size()==70,"pressure drain");

        Fixture e=new Fixture();e.batch(0,30);e.passed.add(1L);Frame ef=frame(40);e.collect(ef,0,false);
        int before=e.calls.size();e.placeFails=true;e.batch(30,3);
        check(e.finishes==1&&e.pending()==0&&e.calls.size()==33,"failed fence fallback drains retained prefix");
        check(new HashSet<>(e.calls).size()==33&&before>0,"fallback double free");

        Fixture g=new Fixture();for(int i=0;i<65;i++)g.batch(i,1);
        check(g.finishes==1&&g.pending()==0&&g.calls.size()==65,"64 group fallback unchanged");

        Fixture h=new Fixture();LET.invoke(h.owner,(Retirement.Freeable)()->{throw new IllegalStateException("expected free failure");});
        LET.invoke(h.owner,(Retirement.Freeable)()->h.calls.add(2));DRAW.invoke(h.owner,2L);h.passed.add(1L);
        boolean thrown=false;try{h.collect(frame(50),0,true);}catch(IllegalStateException expected){thrown=true;}
        check(thrown&&h.pending()==1,"throwing attempt not removed");h.original();
        check(h.calls.equals(List.of(2))&&h.pending()==0,"throwing attempt retried or tail lost");

        Fixture j=new Fixture();j.batch(0,2);j.batch(2,2);j.passed.add(2L);j.collect(frame(60),0,true);
        check(j.calls.isEmpty()&&j.pending()==4,"passed later fence skipped earlier fence");
        j.passed.add(1L);j.collect(frame(60),1,true);check(j.calls.equals(List.of(0,1,2,3)),"multi batch order");
        Field clock=field(RetirementBudget.class,"clock");Object savedClock=clock.get(null);
        try {
            java.util.concurrent.atomic.AtomicLong time=new java.util.concurrent.atomic.AtomicLong();
            clock.set(null,(java.util.function.LongSupplier)()->time.getAndAdd(300_000L));
            Fixture timed=new Fixture();timed.batch(0,5);timed.passed.add(1L);Frame tf=frame(70);
            timed.collect(tf,0,false);check(timed.calls.size()==1&&timed.pending()==4,"fake 300us free did not exhaust time budget");
            timed.collect(tf,1,false);check(timed.calls.size()==1,"time budget reset on second collect");
            tf.number++;timed.collect(tf,2,false);check(timed.calls.size()==2,"next frame time budget did not restart");
            timed.original();check(timed.calls.size()==5&&timed.pending()==0,"timed original tail drain");
        } finally {clock.set(null,savedClock);}
        if(args.length>0&&args[0].equals("woven")) woven();
        System.out.println("RETIREMENT117 CPU PASS checks="+checks+"; actual original VP queue/pending/fenceBefore/freePassed; prefix budget shared by frame, deadline/watermark/pressure, unchanged failed/64-group fallback, original drain, throwing attempt, FIFO; no GL/game/attach or FPS claim.");
    }
    static void woven()throws Exception {
        Object owner=field(R,"ON_GPU").get(null),old=field(R,"fences").get(owner);
        Field supported=field(viewpoint.platform.BuildPin.class,"supported");Object oldSupported=supported.get(null);
        Main.compatible=true;
        try {
            Fixture s=new Fixture(owner);s.batch(0,40);s.passed.add(1L);Frame frame=frame(100);
            IsometricBridge.rendering(frame);Retirement.collect();int n=s.calls.size();
            check(n>0&&n<=16&&s.pending()==40-n,"actual woven collector did not bound singleton");
            Retirement.collect();check(s.calls.size()==n,"woven repeat reset frame");
            IsometricBridge.rendering(null);Retirement.collect();check(s.pending()==0&&s.calls.size()==40,"woven passthrough drain");
            check(field(RetirementBudget.class,"waitingBatch").get(null)==null,"passthrough retained freed Java batch");
            s.batch(40,20);s.passed.add(2L);IsometricBridge.rendering(frame);frame.number++;
            Retirement.collect();check(s.pending()>0,"woven second deferral");
            RetirementBudget.forceReady();check(s.pending()==0&&s.calls.size()==60,"woven allocation/context forced original collector");
            s.batch(60,20);s.passed.add(3L);frame.number++;Retirement.collect();
            Object arenaFree=GameAccess.field("viewpoint.render.MeshArena","free",null);
            if(((Map<?,?>)arenaFree).isEmpty())RetirementBudget.beforeMesh(100);
            check(s.pending()==0&&s.calls.size()==80,"mesh shortage drain");
            check(new HashSet<>(s.calls).size()==80,"woven duplicate frees");
            LET.invoke(owner,(Retirement.Freeable)()->{s.calls.add(80);Retirement.collect();});
            LET.invoke(owner,(Retirement.Freeable)()->s.calls.add(81));DRAW.invoke(owner,2L);s.passed.add(4L);
            RetirementBudget.collect(owner,frame,0,true);check(s.pending()==0&&s.calls.size()==82,"nested collect reentered current prefix");
            Object scope=new Object(){@SuppressWarnings("unused") final Frame frame=Retirement117Probe.frame(200);};
            Frame previous=RetirementBudget.enter(scope);check(previous==null,"scope initial state");
            Frame nested=RetirementBudget.enter(scope);check(nested!=null,"nested scope");RetirementBudget.leave(nested);RetirementBudget.leave(previous);
            ScopeTarget target=new ScopeTarget();target.render();check(target.calls==1&&scope()==null,"actual woven drawer scope restore");
            target.fail=true;boolean failed=false;try{target.render();}catch(IllegalStateException expected){failed=true;}
            check(failed&&target.calls==2&&scope()==null,"actual woven drawer exceptional scope restore");
            IsometricBridge.rendering(null);viewpoint.core.View.enabled=false;supported.set(null,Boolean.TRUE);
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            viewpoint.platform.ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off","reflections","off"));
            KeepTarget keep=new KeepTarget();keep.render();check(keep.calls==1&&keepScope()==null,"woven keep scope restore");
            keep.fail=true;failed=false;try{keep.render();}catch(IllegalStateException expected){failed=true;}
            check(failed&&keep.calls==2&&keepScope()==null,"woven keep exception restore");
            keep.fail=false;keep.expectScope=false;supported.set(null,Boolean.FALSE);keep.render();
            check(keepScope()==null,"unsupported game keep scope");supported.set(null,Boolean.TRUE);
            viewpoint.core.View.enabled=true;keep.render();check(keepScope()==null,"ordinary VP keep scope");viewpoint.core.View.enabled=false;keep.expectScope=true;
            // Same captured number: preloader and later addon draw must share the one allowance.
            s.batch(82,40);s.passed.add(5L);keep.fail=false;keep.number=500;keep.collect=true;keep.render();int afterKeep=s.calls.size();
            check(afterKeep>82&&afterKeep<=98&&s.pending()>0,"queued keep collect not bounded");
            IsometricBridge.rendering(Retirement117Probe.frame(500));Retirement.collect();
            check(s.calls.size()==afterKeep,"later addon pass reset keep budget");
            keep.number=501;keep.render();int nextKeep=s.calls.size();check(nextKeep>afterKeep&&nextKeep<=afterKeep+16,"next queued keep did not advance");
            IsometricBridge.rendering(null);RetirementBudget.forceReady();check(s.calls.size()==122&&s.pending()==0,"keep tail drain");
            s.batch(122,20);s.passed.add(6L);IsometricBridge.rendering(Retirement117Probe.frame(600));
            Field used=field(viewpoint.platform.Pool.class,"used");long oldUsed=used.getLong(viewpoint.platform.Pool.NEAR_MESHES);
            try {used.setLong(viewpoint.platform.Pool.NEAR_MESHES,viewpoint.platform.Pool.NEAR_MESHES.cap());Retirement.collect();check(s.pending()==0&&s.calls.size()==142,"actual Pool pressure did not drain");}
            finally {used.setLong(viewpoint.platform.Pool.NEAR_MESHES,oldUsed);}
            s.batch(142,20);s.passed.add(7L);IsometricBridge.rendering(Retirement117Probe.frame(601));Retirement.collect();check(s.pending()>0,"slot fixture deferral");
            Class<?> arena=Class.forName("viewpoint.render.MeshArena");Field rows=field(arena,"rows"),next=field(arena,"nextSlot");
            int oldRows=rows.getInt(null),oldNext=next.getInt(null);
            try {rows.setInt(null,1);next.setInt(null,204);RetirementBudget.beforeSlot();check(s.pending()==0&&s.calls.size()==162,"exact 204 slot boundary did not drain");}
            finally {rows.setInt(null,oldRows);next.setInt(null,oldNext);}
            s.batch(162,20);s.passed.add(8L);IsometricBridge.rendering(Retirement117Probe.frame(602));Retirement.collect();
            Queue<Object> read=(Queue<Object>)GameAccess.field("viewpoint.render.PackModels","READ",null);Object marker=new Object();
            try{read.add(marker);RetirementBudget.beforeModels();check(s.pending()==0&&s.calls.size()==182,"new model upload did not drain held releases");}
            finally{read.remove(marker);}
            s.batch(182,20);s.passed.add(9L);IsometricBridge.rendering(Retirement117Probe.frame(603));Retirement.collect();
            Class<?> rt=Class.forName("zombie.core.opengl.RenderThread");Field thread=field(rt,"contextThread"),depth=field(rt,"contextLockReentrantDepth");
            Object oldThread=thread.get(null);int oldDepth=depth.getInt(null);
            try{
                thread.set(null,null);depth.setInt(null,1);RetirementBudget.contextEnding();check(s.pending()>0,"foreign context attempted free");
                thread.set(null,Thread.currentThread());depth.setInt(null,2);RetirementBudget.contextEnding();check(s.pending()>0,"nested context release drained");
                depth.setInt(null,1);RetirementBudget.contextEnding();check(s.pending()==0&&s.calls.size()==202,"context teardown did not drain safe remainder");
            }finally{thread.set(null,oldThread);depth.setInt(null,oldDepth);}
            for(String name:List.of("vpiso.IsoDrawer","vpiso.NativeOverlayDrawer","zombie.core.opengl.RenderThread","viewpoint.render.PackModels"))
                check(Class.forName(name,false,Retirement117Probe.class.getClassLoader())!=null,"woven target linkage "+name);
            System.out.println("RETIREMENT117 WOVEN PASS: actual ON_GPU collector, scope/passthrough, original forceReady and allocation shortage drain.");
        } finally {IsometricBridge.rendering(null);field(R,"fences").set(owner,old);supported.set(null,oldSupported);Main.compatible=false;}
    }
    public static class ScopeTarget {
        private final Frame frame=Retirement117Probe.frame(300);int calls;boolean fail;
        public void render(){if(scope()!=frame)throw new AssertionError("woven scope not installed before body");calls++;if(fail)throw new IllegalStateException("scope fixture");}
    }
    public static class KeepTarget {
        private long number=400;int calls;boolean fail,collect,expectScope=true;
        public void render(){if(expectScope?!Long.valueOf(number).equals(keepScope()):keepScope()!=null)throw new AssertionError("woven keep scope gate");calls++;if(collect)Retirement.collect();if(fail)throw new IllegalStateException("keep fixture");}
    }
    static Object keepScope(){try{return ((ThreadLocal<?>)field(RetirementBudget.class,"KEEP_SCOPE").get(null)).get();}catch(Exception e){throw new IllegalStateException(e);}}
    static Object scope(){try{return ((ThreadLocal<?>)field(RetirementBudget.class,"SCOPE").get(null)).get();}catch(Exception e){throw new IllegalStateException(e);}}
    static Field field(Class<?>c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    static Class<?> type(String n){try{return Class.forName(n);}catch(Exception e){throw new IllegalStateException(e);}}
    static Method method(Class<?>c,String n,Class<?>...p){try{Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m;}catch(Exception e){throw new IllegalStateException(e);}}
}
