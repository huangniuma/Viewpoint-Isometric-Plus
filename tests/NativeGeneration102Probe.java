package viewpoint.render;
import java.util.*;
import java.lang.reflect.*;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.models.ModelMotion;
import viewpoint.platform.*;
import viewpoint.upscale.*;
import vpiso.*;
import static viewpoint.render.FrameGen097Probe.*;

/** Actual native producer: a followed animated object must not inherit background camera motion. */
public final class NativeGeneration102Probe {
 public static void main(String[] args)throws Exception {
  boolean baseline=args.length>0&&args[0].equals("baseline101");
  check(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long win=GLFW.glfwCreateWindow(W,H,"native object FG102",0,0);
  try {
   GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());Main.compatible=true;
   GameAccess.set("viewpoint.platform.BuildPin","supported",null,Boolean.TRUE);IsometricControls.tick();((LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null)).set(true);Graphics.FRAME_GENERATION.set(false);
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);ShaderPacks.useBuiltIn("default","high",Map.of("upscaling","off","reflections","off"));
   context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);targets=new Targets();GameAccess.set("viewpoint.render.WorldRenderer","targets",null,targets);pass=new FrameGenPass(new VendorInputs(targets));FrameGenPass.fixedScreen=new int[]{W,H};
   output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);screenColor=texture(GL11.GL_RGBA8,GL11.GL_RGBA,W,H);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,screenColor,0);int nativeDepth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,W,H);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,nativeDepth,0);setup();
   Class<?> fixture=Class.forName("NativeSurfaceReceiverProbe");Method add=fixture.getDeclaredMethod("addModel",SceneData.class,float[][].class,boolean.class,boolean.class);add.setAccessible(true);float[][] plane={{-1,0,-1},{1,0,-1},{1,0,1},{-1,0,-1},{1,0,1},{-1,0,1}};add.invoke(null,frame.scene,plane,true,false);
   Object mesh=java.lang.reflect.Array.get(GameAccess.field("viewpoint.render.ModelDraws","meshes",frame.scene.models),0),tex=java.lang.reflect.Array.get(GameAccess.field("viewpoint.render.ModelDraws","textures",frame.scene.models),0);
   Object self=new Object();Method follow=ModelMotion.class.getDeclaredMethod("follow",ModelDraws.class,int.class,Object.class,int.class);follow.setAccessible(true);
   Object drawer=((Object[])GameAccess.field("vpiso.IsometricFrameGeneration","DRAWERS",null))[0];GameAccess.set(drawer.getClass().getName(),"frame",drawer,frame);NativeReceiver rec=(NativeReceiver)GameAccess.field(drawer.getClass().getName(),"receiver",drawer);rec.fit(new double[][]{{.5,.5,.5},{.375,.5625,.5005},{.625,.5625,.5005},{.5,.25,.499}});
   Matrix4f projection=NativeSurfaces.projection(rec);float expected=0;int wrong=0,tested=0;
   for(int n=0;n<8;n++) {
    Frame f=new Frame();GameAccess.set(drawer.getClass().getName(),"frame",drawer,f);
    f.number=5000+n;f.camX=n*.1f;f.scene.models.reset();f.scene.originX=-f.camX;f.scene.originY=f.scene.originZ=0;
    ModelDraws d=f.scene.models;ModelMotion.begin(d);int palette=d.palette(new float[]{1,0,0,n*.05f,0,1,0,0,0,0,1,0},1);int index=d.add((zombie.core.skinnedmodel.model.ModelMesh)mesh,null,(zombie.core.textures.Texture)tex,new Matrix4f(),palette,palette,GL11.GL_NONE,ModelDraws.COLOUR|ModelDraws.SHADOW);d.bounds(index,0,0,0,3);d.material(index,1,1,1,1);d.light(index,1,1,1,1);follow.invoke(null,d,index,self,1);ModelMotion.end(d);
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,H);GL11.glDepthMask(true);GL11.glClearDepth(.5);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);IsometricBridge.rendering(null);
    ((zombie.core.textures.TextureDraw.GenericDrawer)drawer).render();check(!(Boolean)GameAccess.field("vpiso.IsometricFrameGeneration","failed",null),"Native producer failed");Object selected=GameAccess.field("vpiso.IsometricFrameGeneration","nativePass",null);FrameGeneration generation=(FrameGeneration)GameAccess.field("viewpoint.render.FrameGenPass","generation",selected);
    float[] motion=readTexture(generation.motion(),W*H*2,GL30.GL_RG),depth=readTexture(generation.depth(),W*H,GL11.GL_RED);
    Vector4f a=projection.transform(new Vector4f(n*.05f,0,0,1)),b=projection.transform(new Vector4f((n-1)*.05f,0,0,1));expected=(b.x/b.w-a.x/a.w)*.5f;int x=(int)((a.x/a.w*.5f+.5f)*W),y=(int)((a.y/a.w*.5f+.5f)*H),at=y*W+x;
    if(n>=3){tested++;if(java.lang.Math.abs(motion[at*2]-expected)>.0001)wrong++;System.out.println("NativeGeneration102 n="+n+" actual="+motion[at*2]+" objectExpected="+expected+" depth="+depth[at]);}
   }
   if(baseline){check(wrong==tested&&tested>0,"101 did not reproduce camera/object mismatch");System.out.println("Previous101 native object motion negative PASS: wrong="+wrong+"/"+tested);}
   else {check(wrong==0&&tested>0,"Object motion mismatch");check(GL11.glGetError()==0,"GL error");System.out.println("NativeGeneration102 GPU PASS: actual native producer, followed skinned object and independent background vectors; cases="+tested);}
  }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
 }
}

