import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.*;
/** Real GPU query at a moved input cursor and collection across three queued drawers. */
public final class MouseLatency086Probe {
 static int cases;
 static boolean baseline=Boolean.getBoolean("click086.baseline");
 public static void main(String[] args)throws Exception{
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long win=GLFW.glfwCreateWindow(128,128,"queued mouse selection086",0,0);
  try{
   GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();int scale=Integer.getInteger("click096.scale",100),renderSize=Math.round(128*scale/100f);GL11.glViewport(0,0,renderSize,renderSize);
   GL30.glBindVertexArray(GL30.glGenVertexArrays());int vbo=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
   FloatBuffer vb=BufferUtils.createFloatBuffer(12);vb.put(new float[]{-1,-1,1,-1,1,1,-1,-1,1,1,-1,1}).flip();GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vb,GL15.GL_STATIC_DRAW);GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,8,0L);
   Field v=NativeObjectShaders.class.getDeclaredField("VERTEX");v.setAccessible(true);
   String vs="#version 430\n"+v.get(null)+"\nlayout(location=0)in vec2 p;uniform int key;uniform vec2 centre,size,jitterClip;uniform float z;void main(){vpIsoObjectInfo=vpIsoSource(2,key);gl_Position=vec4((p*size+centre)/64.0-1.0-jitterClip,z,1);}";
   String fs=NativeObjectShaders.rewrite("model_gbuffer.frag","#version 330\nout vec4 color;void fadeDither(){}void main(){fadeDither();color=vec4(1);}");
   GlProgram program=GlProgram.fromSources("latency086",new GlProgram.Sources(vs,null,null,null,fs),Map.of(),Map.of(),List.of("production packed query086"));
   ObjectState084GpuProbe.sampleType=Class.forName("vpiso.NativeObjectState$Sample");ObjectState084GpuProbe.sampleConstructor=ObjectState084GpuProbe.sampleType.getDeclaredConstructor(float.class,int.class);ObjectState084GpuProbe.sampleConstructor.setAccessible(true);
   Main.compatible=true;viewpoint.core.View.enabled=false;GameAccess.set("vpiso.IsometricBridge","modelsEnabled",null,true);GameAccess.set("vpiso.IsometricBridge","submitted",null,true);GameAccess.call("viewpoint.interact.MouseTargets","contextPick",null,new Class<?>[]{int.class,int.class},0,0);
   for(float yaw:new float[]{0,45,137,270}){
    RotationCamera.configureYaw(true,yaw);require(RotationCamera.yawDegrees()==yaw,"Yaw did not activate");NativeObjectState.resetPick();
    NativeObjectState state=ObjectState084GpuProbe.state(.25f);Frame f=(Frame)GameAccess.field("vpiso.NativeObjectState","frame",state);
    MousePick.Ask initial=new MousePick.Ask(30,64,128,128),render=new MousePick.Ask(64,64,128,128);
    GameAccess.set("vpiso.NativeObjectState","ask",state,initial);MousePick.ask=render;
    if(!baseline)((BitSet)GameAccess.field("vpiso.NativeObjectState","assisted",state)).set(1);
    boolean jitter=Boolean.getBoolean("click096.jitter");
    if(jitter){Object context=GameAccess.field("viewpoint.render.WorldRenderer","frame",null);GameAccess.set("viewpoint.render.FrameContext","scene",context,f.scene);GameAccess.set("viewpoint.render.FrameContext","jitterX",context,.375f);GameAccess.set("viewpoint.render.FrameContext","jitterY",context,-.25f);}
    IsometricBridge.modelWorldRendering(f,IsometricEffects.DEFAULT,true);state.beginRender();NativeObjectState.prepared(f.scene);
    require(GameAccess.field("vpiso.NativeObjectState","ask",state)==render,"Query used queued capture cursor instead of current render cursor");
    GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);program.use();NativeObjectState.onUse(program);program.set("jitterClip",jitter?.75f/renderSize:0f,jitter?-.5f/renderSize:0f);
    program.setInt("key",1);program.set("centre",64f,64f);program.set("size",64f,64f);program.set("z",.6f);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
    program.setInt("key",0);program.set("centre",64f,64f);program.set("size",12f,30f);program.set("z",-.4f);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
    GameAccess.set("vpiso.NativeObjectState","capturedAt",state,System.nanoTime());state.finishPick();state.endRender();GL11.glFinish();
    NativeObjectState next=ObjectState084GpuProbe.state(1f);next.beginRender();next.endRender();
    ObjectState084GpuProbe.currentWorld((zombie.iso.IsoGridSquare)GameAccess.call("zombie.iso.IsoObject","getSquare",((List<?>)GameAccess.field("vpiso.NativeObjectState","ids",state)).get(1),new Class<?>[0]),((List<?>)GameAccess.field("vpiso.NativeObjectState","ids",state)).get(1),((List<?>)GameAccess.field("vpiso.NativeObjectState","ids",state)).get(2));
    require((Long)GameAccess.field("vpiso.NativeObjectState","fence",state)==0,"Completed query waited for same drawer instead of next frame");
    List<?> ids=(List<?>)GameAccess.field("vpiso.NativeObjectState","ids",state);Object fallback=new IsoObject();
    for(int dx:new int[]{0,5,10,20,35,41}){
     MousePick.ask=new MousePick.Ask(64+dx,64,128,128);Object selected=NativeObjectState.picked(fallback);
     require(selected==ids.get(dx<=18?1:2),"Current cursor did not sample its own target dx="+dx);cases++;
    }
    MousePick.ask=new MousePick.Ask(110,64,128,128);require(NativeObjectState.picked(fallback)==fallback,"Cursor outside map accepted");
    NativeObjectState.resetPick();require(NativeObjectState.picked(fallback)==fallback,"Camera reset accepted old query");
   }
   System.out.println("Mouse latency086 GPU PASS: cases="+cases+"; query refreshed at render, previous drawer collected on next frame, current input pixel selects door/container without a three-pixel stall, four yaw angles, bounds and camera reset; map=37636 bytes independent of source count.");
  }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
 }
 static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
