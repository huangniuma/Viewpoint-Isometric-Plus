import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Exercises production sprite-ray burial with no proxy mesh or screen neighbour. */
public final class NativeSnowOwnership092Probe {
    static int program,registry,actor,originalDepth,cases;
    static float[] records=new float[1024*193*4];
    static float[] source={0,0,0,3};
    static final Matrix4f projection=new Matrix4f().set(new float[]{1,0,0,0, 0,1,-1,0, 0,1,1,0, 0,0,.4f,1});
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(1,1,"snow native ownership",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            String helpers=NativeSnowOverlay.GLSL.substring(0,NativeSnowOverlay.GLSL.indexOf("void vpIsoSnowOutput"));
            helpers=helpers.replace("#include \"lib/floor_plan.glsl\"","uniform vec3 uSceneOrigin;int planLevelAt(vec3 p){return int(floor(p.y/2.4494896+1e-4));}");
            String frag="#version 330\n"+helpers+"\nuniform vec4 owner;out vec4 result;void main(){bool buried=vpIsoSnowBuried(owner,vec2(.5),vec3(0,.4,0),vec3(0));result=vec4(buried?1:0);}";
            program=NativeGroundProbe.link(NativeVision.VERTEX,frag);GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(loc("vpIsoSnowProjection"),false,projection.get(new float[16]));
            GL20.glUniformMatrix4fv(loc("vpIsoSnowInverse"),false,new Matrix4f(projection).invert().get(new float[16]));
            GL20.glUniform1f(loc("vpIsoSnowDepthWorld"),424.27f);
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            int output=texture(1,1,new float[4]);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,1,1);
            registry=texture(1024,193,records);actor=texture(1,1,new float[4]);originalDepth=texture(1,1,new float[]{.7f,0,0,0});
            GL20.glUniform1i(loc("vpIsoSnowRegistry"),1);GL20.glUniform1i(loc("vpIsoSnowActors"),2);GL20.glUniform1i(loc("vpIsoSnowActorFloors"),3);GL20.glUniform1i(loc("vpIsoSnowOriginalDepth"),4);
            bind(2,actor);bind(3,actor);bind(4,originalDepth);
            check(true,new float[]{-4,0,16,1},"native low sprite without any proxy");
            source[1]=-.1f;check(true,new float[]{-4,0,16,1},"sprite skirt below owning floor still buried");source[1]=0;
            check(true,new float[]{-1,0,4,.25f},"single partial source after known floor subtraction");
            check(false,new float[]{-3,0,10,1},"two source mixture retains foreground");
            check(false,new float[]{1,0,1,1},"unknown anonymous foreground cannot borrow sprite");
            source[2]=1;check(false,new float[]{-4,0,16,1},"other floor retained");source[2]=0;
            source[1]=.8f;check(false,new float[]{-4,0,16,1},"sprite above snow retained");source[1]=0;
            source[3]=0;check(false,new float[]{-4,0,16,1},"recycled source retained");source[3]=3;
            source[3]=4;check(true,new float[]{-4,0,16,1},"foliage own sprite plane");
            source[3]=5;check(true,new float[]{-4,0,16,1},"furniture own sprite plane");
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println("Native snow ownership092 GPU PASS: cases="+cases+"; own sprite ray, alpha, mixed/anonymous/other-floor/recycled rejection, no proxy or neighbour.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static int loc(String name){return GL20.glGetUniformLocation(program,name);}
    static void check(boolean expected,float[] owner,String message){
        Arrays.fill(records,0);System.arraycopy(source,0,records,(1024+2)*4,4);bind(1,registry);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1024,193,GL11.GL_RGBA,GL11.GL_FLOAT,records);
        GL20.glUniform4f(loc("owner"),owner[0],owner[1],owner[2],owner[3]);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        float[] pixel=new float[4];GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
        if((pixel[0]>.5)!=expected)throw new AssertionError(message+" "+Arrays.toString(pixel));cases++;
    }
    static void bind(int unit,int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);}
    static int texture(int w,int h,float[] data){int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return id;}
}
