import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Two cached destinations and a clear/repaint world pass, using production source refcounts. */
public final class NativeSourceLifetime088Probe {
    public static void main(String[] args)throws Exception {
        boolean baseline=args.length>0;
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(8,8,"088 source lifetime",0,0);int output=0;
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();output=NativeWallSources071Probe.fbo();
            NativeSource source=new NativeSource(10.5f,20.5f,0,5,new float[]{-.5f,0,-.5f,.5f,1,.5f},0);
            NativeGround.begin(true);NativeGround.sourceBegin(source);int first=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();NativeGround.finish();
            NativeGround.begin(true);
            float[] data=(float[])GameAccess.field("vpiso.NativeGround","SOURCE_DATA",null);
            if(baseline){if(data[first*4+3]!=0)throw new AssertionError("087 negative control did not clear source immediately");System.out.println("Previous087 source lifetime negative PASS: drawing pass loses source record before repaint.");return;}
            if(data[first*4+3]!=5)throw new AssertionError("Source disappeared while pending draws can name it");
            NativeGround.sourceBegin(source);int second=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();
            if(second!=first)throw new AssertionError("Same source changed identity across clear/repaint");NativeGround.finish();
            NativeGround.begin(true);NativeGround.finish();if(data[first*4+3]!=0)throw new AssertionError("Unreferenced source not collected after pass");
            NativeGround.begin(true);NativeGround.sourceBegin(new NativeSource(50.5f,20.5f,0,3,new float[]{-.5f,0,-.5f,.5f,1,.5f},0));int reuse=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();
            if(reuse!=first || data[first*4]!=50.5f)throw new AssertionError("Retired source not safely reused");NativeGround.finish();
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");System.out.println("Source lifetime088 GPU PASS: stable pending identity, re-registration, post-pass collection and safe reuse.");
        }finally{NativeGround.finish();if(output!=0)NativeGround.release(output);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
