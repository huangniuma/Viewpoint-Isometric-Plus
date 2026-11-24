import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.*;
import vpiso.*;

/** Both consumers must exactly match the full table after new, cleared and reused IDs. */
public final class NativeOwnerUpload104Probe {
 static int checks,changedRows;
 public static void main(String[] args)throws Exception {
  NativeShadow068Probe P=NativePlants072Probe.P;NativeSurfaceReceiverProbe N=P.N;
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long win=GLFW.glfwCreateWindow(N.W,N.H,"104 owner uploads",0,0);
  int registry=0;
  try {
   GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);TestShaderPacks.selectLive("default","high",Map.of("upscaling","off"));
   N.fixture();N.fit(0);P.pack="default";P.nativeProgram=NativeGroundProbe.link(N.VERT,P.NATIVE);
   P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
   var f=P.frame();Object mesh=N.mesh(f.scene,0,N.horizontal(0,2));P.publish(mesh,true,0);P.draw(f,N.horizontal(0,2),0,new float[]{0,0,0,1},1,true);
   NativeOverlay second=new NativeOverlay();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glViewport(0,0,N.W,N.H);second.capture(false);draw(second,N.receiver);
   check(N.overlay);check(second);registry=NativeWallSources071Probe.fbo();NativeGround.begin(true);
   // Cross many1024-ID row boundaries and the high source half of the texture.
   for(int id=0;id<4300;id++) {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
    NativeGround.sourceBegin(new NativeSource(id+.5f,12.5f,0,3,new float[]{-.4f,0,-.4f,.4f,1.5f,.4f},0));NativeGround.sourceEnd();
    if(id%511==0){refresh(N.overlay,N.receiver);check(N.overlay);}
   }
   refresh(second,N.receiver);check(second);
   // Wall-row data must update independently of the three source planes.
   if(((Number)GameAccess.field("vpiso.NativeGround","nextSource",null)).intValue()<4302)throw new AssertionError("Source boundary fixture incomplete");
   GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
   var wall=NativeGround.class.getDeclaredMethod("retainWall",int.class,int.class,int.class,int.class);wall.setAccessible(true);wall.invoke(null,500,12,0,1);
   refresh(N.overlay,N.receiver);check(N.overlay);refresh(second,N.receiver);check(second);
   NativeGround.finish();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);NativeGround.begin(true);NativeGround.finish();
   refresh(N.overlay,N.receiver);check(N.overlay);refresh(second,N.receiver);check(second);
   GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);NativeGround.begin(true);
   for(int i=0;i<7;i++){NativeGround.sourceBegin(new NativeSource(30000+i,30.5f,-1,4,new float[]{-.5f,0,-.5f,.5f,3,.5f},0));NativeGround.sourceEnd();}
   refresh(N.overlay,N.receiver);check(N.overlay);refresh(second,N.receiver);check(second);
   if(changedRows>=checks*193/2)throw new AssertionError("Sparse upload did not reduce table rows");
   if(GL11.glGetError()!=0)throw new AssertionError("GL error");
   System.out.println("Owner upload104 GPU PASS: checks="+checks+" changedRows="+changedRows+"; exact full-table equality, two independent consumers,1024-ID boundaries, wall changes, post-pass deletion, recycled IDs and all source planes.");N.retire(mesh);
  }finally{NativeGround.finish();if(registry!=0)NativeGround.release(registry);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
 }
 static void refresh(NativeOverlay o,NativeReceiver r){
  long[] prior=((long[])GameAccess.field("vpiso.NativeOverlay","ownerRevisions",o)).clone();
  for(int row=0;row<193;row++)if(prior[row]!=NativeGround.ownerRevision(row))changedRows++;
  draw(o,r);
 }
 static void draw(NativeOverlay o,NativeReceiver r){o.draw(r,NativeShadow068Probe.OFF,0,new Matrix4f[]{new Matrix4f(),new Matrix4f(),new Matrix4f()},new float[3],1,0,0,0);}
 static void check(NativeOverlay o){
  int texture=((Number)GameAccess.field("vpiso.NativeOverlay","wallOwners",o)).intValue();GL13.glActiveTexture(GL13.GL_TEXTURE12);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
  float[] actual=new float[1024*193*4];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,actual);
  FloatBuffer full=org.lwjgl.BufferUtils.createFloatBuffer(actual.length);NativeGround.ownerData(full);
  for(int i=0;i<actual.length;i++)if(Float.floatToIntBits(actual[i])!=Float.floatToIntBits(full.get(i)))throw new AssertionError("Owner table mismatch at "+i+" check="+checks);
  checks++;
 }
}
