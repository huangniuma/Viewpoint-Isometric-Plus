import java.io.File;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Test-only single draw interception; never included in an installed artifact. */
public final class PerformanceWeave108 {
    public static void main(String[] args)throws Exception {
        try(var locator=new ClassFileLocator.Compound(ClassFileLocator.ForJarFile.of(new File(args[0])),ClassFileLocator.ForClassLoader.ofSystemLoader())) {
            var pool=TypePool.Default.of(locator);
            new ByteBuddy().redefine(pool.describe("vpiso.NativeOverlay").resolve(),locator)
                .visit(MemberSubstitution.strict().method(named("glDrawArrays").and(isDeclaredBy(named("org.lwjgl.opengl.GL11"))))
                    .replaceWith(NativePerformance108Probe.class.getMethod("timedDraw",int.class,int.class,int.class)).on(named("draw")))
                .make().saveIn(new File(args[1]));
        }
        System.out.println("108 isolated GPU draw timer prepared");
    }
}
