import java.util.*;
import java.lang.reflect.*;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.models.ModelMotion;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Actual model upload/G-buffer, moving bone and scene origin with a following camera. */
public final class NativeMotion102Probe {
 public static void main(String[] args)throws Exception {
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long window=GLFW.glfwCreateWindow(256,256,"skinned102 motion",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
   TestShaderPacks.select(System.getProperty("motion102.pack","default"),System.getProperty("motion102.pack","default").equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off","antialiasing","off","volumetrics","false"));
   GlProgram.onUse(IsometricShaders::onUse);
   int out=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,out);int tex=NativeGroundProbe.texture(256,256,null);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,tex,0);
   Frame first=NativeSurfaceReceiverProbe.frame();NativeSurfaceReceiverProbe.addModel(first.scene,NativeSurfaceReceiverProbe.horizontal(0,1),true,false);
   Object mesh=GameAccess.field("viewpoint.render.ModelDraws","meshes",first.scene.models);
   Object texture=GameAccess.field("viewpoint.render.ModelDraws","textures",first.scene.models);
   Object owner=new Object();Method follow=ModelMotion.class.getDeclaredMethod("follow",ModelDraws.class,int.class,Object.class,int.class);follow.setAccessible(true);
   long[] clock={1_000_000_000L};GameAccess.set("viewpoint.render.WorldRenderer","clock",null,(java.util.function.LongSupplier)()->clock[0]);
   NativeReceiver receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.5,.5},{.375,.5625,.5005},{.625,.5625,.5005},{.5,.25,.499}});
   Matrix4f old=new Matrix4f();int input=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,input);
   int motion=NativeGroundProbe.texture(256,256,null);GL11.glBindTexture(GL11.GL_TEXTURE_2D,motion);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,256,256,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT1,GL11.GL_TEXTURE_2D,motion,0);
   int fgdepth=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,fgdepth);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R32F,256,256,0,GL11.GL_RED,GL11.GL_FLOAT,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,fgdepth,0);
   int raw=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,raw);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R32F,256,256,0,GL11.GL_RED,GL11.GL_FLOAT,new float[256*256]);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
   Method render=NativeFrameMotion.class.getDeclaredMethod("draw",Frame.class,NativeReceiver.class,int.class,int.class,int.class,int.class,Matrix4f.class,Matrix4f.class,boolean.class);render.setAccessible(true);
   for(int n=0;n<8;n++) {
    Frame f=NativeSurfaceReceiverProbe.frame();f.number=2000+n;f.camX=1000+n*.1f;f.camY=2000;f.scene.originX=-f.camX;f.scene.originZ=-f.camY;f.scene.cameraCut=n==0;f.scene.fogStart=10000;f.scene.fogEnd=20000;
    ModelDraws d=f.scene.models;ModelMotion.begin(d);int palette=d.palette(new float[]{1,0,0,n*.05f,0,1,0,0,0,0,1,0},1);
    int slot=d.add((zombie.core.skinnedmodel.model.ModelMesh)java.lang.reflect.Array.get(mesh,0),null,(zombie.core.textures.Texture)java.lang.reflect.Array.get(texture,0),new Matrix4f(),palette,palette,GL11.GL_NONE,ModelDraws.COLOUR|ModelDraws.SHADOW);
    d.bounds(slot,0,0,0,3);d.material(slot,1,1,1,1);d.light(slot,1,1,1,1);follow.invoke(null,d,slot,owner,1);ModelMotion.end(d);
    Matrix4f view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(f.camX,f.camY,4),f.camX,f.camY,256,256,f.scene.projection,view,eye);
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,out);GL11.glViewport(0,0,256,256);IsometricBridge.modelWorldRendering(f,IsometricEffects.DEFAULT,true);clock[0]+=33_333_333L;
    if(!WorldRenderer.begin(f.scene,f.number,1,view,eye[0],eye[1],eye[2]))throw new AssertionError("begin");
    Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);int velocity=((Number)GameAccess.field("viewpoint.render.Targets","velocity",targets)).intValue();GL11.glBindTexture(GL11.GL_TEXTURE_2D,velocity);float[] v=new float[256*256*4];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,v);double x=0,y=0;int count=0;for(int i=0;i<v.length;i+=4)if(v[i+3]>.5){x+=v[i];y+=v[i+1];count++;}
    FrameContext c=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);Matrix4f previous=(Matrix4f)GameAccess.field("viewpoint.render.FrameContext","previousViewProjection",c);Matrix4f current=(Matrix4f)GameAccess.field("viewpoint.render.FrameContext","plainViewProjection",c);
    Vector4f a=current.transform(new Vector4f(n*.05f,0,0,1)),b=previous.transform(new Vector4f((n-1)*.05f,0,0,1));
    System.out.println("SKIN102 n="+n+" valid="+count+" mean="+x/count+","+y/count+" expected="+(a.x/a.w-b.x/b.w)*.5f+","+(a.y/a.w-b.y/b.w)*.5f);
    WorldRenderer.drawTranslucent(f.scene);WorldRenderer.finish(f.scene);
    Matrix4f projection=NativeSurfaces.projection(receiver);float[] nativeD=new float[256*256];
    // Reconstruct the horizontal plane used by the real skinned fixture, retaining its posed translation.
    Matrix4f inv=new Matrix4f(projection).invert();for(int py=0;py<256;py++)for(int px=0;px<256;px++){Vector4f ray=inv.transform(new Vector4f((px+.5f)/128-1,(py+.5f)/128-1,0,1));Vector4f end=inv.transform(new Vector4f((px+.5f)/128-1,(py+.5f)/128-1,1,1));float t=(0-ray.y)/(end.y-ray.y);Vector4f point=projection.transform(new Vector4f(ray.x+t*(end.x-ray.x),0,ray.z+t*(end.z-ray.z),1));nativeD[py*256+px]=point.z/point.w*.5f+.5f;}
    GL11.glBindTexture(GL11.GL_TEXTURE_2D,raw);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,256,256,GL11.GL_RED,GL11.GL_FLOAT,nativeD);
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,input);GL20.glDrawBuffers(new int[]{0,GL30.GL_COLOR_ATTACHMENT1});GL11.glClearColor(.2f,.2f,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    render.invoke(null,f,receiver,raw,input,256,256,projection,old,n==0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D,motion);float[] data=new float[256*256*4];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);double mx=0,my=0;int valid=0;for(int i=0;i<data.length;i+=4)if(java.lang.Math.abs(data[i]-.2f)>.01){mx+=data[i];my+=data[i+1];valid++;}
    Vector4f ca=projection.transform(new Vector4f(n*.05f,0,0,1)),pa=old.transform(new Vector4f((n-1)*.05f,0,0,1));float ex=(pa.x/pa.w-ca.x/ca.w)*.5f,ey=(pa.y/pa.w-ca.y/ca.w)*.5f;
    System.out.println("NATIVE102 n="+n+" count="+valid+" motion="+mx/valid+","+my/valid+" expected="+ex+","+ey);
    if(n>0&&valid<100)throw new AssertionError("No native model motion coverage");if(n>0&&(java.lang.Math.abs(mx/valid-ex)>1e-4||java.lang.Math.abs(my/valid-ey)>1e-4))throw new AssertionError("Wrong native skin motion");
    if(n==7) {
     // Hidden native pixels must not acquire an invisible actor's motion/depth.
     GL11.glBindTexture(GL11.GL_TEXTURE_2D,raw);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,256,256,GL11.GL_RED,GL11.GL_FLOAT,new float[256*256]);
     GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,input);GL20.glDrawBuffers(new int[]{0,GL30.GL_COLOR_ATTACHMENT1});GL11.glClearColor(.2f,.2f,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
     render.invoke(null,f,receiver,raw,input,256,256,projection,old,false);GL11.glBindTexture(GL11.GL_TEXTURE_2D,motion);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
     for(int i=0;i<data.length;i+=4)if(java.lang.Math.abs(data[i]-.2f)>.0001)throw new AssertionError("Motion leaked into hidden native pixels");
    }
    old.set(projection);
   }
   if(GL11.glGetError()!=0)throw new AssertionError("Native motion GL error");
   System.out.println("NativeMotion102 GPU PASS: pack="+System.getProperty("motion102.pack","default")+" skin/bone history, independent shadow preparation, edge coverage, hidden pixels");
  }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
}


