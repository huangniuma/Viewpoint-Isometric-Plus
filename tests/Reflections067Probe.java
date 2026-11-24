import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.pool.TypePool;
import viewpoint.core.Frame;
import viewpoint.render.FrameContext;
import vpiso.*;
import zombie.core.textures.ColorInfo;
import zombie.iso.*;
import zombie.iso.objects.IsoWindowFrame;
import zombie.iso.sprite.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Real optional Surface/consumer bodies with their first GPU preparation call intercepted. */
public final class Reflections067Probe {
    static final String CUT="zombie.iso.fboRenderChunk.FBORenderCutaways";
    static final String ART="vpiso.ReflectionVisibility";
    static int cases;
    static float enteredOpacity;
    static boolean allowFirstPreparation;
    static int preparationCalls;
    static Object mirrorResult;
    static Object nativeCutaways, building;
    static List<Object> collapsed;
    static IsoCell nativeCell;
    static IsoChunk nativeStorage;
    static final class BodyReached extends RuntimeException { BodyReached(String at){super(at);} }
    public static void programsSink(Object gpu,float opacity){enteredOpacity=opacity;if(allowFirstPreparation&&++preparationCalls==1)return;throw new BodyReached("original GPU programs boundary");}
    public static void scoreSink(viewpoint.render.SceneData scene,Object surface,float[] corners,float[] normal){throw new BodyReached("original score toScene boundary");}
    public static Object modelMirrorSink(Object sprite,int x,int y,int z){return mirrorResult;}
    public static final class Panel extends IsoWindowFrame {
        Panel(IsoGridSquare square,IsoSprite sprite){super(null,square,sprite,true);}
        @Override public IsoGridSquare getOppositeSquare(){return square.getAdjacentSquare(IsoDirections.N);}
    }
    static final class Loader extends ClassLoader {
        final Map<String,byte[]> bytes;
        Loader(Map<String,byte[]> bytes){super(Reflections067Probe.class.getClassLoader());this.bytes=bytes;}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            Class<?> type=findLoadedClass(name);
            if(type==null){byte[] data=bytes.get(name);type=data==null?super.loadClass(name,false):defineClass(name,data,0,data.length);}
            if(resolve)resolveClass(type);return type;
        }
    }
    static Object field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void put(Object object,String name,Object value)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static Method method(Class<?> type,String name,int count)throws Exception {
        for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==count){m.setAccessible(true);return m;}
        throw new NoSuchMethodException(type.getName()+"."+name+"/"+count);
    }
    static Object invoke(Method m,Object self,Object...args)throws Exception {
        try{return m.invoke(self,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}
    }
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);cases++;}
    static void near(float actual,float expected,String why){require(Math.abs(actual-expected)<.0001f,why+": "+actual+" != "+expected);}
    static Map<String,byte[]> readJar(Path path,String prefix)throws Exception {
        Map<String,byte[]> out=new HashMap<>();try(JarFile jar=new JarFile(path.toFile())){
            for(JarEntry e:jar.stream().filter(e->e.getName().startsWith(prefix)&&e.getName().endsWith(".class")).toList())
                out.put(e.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(e).readAllBytes());
        }return out;
    }
    static Map<String,byte[]> helperBytes()throws Exception {
        var c=(java.net.JarURLConnection)ClassLoader.getSystemResource("vpiso/ReflectionVisibility.class").openConnection();
        Map<String,byte[]> result=new HashMap<>();try(JarFile jar=c.getJarFile()){
            for(JarEntry e:jar.stream().filter(e->e.getName().startsWith("vpiso/ReflectionVisibility")&&e.getName().endsWith(".class")).toList())
                result.put(e.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(e).readAllBytes());
        }return result;
    }
    static byte[] intercept(byte[] input,String pkg){
        ClassWriter writer=new ClassWriter(0);
        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,desc,sig,ex)){
                    @Override public void visitMethodInsn(int op,String owner,String mn,String md,boolean itf){
                        if(owner.equals(pkg+"/Mirrors$Gpu")&&mn.equals("programs")&&(name.equals("composite")||name.equals("pane"))){
                            super.visitVarInsn(Opcodes.FLOAD,name.equals("composite")?5:4);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"Reflections067Probe","programsSink","(Ljava/lang/Object;F)V",false);
                        }else if(name.equals("score")&&mn.equals("toScene")&&owner.equals(pkg+"/Mirrors")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"Reflections067Probe","scoreSink","(Lviewpoint/render/SceneData;Ljava/lang/Object;[F[F)V",false);
                        }else if(name.equals("mirror")&&mn.equals("modelMirror")&&owner.equals(pkg+"/Scan")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"Reflections067Probe","modelMirrorSink","(Ljava/lang/Object;III)Ljava/lang/Object;",false);
                            super.visitTypeInsn(Opcodes.CHECKCAST,pkg+"/Surface");
                        }else super.visitMethodInsn(op,owner,mn,md,itf);
                    }
                    @Override public void visitMaxs(int stack,int locals){super.visitMaxs(stack+1,locals);}
                };
            }
        },0);return writer.toByteArray();
    }
    static ClassLoader consumer(Path jar,String pkg,boolean woven)throws Exception {
        Map<String,byte[]> target=readJar(jar,pkg.replace('.','/')+"/");
        for(String name:List.of(pkg+".Mirrors$Gpu",pkg+".Mirrors",pkg+".Scan"))target.put(name,intercept(target.get(name),pkg.replace('.','/')));
        if(woven){
            target.putAll(helperBytes());
            String stem="vpiso.Patch_Reflections"+(pkg.equals("prv2")?"V2":"");
            Map<String,byte[]> advice=new HashMap<>();
            Method convert=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
            for(String suffix:List.of("Composite","Pane","Score","Source")){
                String name=stem+suffix;try(var in=ClassLoader.getSystemResourceAsStream(name.replace('.','/')+".class")){
                    if(in==null)throw new AssertionError("Missing packaged patch "+name);advice.put(name,(byte[])convert.invoke(null,(Object)in.readAllBytes()));
                }
            }
            var locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(target),new ClassFileLocator.Simple(advice),ClassFileLocator.ForClassLoader.ofSystemLoader());
            Loader patchLoader=new Loader(advice);
            for(String suffix:List.of("Composite","Pane","Score","Source")){
                String name=suffix.equals("Source")?pkg+".Scan":suffix.equals("Score")?pkg+".Mirrors":pkg+".Mirrors$Gpu";
                String mn=suffix.equals("Source")?"mirror":suffix.toLowerCase(Locale.ROOT);
                target.put(name,new ByteBuddy().redefine(TypePool.Default.of(locator).describe(name).resolve(),locator)
                        .visit(Advice.to(patchLoader.loadClass(stem+suffix),locator).on(named(mn).and(takesArguments(5))))
                        .make().getBytes());
            }
        }
        return new Loader(target);
    }
    static Object surface(ClassLoader loader,String pkg,Object source,int level)throws Exception {
        Class<?> type=loader.loadClass(pkg+".Surface");Constructor<?> c=type.getDeclaredConstructor(int.class,boolean.class,float.class,int.class,float.class,float.class,int.class,float.class,float.class,Object.class);c.setAccessible(true);
        return c.newInstance(1,true,1f,1,0f,1f,level,0f,2.4494896f,source);
    }
    static FrameContext context(Frame frame){FrameContext c=new FrameContext();c.scene=frame.scene;return c;}
    static IsoGridSquare square(int x,int y,int level,boolean hide,float light)throws Exception {
        IsoGridSquare s=ModelCutawayProbe.square(level,hide);s.x=x;s.y=y;s.chunk.wx=Math.floorDiv(x,8);s.chunk.wy=Math.floorDiv(y,8);
        GameAccess.set(IsoGridSquare.class.getName(),"nav",s,new IsoGridSquare[IsoDirections.values().length]);
        GameAccess.set(IsoGridSquare.class.getName(),"targetPlayerCutawayFlags",s,new byte[4]);
        GameAccess.set(IsoGridSquare.class.getName(),"playerCutawayFlags",s,new byte[4]);
        GameAccess.set(IsoGridSquare.class.getName(),"playerCutawayFlagLockUntilTimes",s,new long[4]);
        GameAccess.set(IsoGridSquare.class.getName(),"lightInfo",s,new ColorInfo[]{new ColorInfo(1,1,1,1),new ColorInfo(1,1,1,1),new ColorInfo(1,1,1,1),new ColorInfo(1,1,1,1)});
        lighting(s,light);nativeStorage.squares[level][y*8+x]=s;return s;
    }
    static void lighting(IsoGridSquare square,float dark)throws Exception {
        Class<?> type=GameAccess.type("zombie.iso.IsoGridSquare$ILighting");
        Object light=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getReturnType()==boolean.class)return true;if(m.getReturnType()==float.class)return m.getName().equals("darkMulti")?dark:1f;
            if(m.getReturnType()==int.class)return 0;if(m.getReturnType()==ColorInfo.class)return new ColorInfo(1,1,1,1);return null;
        });Object array=Array.newInstance(type,4);for(int i=0;i<4;i++)Array.set(array,i,light);
        GameAccess.set(IsoGridSquare.class.getName(),"lighting",square,array);
    }
    @SuppressWarnings("unchecked") static void nativeSetup()throws Exception {
        nativeCutaways=GameAccess.staticCall(CUT,"getInstance");
        Object per=((Object[])GameAccess.field(CUT,"perPlayerData",nativeCutaways))[0];
        Object btc=GameAccess.field(CUT+"$PerPlayerData","buildingsToCollapse",per);
        collapsed=(List<Object>)GameAccess.field(CUT+"$BuildingsToCollapse","buildingsToCollapse",btc);
        building=ModelCutawayProbe.allocate(BuildingDef.class);collapsed.add(building);ModelCutawayProbe.building=building;
        // Real native cell -> chunk map -> loaded chunk -> square lookup, without constructors/world loading.
        nativeCell=(IsoCell)ModelCutawayProbe.allocate(IsoCell.class);
        nativeStorage=(IsoChunk)ModelCutawayProbe.allocate(IsoChunk.class);nativeStorage.loaded=true;nativeStorage.minLevel=0;nativeStorage.maxLevel=1;nativeStorage.squares=new IsoGridSquare[2][64];
        IsoChunkMap map=(IsoChunkMap)ModelCutawayProbe.allocate(IsoChunkMap.class);
        IsoChunk[] chunks=new IsoChunk[IsoChunkMap.chunkGridWidth*IsoChunkMap.chunkGridWidth];chunks[0]=nativeStorage;
        GameAccess.set(IsoChunkMap.class.getName(),"chunksSwapA",map,chunks);GameAccess.set(IsoChunkMap.class.getName(),"chunksSwapB",map,chunks);
        GameAccess.set(IsoCell.class.getName(),"chunkMap",nativeCell,new IsoChunkMap[]{map,map,map,map});
        GameAccess.set("zombie.characters.IsoPlayer","numPlayers",null,1);
        ModelCutawayProbe.camera(0);Main.compatible=true;
        Object player=ModelCutawayProbe.allocate(zombie.characters.IsoPlayer.class);GameAccess.set("zombie.characters.IsoPlayer","instance",null,player);
        Object camera=GameAccess.field("zombie.iso.IsoCamera","frameState",null);GameAccess.set("zombie.iso.IsoCamera$FrameState","playerIndex",camera,0);
        GameAccess.set("zombie.iso.IsoWater","instance",null,ModelCutawayProbe.allocate(IsoWater.class));GameAccess.set("zombie.iso.IsoWater","isShaderEnable",null,false);
    }
    static Object object(IsoGridSquare square,IsoSprite sprite){IsoObject o=new IsoObject(null,square,sprite);square.getObjects().add(o);return o;}
    static Object snapshot(ClassLoader loader,Frame frame)throws Exception {Class<?> h=loader.loadClass(ART);Object x=h.getConstructor().newInstance();invoke(method(h,"capture",3),x,frame,nativeCell,0);return x;}
    static float alpha(ClassLoader l,Object c,Object s)throws Exception{return (Float)invoke(method(l.loadClass(ART),"alpha",2),null,c,s);}
    static Object begin(Object snapshot)throws Exception{return invoke(method(snapshot.getClass(),"begin",0),snapshot);}
    static void restore(ClassLoader l,Object previous)throws Exception{invoke(method(l.loadClass(ART),"restore",1),null,previous);}
    static void remember(ClassLoader l,Object s,Object source)throws Exception{invoke(method(l.loadClass(ART),"remember",2),null,s,source);}
    static void current(ClassLoader l,String pkg,Object...surfaces)throws Exception {
        Class<?> scan=l.loadClass(pkg+".Scan"),type=l.loadClass(pkg+".Surface");Object array=Array.newInstance(type,surfaces.length);for(int i=0;i<surfaces.length;i++)Array.set(array,i,surfaces[i]);
        Field f=scan.getDeclaredField("current");f.setAccessible(true);f.set(null,array);
    }
    static void reaches(Method m,Object gpu,Object[] args,boolean expected,float opacity,String label)throws Exception {
        boolean reached=false;enteredOpacity=-99;
        try{Object result=invoke(m,gpu,args);if(m.getReturnType()==float.class)near((Float)result,0,label+" skipped score result");}
        catch(BodyReached e){reached=true;}
        require(reached==expected,label+" body boundary reached="+reached+" expected="+expected);
        if(expected&&m.getReturnType()==void.class)near(enteredOpacity,opacity,label+" argument opacity");
    }
    static void negative(Path jar,String pkg)throws Exception {
        ClassLoader l=consumer(jar,pkg,false);Object s=surface(l,pkg,new Object(),1),gpu=ModelCutawayProbe.allocate(l.loadClass(pkg+".Mirrors$Gpu"));FrameContext c=context(new Frame());
        reaches(method(gpu.getClass(),"composite",5),gpu,new Object[]{c,0,s,false,.8f},true,.8f,"066 "+pkg+" composite");
        reaches(method(gpu.getClass(),"pane",5),gpu,new Object[]{c,0,s,.8f,false},true,.8f,"066 "+pkg+" pane");
        reaches(method(l.loadClass(pkg+".Mirrors"),"score",5),null,new Object[]{c,s,64f,new float[12],new float[3]},true,0,"066 "+pkg+" score");
        System.out.println("066 negative control: original "+pkg+" consumer enters GPU preparation/scoring for upper pane; first preparation call intercepted, no GL invoked.");
    }
    static void regression(Path jar,String pkg)throws Exception {
        ClassLoader l=consumer(jar,pkg,true);Class<?> h=l.loadClass(ART);Object gpu=ModelCutawayProbe.allocate(l.loadClass(pkg+".Mirrors$Gpu"));
        IsoSprite shared=new IsoSprite();IsoGridSquare hidden=square(1,1,1,true,1),visible=square(2,1,1,false,1);
        require(nativeCell.getGridSquare(1,1,1)==hidden&&nativeCell.getGridSquare(2,1,1)==visible,"real native cell lookup identifies fixture source squares");
        Object a=object(hidden,shared),b=object(visible,shared);Object s=surface(l,pkg,a,1),t=surface(l,pkg,b,1);
        current(l,pkg,s,t);Frame first=new Frame();first.camZ=0;FrameContext c=context(first);Object one=snapshot(l,first),previous=begin(one);
        try{
            near(alpha(l,c,s),0,"hidden upper source");near(alpha(l,c,t),1,"independent same-sprite visible source");
            near(alpha(l,context(new Frame()),s),1,"non-owned reflected context");
            Method composite=method(gpu.getClass(),"composite",5),pane=method(gpu.getClass(),"pane",5),score=method(l.loadClass(pkg+".Mirrors"),"score",5);
            reaches(composite,gpu,new Object[]{c,0,s,false,.8f},false,0,"067 hidden composite");
            reaches(composite,gpu,new Object[]{c,0,s,true,.8f},false,0,"067 hidden composite panes forwarding");
            reaches(pane,gpu,new Object[]{c,0,s,.8f,false},false,0,"067 hidden pane");
            reaches(method(gpu.getClass(),"pane",4),gpu,new Object[]{c,0,s,.8f},false,0,"067 hidden pane wrapper");
            reaches(score,null,new Object[]{c,s,64f,new float[12],new float[3]},false,0,"067 hidden score");
            reaches(composite,gpu,new Object[]{c,0,t,false,.8f},true,.8f,"067 visible composite");
            reaches(pane,gpu,new Object[]{c,0,t,.8f,false},true,.8f,"067 visible pane");
            reaches(score,null,new Object[]{c,t,64f,new float[12],new float[3]},true,0,"067 visible score");
            // The original scan's real early-return branch returns a sprite-keyed model mirror;
            // only its pack geometry lookup is replaced. OnExit must retain individual provenance.
            Object mirror=surface(l,pkg,shared,1);mirrorResult=mirror;
            Method scan=method(l.loadClass(pkg+".Scan"),"mirror",5);Object produced=invoke(scan,null,a,shared,1,1,1);
            require(produced==mirror,"real Scan.mirror return identity");
            Object otherMirror=surface(l,pkg,shared,1);mirrorResult=otherMirror;
            require(invoke(scan,null,b,shared,2,1,1)==otherMirror,"real Scan.mirror second same-sprite identity");
            Object recaptured=snapshot(l,first);Object old=begin(recaptured);near(alpha(l,c,mirror),0,"sprite-keyed mirror remembers individual native object");near(alpha(l,c,otherMirror),1,"second mirror with identical sprite stays independent");restore(l,old);
            // Delete it from the periodically refreshed scan; cached/lingering surface must stay tied to its source.
            current(l,pkg,t);Object linger=snapshot(l,first);old=begin(linger);near(alpha(l,c,s),0,"stale surface omitted from Scan.current");restore(l,old);
            // Native state changes only affect a later captured Frame, never a queued previous one.
            ModelCutawayProbe.camera(1);Frame upstairs=new Frame();upstairs.camZ=1;Object two=snapshot(l,upstairs);Object oldTwo=begin(two);
            near(alpha(l,context(upstairs),s),1,"upstairs native recovery");near(alpha(l,c,s),1,"other queued scene does not inherit second frame");restore(l,oldTwo);
            near(alpha(l,c,s),0,"first captured frame remains hidden after stairs");
            // IsoObject removal can retain getSquare(): require identity membership in that square.
            visible.getObjects().remove(b);require(((IsoObject)b).getSquare()==visible,"removed-source fixture retains its previous square");
            ModelCutawayProbe.camera(0);Object removed=snapshot(l,first);Object oldRemoved=begin(removed);near(alpha(l,c,t),0,"removed object cannot remain in lingering reflection");restore(l,oldRemoved);
            near(alpha(l,c,t),1,"queued snapshot remains immutable after source removal");visible.getObjects().add((IsoObject)b);
            // An old world/chunk can leave its object list intact at coordinates now owned by another square.
            IsoGridSquare replacement=square(visible.x,visible.y,visible.z,false,1);
            require(replacement!=visible&&visible.getObjects().contains(b),"old-world fixture has same coordinates and retained old objects");
            require(nativeCell.getGridSquare(visible.x,visible.y,visible.z)==replacement,"real native cell now returns replacement square");
            Object replaced=snapshot(l,first);Object oldReplaced=begin(replaced);near(alpha(l,c,t),0,"old-world source rejected against active cell square identity");restore(l,oldReplaced);
            nativeStorage.squares[visible.z][visible.y*8+visible.x]=visible;
            Object reloaded=snapshot(l,first);Object oldReloaded=begin(reloaded);near(alpha(l,c,t),1,"active source recovers after cell ownership restored");restore(l,oldReloaded);
            Object foreign=surface(l,pkg,new Object(),1);near(alpha(l,c,foreign),1,"non-native object key unaffected");
            Throwable[] threadFailure={null};Thread thread=new Thread(()->{try{near(alpha(l,c,s),1,"thread-local active snapshot isolation");}catch(Throwable e){threadFailure[0]=e;}});thread.start();thread.join();if(threadFailure[0]!=null)throw new AssertionError(threadFailure[0]);
        }finally{restore(l,previous);mirrorResult=null;ModelCutawayProbe.camera(0);}
        near(alpha(l,c,s),1,"ordinary VP after restore");
        // Capture the native same-floor translucent result and test independent GPU opacity multiplication.
        IsoGridSquare source=square(3,3,0,false,1),opposite=square(3,2,0,false,.2f);source.setAdjacentSquare(IsoDirections.N,opposite);
        Panel panel=new Panel(source,new IsoSprite());source.getObjects().add(panel);panel.setAlphaAndTarget(0,0);
        Object paneSurface=surface(l,pkg,panel,0);current(l,pkg,paneSurface);
        Frame fadeFrame=new Frame();fadeFrame.camZ=0;FrameContext fadeContext=context(fadeFrame);
        Object fadeSnapshot=snapshot(l,fadeFrame);Object old=begin(fadeSnapshot);
        try{
            float nativeAlpha=panel.getRenderInfo(0).targetAlpha;require(nativeAlpha>0&&nativeAlpha<1,"native same-floor fixture has partial alpha");near(alpha(l,fadeContext,paneSurface),nativeAlpha,"captured native same-floor fade");
            reaches(method(gpu.getClass(),"composite",5),gpu,new Object[]{fadeContext,0,paneSurface,false,.8f},true,.8f*nativeAlpha,"067 same-floor composite fade");
            reaches(method(gpu.getClass(),"pane",5),gpu,new Object[]{fadeContext,0,paneSurface,.8f,false},true,.8f*nativeAlpha,"067 same-floor pane fade");
            // Retain the original composite(panes=true) -> pane/4 -> pane/5 calls.
            // Only let composite's first preparation boundary return; stop inside pane.
            allowFirstPreparation=true;preparationCalls=0;
            try{reaches(method(gpu.getClass(),"composite",5),gpu,new Object[]{fadeContext,0,paneSurface,true,.8f},true,.8f*nativeAlpha,"067 composite-to-pane applies alpha once");require(preparationCalls==2,"original composite-to-pane forwarding path used");}
            finally{allowFirstPreparation=false;preparationCalls=0;}
            System.out.println("067 original composite(panes=true) -> pane/4 -> pane/5: alpha="+nativeAlpha+", final opacity="+enteredOpacity+"; one native-alpha multiplication PASS.");
        }finally{restore(l,old);}
        System.out.println("067 "+pkg+" PASS: real Surface constructors, woven composite/pane/score and Scan.mirror, native upper masks, same-sprite identity, stairs, queued-frame/thread/VP isolation, linger source and same-floor fade. CPU only; GPU preparation intercepted.");
    }
    static void absent()throws Exception {
        require(ClassLoader.getSystemResource("projectreflections/Surface.class")==null&&ClassLoader.getSystemResource("prv2/Surface.class")==null,"optional jars absent from runtime");
        Class<?> h=Class.forName(ART);Object x=h.getConstructor().newInstance();Frame f=new Frame();invoke(method(h,"capture",3),x,f,new Object(),0);Object old=invoke(method(h,"begin",0),x);
        near((Float)invoke(method(h,"alpha",2),null,context(f),null),1,"no optional jars alpha default");invoke(method(h,"restore",1),null,old);
        for(String stem:List.of("vpiso.Patch_Reflections","vpiso.Patch_ReflectionsV2"))for(String suffix:List.of("Composite","Pane","Score","Source"))Class.forName(stem+suffix).getDeclaredMethods();
        System.out.println("067 optional-absent linkage PASS: helper capture/begin/restore and eight patch classes load without either reflections jar.");
    }
    public static void main(String[] args)throws Exception {
        if(args.length==0||args[0].equals("absent")){absent();return;}
        Path jar=Path.of(args[1]);String pkg=args[2];
        if(args[0].equals("baseline066")){negative(jar,pkg);return;}
        nativeSetup();try{regression(jar,pkg);}finally{collapsed.remove(building);}
        System.out.println("Reflection visibility assertions: "+cases);
    }
}
