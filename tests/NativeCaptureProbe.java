import java.nio.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.NativeOverlay;

/** The displayed native color must never come from the ground-only auxiliary image. */
public final class NativeCaptureProbe {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(16,16,"native color capture",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            int input=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,input);
            int ground=0;
            for(int slot:new int[]{0,7}) {
                int texture=GL11.glGenTextures();if(slot==7)ground=texture;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,16,16,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL11.GL_TEXTURE_2D,texture,0);
            }
            int[] buffers=new int[8];buffers[0]=GL30.GL_COLOR_ATTACHMENT0;buffers[7]=GL30.GL_COLOR_ATTACHMENT0+7;
            GL20.glDrawBuffers(buffers);
            GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{.5f,.625f,.75f,1});
            GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{0,0,0,1});GL11.glViewport(0,0,16,16);
            NativeOverlay overlay=new NativeOverlay();overlay.ground(ground);
            var color=NativeOverlay.class.getDeclaredField("color");color.setAccessible(true);
            var copy=NativeOverlay.class.getDeclaredField("groundCopy");copy.setAccessible(true);
            int readback=GL30.glGenFramebuffers();
            for(int frame=0;frame<24;frame++)for(int source:new int[]{0,7}) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,input);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+source);
                overlay.capture(false);
                if(GL11.glGetInteger(GL11.GL_READ_BUFFER)!=GL30.GL_COLOR_ATTACHMENT0+source)
                    throw new AssertionError("Original framebuffer read source not restored");
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readback);
                for(int texture:new int[]{color.getInt(overlay),copy.getInt(overlay)}) {
                    GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    ByteBuffer b=BufferUtils.createByteBuffer(16*16*4);
                    GL11.glReadPixels(0,0,16,16,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
                    int[] expected=texture==color.getInt(overlay)?new int[]{128,159,191}:new int[]{0,0,0};
                    for(int i=0;i<16*16;i++)for(int channel=0;channel<3;channel++)
                        if((b.get(i*4+channel)&255)!=expected[channel])
                            throw new AssertionError("Auxiliary image replaced native color: inherited="+source+" frame="+frame+" pixel="+i);
                }
                if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            }
            System.out.println("Native color capture GPU PASS: 48 captures, inherited attachments0/7, original RGB preserved, zero contribution kept separate, original read state restored.");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
