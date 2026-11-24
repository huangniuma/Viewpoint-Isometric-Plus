import java.lang.reflect.*;
import java.lang.Math;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import viewpoint.core.Frame;
import viewpoint.render.*;
import viewpoint.world.TileMesh;
import vpiso.*;
import zombie.core.opengl.Shader;
import zombie.core.textures.*;
import zombie.iso.*;
import zombie.iso.objects.IsoWindowFrame;
import zombie.iso.sprite.*;
import zombie.iso.fboRenderChunk.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Actual VP cap ownership and native final translucent alpha, without a map or GL. */
public final class UpperObjects064Probe {
    private static final String CUT="zombie.iso.fboRenderChunk.FBORenderCutaways";
    private static final String CELL="zombie.iso.fboRenderChunk.FBORenderCell";
    private static final Map<String,IsoGridSquare> squares=new HashMap<>();
    public static final IdentityHashMap<IsoObject,Float> finalAlpha=new IdentityHashMap<>();
    private static boolean baseline, capsOnly;
    private static Object cutaways, nativeRenderer;
    private static Class<?> nativeType;
    private static IsoGridSquare hidden,visible,forced;
    private static List<Object> collapsed;
    private static int cases;

    public static IsoGridSquare squareAt(int x,int y,int z) {return squares.get(x+":"+y+":"+z);}
    public static IsoGridSquare nativeSquareAt(IsoCell cell,int x,int y,int z) {return squareAt(x,y,z);}
    public static void drawSink(IsoObject object,float x,float y,float z,ColorInfo light,boolean a,boolean b,Shader shader) {
        finalAlpha.put(object,object.getAlpha(0));
    }
    public static final class FixturePanel extends IsoWindowFrame {
        FixturePanel(IsoGridSquare square) {super(null,square,new IsoSprite(),true);}
        // Bind only the neighbouring-world lookup; the native alpha producer and
        // native final consumer remain the shipped implementations.
        @Override public IsoGridSquare getOppositeSquare() {return square.getAdjacentSquare(IsoDirections.N);}
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        baseline=Arrays.asList(args).contains("baseline063");
        capsOnly=Arrays.asList(args).contains("caps");
        cutaways=GameAccess.staticCall(CUT,"getInstance");
        Object per=((Object[])GameAccess.field(CUT,"perPlayerData",cutaways))[0];
        Object btc=GameAccess.field(CUT+"$PerPlayerData","buildingsToCollapse",per);
        collapsed=(List<Object>)GameAccess.field(CUT+"$BuildingsToCollapse","buildingsToCollapse",btc);
        ModelCutawayProbe.building=ModelCutawayProbe.allocate(BuildingDef.class);
        ModelCutawayProbe.cutaways=cutaways;collapsed.add(ModelCutawayProbe.building);
        hidden=square(0,0,1,true);visible=square(1,0,1,false);forced=square(2,0,1,true);
        hidden.getObjects().add(new IsoObject(null,hidden,new IsoSprite()));
        visible.getObjects().add(new IsoObject(null,visible,new IsoSprite()));
        setupForced();
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",UpperObjects064Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        Main.compatible=true;
        try {
            ModelCutawayProbe.camera(0);
            require(!nativeVisible(hidden)&&nativeVisible(visible),"Fixture must have a native-hidden independent upper square beside a native-visible square");
            capRanges();
            if(!capsOnly) {nativeConsumer();alphaCases();}
            System.out.println((baseline?"Pinned063 upper negative control PASS: ":"Upper visibility regression064 PASS: ")+cases+
                    " cases; real WallMesher.emitCaps -> WorldMesher.op -> Cook.page/ObjectSpans, mixed independent upper squares, true ownerless cap"+
                    (capsOnly?"":", native final translucent draw alpha and same-input .4/.6 panels")+
                    ", physical shadows and cached mesh metadata retained. CPU fixture; GPU calls and final draw only intercepted; no live map.");
        } finally {
            Visibility109Bindings.set(api,"square",previous);
            collapsed.remove(ModelCutawayProbe.building);ModelCutawayProbe.scope(0);
        }
    }

