import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Original and candidate per-draw state are exercised against the same real GL driver. */
public final class NativeDraw109Probe {
 public static int calls;
 public static void uniform(int at,float value){calls++;GL20.glUniform1f(at,value);}
 static Class<?> old;static Method oldBefore;static int fbo,program,baseProgram,candidateProgram;
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
  try(var loader=new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},NativeDraw109Probe.class.getClassLoader()){
   @Override protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{if(!n.equals("vpiso.NativeGround")&&!n.startsWith("vpiso.NativeGround$"))return super.loadClass(n,resolve);Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;}
  }) {
   GLFW.glfwMakeContextCurrent(w);GL.createCapabilities();fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
   for(int i=0;i<8;i++){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,32,32,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+i,GL11.GL_TEXTURE_2D,t,0);}
   baseProgram=NativeGroundProbe.link(NativeVision.VERTEX,FRAGMENT);candidateProgram=NativeGroundProbe.link(NativeVision.VERTEX,FRAGMENT);program=candidateProgram;GL20.glUseProgram(program);
   old=loader.loadClass("vpiso.NativeGround");oldBefore=old.getMethod("beforeDraw");prepare(old);prepare(NativeGround.class);
   int cases=0;
   for(boolean grass:new boolean[]{false,true})for(boolean floor:new boolean[]{false,true})for(boolean relight:new boolean[]{false,true})for(int mode=0;mode<32;mode++){
    program=baseProgram;config(old,grass,floor,relight,mode);nativeState(mode);oldBefore.invoke(null);int[] a=state();
    program=candidateProgram;config(NativeGround.class,grass,floor,relight,mode);nativeState(mode);NativeGround.beforeDraw();int[] b=state();
    if(!Arrays.equals(a,b))throw new AssertionError("Draw state changed: "+grass+"/"+floor+"/"+relight+"/"+mode);
    cases++;
   }
   // The first draw of a new pass must overwrite a previous pass's uniform values.
   for(float level:new float[]{0f,-0f,Float.NaN,Float.POSITIVE_INFINITY,-Float.POSITIVE_INFINITY}) {
    for(Class<?> type:new Class<?>[]{old,NativeGround.class})((Map<?,?>)field(type,null,"TAGS")).clear();
    program=baseProgram;config(old,true,true,true,0);set(old,null,"floorLevel",level);nativeState(0);oldBefore.invoke(null);int[] a=state();
    program=candidateProgram;config(NativeGround.class,true,true,true,0);set(NativeGround.class,null,"floorLevel",level);nativeState(0);NativeGround.beforeDraw();int[] b=state();
    if(!Arrays.equals(a,b))throw new AssertionError("First-pass float uniform differs");cases++;
   }
   // Foreign output FBOs and program zero retain the same original fallbacks.
   int unknown=GL30.glGenFramebuffers();
   for(boolean shader:new boolean[]{true,false}) {
    program=baseProgram;GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,unknown);GL20.glUseProgram(shader?program:0);oldBefore.invoke(null);int[] a=state();
    program=candidateProgram;GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,unknown);GL20.glUseProgram(shader?program:0);NativeGround.beforeDraw();int[] b=state();
    if(!Arrays.equals(a,b))throw new AssertionError("Unknown output/program0 changed");cases++;
   }
   GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL30.glDeleteFramebuffers(unknown);
   int baseA=baseProgram,candidateA=candidateProgram;
   String subset="#version 330\nuniform float vpIsoGroundTag,vpIsoSourceOwner;layout(location=0)out vec4 c;void main(){c=vec4(vpIsoGroundTag,vpIsoSourceOwner,0,1);}";
   int baseB=NativeGroundProbe.link(NativeVision.VERTEX,subset),candidateB=NativeGroundProbe.link(NativeVision.VERTEX,subset);
   for(int sequence=0;sequence<16;sequence++)for(int owner:new int[]{0,1,2,1023}) {
    baseProgram=(sequence&1)==0?baseA:baseB;candidateProgram=(sequence&1)==0?candidateA:candidateB;
    program=baseProgram;config(old,true,true,true,sequence);set(old,null,"wallOwner",owner);nativeState(sequence);oldBefore.invoke(null);int[] a=state();
    program=candidateProgram;config(NativeGround.class,true,true,true,sequence);set(NativeGround.class,null,"wallOwner",owner);nativeState(sequence);NativeGround.beforeDraw();int[] b=state();
    if(!Arrays.equals(a,b))throw new AssertionError("Program switch/absent uniform/wall owner differs");cases++;
   }
   baseProgram=baseA;candidateProgram=candidateA;
   Method currentBefore=NativeGround.class.getMethod("beforeDraw");
   for(Class<?> type:new Class<?>[]{old,NativeGround.class}) {
    program=type==old?baseProgram:candidateProgram;((Map<?,?>)field(type,null,"TAGS")).clear();config(type,true,true,true,0);nativeState(0);
    Method before=type.getMethod("beforeDraw");calls=0;before.invoke(null);int first=calls;
    calls=0;for(int n=0;n<100;n++)before.invoke(null);int repeated=calls;
    if(type==old && (first!=9 || repeated!=900))throw new AssertionError("Baseline fixture not counting9uniforms: "+first+"/"+repeated);
    if(type!=old && (first!=9 || repeated!=0))throw new AssertionError("Owned uniform values not reused: "+first+"/"+repeated);
    System.out.println("UNIFORMS109 mode="+(type==old?"baseline":"candidate")+" first="+first+" repeated100="+repeated);
   }
   // A later native draw changes blend/source flags; no original GL state may be reused.
   for(int mode=31;mode>=0;mode--) {
    program=baseProgram;config(old,true,true,true,mode);nativeState(mode);oldBefore.invoke(null);int[] a=state();
    program=candidateProgram;config(NativeGround.class,true,true,true,mode);nativeState(mode);currentBefore.invoke(null);int[] b=state();
    if(!Arrays.equals(a,b))throw new AssertionError("Later native state stale: "+mode);cases++;
   }
   if(GL11.glGetError()!=0)throw new AssertionError("GL error");
   System.out.println("Native draw109 GPU parity PASS: cases="+cases+"; every auxiliary uniform/blend/mask/drawbuffer value matches108, native state changes remain live;100same-value draws remove900owned uniform submissions, no FPS claim.");

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
  for(String n:new String[]{"vpIsoGroundTag","vpIsoGrassOccluder","vpIsoGrassMaskOn","vpIsoFloorLevel","vpIsoMetadataStraight","vpIsoRelightWrite","vpIsoWallOwner","vpIsoSourceOwner","vpIsoFloorWrite"}){int location=GL20.glGetUniformLocation(program,n);result[at++]=location<0?Integer.MIN_VALUE:Float.floatToIntBits(GL20.glGetUniformf(program,location));}
  return result;
 }
}
