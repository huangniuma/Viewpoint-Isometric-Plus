import java.lang.reflect.*;
import java.lang.invoke.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.render.*;
import viewpoint.platform.*;
import vpiso.*;
import zombie.characters.*;
import zombie.characters.animals.IsoAnimal;
import zombie.iso.*;

/** Native entity alpha -> actual woven eligibility/flag -> real VP model color and sun maps. */
public final class CharacterVisibility094Probe {
 static final Class<?>[] NONE={};static ThreadLocal<Object> self;static ThreadLocal<Boolean> shadow;
 static final Method RANGE,FLAG;static final MethodHandle STEP;static int cases;static boolean baseline;
 static {
  try {
   self=(ThreadLocal<Object>)GameAccess.field("vpiso.IsometricBridge","SELF",null);
   shadow=(ThreadLocal<Boolean>)GameAccess.field("vpiso.IsometricBridge","NATIVE_SHADOW_CAPTURE",null);
   Class<?> c=GameAccess.type("viewpoint.models.Characters");
   RANGE=c.getDeclaredMethod("inRange",IsoMovingObject.class,float.class,float.class,float.class);RANGE.setAccessible(true);
   FLAG=c.getDeclaredMethod("flag",Frame.class,GameAccess.type("zombie.core.skinnedmodel.model.ModelSlotRenderData"),IsoGameCharacter.class,IsoGameCharacter.class,int.class);FLAG.setAccessible(true);
   STEP=MethodHandles.privateLookupIn(IsoGameCharacter.class,MethodHandles.lookup()).findSpecial(IsoObject.class,"updateAlpha",MethodType.methodType(void.class,int.class,float.class,float.class),IsoGameCharacter.class);
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 public static class Observer extends IsoPlayer {
  int checks;boolean sees;public Observer(){super((IsoCell)null);}
  @Override public boolean checkCanSeeClient(IsoPlayer subject){checks++;return sees;}
 }
 public static void main(String[] args)throws Exception {
  baseline=args.length>0&&args[0].equals("baseline093");
  String pack=baseline?"default":args[0];cpu();
  int cpuCases=cases;
  if(baseline){System.out.println("Previous093 character visibility negative PASS: invisible native caster retained; 3D fading subject rejected.");return;}
  require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long window=GLFW.glfwCreateWindow(192,192,"characters094",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
   TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off","antialiasing","off","renderScale","100","bloom","false","clouds","false","volumetrics","false"));
   NativeSurfaceReceiverProbe.fixture();
   for(Class<?> kind:new Class<?>[]{IsoZombie.class,IsoAnimal.class})for(float yaw:new float[]{0,37,90,180})gpu(kind,yaw);
   require(GL11.glGetError()==0,"GL error");
   System.out.println("Character094 GPU PASS: pack="+pack+" cases="+(cases-cpuCases)+"; cpuCases="+cpuCases+"; actual skinned model color and sun-map fade, zombie/animal types, four rotations, native zero, preserved look fields and original VP passthrough.");
  }finally{self.remove();shadow.remove();IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
 static Object allocate(Class<?> c)throws Exception{return NativeSurfaceReceiverProbe.allocate(c);}
 static <T extends IsoGameCharacter>T actor(Class<T> type,float alpha)throws Exception {
  T actor=type.cast(allocate(type));
  GameAccess.set("zombie.iso.IsoObject","alpha",actor,new float[]{alpha,alpha,alpha,alpha});
  GameAccess.set("zombie.iso.IsoObject","targetAlpha",actor,new float[]{0,0,0,0});actor.neverDoneAlpha=false;
  IsoGridSquare square=(IsoGridSquare)allocate(IsoGridSquare.class);
  Class<?> lighting=GameAccess.type("zombie.iso.IsoGridSquare$ILighting");
  Object light=Proxy.newProxyInstance(lighting.getClassLoader(),new Class<?>[]{lighting},(p,m,a)->{
   if(m.getName().equals("bCanSee"))return false;throw new AssertionError("Unexpected lighting call "+m);
  });
  Object lights=Array.newInstance(lighting,4);for(int i=0;i<4;i++)Array.set(lights,i,light);
  GameAccess.set("zombie.iso.IsoGridSquare","lighting",square,lights);
  GameAccess.set("zombie.iso.IsoMovingObject","current",actor,square);GameAccess.set("zombie.iso.IsoObject","square",actor,square);
  return actor;
 }
 static boolean eligible(Object actor)throws Exception{return (boolean)RANGE.invoke(null,actor,0f,0f,64f);}
 static void cpu()throws Exception {
  zombie.core.random.RandStandard.INSTANCE.init();
  Observer observer=actor(Observer.class,1);GameAccess.set("zombie.characters.IsoPlayer","spottedPlayerTimer",observer,new HashMap<Integer,Integer>());
  for(Class<? extends IsoGameCharacter> kind:List.of(IsoZombie.class,IsoAnimal.class)) {
   IsoGameCharacter actor=actor(kind,.6f);
   self.set(observer);shadow.set(false);
   require(eligible(actor)!=baseline,"fading entity abruptly rejected "+kind);cases++;
   if(baseline) {
    self.remove();shadow.set(true);actor.setAlpha(0,0);require(eligible(actor),"093 missing native scope not reproduced");shadow.remove();continue;
   }
   for(float start:new float[]{1,.6f,.1f,0})for(float target:new float[]{0,1}) {
    actor.setAlphaAndTarget(0,start);actor.setTargetAlpha(0,target);
    float before=start;
    for(int i=0;i<9;i++) {
     // Step only the original native routine, as simulation does; capture itself must never advance it.
     try{STEP.invoke(actor,0,.25f,14f);}catch(Throwable error){throw new AssertionError("native ordinary alpha step",error);}
     float current=actor.getAlpha(0);require(target==0?current<=before:current>=before,"native sequence direction");before=current;
     for(boolean mapOnly:new boolean[]{false,true}) {
      shadow.set(mapOnly);float stored=actor.getAlpha(0),wanted=actor.getTargetAlpha(0);
      boolean in=eligible(actor);require(in==(stored>.001f),"eligibility differs from native current alpha");
      if(stored>.001f)near(IsometricBridge.characterAlpha(actor),stored,"current alpha");
      require(actor.getAlpha(0)==stored&&actor.getTargetAlpha(0)==wanted,"capture changed native transition");
     }
     cases++;
    }
   }
   actor.setAlpha(0,.0005f);require(!eligible(actor),"native .001 threshold");
   actor.setAlpha(0,1);GameAccess.set("zombie.iso.IsoMovingObject","current",actor,null);require(!eligible(actor),"unloaded entity retained");
   self.remove();require(eligible(actor),"ordinary VP inherited isometric filter");shadow.remove();
  }
  if(!baseline) {
   self.set(observer);shadow.set(true);
   GameAccess.set("vpiso.IsometricBridge","captureFit",null,null);GameAccess.set("vpiso.IsometricBridge","captureRadius",null,96f);
   require(IsometricBridge.inView(80,0,0)&&!IsometricBridge.inView(1000,0,0),"native shadow range changed with observer scope");
   GameAccess.set("vpiso.IsometricBridge","captureFit",null,new IsoCameraMath.Fit(0,0,1));
   GameAccess.set("vpiso.IsometricBridge","captureWidth",null,192);GameAccess.set("vpiso.IsometricBridge","captureHeight",null,192);
   require(IsometricBridge.inView(80,0,0)&&!IsometricBridge.inView(1000,0,0),"old 3D camera cropped native shadow capture");
   shadow.set(false);require(!IsometricBridge.inView(80,0,0),"model frustum unexpectedly broadened");cases+=3;
   GameAccess.set("vpiso.IsometricBridge","captureFit",null,null);
   self.set(observer);observer.setAlpha(0,0);require(eligible(observer),"self discarded");
   Object car=allocate(GameAccess.type("zombie.vehicles.BaseVehicle"));require(eligible(car),"vehicle changed");
   IsoPlayer remote=actor(IsoPlayer.class,.5f);HashMap<Integer,Integer> timers=(HashMap<Integer,Integer>)GameAccess.field("zombie.characters.IsoPlayer","spottedPlayerTimer",observer);timers.put(remote.getRemoteID(),3);
   boolean client=(Boolean)GameAccess.field("zombie.network.GameClient","client",null);GameAccess.set("zombie.network.GameClient","client",null,true);
   try {
    shadow.set(false);observer.sees=false;int count=observer.checks;require(eligible(remote),"remote fade cut off");
    require(observer.checks==count+1&&remote.getTargetAlpha(0)==0&&!timers.containsKey(remote.getRemoteID()),"native client hidden decision");
    observer.sees=true;eligible(remote);require(remote.getTargetAlpha(0)==1,"native client reveal decision");
    int called=observer.checks;shadow.set(true);eligible(remote);require(observer.checks==called,"native rendering target refreshed twice");
    shadow.set(false);eligible(actor(IsoAnimal.class,.5f));require(observer.checks==called,"animal treated as remote player");cases+=4;
   }finally{GameAccess.set("zombie.network.GameClient","client",null,client);}
   self.remove();shadow.remove();System.out.println("Character094 native alpha/eligibility PASS: simulation fade retained, no extra alpha steps, self/vehicle/unloaded/optional scope and client-player target rules.");
  }
 }
 static void gpu(Class<?> type,float yaw)throws Exception {
  Frame f=NativeSurfaceReceiverProbe.frame();f.scene.fogEnd=10000;f.scene.fogStart=9000;f.scene.sunStrength=1;
  IsoGameCharacter actor=actor(type.asSubclass(IsoGameCharacter.class),1);Observer observer=actor(Observer.class,1);
  NativeSurfaceReceiverProbe.addModel(f.scene,NativeSurfaceReceiverProbe.vertical(.9f,2.5f),true,false);
  NativeSurfaceReceiverProbe.addModel(f.scene,NativeSurfaceReceiverProbe.horizontal(1,.6f),true,false);
  float[] values=(float[])GameAccess.field("viewpoint.render.ModelDraws","values",f.scene.models);float[] original=values.clone();
  Object snapshot=allocate(GameAccess.type("zombie.core.skinnedmodel.model.ModelSlotRenderData"));
  int[] color=new int[5],maps=new int[5];float[] alphas={1,.75f,.4f,.1f,0};
  for(int step=0;step<alphas.length;step++) {
   System.arraycopy(original,0,values,0,original.length);actor.setAlpha(0,alphas[step]);self.set(observer);shadow.set(false);
   float look=values[58],walls=values[59];FLAG.invoke(null,f,snapshot,actor,observer,0);
   near(values[58],look,"look field overwritten");near(values[59],walls,"wall record overwritten");
   for(int i=0;i<2;i++)near(values[i*68+57],alphas[step]==1?2:alphas[step],"missing body part fade");
   self.remove();
   GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,NativeSurfaceReceiverProbe.fbo);GL11.glViewport(0,0,192,192);
   Matrix4f view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(0,0,3.5f),0,0,192,192,f.scene.projection,view,eye,yaw);
   f.scene.cameraCut=true;f.number++;
   IsometricBridge.modelWorldRendering(f,IsometricEffects.DEFAULT,false);
   require(WorldRenderer.begin(f.scene,f.number,1,view,eye[0],eye[1],eye[2]),"model world begin");
   WorldRenderer.drawTranslucent(f.scene);
   Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
   int w=((Number)GameAccess.field("viewpoint.render.Targets","width",targets)).intValue(),h=((Number)GameAccess.field("viewpoint.render.Targets","height",targets)).intValue();
   GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,((Number)GameAccess.field("viewpoint.render.Targets","depth",targets)).intValue());float[] depth=new float[w*h];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
   for(float d:depth)if(d<.99999f)color[step]++;
   WorldRenderer.finish(f.scene);IsometricBridge.rendering(null);
   IsometricBridge.rendering(f);NativeShadowMaps.draw(f.scene,1024,2);IsometricBridge.rendering(null);
   GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());float[] shadowDepth=new float[1024*1024*3];GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,shadowDepth);
   for(int i=0;i<1024*1024;i++)if(shadowDepth[i]<.99999f)maps[step]++;
   cases++;
  }
  require(color[0]>100&&maps[0]>5,"empty real model fixture "+Arrays.toString(color)+Arrays.toString(maps));
  for(int i=1;i<5;i++){require(color[i]<color[i-1],"model fade not progressive "+Arrays.toString(color));require(maps[i]<maps[i-1],"shadow fade not progressive "+Arrays.toString(maps));}
  require(color[4]==0&&maps[4]==0,"invisible entity left color/shadow");
  System.arraycopy(original,0,values,0,original.length);values[56]=.2f;values[57]=.9f;values[58]=7;values[59]=19;
  actor.setAlpha(0,.5f);self.set(observer);FLAG.invoke(null,f,snapshot,actor,observer,0);self.remove();
  near(values[56],.2f,"lower fade bound changed");near(values[57],.55f,"existing fade not composed");near(values[58],7,"composited look lost");near(values[59],19,"wall clip lost");
  System.arraycopy(original,0,values,0,original.length);FLAG.invoke(null,f,snapshot,actor,observer,0);
  for(int i=0;i<original.length;i++)if(i%68==56||i%68==57)near(values[i],original[i],"VP passthrough changed fade");
  GameAccess.call("viewpoint.render.ModelDraws","reset",f.scene.models,NONE);
  IsometricBridge.rendering(f);NativeShadowMaps.draw(f.scene,1024,2);IsometricBridge.rendering(null);
  GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());
  float[] cleared=new float[1024*1024*3];GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,cleared);
  for(int i=0;i<1024*1024;i++)require(cleared[i]>=.99999f,"removed entity left a cached dynamic shadow");
  System.out.println(type.getSimpleName()+" yaw="+yaw+" color="+Arrays.toString(color)+" sun="+Arrays.toString(maps));
 }
 static void near(float a,float b,String m){require(Math.abs(a-b)<.00001f,m+" "+a+" != "+b);}static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
