package vpiso;

import java.nio.file.Path;
import java.util.*;
import viewpoint.core.Frame;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.PackModel;
import viewpoint.render.PackModels;
import viewpoint.render.SceneData;

/** Optional PVNV ownership from actual resident model runs; no addon settings or assets are changed. */
public final class NearVegetationCompat {
    public static final int FLAG=1<<29;
    private static final Path ASSETS=assets();
    private record Owned(Set<Integer> sources,PackModel[] models) {
        boolean ready(){for(PackModel model:models)if(model.state()!=PackModel.RESIDENT)return false;return true;}
    }
    private static final Map<ChunkMeshData,Owned> OWNERS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<PackModel,Integer> KINDS=Collections.synchronizedMap(new WeakHashMap<>());
    public record Owner(int x,int y,int z,int object) {}
    private static Path assets() {
        try {
            Class<?> type=Class.forName("pz.viewpointvegetation.Vegetation",false,NearVegetationCompat.class.getClassLoader());
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent().getParent()
                    .resolve("nearvegetation").toAbsolutePath().normalize();
        } catch(ClassNotFoundException absent){return null;}
        catch(java.net.URISyntaxException bad){throw new IllegalStateException("Invalid PVNV artifact location",bad);}
    }
    public static int kind(PackModel model) {
        if(ASSETS==null || model==null)return 0;
        return KINDS.computeIfAbsent(model,m->{
            Path file=m.obj.toPath().toAbsolutePath().normalize();
            if(!file.startsWith(ASSETS) || !file.toString().endsWith(".obj"))return 0;
            return file.startsWith(ASSETS.resolve("v07"))?1:file.startsWith(ASSETS.resolve("falling_leaves"))?2:0;
        });
    }
    /** PVNV calls Recipe.model directly, bypassing PackGather.place's native source assignment. */
    public static void source(viewpoint.packs.PackBind bind,Object op) {
        if(bind==null || op==null || kind(bind.model)==0)return;
        if((Integer)GameAccess.field("viewpoint.world.Recipe$Op","object",op)>=0)return;
        int object=(Integer)GameAccess.field("viewpoint.world.WorldMesher","object",null);
        if(object>=0)GameAccess.set("viewpoint.world.Recipe$Op","object",op,object);
    }
    private static Set<Integer> owners(ChunkMeshData mesh) {
        Owned cached=OWNERS.get(mesh);
        if(cached!=null){if(cached.ready())return cached.sources;OWNERS.remove(mesh);}
            int[] runs=(int[])GameAccess.field("viewpoint.render.ChunkMeshData","modelRuns",mesh);
            int[] objects=(int[])GameAccess.field("viewpoint.render.ChunkMeshData","modelObjects",mesh);
            Set<Integer> result=new HashSet<>();
            List<PackModel> models=new ArrayList<>();
            boolean complete=true;
            if(runs!=null && objects!=null)for(int r=0;r<runs.length;r+=3) {
                PackModel model=PackModels.get(runs[r]);
                if(model==null){complete=false;continue;}
                if(kind(model)!=1)continue;
                if(model.state()!=PackModel.RESIDENT){complete=false;continue;}
                models.add(model);
                int end=Math.min(objects.length,runs[r+1]+runs[r+2]);
                for(int i=runs[r+1];i<end;i++)if(objects[i]>=0)result.add(objects[i]);
            }
            // A newly cooked model may precede PackModels.publish. Retry that
            // frame instead of caching a permanent false fallback decision.
            if(complete)OWNERS.put(mesh,new Owned(result,models.toArray(PackModel[]::new)));
            return result;
    }
    public static void owners(Frame frame,Set<Owner> destination) {
        destination.clear();if(ASSETS==null)return;
        SceneData scene=frame.scene;
        for(int i=0;i<scene.meshCount;i++)if((scene.meshFlags[i]&SceneData.VISIBLE)!=0) {
            ChunkMeshData mesh=scene.meshes[i];
            int x=Math.round(frame.camX-scene.meshOffsets[i*3]),y=Math.round(frame.camY-scene.meshOffsets[i*3+2]);
            for(int token:owners(mesh))if(token>>>16<64)
                destination.add(new Owner(x+(token>>>16)%8,y+(token>>>16)/8,mesh.level,token&65535));
        }
    }
    public static Owner owner(Object tree,Object square) {
        zombie.iso.IsoGridSquare s=(zombie.iso.IsoGridSquare)square;
        return new Owner(s.x,s.y,s.z,s.getObjects().indexOf(tree));
    }
    private NearVegetationCompat() {}
}
