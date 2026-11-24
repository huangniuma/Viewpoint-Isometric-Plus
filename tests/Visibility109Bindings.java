import java.lang.invoke.*;
import java.lang.reflect.*;

/** Test-only adapters for old Method fixtures and current exact handles. */
public final class Visibility109Bindings {
 public static void set(Object api,String name,Object value)throws Exception {
  Method method=(Method)value;Field field=api.getClass().getDeclaredField(name);field.setAccessible(true);field.set(api,method);
  Field exact;try {exact=api.getClass().getDeclaredField(name+"Handle");}catch(NoSuchFieldException oldArtifact){return;}
  MethodHandle handle=MethodHandles.lookup().unreflect(method);
  if(Modifier.isStatic(method.getModifiers()))handle=MethodHandles.dropArguments(handle,0,Object.class);
  MethodType type=switch(name) {
   case "square" -> MethodType.methodType(Object.class,Object.class,int.class,int.class,int.class);
   case "level" -> MethodType.methodType(int.class,Object.class);
   default -> MethodType.methodType(boolean.class,Object.class,int.class,Object.class);
  };
  exact.setAccessible(true);exact.set(api,handle.asType(type));
 }
 private Visibility109Bindings(){}
}
