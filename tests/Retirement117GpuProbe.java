import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.render.Retirement;
import vpiso.*;

/** Real GPU fences and objects through the woven original collector; controlled hidden context. */
public final class Retirement117GpuProbe {
    static final List<Integer> released = new ArrayList<>();
    static Object owner;
    static Method pending;
    static int count() throws Exception { return (int) pending.invoke(owner); }
    static void require(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
    public static void main(String[] args) throws Exception {
        require(GLFW.glfwInit(), "GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        long window = GLFW.glfwCreateWindow(32, 32, "117 real safe retirement", 0, 0);
        require(window != 0L, "hidden context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            Field single = Retirement.class.getDeclaredField("ON_GPU"); single.setAccessible(true); owner = single.get(null);
            pending = Retirement.class.getDeclaredMethod("pending"); pending.setAccessible(true);
            Frame frame = new Frame(); frame.number = 1000;
            Main.compatible = true;
            Retirement.building(frame.number);
            int[] buffers = new int[40];
            for (int i=0; i<buffers.length; i++) {
                final int ordinal = i;
                int id = buffers[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
                IntBuffer values = BufferUtils.createIntBuffer(256);
                while(values.hasRemaining()) values.put(ordinal); values.flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, values, GL15.GL_STATIC_DRAW);
                Retirement.retire(() -> {
                    require(GL15.glIsBuffer(id), "object already deleted " + ordinal);
                    GL15.glDeleteBuffers(id); released.add(ordinal);
                });
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            Retirement.drawn(frame.number+1); GL11.glFlush();
            require(count()==40 && released.isEmpty(), "submission freed resources early");
            IsometricBridge.rendering(frame);
            long deadline = System.nanoTime()+5_000_000_000L;
            while(released.isEmpty() && System.nanoTime()<deadline) { Retirement.collect(); Thread.yield(); }
            int first = released.size();
            require(first>0 && first<=16 && count()==40-first, "first safe prefix not bounded");
            for(int i=0;i<10;i++) Retirement.collect();
            require(released.size()==first, "same rendered frame spent another budget");
            int passes=1;
            while(count()>0 && passes<50) {
                int before = released.size(); frame.number++; Retirement.collect(); passes++;
                require(released.size()>before && released.size()-before<=16, "next frame budget/progress");
            }
            require(count()==0 && released.size()==40, "retirement incomplete");
            for(int i=0;i<40;i++) {
                require(released.get(i)==i, "release order changed");
                require(!GL15.glIsBuffer(buffers[i]), "GL object leaked");
            }
            require(!Retirement.holding(), "original holding still true");
            IsometricBridge.rendering(null); RetirementBudget.forceReady();
            require(GL11.glGetError()==0, "GL error");
            System.out.println("RETIREMENT117 GPU PASS objects=40 frames="+passes+" firstPrefix="+first+
                    "; actual woven original Retirement, real GL fences, exact pending/FIFO/object deletion, same-frame budget and no early deletion; no live traversal/FPS claim.");
        } finally {
            IsometricBridge.rendering(null); Main.compatible=false;
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }
}
