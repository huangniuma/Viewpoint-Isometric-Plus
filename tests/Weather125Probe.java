import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipFile;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;
import viewpoint.platform.ShaderPacks;

/** Actual precipitation vertex TF and distant-rain fragment, controlled weather, no game. */
public final class Weather125Probe {
 static ZipFile jar;static String defines,constants;static int checks,cases,program;static boolean old;
 static final Pattern INCLUDE=Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
 static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  old=args.length>1&&args[1].equals("baseline");jar=new ZipFile(args[0]);Main.compatible=true;
  GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
  TestShaderPacks.select("library","high",Map.of("upscaling","off","reflections","off"));
  constants=(String)GameAccess.staticCall("viewpoint.platform.Shaders","constants");
  defines=(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active());
  need(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long window=GLFW.glfwCreateWindow(64,64,"weather125 actual shaders",0,0);need(window!=0,"GL context");
  try{GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
   precipitation();haze();rainTable();need(GL11.glGetError()==0,"GL error");
   System.out.println("WEATHER125 "+(old?"NEGATIVE123":"GPU")+" PASS cases="+cases+" checks="+checks+"; actual precip vertex feedback and distant-rain fragment; projected rain/snow width, camera yaw/zoom, physical vs observer rain distance, ordinary VP isolation; controlled inputs, no game/attach");
  }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();jar.close();}
 }
 static void precipitation()throws Exception {
  String pv=source("precip.vert");Files.writeString(Path.of("research/weather125/precip-expanded.glsl"),pv);
  if(Boolean.getBoolean("weather125.debug")){int k=0;while(pv.contains("hide(); return;")){pv=pv.replaceFirst("hide\\(\\); return;","hide(); vFade="+(++k)+".0; return;");}}
  int v=shader(GL20.GL_VERTEX_SHADER,pv);program=GL20.glCreateProgram();GL20.glAttachShader(program,v);GL20.glAttachShader(program,shader(GL20.GL_FRAGMENT_SHADER,source("precip.frag")));
  GL30.glTransformFeedbackVaryings(program,new String[]{"gl_Position","vAcross","vFade","vSeen"},GL30.GL_INTERLEAVED_ATTRIBS);GL20.glLinkProgram(program);need(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(program));
  GL20.glUseProgram(program);samplers();int buffer=GL15.glGenBuffers(),vertices=4096*6;GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,buffer);GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,vertices*8L*4,GL15.GL_STREAM_READ);GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,buffer);
  // Constant weather-noise, clear rain map, no lamps or relief; original generation remains real.
  for(int unit=0;unit<8;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{1,1,1,1});GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);}
  one("uCount",4096);one("uMean",100000);one("uFall",2);one("uExposure",1f/60);one("uRainClock",.23f);one("uViewExposure",1);vec3("uSky",.5f,.5f,.5f);vec2("uFogRange",1000000,2000000);vec2("uHaze",1.0e20f,0);vec3("vpIsoEye",0,1.5f,0);one("vpIsoEnabled",1);one("vpIsoModels",1);
  int total=0;double smallest=100,largest=0;
  for(float scale:new float[]{4,12,32})for(float yaw:new float[]{0,37,90,180})for(int mode=0;mode<2;mode++){
   Matrix4f projection=new Matrix4f(),view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(0,0,scale),0,0,1280,720,projection,view,eye,yaw);
   matrix("uViewProjection",projection.mul(view,new Matrix4f()));matrix("uRainMatrix",new Matrix4f().zero());matrix("uRainUnproject",new Matrix4f());vec3("uEye",eye[0],eye[1],eye[2]);one("uPixel",2/(720*projection.m11()));vec2("uViewport",1280,720);one("uMode",mode);
   GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);GL30.glBeginTransformFeedback(GL11.GL_POINTS);GL11.glDrawArrays(GL11.GL_POINTS,0,vertices);GL30.glEndTransformFeedback();GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
   FloatBuffer data=BufferUtils.createFloatBuffer(vertices*8);GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,data);int seen=0;double sum=0,min=100,minSeen=1;
   for(int i=0;i<vertices;i+=6){int at=i*8;float fade=data.get(at+6),w=data.get(at+3);if(fade<=.001||Math.abs(data.get(at)/w)>1||Math.abs(data.get(at+1)/w)>1)continue;double width=Math.hypot(data.get(at+4),data.get(at+5));if(!Double.isFinite(width))throw new AssertionError("nonfinite particle");min=Math.min(min,width);minSeen=Math.min(minSeen,data.get(at+7));sum+=width;seen++;}
   need(seen>5,"no visible precipitation mode="+mode+" scale="+scale+" yaw="+yaw);
   double mean=sum/seen;smallest=Math.min(smallest,mean);largest=Math.max(largest,mean);total+=seen;
   if(!old)need(min>=(mode==0?.45:1.05),"subpixel rain/snow width="+min+" mode="+mode+" scale="+scale+" yaw="+yaw);
   if(!old)need(minSeen>=.49999,"unlit rain suppressed "+minSeen);
   else if(mode==0)need(minSeen<.1,"123 unlit-rain negative missing");
   cases++;
  }
  if(old)need(smallest<.08,"123 tiny-particle negative missing "+smallest);
  System.out.println("Precip actual TF "+(old?"123":"125")+" visible="+total+" meanWidthRange="+smallest+".."+largest);
 }
 static void rainTable()throws Exception {
  String screen="#version 330\nout vec2 vUv;void main(){vUv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(vUv*2-1,0,1);}";
  program=GL20.glCreateProgram();GL20.glAttachShader(program,shader(GL20.GL_VERTEX_SHADER,screen));GL20.glAttachShader(program,shader(GL20.GL_FRAGMENT_SHADER,source("distant_rain_table.frag")));GL20.glLinkProgram(program);need(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(program));GL20.glUseProgram(program);samplers();
  int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);int target=texture(32,64,new float[32*64*4]);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);GL11.glViewport(0,0,32,64);
  float[] gradient=new float[256*4];for(int x=0;x<256;x++)gradient[x*4+1]=(x+.5f)*(x+.5f)/(256*256);
  int weather=texture(256,1,gradient),noise=texture(1,1,new float[]{.5f,.5f,.5f,.5f}),dry=texture(1,1,new float[]{0,0,0,0});
  integer("uWeatherMap",0);integer("uWeatherNoise",1);one("uWeatherSpan",32768);vec3("vpIsoEye",0,1.5f,0);
  GL13.glActiveTexture(GL13.GL_TEXTURE0+1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,noise);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,weather);
  one("vpIsoModels",0);vec3("uEye",0,1.5f,0);float[] observer=tablePixels();
  vec3("uEye",-3673,3000,-3673);float[] ordinary=tablePixels();one("vpIsoModels",1);float[] owned=tablePixels();
  double referenceDelta=0,remoteDelta=0;for(int i=0;i<owned.length;i+=4){referenceDelta=Math.max(referenceDelta,Math.abs(owned[i]-observer[i]));remoteDelta=Math.max(remoteDelta,Math.abs(ordinary[i]-observer[i]));}
  need(remoteDelta>5,"table fixture lacks spatial weather");if(old)need(referenceDelta>5,"123 remote-origin negative missing");else need(referenceDelta<.002,"rain table not observer-centred "+referenceDelta);
  GL11.glBindTexture(GL11.GL_TEXTURE_2D,dry);float[] none=tablePixels();for(float f:none)need(Float.isFinite(f),"nonfinite dry table");for(int i=0;i<none.length;i+=4)need(none[i]==0,"dry rain veil created");
  System.out.println("Actual rain integral referenceDelta="+referenceDelta+" remoteDelta="+remoteDelta+"; zero-rain retained");cases+=3;
 }
 static float[] tablePixels(){GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);FloatBuffer b=BufferUtils.createFloatBuffer(32*64*4);GL11.glReadPixels(0,0,32,64,GL11.GL_RGBA,GL11.GL_FLOAT,b);float[] a=new float[b.remaining()];b.get(a);return a;}
 static void haze()throws Exception {
  String screen="#version 330\nout vec2 vUv;void main(){vUv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(vUv*2-1,0,1);}";
  program=GL20.glCreateProgram();GL20.glAttachShader(program,shader(GL20.GL_VERTEX_SHADER,screen));GL20.glAttachShader(program,shader(GL20.GL_FRAGMENT_SHADER,source("distant_rain.frag")));GL20.glLinkProgram(program);need(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(program));GL20.glUseProgram(program);samplers();
  int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);int color=texture(1,1,new float[]{0,0,0,0});GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);GL11.glViewport(0,0,1,1);
  int depth=texture(1,1,new float[]{.5f,.5f,.5f,.5f});float[] rows=new float[64*4];for(int y=0;y<64;y++){double d=30+(16000-30)*Math.pow(y/63.,2);for(int c=0;c<4;c++)rows[y*4+c]=(float)Math.max(0,(d-30)*.5);}int table=texture(1,64,rows);
  vec3("vpIsoEye",0,1.5f,0);vec3("uHorizon",.4f,.4f,.4f);vec3("uForward",0,0,-1);vec3("uSunDir",0,1,0);one("uSunStrength",0);integer("uDepth",0);integer("uDistantRain",1);
  for(float radius:new float[]{10,20,80,200})for(float yaw:new float[]{0,.7f,2.1f}){
   float x=(float)Math.cos(yaw)*radius,z=(float)Math.sin(yaw)*radius;
   Matrix4f inverse=new Matrix4f().translation(x,0,z);matrix("uInvViewProjection",inverse);vec3("uEye",-3673,3000,-3673);
   float normal=rainAlpha(depth,table,0),owned=rainAlpha(depth,table,1);need(normal>.98,"physical-eye negative lacks haze "+normal);
   if(old)need(owned>.98,"123 over-haze negative missing");else if(radius<=20)need(owned<.005,"near-scene grey veil "+owned);else need(owned>0&&owned<.4,"distant-rain effect removed or dense "+owned);
   cases++;
  }
 }
 static float rainAlpha(int depth,int table,int owned){one("vpIsoModels",owned);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL13.glActiveTexture(GL13.GL_TEXTURE0+1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,table);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);FloatBuffer b=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,b);return b.get(3);}
 static int texture(int w,int h,float[] values){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,values);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return t;}
 static void samplers(){Map<Integer,Integer> units=new HashMap<>();for(int i=0;i<GL20.glGetProgrami(program,GL20.GL_ACTIVE_UNIFORMS);i++){IntBuffer n=BufferUtils.createIntBuffer(1),t=BufferUtils.createIntBuffer(1);String name=GL20.glGetActiveUniform(program,i,512,n,t);int type=t.get(0);if(type>=0x8B5D&&type<=0x8B64||type>=0x8DC0&&type<=0x8DDF){int[] a=new int[n.get(0)];Arrays.fill(a,units.computeIfAbsent(type,k->units.size()));GL20.glUniform1iv(loc(name),a);}}}
 static int loc(String n){return GL20.glGetUniformLocation(program,n);}static void one(String n,float v){GL20.glUniform1f(loc(n),v);}static void integer(String n,int v){GL20.glUniform1i(loc(n),v);}static void vec2(String n,float x,float y){GL20.glUniform2f(loc(n),x,y);}static void vec3(String n,float x,float y,float z){GL20.glUniform3f(loc(n),x,y,z);}static void matrix(String n,Matrix4f m){GL20.glUniformMatrix4fv(loc(n),false,m.get(new float[16]));}
 static int shader(int kind,String s){int a=GL20.glCreateShader(kind);GL20.glShaderSource(a,s);GL20.glCompileShader(a);if(GL20.glGetShaderi(a,GL20.GL_COMPILE_STATUS)==0)try{Files.writeString(Path.of("research/weather125/failed-"+kind+".glsl"),s);}catch(Exception ignored){}need(GL20.glGetShaderi(a,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(a));return a;}
 static String source(String n)throws Exception {String s=expand(n,new HashSet<>());int i=s.indexOf('\n');return s.substring(0,i+1)+"#extension GL_ARB_shader_storage_buffer_object : enable\n#extension GL_ARB_shader_image_load_store : enable\n"+defines+s.substring(i+1);}
 static String expand(String n,Set<String> stack)throws Exception {need(stack.add(n),"include cycle");String s=n.equals("constants.glsl")?constants:new String(jar.getInputStream(jar.getEntry("viewpoint/shaders/"+n)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);StringBuilder b=new StringBuilder();for(String line:IsometricShaders.rewrite(n,s).split("\n",-1)){var m=INCLUDE.matcher(line);b.append(m.matches()?expand(m.group(1),stack):line+"\n");}stack.remove(n);return b.toString();}
}
