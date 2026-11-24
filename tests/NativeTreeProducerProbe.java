import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real unmodified IsoTree.render -> FBORenderTrees.addTree, with controlled world inputs. */
public final class NativeTreeProducerProbe {
    private static final Class<?>[] NONE=new Class<?>[0];
    private static Object unsafe,batch,player,square,time;
    private static Method allocate;
    private static float step;
    private static final List<Runnable> restore=new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Class<?> u=type("sun.misc.Unsafe"); unsafe=field(u,"theUnsafe").get(null);
        allocate=u.getMethod("allocateInstance",Class.class);
        try {
            fixture();
            Object texture=texture("fixture full");
            Object ordinary=tree(sprite("VPISO_fixture_normal",texture));
            set(ordinary,"renderFlag",true);
            render(ordinary);
            List<?> records=records();
            equal("ordinary records",records.size(),1);
            Object r=records.get(0);
            same("ordinary texture",get(r,"texture"),texture);
            equal("native root X + 1",number(r,"x"),11f);
            equal("native root Y + 1",number(r,"y"),13f);
            yes("native stencil flag",(Boolean)get(r,"useStencil"));
            near("native fade step",number(r,"fadeAlpha"),1f-step);
            no("ordinary transparent",(Boolean)get(r,"transparent"));
            for (int i=0;i<80;i++) { clearBatch(); render(ordinary); }
            near("native minimum .05",number(ordinary,"fadeAlpha"),.05f);
            set(ordinary,"renderFlag",false);
            for (int i=0;i<80;i++) { clearBatch(); render(ordinary); }
            near("native fade recovery",number(ordinary,"fadeAlpha"),1f);

            String xlName="VPISO_fixture_XL";
            Object full=texture("fixture XL full"),trunk=texture("fixture trunk"),crown=texture("fixture crown");
            Object leaves=texture("fixture leaves"),trunkLeaves=texture("fixture trunk leaves"),crownLeaves=texture("fixture crown leaves");
            Object xl=tree(sprite(xlName,full));
            Object leafSprite=sprite("VPISO_fixture_leaves",leaves);
            Object attachment=fresh("zombie.iso.sprite.IsoSpriteInstance"); set(attachment,"parentSprite",leafSprite);
            set(xl,"attachedAnimSprite",new ArrayList<>(List.of(attachment)));
            registerSprite("VPISO_fixture_trunk",sprite("VPISO_fixture_trunk",trunk));
            registerSprite("VPISO_fixture_crown",sprite("VPISO_fixture_crown",crown));
            registerSprite("VPISO_fixture_trunk_leaves",sprite("VPISO_fixture_trunk_leaves",trunkLeaves));
            registerSprite("VPISO_fixture_crown_leaves",sprite("VPISO_fixture_crown_leaves",crownLeaves));
            registerDescription(xlName,"VPISO_fixture_crown","VPISO_fixture_trunk");
            registerDescription("VPISO_fixture_leaves","VPISO_fixture_crown_leaves","VPISO_fixture_trunk_leaves");
            set(player,"numNearbyBuildingsRooms",1f);
            clearBatch(); render(xl);
            records=records(); equal("XL split records",records.size(),2);
            Object base=records.get(0),top=records.get(1);
            same("native trunk selection",get(base,"texture"),trunk);
            same("native crown selection",get(top,"texture"),crown);
            same("native trunk leaf selection",get(base,"texture2"),trunkLeaves);
            same("native crown leaf selection",get(top,"texture2"),crownLeaves);
            no("trunk stays opaque",(Boolean)get(base,"transparent"));
            yes("crown transparent",(Boolean)get(top,"transparent"));
            near("trunk cutaway",number(base,"cutawayAlpha"),1f);
            near("crown independent cutaway",number(top,"cutawayAlpha"),1f-step);
            for (int i=0;i<80;i++) { clearBatch(); render(xl); }
            near("native crown reaches zero",number(xl,"cutawayAlpha"),0f);
            set(player,"numNearbyBuildingsRooms",0f);
            clearBatch(); render(xl);
            records=records(); equal("native restore selects original",records.size(),1);
            same("native restored full texture",get(records.get(0),"texture"),full);
            same("native restored full leaves",get(records.get(0),"texture2"),leaves);
            yes("restoration keeps transparent state",(Boolean)get(records.get(0),"transparent"));
            near("restoration cutaway step",number(xl,"cutawayAlpha"),step);
            for (int i=0;i<80;i++) { clearBatch(); render(xl); }
            near("native cutaway recovers",number(xl,"cutawayAlpha"),1f);

            clearBatch(); set(ordinary,"ppfHighlighted",(byte)1); render(ordinary);
            equal("highlighted native early return",records().size(),0);
            same("highlighted chop reference",staticGet("zombie.iso.objects.IsoTree","chopTreeHighlighted"),ordinary);
            byte highlighted=(Byte)get(ordinary,"ppfHighlighted");
            try { set(ordinary,"ppfHighlighted",(byte)0); render(ordinary); }
            finally { set(ordinary,"ppfHighlighted",highlighted); }
            equal("temporarily cleared highlight base record",records().size(),1);
            equal("highlight bit restored",((Byte)get(ordinary,"ppfHighlighted")).intValue(),1);
            bridgeCapture(ordinary);
            System.out.println("Native tree producer PASS: actual public IsoTree.render and original addTree bodies; ordinary stencil fade/minimum/recovery, native +1 roots, XL near-room trunk/crown and attached leaf selection, independent alpha to zero/recovery, highlighted early return and temporary-clear base record. Controlled CPU world/textures; no map, GPU tree draw, aiming/vehicle or outline acceptance.");
        } finally {
            if (batch!=null) invoke(batch,"postRender",NONE);
            for (int i=restore.size()-1;i>=0;i--) restore.get(i).run();
        }
    }

    private static void bridgeCapture(Object tree) throws Exception {
        Object renderer=staticGet("zombie.iso.fboRenderChunk.FBORenderCell","instance");
        Object oldCurrent=staticGet("zombie.iso.fboRenderChunk.FBORenderTrees","current");
        boolean oldTranslucent=(Boolean)get(renderer,"renderTranslucentOnly");
        vpiso.NativeTrees bridge=new vpiso.NativeTrees();
        try {
            set(square,"objects",new ArrayList<>(List.of(tree)));
            Object color=type("zombie.core.textures.ColorInfo").getConstructor(float.class,float.class,float.class,float.class)
                    .newInstance(.7f,.8f,.9f,1f);
            Object colors=Array.newInstance(color.getClass(),4);
            for (int i=0;i<4;i++) Array.set(colors,i,color);
            set(square,"lightInfo",colors);
            set(square,"targetPlayerCutawayFlags",new byte[4]);
            set(square,"playerCutawayFlags",new byte[4]);
            set(square,"playerCutawayFlagLockUntilTimes",new long[4]);
            set(player,"ecsComponentMap",new java.util.HashMap<>());
            Object cell=fresh("zombie.iso.IsoCell"); set(cell,"stencilAreas",new ArrayList<>());
            set(staticGet("zombie.iso.IsoWorld","instance"),"currentCell",cell);
            set(tree,"highlightColor",colors);
            set(get(tree,"sprite"),"properties",type("zombie.core.properties.PropertyContainer").getConstructor().newInstance());
            bridge.add(square);
            bridge.capture(renderer,0);
            Object captured=get(bridge,"batch");
            List<?> nativeRecords=(List<?>)get(captured,"trees");
            equal("production bridge base records",nativeRecords.size(),1);
            equal("production bridge restores highlight",((Byte)get(tree,"ppfHighlighted")).intValue(),1);
            same("production bridge restores native batch",staticGet("zombie.iso.fboRenderChunk.FBORenderTrees","current"),oldCurrent);
            equal("production bridge restores mode",(Boolean)get(renderer,"renderTranslucentOnly")?1:0,oldTranslucent?1:0);
            bridge.release(); bridge.release();
            same("production bridge released batch",get(bridge,"batch"),null);
            bridge.reset(); bridge.add(square); bridge.add(square);
            equal("duplicate trees sampled once",((List<?>)get(bridge,"trees")).size(),1);
            Array.set(colors,0,null);
            bridge.capture(renderer,0);
            equal("native filter rejects missing lighting",((List<?>)get(get(bridge,"batch"),"trees")).size(),0);
            Array.set(colors,0,color);
            bridge.reset(); bridge.add(square);
            Object oldSprite=get(tree,"sprite");
            try {
                set(tree,"sprite",null);
                try { bridge.capture(renderer,0); throw new AssertionError("Expected native fixture error"); }
                catch (IllegalStateException expected) {
                    Throwable cause=expected;
                    while (cause.getCause()!=null) cause=cause.getCause();
                    if (!(cause instanceof NullPointerException)) throw expected;
                }
            } finally { set(tree,"sprite",oldSprite); }
            same("failed capture releases native batch",get(bridge,"batch"),null);
            same("failed capture restores current",staticGet("zombie.iso.fboRenderChunk.FBORenderTrees","current"),oldCurrent);
            equal("failed capture restores highlight",((Byte)get(tree,"ppfHighlighted")).intValue(),1);
            equal("failed capture restores mode",(Boolean)get(renderer,"renderTranslucentOnly")?1:0,oldTranslucent?1:0);
            System.out.println("Native tree bridge PASS: production add/capture, actual square filter, lighting/alpha preparation, native records, duplicate/filter guards, normal/exceptional highlight/global restoration and idempotent release.");
        } finally { bridge.release(); }
    }

    private static void fixture() throws Exception {
        invoke(staticGet("zombie.core.random.RandStandard","INSTANCE"),"init",NONE);
        replace("zombie.core.PerformanceSettings","fboRenderChunk",true);
        player=fresh("zombie.characters.IsoPlayer");
        Object players=staticGet("zombie.characters.IsoPlayer","players"),oldPlayer=Array.get(players,0);
        Array.set(players,0,player); restore.add(()->Array.set(players,0,oldPlayer));
        square=fresh("zombie.iso.IsoGridSquare");
        set(square,"x",10); set(square,"y",12); set(square,"z",0);
        set(square,"nav",Array.newInstance(square.getClass(),10));
        Object properties=type("zombie.core.properties.PropertyContainer").getConstructor().newInstance();
        set(square,"properties",properties);
        Class<?> lightType=type("zombie.iso.IsoGridSquare$ILighting");
        Object light=Proxy.newProxyInstance(lightType.getClassLoader(),new Class<?>[]{lightType},(proxy,method,values)-> {
            if (method.getName().equals("bCanSee")) return false;
            throw new AssertionError("Unexpected lighting call "+method);
        });
        Object lighting=Array.newInstance(lightType,4);
        for (int i=0;i<4;i++) Array.set(lighting,i,light);
        set(square,"lighting",lighting);
        Object state=staticGet("zombie.iso.IsoCamera","frameState");
        set(state,"playerIndex",0); set(state,"camCharacterX",10f); set(state,"camCharacterY",12f);
        set(state,"camCharacterZ",0f); set(state,"camCharacterSquare",square);
        Object world=fresh("zombie.iso.IsoWorld"),grid=fresh("zombie.iso.IsoMetaGrid");
        set(grid,"maxX",100); set(world,"metaGrid",grid); replace("zombie.iso.IsoWorld","instance",world);
        time=fresh("zombie.GameTime");
        set(time,"multiplier",2f); set(time,"fpsMultiplier",1f); set(time,"multiplierBias",1f); set(time,"perObjectMultiplier",1f);
        replace("zombie.GameTime","instance",time);
        step=.044999998f*((Number)invoke(time,"getThirtyFPSMultiplier",NONE)).floatValue();
        if (!(step>0 && step<.2)) throw new AssertionError("Invalid native fixture time step "+step);
        Object debug=staticGet("zombie.debug.DebugOptions","instance");
        invoke(get(debug,"renderTreeSeen"),"setValue",new Class<?>[]{boolean.class},false);
        Object renderTiles=get(get(debug,"terrain"),"renderTiles");
        invoke(get(renderTiles,"forceFullAlpha"),"setValue",new Class<?>[]{boolean.class},false);
        Object renderer=staticGet("zombie.iso.fboRenderChunk.FBORenderCell","instance");
        set(renderer,"renderTranslucentOnly",true);
        Class<?> batchType=type("zombie.iso.fboRenderChunk.FBORenderTrees");
        batchType.getMethod("startFrame").invoke(null);
        batch=batchType.getMethod("alloc").invoke(null); invoke(batch,"init",NONE);
        replace(batchType.getName(),"current",batch);
    }

    private static Object tree(Object sprite) throws Exception {
        Object tree=fresh("zombie.iso.objects.IsoTree");
        set(tree,"sprite",sprite); set(tree,"square",square);
        set(tree,"fadeAlpha",1f); set(tree,"cutawayAlpha",1f);
        set(tree,"targetAlpha",new float[]{1,1,1,1}); set(tree,"alpha",new float[]{1,1,1,1});
        return tree;
    }
    private static Object texture(String name) throws Exception {
        Object texture=fresh("zombie.core.textures.Texture");
        set(texture,"name",name); set(texture,"widthOrig",384); set(texture,"heightOrig",512);
        return texture;
    }
    private static Object sprite(String name,Object texture) throws Exception {
        Object sprite=fresh("zombie.iso.sprite.IsoSprite"); set(sprite,"name",name); set(sprite,"texture",texture);
        set(sprite,"def",fresh("zombie.iso.sprite.IsoSpriteInstance")); return sprite;
    }
    @SuppressWarnings("unchecked") private static void registerSprite(String name,Object sprite) throws Exception {
        Map<Object,Object> named=(Map<Object,Object>)get(staticGet("zombie.iso.sprite.IsoSpriteManager","instance"),"namedMap");
        Object old=named.put(name,sprite); restore.add(()->{if(old==null)named.remove(name);else named.put(name,old);});
    }
    @SuppressWarnings("unchecked") private static void registerDescription(String name,String crown,String trunk) throws Exception {
        Class<?> desc=type("zombie.iso.IsoTreeJumbo$TreeDescription");
        Object description=desc.getConstructor(String.class,String.class,String.class,String.class,String.class,String.class)
                .newInstance(name,crown,trunk,"","","");
        Map<Object,Object> descriptions=(Map<Object,Object>)staticGet("zombie.iso.IsoTreeJumbo","Jumbos");
        Object old=descriptions.put(name,description); restore.add(()->{if(old==null)descriptions.remove(name);else descriptions.put(name,old);});
    }
    private static void render(Object tree) throws Exception {
        Object color=type("zombie.core.textures.ColorInfo").getConstructor(float.class,float.class,float.class,float.class).newInstance(.7f,.8f,.9f,1f);
        invoke(tree,"render",new Class<?>[]{float.class,float.class,float.class,color.getClass(),boolean.class,boolean.class,type("zombie.core.opengl.Shader")},10f,12f,0f,color,true,false,null);
    }
    private static List<?> records() throws Exception { return (List<?>)get(batch,"trees"); }
    private static void clearBatch() throws Exception { invoke(batch,"init",NONE); }
    private static Object invoke(Object target,String name,Class<?>[] signature,Object...args) throws Exception {
        try { return target.getClass().getMethod(name,signature).invoke(target,args); }
        catch (InvocationTargetException error) {
            Throwable cause=error.getCause(); if(cause instanceof Exception e)throw e;if(cause instanceof Error e)throw e;throw error;
        }
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    private static Object fresh(String name) throws Exception { return allocate.invoke(unsafe,type(name)); }
    private static Field field(Class<?> owner,String name) throws NoSuchFieldException {
        for(Class<?> c=owner;c!=null;c=c.getSuperclass())try {Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(owner.getName()+"."+name);
    }
    private static Object get(Object target,String name) throws Exception { return field(target.getClass(),name).get(target); }
    private static void set(Object target,String name,Object value) throws Exception { field(target.getClass(),name).set(target,value); }
    private static Object staticGet(String owner,String name) throws Exception { return field(type(owner),name).get(null); }
    private static void replace(String owner,String name,Object value) throws Exception {
        Field f=field(type(owner),name); Object old=f.get(null); f.set(null,value);
        restore.add(()->{try {f.set(null,old);}catch(Exception e){throw new RuntimeException(e);}});
    }
    private static float number(Object object,String name) throws Exception { return ((Number)get(object,name)).floatValue(); }
    private static void equal(String label,int actual,int expected) { if(actual!=expected)throw new AssertionError(label+": "+actual+" expected "+expected); }
    private static void equal(String label,float actual,float expected) { near(label,actual,expected); }
    private static void near(String label,float actual,float expected) { if(!Float.isFinite(actual)||Math.abs(actual-expected)>.00001f)throw new AssertionError(label+": "+actual+" expected "+expected); }
    private static void same(String label,Object actual,Object expected) { if(actual!=expected)throw new AssertionError(label); }
    private static void yes(String label,boolean actual) { if(!actual)throw new AssertionError(label); }
    private static void no(String label,boolean actual) { yes(label,!actual); }
}
