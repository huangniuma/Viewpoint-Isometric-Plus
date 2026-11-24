import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Actual native attachment ownership, unknown-shader fallback and indexed-state restoration. */
public final class NativeRelightSlots069Probe {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    static int attachment(int slot){return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);}
    public static void main(String[] args)throws Exception {
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(32,32,"native069 slots",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            require(NativeGroundShaders.PZO_FINAL,"Actual installed PZO final-source boundary absent");
            int target=NativeShadow068Probe.texture(GL30.GL_RGBA32F,32,32),fbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            String source=NativeGroundShaders.rewrite("fixture.frag","#version 330\nout vec4 color;void main(){color=vec4(.2,.3,.4,1);}");
            int program=NativeGroundProbe.link(NativeGroundProbe.VERTEX,source);
            GL20.glUseProgram(program);GL30.glDisablei(GL11.GL_BLEND,4);GL30.glEnablei(GL11.GL_BLEND,5);
            GL40.glBlendFuncSeparatei(4,GL11.GL_DST_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_SRC_ALPHA);
            GL40.glBlendEquationSeparatei(4,GL14.GL_FUNC_SUBTRACT,GL14.GL_FUNC_REVERSE_SUBTRACT);GL30.glColorMaski(4,false,true,false,true);
            NativeGround.begin(true);require(NativeGround.relightTexture()!=0,"Empty slots were not borrowed");
            require(attachment(4)!=0 && attachment(5)!=0,"Missing relight attachment");
            NativeGround.beforeDraw();NativeGround.finish();
            require(attachment(4)==0 && attachment(5)==0,"Borrowed slots still attached");
            require(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+4)==0 && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+5)==0,"Borrowed slots still enabled");
            require(!GL30.glIsEnabledi(GL11.GL_BLEND,4) && GL30.glIsEnabledi(GL11.GL_BLEND,5),"Indexed blend enable leaked");
            require(GL30.glGetIntegeri(GL14.GL_BLEND_SRC_RGB,4)==GL11.GL_DST_ALPHA && GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_ALPHA,4)==GL14.GL_FUNC_REVERSE_SUBTRACT,"Indexed factors/equations leaked");
            var mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,4,mask);
            require(mask.get(0)==0 && mask.get(1)!=0 && mask.get(2)==0 && mask.get(3)!=0,"Indexed color mask leaked");
            // An unrewritten draw invalidates this frame rather than leaving false floor ownership.
            NativeGround.begin(true);GL20.glUseProgram(0);NativeGround.beforeDraw();require(NativeGround.relightTexture()==0,"Unknown shader reused valid floor");NativeGround.finish();
            GL20.glUseProgram(program);
            // Another mod owns attachment4 even with its draw buffer disabled.
            int foreign=NativeShadow068Probe.texture(GL30.GL_RGBA32F,32,32);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT4,GL11.GL_TEXTURE_2D,foreign,0);
            NativeGround.begin(true);require(NativeGround.relightTexture()==0,"Foreign attachment was borrowed");NativeGround.finish();
            require(attachment(4)==foreign && attachment(5)==0,"Foreign attachment overwritten");
            NativeGround.release(fbo);require(GL11.glGetError()==0,"GL error");
            System.out.println("Native relight069 slots GPU PASS: actual installed PZO boundary, empty-slot borrow/detach, indexed blend/factor/equation/color-mask restore, unknown-shader frame fallback, foreign-attachment preservation.");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
