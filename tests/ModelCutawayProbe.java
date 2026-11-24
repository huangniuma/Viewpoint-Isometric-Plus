import java.lang.reflect.*;
import java.lang.Math;
import java.nio.file.*;
import java.util.*;
import org.joml.*;
import viewpoint.core.Frame;
import viewpoint.render.ModelDraws;
import vpiso.*;
import zombie.core.properties.PropertyContainer;
import zombie.core.skinnedmodel.model.*;
import zombie.iso.*;
import zombie.scripting.objects.ModelScript;

/** Actual native cutaway decisions and original rigid door producer through woven Models.own. */
public final class ModelCutawayProbe {
    static final String CUT="zombie.iso.fboRenderChunk.FBORenderCutaways";
    static Method own;
    static Class<?> producer;
    static Object building,cutaways;
    static ArrayList<Object> collapsed;
    static boolean baseline;
    static int cases;

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        baseline=args.length>0 && args[0].equals("baseline035");
        cutaways=GameAccess.staticCall(CUT,"getInstance");
        Object per=((Object[])GameAccess.field(CUT,"perPlayerData",cutaways))[0];
        Object btc=GameAccess.field(CUT+"$PerPlayerData","buildingsToCollapse",per);
        collapsed=(ArrayList<Object>)GameAccess.field(CUT+"$BuildingsToCollapse","buildingsToCollapse",btc);
        building=allocate(BuildingDef.class);collapsed.add(building);
        camera(0f);
        producer=producer(!baseline);
        own=producer.getDeclaredMethod("own",Frame.class,int.class,IsoGridSquare.class,IsoObject.class,zombie.inventory.InventoryItem.class);
        own.setAccessible(true);
        Main.compatible=true;
        if(args.length>0 && args[0].equals("baseline037")) {
            Frame bad=run(square(1,true),0f,1,true);
            require((bad.scene.models.flags(1)&ModelDraws.COLOUR)==0 && (bad.scene.models.flags(1)&ModelDraws.SHADOW)!=0,
                "Pinned037 must reproduce a collapsed upper door still casting");
            System.out.println("Model shadow cutaway negative control PASS: pinned037 hides upper door color but retains its shadow");
            return;
        }
        if(baseline) {
            Frame bad=run(square(1,true),0f,1,true);
            require((bad.scene.models.flags(1)&1)!=0,"Pinned035 must reproduce visible upper door");
            System.out.println("Model cutaway negative control PASS: pinned035 original own + visibility leaves a null-key upper door visible");
            return;
        }
        for(int z:new int[]{-1,0,1,2})for(boolean collapse:new boolean[]{false,true}) {
            IsoGridSquare square=square(z,collapse);
            boolean visible=nativeVisible(square);
            Frame frame=run(square,0f,1,true);
            check(frame,visible,true);cases++;
        }
        // An unchanged native square is re-evaluated as the player goes upstairs and downstairs.
        IsoGridSquare upper=square(1,true);
        for(float level:new float[]{0f,1f,0f,2f}) {
            camera(level);check(run(upper,level,1,false),level>=1f,false);cases++;
        }
        camera(0f);
        // Ordinary VP, native shadow-only capture, and unsupported binaries retain their records.
        check(run(upper,0f,0,false),true,false);cases++;
        check(run(upper,0f,2,false),true,false);cases++;
        Main.compatible=false;check(run(upper,0f,1,false),true,false);Main.compatible=true;cases++;
        // A separate thread cannot inherit the game-thread capture scope.
        scope(1);Throwable[] failure={null};
        Thread other=new Thread(()->{try{check(run(upper,0f,0,false),true,false);}catch(Throwable e){failure[0]=e;}});
        other.start();other.join();scope(0);if(failure[0]!=null)throw new AssertionError(failure[0]);cases++;
        require(!IsometricBridge.capturing() && !IsometricBridge.nativeShadowCapture(),"Scope leaked");
        System.out.println("Model cutaway regression PASS: "+cases+" cases; original rigid null-key door producer, native collapsed/visible floors, stairs-level recovery, ordinary VP/native-shadow/thread/fingerprint isolation; collapsed COLOUR removed, physical SHADOW retained; GLASS, SOLID, target, GPU indices and prior models preserved. Controlled fixtures; no map or live visual acceptance.");
    }
    static void camera(float z) {
        Object camera=GameAccess.field("zombie.iso.IsoCamera","frameState",null);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","camCharacterZ",camera,z);
    }
    static boolean nativeVisible(IsoGridSquare square) {
        return (Boolean)GameAccess.call(CUT,"shouldRenderBuildingSquare",cutaways,new Class<?>[]{int.class,IsoGridSquare.class},0,square);
    }
    static IsoGridSquare square(int z,boolean collapse)throws Exception {
        IsoGridSquare square=(IsoGridSquare)allocate(IsoGridSquare.class);square.x=-9;square.y=23;square.z=z;
        GameAccess.set(IsoGridSquare.class.getName(),"objects",square,new ArrayList<>());
        GameAccess.set(IsoGridSquare.class.getName(),"properties",square,new PropertyContainer());
        GameAccess.set(IsoGridSquare.class.getName(),"associatedBuilding",square,collapse?building:null);
        Object chunk=allocate(IsoChunk.class);
        Object levels=GameAccess.type(CUT+"$ChunkLevelsData").getConstructor(IsoChunk.class).newInstance(chunk);
        GameAccess.set(IsoChunk.class.getName(),"cutawayData",chunk,levels);
        GameAccess.set(IsoGridSquare.class.getName(),"chunk",square,chunk);
        Object level=GameAccess.call(CUT+"$ChunkLevelsData","getDataForLevel",levels,new Class<?>[]{int.class},z);
        Object orphan=GameAccess.field(CUT+"$ChunkLevelData","orphanStructures",level);
        GameAccess.set(CUT+"$OrphanStructures","adjacentChunkLoadedCounter",orphan,0);
        GameAccess.set(IsoChunk.class.getName(),"wx",chunk,Math.floorDiv(square.x,8));
        GameAccess.set(IsoChunk.class.getName(),"wy",chunk,Math.floorDiv(square.y,8));
        byte[][] flags=(byte[][])GameAccess.field(CUT+"$ChunkLevelData","squareFlags",level);
        for(byte[] player:flags)Arrays.fill(player,(byte)1);
        return square;
    }
    @SuppressWarnings("unchecked")
    static void scope(int mode) {
        ThreadLocal<Boolean> capturing=(ThreadLocal<Boolean>)GameAccess.field("vpiso.IsometricBridge","CAPTURING",null);
        ThreadLocal<Boolean> shadows=(ThreadLocal<Boolean>)GameAccess.field("vpiso.IsometricBridge","NATIVE_SHADOW_CAPTURE",null);
        if(mode==0){capturing.remove();shadows.remove();}
        else{capturing.set(true);shadows.set(mode==2);}
    }
    static Frame run(IsoGridSquare square,float camZ,int mode,boolean target)throws Exception {
        Frame frame=new Frame();frame.camX=-9;frame.camY=23;frame.camZ=camZ;
        frame.scene.models.add(null,null,null,new Matrix4f(),-1,-1,0,3);
        ModelMesh mesh=(ModelMesh)allocate(ModelMesh.class);
        Object priv=allocate(GameAccess.type("zombie.asset.Asset$PRIVATE"));
        GameAccess.set("zombie.asset.Asset$PRIVATE","currentState",priv,GameAccess.type("zombie.asset.Asset$State").getField("READY").get(null));
        GameAccess.set("zombie.asset.Asset","priv",mesh,priv);
        GameAccess.set(ModelMesh.class.getName(),"minXyz",mesh,new Vector3f(-.3f,0,-.05f));
        GameAccess.set(ModelMesh.class.getName(),"maxXyz",mesh,new Vector3f(.3f,1.5f,.05f));
        mesh.vb=(VertexBufferObject)allocate(VertexBufferObject.class);
        Model model=(Model)allocate(Model.class);GameAccess.set(Model.class.getName(),"mesh",model,mesh);model.isStatic=true;
        ModelScript script=new ModelScript();GameAccess.set(ModelScript.class.getName(),"shaderName",script,"door");
        IsoObjectModelDrawer drawer=new IsoObjectModelDrawer();
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"model",drawer,model);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"modelScript",drawer,script);
        for(String name:List.of("tintR","tintG","tintB","ambientR","ambientG","ambientB","alpha"))
            GameAccess.set(IsoObjectModelDrawer.class.getName(),name,drawer,1f);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"x",drawer,square.x+.5f);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"y",drawer,square.y+.5f);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"z",drawer,(float)square.z);
        IsoObject object=(IsoObject)allocate(zombie.iso.objects.IsoDoor.class);
        frame.drawers.add(drawer);if(target)frame.lootObject=object;
        Field sink=producer.getDeclaredField("sink");sink.setAccessible(true);sink.set(null,frame.drawers);
        scope(mode);
        try{own.invoke(null,frame,0,square,object,null);IsometricVisibility.apply(frame,null,0);}
        finally{scope(0);sink.set(null,null);}
        require(frame.scene.models.count()==2 && frame.scene.models.key(1)==null,"Actual null-key door not produced");
        return frame;
    }
    static void check(Frame frame,boolean visible,boolean target) {
        int actual=frame.scene.models.flags(1),expected=(1155|(target?ModelDraws.TARGET:0))&~(visible?0:ModelDraws.COLOUR);
        require(actual==expected,"Door flag mismatch "+actual+" expected "+expected);
        require(frame.scene.models.flags(0)==3,"Previous model changed");
        float[] values=(float[])GameAccess.field("viewpoint.render.ModelDraws","values",frame.scene.models);
        require(values[68+55]==actual,"GPU record flags stale");
        boolean casts=(Boolean)GameAccess.call("viewpoint.render.ModelPass","casts",null,
            new Class<?>[]{ModelDraws.class,int.class,float.class,float.class},frame.scene.models,1,0f,128f);
        require(casts,"Camera-hidden door lost physical caster eligibility");
    }
    static Class<?> producer(boolean weave)throws Exception {
        Map<String,byte[]> targets=new HashMap<>();
        var connection=(java.net.JarURLConnection)ClassLoader.getSystemResource("viewpoint/models/Models.class").openConnection();
        try(var jar=connection.getJarFile()) {
            for(var entry:jar.stream().filter(e->e.getName().startsWith("viewpoint/models/") && e.getName().endsWith(".class")).toList())
                targets.put(entry.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(entry).readAllBytes());
        }
        var loaderType=Class.forName("PatchWeavingProbe$BytesLoader");var ctor=loaderType.getDeclaredConstructor(Map.class,boolean.class);ctor.setAccessible(true);
        if(weave) {
            Method convert=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
            byte[] artifactAdvice;
            try(var input=ClassLoader.getSystemResourceAsStream("vpiso/Patch_ModelCutaways.class")) {
                if(input==null)throw new AssertionError("Artifact Advice missing");
                artifactAdvice=input.readAllBytes();
            }
            byte[] bytes=(byte[])convert.invoke(null,(Object)artifactAdvice);
            var converted=Map.of("vpiso.Patch_ModelCutaways",bytes);
            var locator=new net.bytebuddy.dynamic.ClassFileLocator.Compound(new net.bytebuddy.dynamic.ClassFileLocator.Simple(converted),net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
            ClassLoader advice=(ClassLoader)ctor.newInstance(converted,false);
            var target=net.bytebuddy.pool.TypePool.Default.of(locator).describe("viewpoint.models.Models").resolve();
            targets.put("viewpoint.models.Models",new net.bytebuddy.ByteBuddy().redefine(target,locator)
                .visit(net.bytebuddy.asm.Advice.to(advice.loadClass("vpiso.Patch_ModelCutaways"),locator).on(net.bytebuddy.matcher.ElementMatchers.named("own"))).make().getBytes());
        }
        return ((ClassLoader)ctor.newInstance(targets,false)).loadClass("viewpoint.models.Models");
    }
    static Object allocate(Class<?> type)throws Exception {
        Field f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object u=f.get(null);
        return u.getClass().getMethod("allocateInstance",Class.class).invoke(u,type);
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
