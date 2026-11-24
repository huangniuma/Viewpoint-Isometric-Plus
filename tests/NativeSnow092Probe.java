import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Non-square/subviewport native FBOs reproduce the real 2048 backing / 1080 viewport cutoff. */
public final class NativeSnow092Probe {
 static final int W=192,H=108;
 static Frame frame;
 static NativeReceiver receiver=new NativeReceiver();
 static NativeSnowOverlay overlay=new NativeSnowOverlay();
 static int fbo,color,floor,owner,levels,depth,aw,ah,vx,vy;
 static boolean baseline;
 static String pack;
 static float floorLevel;
 static Runnable nativeProducer;
 public static void main(String[] args)throws Exception {
  pack=args[0];baseline=Arrays.asList(args).contains("baseline091");
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");
  GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
  GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long win=GLFW.glfwCreateWindow(W,H,"snow backing alignment",0,0);
  try {
   GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
   TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off","snow","medium"));Gl.init();
   frame=NativeSurfaceReceiverProbe.frame();frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
   frame.scene.time=1;frame.scene.snowLying.depth=.40824828f;frame.scene.snowLying.cover=1;frame.scene.snowLying.hours=100;
   Arrays.fill(frame.scene.snowLying.grid,(byte)255);
   NativeSurfaceReceiverProbe.opaque=64;
   Object mesh=NativeSurfaceReceiverProbe.mesh(frame.scene,0,NativeSurfaceReceiverProbe.horizontal(0,64));frame.scene.meshFlags[0]=(byte)0x80;
   int cases=0,oldFailures=0;
   for(int[] shape:new int[][]{{192,108,0,0},{256,256,0,0},{256,256,17,29},{384,216,89,51}}) {
    aw=shape[0];ah=shape[1];vx=shape[2];vy=shape[3];make();
    for(double zoom:new double[]{1,2.5}) {
     receiver.fit(new double[][]{{.50009114,.47921297,.24951282},{.50009114+.013333333/zoom,.47921297-.011851852/zoom,.248069464},
       {.50009114-.013333333/zoom,.47921297-.011851852/zoom,.248069464},{.50009114,.47921297+.071111111/zoom,.246626108}});
     byte[] out=null;for(int i=0;i<4;i++)out=draw(0);
     int bottom=count(out,0,H/3),top=count(out,H*2/3,H);
     System.out.println("Snow092 shape="+Arrays.toString(shape)+" zoom="+zoom+" top="+top+" bottom="+bottom);
     if(baseline) {if(top<bottom*.2)oldFailures++;continue;}
     require(top>W*H/7 && bottom>W*H/7,"Native snow backing allocation clipped a viewport band");cases++;
     byte[] opaque=draw(1);require(count(opaque,0,H)==0,"Native foreground overwritten");cases++;
     byte[] edge=draw(.5f);int edges=0;
     for(int i=0;i<W*H;i++)if((out[4*i]&255)>100){int expected=Math.round(255*.1f+(out[4*i]&255)*.5f);require(Math.abs((edge[4*i]&255)-expected)<=2,"Alpha edge lost");edges++;}
     require(edges>W*H/3,"No alpha samples");cases++;
     floorLevel=1;require(count(draw(0),0,H)==0,"Wrong storey accumulated snow");floorLevel=0;cases++;
    }
    destroy();
   }
   if(baseline){require(oldFailures>=4,"091 backing mismatch did not reproduce");System.out.println("Previous091 native snow cutoff negative PASS: failedBands="+oldFailures);}
   else System.out.println("Native snow092 GPU PASS: "+pack+" cases="+cases+"; viewport/allocation/subviewport/zoom/foreground/storeys");
   require(GL11.glGetError()==0,"GL error");NativeSurfaceReceiverProbe.retire(mesh);
  }finally{GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
 }
 static byte[] draw(float blocked) {
  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(vx,vy,W,H);
  GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,GL30.GL_COLOR_ATTACHMENT3,0,0,GL30.GL_COLOR_ATTACHMENT6,GL30.GL_COLOR_ATTACHMENT7});
  GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
  for(int slot:new int[]{0,3,6,7})GL30.glClearBufferfv(GL11.GL_COLOR,slot,new float[4]);
  GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
  GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(vx,vy,W,H);
  GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{.2f,.3f,.12f,1});
  GL30.glClearBufferfv(GL11.GL_COLOR,3,new float[]{floorLevel*(1-blocked),floorLevel*floorLevel*(1-blocked),1-blocked,1});
  GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{blocked,0,0,1});
  GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{.2f*(1-blocked),.3f*(1-blocked),.12f*(1-blocked),1});
  GL11.glDisable(GL11.GL_SCISSOR_TEST);
  if(nativeProducer!=null)nativeProducer.run();
  frame.number++;frame.simNanos=frame.number*50_000_000L;
  GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
  try{overlay.draw(frame,receiver,floor,owner,levels);}finally{GL11.glPopClientAttrib();GL11.glPopAttrib();}
  ByteBuffer b=BufferUtils.createByteBuffer(W*H*4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(vx,vy,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
  byte[] result=new byte[b.remaining()];b.get(result);return result;
 }
 static int count(byte[] data,int lo,int hi){int n=0;for(int i=lo*W;i<hi*W;i++)if((data[4*i]&255)>100)n++;return n;}
 static void make(){fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);color=texture(GL11.GL_RGBA8,GL30.GL_COLOR_ATTACHMENT0);floor=texture(GL11.GL_RGBA8,GL30.GL_COLOR_ATTACHMENT7);owner=texture(GL30.GL_RGBA32F,GL30.GL_COLOR_ATTACHMENT6);levels=texture(GL30.GL_RGBA32F,GL30.GL_COLOR_ATTACHMENT3);depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL30.GL_DEPTH_ATTACHMENT);require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");}
 static int texture(int format,int attachment){int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,aw,ah,0,attachment==GL30.GL_DEPTH_ATTACHMENT?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,attachment,GL11.GL_TEXTURE_2D,id,0);return id;}
 static void destroy(){for(int id:new int[]{color,floor,owner,levels,depth})GL11.glDeleteTextures(id);GL30.glDeleteFramebuffers(fbo);}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
