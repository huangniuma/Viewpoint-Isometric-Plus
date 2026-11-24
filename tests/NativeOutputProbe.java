import java.nio.*;
import java.nio.file.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.NativeGroundShaders;

/** Native legacy output broadcast is part of the shader contract, including outside addon scope. */
public final class NativeOutputProbe {
    static final int W=8,H=8;
    static final String VERTEX="""
        #version 330 compatibility
        out vec4 col; out vec2 texCoord;
        void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);
          col=vec4(.7,.8,.9,1);texCoord=p;}
        """;
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(W,H,"native original outputs",0,0);
        try{
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,GL30.glGenFramebuffers());
            int[] buffers=new int[8];
            for(int i=0;i<8;i++){
                int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,W,H,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+i,GL11.GL_TEXTURE_2D,t,0);
                buffers[i]=GL30.GL_COLOR_ATTACHMENT0+i;
            }
            GL20.glDrawBuffers(buffers);GL11.glViewport(0,0,W,H);
            GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
            String source=Files.readString(Path.of(args[0],"media/shaders/default.frag"));
            int original=link(source),patched=link(NativeGroundShaders.rewrite("default.frag",source));
            GL20.glUseProgram(original);GL20.glUniform1i(GL20.glGetUniformLocation(original,"useTexture"),0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            byte[][] baseline=new byte[7][];
            for(int i=0;i<7;i++)baseline[i]=read(i);
            GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL20.glUseProgram(patched);GL20.glUniform1i(GL20.glGetUniformLocation(patched,"useTexture"),0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            for(int i=0;i<7;i++)if(!java.util.Arrays.equals(baseline[i],read(i)))
                throw new AssertionError("Original native MRT output "+i+" altered by addon outside world scope");
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println("Native original output GPU PASS: actual default.frag, all seven original MRT color outputs unchanged outside addon scope.");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static byte[] read(int output){
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+output);
        ByteBuffer b=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
        byte[] result=new byte[b.remaining()];b.get(result);return result;
    }
    static int shader(int type,String source){
        int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);
        if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(s));return s;
    }
    static int link(String fragment){
        int p=GL20.glCreateProgram();GL20.glAttachShader(p,shader(GL20.GL_VERTEX_SHADER,VERTEX));
        GL20.glAttachShader(p,shader(GL20.GL_FRAGMENT_SHADER,fragment));GL20.glLinkProgram(p);
        if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(p));return p;
    }
}
