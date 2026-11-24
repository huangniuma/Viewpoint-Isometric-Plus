import java.lang.reflect.*;
import java.util.*;
import vpiso.*;

/** Checks real native alpha ownership while neighbouring capture membership changes. */
public final class NativeTreeLifecycle056Probe {
    private static final Class<?> FIXTURE=NativeTreeProducerProbe.class;
    private static Object renderer,template,colors,player,cell;
    private static float step;

    public static void main(String[] args) throws Exception {
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); uf.setAccessible(true);
        fixtureField("unsafe").set(null,uf.get(null));
        fixtureField("allocate").set(null,uf.getType().getMethod("allocateInstance",Class.class));
        call("fixture",new Class<?>[0]);
        template=fixtureField("square").get(null); player=fixtureField("player").get(null);
        step=(Float)fixtureField("step").get(null);
        renderer=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell","instance",null);
        Object color=Class.forName("zombie.core.textures.ColorInfo").getConstructor(float.class,float.class,float.class,float.class).newInstance(.7f,.8f,.9f,1f);
        colors=Array.newInstance(color.getClass(),4); for(int i=0;i<4;i++) Array.set(colors,i,color);
        set(player,"ecsComponentMap",new HashMap<>());
        cell=fresh("zombie.iso.IsoCell");
        Object area=Class.forName("zombie.iso.IsoCell$StencilArea").getConstructor(int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class)
                .newInstance(-100000,-100000,100000,100000,0,0,100000,100000,0,0);
        set(cell,"stencilAreas",new ArrayList<>(List.of(area)));
        set(GameAccess.field("zombie.iso.IsoWorld","instance",null),"currentCell",cell);
        Object a=tree("a",10,12),b=tree("b",11,11),c=tree("c",12,10);
        set(a,"fadeAlpha",1f); set(a,"cutawayAlpha",1f);
        int samples=0;
        for(int frame=0;frame<160;frame++) {
            // The second half restores the same tree without any visibility/light change.
            set(GameAccess.field("zombie.iso.IsoCamera","frameState",null),"camCharacterX",frame<80?10f:20f);
            float beforeFade=number(a,"fadeAlpha"),beforeCutaway=number(a,"cutawayAlpha");
            List<String> alone=capture(a);
            float afterFade=number(a,"fadeAlpha"),afterCutaway=number(a,"cutawayAlpha");
            if(Math.abs(afterFade-beforeFade)>step+.00001f || Math.abs(afterCutaway-beforeCutaway)>step+.00001f)
                throw new AssertionError("Native producer stepped more than once at "+frame);
            set(a,"fadeAlpha",beforeFade); set(a,"cutawayAlpha",beforeCutaway);
            List<String> withNeighbours=frame%2==0?capture(c,a,b,a):capture(b,a);
            if(!alone.equals(withNeighbours)) throw new AssertionError("Neighbour membership changed A producer records at "+frame+": "+alone+" / "+withNeighbours);
            near(number(a,"fadeAlpha"),afterFade); near(number(a,"cutawayAlpha"),afterCutaway);
            if(frame<80 && afterFade>beforeFade+.00001f || frame>=80 && afterFade+.00001f<beforeFade)
                throw new AssertionError("Stable predicate made alpha reverse at "+frame);
            if(frame==79) near(afterFade,.05f);
            samples++;
        }
        near(number(a,"fadeAlpha"),1f);
        System.out.println("Native tree lifecycle 056 PASS: "+samples+" original producer frames; hide and recovery monotonic; duplicate discovery and alternating 1/2/3-tree membership preserve tree A texture, fade/cutaway alpha and stencil flags; one alpha step per capture. Controlled CPU fixture, no game or GPU draw.");
    }

    private static List<String> capture(Object... trees) throws Exception {
        var bridge=new NativeTrees();
        try {
            for(Object tree:trees) bridge.add(get(tree,"square"));
            bridge.capture(renderer,0);
            List<String> result=new ArrayList<>();
            for(Object record:(List<?>)get(get(bridge,"batch"),"trees")) {
                if(!get(get(record,"texture"),"name").equals("a")) continue;
                result.add(get(record,"fadeAlpha")+"/"+get(record,"cutawayAlpha")+"/"+get(record,"useStencil")+"/"+get(record,"transparent"));
            }
            if(result.size()!=1)throw new AssertionError("Expected one A record, got "+result);
            return result;
        } finally { bridge.release(); }
    }

    private static Object tree(String label,int x,int y) throws Exception {
        Object sq=fresh("zombie.iso.IsoGridSquare"); set(sq,"x",x);set(sq,"y",y);set(sq,"z",0);
        for(String name:new String[]{"properties","nav","lighting"})set(sq,name,get(template,name));
        set(sq,"lightInfo",colors);set(sq,"targetPlayerCutawayFlags",new byte[4]);set(sq,"playerCutawayFlags",new byte[4]);set(sq,"playerCutawayFlagLockUntilTimes",new long[4]);
        Object texture=call("texture",new Class<?>[]{String.class},label);
        Object sprite=call("sprite",new Class<?>[]{String.class,Object.class},"VPISO_lifecycle_"+label,texture);
        set(sprite,"properties",Class.forName("zombie.core.properties.PropertyContainer").getConstructor().newInstance());
        Object tree=call("tree",new Class<?>[]{Object.class},sprite);set(tree,"square",sq);
        set(sq,"objects",new ArrayList<>(List.of(tree))); return tree;
    }
    private static Field fixtureField(String n)throws Exception{var f=FIXTURE.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Object call(String n,Class<?>[] types,Object...args)throws Exception{var m=FIXTURE.getDeclaredMethod(n,types);m.setAccessible(true);try{return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}}
    private static Object fresh(String name)throws Exception{return call("fresh",new Class<?>[]{String.class},name);}
    private static Object get(Object o,String n)throws Exception{return call("get",new Class<?>[]{Object.class,String.class},o,n);}
    private static void set(Object o,String n,Object v)throws Exception{call("set",new Class<?>[]{Object.class,String.class,Object.class},o,n,v);}
    private static float number(Object o,String n)throws Exception{return ((Number)get(o,n)).floatValue();}
    private static void near(float actual,float expected){if(Math.abs(actual-expected)>.00001f)throw new AssertionError(actual+" expected "+expected);}
}
