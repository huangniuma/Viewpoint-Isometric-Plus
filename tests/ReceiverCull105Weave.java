import java.io.File;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import static net.bytebuddy.matcher.ElementMatchers.*;
import org.lwjgl.opengl.*;

/** Test-only per-receiver submission query. No query or observer ships in the mod. */
public final class ReceiverCull105Weave {
    public static class Count {
        @Advice.OnMethodEnter public static int enter() {
            int id=GL15.glGenQueries();GL15.glBeginQuery(GL30.GL_PRIMITIVES_GENERATED,id);return id;
        }
        @Advice.OnMethodExit public static void exit(@Advice.Enter int id) {
            GL15.glEndQuery(GL30.GL_PRIMITIVES_GENERATED);
            ReceiverCull105Probe.primitives=GL15.glGetQueryObjecti(id,GL15.GL_QUERY_RESULT);GL15.glDeleteQueries(id);
        }
    }
    public static void main(String[] args)throws Exception {
        new ByteBuddy().redefine(Class.forName("vpiso.NativeSurfaces"))
            .visit(Advice.to(Count.class).on(named("draw").and(takesArguments(3))))
            .make().saveIn(new File(args[0]));
    }
}
