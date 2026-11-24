import org.lwjgl.glfw.GLFW;import org.lwjgl.opengl.*;import vpiso.*;
/** Floor moments may survive overlapping objects but may not invent a mixed floor. */
public final class NativeFloorValidity081Probe {
 static int p,t,cases;
 public static void main(String[] args)throws Exception {
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
  long win=GLFW.glfwCreateWindow(3,3,"081 floor validity",0,0);
  try {GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
   p=NativeGroundProbe.link(NativeGroundProbe.VERTEX,"#version 330\nin vec2 texCoord;out vec4 result;uniform sampler2D floorTexture,nativeMetadata,vpIsoNativeWalls;uniform float nativeWorldZ;vec2 uv;\n"+NativeOwnerMetadata.GLSL+"void main(){float z;bool ok=vpIsoDecodeFloor(texture(floorTexture,texCoord),z);result=vec4(z,0,0,ok?1:0);}");
   int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);int out=NativeFloorSampling081Probe.texture(3,GL30.GL_RGBA32F,null);
   GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,out,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,3,3);t=NativeFloorSampling081Probe.texture(3,GL30.GL_RGBA32F,null);
   for(int z:new int[]{-32,-2,0,3,31})for(float a:new float[]{.00001f,.003f,.1f,.999f,1})check(new float[]{z*a,z*z*a,a,1},true,z);
   for(float[] v:new float[][]{{0,0,0,1},{0,0,Float.NaN,1},{.5f,1,1,1},{1,2,1,1},{-.5f,.5f,1,1},{0,-1,1,1}})check(v,false,0);
   if(GL11.glGetError()!=0)throw new AssertionError("GL error");System.out.println("Native floor validity081 GPU PASS cases="+cases+"; independent tiny coverage, floors -32..31, mixed-floor variance, no-floor and invalid-data rejection.");
  } finally {GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
 }
 static void check(float[] raw,boolean valid,int level){
  float[] data=new float[36];for(int i=0;i<9;i++)System.arraycopy(raw,0,data,i*4,4);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,3,3,GL11.GL_RGBA,GL11.GL_FLOAT,data);
  GL20.glUseProgram(p);GL20.glUniform1i(GL20.glGetUniformLocation(p,"floorTexture"),0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);float[] out=new float[4];GL11.glReadPixels(1,1,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,out);
  if((out[3]>.5f)!=valid || valid && Math.abs(out[0]-level)>.001)throw new AssertionError("Floor validation "+java.util.Arrays.toString(raw)+" result="+java.util.Arrays.toString(out));cases++;
 }
}
