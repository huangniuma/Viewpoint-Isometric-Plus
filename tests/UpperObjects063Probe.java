import java.lang.reflect.*;
import java.lang.Math;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.joml.*;
import viewpoint.core.Frame;
import viewpoint.render.*;
import vpiso.*;
import zombie.core.properties.PropertyContainer;
import zombie.core.skinnedmodel.model.*;
import zombie.iso.*;
import zombie.iso.objects.IsoDoor;
import zombie.iso.sprite.*;
import zombie.iso.fboRenderChunk.*;
import zombie.scripting.objects.ModelScript;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** The real native multilevel-square exception must not expose unrelated upper objects. */
public final class UpperObjects063Probe {
    public static final IdentityHashMap<Object,Integer> nativeDraws=new IdentityHashMap<>();
    private static final String CELL="zombie.iso.fboRenderChunk.FBORenderCell";
    private static final String CUT="zombie.iso.fboRenderChunk.FBORenderCutaways";
    private static boolean baseline;
    private static Object cutaways;
    private static IsoGridSquare upper;
    private static IsoObject tall;
    private static IsoDoor door,open;
    private static Object nativeRenderer;
    private static Method nativeOpaque, nativeInfo;
    private static Class<?> modelsProducer;
    private static Method own;
    private static int cases;

    public static final class DrawSink {
        @Advice.OnMethodEnter(skipOn=Advice.OnNonDefaultValue.class)
        public static boolean enter(@Advice.Argument(0) Object object) {
            UpperObjects063Probe.nativeDraws.merge(object,1,Integer::sum);
            return true;
        }
    }

