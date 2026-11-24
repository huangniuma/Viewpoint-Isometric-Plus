import java.lang.reflect.*;
import java.util.*;
import viewpoint.core.Frame;
import vpiso.*;
import zombie.iso.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Real native lighting interface; explored, fading and neighbouring squares stay native. */
public final class NativeRelightMask069Probe {
    public static final class Lookup {
        static final Map<Long,IsoGridSquare> squares=new HashMap<>();
        public static IsoGridSquare find(int x,int y,int z){return z==0?squares.get(key(x,y)):null;}
    }
    static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        Field f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object unsafe=f.get(null);
        Method allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
        byte[] world=new ByteBuddy().redefine(IsoCell.class).method(named("getGridSquare").and(takesArguments(int.class,int.class,int.class)))
                .intercept(MethodDelegation.to(Lookup.class)).make().getBytes();
        byte[] producer=NativeRelightMask.class.getResourceAsStream("NativeRelightMask.class").readAllBytes();
        ClassLoader loader=new ClassLoader(NativeRelightMask069Probe.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.equals("zombie.iso.IsoCell") && !name.equals("vpiso.NativeRelightMask"))return super.loadClass(name,resolve);
                Class<?> c=findLoadedClass(name);if(c==null){byte[] b=name.endsWith("IsoCell")?world:producer;c=defineClass(name,b,0,b.length);}
                if(resolve)resolveClass(c);return c;
            }
        };
        Object cell=allocate.invoke(unsafe,loader.loadClass("zombie.iso.IsoCell"));
        boolean[] canSee={true};float[] dark={1};
        IsoGridSquare.ILighting light=(IsoGridSquare.ILighting)Proxy.newProxyInstance(
                NativeRelightMask069Probe.class.getClassLoader(),new Class<?>[]{IsoGridSquare.ILighting.class},(p,m,a)-> {
                    if(m.getName().equals("bCanSee"))return canSee[0];
                    if(m.getName().equals("bSeen"))return true;
                    if(m.getName().equals("darkMulti"))return dark[0];
                    if(m.getReturnType()==boolean.class)return false;
                    if(m.getReturnType()==float.class)return 0f;
                    if(m.getReturnType()==int.class)return 0;
                    return null;
                });
        for(int y=-33;y<=32;y++)for(int x=-33;x<=32;x++) {
            IsoGridSquare square=(IsoGridSquare)allocate.invoke(unsafe,IsoGridSquare.class);
            Field lights=IsoGridSquare.class.getField("lighting");lights.setAccessible(true);lights.set(square,new IsoGridSquare.ILighting[]{light});Lookup.squares.put(key(x,y),square);
        }
        Frame frame=new Frame();Class<?> maskType=loader.loadClass("vpiso.NativeRelightMask");Object mask=maskType.getConstructor().newInstance();
        Method capture=maskType.getMethod("capture",Frame.class,Object.class,int.class);byte[] pixels=(byte[])maskType.getField("pixels").get(mask);
        capture.invoke(mask,frame,cell,0);for(byte v:pixels)require(v==-1,"Fully seen floor rejected");
        canSee[0]=false;capture.invoke(mask,frame,cell,0);for(byte v:pixels)require(v==0,"Explored square revealed");
        canSee[0]=true;dark[0]=.99999f;capture.invoke(mask,frame,cell,0);for(byte v:pixels)require(v==0,"Native fade rounded to fully lit");
        dark[0]=1;Lookup.squares.remove(key(0,0));capture.invoke(mask,frame,cell,0);
        for(int y=31;y<=33;y++)for(int x=31;x<=33;x++)require(pixels[y*64+x]==0,"Missing neighbour accepted");
        require(pixels[0]==-1,"Unrelated visible floor affected");
        frame.camZ=1;capture.invoke(mask,frame,cell,0);for(byte v:pixels)require(v==0,"Previous floor mask retained");
        System.out.println("Native relight069 visibility mask PASS: current canSee, exact full fade, 3x3 neighbourhood, missing square, floor change; remembered-only native light never rebased.");
    }
}
