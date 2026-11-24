import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Same-row dual-texture trees through the actual producer, native VBO and 3D draw-only camera. */
public final class TreeRow095Probe {
 static final NativeTreeStyle075Probe T=new NativeTreeStyle075Probe();
 static Object a,b,c,bark,leaves,empty;static boolean baseline;static int cases,negative;
 static float yaw,zoom=1;static int jitter,only,mapOffset,wrongOwners,overlapSamples,dense;static boolean faded;static boolean[] mask;static float[] lastDepth;
 public static void main(String[] args)throws Exception {
  baseline=args.length>0&&args[0].equals("baseline094");String pack=args[baseline?1:0];
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long window=GLFW.glfwCreateWindow(T.OW,T.OH,"row trees095",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;GlProgram.onUse(IsometricShaders::onUse);
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
   TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off","antialiasing","off","reflections","off","renderScale","100","clouds","false","volumetrics","false","bloom","false"));
   T.fixture();bark=NativeTreeRender055Probe.texture("row095_bark");leaves=NativeTreeRender055Probe.texture("row095_leaf");empty=NativeTreeRender055Probe.texture("invisible");
   a=NativeTreeRender055Probe.tree("row095_a",10,13);b=NativeTreeRender055Probe.tree("row095_b",11,12);c=NativeTreeRender055Probe.tree("row095_c",12,11);
   for(int origin:new int[]{0,10000})for(float angle:new float[]{0,.004f,.006f,-.004f,-.006f,.04f,.05f,90,90.004f,90.006f,90.04f,90.05f,180,270,37})for(boolean fade:new boolean[]{false,true}) {
    mapOffset=origin;
    yaw=angle;faded=fade;
    // At quarter turns choose the native integer row facing the current camera.
    int[][] pair=angle>80&&angle<100||angle==270?new int[][]{{10,11},{11,12},{12,13}}:new int[][]{{10,13},{11,12},{12,11}};
    place(a,pair[0]);place(b,pair[1]);place(c,pair[2]);
    for(float scale:new float[]{.5f,1,2.5f}) {
     zoom=scale;int changed=0,nonempty=0;
     for(int sample=0;sample<4;sample++) {
      jitter=sample;float[] stable=draw(false,false),neighbour=draw(true,false),reversed=draw(true,true);
      int churn=difference(stable,neighbour),order=difference(neighbour,reversed);changed+=churn+order;nonempty+=colored(stable);
      if(!baseline)require(churn==0&&order==0,"same-row ownership changes yaw="+yaw+" alpha="+(fade?.4:1)+" zoom="+zoom+" jitter="+jitter+" neighbour="+churn+" order="+order);
      if(!fade&&angle!=37) {
       only=1;float[] rear=draw(false,false);boolean[] rearMask=mask.clone();float[] rearDepth=lastDepth.clone();only=2;float[] front=draw(false,false);boolean[] frontMask=mask.clone();float[] frontDepth=lastDepth.clone();only=0;
       int wrong=0,common=0;
       for(int i=0;i<rearMask.length;i++)if(rearMask[i]&&frontMask[i]){common++;float[] expected=rearDepth[i]<frontDepth[i]?rear:front;if(Math.abs(stable[i*4]-expected[i*4])>.02||Math.abs(stable[i*4+2]-expected[i*4+2])>.02)wrong++;}
       require(common>20,"empty actual crown intersection");wrongOwners+=wrong;overlapSamples+=common;
       if(!baseline)require(wrong==0,"coplanar crowns fight at common pixels: yaw="+yaw+" zoom="+zoom+" jitter="+jitter+" wrong="+wrong+"/"+common);
      }
      cases+=2;
     }
     require(nonempty>100,"empty crown fixture");if(changed>100)negative++;
     System.out.println("Tree095 map="+mapOffset+" yaw="+yaw+" fade="+faded+" zoom="+zoom+" membership/order changed="+changed+" visible="+nonempty);
    }
   }
   if(!baseline) {
    mapOffset=0;yaw=0;zoom=1;faded=false;dense=129;place(a,new int[]{10,13});place(b,new int[]{11,12});place(c,new int[]{12,11});
    for(int sample=0;sample<4;sample++) {
     jitter=sample;only=0;float[] combined=draw(false,false);only=2;float[] front=draw(false,false);boolean[] frontMask=mask.clone();only=1;draw(false,false);boolean[] rearMask=mask.clone();only=0;
     int wrong=0,common=0;for(int i=0;i<frontMask.length;i++)if(frontMask[i]&&rearMask[i]){common++;if(Math.abs(combined[i*4]-front[i*4])>.02||Math.abs(combined[i*4+2]-front[i*4+2])>.02)wrong++;}
     require(common>100&&wrong==0,"dense129 roots rear leaves cross front bark "+wrong+"/"+common);cases++;
    }
    dense=0;
   }
   require(GL11.glGetError()==0,"GL error");
   if(baseline){require(negative>0&&wrongOwners>100,"094 same-row defect did not reproduce");System.out.println("Previous094 same-row tree negative PASS: unstable conditions="+negative+" cases="+cases+" wrongOwners="+wrongOwners+"/"+overlapSamples);}
   else System.out.println("Tree095 same-row GPU PASS: pack="+pack+" cases="+cases+" wrongOwners="+wrongOwners+"/"+overlapSamples+"; dual texture opaque/faded, unchanged neighbours, input order, camera jitter, rotation, map coordinates and zoom.");
  }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
 static void place(Object tree,int[] xy)throws Exception {Object square=T.get(tree,"square");NativeTreeRender055Probe.set(square,"x",xy[0]+mapOffset);NativeTreeRender055Probe.set(square,"y",xy[1]+mapOffset);}
 static float[] draw(boolean neighbour,boolean reverse)throws Exception {
  float camX=11+mapOffset,camY=12+mapOffset;
  Frame frame=new Frame();frame.camX=camX;frame.camY=camY;frame.number=++T.number;
  SceneData scene=frame.scene;scene.originX=-camX;scene.originZ=-camY;scene.cameraCut=true;scene.fogStart=10000;scene.fogEnd=20000;
  scene.skyR=scene.skyG=scene.skyB=.4f;scene.sunY=scene.sunTrueY=scene.skySunY=1;scene.sunStrength=0;scene.nightLight=1;Arrays.fill(scene.exposure,(byte)255);
  Matrix4f view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(camX+jitter*.013,camY-jitter*.007,6*zoom),camX,camY,T.W,T.H,scene.projection,view,eye,yaw);
  scene.projection.m20(scene.projection.m20()+((jitter&1)==0?.375f:-.375f)*2f/T.W).m21(scene.projection.m21()+((jitter&2)==0?.25f:-.25f)*2f/T.H);
  NativeTrees trees=new NativeTrees();if(yaw!=0)trees.rotation=new RotationCamera.Snapshot(scene.projection,view,camX,camY,0,T.W,T.H,1f,yaw);
  if(neighbour)trees.add(T.get(c,"square"));trees.add(T.get(reverse?b:a,"square"));trees.add(T.get(reverse?a:b,"square"));trees.capture(T.renderer,0);
  List<?> records=(List<?>)T.get(T.get(trees,"batch"),"trees");require(records.size()==(neighbour?3:2),"record producer membership");
  for(Object r:records) {
   float x=((Number)T.get(r,"x")).floatValue()-mapOffset;boolean third=x>12,red=x<12;
   NativeTreeRender055Probe.set(r,"texture",third?empty:bark);NativeTreeRender055Probe.set(r,"texture2",third?empty:leaves);
   NativeTreeRender055Probe.set(r,"r",red?1f:0f);NativeTreeRender055Probe.set(r,"g",0f);NativeTreeRender055Probe.set(r,"b",red?0f:1f);
   NativeTreeRender055Probe.set(r,"a",1f);NativeTreeRender055Probe.set(r,"useStencil",false);NativeTreeRender055Probe.set(r,"transparent",faded);
   NativeTreeRender055Probe.set(r,"cutawayAlpha",faded?.4f:1f);NativeTreeRender055Probe.set(r,"fadeAlpha",faded?.4f:1f);
  }
  T.syntheticRecordColors(trees,records);
  if(dense>0) {
   Object template=records.get(0);for(int k=-63;k<=65;k++)if(k!=0&&k!=1) {
    Object r=NativeSurfaceReceiverProbe.allocate(template.getClass());for(var field:template.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(r,field.get(template));}
    NativeTreeRender055Probe.set(r,"x",11f+k+mapOffset);NativeTreeRender055Probe.set(r,"y",14f-k+mapOffset);NativeTreeRender055Probe.set(r,"texture",empty);NativeTreeRender055Probe.set(r,"texture2",empty);((List<Object>)records).add(r);
   }
  }
  if(only!=0)for(Object r:records)if(Math.round(((Number)T.get(r,"x")).floatValue())-mapOffset!=(only==1?11:12)) {
   NativeTreeRender055Probe.set(r,"texture",empty);NativeTreeRender055Probe.set(r,"texture2",empty);
  }
  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,T.output);GL11.glViewport(T.X,T.Y,T.W,T.H);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glStencilMask(255);GL11.glClearStencil(0);GL11.glClearDepth(1);GL11.glClearColor(0,0,0,1);GL11.glColorMask(true,true,true,true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
  IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,false);
  try {
   require(WorldRenderer.begin(scene,frame.number,1,view,eye[0],eye[1],eye[2]),"world begin");WorldRenderer.drawTranslucent(scene);trees.renderWorld(frame);
   Object target=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);int hdr=((Number)GameAccess.field("viewpoint.render.Targets","hdrFbo",target)).intValue();
   GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,((Number)GameAccess.field("viewpoint.render.Targets","depth",target)).intValue());float[] depth=new float[T.W*T.H];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);lastDepth=depth;mask=new boolean[depth.length];for(int i=0;i<depth.length;i++)mask[i]=depth[i]<.99999f;
   GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,hdr);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);FloatBuffer pixels=BufferUtils.createFloatBuffer(T.W*T.H*4);GL11.glReadPixels(0,0,T.W,T.H,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);float[] result=new float[pixels.capacity()];pixels.get(result);
   WorldRenderer.finish(scene);return result;
  }finally{trees.release();IsometricBridge.rendering(null);}
 }
 static int colored(float[] pixels){int n=0;for(boolean b:mask)if(b)n++;return n;}
 static int difference(float[] a,float[] b){int n=0;for(int i=0;i<a.length;i+=4)if(Math.abs(a[i]-b[i])>.02f||Math.abs(a[i+1]-b[i+1])>.02f||Math.abs(a[i+2]-b[i+2])>.02f)n++;return n;}
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
