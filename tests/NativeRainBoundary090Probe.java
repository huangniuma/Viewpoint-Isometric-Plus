import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.*;
import vpiso.*;

/** Actual captured remote-camera depths, including the089 floor->basement failure. */
public final class NativeRainBoundary090Probe {
 static boolean previous;
 static GlProgram program;
 static int cases,previousLeaks;
 public static void main(String[] args)throws Exception {
  previous=args.length>0&&args[0].equals("baseline089");String pack=args.length>(previous?1:0)?args[previous?1:0]:"default";
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
  GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
  long window=GLFW.glfwCreateWindow(16,16,"native rain floor090",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;GlProgram.onUse(IsometricShaders::onUse);
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
   TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of());
   GL30.glBindVertexArray(GL30.glGenVertexArrays());GL13.glActiveTexture(GL13.GL_TEXTURE0);
   int color=NativeRain089Probe.tex(GL30.GL_RGBA32F,GL11.GL_RGBA,4,new float[4]);
   NativeRain089Probe.rain=NativeRain089Probe.tex(GL30.GL_RGBA32F,GL11.GL_RGBA,4,new float[]{.2f,.3f,.4f,.75f});
   NativeRain089Probe.depth=NativeRain089Probe.tex(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,1,new float[]{.5f});
   int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
   GL11.glViewport(0,0,1,1);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
   NativeRain089Probe.fixture();
   GlProgram real=GlProgram.create("rain090 actual resource","screen.vert","rain_over.frag");String source=(String)GameAccess.field("viewpoint.platform.GlProgram","source",real);int split=source.lastIndexOf("#version");
   String vertex="#version330\nuniform vec2 sampleUv;out vec2 vUv;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);vUv=sampleUv;}".replace("#version330","#version 330");
   program=GlProgram.fromSources("rain090 captured receivers",new GlProgram.Sources(vertex,null,null,null,source.substring(split)),Map.of(),Map.of(),List.of("rain090 fixture"));
   List<Path> fixtures;try(var paths=Files.list(Path.of("research/rain090/fixtures"))){fixtures=paths.filter(p->p.toString().endsWith(".properties")).sorted().toList();}
   for(Path file:fixtures) {
    Properties props=new Properties();try(var in=Files.newBufferedReader(file)){props.load(in);}
    for(int delta:new int[]{-1,0,1,7}) {
     float[] camera=values(props,"camera"),origin=values(props,"origin");camera[2]+=delta;origin[2]+=delta;
     Object weather=GameAccess.field("vpiso.NativeViewData","weather",NativeRain089Probe.data);
     for(String n:new String[]{"x","y","z"})GameAccess.set("vpiso.NativeWeather",n,weather,(int)origin[n.equals("x")?0:n.equals("y")?1:2]);
     float[] size=values(props,"size");for(int i=0;i<3;i++)GameAccess.set("vpiso.NativeWeather",new String[]{"width","height","layers"}[i],weather,(int)size[i]);
     for(int i=0;i<3;i++)GameAccess.set("vpiso.NativeWeather",new String[]{"camX","camY","camZ"}[i],weather,camera[i]);
     ByteBuffer tiles=BufferUtils.createByteBuffer((int)(size[0]*size[1]*size[2]));for(int i=0;i<tiles.capacity();i++)tiles.put(i,(byte)2);
     GameAccess.set("vpiso.NativeWeather","pixels",weather,tiles);GameAccess.set("vpiso.NativeWeather","ready",weather,true);GameAccess.set("vpiso.NativeWeather","dirty",weather,true);
     Matrix4f inverse=new Matrix4f().set(values(props,"inverse"));NativeRain089Probe.context.invViewProjection.set(inverse);
     NativeRain089Probe.context.viewProjection.set(values(props,"toDepth"));
     ((Matrix4f)GameAccess.field("viewpoint.render.FrameContext","invPlainViewProjection",NativeRain089Probe.context)).identity();
     GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,NativeRain089Probe.depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,new float[]{Float.parseFloat(props.getProperty("depth"))});
     NativeRain089Probe.data.begin();try {
      IsometricBridge.modelWorldRendering(NativeRain089Probe.frame,IsometricEffects.DEFAULT,false);
      float alpha=draw(values(props,"uv"));
      if(previous){if(alpha>.5)previousLeaks++;}else if(alpha>.001)throw new AssertionError("Real captured floor leaked rain: "+file+" floor="+delta+" alpha="+alpha);
      // The same pixel's outside/missing receiver and sky must still show precipitation.
      for(int state:new int[]{0,1}) {for(int i=0;i<tiles.capacity();i++)tiles.put(i,(byte)state);GameAccess.set("vpiso.NativeWeather","dirty",weather,true);if(draw(values(props,"uv"))<.74)throw new AssertionError("Outdoor/missing precipitation lost");}
      cases++;
     }finally{NativeRain089Probe.data.end();IsometricBridge.rendering(null);}
    }
   }
   if(cases<100||previous&&previousLeaks<20)throw new AssertionError("Vacuous captured camera control cases="+cases+" leaks="+previousLeaks);
   if(GL11.glGetError()!=0)throw new AssertionError("GL error");
   System.out.println((previous?"Previous089 floor-boundary leak negative PASS":"Native rain boundary090 GPU PASS")+" pack="+pack+" cases="+cases+" oldLeaks="+previousLeaks+"; actual captured6000-unit camera/jitter/depth and shader, basement/upper floors, exposed and missing-square identity.");
  } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
 static float draw(float[] uv){program.use();program.set("sampleUv",uv[0],uv[1]);program.setInt("uRain",0);program.set("uPanini",0,0,0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,NativeRain089Probe.rain);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);FloatBuffer result=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,result);return result.get(3);}
 static float[] values(Properties props,String name){String[] parts=props.getProperty(name).split(",");float[] out=new float[parts.length];for(int i=0;i<parts.length;i++)out[i]=Float.parseFloat(parts[i]);return out;}
}
