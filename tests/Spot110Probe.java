import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.*;
import vpiso.*;

/** Original/fixed spotLight for every legal map and -1, all beam kinds and shadow blending. */
public final class Spot110Probe {
 static final int W=64,H=64;
 static final String BODY="""
  #version 330
  in vec2 uv;out vec4 color;uniform float amount;
  void main(){vec3 p=vec3((uv.x-.5)*3.,0.,(uv.y-.5)*3.);
   color=vec4(vpIsoSpotLight(p,vec3(0,1,0),amount),1);}
  """;
 static int fbo,vao,ubo,mapTexture;
 public static void main(String[] args)throws Exception {
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
  long win=GLFW.glfwCreateWindow(W,H,"110 beam regression",0,0);
  try(var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()},Spot110Probe.class.getClassLoader()){
   @Override protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{
    if(!n.startsWith("vpiso.NativeLighting"))return super.loadClass(n,resolve);
    Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;
   }
  }) {
   GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
   fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
   int output=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,output);
   GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,W,H,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
   GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
   vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);ubo=GL15.glGenBuffers();
   GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,ubo);GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,33*24*4L,GL15.GL_DYNAMIC_DRAW);
   GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,2,ubo);
   mapTexture=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0+21);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,mapTexture);
   FloatBuffer depth=BufferUtils.createFloatBuffer(W*H*5);for(int i=0;i<W*H*5;i++)depth.put(i%W<W/2?.25f:1f);depth.flip();
   GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL30.GL_DEPTH_COMPONENT32F,W,H,5,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
   GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
   GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL14.GL_TEXTURE_COMPARE_MODE,GL30.GL_COMPARE_REF_TO_TEXTURE);GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL14.GL_TEXTURE_COMPARE_FUNC,GL11.GL_LEQUAL);
   GL11.glViewport(0,0,W,H);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
   for(String pack:new String[]{"default","library","vivid","somber"}) {
    TestShaderPacks.selectLive(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
    GameAccess.set("viewpoint.platform.GlProgram","pack",null,ShaderPacks.active());
    ShaderPacks.linked(ShaderPacks.active());
    String old=(String)loader.loadClass("vpiso.NativeLighting").getMethod("fragment",String.class).invoke(null,BODY);
    GlProgram a=link(old),b=link(NativeLighting.fragment(BODY));int cases=0,positive=0,shadowDifferences=0;
    for(int kind=0;kind<3;kind++)for(int map=-1;map<5;map++)for(float amount:new float[]{0f,.4f,1f}) {
     float[] rec={0,3,2,16, 0,-.6f,-.8f,kind, .8f,.7f,.6f,map, 0,.8f,-.6f,.4f, 0,-.6f,-.8f,.3f, .1f,0,0,0};
     GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,ubo);GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER,0,rec);
     float[] before=draw(a,amount),after=draw(b,amount);
     if(!Arrays.equals(before,after))throw new AssertionError("Spot output differs "+pack+"/"+kind+"/"+map+"/"+amount);
     for(int i=0;i<after.length;i+=4)if(after[i]+after[i+1]+after[i+2]>.0001f)positive++;
     if(map>=0&&kind==0&&amount==1f){float[] clear=draw(b,0f);for(int i=0;i<after.length;i+=4)if(clear[i]-after[i]>.0001f)shadowDifferences++;}
     cases++;
    }
    if(positive==0||shadowDifferences==0)throw new AssertionError("Empty spot fixture "+positive+"/"+shadowDifferences);
    System.out.println("Spot110 exact GPU PASS pack="+pack+" cases="+cases+" litPixels="+positive+" blockedPixels="+shadowDifferences+"; map-1 and0..4,torch/headlight/rear beam, strength0/.4/1; no NVIDIA runtime claim.");
   }
   if(GL11.glGetError()!=0)throw new AssertionError("GL error");
  }finally{GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
 }
 static GlProgram link(String source){return GlProgram.fromSources("110 spot parity",new GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,source),Map.of(),Map.of(),List.of("110spots"));}
 static float[] draw(GlProgram program,float amount){
  program.use();program.setInt("vpIsoSpotsOn",1);program.setInt("uSpotCount",1);program.set("amount",amount);program.setInt("uSpotShadow",21);
  for(int i=0;i<5;i++) {
   program.set("uSpotMaps["+i+"]",new org.joml.Matrix4f().translation(.5f,.5f,.5f).scale(.15f,.1f,.1f));
   program.set("uSpotTexel["+i+"]",.001f);program.set("uSpotLayer["+i+"]",(float)i);program.set("uSpotTiles["+i+"]",0f,0f,1f,1f);
  }
  int id=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),block=GL31.glGetUniformBlockIndex(id,"SpotRecords");GL31.glUniformBlockBinding(id,block,2);
  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
  FloatBuffer values=BufferUtils.createFloatBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_FLOAT,values);float[] pixels=new float[values.remaining()];values.get(pixels);return pixels;
 }
}
