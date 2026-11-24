import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import vpiso.*;
/** The actual woven game loader must capture default.frag before addon activation. */
public final class NativeBootstrap081Probe {
 public static void main(String[] args)throws Exception {
  Main.compatible=false;
  String name="zombie.core.opengl.ShaderUnit";
  byte[] woven=Files.readAllBytes(Path.of(args[0],"zombie/core/opengl/ShaderUnit.class"));
  var locator=new net.bytebuddy.dynamic.ClassFileLocator.Compound(net.bytebuddy.dynamic.ClassFileLocator.Simple.of(name,woven),net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
  // Isolate the game's filesystem/preprocessor while executing its actual
  // woven loadShaderFile return path with the installed default source.
  byte[] bytes=new net.bytebuddy.ByteBuddy().redefine(net.bytebuddy.pool.TypePool.Default.of(locator).describe(name).resolve(),locator)
      .method(net.bytebuddy.matcher.ElementMatchers.named("preProcessShaderFile"))
      .intercept(net.bytebuddy.implementation.FixedValue.value(Files.readString(Path.of(args[1],"media/shaders/default.frag"))))
      .make().getBytes();
  ClassLoader loader=new ClassLoader(NativeBootstrap081Probe.class.getClassLoader()) {
   @Override protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException {
    if(!n.equals("zombie.core.opengl.ShaderUnit"))return super.loadClass(n,resolve);
    Class<?> c=findLoadedClass(n);if(c==null)c=defineClass(n,bytes,0,bytes.length);if(resolve)resolveClass(c);return c;
   }
  };
  Class<?> type=loader.loadClass("zombie.core.opengl.ShaderUnit");
  Field combine=type.getDeclaredField("combineShaderSources");combine.setAccessible(true);combine.setBoolean(null,false);
  Object unit=type.getConstructor(zombie.core.opengl.ShaderProgram.class,String.class,zombie.core.opengl.ShaderUnit.Type.class)
      .newInstance(null,"default",zombie.core.opengl.ShaderUnit.Type.Frag);
  Method m=type.getDeclaredMethod("loadShaderFile",String.class,ArrayList.class);m.setAccessible(true);
  String text=(String)m.invoke(unit,Path.of(args[1],"media/shaders/default.frag").toString(),new ArrayList<String>());
  if(text==null || !text.contains("VPISO_GROUND_CONTRIBUTION") || !text.contains("vpIsoFloorWrite"))throw new AssertionError("Bootstrap default shader missing capture before Main.compatible: "+text);
  String other=(String)m.invoke(unit,Path.of(args[1],"media/shaders/chunkShader.frag").toString(),new ArrayList<String>());
  if(other==null || other.contains("VPISO_GROUND_CONTRIBUTION"))throw new AssertionError("Early hook escaped default.frag");
  System.out.println("Native bootstrap081 PASS: actual woven ShaderUnit, default capture before addon compatibility check; other programs retain final-source hook.");
 }
}
