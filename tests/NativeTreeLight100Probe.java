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

/** Compare actual tree fill with VP's actual deferred foliage material, including low ambient. */
public final class NativeTreeLight100Probe {
 static boolean baseline;static int samples,cases,negative;static double maxError;static Object green;static GlProgram referenceLight;
 static final NativeTreeStyle075Probe T=new NativeTreeStyle075Probe();
 public static void main(String[] args)throws Exception {
  baseline=args[0].equals("baseline099");String pack=args[1];
  if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
  long window=GLFW.glfwCreateWindow(T.OW,T.OH,"tree100 actual material",0,0);
  try {
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;GlProgram.onUse(IsometricShaders::onUse);
   GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
   TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.ofEntries(Map.entry("upscaling","off"),Map.entry("antialiasing","off"),Map.entry("renderScale","100"),Map.entry("reflections","off"),Map.entry("volumetrics","false"),Map.entry("bloom","false"),Map.entry("clouds","false"),Map.entry("bounceLight","false"),Map.entry("ambientOcclusion","off"),Map.entry("contactShadows","false"),Map.entry("lampShadows","0")));
   T.fixture();
   green=NativeTreeRender055Probe.texture("green100");
   int greenId=((Number)GameAccess.call("zombie.core.textures.Texture","getID",green,new Class<?>[0])).intValue();
   GL11.glBindTexture(GL11.GL_TEXTURE_2D,greenId);ByteBuffer greenPixels=BufferUtils.createByteBuffer(384*512*4);
   for(int i=0;i<384*512;i++)greenPixels.put((byte)128).put((byte)192).put((byte)64).put((byte)255);greenPixels.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,384,512,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,greenPixels);
   for(float yaw:new float[]{0,37,90})for(float light:new float[]{.02f,.1f,.7f})draw(yaw,light);
   for(float yaw:new float[]{0,37,90})draw(yaw,.02f,true);
   roots();
   if(baseline && negative<3)throw new AssertionError("099 defect not reproduced "+negative);
   if(!baseline && maxError>.006)throw new AssertionError("selected material mismatch "+maxError);
   T.gl("all material cases");
   System.out.println((baseline?"Previous099 tree light negative PASS:":"Tree100 selected material GPU PASS:")+" pack="+pack+" cases="+cases+" samples="+samples+" maxHDRerror="+maxError+" negative="+negative+"; actual native producer/VBO vs actual selected VP SurfacePass.light; night/day, 3 rotations; controlled scene.");
  }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
 }
 static void draw(float yaw,float level)throws Exception {
  draw(yaw,level,false);
 }
 static void draw(float yaw,float level,boolean lamp)throws Exception {
  Frame f=new Frame();f.camX=11;f.camY=13;f.number=++T.number;SceneData s=f.scene;s.originX=-11;s.originZ=-13;s.cameraCut=true;
  s.skyR=level*.8f;s.skyG=level;s.skyB=level*1.2f;s.fogR=.1f;s.fogG=.2f;s.fogB=.3f;s.fogStart=10000;s.fogEnd=20000;s.sunY=s.sunTrueY=s.skySunY=1;s.sunStrength=0;s.skyGlow=1;s.nightLight=level;s.shadowReach=64;Arrays.fill(s.exposure,(byte)255);
  if(lamp){s.lightCount=1;s.lightKeys[0]=123;s.lights[0]=s.lights[16]=0;s.lights[1]=s.lights[17]=2;s.lights[2]=s.lights[18]=0;s.lights[3]=s.lights[19]=6;s.lights[4]=1;s.lights[5]=.5f;s.lights[6]=.1f;s.lights[7]=s.lights[8]=s.lights[9]=1;s.lights[10]=2;}
  Matrix4f view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(11,13,6),11,13,T.W,T.H,s.projection,view,eye,yaw);
  NativeTrees tree=new NativeTrees();if(yaw!=0)tree.rotation=new RotationCamera.Snapshot(s.projection,view,11,13,0,T.W,T.H,1,yaw);
  try {
   Object object=NativeTreeRender055Probe.tree("light100",10,12);tree.add(T.get(object,"square"));tree.capture(T.renderer,0);
   for(Object r:(List<?>)T.get(T.get(tree,"batch"),"trees")) {
    T.set(r,"texture",lamp?green:T.overlapTexture);T.set(r,"texture2",null);T.set(r,"r",.6f);T.set(r,"g",.6f);T.set(r,"b",.6f);T.set(r,"a",1f);T.set(r,"useStencil",false);T.set(r,"transparent",false);
   }
   GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,T.output);GL11.glViewport(T.X,T.Y,T.W,T.H);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glStencilMask(255);GL11.glClearStencil(37);GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
   IsometricBridge.modelWorldRendering(f,new IsometricEffects(false,0,1,64,256,false,1,1,1,1),false);
   require(WorldRenderer.begin(s,f.number,1,view,eye[0],eye[1],eye[2]),"world begin");WorldRenderer.drawTranslucent(s);
   FrameContext c=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
   WorldRenderer.bindTarget();GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glStencilMask(255);GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
   int[] samplers=new int[GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)];for(int i=0;i<samplers.length;i++)samplers[i]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,i);
   tree.renderWorld(f);
   for(int i=0;i<samplers.length;i++)require(GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,i)==samplers[i],"tree sampler leak at unit "+i);
   int hdr=(Integer)T.get(targets,"hdrFbo");float[] actual=T.readColor(hdr,c.width,c.height);float[] depth=T.readDepth(hdr,c.width,c.height);
   fill((Integer)T.get(targets,"albedo"),lamp?128/255f:1,lamp?192/255f:1,lamp?64/255f:1,1);fill((Integer)T.get(targets,"normal"),.5f,1,1,1);fill((Integer)T.get(targets,"engineLight"),s.skyR,s.skyG,s.skyB,1);fill((Integer)T.get(targets,"occlusion"),.5f,1,.5f,1);
   GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,(Integer)T.get(targets,"lightFbo"));GL11.glViewport(0,0,c.width,c.height);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
   Object surface=GameAccess.field("viewpoint.render.WorldRenderer","surfaces",null);
   reference(surface,c,targets,lamp);
   float[] expected=T.readColor((Integer)T.get(targets,"lightFbo"),c.width,c.height);double error=0;int count=0;
   if(lamp) {
    s.lightCount=0;reference(surface,c,targets,true);
    float[] unlit=T.readColor((Integer)T.get(targets,"lightFbo"),c.width,c.height);double change=0;int n=0;
    for(int i=0;i<depth.length;i++)if(depth[i]<.9999f){for(int k=0;k<3;k++)change+=Math.abs(expected[i*4+k]-unlit[i*4+k]);n+=3;}
    if(change/n<=.0005)for(int i=0;i<depth.length;i++)if(depth[i]<.9999f){
     org.joml.Vector4f at=c.invViewProjection.transform(new org.joml.Vector4f((i%c.width+.5f)/c.width*2-1,(i/c.width+.5f)/c.height*2-1,depth[i]*2-1,1));at.div(at.w);
     System.out.println("Tree100 lamp pixel pos="+at+" depth="+depth[i]+" expected="+expected[i*4]+","+expected[i*4+1]+","+expected[i*4+2]+" unlit="+unlit[i*4]+","+unlit[i*4+1]+","+unlit[i*4+2]);break;
    }
    require(n>3000&&change/n>.0005,"vacuous coloured lamp "+change/Math.max(n,1));s.lightCount=1;
    System.out.println("Tree100 actual coloured lamp contribution="+change/n);
   }
   for(int y=2;y<c.height-2;y++)for(int x=2;x<c.width-2;x++) {
    int at=y*c.width+x;if(depth[at]>=.9999f || depth[at-1]>=.9999f || depth[at+1]>=.9999f || depth[at-c.width]>=.9999f || depth[at+c.width]>=.9999f)continue;
    for(int k=0;k<3;k++){error+=Math.abs(actual[at*4+k]-expected[at*4+k]);count++;}
   }
   require(count>3000,"empty material mask "+count);error/=count;maxError=Math.max(maxError,error);if(error>.01)negative++;samples+=count/3;cases++;
   System.out.println("Tree100 yaw="+yaw+" light="+level+" localLamp="+lamp+" HDRerror="+error+" samples="+count/3);
   WorldRenderer.finish(s);T.gl("material case");
  }finally{tree.release();IsometricBridge.rendering(null);}
 }
 static void fill(int texture,float r,float g,float b,float a) {
  GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
  FloatBuffer pixels=BufferUtils.createFloatBuffer(w*h*4);for(int i=0;i<w*h;i++)pixels.put(r).put(g).put(b).put(a);pixels.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
 }
 static void reference(Object surface,FrameContext c,Object targets,boolean lamps)throws Exception {
  // This fixture injects material/depth after begin. Its original opaque light
  // tiles were empty; select VP's real bounded lamp-walk compile-time branch.
  // Its G-buffer reads, material shader and SurfacePass setup stay unchanged.
  Object original=T.get(surface,"light");
  if(lamps) {
   if(referenceLight==null) {
    Object loaded=GameAccess.call("viewpoint.platform.Shaders","load",null,new Class<?>[]{String.class},"light.frag");
    String fragment=(String)GameAccess.field("viewpoint.platform.Shaders$Source","text",loaded);
    fragment=fragment.replace("#define VIEWPOINT_LAMP_LIST","// reference actual bounded lamp walk");
    java.nio.file.Files.writeString(java.nio.file.Path.of("research/tree100/reference-"+GlProgram.pack().pack.id+".frag"),fragment);
    String vertex="#version 330\nout vec2 vUv;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);vUv=p;gl_Position=vec4(p*2.-1.,0,1);}";
    referenceLight=GlProgram.fromSources("actual selected tree100 reference",new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of(),List.of("actual selected VP light, bounded list"));
    referenceLight.sampler("uAlbedo",9);referenceLight.sampler("uNormal",10);referenceLight.sampler("uEngineLight",11);referenceLight.sampler("uDepth",12);
    for(String type:List.of("ShadowPass","Spots","LampShadows"))GameAccess.call("viewpoint.render."+type,type.equals("ShadowPass")?"samplers":"sampler",null,new Class<?>[]{GlProgram.class},referenceLight);
   }
   T.set(surface,"light",referenceLight);
  }
  try {
   GameAccess.call("viewpoint.render.SurfacePass","light",surface,new Class<?>[]{FrameContext.class},c);
   if(lamps)for(String key:List.of("uLightCount","uLightPos[0]","uLightColor[0]","uLightRoom[0]","uLightAim[0]","uLampShadowCount")) {
    int at=GL20.glGetUniformLocation(referenceLight.id(),key);float[] v=new float[4];if(at>=0)GL20.glGetUniformfv(referenceLight.id(),at,v);System.out.println("Tree100 lamp fixture "+key+"="+Arrays.toString(v));
   }
  }
  finally {T.set(surface,"light",original);}
 }
 static void roots()throws Exception {
  Object texture=NativeTreeRender055Probe.texture("roots100");
  GL13.glActiveTexture(GL13.GL_TEXTURE0);int id=((Number)GameAccess.call("zombie.core.textures.Texture","getID",texture,new Class<?>[0])).intValue();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
  ByteBuffer pixels=BufferUtils.createByteBuffer(384*512*4);
  for(int y=0;y<512;y++)for(int x=0;x<384;x++)pixels.put((byte)255).put((byte)0).put((byte)0).put((byte)(y>=480&&y<504&&x>=128&&x<256?255:0));pixels.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,384,512,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
  String vertex="#version 330\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0,1);}";
  String fragment="#version 330\nuniform mat4 inv,projection;uniform vec2 size;uniform float floorY;out vec4 color;void main(){vec2 uv=gl_FragCoord.xy/size;vec4 a=inv*vec4(uv*2.-1.,-1,1),b=inv*vec4(uv*2.-1.,1,1);vec3 start=a.xyz/a.w,ray=b.xyz/b.w-start;vec3 p=start+ray*((floorY-start.y)/ray.y);vec4 clip=projection*vec4(p,1);gl_FragDepth=clip.z/clip.w*.5+.5;color=vec4(.05,.05,.05,1);}";
  int ground=NativeVisibility053Probe.program(vertex,fragment),vao=GL30.glGenVertexArrays(),visible=0,blocked=0;
  for(float yaw:new float[]{0,37,90,180})for(float half:new float[]{2,6,15})for(boolean foreground:new boolean[]{false,true}) {
   Frame f=new Frame();f.camX=11;f.camY=13;f.number=++T.number;SceneData s=f.scene;s.originX=-11;s.originZ=-13;s.cameraCut=true;s.skyR=s.skyG=s.skyB=.7f;s.sunY=s.sunTrueY=s.skySunY=1;s.sunStrength=0;s.nightLight=1;s.fogStart=10000;s.fogEnd=20000;Arrays.fill(s.exposure,(byte)255);
   Matrix4f view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(11,13,half),11,13,T.W,T.H,s.projection,view,eye,yaw);
   NativeTrees trees=new NativeTrees();if(yaw!=0)trees.rotation=new RotationCamera.Snapshot(s.projection,view,11,13,0,T.W,T.H,1,yaw);
   try {
    Object object=NativeTreeRender055Probe.tree("root100",10,12);trees.add(T.get(object,"square"));trees.capture(T.renderer,0);
    for(Object r:(List<?>)T.get(T.get(trees,"batch"),"trees")){T.set(r,"texture",texture);T.set(r,"texture2",null);T.set(r,"a",1f);T.set(r,"r",1f);T.set(r,"g",1f);T.set(r,"b",1f);T.set(r,"useStencil",false);T.set(r,"transparent",false);}
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,T.output);GL11.glViewport(T.X,T.Y,T.W,T.H);IsometricBridge.modelWorldRendering(f,IsometricEffects.DEFAULT,false);require(WorldRenderer.begin(s,f.number,1,view,eye[0],eye[1],eye[2]),"root world");WorldRenderer.drawTranslucent(s);
    FrameContext c=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);WorldRenderer.bindTarget();GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_ALWAYS);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    GL20.glUseProgram(ground);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(ground,"inv"),false,c.invViewProjection.get(new float[16]));GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(ground,"projection"),false,c.viewProjection.get(new float[16]));GL20.glUniform2f(GL20.glGetUniformLocation(ground,"size"),c.width,c.height);GL20.glUniform1f(GL20.glGetUniformLocation(ground,"floorY"),foreground?.8f:0);GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    trees.renderWorld(f);Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);float[] image=T.readColor((Integer)T.get(targets,"hdrFbo"),c.width,c.height);int red=0;
    for(int i=0;i<image.length;i+=4)if(image[i]>.001f&&image[i+1]<image[i]*.1f)red++;
    if(foreground){require(red==0,"roots cross foreground "+red);blocked++;}
    else if(baseline)require(red==0,"099 root missing negative did not reproduce "+red);
    else {require(red>20,"roots still below ground yaw="+yaw+" fit="+half+" red="+red);visible+=red;}
    WorldRenderer.finish(s);T.gl("root depth case");
   }finally{trees.release();IsometricBridge.rendering(null);}
  }
  GL20.glDeleteProgram(ground);GL30.glDeleteVertexArrays(vao);
  System.out.println((baseline?"Previous099 roots negative PASS:":"Tree100 root GPU PASS:")+" visible="+visible+" foregroundCases="+blocked+"; native last32px root art, real world ground/foreground depth, 4 yaws and 3 zoom fits.");
 }
 static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
}
