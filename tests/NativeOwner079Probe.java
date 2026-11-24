import java.nio.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.NativeOwnerMetadata;

/** Actual decoder at continuous alpha pixels with no pure neighbouring owner. */
public final class NativeOwner079Probe {
    static int program,metadata,registry,cases;
    static float[] records=new float[1024*193*4];
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(3,3,"079 alpha owner",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            String frag="#version 330\nin vec2 uv;out vec4 result;uniform sampler2D nativeMetadata,vpIsoNativeWalls;uniform float nativeWorldZ;\n"+NativeOwnerMetadata.GLSL+
                "void main(){ivec2 owner;float level;bool floorValid;bool ok=vpIsoDecodeOwner(texture(nativeMetadata,uv),owner,level,floorValid);result=vec4(vec2(owner),level,(ok?2:0)+(floorValid?1:0));}";
            program=NativeGroundProbe.link(NativeGroundProbe.VERTEX,frag);
            metadata=NativeShadow068Probe.texture(GL30.GL_RGBA32F,3,3);
            registry=NativeShadow068Probe.texture(GL30.GL_RGBA32F,1024,193);
            int tex=NativeShadow068Probe.texture(GL30.GL_RGBA32F,3,3),fbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,tex,0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,3,3);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
            for(int z:new int[]{-2,0,3})for(int id:new int[]{2,255,256,65534,65535}) {
                int x=-2-(id&255),y=id>>>8;record(id,z,3);
                for(float a:new float[]{1f/255,.02f,.25f,.5f,.9f,254f/255})for(float coverage:new float[]{.8f,1f}) {
                    float[] raw=mix(x,y,z,a,coverage);float[] value=draw(raw,z+.2f);
                    require(value[3]==3 && value[0]==x && value[1]==y && value[2]==z,"Continuous source alpha decode failed id="+id+" alpha="+a+" z="+z);
                    float[] wrong=draw(raw,z+1.2f);require(wrong[3]==0,"Wrong floor borrowed source");cases+=2;
                }
            }
            for(int z:new int[]{-2,0,3})for(int id:new int[]{2,1023})for(float a:new float[]{.02f,.25f,.5f,.9f}) {
                records[id*4+2]=z;records[id*4+3]=1;float[] value=draw(mix(-1,id,z,a,1),z+.2f);
                require(value[3]==3 && value[0]==-1 && value[1]==id && value[2]==z,"Continuous wall alpha decode failed");cases++;
            }
            // Malformed moments, unknown identities, blades, different source/floor
            // levels and two foreground IDs must not fabricate a floor contribution.
            record(2,0,3);record(3,0,3);
            for(float[] raw:new float[][]{{-4,0,17,1},{-1,0,2,1},{-50,12,4000,1},mix(-4,0,1,.5f,1),{-2.5f,0,6.5f,1}}) {
                float[] value=draw(raw,.2f);require(value[3]==0,"Invalid or mixed owners borrowed floor: "+java.util.Arrays.toString(raw));cases++;
            }
            require(GL11.glGetError()==0,"GL error");
            System.out.println("Native owner079 GPU PASS cases="+cases+"; continuous alpha without pure neighbours, 65K source address range, negative/upper floors, high wall IDs, wrong-floor and invalid-owner controls.");
        }finally{GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static float[] mix(int x,int y,int z,float a,float coverage){return new float[]{coverage*a*x,coverage*(a*y+(1-a)*z),coverage*(a*(x*x+y*y)+(1-a)*z*z),coverage};}
    static void record(int id,int z,int kind){int i=(1024+id)*4;records[i]=3;records[i+1]=3;records[i+2]=z;records[i+3]=kind;}
    static float[] draw(float[] raw,float level){
        float[] pixels=new float[36];for(int i=0;i<9;i++)System.arraycopy(raw,0,pixels,i*4,4);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,metadata);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,3,3,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,registry);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1024,193,GL11.GL_RGBA,GL11.GL_FLOAT,records);
        GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"nativeMetadata"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"vpIsoNativeWalls"),1);GL20.glUniform1f(GL20.glGetUniformLocation(program,"nativeWorldZ"),level);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);float[] output=new float[4];GL11.glReadPixels(1,1,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,output);return output;
    }
}
