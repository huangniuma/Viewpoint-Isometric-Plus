import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
import vpiso.*;import viewpoint.core.Frame;import viewpoint.render.*;import viewpoint.packs.PackBind;
public final class PVNV127CpuProbe {
 static int checks;static void need(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static PackModel model(int id,Path file)throws Exception{Constructor<PackModel> c=PackModel.class.getDeclaredConstructor(int.class,java.io.File.class,int.class);c.setAccessible(true);return c.newInstance(id,file.toFile(),0);}
 public static void main(String[] args)throws Exception{
  Path assets=Path.of(args[0]);PackModel tree=model(70,assets.resolve("v07/birch0_summer.obj")),dense=model(71,assets.resolve("v07/cluster_high/birch0_summer.obj")),leaf=model(72,assets.resolve("falling_leaves/autumn_leaves.obj")),foreign=model(73,assets.getParent().resolve("other/v07/birch0_summer.obj"));
  need(NearVegetationCompat.kind(tree)==1&&NearVegetationCompat.kind(dense)==1,"tree ownership");need(NearVegetationCompat.kind(leaf)==2,"falling leaves ownership");need(NearVegetationCompat.kind(foreign)==0,"foreign model matched");
  for(int incoming:new int[]{0,134217728,268435456,16384}) {
   int t=TreeModelScope.flags(incoming,new PackBind(tree,0,0,0,0,1)),l=TreeModelScope.flags(incoming,new PackBind(leaf,0,0,0,0,1));
   need((t&TreeModelScope.TREE)!=0&&(t&NearVegetationCompat.FLAG)!=0,"tree model not tagged");need((t&incoming)==incoming,"provider flags changed");need(l==incoming,"falling leaves inherited native tree tag");
  }
  need((TreeModelScope.TREE&(134217728|268435456))==0&&(TreeModelScope.BUSH&(134217728|268435456))==0,"flag collision remains");
  // Real public provider initialization must still accept its own flags/shader ABI.
  pz.viewpointvegetation.ShaderOptimization.initialize();pz.viewpointvegetation.TreeWind.initialize();pz.viewpointvegetation.TreeSnow.initialize();
  need(pz.viewpointvegetation.ShaderOptimization.treeFlags()==134217728,"PVNV optimization disabled");
  String raw=pz.viewpointvegetation.ShaderOptimization.resource("lib/pack_model.glsl");String wind=pz.viewpointvegetation.TreeWind.onSource("lib/pack_model.glsl",raw);need(wind.contains("uPVNVWindWave"),"provider wind hook lost");
  String sampling=pz.viewpointvegetation.AlphaSampling.transform(pz.viewpointvegetation.ShaderOptimization.resource("lib/sample_sprite_body.glsl"));need(sampling.contains("PVNV: gradients"),"provider alpha path lost");
  // Simulate cached published packed models and verify frame ownership, reload and shadow-only isolation.
  Method publish=PackModels.class.getDeclaredMethod("publish");publish.setAccessible(true);
  Method register=PackModels.class.getDeclaredMethod("of",java.io.File.class);register.setAccessible(true);
  PackModel resident=(PackModel)register.invoke(null,tree.obj);field(PackModel.class,"state").setInt(resident,PackModel.RESIDENT);publish.invoke(null);
  ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[0],new int[0],new int[0],new float[0],0);
  field(ChunkMeshData.class,"modelRuns").set(mesh,new int[]{resident.index,0,1});field(ChunkMeshData.class,"modelObjects").set(mesh,new int[]{(2<<16)|3});
  Frame f=new Frame();f.camX=100;f.camY=200;f.scene.meshCount=1;f.scene.meshes[0]=mesh;f.scene.meshFlags[0]=SceneData.VISIBLE;f.scene.meshOffsets[0]=4;f.scene.meshOffsets[2]=8;
  Set<NearVegetationCompat.Owner> own=new HashSet<>();NearVegetationCompat.owners(f,own);need(own.equals(Set.of(new NearVegetationCompat.Owner(98,192,0,3))),"actual run ownership "+own);
  f.scene.meshFlags[0]=SceneData.CASTS_SHADOW;NearVegetationCompat.owners(f,own);need(own.isEmpty(),"shadow-only model suppresses native picture");
  PackModel pending=(PackModel)register.invoke(null,assets.resolve("v07/oak2_summer.obj").toFile());
  ChunkMeshData late=new ChunkMeshData(new zombie.core.textures.TextureID[0],new int[0],new int[0],new float[0],0);
  field(ChunkMeshData.class,"modelRuns").set(late,new int[]{pending.index,0,1});field(ChunkMeshData.class,"modelObjects").set(late,new int[]{(2<<16)|3});f.scene.meshes[0]=late;f.scene.meshFlags[0]=SceneData.VISIBLE;
  NearVegetationCompat.owners(f,own);need(own.isEmpty(),"unpublished replacement hides native fallback");publish.invoke(null);NearVegetationCompat.owners(f,own);need(own.isEmpty(),"unready replacement hides native fallback");field(PackModel.class,"state").setInt(pending,PackModel.RESIDENT);NearVegetationCompat.owners(f,own);need(own.size()==1,"unpublished false result persisted in cache");
  field(PackModel.class,"state").setInt(pending,PackModel.UNLOADED);NearVegetationCompat.owners(f,own);need(own.isEmpty(),"released model leaves native picture hidden");
  System.out.println("PVNV127 CPU PASS checks="+checks+"; actual provider flags/wind/alpha, actual PackModels and resident run ownership; no game attach, settings or assets changed");
 }
}
