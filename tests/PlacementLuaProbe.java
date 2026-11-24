import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real native opaque event and game cursor Lua, with isolated world/input/action fixtures. */
public final class PlacementLuaProbe {
    private static Object environment, thread, cell;
    private static Method compile, call, rawget;

    public static void main(String[] args) throws Exception {
        Class<?> platformType = type("se.krka.kahlua.vm.Platform");
        Class<?> tableType = type("se.krka.kahlua.vm.KahluaTable");
        Class<?> platformClass = type("se.krka.kahlua.j2se.J2SEPlatform");
        Object platform = platformClass.getMethod("getInstance").invoke(null);
        environment = platformClass.getMethod("newEnvironment").invoke(platform);
        Class<?> threadType = type("se.krka.kahlua.vm.KahluaThread");
        thread = threadType.getConstructor(platformType, tableType).newInstance(platform, environment);
        compile = type("se.krka.kahlua.luaj.compiler.LuaCompiler")
                .getMethod("loadstring", String.class, String.class, tableType);
        call = threadType.getMethod("call", Object.class, Object[].class);
        rawget = tableType.getMethod("rawget", Object.class);
        run("fixture setup", FIXTURE);
        Path lua = Path.of(args[0], "media", "lua");
        for (String file : List.of("shared/ISBaseObject.lua", "shared/luautils.lua",
                "server/BuildingObjects/ISBuildingObject.lua", "server/BuildingObjects/ISPlace3DItemCursor.lua"))
            run(file, Files.readString(lua.resolve(file)));
        run("placement fixture helpers", HELPERS);
        nativeFixture();

        run("reproduce missing producer", """
            resetCursor(1, 100.25, 200.75, 0)
            DoTileBuilding(cursor, true, 100, 200, 0, square)
            setInput(false, true)
            local ok, err = pcall(DoTileBuilding, cursor, true, 100, 200, 0, square)
            assert(not ok and string.find(tostring(err), 'has'), 'missing producer must reproduce nil:has error')
            assert(dropCount == 0 and previewCount == 0)
            """
        );

        run("floor setup", "resetCursor(1, 100.25, 200.75, 0); cursor.render3DItemRot = 90");
        produce(100.25f, 200.75f, 0);
        run("floor preview and click", """
            assert(opaqueCalls == 1 and eventX == 100 and eventY == 200 and eventZ == 0)
            assert(cursor.selectedSqDrop == square and previewCount == 1)
            assert(preview.x == 100.25 and preview.y == 200.75 and preview.z == 0 and preview.rot == 90)
            finishClick()
            assert(dropCount == 1 and walkCount == 1 and lastWalk.target == square)
            assert(lastDrop.target == square and lastDrop.x == .25 and lastDrop.y == .75 and lastDrop.z == 0)
            assert(lastDrop.rot == 90 and lastDrop.isPlaceItem and not lastDrop.multiple)
            """
        );

        run("surface setup", "resetCursor(1, 100.1, 200.9, 1); surfaceHeight = 48");
        produce(100.1f, 200.9f, 1);
        run("original surface offset", """
            assert(cursor.selectedSqDrop == square and math.abs(cursor.render3DItemZOffset - .5) < .00001)
            assert(math.abs(preview.z - 1.5) < .00001)
            finishClick()
            assert(dropCount == 1 and lastDrop.target == square and lastDrop.z == .5)
            """
        );

        run("wall setup", "resetCursor(1, 100.25, 200.75, 0); wallW = true; playerX = 99");
        produce(100.25f, 200.75f, 0);
        run("original wall adjacency", """
            finishClick()
            assert(dropCount == 1 and lastDrop.target == square)
            assert(lastWalk.target == neighbor and lastWalk.target:getX() == 101)
            """
        );

        run("all-items setup", "resetCursor(2, 100.25, 200.75, 0); shift = true; cursor.placeAll = true");
        produce(100.25f, 200.75f, 0);
        run("original place-all path", """
            assert(previewCount == 2 and cursor.selectedSqDrop == square)
            finishClick()
            assert(dropCount == 2 and walkCount == 1 and lastDrop.multiple and lastDrop.isPlaceItem)
            """
        );

        run("negative coordinates setup", "resetCursor(1, -1.25, -2.75, 0)");
        produce(-1.25f, -2.75f, 0);
        run("negative native flooring", """
            assert(eventX == -2 and eventY == -3 and cursor.selectedSqDrop == square)
            finishClick()
            assert(dropCount == 1 and lastDrop.x == .75 and lastDrop.y == .25)
            """
        );

        run("boundary setup", "resetCursor(1, 100.25, 200.75, 0)");
        produce(100.25f, 200.75f, 0);
        run("move to invalid map boundary", "worldX = 1024.25; worldY = 200.75; square = nil");
        produce(1024.25f, 200.75f, 0);
        run("native invalid boundary consumes no stale target", """
            assert(opaqueCalls == 1 and previewCount == 1, 'native event must skip invalid map coordinates')
            DoTileBuilding(cursor, true, 1024, 200, 0, nil)
            setInput(false, true)
            DoTileBuilding(cursor, true, 1024, 200, 0, nil)
            assert(dropCount == 0 and not cursor.canBeBuild, 'invalid boundary must not place previous target')
            """
        );
        System.out.println("Placement Lua regression PASS: missing opaque producer reproduces original nil:has error; production NativePlacement helper invokes actual FBORenderCell event and LuaEventManager/Kahlua callback; original cursor preview/click queues floor, surface, wall-adjacent, place-all and negative-coordinate actions; invalid native map boundary queues no drop. World/input/action fixtures only; no live map, pathfinding or GPU preview acceptance.");
    }

