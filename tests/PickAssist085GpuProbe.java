import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import vpiso.*;
/** Small-target, dither and occlusion failures, using production rewrite/upload/readback. */
public final class PickAssist085GpuProbe {
 static int cases;
 public static void main(String[] args)throws Exception {
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
  GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long window=GLFW.glfwCreateWindow(128,128,"interaction aperture085",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
   int vbo=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
   FloatBuffer vertices=BufferUtils.createFloatBuffer(24);float[] quad={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};vertices.put(quad).put(quad).flip();
   GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,8,0L);
   Field v=NativeObjectShaders.class.getDeclaredField("VERTEX");v.setAccessible(true);
   String vs="#version 430\n"+v.get(null)+"\nflat out vec2 vFade;layout(location=0)in vec2 p;uniform int kind,key;uniform float z;uniform vec2 offset,size;void main(){vFade=vec2(0,1);vpIsoObjectInfo=vpIsoSource(kind,kind==0?gl_VertexID:key);gl_Position=vec4((p*size+offset)/32.0,z,1);}";
   // Run the real rewrite, including its placement relative to view fading and material discard.
   String fs=NativeObjectShaders.rewrite("gbuffer.frag","#version 330\nuniform int hole;const int vFlags=0,FLAG_BAKED_FLOOR=64;out vec4 color;void fadeDither(){}void main(){fadeDither();if(hole!=0)discard;color=vec4(.3,.6,.8,1);}");
   GlProgram program=GlProgram.fromSources("assist085",new GlProgram.Sources(vs,null,null,null,fs),Map.of(),Map.of(),List.of("production 085 rewrite"));
   GameAccess.set("vpiso.IsometricBridge","modelsEnabled",null,true);GameAccess.set("vpiso.IsometricBridge","submitted",null,true);
   GameAccess.call("viewpoint.interact.MouseTargets","contextPick",null,new Class<?>[]{int.class,int.class},0,0);
   ObjectState084GpuProbe.sampleType=Class.forName("vpiso.NativeObjectState$Sample");ObjectState084GpuProbe.sampleConstructor=ObjectState084GpuProbe.sampleType.getDeclaredConstructor(float.class,int.class);ObjectState084GpuProbe.sampleConstructor.setAccessible(true);
   for(int kind=0;kind<3;kind++)for(int viewport:new int[]{32,64,128})for(int scenario=0;scenario<8;scenario++)for(boolean reverse:new boolean[]{false,true}){
    float alpha=scenario==3?0f:.08f;
    NativeObjectState state=ObjectState084GpuProbe.state(alpha);
    Frame frame=(Frame)GameAccess.field("vpiso.NativeObjectState","frame",state);
    BitSet assist=(BitSet)GameAccess.field("vpiso.NativeObjectState","assisted",state);assist.set(1);if(scenario==6)assist.set(2);
    IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);state.beginRender();
    try {
     NativeObjectState.prepared(frame.scene);GL11.glViewport(0,0,viewport,viewport);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);
     ByteBuffer[] colours=new ByteBuffer[2];
     for(int enabled=0;enabled<=1;enabled++){
      GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);program.use();NativeObjectState.onUse(program);program.setInt("vpIsoPickOn",enabled);program.setInt("kind",kind);
      for(int step=0;step<2;step++){
       boolean front=(step==0)==reverse;int source=front?0:1;
       program.setInt("key",source);program.setInt("hole",front&&scenario==7?1:0);
       program.set("z",front?-.4f:scenario==2?-.7f:.4f);
       if(front){float x=scenario==1?3:scenario==4?9:scenario==5?5:scenario==6?3:0;
        program.set("offset",x,scenario==5?8f:0f);program.set("size",scenario==0?12f:.6f,scenario==0?16f:2f);
       }else if(scenario==6){program.set("offset",5f,0f);program.set("size",.6f,2f);}else{program.set("offset",0f,0f);program.set("size",32f,32f);}
       GL11.glDrawArrays(GL11.GL_TRIANGLES,front?0:6,6);
      }
      colours[enabled]=BufferUtils.createByteBuffer(viewport*viewport*4);GL11.glReadPixels(0,0,viewport,viewport,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,colours[enabled]);
     }
     require(colours[0].equals(colours[1]),"Interaction query changed colour coverage kind="+kind+" scenario="+scenario);
     state.finishPick();GL11.glFinish();Method collect=NativeObjectState.class.getDeclaredMethod("collect");collect.setAccessible(true);collect.invoke(state);
     Object latest=GameAccess.field("vpiso.NativeObjectState","latest",null);Object picked=GameAccess.field(latest.getClass().getName(),"object",latest);
     List<?> ids=(List<?>)GameAccess.field("vpiso.NativeObjectState","ids",state);
     Object expected=ids.get(scenario==2||scenario==3||scenario==4||scenario==5||scenario==7?2:1);
     require(picked==expected,"Wrong interaction kind="+kind+" viewport="+viewport+" scenario="+scenario+" picked="+picked+" expected="+expected);
     require(GL11.glGetError()==0,"GL error");cases++;
    }finally{state.endRender();IsometricBridge.rendering(null);}
   }
   System.out.println("Pick assist085 GPU PASS: cases="+cases+"; mesh/packed/independent, three render scales, both submission orders, stable faded doors, thin lamps, nearest visible target, wall occlusion, hidden/material holes and radius bounds; pixel-identical colour output.");
  }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
