import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import vpiso.*;

/** Real shipped TrueWeathers shaders: packed normals, native snow, camera depth and mode isolation. */
public final class TrueWeather093Probe {
 static final String DRAW="vptw.AmbientDraw";static final int W=32;
 static int fbo,vao,output,checks;static final Map<String,Integer> tex=new LinkedHashMap<>();
 static final String SCREEN="""
  #version 330 core
  out vec2 vUv;
  void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);vUv=p;gl_Position=vec4(p*2-1,0,1);}
  """;
 public static void main(String[] args)throws Exception {
  if(args.length>0&&args[0].equals("absent")) {
   IsometricBridge.modelRendering(new Frame(),IsometricEffects.DEFAULT);
   TrueWeatherCompat.beforeWeather(new Frame().scene);IsometricBridge.rendering(null);
   System.out.println("TrueWeather093 optional absent PASS");return;
  }
  audit(Path.of(args[0]));
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
  GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long window=GLFW.glfwCreateWindow(W,W,"TrueWeather093",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
   output=texture(new float[]{0,0,0,0});GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,W,W,0,GL11.GL_RGBA,GL11.GL_FLOAT,(FloatBuffer)null);
   fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
   GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
   require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");
   for(String n:new String[]{"uDepth","uColor","uNormal","uAlbedo","uExposure","uMotion","uTrack","uDriftMap","uTWGround"})tex.put(n,texture(new float[]{1,1,1,1}));
   String cover=source("COVER_FS"),water=source("WATER_FS");
   for(String[] pair:new String[][]{{"SCREEN_VS","COVER_FS"},{"SCREEN_VS","WATER_FS"},{"GLASS_VS","GLASS_FS"},{"BOLT_VS","BOLT_FS"},{"PART_VS","PART_FS"},{"SCREEN_VS","FOG_FS"},{"SCREEN_VS","BOW_FS"}}) {
    String fs=source(pair[1]);String rewritten=TrueWeatherCompat.fragment(fs);
    require(TrueWeatherCompat.fragment(rewritten).equals(rewritten),"idempotence "+pair[1]);
    int linked=link(source(pair[0]),rewritten);GL20.glDeleteProgram(linked);checks++;
   }
   require(TrueWeatherCompat.fragment(null)==null,"null passthrough");
   require(TrueWeatherCompat.fragment("#version 330\nvoid main(){}").equals("#version 330\nvoid main(){}"),"unknown passthrough");
   int old=link(SCREEN,cover),fixed=link(SCREEN,TrueWeatherCompat.fragment(cover));
   int actual=((Number)GameAccess.call(DRAW,"program",null,new Class<?>[]{String.class,String.class},SCREEN,cover)).intValue();
   require(GL20.glGetUniformLocation(actual,"vpIsoTwOn")>=0,"actual woven raw compiler did not adapt shader");
   inputs();float[] ground=draw(fixed,true,new float[]{.5f,1,0,1},28/255f,new float[]{.1953f,.1738f,.0566f,1},.5f);
   require(ground[3]>.8f,"packed upward ground snow absent "+Arrays.toString(ground));
   float[] wall=draw(fixed,true,new float[]{.5f,.5f,0,1},28/255f,new float[]{.1953f,.1738f,.0566f,1},.5f);
   require(wall[3]<.001f,"snow painted on vertical wall "+Arrays.toString(wall));checks++;
   float[] badWall=draw(old,false,new float[]{.5f,.5f,0,1},28/255f,new float[]{.1953f,.1738f,.0566f,1},.5f);
   require(badWall[3]>.01f,"old wall error not reproduced");
   float[] snow=draw(fixed,true,new float[]{.5f,1,0,1},208/255f,new float[]{.4f,.4f,.45f,1},.5f);
   require(snow[3]<.001f,"existing VP snow painted twice");checks++;
   for(float[] n:new float[][]{{.2f,.8f,.1f,1},{.5f,1,0,1},{1,.5f,0,1}}) {
    float[] a=draw(old,false,n,28/255f,new float[]{.2f,.15f,.06f,1},.5f);
    float[] b=draw(fixed,false,n,28/255f,new float[]{.2f,.15f,.06f,1},.5f);
    same(a,b,"inactive original cover");checks++;
   }
   // The old late cover divides haze by dark grass albedo, amplifying blue. The early cover must be neutral.
   float[] late=draw(fixed,true,new float[]{.5f,1,0,1},28/255f,new float[]{.35f,.33f,.4f,1},.5f);
   require(late[2]>late[0]*1.7f,"late blue amplification not reproduced "+Arrays.toString(late));
   require(ground[2]<ground[0]*1.4f,"pre-haze snow remains blue "+Arrays.toString(ground));checks++;
   int waterOld=link(SCREEN,water),waterFixed=link(SCREEN,TrueWeatherCompat.fragment(water));
   same(draw(waterOld,false,new float[]{.5f,1,0,1},28/255f,new float[]{.2f,.15f,.06f,1},.5f),
        draw(waterFixed,false,new float[]{.5f,1,0,1},28/255f,new float[]{.2f,.15f,.06f,1},.5f),"inactive water");checks++;
   float[] wetGround=draw(waterFixed,true,new float[]{.5f,1,0,1},28/255f,new float[]{.2f,.15f,.06f,1},.5f);
   require(wetGround[3]>.01f,"wet ground effect lost "+Arrays.toString(wetGround));
   require(draw(waterFixed,true,new float[]{.5f,.5f,0,1},28/255f,new float[]{.2f,.15f,.06f,1},.5f)[3]<.001f,"wet ground on wall");
   require(draw(waterFixed,true,new float[]{.5f,1,0,1},208/255f,new float[]{.2f,.15f,.06f,1},.5f)[3]<.001f,"VP snow overwritten by wet ground");checks+=3;
   String glassVertex="""
    #version 330 core
    out vec2 vUv;out float vSeed;out float vW;
    void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);vUv=p;vSeed=1;vW=6000;gl_Position=vec4(p*2-1,0,1);}
    """;
   int glass=link(glassVertex,TrueWeatherCompat.fragment(source("GLASS_FS")));
   float[] front=draw(glass,true,new float[]{.5f,1,0,1},28/255f,new float[]{.3f,.3f,.3f,1},depth(5999f));
   float[] behind=draw(glass,true,new float[]{.5f,1,0,1},28/255f,new float[]{.3f,.3f,.3f,1},depth(6001f));
   require(front[3]<.001f&&behind[3]>.002f,"remote glass depth occlusion "+Arrays.toString(front)+Arrays.toString(behind));checks++;
   GameAccess.set(DRAW,"coverProg",null,fixed);GameAccess.set(DRAW,"waterProg",null,waterFixed);GameAccess.set(DRAW,"glassProg",null,glass);
   GL20.glUseProgram(old);IsometricBridge.modelRendering(new Frame(),IsometricEffects.DEFAULT);TrueWeatherCompat.uniforms();
   require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==old,"uniform upload changed program");
   require(GL20.glGetUniformf(fixed,GL20.glGetUniformLocation(fixed,"vpIsoTwOn"))==1,"3D flag");
   require(TrueWeatherCompat.skipLate(),"late duplicate not skipped");
   Field early=TrueWeatherCompat.class.getDeclaredField("EARLY");early.setAccessible(true);
   @SuppressWarnings("unchecked") ThreadLocal<Boolean> scope=(ThreadLocal<Boolean>)early.get(null);
   scope.set(true);require(!TrueWeatherCompat.skipLate(),"early callback skipped");scope.remove();
   IsometricBridge.rendering(null);TrueWeatherCompat.uniforms();require(!TrueWeatherCompat.skipLate(),"normal VP callback skipped");
   require(GL20.glGetUniformf(fixed,GL20.glGetUniformLocation(fixed,"vpIsoTwOn"))==0,"mode flag stale");checks++;
   // Exercise @OnExit even when init returns immediately after programs have already been prepared.
   GameAccess.set(DRAW,"partProg",null,old);
   IsometricBridge.modelRendering(new Frame(),IsometricEffects.DEFAULT);GameAccess.staticCall(DRAW,"init");
   require(GL20.glGetUniformf(fixed,GL20.glGetUniformLocation(fixed,"vpIsoTwOn"))==1,"init quick-return missed 3D mode");
   IsometricBridge.rendering(null);GameAccess.staticCall(DRAW,"init");
   require(GL20.glGetUniformf(fixed,GL20.glGetUniformLocation(fixed,"vpIsoTwOn"))==0,"init quick-return missed VP mode");
   require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==old,"init uniform callback changed program");checks++;
   require(GL11.glGetError()==0,"GL error");
   System.out.println("TrueWeather093 GPU PASS: checks="+checks+"; real seven shader pairs link, old wall and late blue negatives, packed ground/vertical wall, existing snow, inactive cover/water, remote window depth, mode flags and program restore.");
  }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
 static String source(String name){return (String)GameAccess.field(DRAW,name,null);}
 static float depth(float distance){float n=IsoCameraMath.NEAR,f=IsoCameraMath.FAR;return ((f+n-2*n*f/distance)/(f-n)+1)*.5f;}
 static void inputs(){put("uExposure",new float[]{1,1,1,1});put("uMotion",new float[]{0,0,0,0});put("uTrack",new float[]{0,0,0,0});put("uDriftMap",new float[]{0,0,0,0});put("uTWGround",new float[]{0,0,0,0});}
 static float[] draw(int program,boolean active,float[] normal,float material,float[] color,float depth){
  put("uDepth",new float[]{depth,depth,depth,1});put("uColor",color);put("uNormal",normal);put("uAlbedo",new float[]{.3373f,.3176f,.0863f,material});
  put("uDriftMap",loc(program,"uWater")>=0?new float[]{1,1,1,1}:new float[]{0,0,0,0});
  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);
  GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL20.glUseProgram(program);
  int unit=0;for(var e:tex.entrySet()){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,e.getValue());GL20.glUniform1i(GL20.glGetUniformLocation(program,e.getKey()),unit++);}
  f(program,"vpIsoTwOn",active?1:0);GL20.glUniform2f(loc(program,"vpIsoTwClips"),IsoCameraMath.NEAR,IsoCameraMath.FAR);
  GL20.glUniformMatrix4fv(loc(program,"uInvVP"),false,new org.joml.Matrix4f().scaling(.1f).get(new float[16]));
  GL20.glUniform3f(loc(program,"uEye"),0,1.5f,0);f(program,"uCover",1);GL20.glUniform4f(loc(program,"uSnow"),.4f,0,1,0);
  GL20.glUniform2f(loc(program,"uViewport"),W,W);f(program,"uFrost",1);GL20.glUniform3f(loc(program,"uLight"),.4f,.4f,.4f);
  if(loc(program,"uWater")>=0){GL20.glUniform4f(loc(program,"uWater"),.8f,1,.5f,1);GL20.glUniform3f(loc(program,"uDriftWin"),-.4f,-.4f,1);}
  GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);FloatBuffer b=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,b);
  float[] mean=new float[4];for(int i=0;i<W*W;i++)for(int j=0;j<4;j++)mean[j]+=b.get(i*4+j)/(W*W);return mean;
 }
 static int loc(int p,String n){return GL20.glGetUniformLocation(p,n);}static void f(int p,String n,float x){GL20.glUniform1f(loc(p,n),x);}
 static int texture(float[] rgba){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,rgba);return t;}
 static void put(String name,float[] values){GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex.get(name));GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,values);}
 static int link(String v,String f){int p=GL20.glCreateProgram();for(int kind:new int[]{GL20.GL_VERTEX_SHADER,GL20.GL_FRAGMENT_SHADER}){int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,kind==GL20.GL_VERTEX_SHADER?v:f);GL20.glCompileShader(s);require(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(s));GL20.glAttachShader(p,s);GL20.glDeleteShader(s);}GL20.glLinkProgram(p);require(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}
 static void same(float[] a,float[] b,String label){for(int i=0;i<4;i++)require(Math.abs(a[i]-b[i])<.00001f,label+Arrays.toString(a)+Arrays.toString(b));}
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void audit(Path woven)throws Exception {
  List<String> order=new ArrayList<>();new ClassReader(Files.readAllBytes(Path.of("build/classes/vpiso/IsoDrawer.class"))).accept(new ClassVisitor(Opcodes.ASM9){
   public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(!name.equals("render"))return null;return new MethodVisitor(Opcodes.ASM9){public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean iface){if(owner.equals("vpiso/TrueWeatherCompat")||owner.equals("vpiso/NativeTrees")||owner.equals("viewpoint/render/WorldRenderer"))order.add(owner+"."+method);}};}
  },0);
  require(Collections.frequency(order,"vpiso/TrueWeatherCompat.beforeWeather")==1,"early callback count");
  require(order.indexOf("vpiso/NativeTrees.renderWorld")<order.indexOf("vpiso/TrueWeatherCompat.beforeWeather")&&order.indexOf("vpiso/TrueWeatherCompat.beforeWeather")<order.indexOf("viewpoint/render/WorldRenderer.drawWeather"),"weather ordering");
  List<String> calls=new ArrayList<>();new ClassReader(Files.readAllBytes(woven.resolve("vptw/AmbientDraw.class"))).accept(new ClassVisitor(Opcodes.ASM9){
   public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean iface){if(owner.equals("vpiso/TrueWeatherCompat"))calls.add(name+":"+method);}};}
  },0);
  require(calls.containsAll(List.of("draw:skipLate","init:uniforms","program:fragment")),"missing woven advice "+calls);
  System.out.println("TrueWeather093 ordering/weaving PASS: early draw once, after trees/before weather, late callback guarded.");
 }
}
