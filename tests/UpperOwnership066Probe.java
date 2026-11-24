import java.lang.reflect.*;
import java.util.*;
import viewpoint.core.Frame;
import viewpoint.render.*;
import viewpoint.world.TileMesh;
import vpiso.*;
import zombie.core.textures.*;
import zombie.iso.*;

/** Exact shipped cap producer/cooker; no rendered scene, alpha manipulation, or fake mesh metadata. */
public final class UpperOwnership066Probe {
    static Object construct(String name, Class<?>[] signature, Object... args)throws Exception {
        Constructor<?> c=GameAccess.type(name).getDeclaredConstructor(signature);c.setAccessible(true);return c.newInstance(args);
    }
    static Object fixture(String name)throws Exception {
        Field f=UpperObjects064Probe.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }
    static boolean contains(Frame f,int vertex) {
        if((f.scene.meshFlags[0]&SceneData.VISIBLE)==0)return false;
        int start=f.scene.meshPlan[0];if(start<0)return true;
        for(int i=start;i<start+f.scene.meshPlanLength[0];i++)
            if(vertex>=f.scene.plan[i*3+1]&&vertex<f.scene.plan[i*3+1]+f.scene.plan[i*3+2])return true;
        return false;
    }
    static ChunkMeshData cook(boolean coarseOwned, Texture texture, TileMesh tile)throws Exception {
        Object recipe=construct("viewpoint.world.Recipe",new Class<?>[]{int.class},1);
        Object batch=construct("viewpoint.world.Recipe$Batch",new Class<?>[0]);
        ((List<Object>)GameAccess.field("viewpoint.world.Recipe","batches",recipe)).add(batch);
        Class<?> batchType=GameAccess.type("viewpoint.world.Recipe$Batch");
        for(int square:new int[]{0,1}) {
            GameAccess.set("viewpoint.world.WorldMesher","owner",null,coarseOwned?(square<<2|1):-1);
            GameAccess.set("viewpoint.world.WorldMesher","object",null,square<<16);
            // This is the exact cap emission entry used by WallMesher.wall for NW corners.
            GameAccess.call("viewpoint.world.WallMesher","emitCaps",null,
                new Class<?>[]{batchType,TileMesh.class,Texture.class,float.class,float.class,float.class},
                batch,tile,texture,(float)square,0f,2.4494896f);
        }
        return (ChunkMeshData)GameAccess.call("viewpoint.world.Cook","mesh",null,
            new Class<?>[]{GameAccess.type("viewpoint.world.Recipe")},recipe);
    }
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception {
        boolean baseline=Arrays.asList(args).contains("baseline065");
        UpperObjects064Probe.main(new String[]{"caps"});
        List<Object> collapsed=(List<Object>)fixture("collapsed");
        Object building=ModelCutawayProbe.building;
        collapsed.add(building);ModelCutawayProbe.camera(0);
        IsoGridSquare hidden=(IsoGridSquare)fixture("hidden"),visible=(IsoGridSquare)fixture("visible");
        Object cuts=fixture("cutaways");
        check(!(Boolean)GameAccess.call(cuts.getClass().getName(),"shouldRenderBuildingSquare",cuts,
            new Class<?>[]{int.class,IsoGridSquare.class},0,hidden),"Real native upper building gate must hide source");
        check((Boolean)GameAccess.call(cuts.getClass().getName(),"shouldRenderBuildingSquare",cuts,
            new Class<?>[]{int.class,IsoGridSquare.class},0,visible),"Native neighbour must remain visible");
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",UpperObjects064Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        Texture texture=(Texture)ModelCutawayProbe.allocate(Texture.class);
        Map<Texture,float[]> mappings=(Map<Texture,float[]>)GameAccess.field("viewpoint.world.WorldMesher","mappings",null);
        mappings.put(texture,new float[]{0,0,1,1,0,0,1,1,1,1,1});
        TileMesh tile=(TileMesh)construct("viewpoint.world.TileMesh",new Class<?>[]{float[].class},(Object)new float[]{
            0,1,0,0,0,0,1,0, 1,1,0,1,0,0,1,0, 1,1,1,1,1,0,1,0,
            0,1,0,0,0,0,1,0, 1,1,1,1,1,0,1,0, 0,1,1,0,1,0,1,0});
        try {
            for(boolean coarseOwned:new boolean[]{false,true}) {
                ChunkMeshData mesh=cook(coarseOwned,texture,tile);
                int[] spans=(int[])GameAccess.field(ChunkMeshData.class.getName(),"spans",mesh);
                int[] pageSpans=(int[])GameAccess.field(ChunkMeshData.class.getName(),"pageSpans",mesh);
                check((mesh.runs!=null)==coarseOwned&&(mesh.pageRuns!=null)==coarseOwned,"Unexpected actual Cook coarse ownership condition");
                check(spans!=null&&pageSpans!=null&&spans.length==6&&spans[0]==0&&spans[3]==65536,"Exact producer source spans absent");
                Frame frame=new Frame();frame.scene.addMesh(mesh,0,0,0,(byte)(SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW),0);
                IsometricVisibility.apply(frame,new Object(),0);
                boolean leak=contains(frame,spans[1]);
                check(leak==(baseline&&!coarseOwned),"Unexpected ownership result: coarseOwned="+coarseOwned+", hidden source retained="+leak);
                check(contains(frame,spans[4]),"Visible neighbour was cut");
                check((frame.scene.meshFlags[0]&SceneData.CASTS_SHADOW)!=0,"Physical shadow eligibility changed");
                System.out.println("ACTUAL COOK: coarseOwned="+coarseOwned+", runs="+Arrays.toString(mesh.runs)+", pageRuns="+Arrays.toString(mesh.pageRuns)+", spans="+Arrays.toString(spans)+", pageSpans="+Arrays.toString(pageSpans)+", nativeSourceVisible=false, nativeNeighbourVisible=true, hiddenSourceRetained="+leak+", visibleNeighbourRetained=true");
            }
            System.out.println((baseline?"Pinned065 exact ownership negative control PASS":"Native exact ownership regression066 PASS")+": actual WallMesher/WorldMesher/Cook output; native hidden source removed, visible neighbour and physical casters retained; no live map.");
        } finally {
            mappings.remove(texture);collapsed.remove(building);
            Visibility109Bindings.set(api,"square",previous);
        }
    }
}
