import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.*;
import vpiso.*;

/** Dump actual selected shader expansion and link without loading a world or attaching a game. */
public final class Shader110Probe {
 public static void main(String[] args)throws Exception {
  Path output=Path.of(args[0]);Files.createDirectories(output);
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
  long window=GLFW.glfwCreateWindow(32,32,"110 shader validation",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
   for(String pack:new String[]{"default","library","vivid","somber"}) {
    TestShaderPacks.selectLive(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
    GameAccess.set("viewpoint.platform.GlProgram","pack",null,ShaderPacks.active());
    ShaderPacks.linked(ShaderPacks.active());
    String fragment=NativeLighting.fragment(NativeOverlay.FRAGMENT);
    Files.writeString(output.resolve(pack+".frag"),fragment);Files.writeString(output.resolve(pack+".vert"),NativeOverlay.VERTEX);
    if(args.length>1 && args[1].equals("dump-only"))continue;
    GlProgram p=GlProgram.fromSources("VPISO110 shader check",new GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,fragment),Map.of(),Map.of(),List.of("110 shader regression"));
    if(p==null)throw new AssertionError("No linked program");p.delete();
    System.out.println("Shader110 link PASS pack="+pack+" renderer="+GL11.glGetString(GL11.GL_RENDERER));
   }
  }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
}
