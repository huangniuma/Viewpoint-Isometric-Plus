import java.lang.reflect.*;
import java.nio.file.*;
import java.util.List;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.*;
import zombie.characters.TimedInputHandler;
import zombie.characters.component.CharacterInputComponent;
import vpiso.GameAccess;

/** Real native release lifetime and original cursor Lua; world, inventory and action queues are fixtures. */
public final class NativeCursorInputProbe {
    static KahluaTable env;
    static KahluaThread thread;
    static Class<?> latch;
    static Method observe,begin,released,end,reset;
    static final boolean[] down={false};
    static boolean muted;
    static TimedInputHandler input;
    static Object cursor;
    static boolean latched;

    public static void main(String[] args)throws Exception {
        latch=Class.forName("vpiso.NativeCursorInput");
        observe=latch.getMethod("observe",Object.class,boolean.class);
        begin=latch.getMethod("begin",Object.class);
        released=latch.getMethod("released",Object.class,boolean.class);
        end=latch.getMethod("end");reset=latch.getMethod("reset");
        var platform=J2SEPlatform.getInstance();env=platform.newEnvironment();thread=new KahluaThread(platform,env);
        lua("existing placement world fixture",constant(PlacementLuaProbe.class,"FIXTURE"));
        lua("extra native cursor fixture","""
            getText=function(key) return key end; getDebug=function() return false end
            local color={getR=function() return 1 end,getG=function() return 1 end,getB=function() return 1 end}
            getCore=function() return {getGoodHighlitedColor=function() return color end,getBadHighlitedColor=function() return color end} end
            local oldWorld=getWorld
            getWorld=function() local world=oldWorld(); world.getCell=getCell; return world end
            ISTimedActionQueue.clear=function() end
            """);
        Path source=Path.of(args[0],"media/lua");
        for(String file:List.of("shared/ISBaseObject.lua","shared/luautils.lua",
                "server/BuildingObjects/ISBuildingObject.lua","server/BuildingObjects/ISWalkToCursor.lua",
                "server/BuildingObjects/ISPlace3DItemCursor.lua","server/BuildingObjects/ISMoveableCursor.lua"))
            lua(file,Files.readString(source.resolve(file)));
        lua("existing placement helpers",constant(PlacementLuaProbe.class,"HELPERS"));
        env.rawset("actualBuildDown",(JavaFunction)(frame,count)->{frame.push(down[0]);return 1;});
        env.rawset("actualBuildRelease",(JavaFunction)(frame,count)->{
            try {frame.push(latched?(Boolean)released.invoke(null,null,input.isKeyReleased()):input.isKeyReleased());return 1;}
            catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        });
        lua("actual input and furniture action fixture","""
            player.isBuildButtonDown=function() return actualBuildDown() end
            player.isBuildButtonReleased=function() return actualBuildRelease() end
            ISMoveableDefinitions={cheat=false}
            local add=ISTimedActionQueue.add
            ISTimedActionQueue.add=function(action)
                if action.kind=='furniture' then furnitureCount=furnitureCount+1; lastFurniture=action else add(action) end
            end
            ISMoveablesAction={new=function(self,chr,sq,mode,sprite,object,direction,item,cursor)
                assert(chr==player and mode=='place'); return {kind='furniture',target=sq,item=item}
            end}
            makeNativeInputCursor=function(kind)
                resetCursor(1,100.25,200.75,0); furnitureCount=0
                if kind=='walk' then cursor=ISWalkToCursor:new('','',player) end
                if kind=='furniture' then
                    local props={sprite={getName=function() return 'fixture-chair' end},
                        walkToAndEquip=function() return true end,
                        getFaceDirectionFromSpriteName=function() return 'N' end,
                        findInInventory=function() return {name='fixture-chair'} end}
                    ISMoveableCursor.mode[0]='place'
                    cursor=setmetatable({Type='ISMoveableCursor',player=0,character=player,
                        skipBuildAction=true,skipWalk2=true,noNeedHammer=true,
                        currentSquare=square,currentMoveProps=props,origMoveProps={spriteName='fixture-chair'},
                        origSpriteName='fixture-chair',canCreate=true,
                        isValid=function() return true end,render=function() end,getSprite=function() return '' end},
                        {__index=ISMoveableCursor})
                end
                activeCursor=cursor
            end
            processNativeCursor=function()
                if getCell():getDrag(0)~=activeCursor then return end
                nativeOpaqueCallback(0,100,200,0,nil)
                DoTileBuilding(activeCursor,true,100,200,0,square)
            end
            assertNativeAction=function(kind,count)
                if kind=='walk' then assert(walkCount==count and dropCount==0 and furnitureCount==0)
                elseif kind=='item' then assert(dropCount==count and furnitureCount==0)
                else assert(furnitureCount==count and dropCount==0) end
            end
            """);
        try {
            for(String kind:List.of("walk","item","furniture")) {
                fresh(kind);process(false);
                update(true,false);update(false,false);
                check(input.isKeyReleased(),"Native handler did not produce release");
                update(false,false);
                check(!input.isKeyReleased(),"Second native update did not erase release");
                process(false);assertActions(kind,0);
                fresh(kind);process(true);
                update(true,true);update(false,true);update(false,true);
                check(!input.isKeyReleased(),"Fixed fixture must consume after native edge was erased");
                check(process(true),"Latched confirmation missing");assertActions(kind,1);
                check(!process(true),"Confirmation repeated");assertActions(kind,1);
                System.out.println("Native cursor input consumer PASS: "+kind+"; actual TimedInputHandler second update erases native release, original Lua queues0 without latch and1 with latch; repeat callback queues no duplicate.");
            }
            fresh("walk");process(true);update(true,true);update(false,true);
            check(input.isKeyReleased(),"Native released control missing");
            check(process(true),"Native edge was not confirmed");assertActions("walk",1);
            observe.invoke(null,cursor,true);
            check(!process(true),"Same still-live native release repeated");assertActions("walk",1);
            check((Boolean)released.invoke(null,null,true),"Outside owned scope changed original native release");
            begin.invoke(null,cursor);
            try {check((Boolean)released.invoke(null,new Object(),true),"Foreign component changed original release");}
            finally {end.invoke(null);}

            fresh("walk");update(true,true);update(false,false);update(false,true);
            check(!(Boolean)begin.invoke(null,cursor),"Raw down/up synthesized a confirmation without native release");end.invoke(null);
            fresh("walk");update(true,true);muted=true;update(false,true);
            check(!input.isKeyReleased(),"Native muted handler exposed a release");
            check(!(Boolean)begin.invoke(null,cursor),"Muted native release synthesized a confirmation");end.invoke(null);

            fresh("walk");update(true,true);update(false,true);
            Object other=new Object();
            check(!(Boolean)begin.invoke(null,other),"Foreign cursor consumed confirmation");end.invoke(null);
            check((Boolean)begin.invoke(null,cursor),"Foreign begin destroyed owning confirmation");end.invoke(null);
            update(true,true);update(false,true);
            observe.invoke(null,other,true);
            check(!(Boolean)begin.invoke(null,cursor),"Replaced cursor kept stale confirmation");end.invoke(null);
            check(!(Boolean)begin.invoke(null,other),"New cursor inherited opening release");end.invoke(null);

            for(String cancellation:List.of("off","paused","UI-cancel","closed-cursor")) {
                fresh("walk");update(true,true);update(false,true);
                observe.invoke(null,null,false);
                observe.invoke(null,cursor,input.isKeyReleased());
                check(!(Boolean)begin.invoke(null,cursor),"Stale confirmation after "+cancellation);end.invoke(null);
            }
            fresh("walk");update(true,true);update(false,true);reset.invoke(null);
            check(!(Boolean)begin.invoke(null,cursor),"Reset retained confirmation");end.invoke(null);
            System.out.println("Native cursor input lifecycle PASS: native release deduplication; no synthetic raw down/up or muted releases; cursor replacement and foreign scope; null observation and reset cancel pending confirmations; off/pause/UI labels exercise the core cancellation API, not live wrapper gates. Controlled CPU/Kahlua test; user-session timing root cause remains unobserved.");
        }finally {end.invoke(null);reset.invoke(null);}
        if(args.length>1 && args[1].equals("woven")) wovenSample();
    }

