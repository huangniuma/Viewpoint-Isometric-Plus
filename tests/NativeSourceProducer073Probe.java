import java.lang.reflect.*;
import java.util.*;
import vpiso.*;

/** Execute actual production-woven native tree and VP plant producers without a running world. */
public final class NativeSourceProducer073Probe {
    static final Class<?> P=NativeTreeProducerProbe.class;
    public static void main(String[] args)throws Exception {
        if(args.length==0) {
            ClassLoader loader=new ClassLoader(NativeSourceProducer073Probe.class.getClassLoader()) {
                @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                    boolean own=name.indexOf('.')<0 || name.startsWith("zombie.") || name.startsWith("viewpoint.") || name.startsWith("vpiso.") || name.startsWith("pzopt.") || name.startsWith("fmod.");
                    if(!own)return super.loadClass(name,resolve);
                    Class<?> c=findLoadedClass(name);
                    if(c==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                        if(input==null)throw new ClassNotFoundException(name);byte[] b=input.readAllBytes();c=defineClass(name,b,0,b.length);
                    }catch(java.io.IOException e){throw new ClassNotFoundException(name,e);}
                    if(resolve)resolveClass(c);return c;
                }
            };
            try{loader.loadClass(NativeSourceProducer073Probe.class.getName()).getMethod("main",String[].class).invoke(null,(Object)new String[]{"isolated"});}
            catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}
            return;
        }
        Class<?> u=Class.forName("sun.misc.Unsafe");Field singleton=u.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
        field("unsafe").set(null,unsafe);field("allocate").set(null,u.getMethod("allocateInstance",Class.class));
        invoke("fixture",new Class<?>[0]);
        Object square=field("square").get(null);
        Object texture=new FixtureTexture();
        GameAccess.set("zombie.core.textures.Texture","widthOrig",texture,384);GameAccess.set("zombie.core.textures.Texture","heightOrig",texture,512);
        GameAccess.set("zombie.core.textures.Texture","width",texture,384);GameAccess.set("zombie.core.textures.Texture","height",texture,512);
        Object sprite=invoke("sprite",new Class<?>[]{String.class,Object.class},"VPISO_fixture_normal",texture);
        Object tree=invoke("tree",new Class<?>[]{Object.class},sprite);
        GameAccess.set("zombie.iso.IsoGridSquare","objects",square,new ArrayList<>(List.of(tree)));
        GameAccess.set("vpiso.IsometricBridge","groundRequested",null,true);
        Main.compatible=true;
        try {
            invoke("render",new Class<?>[]{Object.class},tree);
            List<?> records=(List<?>)invoke("records",new Class<?>[0]);require(records.size()==1,"Native tree record");
            Object record=records.get(0);NativeSource source=NativeTreeSources.source(record);
            require(source!=null && source.x==10.5f && source.y==12.5f && source.level==0,"Native tree root convention");
            require(Arrays.equals(source.owner,NativeSource.ownerKey(10,12,0,0)),"Actual native tree lost logical object key");
            GameAccess.set("zombie.iso.fboRenderChunk.FBORenderTrees$Tree","x",record,12f);
            require(NativeTreeSources.source(record).owner[1]==0,"Reused pooled tree borrowed stale native owner");
            Object cooker=NativePackedPlants072Probe.fresh("viewpoint.world.Cook$Cooker");
            Object op=NativePackedPlants072Probe.fresh("viewpoint.world.Recipe$Op");
            String type="viewpoint.world.Recipe$Op";GameAccess.set(type,"face",op,2);GameAccess.set(type,"object",op,(4<<16)|3);
            GameAccess.set(type,"map",op,new float[]{0,0,1,1});GameAccess.set(type,"card",op,new float[]{-.7f,.7f,0,2});
            GameAccess.call(cooker.getClass().getName(),"plant",cooker,new Class<?>[]{op.getClass()},op);
            float[] plants=(float[])GameAccess.field(cooker.getClass().getName(),"plants",cooker);
            int end=((Number)GameAccess.field(cooker.getClass().getName(),"plantFloats",cooker)).intValue();
            require(end>=28,"VP crossed plant producer empty");
            for(int at=0;at<end;at+=14)require(plants[at+13]==((4<<16)|3)+1,"Actual Cook.plant lost exact original object token");
            System.out.println("Native source producers073 PASS: production-woven native IsoTree.render batch ownership/root/pooled-record guard and Cook.plant crossed-card tokens; no game attach or world writes.");
        }finally{GameAccess.set("vpiso.IsometricBridge","groundRequested",null,false);}
    }
    static Field field(String name)throws Exception {Field f=P.getDeclaredField(name);f.setAccessible(true);return f;}
    static Object invoke(String name,Class<?>[] types,Object...args)throws Exception {Method m=P.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
    static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
    public static final class FixtureTexture extends zombie.core.textures.Texture {
        public FixtureTexture(){super();}
        @Override public int getWidth(){return 384;}
        @Override public int getHeight(){return 512;}
    }
}