    public static IsoGridSquare squareAt(int x,int y,int z) {
        return upper!=null&&x==upper.x&&y==upper.y&&z==upper.z?upper:null;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        baseline=args.length>0&&args[0].equals("baseline062");
        cutaways=GameAccess.staticCall(CUT,"getInstance");
        Object per=((Object[])GameAccess.field(CUT,"perPlayerData",cutaways))[0];
        Object btc=GameAccess.field(CUT+"$PerPlayerData","buildingsToCollapse",per);
        ArrayList<Object> collapsed=(ArrayList<Object>)GameAccess.field(CUT+"$BuildingsToCollapse","buildingsToCollapse",btc);
        ModelCutawayProbe.building=ModelCutawayProbe.allocate(BuildingDef.class);
        ModelCutawayProbe.cutaways=cutaways;
        collapsed.add(ModelCutawayProbe.building);
        upper=ModelCutawayProbe.square(1,true);
        upper.x=upper.y=0;
        upper.chunk.wx=upper.chunk.wy=0;
        GameAccess.set("zombie.iso.IsoGridSquare","targetPlayerCutawayFlags",upper,new byte[4]);
        GameAccess.set("zombie.iso.IsoGridSquare","playerCutawayFlags",upper,new byte[4]);
        GameAccess.set("zombie.iso.IsoGridSquare","playerCutawayFlagLockUntilTimes",upper,new long[4]);
        PropertyContainer properties=upper.getProperties();
        zombie.core.TilePropertyAliasMap.instance.Generate(new HashMap<>(Map.of("SpriteGridPos",new ArrayList<>(List.of("0,0,1")))));
        properties.set("SpriteGridPos","0,0,1");
        require(properties.has("SpriteGridPos"),"Native SpriteGridPos property fixture was not produced");
        IsoSprite bottom=new IsoSprite(),top=new IsoSprite();
        IsoSpriteGrid grid=new IsoSpriteGrid(1,1,2);
        grid.setSprite(0,0,0,bottom);grid.setSprite(0,0,1,top);
        bottom.setSpriteGrid(grid);top.setSpriteGrid(grid);
        tall=new IsoObject(null,upper,top);
        door=new IsoDoor(null);door.square=upper;door.sprite=new IsoSprite();
        open=new IsoDoor(null);open.square=upper;open.sprite=new IsoSprite();
        // An open door's native alpha producer changes MinusFloor to Translucent.
        GameAccess.set("zombie.iso.objects.IsoDoor","open",open,true);
        upper.getObjects().addAll(List.of(tall,door,open));
        Object player=ModelCutawayProbe.allocate(zombie.characters.IsoPlayer.class);
        GameAccess.set("zombie.characters.IsoPlayer","instance",null,player);
        Object camera=GameAccess.field("zombie.iso.IsoCamera","frameState",null);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","playerIndex",camera,0);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","camCharacterSquare",camera,upper);
        // The native classifier queries this singleton even for dry upper squares.
        // Supply an uninitialised dry-water service so classification needs no GL context.
        GameAccess.set("zombie.iso.IsoWater","instance",null,ModelCutawayProbe.allocate(IsoWater.class));
        GameAccess.set("zombie.iso.IsoWater","isShaderEnable",null,false);
        lighting();
        nativeInfo=GameAccess.type(CELL).getDeclaredMethod("calculateObjectRenderInfo",int.class,IsoGridSquare.class,IsoObject.class);
        nativeInfo.setAccessible(true);
        buildNativeSink();
        modelsProducer=modelProducer();
        own=modelsProducer.getDeclaredMethod("own",Frame.class,int.class,IsoGridSquare.class,IsoObject.class,zombie.inventory.InventoryItem.class);
        own.setAccessible(true);Main.compatible=true;
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",UpperObjects063Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        try {
            camera(0);
            require(nativeVisible()&&force(),"Real multilevel producer must permit the upper square as a forced exception");
            nativeLayers();
            require(layer(door)==ObjectRenderLayer.MinusFloor,"Closed opaque door did not select real native MinusFloor");
            require(layer(open)==ObjectRenderLayer.Translucent&&Math.abs(open.getRenderInfo(0).targetAlpha-.6f)<.0001f,
                    "Open door did not select real native alpha conversion to Translucent");
            checkNativeDraws(true);
            Frame d=model(door,0,1),t=model(tall,0,1),o=model(open,0,1);
            require(colour(t)&&colour(o),"Multilevel source or actual native translucent door was over-hidden");
            require(colour(d)==baseline,"Opaque upper door must match native forced-square filtering");
            shadow(d);shadow(t);shadow(o);cases+=3;
            checkMesh(0,baseline);
            // Visibility is a frame decision: no cache mutation as the player goes upstairs and back.
            for(float z:new float[]{1,0,1,0}) {
                camera(z);nativeLayers();checkNativeDraws(z<1);
                Frame current=model(door,z,1);
                require(colour(current)==(baseline||z>=1),"Stair-level recovery or repeated capture failed");shadow(current);
                checkMesh(z,baseline||z>=1);cases++;
            }
            camera(0);
            for(int mode:new int[]{0,2}) {
                Frame current=model(door,0,mode);require(colour(current),"Ordinary VP/native shadow capture changed");shadow(current);cases++;
            }
            Main.compatible=false;require(colour(model(door,0,1)),"Unsupported binary gate changed");Main.compatible=true;cases++;
            System.out.println((baseline?"Pinned062 upper mixed negative control PASS: ":"Upper mixed objects regression063 PASS: ")+cases+
                    " cases; actual native multilevel square producer, native opaque draw consumer with draw-only interception, actual native open-door alpha conversion, woven VP rigid source, real ObjectSpans producer and owned baked plans; stairs and physical SHADOW retained. Controlled fixture, no live map/GL.");
        } finally {
            Visibility109Bindings.set(api,"square",previous);
            ModelCutawayProbe.scope(0);collapsed.remove(ModelCutawayProbe.building);
        }
    }

    private static void camera(float z) {ModelCutawayProbe.camera(z);}
    private static boolean nativeVisible() {return (Boolean)GameAccess.call(CUT,"shouldRenderBuildingSquare",cutaways,new Class<?>[]{int.class,IsoGridSquare.class},0,upper);}
    private static boolean force() {return (Boolean)GameAccess.call(CUT,"isForceRenderSquare",cutaways,new Class<?>[]{int.class,IsoGridSquare.class},0,upper);}
    private static ObjectRenderLayer layer(IsoObject object) {return object.getRenderInfo(0).layer;}
    private static void nativeLayers()throws Exception {
        Object renderer=GameAccess.field(CELL,"instance",null);
        for(IsoObject object:upper.getObjects())nativeInfo.invoke(renderer,0,upper,object);
    }
    private static void lighting()throws Exception {
        Class<?> type=GameAccess.type("zombie.iso.IsoGridSquare$ILighting");
        Object lighting=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getReturnType()==boolean.class)return true;
            if(m.getReturnType()==float.class)return 1f;
            if(m.getReturnType()==int.class)return 0;
            if(m.getReturnType()==zombie.core.textures.ColorInfo.class)return new zombie.core.textures.ColorInfo(1,1,1,1);
            return null;
        });
        Object array=Array.newInstance(type,4);for(int i=0;i<4;i++)Array.set(array,i,lighting);
        GameAccess.set("zombie.iso.IsoGridSquare","lighting",upper,array);
    }
    private static void buildNativeSink()throws Exception {
        Map<String,byte[]> entries=new HashMap<>();
        var connection=(java.net.JarURLConnection)ClassLoader.getSystemResource(CELL.replace('.','/')+".class").openConnection();
        try(JarFile jar=connection.getJarFile()) {
            for(JarEntry e:jar.stream().filter(e->e.getName().startsWith(CELL.replace('.','/'))&&e.getName().endsWith(".class")).toList())
                entries.put(e.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(e).readAllBytes());
        }
        ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(entries),ClassFileLocator.ForClassLoader.ofSystemLoader());
        entries.put(CELL,new ByteBuddy().redefine(TypePool.Default.of(locator).describe(CELL).resolve(),locator)
                .visit(Advice.to(DrawSink.class).on(named("renderMinusFloor").and(takesArguments(IsoObject.class)))).make().getBytes());
        var ctor=Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class,boolean.class);ctor.setAccessible(true);
        Class<?> renderer=((ClassLoader)ctor.newInstance(entries,false)).loadClass(CELL);
        nativeRenderer=renderer.getField("instance").get(null);
        nativeOpaque=renderer.getDeclaredMethod("renderMinusFloor",IsoChunk.class,IsoGridSquare.class,List.class);
        nativeOpaque.setAccessible(true);
    }
    private static Class<?> modelProducer()throws Exception {
        // Weave the Advice from the artifact under test, including the actual pinned062
        // Advice. Loading build/classes here would silently test current Advice twice.
        Map<String,byte[]> entries=new HashMap<>();
        var connection=(java.net.JarURLConnection)ClassLoader.getSystemResource("viewpoint/models/Models.class").openConnection();
        try(JarFile jar=connection.getJarFile()) {
            for(JarEntry e:jar.stream().filter(e->e.getName().startsWith("viewpoint/models/")&&e.getName().endsWith(".class")).toList())
                entries.put(e.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(e).readAllBytes());
        }
        byte[] patch;
        try(var input=ClassLoader.getSystemResourceAsStream("vpiso/Patch_ModelCutaways.class")) {
            require(input!=null,"Artifact under test has no ModelCutaways Advice");patch=input.readAllBytes();
        }
        Method convert=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
        Map<String,byte[]> converted=Map.of("vpiso.Patch_ModelCutaways",(byte[])convert.invoke(null,(Object)patch));
        var ctor=Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class,boolean.class);ctor.setAccessible(true);
        ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(converted),ClassFileLocator.ForClassLoader.ofSystemLoader());
        ClassLoader advice=(ClassLoader)ctor.newInstance(converted,false);
        entries.put("viewpoint.models.Models",new ByteBuddy().redefine(TypePool.Default.of(locator).describe("viewpoint.models.Models").resolve(),locator)
                .visit(Advice.to(advice.loadClass("vpiso.Patch_ModelCutaways"),locator).on(named("own"))).make().getBytes());
        return ((ClassLoader)ctor.newInstance(entries,false)).loadClass("viewpoint.models.Models");
    }
    private static void checkNativeDraws(boolean forced)throws Exception {
        nativeDraws.clear();nativeOpaque.invoke(nativeRenderer,upper.chunk,upper,upper.getObjects());
        require(nativeDraws.getOrDefault(tall,0)==1,"Native consumer dropped the real multilevel part");
        require(nativeDraws.getOrDefault(door,0)==(forced?0:1),"Native opaque consumer did not distinguish unrelated door on the mixed square");
        require(nativeDraws.getOrDefault(open,0)==0,"Native opaque consumer drew the actual translucent door");
    }
    private static Frame model(IsoObject source,float camZ,int mode)throws Exception {
        Frame frame=new Frame();frame.camZ=camZ;
        ModelMesh mesh=(ModelMesh)ModelCutawayProbe.allocate(ModelMesh.class);
        Object priv=ModelCutawayProbe.allocate(GameAccess.type("zombie.asset.Asset$PRIVATE"));
        GameAccess.set("zombie.asset.Asset$PRIVATE","currentState",priv,GameAccess.type("zombie.asset.Asset$State").getField("READY").get(null));
        GameAccess.set("zombie.asset.Asset","priv",mesh,priv);
        GameAccess.set(ModelMesh.class.getName(),"minXyz",mesh,new Vector3f(-.3f,0,-.05f));
        GameAccess.set(ModelMesh.class.getName(),"maxXyz",mesh,new Vector3f(.3f,1.5f,.05f));
        mesh.vb=(VertexBufferObject)ModelCutawayProbe.allocate(VertexBufferObject.class);
        Model model=(Model)ModelCutawayProbe.allocate(Model.class);GameAccess.set(Model.class.getName(),"mesh",model,mesh);model.isStatic=true;
        ModelScript script=new ModelScript();GameAccess.set(ModelScript.class.getName(),"shaderName",script,"door");
        IsoObjectModelDrawer drawer=new IsoObjectModelDrawer();
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"model",drawer,model);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"modelScript",drawer,script);
        for(String name:List.of("tintR","tintG","tintB","ambientR","ambientG","ambientB","alpha"))
            GameAccess.set(IsoObjectModelDrawer.class.getName(),name,drawer,1f);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"x",drawer,.5f);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"y",drawer,.5f);
        GameAccess.set(IsoObjectModelDrawer.class.getName(),"z",drawer,1f);
        frame.drawers.add(drawer);
        Field sink=modelsProducer.getDeclaredField("sink");sink.setAccessible(true);sink.set(null,frame.drawers);
        ModelCutawayProbe.scope(mode);
        try {own.invoke(null,frame,0,upper,source,null);}
        finally {ModelCutawayProbe.scope(0);sink.set(null,null);}
        require(frame.scene.models.count()==1,"Actual VP rigid producer did not emit source model");
        return frame;
    }
    private static boolean colour(Frame frame) {return (frame.scene.models.flags(0)&ModelDraws.COLOUR)!=0;}
    private static void shadow(Frame frame) {
        require((frame.scene.models.flags(0)&ModelDraws.SHADOW)!=0,"Camera-only hide removed physical shadow");
        float[] values=(float[])GameAccess.field("viewpoint.render.ModelDraws","values",frame.scene.models);
        require((int)values[55]==frame.scene.models.flags(0),"GPU model flag record stale");
    }
    private static void checkMesh(float camZ,boolean allVisible)throws Exception {
        Class<?> spansType=GameAccess.type("viewpoint.world.ObjectSpans");
        Constructor<?> ctor=spansType.getDeclaredConstructor();ctor.setAccessible(true);Object source=ctor.newInstance();
        call(spansType,source,"begin",new Class<?>[]{int.class},1);
        call(spansType,source,"page",new Class<?>[]{int.class},0);
        for(int i=0;i<3;i++)call(spansType,source,"add",new Class<?>[]{int.class,int.class,int.class,int.class},i,i*6,(i+1)*6,0);
        call(spansType,source,"end",new Class<?>[]{int.class},1);
        int[] spans=(int[])call(spansType,source,"spans",new Class<?>[0]);
        int[] pages=(int[])call(spansType,source,"pages",new Class<?>[0]);
        ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[1],new int[]{0},new int[]{18},new float[18*14],1);
        GameAccess.set("viewpoint.render.ChunkMeshData","runs",mesh,new int[]{0,0,18});
        GameAccess.set("viewpoint.render.ChunkMeshData","pageRuns",mesh,new int[]{0,1});
        mesh.objects(spans,pages,null);
        int[] spansBefore=spans.clone(),runsBefore=mesh.runs.clone();
        Frame frame=new Frame();frame.camZ=camZ;frame.scene.meshCount=1;frame.scene.meshes[0]=mesh;
        frame.scene.meshFlags[0]=SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW;
        IsometricVisibility.apply(frame,new Object(),0);
        require((frame.scene.meshFlags[0]&SceneData.CASTS_SHADOW)!=0,"Baked camera hide removed physical shadow mesh");
        int start=frame.scene.meshPlan[0],length=frame.scene.meshPlanLength[0];
        if(allVisible)require(start<0||planCount(frame,start,length)==18,"Visible source ranges not restored");
        else {
            require(start>=0&&planCount(frame,start,length)==12,"Owned baked plan did not remove only the unrelated opaque door");
            require(!planContains(frame,start,length,6)&&planContains(frame,start,length,0)&&planContains(frame,start,length,12),"Baked plan clipped wrong source object");
        }
        require(Arrays.equals(spans,spansBefore)&&Arrays.equals(mesh.runs,runsBefore),"Shared native VP mesh metadata mutated");cases++;
    }
    private static Object call(Class<?> type,Object target,String name,Class<?>[] args,Object...values)throws Exception {
        Method method=type.getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(target,values);
    }
    private static int planCount(Frame frame,int start,int length) {
        int sum=0;for(int i=start;i<start+length;i++)sum+=frame.scene.plan[i*3+2];return sum;
    }
    private static boolean planContains(Frame frame,int start,int length,int vertex) {
        for(int i=start;i<start+length;i++)if(vertex>=frame.scene.plan[i*3+1]&&vertex<frame.scene.plan[i*3+1]+frame.scene.plan[i*3+2])return true;
        return false;
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
