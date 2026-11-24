import java.nio.*;
import java.security.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Actual production upload and readback; CPU-prefix adapter is not on this classpath. */
public final class Range107GpuProbe {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(32,32,"107 range upload",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();MessageDigest hash=MessageDigest.getInstance("SHA-256");
            NativeObjectState state=new NativeObjectState();
            ((List<Object>)GameAccess.field("vpiso.NativeObjectState","ids",state)).add(null);int cases=0;
            for(int seed=0;seed<3;seed++)for(int layout=0;layout<12;layout++)for(int count:new int[]{0,1,2,129,1024,8192,17,0}) {
                Range107Probe.fill(state,count,layout,seed);state.beginRender();
                try {
                    Range107Probe.UPLOAD.invoke(state);
                    int buffer=((Number)GameAccess.field("vpiso.NativeObjectState","tableBuffer",state)).intValue();
                    GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER,buffer);
                    int bytes=GL15.glGetBufferParameteri(GL31.GL_TEXTURE_BUFFER,GL15.GL_BUFFER_SIZE);
                    ByteBuffer read=org.lwjgl.BufferUtils.createByteBuffer(bytes);GL15.glGetBufferSubData(GL31.GL_TEXTURE_BUFFER,0,read);
                    byte[] values=new byte[bytes];read.get(values);hash.update(values);
                    ByteBuffer header=ByteBuffer.allocate(24);
                    for(int n:(int[])GameAccess.field("vpiso.NativeObjectState","starts",state))header.putInt(n);
                    for(int n:(int[])GameAccess.field("vpiso.NativeObjectState","counts",state))header.putInt(n);hash.update(header.array());
                    if(!args[0].equals("baseline"))Range107Probe.safety(state);
                    cases++;
                } finally {state.endRender();}
            }
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println("RANGEGPU107 PASS cases="+cases+" sha256="+HexFormat.of().formatHex(hash.digest())+"; actual GPU buffer words/start/count, growth/shrink/equal keys/NaN/high IDs; no adapter, no live FPS.");
        } finally {GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
}
