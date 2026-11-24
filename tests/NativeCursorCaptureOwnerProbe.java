import java.nio.file.*;
import java.util.*;
import se.krka.kahlua.vm.*;
import viewpoint.building.BuildDriver;
import viewpoint.core.Frame;
import vpiso.*;
import zombie.characters.TimedInputHandler;
import zombie.characters.component.CharacterInputComponent;
import zombie.input.Mouse;

/** Real VP BuildDriver + shipped Lua + native Attack/Click binding and original build handler.
 * The map, inventory, and action queues are fixtures. This does not launch the game. */
public final class NativeCursorCaptureOwnerProbe {
    static CharacterInputComponent component;
    static TimedInputHandler build;
    static Object actor;
    static KahluaTable[] drag;
    static ThreadLocal<Boolean> capturing;

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        boolean baseline=args.length>2&&args[2].equals("baseline044");
        // Reuse only the existing map/action fixture and original walk/item/furniture scripts.
        NativeCursorInputProbe.main(new String[]{args[0]});
        var env=NativeCursorInputProbe.env;
        var thread=NativeCursorInputProbe.thread;
        lua("load VP without running its game-start hooks","Events={OnGameStart={Add=function() end}}");
        lua("shipped VP building Lua",Files.readString(Path.of(args[1])));
        lua("count the real destructive branch","""
            noneCalls=0
            local none=ViewpointBuild.none
            ViewpointBuild.none=function(cursor) noneCalls=noneCalls+1; return none(cursor) end
            """);
        Class<?> managerType=Class.forName("se.krka.kahlua.converter.KahluaConverterManager");
        Object manager=managerType.getConstructor().newInstance();
        Class.forName("zombie.Lua.KahluaNumberConverter").getMethod("install",managerType).invoke(null,manager);
        Object caller=Class.forName("se.krka.kahlua.integration.LuaCaller").getConstructor(managerType).newInstance(manager);
        GameAccess.set("zombie.Lua.LuaManager","env",null,env);
        GameAccess.set("zombie.Lua.LuaManager","thread",null,thread);
        GameAccess.set("zombie.Lua.LuaManager","caller",null,caller);
        GameAccess.set("se.krka.kahlua.vm.KahluaThread","debugOwnerThread",thread,Thread.currentThread());
        GameAccess.call("zombie.core.random.RandStandard","init",GameAccess.field("zombie.core.random.RandStandard","INSTANCE",null),new Class<?>[0]);
        Object world=NativeCursorInputProbe.allocate("zombie.iso.IsoWorld");
        Object cell=NativeCursorInputProbe.allocate("zombie.iso.IsoCell");
        actor=NativeCursorInputProbe.allocate("zombie.characters.IsoPlayer");
        Object[] players=(Object[])GameAccess.field("zombie.characters.IsoPlayer","players",null);
        Arrays.fill(players,null);players[0]=actor;
        drag=new KahluaTable[4];
        GameAccess.set("zombie.iso.IsoCell","drag",cell,drag);
        GameAccess.set("zombie.iso.IsoWorld","currentCell",world,cell);
        GameAccess.set("zombie.iso.IsoWorld","instance",null,world);
        Main.compatible=true;
        viewpoint.core.View.enabled=false;
        GameAccess.set("viewpoint.platform.SettingsWindow","shown",null,false);
        GameAccess.set("viewpoint.platform.ImGuiFrame","wantsMouse",null,false);
        GameAccess.set("zombie.ui.UIManager","speedControls",null,null);
        ((List<?>)GameAccess.field("zombie.ui.UIManager","UI",null)).clear();
        Arrays.fill(Mouse.uiCaptured,false);
        GameAccess.set("zombie.input.Mouse","buttonDownStates",null,new boolean[8]);
        GameAccess.set("zombie.input.Mouse","buttonPrevStates",null,new boolean[8]);
        Object core=GameAccess.staticCall("zombie.core.Core","getInstance");
        GameAccess.call("zombie.core.Core","addKeyBinding",core,
                new Class<?>[]{String.class,int.class,int.class,boolean.class,boolean.class,boolean.class},
                "Attack/Click",10000,0,false,false,false);
        component=new CharacterInputComponent();
        GameAccess.set("zombie.characters.ecs.ECSComponent","ecsOwner",component,actor);
        build=(TimedInputHandler)GameAccess.field("zombie.characters.component.CharacterInputComponent","build",component);
        capturing=(ThreadLocal<Boolean>)GameAccess.field("vpiso.IsometricBridge","CAPTURING",null);
        env.rawset("actualBuildDown",(JavaFunction)(f,n)->{f.push(component.isBuildButtonDown());return 1;});
        env.rawset("actualBuildRelease",(JavaFunction)(f,n)->{f.push(component.isBuildButtonReleased());return 1;});

