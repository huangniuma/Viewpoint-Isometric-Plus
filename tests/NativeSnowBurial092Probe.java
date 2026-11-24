import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.*;
import vpiso.*;

/** Uses an actual skinned mesh and a separately drawn native image/depth of that same pose. */
public final class NativeSnowBurial092Probe {
 static int program,vao,vbo;static float[][] points;static float bias;
 static final String VERT="""
  #version 330
  layout(location=0)in vec3 p;uniform mat4 projection;
  void main(){gl_Position=projection*vec4(p,1);}
  """;
 static final String FRAG="""
  #version 330
  layout(location=0)out vec4 c;layout(location=3)out vec4 f;
  layout(location=6)out vec4 o;layout(location=7)out vec4 g;
  uniform float bias;
  void main(){c=vec4(.9,.1,.05,1);o=vec4(1,0,1,1);f=g=vec4(0,0,0,1);gl_FragDepth=gl_FragCoord.z+bias;}
  """;
 public static void main(String[] args)throws Exception{
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
  GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long win=GLFW.glfwCreateWindow(192,108,"physical snow burial",0,0);
  try{
   GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
   TestShaderPacks.select(args[0],args[0].equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off","snow","medium"));Gl.init();
   NativeSnow092Probe.frame=NativeSurfaceReceiverProbe.frame();var scene=NativeSnow092Probe.frame.scene;
   scene.skyR=scene.skyG=scene.skyB=1;scene.time=1;scene.snowLying.depth=.4f;scene.snowLying.cover=1;scene.snowLying.hours=100;Arrays.fill(scene.snowLying.grid,(byte)255);
   NativeSnow092Probe.receiver.fit(new double[][]{{.5,.32,.5},{.62,.26,.4985566458},{.38,.26,.4985566458},{.5,.68,.4971132916}});
   NativeSurfaceReceiverProbe.opaque=64;Object floor=NativeSurfaceReceiverProbe.mesh(scene,0,NativeSurfaceReceiverProbe.horizontal(0,24));scene.meshFlags[0]=(byte)0x80;
   NativeSnow092Probe.aw=192;NativeSnow092Probe.ah=108;NativeSnow092Probe.make();
   program=NativeGroundProbe.link(VERT,FRAG);vao=GL30.glGenVertexArrays();vbo=GL15.glGenBuffers();
   NativeSnow092Probe.nativeProducer=NativeSnowBurial092Probe::nativeModel;
   for(float height:new float[]{.035f,.8f}){
    GameAccess.call("viewpoint.render.ModelDraws","reset",scene.models,new Class<?>[0]);points=NativeSurfaceReceiverProbe.horizontal(height,.7f);NativeSurfaceReceiverProbe.addModel(scene,points,true,false);bias=-.0001f;
    byte[] result=null;for(int i=0;i<8;i++)result=NativeSnow092Probe.draw(0);
    int left=red(result);System.out.println("Burial092 model height="+height+" retained red pixels="+left);
    if(height<.4)require(left<5,"Low actual model remains above snow");else require(left>100,"Above-snow actual model was covered");
    if(height<.4){bias=-.001f;for(int i=0;i<2;i++)result=NativeSnow092Probe.draw(0);require(red(result)>100,"Unrelated closer native foreground borrowed model burial");}
   }
   GameAccess.call("viewpoint.render.ModelDraws","reset",scene.models,new Class<?>[0]);points=NativeSurfaceReceiverProbe.horizontal(.035f,.7f);Object tile=NativeSurfaceReceiverProbe.mesh(scene,1,points);scene.meshFlags[1]=2;bias=0;
   byte[] unknown=NativeSnow092Probe.draw(0);require(red(unknown)>100,"Ordinary world mesh impersonated an actor");
   require(GL11.glGetError()==0,"GL error");NativeSurfaceReceiverProbe.retire(tile);NativeSurfaceReceiverProbe.retire(floor);
   System.out.println("Native snow burial092 GPU PASS: "+args[0]+"; low posed model buried, high model retained, mismatched native depth and ordinary mesh rejected.");
  }finally{GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
 }
 static void nativeModel(){
  GL20.glUseProgram(program);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"projection"),false,NativeSurfaces.projection(NativeSnow092Probe.receiver).get(new float[16]));GL20.glUniform1f(GL20.glGetUniformLocation(program,"bias"),bias);
  FloatBuffer b=BufferUtils.createFloatBuffer(points.length*3);for(float[] p:points)b.put(p);b.flip();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,b,GL15.GL_STREAM_DRAW);
  GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0L);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_ALWAYS);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);
 }
 static int red(byte[] b){int n=0;for(int i=0;i<b.length;i+=4)if((b[i]&255)>180 && (b[i+1]&255)<60)n++;return n;}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
