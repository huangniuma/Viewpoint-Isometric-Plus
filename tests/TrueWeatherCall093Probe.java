import java.nio.file.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import viewpoint.core.Frame;
import vpiso.*;

/** Executes the actual woven entry/early call, replacing only the external effect body with a counter. */
public final class TrueWeatherCall093Probe {
 static final List<String> calls=new ArrayList<>();static boolean fail;
 public static void mark(){calls.add("details");if(IsometricBridge.modelRendering()&&TrueWeatherCompat.skipLate())throw new AssertionError("early invocation suppressed");if(fail)throw new IllegalStateException("fixture draw failure");}
 public static void main(String[] args)throws Exception {
  if(args.length>0&&args[0].equals("prepare")) {
   Path input=Path.of(args[1],"vptw/AmbientDraw.class"),output=Path.of(args[2],"vptw/AmbientDraw.class");
   ClassReader reader=new ClassReader(Files.readAllBytes(input));ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
   reader.accept(new ClassVisitor(Opcodes.ASM9,writer){public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){MethodVisitor next=super.visitMethod(access,name,desc,sig,exceptions);if(!name.equals("draw"))return next;return new MethodVisitor(Opcodes.ASM9,next){boolean marked;public void visitFieldInsn(int opcode,String owner,String field,String descriptor){if(!marked&&opcode==Opcodes.GETSTATIC&&owner.equals("vptw/AmbientDraw")&&field.equals("failed")){marked=true;super.visitMethodInsn(Opcodes.INVOKESTATIC,"TrueWeatherCall093Probe","mark","()V",false);super.visitInsn(Opcodes.RETURN);}super.visitFieldInsn(opcode,owner,field,descriptor);}};}} ,0);
   Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());return;
  }
  Frame frame=new Frame();
  for(int i=0;i<3;i++) {
   calls.clear();IsometricBridge.modelRendering(frame,IsometricEffects.DEFAULT);
   TrueWeatherCompat.beforeWeather(frame.scene);calls.add("weather");late(frame);
   require(calls.equals(List.of("details","weather")),"duplicate or late draw "+calls);
   calls.clear();IsometricBridge.rendering(null);TrueWeatherCompat.beforeWeather(frame.scene);late(frame);
   require(calls.equals(List.of("details")),"original VP callback missing "+calls);
  }
  calls.clear();IsometricBridge.modelRendering(frame,IsometricEffects.DEFAULT);fail=true;
  try{TrueWeatherCompat.beforeWeather(frame.scene);throw new AssertionError("failure not propagated");}catch(IllegalStateException expected){}
  finally{fail=false;}
  require(TrueWeatherCompat.skipLate(),"early scope leaked on failure");IsometricBridge.rendering(null);
  System.out.println("TrueWeather093 woven calls PASS: once before weather, original VP retained over three toggles, failed early draw restores scope.");
 }
 static void late(Frame frame){GameAccess.call("vptw.AmbientDraw","draw",null,new Class<?>[]{Object.class,float.class,float.class,float.class},frame.scene,0f,1.5f,0f);}
 static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