    private static IsoGridSquare square(int x,int y,int z,boolean collapse)throws Exception {
        IsoGridSquare s=ModelCutawayProbe.square(z,collapse);s.x=x;s.y=y;s.chunk.wx=s.chunk.wy=0;
        GameAccess.set(IsoGridSquare.class.getName(),"nav",s,new IsoGridSquare[IsoDirections.values().length]);
        GameAccess.set(IsoGridSquare.class.getName(),"targetPlayerCutawayFlags",s,new byte[4]);
        GameAccess.set(IsoGridSquare.class.getName(),"playerCutawayFlags",s,new byte[4]);
        GameAccess.set(IsoGridSquare.class.getName(),"playerCutawayFlagLockUntilTimes",s,new long[4]);
        GameAccess.set(IsoGridSquare.class.getName(),"lightInfo",s,new ColorInfo[]{new ColorInfo(1,1,1,1),new ColorInfo(1,1,1,1),new ColorInfo(1,1,1,1),new ColorInfo(1,1,1,1)});
        lighting(s,1f);squares.put(x+":"+y+":"+z,s);return s;
    }
    private static void lighting(IsoGridSquare square,float darkness)throws Exception {
        Class<?> type=GameAccess.type("zombie.iso.IsoGridSquare$ILighting");
        Object lighting=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getReturnType()==boolean.class)return true;
            if(m.getReturnType()==float.class)return m.getName().equals("darkMulti")?darkness:1f;
            if(m.getReturnType()==int.class)return 0;
            if(m.getReturnType()==ColorInfo.class)return new ColorInfo(1,1,1,1);
            return null;
        });
        Object array=Array.newInstance(type,4);for(int i=0;i<4;i++)Array.set(array,i,lighting);
        GameAccess.set(IsoGridSquare.class.getName(),"lighting",square,array);
    }
    private static void setupForced()throws Exception {
        zombie.core.TilePropertyAliasMap.instance.Generate(new HashMap<>(Map.of("SpriteGridPos",new ArrayList<>(List.of("0,0,1")))));
        forced.getProperties().set("SpriteGridPos","0,0,1");
        IsoSprite bottom=new IsoSprite(),top=new IsoSprite();IsoSpriteGrid grid=new IsoSpriteGrid(1,1,2);
        grid.setSprite(0,0,0,bottom);grid.setSprite(0,0,1,top);bottom.setSpriteGrid(grid);top.setSpriteGrid(grid);
        forced.getObjects().add(new IsoObject(null,forced,top));
        Object player=ModelCutawayProbe.allocate(zombie.characters.IsoPlayer.class);
        GameAccess.set("zombie.characters.IsoPlayer","instance",null,player);
        Object camera=GameAccess.field("zombie.iso.IsoCamera","frameState",null);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","playerIndex",camera,0);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","camCharacterSquare",camera,forced);
        GameAccess.set("zombie.iso.IsoWater","instance",null,ModelCutawayProbe.allocate(IsoWater.class));
        GameAccess.set("zombie.iso.IsoWater","isShaderEnable",null,false);
    }
    private static boolean nativeVisible(IsoGridSquare s) {
        return (Boolean)GameAccess.call(CUT,"shouldRenderBuildingSquare",cutaways,new Class<?>[]{int.class,IsoGridSquare.class},0,s);
    }

    @SuppressWarnings("unchecked")
    private static void capRanges()throws Exception {
        // WallMesher.wall sets owner=-1 at bytecode 517..531 for a north+west
        // corner, while WorldMesher.object keeps the actual source object.
        // Exercise its exact cap producer and cooker, rather than fabricating spans.
        Object batch=fresh("viewpoint.world.Recipe$Batch");
        Constructor<?> tileCtor=TileMesh.class.getDeclaredConstructor(float[].class);tileCtor.setAccessible(true);
        TileMesh tile=(TileMesh)tileCtor.newInstance((Object)new float[]{0,1,0,0,0,0,1,0, 1,1,0,1,0,0,1,0, 0,1,1,0,1,0,1,0});
        Texture texture=(Texture)ModelCutawayProbe.allocate(Texture.class);
        Map<Texture,float[]> mappings=(Map<Texture,float[]>)GameAccess.field("viewpoint.world.WorldMesher","mappings",null);
        mappings.put(texture,new float[]{0,0,1,1,0,0,1,1,1,1,1});
        Class<?> batchType=GameAccess.type("viewpoint.world.Recipe$Batch");
        Class<?> wall=GameAccess.type("viewpoint.world.WallMesher");
        Method caps=wall.getDeclaredMethod("emitCaps",batchType,TileMesh.class,Texture.class,float.class,float.class,float.class);caps.setAccessible(true);
        for(int object:new int[]{0,-1}) {
            GameAccess.set("viewpoint.world.WorldMesher","owner",null,-1);
            GameAccess.set("viewpoint.world.WorldMesher","object",null,object);
            caps.invoke(null,batch,tile,texture,0f,0f,1f);
        }
        GameAccess.set("viewpoint.world.WorldMesher","owner",null,4);
        GameAccess.set("viewpoint.world.WorldMesher","object",null,1<<16);
        GameAccess.call("viewpoint.world.WorldMesher","op",null,
                new Class<?>[]{batchType,int.class,TileMesh.class,Texture.class,float.class,float.class,float.class},batch,0,tile,texture,1f,0f,1f);
        List<Object> ops=(List<Object>)GameAccess.field(batchType.getName(),"ops",batch);
        require((int)GameAccess.field("viewpoint.world.Recipe$Op","owner",ops.get(0))==-1
                &&(int)GameAccess.field("viewpoint.world.Recipe$Op","object",ops.get(0))==0,"Actual cap producer lost exact source owner");
        Object cooker=fresh("viewpoint.world.Cook$Cooker");String type=cooker.getClass().getName();
        GameAccess.call(type,"begin",cooker,new Class<?>[]{int.class},1);
        GameAccess.call(type,"page",cooker,new Class<?>[]{batchType,int.class,int[].class,boolean.class},batch,0,new int[16],false);
        GameAccess.call(type,"end",cooker,new Class<?>[]{int.class},1);
        int count=(int)GameAccess.field(type,"n",cooker)/14;require(count==9,"Real cap cooker did not emit three triangles: "+count);
        int runsCount=(int)GameAccess.field(type,"runCount",cooker);
        int[] runs=Arrays.copyOf((int[])GameAccess.field(type,"runs",cooker),runsCount*3);
        int[] pageRuns=(int[])GameAccess.field(type,"pageRuns",cooker);
        Object spansSource=GameAccess.field(type,"objects",cooker);
        int[] spans=(int[])GameAccess.call(spansSource.getClass().getName(),"spans",spansSource,new Class<?>[0]);
        int[] pageSpans=(int[])GameAccess.call(spansSource.getClass().getName(),"pages",spansSource,new Class<?>[0]);
        require(runs[0]==-1&&spans[0]==0&&spans[1]==0&&spans[2]==3,"Real cap metadata does not expose coarse/exact ownership gap");
        ChunkMeshData mesh=new ChunkMeshData(new TextureID[1],new int[]{0},new int[]{count},new float[count*14],1);
        GameAccess.set(ChunkMeshData.class.getName(),"runs",mesh,runs);GameAccess.set(ChunkMeshData.class.getName(),"pageRuns",mesh,pageRuns);
        mesh.objects(spans,pageSpans,null);
        int[] before=spans.clone(),beforeRuns=runs.clone();
        for(float floor:new float[]{0,1,0,1,0}) {
            ModelCutawayProbe.camera(floor);Frame frame=new Frame();frame.camZ=floor;
            frame.scene.meshCount=1;frame.scene.meshes[0]=mesh;frame.scene.meshFlags[0]=SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW;
            IsometricVisibility.apply(frame,new Object(),0);
            boolean leak=contains(frame,0);boolean expected=baseline||floor>=1;
            require(leak==expected,"Independent hidden upper cap leakage="+leak+", expected="+expected+", floor="+floor);
            require(contains(frame,3)&&contains(frame,6),"Truly ownerless cap or visible neighbour disappeared");
            require((frame.scene.meshFlags[0]&SceneData.CASTS_SHADOW)!=0,"Camera hide removed physical shadow mesh");
            require(Arrays.equals(spans,before)&&Arrays.equals(runs,beforeRuns),"Cached source metadata was mutated");cases++;
        }
        mappings.remove(texture);ModelCutawayProbe.camera(0);
        System.out.println("Cap producer observation: coarseOwner=-1, exactObject=0, source native-hidden; pinned063 retains="+baseline+"; 3 vertices remain physically eligible, truly ownerless/visible ranges retained.");
    }
    private static boolean contains(Frame frame,int vertex) {
        int start=frame.scene.meshPlan[0],length=frame.scene.meshPlanLength[0];if(start<0)return (frame.scene.meshFlags[0]&SceneData.VISIBLE)!=0;
        for(int i=start;i<start+length;i++)if(vertex>=frame.scene.plan[i*3+1]&&vertex<frame.scene.plan[i*3+1]+frame.scene.plan[i*3+2])return true;
        return false;
    }

    private static void nativeConsumer()throws Exception {
        Map<String,byte[]> entries=new HashMap<>();
        var connection=(java.net.JarURLConnection)ClassLoader.getSystemResource(CELL.replace('.','/')+".class").openConnection();
        try(JarFile jar=connection.getJarFile()) {
            for(JarEntry e:jar.stream().filter(e->e.getName().startsWith(CELL.replace('.','/'))&&e.getName().endsWith(".class")).toList())
                entries.put(e.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(e).readAllBytes());
        }
        ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(entries),ClassFileLocator.ForClassLoader.ofSystemLoader());
        entries.put(CELL,new ByteBuddy().redefine(TypePool.Default.of(locator).describe(CELL).resolve(),locator)
                .visit(MemberSubstitution.strict().method(isDeclaredBy(named("zombie.IndieGL"))).stub().on(any()))
                .visit(MemberSubstitution.strict().method(named("getGridSquare").and(isDeclaredBy(named("zombie.iso.IsoCell"))).and(takesArguments(int.class,int.class,int.class)))
                        .replaceWith(UpperObjects064Probe.class.getMethod("nativeSquareAt",IsoCell.class,int.class,int.class,int.class)).on(any()))
                .visit(MemberSubstitution.strict().method(named("render").and(isDeclaredBy(named("zombie.iso.IsoObject")))
                        .and(takesArguments(float.class,float.class,float.class,ColorInfo.class,boolean.class,boolean.class,Shader.class)))
                        .replaceWith(UpperObjects064Probe.class.getMethod("drawSink",IsoObject.class,float.class,float.class,float.class,ColorInfo.class,boolean.class,boolean.class,Shader.class)).on(any()))
                .make().getBytes());
        var ctor=Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class,boolean.class);ctor.setAccessible(true);
        nativeType=((ClassLoader)ctor.newInstance(entries,false)).loadClass(CELL);nativeRenderer=nativeType.getField("instance").get(null);
    }
    private static void alphaCases()throws Exception {
        require(nativeVisible(forced)&&(Boolean)GameAccess.call(CUT,"isForceRenderSquare",cutaways,new Class<?>[]{int.class,IsoGridSquare.class},0,forced),"Tall source must force real native upper-square exception");
        IsoGridSquare opposite=square(2,-1,1,false);forced.setAdjacentSquare(IsoDirections.N,opposite);
        IsoWindowFrame panel=new FixturePanel(forced);forced.getObjects().add(panel);
        Method classify=nativeType.getDeclaredMethod("calculateObjectRenderInfo",int.class,IsoGridSquare.class,IsoObject.class);classify.setAccessible(true);
        Method draw=nativeType.getMethod("renderTranslucent",IsoObject.class);
        for(float desired:new float[]{0,.4f,.6f,0,.4f}) {
            lighting(opposite,desired*.5f);panel.setAlphaAndTarget(0,0);
            classify.invoke(nativeRenderer,0,forced,panel);
            ObjectRenderInfo info=panel.getRenderInfo(0);
            require(info.layer==ObjectRenderLayer.Translucent&&Math.abs(info.targetAlpha-desired)<.0001f,"Native window alpha fixture did not select expected translucent result "+desired+" actual="+info.targetAlpha);
            finalAlpha.clear();draw.invoke(nativeRenderer,panel);
            require(finalAlpha.containsKey(panel)&&Math.abs(finalAlpha.get(panel)-desired)<.0001f,"Native final draw alpha differs from producer "+desired+": "+finalAlpha.get(panel));
            // Native final drawing mutates object alpha. Compare the addon's
            // producer from the identical pre-render state, not that mutated state.
            panel.setAlphaAndTarget(0,0);
            Frame frame=new Frame();frame.camZ=0;frame.scene.models.add(null,null,null,new org.joml.Matrix4f(),-1,-1,0,ModelDraws.COLOUR|ModelDraws.SHADOW);
            IsometricVisibility.applyModels(frame,forced,panel,0);
            boolean rendered=(frame.scene.models.flags(0)&ModelDraws.COLOUR)!=0;
            require(rendered==(baseline||desired>0),"Native final alpha="+desired+" but VP source colour="+rendered);
            require((frame.scene.models.flags(0)&ModelDraws.SHADOW)!=0,"Alpha hide changed physical shadow eligibility");cases++;
            System.out.println("Native final consumer: window target="+desired+", actual final draw alpha="+finalAlpha.get(panel)+", VP colour="+rendered);
        }
    }
    private static Object fresh(String name)throws Exception {Constructor<?> c=GameAccess.type(name).getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
