import java.nio.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.IsoCameraMath;
import vpiso.NativeViewData;

/** Rasterized floors through the real remote camera and production blackout GLSL. */
public final class NativeVisibility053Probe {
    static final int W=512,H=288;
    static int vao,fbo,depth,color,tiles,geometry,post;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit()) throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long win=GLFW.glfwCreateWindow(W,H,"native visibility precision",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win); GL.createCapabilities();
            vao=GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            fbo=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            color=texture(GL30.GL_RGBA32F,GL11.GL_RGBA);
            depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            geometry=program("""
                #version 330
                uniform mat4 camera;
                uniform float level;
                void main() {
                  vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2)*80.0-40.0;
                  gl_Position=camera*vec4(p.x,level,p.y,1.0);
                }
                ""","#version 330\nout vec4 c; void main(){ c=vec4(1); }");
            post=program("""
                #version 330
                out vec2 uv;
                void main(){ uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2); gl_Position=vec4(uv*2-1,0,1); }
                ""","#version 330\nin vec2 uv; uniform sampler2D sourceDepth; uniform mat4 inverseCamera;\n"+NativeViewData.class.getField("GLSL").get(null)+"""
                out vec4 c;
                void main(){
                  float d=texture(sourceDepth,uv).r;
                  vec4 p=inverseCamera*vec4(uv*2-1,d*2-1,1);
                  vec3 scene=p.xyz/p.w;
                  c=vec4(vpIsoNativeDark(scene),scene.y,0,d<1?1:0);
                }
                """);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            tiles=GL11.glGenTextures(); GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,tiles);
            ByteBuffer data=BufferUtils.createByteBuffer(128*128*7*4);
            // Alternate black and visible floors, including basements. Misclassification
            // in either direction must change the result; clamping every sample cannot pass.
            for(int z=-3;z<=3;z++)for(int i=0;i<128*128;i++)data.put((byte)((z&1)==0?0:255)).put((byte)0).put((byte)0).put((byte)128);
            data.flip();
            GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA8,128,128,7,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            int bad=0,samples=0; float min=100,max=-100;
            int cases=0;
            for(float yaw:new float[]{0,37,90,180,270})for(float zoom:new float[]{.5f,2f})
            for(float camZ:new float[]{0,.375f,-.25f})for(int floor=-2;floor<=2;floor++) {
                float level=(floor-camZ)*2.4494896f;
                float camX=-12004.25f,camY=257.75f;
                var fit=IsoCameraMath.fit(camX,camY,W*.5+cases%3*.37,H*.5-cases%5*.19,32/zoom,16/zoom,-32/zoom,16/zoom,W,H);
                var projection=new Matrix4f(); var view=new Matrix4f();
                IsoCameraMath.matrices(fit,camX,camY,W,H,projection,view,new float[3],yaw);
                var pv=projection.mul(view); var inv=new Matrix4f(pv).invert();
                GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D,0);
                GL11.glViewport(0,0,W,H); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_ALWAYS); GL11.glDepthMask(true);
                GL11.glClearDepth(1); GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_COLOR_BUFFER_BIT);
                GL20.glUseProgram(geometry); matrix(geometry,"camera",pv); GL20.glUniform1f(GL20.glGetUniformLocation(geometry,"level"),level);
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,0,0);
                GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth); GL20.glUseProgram(post);
                GL20.glUniform1i(GL20.glGetUniformLocation(post,"sourceDepth"),0);
                GL20.glUniform1i(GL20.glGetUniformLocation(post,"vpIsoNativeTiles"),1);
                GL20.glUniform1f(GL20.glGetUniformLocation(post,"vpIsoNativeOn"),1);
                GL20.glUniform3f(GL20.glGetUniformLocation(post,"vpIsoNativeOrigin"),(float)Math.floor(camX)-64,(float)Math.floor(camY)-64,-3);
                GL20.glUniform3f(GL20.glGetUniformLocation(post,"vpIsoNativeSize"),128,128,7);
                GL20.glUniform3f(GL20.glGetUniformLocation(post,"vpIsoNativeCam"),camX,camY,camZ);
                matrix(post,"inverseCamera",inv); GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(W*H*4);
                GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                int errors=0,count=0;
                for(int i=0;i<W*H;i++) if(pixels.get(i*4+3)>0) {
                    float y=pixels.get(i*4+1); min=Math.min(min,y-level); max=Math.max(max,y-level);
                    if(Math.abs(pixels.get(i*4)-((floor&1)==0?0:1))>.01)errors++;
                    count++;
                }
                if(errors>0)System.out.println("Rasterized blackout yaw="+yaw+" zoom="+zoom+" floor="+floor+" camZ="+camZ+" incorrect="+errors+"/"+count);
                cases++;
                bad+=errors; samples+=count;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,0);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            }
            System.out.println("Native blackout reconstruction cases="+cases+" error="+min+".."+max+" bad="+bad+"/"+samples);
            if(args.length>0&&args[0].equals("baseline052")) {
                if(bad<100)throw new AssertionError("Pinned 052 failure did not reproduce");
                System.out.println("Pinned 052 native visibility negative control PASS");
            } else if(bad!=0)throw new AssertionError("Native blackout floor classification unstable: "+bad);
            else System.out.println("Native visibility 053 GPU PASS");
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
        } finally { GLFW.glfwDestroyWindow(win); GLFW.glfwTerminate(); }
    }
    static int texture(int internal,int format){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,W,H,0,format,GL11.GL_FLOAT,(ByteBuffer)null);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    static void matrix(int p,String name,Matrix4f m){GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(p,name),false,BufferUtils.createFloatBuffer(16).put(m.get(new float[16])).flip());}
    static int shader(int kind,String src){int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,src);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(s));return s;}
    static int program(String vs,String fs){int p=GL20.glCreateProgram();GL20.glAttachShader(p,shader(GL20.GL_VERTEX_SHADER,vs));GL20.glAttachShader(p,shader(GL20.GL_FRAGMENT_SHADER,fs));GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(p));return p;}
}
