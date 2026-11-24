import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.*;
import vpiso.*;
import viewpoint.core.View;
import viewpoint.render.MousePick;
import zombie.iso.*;
import zombie.ui.UIManager;
/** Actual woven UI click validation and unchanged original Lua action rules. */
public final class NativeClick086Probe {
 static int cases;static KahluaTable env;static KahluaThread lua;
 static class Source extends IsoObject {public int getObjectIndex(){return getSquare()==null?-1:0;}}
 public static void main(String[] args)throws Exception{
  Object world=Interaction084Probe.UNSAFE.allocateInstance(IsoWorld.class),cell=Interaction084Probe.UNSAFE.allocateInstance(IsoCell.class);
  GameAccess.set("zombie.iso.IsoWorld","currentCell",world,cell);GameAccess.set("zombie.iso.IsoWorld","instance",null,world);
  GameAccess.set("zombie.iso.IsoCell","drag",cell,new KahluaTable[4]);
  GameAccess.set("zombie.core.Core","width",null,128);GameAccess.set("zombie.core.Core","height",null,128);
  Main.compatible=true;View.enabled=false;IsometricBridge.setEnabled(true);IsometricBridge.setModelsEnabled(true);GameAccess.set("vpiso.IsometricBridge","submitted",null,true);
  Method check=UIManager.class.getDeclaredMethod("checkPicked");check.setAccessible(true);
  Source a=(Source)Interaction084Probe.UNSAFE.allocateInstance(Source.class),b=(Source)Interaction084Probe.UNSAFE.allocateInstance(Source.class);
  IsoGridSquare sq=(IsoGridSquare)Interaction084Probe.UNSAFE.allocateInstance(IsoGridSquare.class);GameAccess.set("zombie.iso.IsoObject","square",a,sq);GameAccess.set("zombie.iso.IsoObject","square",b,sq);
  ObjectState084GpuProbe.currentWorld(sq,a,b);
  Object selectionWorld=GameAccess.field("zombie.iso.IsoWorld","instance",null);Object selectionCell=GameAccess.field("zombie.iso.IsoWorld","currentCell",selectionWorld);
  GameAccess.set("zombie.iso.IsoCell","drag",selectionCell,new KahluaTable[4]);
  Class<?> pick=Class.forName("vpiso.NativeObjectState$Pick");Constructor<?> ctor=pick.getDeclaredConstructors()[0];ctor.setAccessible(true);
  var platform=J2SEPlatform.getInstance();env=platform.newEnvironment();lua=new KahluaThread(platform,env);
  run("fixtures",FIXTURE);run("original handler",Files.readString(Path.of(args[0],"media/lua/server/ISObjectClickHandler.lua")));
  for(float yaw:new float[]{0,45,137,270}){
   RotationCamera.configureYaw(true,yaw);require(RotationCamera.yawDegrees()==yaw,"Yaw did not activate");NativeObjectClick.reset();
   setMouse(64,64);IsoObjectPicker.ClickObject stale=new IsoObjectPicker.ClickObject();stale.tile=b;stale.square=sq;UIManager.setPicked(stale);
   int[] pixels=new int[97*97];Arrays.fill(pixels,(12345<<2)|1);BitSet assist=new BitSet();assist.set(1);
   Object snapshot=ctor.newInstance(new MousePick.Ask(50,64,128,128),a,yaw,System.nanoTime(),GameAccess.field("vpiso.NativeObjectState","pickGeneration",null),pixels,Arrays.asList(null,a,b),assist,2);
   GameAccess.set("vpiso.NativeObjectState","latest",null,snapshot);MousePick.hit=null;
   require((Boolean)check.invoke(null)&&UIManager.getPicked().tile==a,"Actual checkPicked dispatched old hover object");cases++;
   NativeObjectClick.event("OnObjectLeftMouseButtonDown",a,64d,64d,false);
   // Simulate a queued frame changing the hover while the native button is held.
   GameAccess.set("vpiso.NativeObjectState","latest",null,null);setMouse(68,65);UIManager.setPicked(stale);
   require((Boolean)check.invoke(null)&&UIManager.getPicked().tile==a,"Press/release target changed during small mouse movement");cases++;
   NativeObjectClick.event("OnObjectLeftMouseButtonUp",a,68d,65d,true);
   require(GameAccess.field("vpiso.NativeObjectClick","pressed",null)==null,"Release did not clear target");cases++;
   NativeObjectClick.event("OnObjectLeftMouseButtonDown",a,64d,64d,false);setMouse(90,64);check.invoke(null);
   require(GameAccess.field("vpiso.NativeObjectClick","pressed",null)==null,"Drag retained previous target");cases++;
   NativeObjectClick.event("OnObjectLeftMouseButtonDown",a,90d,64d,false);NativeObjectState.resetPick();
   require(GameAccess.field("vpiso.NativeObjectClick","pressed",null)==null,"Camera/mode reset retained press");cases++;
   NativeObjectClick.event("OnObjectLeftMouseButtonDown",a,90d,64d,false);((boolean[])GameAccess.field("zombie.input.Mouse","uiCaptured",null))[0]=true;check.invoke(null);
   require(GameAccess.field("vpiso.NativeObjectClick","pressed",null)==null,"UI capture retained world click");((boolean[])GameAccess.field("zombie.input.Mouse","uiCaptured",null))[0]=false;cases++;
  }
  run("original restrictions",CHECKS);
  System.out.println("Native click086 PASS: cases="+cases+"; actual woven checkPicked refresh, four yaw angles, press/release identity, drag/UI/camera cancellation; unchanged original Lua opens/closes door, selects container and preserves adjacency/wall/aim/cursor rules.");
 }
 static void setMouse(int x,int y){GameAccess.set("zombie.input.Mouse","x",null,x);GameAccess.set("zombie.input.Mouse","y",null,y);}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(String name,String source)throws Exception{lua.call(LuaCompiler.loadstring(source,name,env),new Object[0]);}
 static final String FIXTURE="""
 calls=0; selected=0; walk=0; aiming=false; adjacent=true; wall=false; blocked=false; paused=false
 function sq()return {getX=function()return 0 end,getY=function()return 0 end,getZ=function()return 0 end,isSeen=function()return true end,isWallTo=function()return wall end,isDoorTo=function()return false end,isBlockedTo=function()return blocked end,isSomethingTo=function()return false end}end
 square=sq(); actor={getCurrentSquare=function()return square end,isDead=function()return false end,isAlive=function()return true end,isAiming=function()return aiming end,isIgnoreContextKey=function()return false end,isClimbingLadder=function()return false end,isClimbingDownLadder=function()return false end}
 door={kind='IsoDoor',open=false,getSquare=function()return square end,isAdjacentToSquare=function()return adjacent end,HasCurtains=function()return false end,getSpriteConfig=function()return nil end,getContainer=function()return nil end}
 box={kind='IsoObject',getSquare=function()return square end,getSpriteConfig=function()return nil end,getContainer=function()return contents end,getContainerClickedOn=function()return contents end}
 contents={isExplored=function()return true end};panel={setNewContainer=function(_,c)selected=selected+1;assert(c==contents)end,setVisible=function()end}
 function getSpecificPlayer()return actor end
 function instanceof(o,t)return o and o.kind==t end
 function isKeyDown()return false end
 Keyboard={KEY_LSHIFT=42}
 function CancelAction()end
 function getTimestampMs()return 1000 end
 function getCell()return {getDrag=function()return nil end}end
 UIManager={getSpeedControls=function()return {getCurrentGameSpeed=function()return paused and 0 or 1 end}end,isDoubleClick=function()return false end}
 ISTimedActionQueue={addGetUpAndThen=function(_,action)calls=calls+1;action.target.open=not action.target.open end,clear=function()end,add=function()walk=walk+1 end}
 ISOpenCloseDoor={new=function(_,a,d)return {target=d}end}
 function getCore()return {isTutorial=function()return false end,getOptionAutoWalkContainer=function()return true end}end
 function isClient()return false end
 function getPlayerLoot()return panel end
 AdjacentFreeTileFinder={isTileOrAdjacent=function()return true end}
 Events={OnObjectLeftMouseButtonUp={Add=function()end},OnObjectLeftMouseButtonDown={Add=function()end},OnObjectRightMouseButtonUp={Add=function()end},OnObjectRightMouseButtonDown={Add=function()end}}
 """;
 static final String CHECKS="""
 ISObjectClickHandler.onObjectLeftMouseButtonDown(door,64,64);assert(door.open and calls==1)
 ISObjectClickHandler.onObjectLeftMouseButtonUp(door,64,64)
 ISObjectClickHandler.onObjectLeftMouseButtonDown(door,64,64);assert(not door.open and calls==2)
 ISObjectClickHandler.onObjectLeftMouseButtonUp(door,64,64)
 adjacent=false;ISObjectClickHandler.onObjectLeftMouseButtonDown(door,64,64);assert(calls==2);adjacent=true
 wall=true;ISObjectClickHandler.onObjectLeftMouseButtonDown(door,64,64);assert(calls==2);wall=false
 aiming=true;ISObjectClickHandler.onObjectLeftMouseButtonDown(door,64,64);assert(calls==2);aiming=false
 ISObjectClickHandler.onObjectLeftMouseButtonDown(box,64,64);ISObjectClickHandler.onObjectLeftMouseButtonUp(box,65,64);assert(selected==1)
 aiming=true;ISObjectClickHandler.onObjectLeftMouseButtonDown(box,64,64);ISObjectClickHandler.onObjectLeftMouseButtonUp(box,64,64);assert(selected==1);aiming=false
 """;
}