    private static void nativeFixture() throws Exception {
        Class<?> managerType = type("se.krka.kahlua.converter.KahluaConverterManager");
        Object manager = managerType.getConstructor().newInstance();
        type("zombie.Lua.KahluaNumberConverter").getMethod("install", managerType).invoke(null, manager);
        Object caller = type("se.krka.kahlua.integration.LuaCaller").getConstructor(managerType).newInstance(manager);
        set(type("zombie.Lua.LuaManager"), "env", null, environment);
        set(type("zombie.Lua.LuaManager"), "thread", null, thread);
        set(type("zombie.Lua.LuaManager"), "caller", null, caller);
        set(thread.getClass(), "debugOwnerThread", thread, Thread.currentThread());
        Object event = type("zombie.Lua.LuaEventManager").getMethod("AddEvent", String.class)
                .invoke(null, "RenderOpaqueObjectsInWorld");
        @SuppressWarnings("unchecked") List<Object> callbacks = (List<Object>) field(event.getClass(), "callbacks").get(event);
        callbacks.add(rawget.invoke(environment, "nativeOpaqueCallback"));
        callbacks.add(observer());

        cell = fresh("zombie.iso.IsoCell");
        Object world = field(type("zombie.iso.IsoWorld"), "instance").get(null);
        Object meta = fresh("zombie.iso.IsoMetaGrid");
        set(meta.getClass(), "minX", meta, -2); set(meta.getClass(), "minY", meta, -2);
        set(meta.getClass(), "maxX", meta, 2); set(meta.getClass(), "maxY", meta, 2);
        set(world.getClass(), "metaGrid", world, meta);
        set(type("zombie.characters.IsoPlayer"), "numPlayers", null, 0);
    }

    private static Object observer() throws Exception {
        run("opaque observer", "nativeObserver = function(player, x, y, z, sq) opaqueCalls=opaqueCalls+1; eventX=x; eventY=y; eventZ=z end");
        return rawget.invoke(environment, "nativeObserver");
    }

    private static void produce(float x, float y, float z) throws Exception {
        Object picked = field(type("zombie.ui.UIManager"), "PickedTile").get(null);
        set(picked.getClass(), "x", picked, x); set(picked.getClass(), "y", picked, y);
        Object state = field(type("zombie.iso.IsoCamera"), "frameState").get(null);
        set(state.getClass(), "camCharacterZ", state, z);
        vpiso.NativePlacement.renderOpaque(cell, 0);
    }

