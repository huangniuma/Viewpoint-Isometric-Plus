import java.lang.reflect.*;
import viewpoint.core.Frame;
import viewpoint.core.View;
import viewpoint.platform.*;
import vpiso.*;

/** Actual SpriteRenderer queue and woven VP cursor boundary; no world is rendered. */
public final class FrameGenQueue097Probe {
 public static void main(String[] args)throws Exception {
  Field unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);Object unsafe=unsafeField.get(null);
  GameAccess.set("NativeWaterProbe","unsafe",null,unsafe);GameAccess.set("NativeWaterProbe","allocate",null,unsafe.getClass().getMethod("allocateInstance",Class.class));
  Method fixture=Class.forName("NativeWaterProbe").getDeclaredMethod("fixture");fixture.setAccessible(true);fixture.invoke(null);
  Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
  Object states=GameAccess.field("zombie.core.SpriteRenderer","states",sprites);
  Object queue=GameAccess.call(states.getClass().getName(),"getPopulatingActiveState",states,new Class<?>[0]);
  String type="zombie.core.sprite.GenericSpriteRenderState";
  Main.compatible=true;View.enabled=false;IsometricControls.tick();
  var toggle=(LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null);
  toggle.set(false);GameAccess.set(type,"numSprites",queue,0);viewpoint.Hooks.cursorRendering();
  require((Integer)GameAccess.field(type,"numSprites",queue)==0,"Disabled FG queued a HUD capture");
  toggle.set(true);viewpoint.Hooks.cursorRendering();
  require((Integer)GameAccess.field(type,"numSprites",queue)==1,"Isometric HUD capture not queued");
  Object[] commands=(Object[])GameAccess.field(type,"sprite",queue);
  require(GameAccess.field("zombie.core.textures.TextureDraw","drawer",commands[0])==GameAccess.field("vpiso.IsometricFrameGeneration","HUDLESS",null),"Wrong HUD boundary");
  for(int i=0;i<3;i++) {
   Frame frame=new Frame();NativeReceiver receiver=new NativeReceiver();receiver.origin[0]=i*.1f;
   IsometricFrameGeneration.queueNative(frame,receiver,sprites,i);receiver.origin[0]=99;
  }
  Object[] drawers=(Object[])GameAccess.field("vpiso.IsometricFrameGeneration","DRAWERS",null);
  for(int i=0;i<3;i++) {
   NativeReceiver copied=(NativeReceiver)GameAccess.field(drawers[i].getClass().getName(),"receiver",drawers[i]);
   require(copied.origin[0]==i*.1f,"Queued native camera shared mutable receiver");
   require(GameAccess.field("zombie.core.textures.TextureDraw","drawer",commands[i+1])==drawers[i],"Native state used another queued drawer");
  }
  GameAccess.set(type,"numSprites",queue,0);View.enabled=true;viewpoint.Hooks.cursorRendering();
  require((Integer)GameAccess.field(type,"numSprites",queue)==1,"Stock VP HUD boundary duplicated or lost");
  require(GameAccess.field("zombie.core.textures.TextureDraw","drawer",commands[0]).getClass().getName().equals("viewpoint.OverWorldDrawer"),"Isometric HUD capture leaked into stock VP");
  System.out.println("FrameGen097 queue PASS: actual cursor hook, native SpriteRenderer triple-buffer camera copies, default-off no-op and stock VP passthrough.");
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
