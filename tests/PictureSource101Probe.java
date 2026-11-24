import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.render.KeptPicture;
import vpiso.*;

/** Actual VP snapshot and actual native save backup, with an inherited floor-only read source. */
public final class PictureSource101Probe {
 static int cases,wrong,source,target,other;static boolean baseline;
 public static void main(String[] args)throws Exception {
  baseline=args.length>0;require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long window=GLFW.glfwCreateWindow(64,64,"picture source101",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
   source=fbo(true);target=fbo(false);other=fbo(false);GL11.glViewport(0,0,64,64);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);
   Object funcs=Class.forName("zombie.core.textures.GLFramebufferObject30").getConstructor().newInstance();GameAccess.set("zombie.core.textures.TextureFBO","funcs",null,funcs);
   Class<?> type=Class.forName("zombie.savefile.SavefileThumbnail$CopyTextureDrawer");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object copy=constructor.newInstance();
   Method blit=type.getDeclaredMethod("blitFramebuffer",zombie.core.textures.TextureFBO.class,zombie.core.textures.TextureFBO.class);blit.setAccessible(true);
   Object from=textureFbo(source),to=textureFbo(target);
   for(int size:new int[]{16,32,64,24,64})for(int slot:new int[]{0,7})for(boolean separate:new boolean[]{false,true}) {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,source);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+slot);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,size,size);
    if(separate)GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,other);
    KeptPicture.keep();int kept=(Integer)GameAccess.field("viewpoint.render.KeptPicture","fbo",null);check(kept,"VP keep",slot);
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);require(GL11.glGetInteger(GL11.GL_READ_BUFFER)==GL30.GL_COLOR_ATTACHMENT0+slot,"VP source selection not restored");
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,other);
    blit.invoke(copy,from,to);check(target,"native save copy",slot);
    require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==0,"native final draw binding changed");
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);require(GL11.glGetInteger(GL11.GL_READ_BUFFER)==GL30.GL_COLOR_ATTACHMENT0+slot,"save source selection not restored");
    require(GL11.glGetError()==0,"GL error");
   }
   if(baseline)require(wrong==20,"100 negative source count "+wrong);else require(wrong==0,"wrong source "+wrong);
   System.out.println((baseline?"Previous100 picture negative PASS:":"Picture101 GPU PASS:")+" copies="+cases+" wrong="+wrong+"; actual VP keep and native save backup, inherited attachments0/7, separate read/draw, cold and resized snapshot, source state restored.");
   if(!baseline) {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,source);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+7);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,other);
    long saved=NativePictureSource.begin(source);
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target);NativePictureSource.end(saved);
    require(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)==target,"copy error-path read binding changed");
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);require(GL11.glGetInteger(GL11.GL_READ_BUFFER)==GL30.GL_COLOR_ATTACHMENT0+7,"error source not restored");
    Main.compatible=false;require(NativePictureSource.begin(source)==-1,"unsupported intervention");NativePictureSource.end(-1);Main.compatible=true;
    System.out.println("Picture101 cleanup PASS: copy error boundary, unsupported version and sentinel preserve caller state.");
   }
   KeptPicture.release();
  }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
 static void check(int fbo,String label,int inherited) {
  int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);int buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
  ByteBuffer b=BufferUtils.createByteBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);boolean good=(b.get(0)&255)==128&&(b.get(1)&255)==159&&(b.get(2)&255)==191;
  if(!good)wrong++;if(!baseline)require(good,label+" read auxiliary attachment "+inherited+" rgb="+(b.get(0)&255));cases++;
  GL11.glReadBuffer(buffer);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
 }
 static int fbo(boolean auxiliary) {
  int f=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);
  for(int slot:auxiliary?new int[]{0,7}:new int[]{0}) {
   int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,64,64,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL11.GL_TEXTURE_2D,t,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0+slot);GL11.glClearColor(slot==0?.5f:0,slot==0?.625f:0,slot==0?.75f:0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
  }
  int depth=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH24_STENCIL8,64,64,0,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"fbo");return f;
 }
 static Object textureFbo(int fbo)throws Exception {
  var f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object u=f.get(null);Object o=u.getClass().getMethod("allocateInstance",Class.class).invoke(u,zombie.core.textures.TextureFBO.class);
  for(var e:Map.of("id",fbo,"width",64,"height",64).entrySet())GameAccess.set("zombie.core.textures.TextureFBO",e.getKey(),o,e.getValue());return o;
 }
 static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
}
