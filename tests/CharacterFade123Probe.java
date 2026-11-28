import java.lang.reflect.*;
import java.util.*;
import viewpoint.core.Frame;
import viewpoint.render.ModelDraws;
import viewpoint.render.MousePick;
import vpiso.*;
import zombie.characters.*;
import zombie.characters.animals.IsoAnimal;
import zombie.iso.*;

/** Native actor alpha -> tagged colour source table plus unchanged legacy shadow vFade. CPU only. */
public final class CharacterFade123Probe {
 static int checks,cases;static boolean baseline;
 static ThreadLocal<Object> self;static ThreadLocal<Boolean> shadow;
 static final Object U;static final Method ALLOC;
 static {try{Class<?> c=Class.forName("sun.misc.Unsafe");Field f=c.getDeclaredField("theUnsafe");f.setAccessible(true);U=f.get(null);ALLOC=c.getMethod("allocateInstance",Class.class);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 public static void main(String[] args)throws Exception {
  baseline=args.length>0&&args[0].equals("--baseline122");Main.compatible=true;
  self=(ThreadLocal<Object>)GameAccess.field("vpiso.IsometricBridge","SELF",null);shadow=(ThreadLocal<Boolean>)GameAccess.field("vpiso.IsometricBridge","NATIVE_SHADOW_CAPTURE",null);
  boolean api;
  try{NativeObjectState.class.getMethod("characterFade",Frame.class,Object.class,int.class,float.class);api=true;}catch(NoSuchMethodException absent){api=false;}
  require(api!=baseline,"expected "+(baseline?"122 legacy API absence":"123 owned actor API"));
  IsoGameCharacter observer=actor(IsoPlayer.class,1);
  try {
   for(Class<? extends IsoGameCharacter> kind:List.of(IsoZombie.class,IsoAnimal.class,IsoPlayer.class))
   for(float alpha:new float[]{0,.1f,.25f,.5f,1})for(float[] interval:new float[][]{{0,2},{.2f,.9f},{.5f,2}})
   for(String scope:List.of("owned","shadow","foreign","nonowned"))inspect(kind,observer,alpha,interval,scope);
   // The local camera character retains its full default visibility.
   Frame frame=frame(new float[]{0,2});NativeObjectState state=new NativeObjectState();
   MousePick.ask=null;state.beginCapture(frame);self.set(observer);shadow.set(false);observer.setAlpha(0,.25f);
   try{IsometricBridge.characterFade(frame,observer,1);require(modelSamples(state).isEmpty(),"self gained custom alpha");require(values(frame)[57]==2,"self upstream fade changed");}finally{NativeObjectState.endCapture();}
   System.out.println((baseline?"CHARACTER123 NEGATIVE122 PASS":"CHARACTER123 CPU PASS")+" cases="+cases+" checks="+checks+"; actual zombie/animal/player native alpha; "+(baseline?"122 stores owned visibility only in vFade, no tagged colour source API":"owned 3D tagged colour source alpha + low16 ID0 blockers; legacy vFade retained for physical shadows; look/wall intact")+"; native-shadow/nonowned/foreign legacy behavior, no extra alpha/target stepping; no GL/game attach");
  }finally{NativeObjectState.endCapture();MousePick.ask=null;self.remove();shadow.remove();}
 }
 static void inspect(Class<? extends IsoGameCharacter> kind,IsoGameCharacter observer,float alpha,float[] interval,String scope)throws Exception {
  IsoGameCharacter actor=actor(kind,alpha);Frame frame=frame(interval),foreign=new Frame();NativeObjectState state=new NativeObjectState();
  MousePick.ask=null;if(!scope.equals("nonowned"))state.beginCapture(scope.equals("foreign")?foreign:frame);
  self.set(observer);shadow.set(scope.equals("shadow"));
  // Query lies exactly on the actor: recording its native alpha must still keep prior ID0 blocker semantics.
  GameAccess.set("vpiso.NativeObjectState","ask",state,new MousePick.Ask(0,0,1920,1080));
  GameAccess.set("vpiso.NativeObjectState","pickX",state,0f);GameAccess.set("vpiso.NativeObjectState","pickY",state,0f);
  float[] before=values(frame).clone();float nativeBefore=actor.getAlpha(0),targetBefore=actor.getTargetAlpha(0);
  try {
   IsometricBridge.characterFade(frame,actor,1);
   Map<Integer,?> samples=modelSamples(state);
   boolean owned=scope.equals("owned")&&!baseline;
   String label=kind.getSimpleName()+" alpha="+alpha+" interval="+Arrays.toString(interval)+" scope="+scope;
   require(actor.getAlpha(0)==nativeBefore&&actor.getTargetAlpha(0)==targetBefore,"capture stepped native alpha "+label);
   for(int row=0;row<3;row++)for(int field=0;field<68;field++) {
    float wanted=before[row*68+field];
    if(alpha<1&&row>=1&&field==57){float lo=Math.max(0,Math.min(1,before[row*68+56])),hi=Math.max(lo,Math.min(1,wanted));wanted=lo+(hi-lo)*alpha;}
    require(values(frame)[row*68+field]==wanted,"record mutation row="+row+" field="+field+" "+label);
   }
   if(owned&&alpha<1) {
    require(samples.size()==2&&samples.containsKey(1)&&samples.containsKey(2)&&!samples.containsKey(0),"actor parts not table-routed "+label);
    for(Object sample:samples.values()) {
     Method a=sample.getClass().getDeclaredMethod("alpha"),id=sample.getClass().getDeclaredMethod("id");a.setAccessible(true);id.setAccessible(true);
     require((Float)a.invoke(sample)==alpha,"source alpha mismatch "+label);
     int encoded=(Integer)id.invoke(sample);require((encoded&65535)==0,"actor gained new pick ID "+label);require(encoded==0x20000000,"actor colour source tag changed "+label);
    }
    require(samples.get(1)==samples.get(2),"actor part source sample not shared "+label);
   } else require(samples.isEmpty(),"legacy/full-alpha capture gained source table entry "+label);
   cases++;
  }finally{NativeObjectState.endCapture();}
 }
 static Frame frame(float[] interval)throws Exception {
  Frame frame=new Frame();GameAccess.set("viewpoint.render.ModelDraws","count",frame.scene.models,3);float[] values=values(frame);
  for(int row=0;row<3;row++){for(int i=0;i<68;i++)values[row*68+i]=row*100+i;values[row*68+56]=interval[0];values[row*68+57]=interval[1];values[row*68+58]=7;values[row*68+59]=19;}
  return frame;
 }
 static float[] values(Frame f){return (float[])GameAccess.field("viewpoint.render.ModelDraws","values",f.scene.models);}
 static Map<Integer,?> modelSamples(NativeObjectState s){return (Map<Integer,?>)GameAccess.field("vpiso.NativeObjectState","models",s);}
 static <T extends IsoGameCharacter>T actor(Class<T> kind,float alpha)throws Exception {
  T actor=kind.cast(ALLOC.invoke(U,kind));
  GameAccess.set("zombie.iso.IsoObject","alpha",actor,new float[]{alpha,alpha,alpha,alpha});GameAccess.set("zombie.iso.IsoObject","targetAlpha",actor,new float[]{.75f,.75f,.75f,.75f});actor.neverDoneAlpha=false;
  IsoGridSquare square=(IsoGridSquare)ALLOC.invoke(U,IsoGridSquare.class);square.x=square.y=0;
  GameAccess.set("zombie.iso.IsoMovingObject","current",actor,square);GameAccess.set("zombie.iso.IsoObject","square",actor,square);return actor;
 }
 static void require(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
}