        for(boolean models:new boolean[]{false,true}) {
            IsometricBridge.setEnabled(true);IsometricBridge.setModelsEnabled(models);
            for(String kind:List.of("walk","item","furniture")) {
                NativeCursorInputProbe.fresh(kind);
                drag[0]=(KahluaTable)env.rawget("activeCursor");
                tick(false,models);
                NativeCursorInputProbe.process(false);
                lua("visible valid cursor","assert(cursor.canBeBuild)");
                captureDriver(); // native preview -> shadow capture, or next model capture
                tick(true,models);
                check(component.isBuildButtonDown(),"Actual Attack/Click binding did not report press");
                NativeCursorInputProbe.process(false);
                captureDriver();
                tick(false,models);
                check(build.isKeyReleased(),"Original native build handler did not report release");
                if(models) {
                    tick(false,true); // the existing 044 latch must retain this real native edge
                    captureDriver(); // model capture's FrameFill runs before its native cursor
                    NativeCursorInput.begin(drag[0]);
                    try {NativeCursorInputProbe.process(false);}
                    finally {NativeCursorInput.end();}
                } else NativeCursorInputProbe.process(false);
                NativeCursorInputProbe.assertActions(kind,baseline?0:1);
                if(!baseline) {
                    tick(false,models);
                    if(models) NativeCursorInput.begin(drag[0]);
                    try {NativeCursorInputProbe.process(false);}
                    finally {if(models)NativeCursorInput.end();}
                    NativeCursorInputProbe.assertActions(kind,1);
                }
                System.out.println((baseline?"044 negative control":"045 capture ownership PASS")+": "+kind+", "+(models?"model":"native shadows")+", actions="+(baseline?0:1));
            }
        }
        int count=((Number)env.rawget("noneCalls")).intValue();
        check(baseline?count>0:count==0,"Unexpected VP none calls during addon capture: "+count);
        // Ordinary VP, or a direct call outside addon capture, still owns its controller.
        lua("real VP control","cursor.canBeBuild=true; cursor.build=true");
        drag[0]=(KahluaTable)env.rawget("cursor");
        viewpoint.core.View.enabled=true;
        BuildDriver.frame(new Frame(),(zombie.characters.IsoGameCharacter)actor);
        lua("ordinary VP still reaches shipped none","assert(not cursor.canBeBuild and not cursor.build)");
        check(((Number)env.rawget("noneCalls")).intValue()==count+1,"Ordinary VP driver was suppressed");
        check(!IsometricBridge.capturing(),"Addon capture scope leaked");
        System.out.println("Capture ownership checks complete: actual VP driver + actual ViewpointBuild.none + actual Attack/Click binding/native constructor handler + original three Lua consumers; normal VP driver preserved. Isolated world/action queues; live-save acceptance remains.");
    }

    static void tick(boolean down,boolean models) {
        Mouse.buttonPrevStates[0]=Mouse.buttonDownStates[0];Mouse.buttonDownStates[0]=down;
        build.updateState();
        if(models)NativeCursorInput.sample(component);
    }
    static void captureDriver() {
        capturing.set(true);
        try {BuildDriver.frame(new Frame(),(zombie.characters.IsoGameCharacter)actor);}
        finally {capturing.remove();}
        check(!(Boolean)GameAccess.field("viewpoint.building.BuildDriver","failed",null),"VP driver failed instead of exercising its state transition");
    }
    static void lua(String name,String source)throws Exception {NativeCursorInputProbe.lua(name,source);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
