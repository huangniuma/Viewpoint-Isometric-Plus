import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import viewpoint.core.Frame;
import viewpoint.packs.PackBind;
import viewpoint.platform.GlProgram;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.*;
import zombie.iso.objects.IsoWindow;
import zombie.iso.sprite.IsoSprite;

/** Native hidden source vs visible opposite side through actual packed wall producer and collector. */
public final class PackedSource066Probe {
    private static final String DRAW="viewpoint.render.PackDraws";
    private static final Path DGV=Path.of("D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3820833348/mods/DGVWindows3D/42/media/dgv_windows_3d_storefronts");
    private static int cases;
    public static final class Window extends IsoWindow {
        Window() {super(null);}
        @Override public IsoGridSquare getOppositeSquare() {
            IsoGridSquare other=square.getAdjacentSquare(isNorth()?IsoDirections.N:IsoDirections.W);
            return other==null?square:other;
        }
    }
    public static void main(String[] args)throws Exception {
        boolean baseline=Arrays.asList(args).contains("baseline065");
        boolean sameFloor=Arrays.asList(args).contains("same075");
        boolean previous074=Arrays.asList(args).contains("baseline074");
        UpperObjects064Probe.main(new String[]{"caps"});
        Object cuts=GameAccess.staticCall("zombie.iso.fboRenderChunk.FBORenderCutaways","getInstance");
        Object per=((Object[])GameAccess.field(cuts.getClass().getName(),"perPlayerData",cuts))[0];
        Object btc=GameAccess.field(per.getClass().getName(),"buildingsToCollapse",per);
        @SuppressWarnings("unchecked") List<Object> collapsed=(List<Object>)GameAccess.field(btc.getClass().getName(),"buildingsToCollapse",btc);
        collapsed.add(ModelCutawayProbe.building);
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object lookup=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",UpperObjects064Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        String shader;
        try(var in=ClassLoader.getSystemResourceAsStream("viewpoint/shaders/lib/pack_model.glsl")) {shader=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        require(shader.contains("squareHidden(square) && (across < 0 || squareHidden(across))"),"Shipped dual-square consumer changed");
        try {
            for(boolean north:new boolean[]{false,true}) {
                String tile="walls_commercial_01_"+(north?65:64);
                String manifest=Files.readString(DGV.resolve("package.properties"));
                require(manifest.contains("model."+tile+"="+tile+".obj"),"Actual replacement mapping missing");
                String binding=Arrays.stream(manifest.split("\\R")).filter(s->s.startsWith("bind."+tile+"=")).findFirst().orElseThrow();
                String[] values=binding.substring(binding.indexOf('=')+1).split(",");
                PackModel model=PackModels.of(DGV.resolve(tile+".obj").toFile(),0);
                PackModels.publish();GameAccess.set(PackModel.class.getName(),"state",model,3);
                GameAccess.set(PackModel.class.getName(),"indices",model,6);
                PackBind bind=new PackBind(model,Float.parseFloat(values[1]),Float.parseFloat(values[2]),Float.parseFloat(values[3]),Float.parseFloat(values[4]),Float.parseFloat(values[5]));
                IsoGridSquare source=square(1,1,!sameFloor),outside=square(north?1:0,north?0:1,false),neighbour=square(2,1,false);
                IsoSprite sprite=new IsoSprite();sprite.setName(tile);
                sprite.getProperties().set(north?"WindowN":"WindowW","");
                Window hidden=window(source,sprite,north),visible=window(neighbour,sprite,north);
                if(sameFloor)hidden.setDoRender(false);
                source.setAdjacentSquare(north?IsoDirections.N:IsoDirections.W,outside);
                List<Object> recipe=new ArrayList<>();
                Constructor<?> recipeCtor=GameAccess.type("viewpoint.world.Recipe").getDeclaredConstructor(int.class);recipeCtor.setAccessible(true);
                GameAccess.set("viewpoint.world.PackGather","recipe",null,recipeCtor.newInstance(1));
                for(int exact:new int[]{9<<16,9<<16,10<<16,-1}) {
                    GameAccess.set("viewpoint.world.WorldMesher","object",null,exact);
                    IsoGridSquare at=exact==(10<<16)?neighbour:source;
                    Object op=GameAccess.call("viewpoint.world.PackGather","place",null,
                        new Class<?>[]{IsoObject.class,PackBind.class,IsoSprite.class,float.class,float.class,float.class,int.class,int.class},
                        at==source?hidden:visible,bind,sprite,at.x+.5f,at.y+.5f,2.4494896f,at.x+at.y*8,0);
                    int encoded=(Integer)GameAccess.field(op.getClass().getName(),"square",op);
                    if(at==source)require((encoded&127)==9&&(encoded>>7)-1==(north?1:8),"Actual across-square producer changed: "+encoded);
                    recipe.add(op);
                }
                Object cooker=fresh("viewpoint.world.Cook$Cooker");
                int[] runs=(int[])GameAccess.call(cooker.getClass().getName(),"models",cooker,new Class<?>[]{List.class},recipe);
                int[] owners=(int[])GameAccess.field(cooker.getClass().getName(),"modelObjects",cooker);
                float[] instances=((float[])GameAccess.field(cooker.getClass().getName(),"instances",cooker)).clone();
                ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[0],new int[0],new int[0],new float[0],1);
                mesh.models(0,runs);mesh.objects(null,null,owners);mesh.arenaFirst=200;
                int[] ownersBefore=owners.clone(),runsBefore=runs.clone();
                GlProgram program=program();
                for(float floor:new float[]{0,1,0}) {
                    ModelCutawayProbe.camera(floor);
                    Frame frame=new Frame();frame.camZ=floor;
                    frame.scene.addMesh(mesh,0,0,0,(byte)(SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW),0);
                    IsometricVisibility.apply(frame,new Object(),0);
                    long squares=frame.scene.meshModelSquares[0];
                    if(floor==0) {
                        require(((squares&(1L<<9))==0)==!sameFloor&&(squares&(1L<<(north?1:8)))!=0,"Need native hidden source/object beside visible exterior");
                        // This is the exact boolean in the retained shipped shader above.
                        require(!shaderHidden(Float.floatToRawIntBits(instances[7]),squares),"Pinned shader must retain hidden-source window through exterior");
                    }
                    @SuppressWarnings("unchecked") List<Object> cache=(List<Object>)GameAccess.field(DRAW,"collected",null);cache.clear();
                    for(int reuse=0;reuse<2;reuse++) {
                        draw(frame.scene,program,SceneData.VISIBLE,true);
                        Set<Integer> expected=new HashSet<>();
                        int first=(Integer)GameAccess.call(ChunkMeshData.class.getName(),"slot",mesh,new Class<?>[]{int.class},0);
                        for(int i=0;i<owners.length;i++)if(baseline||(!sameFloor&&floor>=1)||(sameFloor&&previous074&&floor>=1)||owners[i]!=(9<<16))expected.add(first+i);
                        require(slots().equals(expected),"Hidden-source packed colour leak: "+tile+" floor="+floor+" actual="+slots()+" expected="+expected);cases++;
                        draw(frame.scene,program,SceneData.CASTS_SHADOW,false);
                        require(slots().size()==owners.length,"Physical shadows clipped");cases++;
                    }
                    require(Arrays.equals(owners,ownersBefore)&&Arrays.equals(runs,runsBefore),"Shared packed mesh mutated");
                }
                System.out.println(tile+" actual DGV bind -> PackGather.place -> Cook.models: source9 hidden, opposite"+(north?1:8)+" visible; "+(baseline?"065 LEAK reproduced":"source instances REMOVED")+"; visible neighbour, unknown owner, shadows and upstairs restore retained.");
            }
        } finally {Visibility109Bindings.set(api,"square",lookup);collapsed.remove(ModelCutawayProbe.building);}
        System.out.println((baseline?"Pinned065 packed source negative control PASS: ":"Packed source regression066 PASS: ")+cases+" cases; actual producer/cooker/woven collector and cached commands. CPU fixture; native collapse input controlled, model GPU residency and issue intercepted; no live map visual acceptance.");
        if(sameFloor)System.out.println("Same-floor packed075 "+(previous074?"074 LEAK reproduced":"native None respected")+" PASS");
    }
    private static boolean shaderHidden(int encoded,long mask) {
        int own=encoded&127,across=(encoded>>7)-1;
        return own<64&&(mask&(1L<<own))==0&&(across<0||across<64&&(mask&(1L<<across))==0);
    }
    private static IsoGridSquare square(int x,int y,boolean hidden)throws Exception {
        Method m=UpperObjects064Probe.class.getDeclaredMethod("square",int.class,int.class,int.class,boolean.class);m.setAccessible(true);
        return (IsoGridSquare)m.invoke(null,x,y,1,hidden);
    }
    private static Window window(IsoGridSquare square,IsoSprite sprite,boolean north) {
        Window w=new Window();GameAccess.set(IsoObject.class.getName(),"square",w,square);GameAccess.set(IsoObject.class.getName(),"sprite",w,sprite);
        GameAccess.set(IsoWindow.class.getName(),"north",w,north);square.getObjects().add(w);return w;
    }
    private static GlProgram program()throws Exception {
        GlProgram p=(GlProgram)ModelCutawayProbe.allocate(GlProgram.class);Object u=fresh("viewpoint.platform.UniformLookups");
        @SuppressWarnings("unchecked") Map<String,Integer> locations=(Map<String,Integer>)GameAccess.field(u.getClass().getName(),"locations",u);
        locations.put("uModels",0);GameAccess.set(GlProgram.class.getName(),"uniforms",p,u);GameAccess.set(GlProgram.class.getName(),"id",p,1);
        GameAccess.set(PackModels.class.getName(),"tableTexture",null,1);return p;
    }
    private static void draw(SceneData scene,GlProgram program,byte flag,boolean planned) {
        GameAccess.call(DRAW,"draw",null,new Class<?>[]{SceneData.class,GlProgram.class,byte.class,int.class,boolean.class},scene,program,flag,10,planned);
    }
    private static Set<Integer> slots()throws Exception {
        Method m=PackedObjects063Probe.class.getDeclaredMethod("slots");m.setAccessible(true);return (Set<Integer>)m.invoke(null);
    }
    private static Object fresh(String name)throws Exception {Constructor<?> c=GameAccess.type(name).getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
