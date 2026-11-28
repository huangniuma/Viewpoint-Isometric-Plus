import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

/** CPU integration: real woven ModelPass predicate + actual production routing helpers. No GL. */
public final class FadeRouting122Probe {
    private static final int COLOUR=1, SHADOW=2, GLASS=1024;
    private static int checks, cases, negativeControls;
    private static final Object UNSAFE;
    private static final Method ALLOCATE;
    static {
        try {
            Class<?> type=Class.forName("sun.misc.Unsafe");
            Field f=type.getDeclaredField("theUnsafe");f.setAccessible(true);UNSAFE=f.get(null);
            ALLOCATE=type.getMethod("allocateInstance",Class.class);
        } catch(Exception failure) {throw new ExceptionInInitializerError(failure);}
    }
    private record Interval(String label,float lo,float hi,float coverage) {}
    private static final class RoutingStop extends RuntimeException {}
    /** Observe real glass() after Advice entry at its first frustum update, before any GL call. */
    public static final class FrustumTrap extends FrustumIntersection {
        private Env env;private boolean armed,called,expected;private int expectedFlags;
        FrustumTrap(){super();super.set(new Matrix4f());}
        @Override public FrustumIntersection set(Matrix4fc matrix) {
            if(!armed)return super.set(matrix);
            called=true;
            try {
                require(env.flags[0]==expectedFlags,"real Advice entry flags at pre-GL frustum boundary");
                require(env.predicate()==expected,"real original lambda under actual Advice scope");
            } catch(Exception failure){throw new IllegalStateException(failure);}
            throw new RoutingStop();
        }
    }
    private static final class Env {
        final ClassLoader loader;
        final Class<?> stateType,drawType,bridgeType;
        final Object state,draws,pass,frame,effects,context,scene;
        final FrustumTrap trap;
        final ThreadLocal<Object> render;
        final Map<Integer,Object> models;
        final Map<Integer,Map<Integer,Object>> meshSources;
        final Constructor<?> sample;
        final Method predicate,fading,meshBegin,meshEnd,modelRendering,rendering,modelScope,begin,end,glass;
        final int[] flags,materials;
        final float[] values;
        Env(byte[] modelPassBytes)throws Exception {
            Class<?> loaderType=Class.forName("PatchWeavingProbe$BytesLoader");
            Constructor<?> ctor=loaderType.getDeclaredConstructor(Map.class,boolean.class);ctor.setAccessible(true);
            loader=(ClassLoader)ctor.newInstance(Map.of("viewpoint.render.ModelPass",modelPassBytes),true);
            Class<?> passType=Class.forName("viewpoint.render.ModelPass",true,loader);
            stateType=Class.forName("vpiso.NativeObjectState",true,loader);
            drawType=Class.forName("viewpoint.render.ModelDraws",true,loader);
            bridgeType=Class.forName("vpiso.IsometricBridge",true,loader);
            state=stateType.getConstructor().newInstance();draws=drawType.getConstructor().newInstance();
            // ModelPass construction creates shader programs. Allocate only CPU fields used by shown().
            pass=ALLOCATE.invoke(UNSAFE,passType);
            trap=new FrustumTrap();field(passType,"frustum").set(pass,trap);trap.env=this;
            field(passType,"draws").set(pass,draws);
            field(drawType,"count").setInt(draws,1);
            flags=(int[])field(drawType,"flags").get(draws);
            materials=(int[])field(drawType,"material").get(draws);
            values=(float[])field(drawType,"values").get(draws);
            Class<?> frameType=Class.forName("viewpoint.core.Frame",true,loader);
            Class<?> effectsType=Class.forName("vpiso.IsometricEffects",true,loader);
            frame=frameType.getConstructor().newInstance();effects=field(effectsType,"DEFAULT").get(null);
            scene=field(frameType,"scene").get(frame);field(stateType,"frame").set(state,frame);
            rendering=bridgeType.getMethod("rendering",frameType);
            modelScope=bridgeType.getMethod("modelRendering",frameType,effectsType);
            modelRendering=bridgeType.getMethod("modelRendering");
            fading=stateType.getMethod("fadingModel",Object.class,drawType,int.class);
            meshBegin=stateType.getMethod("beginMeshFade",scene.getClass(),byte.class);meshEnd=stateType.getMethod("endMeshFade",Object.class);
            predicate=passType.getDeclaredMethod("lambda$glass$0",drawType,int.class);predicate.setAccessible(true);
            begin=stateType.getMethod("beginModelFade",Object.class);end=stateType.getMethod("endModelFade",Object.class);
            Class<?> contextType=Class.forName("viewpoint.render.FrameContext",true,loader);
            context=contextType.getConstructor().newInstance();
            glass=passType.getDeclaredMethod("glass",contextType,Class.forName("viewpoint.render.ShadowPass",false,loader),Class.forName("viewpoint.render.GlassWaterPass",false,loader));glass.setAccessible(true);
            sample=Class.forName("vpiso.NativeObjectState$Sample",true,loader).getDeclaredConstructor(float.class,int.class);sample.setAccessible(true);
            render=(ThreadLocal<Object>)field(stateType,"RENDER").get(null);
            models=(Map<Integer,Object>)field(stateType,"models").get(state);
            meshSources=(Map<Integer,Map<Integer,Object>>)field(stateType,"meshes").get(state);
            // Real FarOcclusion.hides returns false when there is no prepared far pass. No fake predicate.
            Class<?> world=Class.forName("viewpoint.render.WorldRenderer",true,loader);
            field(world,"far").set(null,null);
            Method hides=Class.forName("viewpoint.render.FarOcclusion",true,loader).getDeclaredMethod("hides",drawType,int.class);hides.setAccessible(true);
            require(!(Boolean)hides.invoke(null,draws,0),"CPU no-far fixture must not hide");
            require(passType.getClassLoader()==loader && stateType.getClassLoader()==loader && drawType.getClassLoader()==loader,"runtime package isolation");
            scope("owned");
        }
        void scope(String name)throws Exception {
            rendering.invoke(null,new Object[]{null});render.remove();field(stateType,"ready").setBoolean(state,true);
            switch(name) {
                case "owned" -> {render.set(state);modelScope.invoke(null,frame,effects);}
                case "not-ready" -> {render.set(state);field(stateType,"ready").setBoolean(state,false);modelScope.invoke(null,frame,effects);}
                case "normal-vp" -> {render.set(state);rendering.invoke(null,new Object[]{null});}
                case "native-mode" -> {render.set(state);rendering.invoke(null,frame);}
                case "no-source-scope" -> modelScope.invoke(null,frame,effects);
                default -> throw new IllegalArgumentException(name);
            }
            require((Boolean)modelRendering.invoke(null)==(name.equals("owned")||name.equals("not-ready")||name.equals("no-source-scope")),"real model camera scope "+name);
        }
        void row(Float alpha,Interval interval,int f,int material,boolean inFrustum)throws Exception {
            models.clear();if(alpha!=null)models.put(0,sample.newInstance(alpha,7));
            flags[0]=f;materials[0]=material;
            Arrays.fill(values,0,68,0f);values[24]=inFrustum?0:1000;values[27]=.1f;
            values[56]=interval.lo;values[57]=interval.hi;
            values[58]=17;values[59]=23; // Look/wall metadata sentinels.
        }
        boolean helper()throws Exception {return (Boolean)fading.invoke(null,pass,draws,0);}
        boolean predicate()throws Exception {return (Boolean)predicate.invoke(pass,draws,0);}
        void actualGlass(boolean expected,int expectedFlags)throws Exception {
            trap.armed=true;trap.called=false;trap.expected=expected;trap.expectedFlags=expectedFlags;
            try {glass.invoke(pass,context,null,null);throw new AssertionError("pre-GL trap was not reached");}
            catch(InvocationTargetException failure){if(!(failure.getCause() instanceof RoutingStop))throw failure;}
            finally {trap.armed=false;}
            require(trap.called,"real glass body reached before GL");
        }
        void close()throws Exception {render.remove();rendering.invoke(null,new Object[]{null});}
    }
    public static void main(String[] args)throws Exception {
        if(args.length==3 && args[0].equals("--weave")){weave(Path.of(args[1]),Path.of(args[2]));return;}
        if(args.length==3 && args[0].equals("--parity")){parity(Path.of(args[1]),Path.of(args[2]));return;}
        require(args.length==2,"Usage: FadeRouting122Probe <woven ModelPass.class> <woven Meshes.class>");
        byte[] woven=Files.readAllBytes(Path.of(args[0])),original=resource("viewpoint/render/ModelPass.class");
        require(!Arrays.equals(woven,original),"negative control needs genuinely woven bytes");
        String encoded=new String(woven,java.nio.charset.StandardCharsets.ISO_8859_1);
        require(encoded.contains("beginModelFade")&&encoded.contains("endModelFade"),"real glass method is missing production scope Advice");
        String meshEncoded=new String(Files.readAllBytes(Path.of(args[1])),java.nio.charset.StandardCharsets.ISO_8859_1);
        require(meshEncoded.contains("beginMeshFade")&&meshEncoded.contains("endMeshFade"),"real Meshes.drawPlanned missing production scope Advice");
        Env candidate=new Env(woven),baseline=new Env(original);
        try {
            Interval[] intervals={new Interval("full-hi2",0,2,1),new Interval("full-hi1",0,1,1),new Interval("incoming-half",0,.5f,.5f),new Interval("outgoing-quarter",.25f,2,.75f),new Interval("outgoing-half",.5f,2,.5f),new Interval("outgoing-end",1,2,0),new Interval("empty",.5f,.5f,0)};
            for(float alpha:new float[]{0,.25f,.5f,1})for(Interval interval:intervals) {
                boolean partial=alpha*interval.coverage>0 && alpha*interval.coverage<1;
                inspect(candidate,baseline,alpha,interval,COLOUR|SHADOW,-1,true,"owned",partial,false);
            }
            Interval whole=intervals[0],half=intervals[2];
            for(String mode:List.of("not-ready","normal-vp","native-mode","no-source-scope"))
                inspect(candidate,baseline,.25f,whole,COLOUR|SHADOW,-1,true,mode,false,false);
            inspect(candidate,baseline,.5f,whole,SHADOW,-1,true,"owned",false,false);
            inspect(candidate,baseline,.5f,whole,COLOUR|SHADOW,0,true,"owned",false,false);
            inspect(candidate,baseline,.5f,whole,COLOUR|SHADOW,4,true,"owned",false,false);
            inspect(candidate,baseline,.5f,whole,COLOUR|SHADOW,-1,false,"owned",false,false);
            inspect(candidate,baseline,null,whole,COLOUR|SHADOW,-1,true,"owned",false,false);
            inspect(candidate,baseline,null,half,COLOUR|SHADOW,-1,true,"owned",true,false);
            for(float alpha:new float[]{0,.25f,.5f,1}) {
                inspect(candidate,baseline,alpha,whole,COLOUR|SHADOW|GLASS,-1,true,"owned",alpha>0&&alpha<1,true);
                inspect(candidate,baseline,alpha,whole,SHADOW|GLASS,-1,true,"owned",false,false);
                inspect(candidate,baseline,alpha,whole,COLOUR|SHADOW|GLASS,-1,true,"normal-vp",false,true);
            }
            candidate.scope("owned");candidate.row(.25f,whole,COLOUR|SHADOW,-1,true);
            require(!(Boolean)candidate.fading.invoke(null,candidate.pass,candidate.draws,-1),"negative index guard");
            require(!(Boolean)candidate.fading.invoke(null,candidate.pass,candidate.draws,1),"count index guard");
            meshCases(candidate,intervals);
            require(negativeControls>0,"woven test did not distinguish shipped predicate");
            System.out.println("FADE122 ROUTING CPU PASS cases="+cases+" checks="+checks+" negativeControls="+negativeControls+"; staged helper/Advice byte parity; real glass Advice entry/throw/exit + original lambda/VP control; source and interval fades, visibility/material guards, bounded far-partial mesh scope, full far/near/shadow/mode isolation, nested/exception restore, model metadata unchanged; pre-GL trap, no GL/game attach");
            System.out.println("wovenModelPassSHA256="+hex(MessageDigest.getInstance("SHA-256").digest(woven)));
        } finally {candidate.close();baseline.close();}
    }
    private static void inspect(Env c,Env old,Float alpha,Interval interval,int flags,int material,boolean inFrustum,String mode,boolean helperExpected,boolean originalExpected)throws Exception {
        c.scope(mode);old.scope(mode);c.row(alpha,interval,flags,material,inFrustum);old.row(alpha,interval,flags,material,inFrustum);
        int[] beforeFlags=c.flags.clone(),beforeMaterials=c.materials.clone();float[] beforeValues=c.values.clone();
        String label="alpha="+alpha+" interval="+interval.label+" flags="+flags+" material="+material+" inFrustum="+inFrustum+" mode="+mode;
        require(c.helper()==helperExpected,"production helper "+label);
        boolean before=old.predicate();
        require(before==originalExpected,"original VP control "+label);
        Object scope=c.begin.invoke(null,c.pass);
        boolean after;
        try {
            after=c.predicate();
            require(after==(originalExpected||helperExpected),"actual original predicate under production scope "+label);
            require((c.flags[0]&SHADOW)==(flags&SHADOW),"physical SHADOW flag changed in scope "+label);
            require(c.flags[0]==(helperExpected?flags|GLASS:flags),"only temporary GLASS bit may change "+label);
            Object nested=c.begin.invoke(null,c.pass);c.end.invoke(null,nested);
            require(c.predicate()==after,"nested scope prematurely restored outer flags "+label);
        } finally {c.end.invoke(null,scope);}
        if(after!=before)negativeControls++;
        c.actualGlass(originalExpected||helperExpected,helperExpected?flags|GLASS:flags);
        old.actualGlass(originalExpected,flags);
        require(Arrays.equals(beforeFlags,c.flags),"COLOUR/SHADOW/GLASS flags mutated "+label);
        require(Arrays.equals(beforeMaterials,c.materials),"material mutated "+label);
        require(Arrays.equals(beforeValues,c.values),"look/wall/fade/geometry record mutated "+label);
        cases++;
    }
    private static Field field(Class<?> type,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void meshCases(Env e,Interval[] intervals)throws Exception {
        Class<?> type=e.scene.getClass();byte[] flags=(byte[])field(type,"meshFlags").get(e.scene);float[] fade=(float[])field(type,"meshFade").get(e.scene);
        field(type,"meshCount").setInt(e.scene,6);
        for(String mode:List.of("owned","not-ready","normal-vp","native-mode","no-source-scope"))
        for(byte selection:new byte[]{1,2,4})for(float alpha:new float[]{0,.25f,.5f,1})for(Interval interval:intervals) {
            e.scope(mode);e.meshSources.clear();
            byte[] original={1,3,5,7,2,1};System.arraycopy(original,0,flags,0,6);
            for(int i=0;i<6;i++){fade[i*2]=0;fade[i*2+1]=2;}
            // Index 0 and 1 are far sources, index 2/3 already NEAR, index4 shadow-only, index5 opaque far control.
            for(int i=0;i<5;i++){fade[i*2]=interval.lo;fade[i*2+1]=interval.hi;e.meshSources.put(i,Map.of(9,e.sample.newInstance(alpha,7)));}
            float[] beforeFade=fade.clone();
            boolean eligible=mode.equals("owned")&&selection==4&&((interval.coverage>0&&interval.coverage<1)||(alpha>0&&alpha<1));
            Object scope=e.meshBegin.invoke(null,e.scene,selection);
            boolean threw=false;
            try {
              try {
                for(int i=0;i<6;i++)require(flags[i]==(eligible&&i<2?(byte)(original[i]|4):original[i]),"bounded mesh scope index="+i+" mode="+mode+" selection="+selection+" alpha="+alpha+" interval="+interval.label);
                require((flags[0]&2)==0 && (flags[1]&2)==2,"mesh scope changes no shadow eligibility");
                Object nested=e.meshBegin.invoke(null,e.scene,selection);e.meshEnd.invoke(null,nested);
                require(flags[0]==(eligible?5:1)&&flags[1]==(eligible?7:3),"nested mesh scope retains outer flags");
                throw new RoutingStop();
              } finally {e.meshEnd.invoke(null,scope);}
            } catch(RoutingStop expected) {threw=true;}
            require(threw,"mesh exception propagates through cleanup boundary");
            require(Arrays.equals(Arrays.copyOf(flags,6),original),"mesh scope restores on exception boundary");
            require(Arrays.equals(fade,beforeFade),"mesh scope mutates no fade records");cases++;
        }
        e.scope("owned");Object alien=type.getConstructor().newInstance();
        require(e.meshBegin.invoke(null,alien,(byte)4)==null,"foreign scene guard");
        field(type,"meshCount").setInt(e.scene,0);require(e.meshBegin.invoke(null,e.scene,(byte)4)==null,"empty mesh scene guard");
    }
    private static void parity(Path compiled,Path artifact)throws Exception {
        try(var zip=new java.util.zip.ZipFile(artifact.toFile());var files=Files.walk(compiled)) {
            int compared=0;
            for(Path p:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".class")).toList()) {
                String name=compiled.relativize(p).toString().replace('\\','/');var entry=zip.getEntry(name);
                require(entry!=null,"compiled helper missing from artifact: "+name);
                try(InputStream in=zip.getInputStream(entry)){require(Arrays.equals(Files.readAllBytes(p),in.readAllBytes()),"compiled helper differs from staged artifact: "+name);}
                compared++;
            }
            require(compared>=5,"helper/nested/patch parity coverage");System.out.println("FADE122 STAGED BYTE PARITY PASS classes="+compared+" artifact="+artifact);
        }
    }
    private static void weave(Path patch,Path output)throws Exception {
        boolean mesh=patch.getFileName().toString().contains("Mesh");
        String name=mesh?"vpiso.Patch_SmoothMeshFade":"vpiso.Patch_SmoothModelFade";
        String targetName=mesh?"viewpoint.render.Meshes":"viewpoint.render.ModelPass",methodName=mesh?"drawPlanned":"glass",scopeName=mesh?"Mesh":"Model";
        Class<?> verifier=Class.forName("PatchWeavingProbe");
        Method convert=verifier.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
        byte[] converted=(byte[])convert.invoke(null,Files.readAllBytes(patch));
        Class<?> loaderType=Class.forName("PatchWeavingProbe$BytesLoader");
        Constructor<?> ctor=loaderType.getDeclaredConstructor(Map.class);ctor.setAccessible(true);
        ClassLoader adviceLoader=(ClassLoader)ctor.newInstance(Map.of(name,converted));
        Class<?> advice=Class.forName(name,true,adviceLoader);
        Method matcher=verifier.getDeclaredMethod("matcher",Class.class,String.class,boolean.class);matcher.setAccessible(true);
        ElementMatcher.Junction<MethodDescription> match=(ElementMatcher.Junction<MethodDescription>)matcher.invoke(null,advice,methodName,true);
        ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(Map.of(name,converted)),ClassFileLocator.ForClassLoader.ofSystemLoader());
        var target=TypePool.Default.of(locator).describe(targetName).resolve();
        require(target.getDeclaredMethods().filter(match).size()==1,"production Advice matcher must select exactly real lambda");
        byte[] woven=new ByteBuddy().redefine(target,locator).visit(Advice.to(advice,locator).on(match)).make().getBytes();
        Files.write(output,woven);Files.write(output.resolveSibling(patch.getFileName()+".converted.class"),converted);
        require(new String(woven,java.nio.charset.StandardCharsets.ISO_8859_1).contains("begin"+scopeName+"Fade"),"default production weave omitted entry scope");
        require(new String(woven,java.nio.charset.StandardCharsets.ISO_8859_1).contains("end"+scopeName+"Fade"),"default production weave omitted exit scope");
        System.out.println("FADE122 real production Advice WEAVE PASS matched=1; target="+targetName+"."+methodName+"; default ByteBuddy; source="+patch);
    }
    private static byte[] resource(String name)throws Exception {try(InputStream in=FadeRouting122Probe.class.getClassLoader().getResourceAsStream(name)){if(in==null)throw new AssertionError(name);return in.readAllBytes();}}
    private static String hex(byte[] b){return java.util.HexFormat.of().formatHex(b);}
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
