import java.nio.file.*;
import java.util.zip.*;
import net.bytebuddy.jar.asm.*;
/** Offline test-only call counter, never packaged in the renderer. */
public final class Uniform109Weave {
 public static void main(String[] args)throws Exception {
  Path output=Path.of(args[1]);Files.createDirectories(output);
  try(ZipFile jar=new ZipFile(args[0])) {
   var entries=jar.entries();
   while(entries.hasMoreElements()){
    var e=entries.nextElement();if(!e.getName().startsWith("vpiso/NativeGround")||!e.getName().endsWith(".class"))continue;
    byte[] data=jar.getInputStream(e).readAllBytes();
    if(e.getName().equals("vpiso/NativeGround.class")) {
     ClassWriter writer=new ClassWriter(0);
     new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9,writer){
      @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,desc,sig,ex)){
       @Override public void visitMethodInsn(int op,String owner,String n,String d,boolean itf){
        if(owner.equals("org/lwjgl/opengl/GL20")&&n.equals("glUniform1f")&&d.equals("(IF)V"))super.visitMethodInsn(Opcodes.INVOKESTATIC,"NativeDraw109Probe","uniform",d,false);
        else super.visitMethodInsn(op,owner,n,d,itf);
       }
      };}
     },0);data=writer.toByteArray();
    }
    Path target=output.resolve(e.getName());Files.createDirectories(target.getParent());Files.write(target,data);
   }
  }
  System.out.println("Uniform109 counter weave PASS: isolated NativeGround copy only.");
 }
}
