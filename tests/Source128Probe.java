import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import viewpoint.core.Frame;
import viewpoint.packs.PackBind;
import viewpoint.render.*;
import viewpoint.world.Recipe;
import vpiso.*;

/** Actual Recipe.model -> Cooker.models -> modelObjects -> native replacement decision. */
public final class Source128Probe {
 static int checks,cases;
 static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 static void need(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  boolean baseline=args[1].equals("baseline");Path assets=Path.of(args[0]);
  Method of=PackModels.class.getDeclaredMethod("of",java.io.File.class),publish=PackModels.class.getDeclaredMethod("publish");of.setAccessible(true);publish.setAccessible(true);
  PackModel tree=(PackModel)of.invoke(null,assets.resolve("v07/birch0_summer.obj").toFile());field(PackModel.class,"state").setInt(tree,PackModel.RESIDENT);publish.invoke(null);
  Field current=field(Class.forName("viewpoint.world.WorldMesher"),"object");int previous=current.getInt(null);
  try {
   for(boolean enabled:new boolean[]{false,true})for(int token:new int[]{-1,0,3,2<<16|3,63<<16|9}) {
    Main.compatible=enabled;current.setInt(null,token);
    Recipe recipe=new Recipe(0);Object op=Recipe.model(recipe,new PackBind(tree,0,0,0,0,1),true,2,0,0,2,134217728,0,0);
    int source=field(op.getClass(),"object").getInt(op);
    int expected=!baseline&&enabled?token:-1;
    need(source==expected,"source not carried from real WorldMesher: "+source+" expected="+expected);
    Class<?> type=Class.forName("viewpoint.world.Cook$Cooker");Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object cooker=ctor.newInstance();
    Method models=type.getDeclaredMethod("models",List.class);models.setAccessible(true);int[] runs=(int[])models.invoke(cooker,List.of(op));
    int[] owners=(int[])field(type,"modelObjects").get(cooker);
    if(expected<0)need(owners==null,"missing-source baseline unexpectedly had ownership");else need(owners.length==1&&owners[0]==token,"cook lost native object identity");
    ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[0],new int[0],new int[0],new float[0],0);
    mesh.models(0,runs);mesh.objects(null,null,owners);
    Frame frame=new Frame();frame.camX=100;frame.camY=200;frame.scene.meshCount=1;frame.scene.meshes[0]=mesh;frame.scene.meshFlags[0]=SceneData.VISIBLE;frame.scene.meshOffsets[0]=4;frame.scene.meshOffsets[2]=8;
    Set<NearVegetationCompat.Owner> actual=new HashSet<>();NearVegetationCompat.owners(frame,actual);
    Set<NearVegetationCompat.Owner> wanted=expected<0?Set.of():Set.of(new NearVegetationCompat.Owner(96+(token>>>16)%8,192+(token>>>16)/8,0,token&65535));
    need(actual.equals(wanted),"native fallback/replacement disagrees with emitted model: "+actual+" != "+wanted);
    cases++;
   }
   if(!baseline)nativeCapture(tree);
   System.out.println("SOURCE128 "+(baseline?"DUPLICATE127 REPRODUCED":"CANDIDATE PASS")+" cases="+cases+" checks="+checks+"; actual Recipe->Cooker->ChunkMeshData->ownership, no injected modelObjects or live attach");
  }finally{current.setInt(null,previous);}
 }
 static Object fixture(String name,Class<?>[] signature,Object...args)throws Exception {
  Method m=NativeFade127Probe.class.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(null,args);
 }
 static void nativeCapture(PackModel model)throws Exception {
  Class<?> unsafe=Class.forName("sun.misc.Unsafe");
  field(NativeFade127Probe.class,"unsafe").set(null,field(unsafe,"theUnsafe").get(null));
  field(NativeFade127Probe.class,"allocate").set(null,unsafe.getMethod("allocateInstance",Class.class));
  fixture("fixture",new Class<?>[0]);
  Object texture=fixture("texture",new Class<?>[]{String.class},"source128");
  Object sprite=fixture("sprite",new Class<?>[]{String.class,Object.class},"source128",texture);
  Object tree=fixture("tree",new Class<?>[]{Object.class},sprite);
  fixture("bridgeCapture",new Class<?>[]{Object.class},tree); // Existing real native world/lighting fixture.
  Object square=field(NativeFade127Probe.class,"square").get(null);
  int x=((zombie.iso.IsoGridSquare)square).x,y=((zombie.iso.IsoGridSquare)square).y;
  int token=((x&7)|(y&7)<<3)<<16;
  field(Class.forName("viewpoint.world.WorldMesher"),"object").setInt(null,token);
  Main.compatible=true;
  Object op=Recipe.model(new Recipe(0),new PackBind(model,0,0,0,0,1),true,2,0,4,34,134217728,0,0);
  Class<?> type=Class.forName("viewpoint.world.Cook$Cooker");Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object cooker=ctor.newInstance();
  Method models=type.getDeclaredMethod("models",List.class);models.setAccessible(true);int[] runs=(int[])models.invoke(cooker,List.of(op));
  ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[0],new int[0],new int[0],new float[0],0);
  mesh.models(0,runs);mesh.objects(null,null,(int[])field(type,"modelObjects").get(cooker));
  Frame frame=new Frame();frame.camX=16;frame.camY=16;frame.scene.meshCount=1;frame.scene.meshes[0]=mesh;frame.scene.meshFlags[0]=SceneData.VISIBLE;
  frame.scene.meshOffsets[0]=frame.camX-(x&~7);frame.scene.meshOffsets[2]=frame.camY-(y&~7);
  NativeTrees bridge=new NativeTrees();NativeObjectState state=new NativeObjectState();state.beginCapture(frame);
  try {
   Method sample=NativeObjectState.class.getDeclaredMethod("sample",Object.class,float.class);sample.setAccessible(true);sample.invoke(state,tree,1f);
   bridge.prepare(frame);bridge.add(square);
   bridge.capture(field(Class.forName("zombie.iso.fboRenderChunk.FBORenderCell"),"instance").get(null),0);
   int records=field(NativeTrees.class,"records").getInt(bridge);
   Set<?> excluded=(Set<?>)field(NativeTrees.class,"modelRecords").get(bridge);
   need(records>0&&excluded.size()==records,"actual native tree records were not suppressed by emitted replacement");
   need(((Map<?,?>)field(NativeObjectState.class,"crowns").get(state)).size()==1,"real source did not receive native crown fade");
   bridge.renderWorld(frame); // Must skip native colour draw; this process deliberately has no GL context.
   need(field(NativeTrees.class,"batch").get(bridge)!=null,"native records released before pool cleanup");
   System.out.println("SOURCE128 NATIVE CAPTURE PASS records="+records+" excluded="+excluded.size()+"; no duplicate native colour draw, crown source retained");
  }finally{bridge.release();NativeObjectState.endCapture();}
 }
}
