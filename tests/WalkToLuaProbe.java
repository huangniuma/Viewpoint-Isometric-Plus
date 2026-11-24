import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Executes the game's real cursor Lua in its real Kahlua VM with isolated world/input fixtures. */
public final class WalkToLuaProbe {
    private static Object environment, thread;
    private static Method compile, call;

    public static void main(String[] args) throws Exception {
        Class<?> platformType = Class.forName("se.krka.kahlua.vm.Platform");
        Class<?> tableType = Class.forName("se.krka.kahlua.vm.KahluaTable");
        Class<?> platformClass = Class.forName("se.krka.kahlua.j2se.J2SEPlatform");
        Object platform = platformClass.getMethod("getInstance").invoke(null);
        environment = platformClass.getMethod("newEnvironment").invoke(platform);
        Class<?> threadType = Class.forName("se.krka.kahlua.vm.KahluaThread");
        thread = threadType.getConstructor(platformType,tableType).newInstance(platform,environment);
        compile = Class.forName("se.krka.kahlua.luaj.compiler.LuaCompiler")
                .getMethod("loadstring",String.class,String.class,tableType);
        call = threadType.getMethod("call",Object.class,Object[].class);
        run("fixture setup", """
            require = function() end
            local event = { Add = function() end }
            Events = setmetatable({}, {__index=function() return event end})
            JoypadState = { players = {} }
            local function state() return {instance=function() return {} end} end
            ClimbOverFenceState=state(); ClimbOverWallState=state(); ClimbState=state()
            ClimbDownState=state(); ClimbThroughWindowState=state()
            DebugType={Action={}}; log=function() end
            local pressed=false; local released=false
            player={getPlayerNum=function() return 0 end,
                isBuildButtonDown=function() return pressed end,
                isBuildButtonReleased=function() return released end,
                getCurrentState=function() return 'normal' end}
            setInput=function(down,up) pressed=down; released=up end
            getSpecificPlayer=function() return player end
            square={getX=function() return 100 end,getY=function() return 200 end,
                getZ=function() return 0 end,TreatAsSolidFloor=function() return true end}
            local cell={getGridSquare=function() return square end}
            getWorld=function() return {getCell=function() return cell end} end
            getCell=function() return cell end
            local color={getR=function() return 1 end,getG=function() return 1 end,getB=function() return 1 end}
            getCore=function() return {getGoodHighlitedColor=function() return color end,
                getBadHighlitedColor=function() return color end} end
            rendered=0; queued=0; cleared=0; lastTarget=nil
            ISTimedActionQueue={clear=function(character) assert(character==player); cleared=cleared+1 end,
                add=function(action) queued=queued+1; lastTarget=action.target end}
            ISWalkToTimedAction={new=function(self,character,target)
                assert(character==player); return {target=target} end}
            """
        );
        Path lua = Path.of(args[0],"media","lua");
        load(lua.resolve("shared/ISBaseObject.lua"));
        load(lua.resolve("server/BuildingObjects/ISBuildingObject.lua"));
        load(lua.resolve("server/BuildingObjects/ISWalkToCursor.lua"));
        run("real walk-to consumer regression", """
            ISBuildingObject.floorCursorSprite={RenderGhostTileColor=function() rendered=rendered+1 end}
            local missing=ISWalkToCursor:new('', '', player)
            setInput(false,false); DoTileBuilding(missing,false,100,200,0,square)
            setInput(false,true); DoTileBuilding(missing,false,100,200,0,square)
            assert(queued==0 and rendered==0, 'missing render callback must reproduce failure')
            local restored=ISWalkToCursor:new('', '', player)
            setInput(false,false); DoTileBuilding(restored,true,100,200,0,square)
            assert(restored.canBeBuild and rendered==1, 'render callback must validate/show cursor')
            setInput(false,true); DoTileBuilding(restored,false,100,200,0,square)
            assert(queued==1 and cleared==1 and lastTarget==square,
                'restored callback must allow original click path to queue walk-to')
            return true
            """
        );
        System.out.println("Walk-to Lua regression PASS: actual Kahlua + original ISBuildingObject/ISWalkToCursor reproduce missing-render failure and queue native walk action after render callback; fixture input/world only, no map or pathfinding run.");
    }

    private static void load(Path path) throws Exception { run(path.getFileName().toString(),Files.readString(path)); }
    private static void run(String name, String source) throws Exception {
        Object closure = compile.invoke(null,source,name,environment);
        call.invoke(thread,closure,new Object[0]);
    }
}