    private static Object fresh(String name) throws Exception {
        Class<?> u = type("sun.misc.Unsafe");
        Object unsafe = field(u, "theUnsafe").get(null);
        return u.getMethod("allocateInstance", Class.class).invoke(unsafe, type(name));
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void set(Class<?> owner, String name, Object target, Object value) throws Exception {
        field(owner, name).set(target, value);
    }
    private static void run(String name, String source) throws Exception {
        Object closure = compile.invoke(null, source, name, environment);
        call.invoke(thread, closure, new Object[0]);
    }

    private static final String FIXTURE = """
        require = function() end
        isServer = function() return false end
        isClient = function() return false end
        Events = setmetatable({}, {__index=function(t,name)
            local event = {Add=function(callback)
                if name == 'RenderOpaqueObjectsInWorld' then nativeOpaqueCallback = callback end
            end}
            rawset(t,name,event); return event
        end})
        UIFont={Medium={}}; getTextManager=function() return {getFontHeight=function() return 20 end} end
        JoypadState={players={}}
        local function state() return {instance=function() return {} end} end
        ClimbOverFenceState=state(); ClimbOverWallState=state(); ClimbState=state()
        ClimbDownState=state(); ClimbThroughWindowState=state()
        DebugType={Lua={debugln=function() end},Action={}}
        showDebugInfoInChat=function() end; log=function() end; ISBuildMenu={cheat=false}
        KeybindId={ROTATE_BUILDING={},TOGGLE_MODE={}}
        isKeyDown=function() return false end; isKeyPressed=function() return false end
        isShiftKeyDown=function() return shift end
        getMouseX=function() return 1 end; getMouseY=function() return 2 end
        screenToIsoX=function() return worldX end; screenToIsoY=function() return worldY end
        IsoFlagType={collideW={},collideN={}}; IsoPropertyType={COUNTERTOP={},FITS_BENEATH_COUNTERTOP={}}
        PZMath={clampFloat=function(v,min,max) return math.max(min,math.min(v,max)) end}
        local down,up=false,false
        setInput=function(d,u) down=d; up=u end
        player={getPlayerNum=function() return 0 end,getJoypadBind=function() return -1 end,
            getX=function() return playerX end,getY=function() return playerY end,getZ=function() return level end,
            getCurrentSquare=function() return square end,getSquare=function() return square end,
            getCharacterActions=function() return {isEmpty=function() return true end} end,
            isSittingOnFurniture=function() return false end,faceLocation=function() end,
            isEquipped=function() return false end,getInventory=function() return {} end,
            getCurrentState=function() return 'normal' end,
            isBuildButtonDown=function() return down end,isBuildButtonReleased=function() return up end}
        getSpecificPlayer=function() return player end
        cell={getDrag=function() return cursor end,setDrag=function(self,c) cursor=c end,
            getGridSquare=function(self,x,y,z)
                if not square then return nil end
                if x == square:getX()+1 and y == square:getY() then return neighbor end
                return square
            end}
        getCell=function() return cell end
        getSquare=function(x,y,z)
            if square and math.floor(x)==square:getX() and math.floor(y)==square:getY() then return square end
            return nil
        end
        getWorld=function() return {isValidSquare=function(self,x,y,z) return x>=-512 and x<768 and y>=-512 and y<768 end} end
        AdjacentFreeTileFinder={Find=function(sq) return sq end}
        ISTimedActionQueue={add=function(action)
            if action.kind=='drop' then dropCount=dropCount+1; lastDrop=action else walkCount=walkCount+1; lastWalk=action end
        end}
        ISWalkToTimedAction={new=function(self,chr,sq) assert(chr==player); return {kind='walk',target=sq} end}
        ISDropWorldItemAction={new=function(self,chr,item,sq,x,y,z,rot,multiple)
            assert(chr==player and item and sq)
            return {kind='drop',target=sq,x=x,y=y,z=z,rot=rot,multiple=multiple}
        end}
        ISWorldObjectContextMenu={transferIfNeeded=function() end}
        Render3DItem=function(item,sq,x,y,z,rot)
            previewCount=previewCount+1; preview={target=sq,x=x,y=y,z=z,rot=rot}
        end
        """;

    private static final String HELPERS = """
        ISBuildingObject.floorCursorSprite={RenderGhostTileColor=function() end}
        local function makeSquare(x,y,z)
            return {getX=function() return x end,getY=function() return y end,getZ=function() return z end,
                getObjects=function() return {size=function() return surfaceHeight>0 and 1 or 0 end,
                    get=function() return {getSurfaceOffsetNoTable=function() return surfaceHeight end,hasProperty=function() return false end} end} end,
                has=function(self,flag) return wallW and flag==IsoFlagType.collideW end,
                isCouldSee=function() return true end,isWallTo=function() return false end,isWindowTo=function() return false end,
                getTotalWeightOfItemsOnFloor=function() return 0 end,
                isSolid=function() return false end,isSolidTrans=function() return false end,TreatAsSolidFloor=function() return true end}
        end
        resetCursor=function(count,x,y,z)
            worldX=x; worldY=y; level=z; surfaceHeight=0; wallW=false; shift=false
            playerX=math.floor(x)+.5; playerY=math.floor(y)+.5
            square=makeSquare(math.floor(x),math.floor(y),z)
            neighbor=makeSquare(math.floor(x)+1,math.floor(y),z)
            previewCount=0; dropCount=0; walkCount=0; opaqueCalls=0; lastDrop=nil; lastWalk=nil
            local items={}
            for i=1,count do items[i]={getWorldItem=function() return nil end,
                getOutermostContainer=function() return nil end,getUnequippedWeight=function() return .5 end} end
            cursor=ISPlace3DItemCursor:new(player,items)
            setInput(false,false)
        end
        finishClick=function()
            local active=cursor
            DoTileBuilding(active,true,square:getX(),square:getY(),level,square)
            assert(active.canBeBuild, 'native cursor validation must remain active')
            setInput(false,true)
            DoTileBuilding(active,true,square:getX(),square:getY(),level,square)
        end
        """;
}
