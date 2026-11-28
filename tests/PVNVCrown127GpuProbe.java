import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipFile;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.GameAccess;
import vpiso.IsometricShaders;

/** Actual installed gbuffer/forward fragments, production rewrite and FP16 WBOIT; fixture vertex only. */
public final class PVNVCrown127GpuProbe {
 static final int W=32;
 static final Pattern INCLUDE=Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
 static ZipFile archive; static String constants,defines; static int program,checks,cases; static Path out;
 static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  boolean expect122=args.length>2&&args[2].equals("baseline");out=Path.of(args[1]);Files.createDirectories(out);
  archive=new ZipFile(args[0]);vpiso.Main.compatible=true;
  GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
  TestShaderPacks.select("default","high",Map.of("upscaling","off","reflections","off"));
  constants=(String)GameAccess.call("viewpoint.platform.Shaders","constants",null,new Class<?>[0]);
  Object active=GameAccess.call("viewpoint.platform.ShaderPacks","active",null,new Class<?>[0]);
  defines=(String)GameAccess.field(active.getClass().getName(),"defines",active);
  String fragment=defined(expand("forward.frag",new HashSet<>()));
  String opaqueFragment=defined(expand("gbuffer.frag",new HashSet<>()));
  Files.writeString(out.resolve("actual-forward-expanded.frag"),fragment);
  Files.writeString(out.resolve("actual-gbuffer-expanded.frag"),opaqueFragment);
  require(fragment.contains("vec4 sampleSprite("),"actual sprite sampling absent");
  require(fragment.contains("translucentSum = vec4(colour * alpha, alpha) * weight;"),"actual WBOIT absent");
  String vertex="""
   #version 430
   out vec2 vUv;out vec3 vLocal;flat out vec4 vRect;out vec3 vNormal;out vec3 vPos;out float vDistance;
   flat out float vFill;flat out int vFlags;flat out float vFloorLayer;flat out vec2 vFade;flat out vec2 vLightOrigin;flat out float vFoliage;
   flat out vec3 vpIsoObjectInfo;flat out vec3 vpIsoTreeRoot;flat out int vpIsoPlants;flat out vec4 vpIsoWallParent;
   uniform vec2 interval;uniform float nativeAlpha;uniform int wood;
   void main(){vUv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(vUv*2-1,0,1);
    vLocal=vec3(vUv,0);vRect=vec4(0,0,1,1);vNormal=wood!=0?vec3(0,1,0):vec3(0);vPos=vec3(vUv,1);vDistance=1;
    vFill=0;vFlags=0;vFloorLayer=0;vFade=interval;vLightOrigin=vec2(0);vFoliage=0;
    vpIsoObjectInfo=vec3(1,0,1.0625+floor(nativeAlpha*255.0+0.5)/4096.0);vpIsoTreeRoot=vec3(0);vpIsoPlants=0;vpIsoWallParent=vec4(0);}
   """;
  Files.writeString(out.resolve("fixture.vert"),vertex);
  require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
  long win=GLFW.glfwCreateWindow(W,W,"actual forward overlap123",0,0);require(win!=0,"hidden GL");
  try{GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();System.out.println("GL "+GL11.glGetString(GL11.GL_VERSION)+" / "+GL11.glGetString(GL11.GL_RENDERER));
   int forward=link(vertex,fragment),gbuffer=link(vertex,opaqueFragment);
   int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
   target(GL30.GL_RGBA16F,GL11.GL_RGBA,0);target(GL30.GL_R16F,GL11.GL_RED,1);target(GL30.GL_RG16F,GL30.GL_RG,2);
   require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1,GL30.GL_COLOR_ATTACHMENT2});GL11.glViewport(0,0,W,W);
   int opaqueFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,opaqueFbo);target(GL30.GL_RGBA16F,GL11.GL_RGBA,0);require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"opaque FBO");GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
   int texture=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
   // Every active sampler type has its own texture unit, avoiding type alias validation.
   List<String> samplers=new ArrayList<>();for(int p:new int[]{forward,gbuffer}){program=p;GL20.glUseProgram(program);Map<Integer,Integer> types=new HashMap<>();types.put(GL20.GL_SAMPLER_2D,0);int count=GL20.glGetProgrami(program,GL20.GL_ACTIVE_UNIFORMS);
   for(int i=0;i<count;i++){IntBuffer n=BufferUtils.createIntBuffer(1),t=BufferUtils.createIntBuffer(1);String name=GL20.glGetActiveUniform(program,i,512,n,t);int type=t.get(0);if(isSampler(type)){int unit=types.computeIfAbsent(type,key->types.size());int[] units=new int[n.get(0)];Arrays.fill(units,unit);GL20.glUniform1iv(GL20.glGetUniformLocation(program,name),units);samplers.add(name+" type="+type+" unit="+unit);}}
   set("vpIsoObjectsOn",1);set("vpIsoPickOn",0);set("uDebugView",1);set("uSpriteFilter",0);set("uAlphaHash",0);set("uWireframe",0);if(p==forward)GL20.glUniform2f(loc("uAlphaRange"),.04f,.85f);
   }Files.write(out.resolve("active-samplers.txt"),samplers);
   // sampler2D must use art unit; all other samplers are unreachable for debugView=1
   // or read only lighting whose value does not affect that debug colour.
   GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
   GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glEnable(GL11.GL_BLEND);GL40.glBlendFunci(0,GL11.GL_ONE,GL11.GL_ONE);GL40.glBlendFunci(1,GL11.GL_ZERO,GL11.GL_ONE_MINUS_SRC_COLOR);GL40.glBlendFunci(2,GL11.GL_ONE,GL11.GL_ONE);
   List<String> csv=new ArrayList<>();csv.add("sourceAlpha,share,noiseOffset,meanCoverage,minCoverage,maxCoverage,expectedSource,expected122,maxSourceError,distinctColours");
   int baselineFailures=0;double peakError=0;
   for(boolean wood:new boolean[]{false,true})for(float crown:new float[]{0,1f/255,.01f,.25f,.5f,1})for(float share:new float[]{.1f,.25f,.5f,.75f,.9f})for(float noise:new float[]{0,17.25f,43.5f}){
    float source=wood?1f:crown<=1f/255?0f:Math.round(crown*255)/255f;
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,opaqueFbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glDisable(GL11.GL_BLEND);GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[4]);program=gbuffer;GL20.glUseProgram(program);GL20.glUniform1f(loc("nativeAlpha"),crown);set("wood",wood?1:0);GL20.glUniform1f(loc("uFadeNoise"),noise);art(.8f,.2f,.1f);draw(0,share);art(.1f,.2f,.8f);draw(share,2);
    FloatBuffer op=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,op);
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1,GL30.GL_COLOR_ATTACHMENT2});GL11.glEnable(GL11.GL_BLEND);clear();program=forward;GL20.glUseProgram(program);GL20.glUniform1f(loc("nativeAlpha"),crown);set("wood",wood?1:0);GL20.glUniform1f(loc("uFadeNoise"),noise);
    art(.8f,.2f,.1f);draw(0,share);art(.1f,.2f,.8f);draw(share,2);
    FloatBuffer reveal=BufferUtils.createFloatBuffer(W*W);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT1);GL11.glReadPixels(0,0,W,W,GL11.GL_RED,GL11.GL_FLOAT,reveal);
    FloatBuffer sums=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,sums);
    double mean=0,min=2,max=-1,error=0,minRed=2,maxRed=-1;Set<Integer> colours=new HashSet<>();float predicted=source==1f?.8f*share+.1f*(1-share):.8f;
    for(int i=0;i<W*W;i++){
      double coverage=op.get(i*4)>0?1:1-reveal.get(i);mean+=coverage;min=Math.min(min,coverage);max=Math.max(max,coverage);error=Math.max(error,Math.abs(coverage-source));
      float red=(op.get(i*4)>0?op.get(i*4)*reveal.get(i):0)+(sums.get(i*4+3)>0?sums.get(i*4)/sums.get(i*4+3)*(1-reveal.get(i)):0);
      // Source opacity is accounted separately; colour below is over black.
      minRed=Math.min(minRed,red);maxRed=Math.max(maxRed,red);float expected=predicted*source;colours.add(Math.round(1000*red));
      if(!expect122){require(Math.abs(coverage-source)<.003,"source coverage pulse at="+i+" got="+coverage+" expected="+source);require(Math.abs(red-expected)<.004,"grain/colour mismatch at="+i+" red="+red+" expected="+expected);}
    }
    if(!expect122)require(maxRed-minRed<.001,"spatial grain remains RGB range="+(maxRed-minRed));
    else require(colours.size()==2,"baseline123 no grain negative missing");
    mean/=W*W;peakError=Math.max(error,peakError);if(error>.005)baselineFailures++;
    csv.add(source+","+share+","+noise+","+mean+","+min+","+max+","+source+","+predicted+","+error+","+colours.size());cases++;
   }
   Files.write(out.resolve("overlap.csv"),csv);require(GL11.glGetError()==0,"GL error "+GL11.glGetError());
   System.out.println("PVNVCROWN127 ACTUAL GBUFFER/FORWARD "+(expect122?"123 GRAIN REPRODUCED":"CANDIDATE PASS")+" cases="+cases+" checks="+checks+" sourceContractFailures="+baselineFailures+" peakOpacityError="+peakError+"; actual installed gbuffer/forward+production rewrite, debug albedo, FP16 opaque/sum/reveal/distance, old/new distinct art, correlated interval noise, no game/attach");
  }finally{GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();archive.close();}
 }
 static boolean isSampler(int type){return type==0x8B5E||type==0x8B5F||type==0x8B60||type==0x8B62||type==0x8DC1||type==0x8DC4||type==0x8DC2||type==0x8DCA||type==0x8DD2;}
 static int loc(String n){return GL20.glGetUniformLocation(program,n);}static void set(String n,int v){GL20.glUniform1i(loc(n),v);}
 static void draw(float lo,float hi){GL20.glUniform2f(loc("interval"),lo,hi);GL20.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
 static void art(float r,float g,float b){GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{r,g,b,1});}
 static void clear(){GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[4]);GL30.glClearBufferfv(GL11.GL_COLOR,1,new float[]{1,1,1,1});GL30.glClearBufferfv(GL11.GL_COLOR,2,new float[4]);}
 static void target(int internal,int format,int at){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,W,W,0,format,GL11.GL_FLOAT,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+at,GL11.GL_TEXTURE_2D,t,0);}
 static int shader(int kind,String source){int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,source);GL20.glCompileShader(s);require(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(s));return s;}
 static int link(String vertex,String fragment){int p=GL20.glCreateProgram();GL20.glAttachShader(p,shader(GL20.GL_VERTEX_SHADER,vertex));GL20.glAttachShader(p,shader(GL20.GL_FRAGMENT_SHADER,fragment));GL20.glLinkProgram(p);require(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}
 static String defined(String s){int at=s.indexOf('\n');return s.substring(0,at+1)+defines+s.substring(at+1);}
 static String expand(String path,Set<String> stack)throws Exception{require(stack.add(path),"include cycle "+path);String s=path.equals("constants.glsl")?constants:new String(archive.getInputStream(archive.getEntry("viewpoint/shaders/"+path)).readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");s=IsometricShaders.rewrite(path,s);StringBuilder b=new StringBuilder();for(String line:s.split("\n",-1)){Matcher m=INCLUDE.matcher(line);b.append(m.matches()?expand(m.group(1),stack):line+"\n");}stack.remove(path);return b.toString();}
}
