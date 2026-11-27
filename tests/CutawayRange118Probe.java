import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.platform.LiveSettings;
import vpiso.*;

/** Real production GLSL/onUse, actual cameras and captured setting lifetime; no game attach. */
public final class CutawayRange118Probe {
    static final int SIDE=256;
    static final String VERT="#version 330\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);}";
    static final String HEADER="""
        #version 330
        uniform float vpIsoNativeOn,capability;
        uniform vec3 vpIsoNativeCam,position;
        uniform mat4 testInverse;
        uniform int testMode;
        uniform float testDepth;
        vec4 vpIsoNativeRule(vec3 p){return vec4(1,0,0,capability/255.0);}
        """;
    static final String BODY="""
        out vec4 color;
        void main(){
          vec3 p=position;vec4 parent=vec4(0,0,0,-1);
          if(testMode!=0){
            vec4 body=vpIsoRotatedPV*vec4(0,1.1,0,1);
            vec2 pixel=(gl_FragCoord.xy/256.0-.5)*vec2(720,960);
            vec4 scene=testInverse*vec4(body.xy/body.w+pixel*2.0/vpIsoRotatedScreen,body.z/body.w+testDepth,1);
            p=scene.xyz/scene.w;parent=vec4(0,0,0,2);
          }
          vpIsoCutWall(p,vec3(0,p.y,0),vec3(1,0,0),0,parent);color=vec4(1);
        }
        """;
    static Field settingField,valueField;
    static LiveSettings.Number setting;
    static int checks,cases;
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        settingField=IsometricControls.class.getDeclaredField("cutawayRange");settingField.setAccessible(true);Object saved=settingField.get(null);
        Constructor<LiveSettings.Number> ctor=LiveSettings.Number.class.getDeclaredConstructor(String.class,String.class,String.class,float.class,float.class,float.class,float.class);ctor.setAccessible(true);
        setting=ctor.newInstance("vpiso.test118","test118","test",50f,200f,10f,100f);valueField=LiveSettings.Number.class.getDeclaredField("value");valueField.setAccessible(true);
        settingField.set(null,null);require(scale()==1f,"unregistered default must be 100%");settingField.set(null,setting);
        for(float[] pair:new float[][]{{50,.5f},{100,1},{200,2},{0,.5f},{999,2},{Float.NaN,1}}){set(pair[0]);require(scale()==pair[1],"accessor bound/fallback "+pair[0]);}
        set(50);RotationCamera.Snapshot frozen=camera(37,1);set(200);require(captured(frozen)==.5f&&captured(camera(37,1))==2f,"queued setting snapshot changed");
        String baseline=args.length==0?legacySameInput():baseline(Path.of(args[0]));
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(SIDE,SIDE,"118 cutaway range",0,0);
        try {
            require(window!=0,"hidden context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,SIDE,SIDE,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");GL11.glViewport(0,0,SIDE,SIDE);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(0,0,0,0);
            GlProgram candidate=program(NativeWallCutaway.GLSL),old=program(baseline);
            for(float yaw:new float[]{0,37,90,180,270})for(float zoom:new float[]{.5f,1,2}) {
                byte[][] images=new byte[3][];int[] cuts=new int[3];int index=0;
                for(float percent:new float[]{50,100,200}){
                    set(percent);RotationCamera.Snapshot camera=camera(yaw,zoom);images[index]=draw(candidate,camera,1,-.001f,true,224,0);cuts[index]=cut(images[index]);
                    require(cuts[index]>0&&cuts[index]<SIDE*SIDE,"ellipse missing/whole frame yaw="+yaw+" scale="+percent);
                    require(cut(draw(candidate,camera,1,.001f,true,224,0))==0,"background cut yaw="+yaw);
                    require(cut(draw(candidate,camera,1,-.001f,false,224,0))==0,"nativeOn=false cut yaw="+yaw);
                    require(cut(draw(candidate,camera,1,-.001f,true,128,0))==0,"incapable wall cut yaw="+yaw);
                    if(percent==100)require(Arrays.equals(images[index],draw(old,camera,1,-.001f,true,224,0)),"100% changed legacy pixels yaw="+yaw+" zoom="+zoom);
                    index++;cases++;
                }
                require(cuts[0]<cuts[1]&&cuts[1]<cuts[2],"coverage not strictly monotonic yaw="+yaw);
                for(int p=0;p<SIDE*SIDE;p++){require(images[0][p*4]!=0||images[1][p*4]==0,"50% not subset of100%");require(images[1][p*4]!=0||images[2][p*4]==0,"100% not subset of200%");}
                // Existing ordinary physical-wall path is unchanged, in addition to attached-parent ellipse coverage above.
                set(100);RotationCamera.Snapshot camera=camera(yaw,zoom);float[] eye=new float[3];Matrix4f projection=new Matrix4f(),view=new Matrix4f();
                IsoCameraMath.matrices(new IsoCameraMath.Fit(.5,.5,8),.5f,.5f,1920,1080,projection,view,eye,yaw);float side=Math.signum(eye[0]);
                require(cut(draw(candidate,camera,0,0,true,224,side*.5f))==SIDE*SIDE,"ordinary foreground wall visible yaw="+yaw);
                require(cut(draw(candidate,camera,0,0,true,224,-side*.5f))==0,"ordinary background cut yaw="+yaw);
                require(cut(draw(candidate,camera,0,0,true,224,side*20.5f))==0,"ordinary outside wall cut yaw="+yaw);
            }
            // Main-thread setting moves after queuing: uniform must still come from the frozen snapshot.
            set(200);draw(candidate,frozen,1,-.001f,true,224,0);require(GL20.glGetUniformf(candidate.id(),GL20.glGetUniformLocation(candidate.id(),"vpIsoCutawayScale"))==.5f,"onUse read mutable controls instead of queued snapshot");
            require(GL11.glGetError()==0,"GL error");candidate.delete();old.delete();
            System.out.println("CutawayRange118 GPU PASS cases="+cases+" checks="+checks+"; 50/100/200% strict ellipse subsets; exact 100% legacy pixels; 0/37/90/180/270 yaw, .5/1/2 zoom; foreground/background/outside/parent/capability/native-off; frozen snapshot setting and uniform; baseline="+(args.length==0?"same-input117-formula":args[0]));
        }finally{settingField.set(null,saved);RotationLayers.end(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static byte[] draw(GlProgram p,RotationCamera.Snapshot camera,int mode,float depth,boolean on,float caps,float position){
        RotationCamera.Snapshot previous=RotationLayers.begin(camera);
        try {p.use();NativeWallCutaway.onUse(p);p.set("vpIsoNativeOn",on?1:0);p.set("vpIsoNativeCam",.5f,.5f,0f);p.set("capability",caps);p.setInt("testMode",mode);p.set("testDepth",depth);p.set("position",position,1.1f,0f);p.set("testInverse",camera.inverse());
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);ByteBuffer b=BufferUtils.createByteBuffer(SIDE*SIDE*4);GL11.glReadPixels(0,0,SIDE,SIDE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[] result=new byte[b.capacity()];b.get(result);return result;
        }finally{RotationLayers.end(previous);}
    }
    static int cut(byte[] pixels){int n=0;for(int i=0;i<pixels.length;i+=4)if(pixels[i]==0)n++;return n;}
    static RotationCamera.Snapshot camera(float yaw,float zoom){Matrix4f p=new Matrix4f(),v=new Matrix4f();IsoCameraMath.matrices(new IsoCameraMath.Fit(.5,.5,8),.5f,.5f,1920,1080,p,v,new float[3],yaw);return new RotationCamera.Snapshot(p,v,.5f,.5f,0,1920,1080,zoom,yaw);}
    static float scale()throws Exception{return ((Number)IsometricControls.class.getMethod("cutawayScale").invoke(null)).floatValue();}
    static float captured(RotationCamera.Snapshot snapshot)throws Exception{return ((Number)snapshot.getClass().getMethod("cutawayScale").invoke(snapshot)).floatValue();}
    static void set(float percent)throws Exception{valueField.setFloat(setting,percent);}
    static GlProgram program(String glsl){return GlProgram.fromSources("118 range regression",new GlProgram.Sources(VERT,null,null,null,HEADER+glsl+BODY),Map.of(),Map.of(),List.of("actual production helper"));}
    static String legacySameInput(){require(NativeWallCutaway.GLSL.contains(" / vpIsoCutawayScale"),"missing production scale expression");return NativeWallCutaway.GLSL.replace(" / vpIsoCutawayScale","");}
    static String baseline(Path artifact)throws Exception {
        try(URLClassLoader loader=new URLClassLoader(new URL[]{artifact.toUri().toURL()},CutawayRange118Probe.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(!name.equals("vpiso.NativeWallCutaway"))return super.loadClass(name,resolve);Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;}
        }){String source=(String)loader.loadClass("vpiso.NativeWallCutaway").getField("GLSL").get(null);require(!source.contains("vpIsoCutawayScale"),"baseline already contains118 change");return source;}
    }
}
