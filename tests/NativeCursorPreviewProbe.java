import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.*;
import viewpoint.core.Frame;
import vpiso.*;
import zombie.core.textures.Texture;
import zombie.iso.*;
import zombie.iso.sprite.*;

/** Actual addon capture, native events and VP Lua/CPU geometry; isolated world and assets. */
public final class NativeCursorPreviewProbe {
    private static KahluaTable env;
    private static KahluaThread thread;
    private static final sun.misc.Unsafe U;
    static {
        try { Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);U=(sun.misc.Unsafe)f.get(null); }
        catch(Exception e){throw new ExceptionInInitializerError(e);}
    }

    public static final class FixtureTexture extends Texture {
        private static final long serialVersionUID=1L;
        @Override public String getName(){return "CPU-preview-floor";}
        @Override public int getWidth(){return 128;}
        @Override public int getHeight(){return 256;}
        @Override public int getWidthOrig(){return 128;}
        @Override public int getHeightOrig(){return 256;}
        @Override public int getWidthHW(){return 128;}
        @Override public int getHeightHW(){return 256;}
        @Override public float getXStart(){return 0;}
        @Override public float getYStart(){return 0;}
        @Override public float getXEnd(){return 1;}
        @Override public float getYEnd(){return 1;}
    }
    private static IsoSprite fixtureSprite() {
        IsoSprite sprite=new IsoSprite();sprite.name="CPU-preview-floor";sprite.solidfloor=true;
        sprite.currentAnim=new IsoAnim();sprite.currentAnim.frames.add(new IsoDirectionFrame(new FixtureTexture()));
        return sprite;
    }

    public static void main(String[] args)throws Exception {
        zombie.core.random.RandStandard.INSTANCE.init();
        var platform=J2SEPlatform.getInstance();env=platform.newEnvironment();thread=new KahluaThread(platform,env);
        lua("placement world fixture",constant(PlacementLuaProbe.class,"FIXTURE"));
        Path game=Path.of(args[0],"media/lua");
        for(String file:List.of("shared/ISBaseObject.lua","shared/luautils.lua",
                "server/BuildingObjects/ISBuildingObject.lua","server/BuildingObjects/ISPlace3DItemCursor.lua"))
            lua(file,Files.readString(game.resolve(file)));
        lua("placement helpers",constant(PlacementLuaProbe.class,"HELPERS"));
        env.rawset("actualVpViewOn",(JavaFunction)(f,n)->{f.push(viewpoint.LootLua.viewOn());return 1;});
        env.rawset("actualSprite",fixtureSprite());
        lua("VP native event and asset fixture", """
            local nativeBuilding=DoTileBuilding
            local nativeOpaque=nativeOpaqueCallback
            DoTileBuilding=function(c,render,x,y,z,nativeSquare)
                buildingCalls=buildingCalls+1; receivedX=x; receivedY=y; receivedZ=z
                return nativeBuilding(c,render,x,y,z,square)
            end
            opaqueFixture=function(p,x,y,z,nativeSquare)
                opaqueCalls=opaqueCalls+1; eventX=x; eventY=y; eventZ=z
                return nativeOpaque(p,x,y,z,nil)
            end
            local events={}
            Events=setmetatable({}, {__index=function(t,name)
                local e={callbacks={}}
                e.Add=function(f) table.insert(e.callbacks,f) end
                e.Remove=function(f) for i=#e.callbacks,1,-1 do if e.callbacks[i]==f then table.remove(e.callbacks,i) end end end
                e.fire=function(...) for _,f in ipairs(e.callbacks) do f(...) end end
                rawset(t,name,e);return e
            end})
            Events.OnDoTileBuilding2.Add(DoTileBuilding)
            ghostMethods={RenderGhostTileColor=function() rawDraws=rawDraws+1 end}
            IsoSprite={new=function() return setmetatable({}, {__index=ghostMethods}) end}
            Viewpoint={Loot={viewOn=actualVpViewOn}}
            """);
        lua("actual Viewpoint_Build.lua",Files.readString(Path.of(args[1])));
        lua("install actual VP wrappers", """
            Events.OnGameStart.fire()
            driverCallback=Events.OnDoTileBuilding2.callbacks[1]
            local done=ViewpointBuild.done
            ViewpointBuild.done=function(c) lastShown=done(c); return lastShown end
            ISBuildingObject.floorCursorSprite={RenderGhostTileColor=function(self,...)
                ghostMethods.RenderGhostTileColor(actualSprite,...)
            end}
            resetPreview=function(x,y,z)
                resetCursor(1,x,y,z)
                buildingCalls=0; rawDraws=0
                -- Count the original validity/create routines without replacing their behavior.
                local valid,create=cursor.isValid,cursor.create
                validationCalls=0; creationCalls=0
                cursor.isValid=function(self,...) validationCalls=validationCalls+1; return valid(self,...) end
                cursor.create=function(self,...) creationCalls=creationCalls+1; return create(self,...) end
            end
            """);
        nativeFixture();
        IsoCell cell=(IsoCell)U.allocateInstance(IsoCell.class);
        KahluaTable[] drag=new KahluaTable[4];GameAccess.set("zombie.iso.IsoCell","drag",cell,drag);
        Object world=GameAccess.field("zombie.iso.IsoWorld","instance",null);
        GameAccess.set("zombie.iso.IsoWorld","currentCell",world,cell);
        vpiso.Main.compatible=true;viewpoint.core.View.enabled=false;
        IsometricBridge.setEnabled(true);IsometricBridge.setModelsEnabled(true);
        GameAccess.set("vpiso.IsometricBridge","submitted",null,true);
        try {
            check(!viewpoint.LootLua.viewOn(),"Capture view advice leaked before capture");
            int cases=0;
            for(float yaw:new float[]{17.25f,123.75f,225.5f}) for(float zoom:new float[]{.5f,1,2}) {
                Frame frame=camera(yaw,zoom,cases%2==0?100:-20,cases%2==0?200:-30,cases%2);
                int mx=711,my=379;
                GameAccess.set("zombie.input.Mouse","x",null,mx);GameAccess.set("zombie.input.Mouse","y",null,my);
                double[] point=new double[2];check(RotationCamera.ground(mx,my,Math.floor(frame.camZ),point),"No inverse pointer projection");
                lua("fresh actual item cursor","resetPreview("+point[0]+","+point[1]+","+(int)Math.floor(frame.camZ)+")");
                drag[0]=(KahluaTable)env.rawget("cursor");
                setCameraZ(frame.camZ);
                Object picked=GameAccess.field("zombie.ui.UIManager","PickedTile",null);
                GameAccess.set("zombie.iso.Vector2","x",picked,-111f);GameAccess.set("zombie.iso.Vector2","y",picked,-222f);
                NativeCursorPreview.capture(frame,cell,0);
                KahluaTable shown=(KahluaTable)env.rawget("lastShown");
                close(number(shown,"x"),Math.floor(point[0]));close(number(shown,"y"),Math.floor(point[1]));
                lua("original native validation and captured outputs", """
                    assert(buildingCalls==1 and opaqueCalls==1 and validationCalls==2,'calls: '..buildingCalls..','..opaqueCalls..','..validationCalls)
                    assert(cursor.canBeBuild and lastShown.valid and cursor.selectedSqDrop==square,'native validity/opaque target')
                    assert(receivedX==square:getX() and receivedY==square:getY() and receivedZ==square:getZ(),'native consumer coordinates')
                    assert(eventX==receivedX and eventY==receivedY and eventZ==receivedZ,'opaque/consumer coordinates differ')
                    assert(#lastShown.ghosts==1 and lastShown.ghosts[1][1]==actualSprite and rawDraws==0,'ghost capture')
                    assert(#lastShown.items==1 and lastShown.items[1][1]==cursor.items[1],'item capture')
                    assert(math.abs(lastShown.items[1][3]-worldX)<.001 and math.abs(lastShown.items[1][4]-worldY)<.001,'item pointer coordinates')
                    assert(creationCalls==0 and dropCount==0,'unexpected create')
                    """);
                Object ghosts=frame.scene.groundDraws.ghosts;
                int verts=((Number)GameAccess.field("viewpoint.render.GhostDraws","vertexCount",ghosts)).intValue();
                check(verts==6,"Actual VP ghost conversion emitted "+verts+" vertices instead of floor mesh");
                float[] vertices=(float[])GameAccess.field("viewpoint.render.GhostDraws","vertices",ghosts);
                for(int i=0;i<verts*5;i++)check(Float.isFinite(vertices[i]),"Nonfinite ghost geometry");
                float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
                float minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
                for(int i=0;i<verts;i++) {
                    minX=Math.min(minX,vertices[i*5]);maxX=Math.max(maxX,vertices[i*5]);
                    minZ=Math.min(minZ,vertices[i*5+2]);maxZ=Math.max(maxZ,vertices[i*5+2]);
                    close(vertices[i*5+1],0);
                }
                close(maxX-minX,1);close(maxZ-minZ,1);
                close((minX+maxX)*.5,frame.camX-Math.floor(point[0])-.5);
                close((minZ+maxZ)*.5,frame.camY-Math.floor(point[1])-.5);
                check(!NativeCursorPreview.capturing()&&!viewpoint.LootLua.viewOn(),"Capture/view scope leaked");
                close(RotationCamera.yawDegrees(),yaw);check(drag[0]==env.rawget("cursor"),"Capture replaced native cursor");
                int before=((Number)env.rawget("buildingCalls")).intValue();
                cell.DoBuilding(0,true);cell.DoBuilding(0,false);
                check(number(env,"buildingCalls")==before,"Woven owner advice admitted a second native callback");
                if(cases==0) {
                    lua("original create confirmation","setInput(false,true)");
                    Frame click=camera(yaw,zoom,frame.camX,frame.camY,frame.camZ);
                    NativeCursorPreview.capture(click,cell,0);
                    lua("original action queued exactly once","assert(dropCount==1 and creationCalls==1 and walkCount==1); setInput(false,false)");
                    drag[0]=(KahluaTable)env.rawget("cursor");
                    Frame repeat=camera(yaw,zoom,frame.camX,frame.camY,frame.camZ);
                    NativeCursorPreview.capture(repeat,cell,0);
                    lua("no repeated original create","assert(dropCount==1 and creationCalls==1)");
                }
                cases++;
            }
            lua("outside scope ghost forwarding","ghostMethods.RenderGhostTileColor(actualSprite,1,2,0,1,1,1,1); assert(rawDraws==1)");
            check(!NativeCursorPreview.capturing(),"Scope remained set after completed captures");
            System.out.println("Native cursor preview PASS: production capture + actual native FBORenderCell/IsoCell event producers + real VP Lua wrappers + real ISPlace3DItemCursor validation/create; 9 yaw/zoom/height/negative-centre pointer cases retain selected yaw and native cursor; real shown.x/y and captured item/ghost lists; actual VP BuildGhosts emits6 finite CPU mesh vertices into owning Frame; woven capture advice holds raw sprite draw and owner advice prevents duplicate native callbacks; original drop queues once. Synthetic map/sprite texture/input/action queue; item model asset rendering and live game/GPU acceptance are not tested.");
        }finally {RotationCamera.configure(false,0);IsometricBridge.setModelsEnabled(false);}
    }

    private static void nativeFixture()throws Exception {
        Class<?> managerType=Class.forName("se.krka.kahlua.converter.KahluaConverterManager");
        Object manager=managerType.getConstructor().newInstance();
        Class.forName("zombie.Lua.KahluaNumberConverter").getMethod("install",managerType).invoke(null,manager);
        Object caller=Class.forName("se.krka.kahlua.integration.LuaCaller").getConstructor(managerType).newInstance(manager);
        GameAccess.set("zombie.Lua.LuaManager","env",null,env);GameAccess.set("zombie.Lua.LuaManager","thread",null,thread);
        GameAccess.set("zombie.Lua.LuaManager","caller",null,caller);GameAccess.set("se.krka.kahlua.vm.KahluaThread","debugOwnerThread",thread,Thread.currentThread());
        event("RenderOpaqueObjectsInWorld",env.rawget("opaqueFixture"));event("OnDoTileBuilding2",env.rawget("driverCallback"));
        Object meta=U.allocateInstance(Class.forName("zombie.iso.IsoMetaGrid"));
        for(String key:List.of("minX","minY"))GameAccess.set("zombie.iso.IsoMetaGrid",key,meta,-2);
        for(String key:List.of("maxX","maxY"))GameAccess.set("zombie.iso.IsoMetaGrid",key,meta,2);
        GameAccess.set("zombie.iso.IsoWorld","metaGrid",GameAccess.field("zombie.iso.IsoWorld","instance",null),meta);
        GameAccess.set("zombie.characters.IsoPlayer","numPlayers",null,0);
        GameAccess.set("zombie.iso.IsoCamera","isoCameraGameCharacter",null,U.allocateInstance(Class.forName("zombie.characters.IsoPlayer")));
    }
    @SuppressWarnings("unchecked") private static void event(String name,Object callback)throws Exception {
        Object event=Class.forName("zombie.Lua.LuaEventManager").getMethod("AddEvent",String.class).invoke(null,name);
        List<Object> callbacks=(List<Object>)GameAccess.field("zombie.Lua.Event","callbacks",event);callbacks.clear();callbacks.add(callback);
    }
    private static Frame camera(float yaw,float zoom,float x,float y,float z) {
        RotationCamera.configureYaw(true,yaw);check(RotationCamera.active(),"Rotation fixture not allowed");
        Frame f=new Frame();f.camX=x;f.camY=y;f.camZ=z;Matrix4f view=new Matrix4f();
        IsoCameraMath.matrices(new IsoCameraMath.Fit(x,y,12*zoom),x,y,1280,720,f.scene.projection,view,new float[3],yaw);
        RotationCamera.capture(f,view,1280,720,zoom);return f;
    }
    private static void setCameraZ(float z){Object state=GameAccess.field("zombie.iso.IsoCamera","frameState",null);GameAccess.set(state.getClass().getName(),"camCharacterZ",state,z);GameAccess.set("zombie.iso.IsoMovingObject","z",GameAccess.field("zombie.iso.IsoCamera","isoCameraGameCharacter",null),z);}
    private static double number(KahluaTable table,String key){return ((Number)table.rawget(key)).doubleValue();}
    private static String constant(Class<?> c,String n)throws Exception {Field f=c.getDeclaredField(n);f.setAccessible(true);return (String)f.get(null);}
    private static void lua(String name,String source)throws Exception {thread.call(LuaCompiler.loadstring(source,name,env),new Object[0]);}
    private static void close(double a,double b){check(Math.abs(a-b)<.001,a+" != "+b);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
