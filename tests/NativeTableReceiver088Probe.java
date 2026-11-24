import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;
import java.util.*;

/** Actual production table-plane lookup on GPU: ownership, levels, lips and unchanged exact faces. */
public final class NativeTableReceiver088Probe {
    static final int S=64;
    static float[] points=new float[S*S*4],normals=new float[S*S*4],keys=new float[S*S*4];
    static int program,fbo,output,cases;
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(1,1,"088 table source planes",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            String source=NativeOverlay.FRAGMENT;int begin=source.indexOf("bool vpIsoTableReceiver("),end=source.indexOf("void main()",begin);
            String frag="#version 330\nuniform sampler2D nativeSurfaces,nativeNormals,nativeSolar;uniform mat4 inverseNative;uniform vec3 uSceneOrigin;uniform vec2 key;out vec4 result;vec2 uv=vec2(32.5/64.0);\n"
                +source.substring(begin,end)+"\nvoid main(){vec3 p;bool ok=vpIsoTableReceiver(vec3(0),vec3(.5,1,.5),key,vec3(-2,0,-2),vec3(2,1,2),0,p);result=vec4(p,ok?1:0);}";
            program=NativeGroundProbe.link(NativeVision.VERTEX,frag);GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"nativeSurfaces"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"nativeNormals"),1);GL20.glUniform1i(GL20.glGetUniformLocation(program,"nativeSolar"),2);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"inverseNative"),false,new org.joml.Matrix4f().scale(2.56f,1,1).get(new float[16]));
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"key"),123,456);
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);output=texture(1,new float[4]);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,1,1);
            sample(33,32,.6316f,123,456,0);sample(35,32,1,123,456,0);check(draw(),new float[]{.5f,1,.5f,1},"foreground top instead of closer bench");
            reset();sample(35,32,1,999,456,0);check(draw(),new float[]{0,0,0,0},"foreign object excluded");
            reset();sample(35,32,1,123,456,1);check(draw(),new float[]{0,0,0,0},"other floor excluded");
            reset();sample(34,32,.6316f,123,456,0);check(draw(),new float[]{.3158f,.6316f,.3158f,1},"own lower seat retained");
            reset();sample(32,32,1,123,456,0);int at=(32*S+32)*4;points[at]=.2f;points[at+2]=.3f;
            check(draw(),new float[]{.2f,1,.3f,1},"exact top unchanged");
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"key"),0,0);check(draw(),new float[]{0,0,0,0},"unknown source excluded");
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println("Table receiver088 GPU PASS: cases="+cases+"; actual production lookup, own upper/lower planes, source and floor isolation, exact face preservation.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void sample(int x,int y,float h,float a,float b,int level) {int at=(y*S+x)*4;points[at+1]=h;points[at+3]=1;normals[at+1]=1;normals[at+3]=level;keys[at+2]=a;keys[at+3]=b;}
    static void reset(){Arrays.fill(points,0);Arrays.fill(normals,0);Arrays.fill(keys,0);}
    static int texture(int size,float[] data){int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,size,size,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return id;}
    static float[] draw(){int[] textures=new int[3];float[][] data={points,normals,keys};for(int i=0;i<3;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);textures[i]=texture(S,data[i]);}GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);float[] value=new float[4];GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,value);GL11.glDeleteTextures(textures);return value;}
    static void check(float[] value,float[] expected,String message){for(int i=0;i<4;i++)if(expected[3]!=0 || i==3)if(Math.abs(value[i]-expected[i])>.0001f)throw new AssertionError(message+": "+Arrays.toString(value));cases++;}
}