    /** Only the woven CharacterInputComponent is placed before the game jar for this optional check. */
    @SuppressWarnings("unchecked")
    static void wovenSample()throws Exception {
        var restore=new java.util.ArrayList<Runnable>();
        Object[] players=(Object[])GameAccess.field("zombie.characters.IsoPlayer","players",null);
        Object[] originalPlayers=players.clone();
        try {
            GameAccess.call("zombie.core.random.RandStandard","init",
                    GameAccess.field("zombie.core.random.RandStandard","INSTANCE",null),new Class<?>[0]);
            Object world=allocate("zombie.iso.IsoWorld"),cell=allocate("zombie.iso.IsoCell");
            Object actor=allocate("zombie.characters.IsoPlayer"),outsider=allocate("zombie.characters.IsoPlayer");
            for(int i=0;i<players.length;i++)players[i]=null;
            players[0]=actor;
            Object drag=java.lang.reflect.Array.newInstance(KahluaTable.class,4);
            java.lang.reflect.Array.set(drag,0,cursor);
            GameAccess.set("zombie.iso.IsoCell","drag",cell,drag);
            GameAccess.set("zombie.iso.IsoWorld","currentCell",world,cell);
            replace(restore,"zombie.iso.IsoWorld","instance",world);
            replace(restore,"vpiso.IsometricBridge","enabled",true);
            replace(restore,"vpiso.IsometricBridge","modelsEnabled",true);
            replace(restore,"vpiso.IsometricBridge","failed",false);
            replace(restore,"vpiso.Main","compatible",true);
            replace(restore,"viewpoint.core.View","enabled",false);
            replace(restore,"viewpoint.platform.SettingsWindow","shown",false);
            replace(restore,"viewpoint.platform.ImGuiFrame","wantsMouse",false);
            replace(restore,"zombie.network.GameServer","server",false);
            replace(restore,"zombie.network.GameClient","client",false);
            replace(restore,"zombie.ui.UIManager","speedControls",null);
            var ui=(java.util.ArrayList<Object>)GameAccess.field("zombie.ui.UIManager","UI",null);
            var previousUI=new java.util.ArrayList<>(ui);
            restore.add(()->{ui.clear();ui.addAll(previousUI);});ui.clear();
            boolean[] captured=(boolean[])GameAccess.field("zombie.input.Mouse","uiCaptured",null);
            boolean[] originalCaptured=captured.clone();
            restore.add(()->System.arraycopy(originalCaptured,0,captured,0,captured.length));
            captured[0]=false;
            check(vpiso.IsometricBridge.rotationAllowed(),"Actual production eligibility fixture blocked: "+vpiso.IsometricBridge.requestedBlockReason());

            FixtureComponent component=new FixtureComponent(actor),foreign=new FixtureComponent(outsider);
            prime(component);
            check(component.isBuildButtonReleased(),"Woven getter lost unscoped original native edge");
            component.tick(false);
            check(!component.isBuildButtonReleased(),"Second native update did not erase original release");
            foreign.tick(true);foreign.tick(false);
            check((Boolean)begin.invoke(null,cursor),"Woven update advice did not retain pending confirmation");
            try {
                check(component.isBuildButtonReleased(),"Woven getter did not return owning retained confirmation");
                check(foreign.isBuildButtonReleased(),"Woven getter changed foreign component's original release");
            }finally {end.invoke(null);}
            check(!(Boolean)begin.invoke(null,cursor),"Woven sample confirmation repeated");
            try {check(!component.isBuildButtonReleased(),"Woven getter exposed consumed confirmation");}
            finally {end.invoke(null);}

            prime(component);
            check((Boolean)begin.invoke(null,cursor),"Still-live native edge was not retained");end.invoke(null);
            check(component.isBuildButtonReleased(),"Scoped deduplication changed unscoped native release");
            check(!(Boolean)begin.invoke(null,cursor),"Still-live edge repeated on second callback");
            try {check(!component.isBuildButtonReleased(),"Woven getter did not suppress already-consumed native edge");}
            finally {end.invoke(null);}

            reset.invoke(null);component.tick(false);component.tick(true);component.muted=true;component.tick(false);
            check(!component.isBuildButtonReleased(),"Actual native component exposed muted release");
            check(!(Boolean)begin.invoke(null,cursor),"Actual sample synthesized muted native release");end.invoke(null);
            component.muted=false;

            Object paused=allocate("zombie.ui.SpeedControls");
            GameAccess.set("zombie.ui.SpeedControls","currentSpeed",paused,0);
            Object modal=Proxy.newProxyInstance(NativeCursorInputProbe.class.getClassLoader(),
                    new Class<?>[]{Class.forName("zombie.ui.UIElementInterface")},
                    (proxy,method,args)->method.getName().equals("isModalVisible")?true:null);
            for(String gate:List.of("off","models-off","paused","VP-settings","native-modal","ImGui-capture","native-capture","closed-cursor")) {
                prime(component);
                switch(gate) {
                    case "off"->GameAccess.set("vpiso.IsometricBridge","enabled",null,false);
                    case "models-off"->GameAccess.set("vpiso.IsometricBridge","modelsEnabled",null,false);
                    case "paused"->GameAccess.set("zombie.ui.UIManager","speedControls",null,paused);
                    case "VP-settings"->GameAccess.set("viewpoint.platform.SettingsWindow","shown",null,true);
                    case "native-modal"->ui.add(modal);
                    case "ImGui-capture"->GameAccess.set("viewpoint.platform.ImGuiFrame","wantsMouse",null,true);
                    case "native-capture"->captured[0]=true;
                    case "closed-cursor"->java.lang.reflect.Array.set(drag,0,null);
                }
                component.tick(false);
                check(!(Boolean)begin.invoke(null,cursor),"Actual production sample kept stale confirmation after "+gate);end.invoke(null);
                GameAccess.set("vpiso.IsometricBridge","enabled",null,true);
                GameAccess.set("vpiso.IsometricBridge","modelsEnabled",null,true);
                GameAccess.set("zombie.ui.UIManager","speedControls",null,null);
                GameAccess.set("viewpoint.platform.SettingsWindow","shown",null,false);
                GameAccess.set("viewpoint.platform.ImGuiFrame","wantsMouse",null,false);
                ui.clear();captured[0]=false;java.lang.reflect.Array.set(drag,0,cursor);
                component.tick(false);
                check(!(Boolean)begin.invoke(null,cursor),"Actual production sample revived stale confirmation after "+gate);end.invoke(null);
            }
            System.out.println("Native cursor woven sample PASS: actual updateToggleButtons and isBuildButtonReleased advice; actual ECS owner selection; owned release retained across second update and consumed once; foreign component original release preserved; actual muted native release not synthesized; off/models-off/pause/VP settings/native modal/ImGui capture/native mouse capture/closed cursor cancel pending confirmation. Fixture world and input callbacks; no live game timing observed.");
        }finally {
            end.invoke(null);reset.invoke(null);
            for(int i=restore.size()-1;i>=0;i--)restore.get(i).run();
            System.arraycopy(originalPlayers,0,players,0,players.length);
        }
    }

