import java.lang.reflect.*;
import java.util.*;
import vpiso.*;
import viewpoint.core.*;
import viewpoint.render.MousePick;
import zombie.iso.*;
import zombie.characters.IsoPlayer;
/** Network client flags and real streamed square lookup; no server/socket/world launch. */
public final class Session087Probe {
 static int cases;static boolean baseline=Boolean.getBoolean("session087.baseline");
 public static void main(String[] args)throws Exception{
  zombie.core.random.RandStandard.INSTANCE.init();
  Main.compatible=true;View.enabled=false;GameAccess.set("vpiso.IsometricBridge","enabled",null,true);GameAccess.set("vpiso.IsometricBridge","modelsEnabled",null,true);GameAccess.set("vpiso.IsometricBridge","submitted",null,true);
  IsoGridSquare square=(IsoGridSquare)Interaction084Probe.UNSAFE.allocateInstance(IsoGridSquare.class);square.x=square.y=0;
  IsoObject object=new IsoObject(),fallback=new IsoObject();GameAccess.set("zombie.iso.IsoObject","square",object,square);ObjectState084GpuProbe.currentWorld(square,object);
  Object world=GameAccess.field("zombie.iso.IsoWorld","instance",null),cell=GameAccess.field("zombie.iso.IsoWorld","currentCell",world);
  Class<?> type=Class.forName("vpiso.NativeObjectState$Pick");Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);int[] pixels=new int[97*97];Arrays.fill(pixels,(12345<<1)|1);BitSet assist=new BitSet();assist.set(1);MousePick.ask=new MousePick.Ask(64,64,128,128);
  Object snapshot=ctor.newInstance(MousePick.ask,object,0f,System.nanoTime(),GameAccess.field("vpiso.NativeObjectState","pickGeneration",null),pixels,Arrays.asList(null,object),assist,1);
  GameAccess.set("vpiso.NativeObjectState","latest",null,snapshot);require(NativeObjectState.picked(fallback)==object,"Live loaded source rejected");cases++;
  square.getObjects().clear();Object removed=NativeObjectState.picked(fallback);
  if(baseline){require(removed==object,"Baseline changed");System.out.println("Previous086 negative control PASS: removed network/streamed object is still returned.");return;}
  require(removed==fallback,"Removed object remained selectable");require(NativeObjectState.picked(object)==null,"Stale VP fallback bypassed source validation");cases+=2;
  square.getObjects().add(object);ObjectState084GpuProbe.currentWorld((IsoGridSquare)Interaction084Probe.UNSAFE.allocateInstance(IsoGridSquare.class));
  require(NativeObjectState.picked(fallback)==fallback,"Old world object accepted at same coordinates");cases++;
  ObjectState084GpuProbe.currentWorld(square,object);world=GameAccess.field("zombie.iso.IsoWorld","instance",null);cell=GameAccess.field("zombie.iso.IsoWorld","currentCell",world);
  Object local=Interaction084Probe.UNSAFE.allocateInstance(IsoPlayer.class),remote=Interaction084Probe.UNSAFE.allocateInstance(IsoPlayer.class);Object[] players=(Object[])GameAccess.field("zombie.characters.IsoPlayer","players",null);Arrays.fill(players,null);players[0]=local;
  GameAccess.set("zombie.network.GameClient","client",null,true);
  IsometricBridge.afterWorld(cell,true);GameAccess.set("vpiso.IsometricBridge","failed",null,true);GameAccess.set("vpiso.IsometricBridge","groundPrepared",null,true);
  // A stable session must not continuously recover a real renderer error.
  IsometricBridge.afterWorld(cell,true);require((Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null),"Stable session reset failure every frame");cases++;
  players[0]=remote;IsometricBridge.afterWorld(cell,true);require(!(Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null),"Replacement local player did not recover new session");require(!(Boolean)GameAccess.field("vpiso.IsometricBridge","groundPrepared",null),"New session kept old native chunk readiness");require(GameAccess.field("vpiso.NativeObjectState","latest",null)==null,"New session retained click history");cases+=3;
  players[0]=local;IsometricBridge.afterWorld(cell,true);RotationCamera.configureYaw(true,137);require(RotationCamera.yawDegrees()==137,"Network client unnecessarily disabled rotation");
  boolean prior=RotationInteraction.enterPlayer(remote);require(!(Boolean)GameAccess.field("vpiso.RotationInteraction","PLAYER_SCOPE",null).getClass().getMethod("get").invoke(GameAccess.field("vpiso.RotationInteraction","PLAYER_SCOPE",null)),"Remote player's input was captured");RotationInteraction.restorePlayer(prior);
  prior=RotationInteraction.enterPlayer(local);require((Boolean)((ThreadLocal<?>)GameAccess.field("vpiso.RotationInteraction","PLAYER_SCOPE",null)).get(),"Local player's input lost");RotationInteraction.restorePlayer(prior);cases+=2;
  Object[] localSlots={local,null,null,null};require(SplitScreen.count(localSlots)==1,"Online client mistaken for local split screen");
  localSlots[1]=remote;require(SplitScreen.count(localSlots)==2,"Local split screen guard changed");cases+=2;
  GameAccess.set("zombie.network.GameClient","client",null,false);
  System.out.println("Session087 PASS: cases="+cases+"; loaded/removed/replaced square identity, stale VP fallback, stable-vs-new session error recovery, local/remote input, network-client mode and local split-screen boundary; no sockets or real multiplayer session.");
 }
 static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
}
