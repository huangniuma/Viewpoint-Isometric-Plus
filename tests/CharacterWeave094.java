import java.lang.reflect.*;
import java.nio.file.*;
import java.util.zip.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Reproduce093 with its exact historical eligibility advice, without changing history. */
public final class CharacterWeave094 extends ClassLoader {
 CharacterWeave094(){super(CharacterWeave094.class.getClassLoader());}
 public static void main(String[] args)throws Exception {
  byte[] original;try(ZipFile z=new ZipFile(args[0])){original=z.getInputStream(z.getEntry("vpiso/Patch_CharacterVisibility.class")).readAllBytes();}
  Method convert=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
  byte[] bytes=(byte[])convert.invoke(null,(Object)original);CharacterWeave094 loader=new CharacterWeave094();
  Class<?> advice=loader.defineClass("vpiso.Patch_CharacterVisibility",bytes,0,bytes.length);
  byte[] woven=new ByteBuddy().redefine(Class.forName("viewpoint.models.Characters")).visit(Advice.to(advice).on(named("inRange"))).make().getBytes();
  Path out=Path.of(args[1],"viewpoint/models/Characters.class");Files.createDirectories(out.getParent());Files.write(out,woven);
 }
}