    static final class FixtureComponent extends CharacterInputComponent {
        boolean held,muted;
        FixtureComponent(Object owner)throws Exception {
            GameAccess.set("zombie.characters.ecs.ECSComponent","ecsOwner",this,owner);
            for(Field field:CharacterInputComponent.class.getDeclaredFields())if(field.getType()==TimedInputHandler.class) {
                field.setAccessible(true);
                field.set(this,field.getName().equals("build")
                        ?new TimedInputHandler(()->held,()->true,()->muted)
                        :new TimedInputHandler(()->false,()->false,()->true));
            }
        }
        @Override public boolean isBuildButtonDown(){return held;}
        void tick(boolean value){held=value;updateToggleButtons();}
    }
    static void prime(FixtureComponent component)throws Exception {
        reset.invoke(null);component.tick(false);component.tick(true);component.tick(false);
    }
    static Object allocate(String name)throws Exception {
        Class<?> type=Class.forName("sun.misc.Unsafe");Field field=type.getDeclaredField("theUnsafe");field.setAccessible(true);
        return type.getMethod("allocateInstance",Class.class).invoke(field.get(null),Class.forName(name));
    }
    static void replace(java.util.List<Runnable> restore,String owner,String name,Object value) {
        Object previous=GameAccess.field(owner,name,null);
        restore.add(()->GameAccess.set(owner,name,null,previous));GameAccess.set(owner,name,null,value);
    }

    static void fresh(String kind)throws Exception {
        end.invoke(null);reset.invoke(null);down[0]=false;muted=false;
        input=new TimedInputHandler(()->down[0],()->true,()->muted);input.updateState();
        lua("new "+kind,"makeNativeInputCursor('"+kind+"')");cursor=env.rawget("activeCursor");
        observe.invoke(null,cursor,false);
    }
    static void update(boolean held,boolean record)throws Exception {
        down[0]=held;input.updateState();
        if(record)observe.invoke(null,cursor,input.isKeyReleased());
    }
    static boolean process(boolean scope)throws Exception {
        latched=scope;boolean confirm=scope&&(Boolean)begin.invoke(null,cursor);
        try {lua("original native cursor consumer","processNativeCursor()");}
        finally {if(scope)end.invoke(null);latched=false;}
        return confirm;
    }
    static void assertActions(String kind,int count)throws Exception {lua("native queued actions","assertNativeAction('"+kind+"',"+count+")");}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String constant(Class<?> owner,String name)throws Exception {Field field=owner.getDeclaredField(name);field.setAccessible(true);return (String)field.get(null);}
    static void lua(String name,String text)throws Exception {thread.call(LuaCompiler.loadstring(text,name,env),new Object[0]);}
}
