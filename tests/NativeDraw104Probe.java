import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Original and candidate per-draw state are exercised against the same real GL driver. */
public final class NativeDraw104Probe {
 static Class<?> old;static Method oldBefore;static int fbo,program;
 static final String FRAGMENT="""
  #version 330
  uniform float vpIsoGroundTag,vpIsoGrassOccluder,vpIsoGrassMaskOn,vpIsoFloorLevel,vpIsoMetadataStraight;
  uniform float vpIsoRelightWrite,vpIsoWallOwner,vpIsoSourceOwner,vpIsoFloorWrite;
  layout(location=0) out vec4 c;
  void main(){c=vec4(vpIsoGroundTag+vpIsoGrassOccluder+vpIsoGrassMaskOn+vpIsoFloorLevel,
   vpIsoMetadataStraight+vpIsoRelightWrite,vpIsoWallOwner+vpIsoSourceOwner,vpIsoFloorWrite);}
  """;
 static Object field(Class<?> c,Object owner,String n)throws Exception {var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(owner);}
 static void set(Class<?> c,Object owner,String n,Object v)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(owner,v);}
 public static void main(String[] args)throws Exception {
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long w=GLFW.glfwCreateWindow(32,32,"104 draw state",0,0);
  try(var loader=new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},NativeDraw104Probe.class.getClassLoader()){
   @Override protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{if(!n.equals("vpiso.NativeGround")&&!n.startsWith("vpiso.NativeGround$"))return super.loadClass(n,resolve);Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;}
  }) {
   GLFW.glfwMakeContextCurrent(w);GL.createCapabilities();fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
   for(int i=0;i<8;i++){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,32,32,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+i,GL11.GL_TEXTURE_2D,t,0);}
   program=NativeGroundProbe.link(NativeVision.VERTEX,FRAGMENT);GL20.glUseProgram(program);
   old=loader.loadClass("vpiso.NativeGround");oldBefore=old.getMethod("beforeDraw");prepare(old);prepare(NativeGround.class);
   int cases=0;
   for(boolean grass:new boolean[]{false,true})for(boolean floor:new boolean[]{false,true})for(boolean relight:new boolean[]{false,true})for(int mode=0;mode<32;mode++){
    config(old,grass,floor,relight,mode);nativeState(mode);oldBefore.invoke(null);int[] a=state();
    config(NativeGround.class,grass,floor,relight,mode);nativeState(mode);NativeGround.beforeDraw();int[] b=state();
    if(!Arrays.equals(a,b))throw new AssertionError("Draw state changed: "+grass+"/"+floor+"/"+relight+"/"+mode);
    cases++;
   }
   long[] base=new long[7],candidate=new long[7];Method currentBefore=NativeGround.class.getMethod("beforeDraw");
   for(int pass=-2;pass<7;pass++){
    config(old,true,true,true,0);nativeState(0);long t=System.nanoTime();for(int n=0;n<3000;n++)oldBefore.invoke(null);long elapsed=System.nanoTime()-t;
    config(NativeGround.class,true,true,true,0);nativeState(0);t=System.nanoTime();for(int n=0;n<3000;n++)currentBefore.invoke(null);long elapsed2=System.nanoTime()-t;
    if(pass>=0){base[pass]=elapsed;candidate[pass]=elapsed2;}
   }
   Arrays.sort(base);Arrays.sort(candidate);if(GL11.glGetError()!=0)throw new AssertionError("GL error");
   System.out.println("Native draw104 GPU parity PASS: cases="+cases+" baseline_ns_per_call="+base[3]/3000+" candidate_ns_per_call="+candidate[3]/3000+"; live GL source state refreshed each draw, optional MRT states and masks exact. Both benchmarks use reflection; timing is isolated, not game FPS.");
  }finally{GLFW.glfwDestroyWindow(w);GLFW.glfwTerminate();}
 }
 static void prepare(Class<?> c)throws Exception{
  set(c,null,"world",true);Class<?> s=Class.forName("vpiso.NativeGround$Surface",true,c.getClassLoader());var ctor=s.getDeclaredConstructor();ctor.setAccessible(true);Object surface=ctor.newInstance();
  @SuppressWarnings("unchecked")Map<Integer,Object> map=(Map<Integer,Object>)field(c,null,"SURFACES");map.put(fbo,surface);
 }
 static void config(Class<?> c,boolean grass,boolean floor,boolean relight,int mode)throws Exception{
  set(c,null,"grassMask",grass);set(c,null,"floor",(mode&1)!=0);set(c,null,"floorLevel",(float)(mode-16));set(c,null,"occluder",(mode&1)==0);set(c,null,"sourceOwner",mode+2);set(c,null,"wallOwner",0);
  Object surface=((Map<?,?>)field(c,null,"SURFACES")).get(fbo);set(surface.getClass(),surface,"floorAttached",floor);set(surface.getClass(),surface,"relightAttached",relight);
 }
 static void nativeState(int mode){
  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL20.glUseProgram(program);
  GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,0,0,0,0,0});
  if((mode&1)==0)GL30.glEnablei(GL11.GL_BLEND,0);else GL30.glDisablei(GL11.GL_BLEND,0);
  GL40.glBlendFuncSeparatei(0,(mode&2)==0?GL11.GL_SRC_ALPHA:GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,(mode&4)==0?GL11.GL_ONE:GL11.GL_ZERO,GL11.GL_ONE_MINUS_SRC_ALPHA);
  GL40.glBlendEquationSeparatei(0,(mode&8)==0?GL14.GL_FUNC_ADD:GL14.GL_FUNC_REVERSE_SUBTRACT,GL14.GL_FUNC_ADD);
  GL30.glColorMaski(0,(mode&1)==0,(mode&2)==0,(mode&4)==0,(mode&16)==0);
 }
 static int[] state(){
  int[] result=new int[8*9+9];int at=0;ByteBuffer mask=org.lwjgl.BufferUtils.createByteBuffer(4);
  for(int slot=0;slot<8;slot++){
   result[at++]=GL30.glIsEnabledi(GL11.GL_BLEND,slot)?1:0;
   for(int name:new int[]{GL14.GL_BLEND_SRC_RGB,GL14.GL_BLEND_DST_RGB,GL14.GL_BLEND_SRC_ALPHA,GL14.GL_BLEND_DST_ALPHA,GL20.GL_BLEND_EQUATION_RGB,GL20.GL_BLEND_EQUATION_ALPHA})result[at++]=GL30.glGetIntegeri(name,slot);
   GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,slot,mask);int bits=0;for(int c=0;c<4;c++)if(mask.get(c)!=0)bits|=1<<c;result[at++]=bits;result[at++]=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+slot);
  }
  for(String n:new String[]{"vpIsoGroundTag","vpIsoGrassOccluder","vpIsoGrassMaskOn","vpIsoFloorLevel","vpIsoMetadataStraight","vpIsoRelightWrite","vpIsoWallOwner","vpIsoSourceOwner","vpIsoFloorWrite"})result[at++]=Float.floatToIntBits(GL20.glGetUniformf(program,GL20.glGetUniformLocation(program,n)));
  return result;
 }
}
