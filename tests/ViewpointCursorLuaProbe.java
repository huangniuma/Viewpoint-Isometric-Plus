import java.lang.reflect.*;
import java.nio.file.*;
import se.krka.kahlua.vm.*;

/** Executes VP's actual building event wrapper around the native Lua walk cursor. */
public final class ViewpointCursorLuaProbe {
    static Method run;
    static void lua(String name,String source)throws Exception {run.invoke(null,name,source);}
    public static void main(String[] args)throws Exception {
        boolean wasEnabled=viewpoint.core.View.enabled;
        try {
            WalkToLuaProbe.main(args);
            run=WalkToLuaProbe.class.getDeclaredMethod("run",String.class,String.class);run.setAccessible(true);
            Field field=WalkToLuaProbe.class.getDeclaredField("environment");field.setAccessible(true);
            KahluaTable environment=(KahluaTable)field.get(null);
            environment.rawset("actualVpViewOn",(JavaFunction)(frame,count)->{frame.push(viewpoint.LootLua.viewOn());return 1;});
            lua("actual VP wrapper fixture","""
                local oldCell=getCell()
                oldCell.getDrag=function() return activeCursor end
                local events={}
                Events=setmetatable({}, {__index=function(t,name)
                    local e={callbacks={}}
                    e.Add=function(f) table.insert(e.callbacks,f) end
                    e.Remove=function(f) for i=#e.callbacks,1,-1 do if e.callbacks[i]==f then table.remove(e.callbacks,i) end end end
                    e.fire=function(...) for _,f in ipairs(e.callbacks) do f(...) end end
                    rawset(t,name,e);return e
                end})
                Events.OnDoTileBuilding2.Add(DoTileBuilding)
                IsoSprite={new=function() return setmetatable({}, {__index={}}) end}
                Viewpoint={Loot={viewOn=actualVpViewOn}}
                """);
            lua("actual Viewpoint_Build.lua",Files.readString(Path.of(args[1])));
            lua("install actual VP wrappers","Events.OnGameStart.fire(); assert(#Events.OnDoTileBuilding2.callbacks==1)");
            viewpoint.core.View.enabled=false;
            lua("native mode forwarding","""
                activeCursor=ISWalkToCursor:new('', '', player)
                local before=queued
                setInput(false,false); Events.OnDoTileBuilding2.fire(activeCursor,true,100,200,0,square)
                assert(activeCursor.canBeBuild)
                setInput(false,true); Events.OnDoTileBuilding2.fire(activeCursor,true,100,200,0,square)
                assert(queued==before+1 and lastTarget==square, 'VP viewOff wrapper must forward native click')
                """);
            viewpoint.core.View.enabled=true;
            lua("VP mode wrapper ownership","""
                activeCursor=ISWalkToCursor:new('', '', player)
                local before=queued
                setInput(false,false); Events.OnDoTileBuilding2.fire(activeCursor,true,100,200,0,square)
                setInput(false,true); Events.OnDoTileBuilding2.fire(activeCursor,true,100,200,0,square)
                assert(queued==before, 'VP own mode must reject unrelated native events')
                ViewpointBuild.place(activeCursor,100,200,0,100.25,200.75)
                setInput(false,false); Events.OnDoTileBuilding2.fire(activeCursor,true,100,200,0,square)
                setInput(false,true); Events.OnDoTileBuilding2.fire(activeCursor,true,100,200,0,square)
                ViewpointBuild.done(activeCursor)
                assert(queued==before+1, 'VP own driver scope must forward its original native click')
                """);
            System.out.println("Actual VP Lua cursor wrapper PASS: real Viewpoint_Build.lua with actual LootLua.viewOn and native ISWalkToCursor; viewOff events forward original preview/click, VP viewOn blocks unowned events and accepts its driving scope. Input is a fixture, not a release-lifetime or live-map test.");
        }finally {viewpoint.core.View.enabled=wasEnabled;}
    }
}
