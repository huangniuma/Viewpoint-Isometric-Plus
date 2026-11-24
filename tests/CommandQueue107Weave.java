import java.io.File;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Test-only queue interception avoids game/render-thread launch. */
public final class CommandQueue107Weave {
    public static class Capture {
        @Advice.OnMethodEnter(skipOn=Advice.OnNonDefaultValue.class) public static boolean enter(@Advice.Argument(0) Object value) {
            CommandQueue107Probe.accept(value);return true;
        }
    }
    public static void main(String[] args)throws Exception {
        new ByteBuddy().redefine(Class.forName("vpiso.NativeGround"))
            .visit(Advice.to(Capture.class).on(named("queue"))).make().saveIn(new File(args[0]));
    }
}
