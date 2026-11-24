import java.lang.reflect.*;
import viewpoint.core.View;
import viewpoint.platform.*;
import vpiso.*;
import zombie.GameWindow;
import zombie.gameStates.*;

/** Real woven cap entry points; independent FG readiness and stored cap preservation. */
public final class FrameRate099Probe {
    static int cases;
    public static void main(String[] args)throws Exception {
        zombie.core.random.RandStandard.INSTANCE.init();
        Main.compatible=true;GameAccess.set("viewpoint.platform.BuildPin","supported",null,Boolean.TRUE);
        IsometricControls.tick();
        var toggle=(LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null);
        var rate=(LiveSettings.Choice)GameAccess.field("vpiso.IsometricControls","frameGenerationRate",null);
        check(IsometricControls.frameGenerationRate()==30,"Default base rate");
        for(int old:new int[]{0,24,30,45,75,144,240})for(int selected:new int[]{0,30,40,50,60}) {
            check(IsometricFrameGeneration.baseLimit(old,false,selected)==old,"Off identity");
            int expected=selected==0?old:old>0?Math.min(old,selected):selected;
            check(IsometricFrameGeneration.baseLimit(old,true,selected)==expected,"Selected cap");
        }
        GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
        ShaderPacks.useBuiltIn("default","high",java.util.Map.of("upscaling","off","reflections","off"));
        GameAccess.set("viewpoint.game.FrameCaps","caps",null,newCaps(75,45));
        java.util.Arrays.fill(zombie.characters.IsoPlayer.players,null);
        IngameState game=(IngameState)allocate(IngameState.class);IngameState.instance=game;GameWindow.states.current=game;
        View.enabled=false;toggle.set(true);rate.set(1);
        GameAccess.set("vpiso.IsometricFrameGeneration","lastReadyNanos",null,0L);
        check(viewpoint.Hooks.lockFps(75)==75,"Unready FG changed cap");
        for(boolean models:new boolean[]{false,true}) {
            IsometricBridge.setModelsEnabled(models);
            ready();check(viewpoint.Hooks.lockFps(75)==30,"Active 30 base");
            check(!viewpoint.Hooks.framerateUncapped(true),"Active FG remained uncapped");
            for(int choice=0;choice<5;choice++) {
                rate.set(choice);ready();check(viewpoint.Hooks.lockFps(75)==new int[]{75,30,40,50,60}[choice],"Rate choice");
            }
            rate.set(1);GameAccess.set("viewpoint.game.FrameCaps","caps",null,newCaps(24,45));ready();
            check(viewpoint.Hooks.lockFps(24)==24,"Lower original cap raised");
            GameAccess.set("viewpoint.game.FrameCaps","caps",null,newCaps(75,45));ready();
            toggle.set(false);check(viewpoint.Hooks.lockFps(75)==75,"FG disabled cap leaked");toggle.set(true);
            ready();View.enabled=true;check(viewpoint.Hooks.lockFps(75)==75,"Stock VP cap changed");View.enabled=false;
            ready();GameAccess.set("viewpoint.game.FrameCaps","writingOptions",null,true);
            check(viewpoint.Hooks.lockFps(75)==75,"Saving options persisted temporary cap");GameAccess.set("viewpoint.game.FrameCaps","writingOptions",null,false);
            ready();IsometricBridge.setEnabled(false);check(viewpoint.Hooks.lockFps(75)==75,"Master disabled cap leaked");IsometricBridge.setEnabled(true);
            ready();GameAccess.set("vpiso.IsometricFrameGeneration","failed",null,true);check(viewpoint.Hooks.lockFps(75)==75,"Failed FG cap leaked");GameAccess.set("vpiso.IsometricFrameGeneration","failed",null,false);
            GameAccess.set("vpiso.IsometricFrameGeneration","lastReadyNanos",null,System.nanoTime()-2_000_000_000L);check(viewpoint.Hooks.lockFps(75)==75,"Stale producer cap leaked");
            ready();IsometricFrameGeneration.newSession();check(viewpoint.Hooks.lockFps(75)==75,"Session reset cap leaked");
        }
        ready();GameWindow.states.current=null;GameWindow.states.states.clear();check(viewpoint.Hooks.lockFps(75)==45,"Menu cap changed");
        check((Integer)GameAccess.call("viewpoint.game.FrameCaps","gameCap",null,new Class<?>[0])==75,"Stored game cap changed");
        check((Integer)GameAccess.call("viewpoint.game.FrameCaps","menuCap",null,new Class<?>[0])==45,"Stored menu cap changed");
        check(IsometricText.localize("Frame generation base rate")!=null,"Rate label missing");
        System.out.println("FrameRate099 CPU PASS: "+cases+" cases; woven hooks, native/model, 30/40/50/60/follow, lower cap, menu/save, stale/unready/failed/disabled producer and stock VP; no stored cap writes.");
    }
    static Object newCaps(int game,int menu)throws Exception {
        var c=Class.forName("viewpoint.game.FrameCaps$Caps").getDeclaredConstructor(int.class,int.class,boolean.class);c.setAccessible(true);return c.newInstance(game,menu,true);
    }
    static void ready(){GameAccess.set("vpiso.IsometricFrameGeneration","lastReadyNanos",null,System.nanoTime());}
    static Object allocate(Class<?> c)throws Exception{Field f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object u=f.get(null);return u.getClass().getMethod("allocateInstance",Class.class).invoke(u,c);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);cases++;}
}
