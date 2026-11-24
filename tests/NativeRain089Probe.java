import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Native per-floor shelter producer -> actual rewritten rain overlay, independent of a live game. */
public final class NativeRain089Probe {
    static int checked, fbo, rain, depth;
    static boolean baseline;
    static Frame frame;
    static FrameContext context;
    static NativeViewData data;
    static GlProgram overlay;
    static final Class<?>[] NONE=new Class<?>[0];
    static Object privateCall(String name,Class<?>[] types,Object...args)throws Exception {
        Method m=NativeViewProbe.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);
    }
    static void fixture()throws Exception {
        // Initialize the real API fixture, then supply squares using its retained map supplier.
        privateCall("nativeSnapshot",NONE);
        Object api=GameAccess.field("vpiso.NativeViewData$BindingsHolder","API",null);
        GameAccess.set(api.getClass().getName(),"square",api,MethodHandles.lookup().findStatic(NativeViewProbe.class,"squareAt",
                MethodType.methodType(Object.class,Object.class,int.class,int.class,int.class)));
        GameAccess.set(api.getClass().getName(),"blacked",api,MethodHandles.lookup().findStatic(NativeViewProbe.class,"isBlack",
                MethodType.methodType(boolean.class,Object.class,Object.class)));
        GameAccess.set(api.getClass().getName(),"fade",api,MethodHandles.lookup().findStatic(NativeViewProbe.class,"fadeOf",
                MethodType.methodType(float.class,Object.class,Object.class)));
        @SuppressWarnings("unchecked") Map<String,Object> squares=(Map<String,Object>)GameAccess.field("NativeViewProbe","SQUARES",null);
        squares.clear();
        for(int z=-1;z<=1;z++)for(int x=0;x<4;x++) {
            Object square=privateCall("square",new Class<?>[]{boolean.class,float.class,int.class,String[].class},true,1f,0,
                    x==0?new String[0]:new String[]{"exterior"});
            GameAccess.set("zombie.iso.IsoGridSquare","haveRoof",square,x==1 && z==0);
            squares.put(x+",0,"+z,square);
        }
        frame=new Frame();frame.camX=1.5f;frame.camY=.5f;frame.camZ=0;
        frame.scene.rain=1f;
        privateCall("mesh",new Class<?>[]{Frame.class,int.class,int.class,int.class,int.class,byte.class},frame,0,0,0,-1,SceneData.VISIBLE);
        privateCall("mesh",new Class<?>[]{Frame.class,int.class,int.class,int.class,int.class,byte.class},frame,1,0,0,1,SceneData.NEAR);
        frame.scene.meshCount=2;
        data=new NativeViewData();data.capture(frame,new Object(),0,new Object());
        context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        Targets targets=new Targets();targets.depth=depth;
        GameAccess.set("viewpoint.render.WorldRenderer","targets",null,targets);
        context.scene=frame.scene;
    }
    public static void main(String[] args)throws Exception {
        baseline=args.length>0 && args[0].equals("baseline088");
        String pack=args.length> (baseline?1:0)?args[baseline?1:0]:"default";
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(16,16,"Native rain089",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            Main.compatible=true;GlProgram.onUse(IsometricShaders::onUse);
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of());
            GL30.glBindVertexArray(GL30.glGenVertexArrays());GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int color=tex(GL30.GL_RGBA32F,GL11.GL_RGBA,4,new float[4]);
            rain=tex(GL30.GL_RGBA32F,GL11.GL_RGBA,4,new float[]{.2f,.3f,.4f,.75f});
            depth=tex(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,1,new float[]{.5f});
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            GL11.glViewport(0,0,1,1);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
            fixture();
            overlay=GlProgram.create("native rain089 actual overlay","screen.vert","rain_over.frag");
            int[] oldTex=new int[2],oldSampler=new int[2];
            for(int i=0;i<2;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+148+i);oldTex[i]=GL11.glGenTextures();oldSampler[i]=GL33.glGenSamplers();
                GL11.glBindTexture(i==0?GL30.GL_TEXTURE_2D_ARRAY:GL11.GL_TEXTURE_2D,oldTex[i]);GL33.glBindSampler(148+i,oldSampler[i]);
            }
            int[] names={GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL12.GL_UNPACK_IMAGE_HEIGHT,
                    GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS,GL12.GL_UNPACK_SKIP_IMAGES};
            int[] values={8,11,13,2,3,1};
            data.begin();
            try {
                // Real indoor, outside-with-roof, exposed floor, upper exposed roof and basement.
                for(float yaw:new float[]{0,37,90,180,270})for(boolean jitter:new boolean[]{false,true}) {
                    sample(0,0,yaw,jitter,true,true,false);sample(1,0,yaw,jitter,true,true,false);
                    sample(2,0,yaw,jitter,true,false,false);sample(1,1,yaw,jitter,true,false,false);
                    sample(0,-1,yaw,jitter,true,true,false);sample(3,1,yaw,jitter,true,false,false);
                    sample(4,0,yaw,jitter,true,false,false); // missing square must retain rain
                    sample(0,0,yaw,jitter,true,false,true); // sky must retain rain
                    sample(0,0,yaw,jitter,false,false,false); // ordinary VP/non-model identity
                }
                // Upload must work even if the caller has an unpack PBO and non-default strides.
                for(int i=0;i<names.length;i++)GL11.glPixelStorei(names[i],values[i]);
                if(!baseline) {Object weather=GameAccess.field("vpiso.NativeViewData","weather",data);GameAccess.set("vpiso.NativeWeather","dirty",weather,true);}
                int pbo=GL15.glGenBuffers();GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER,64L,GL15.GL_DYNAMIC_DRAW);
                sample(0,0,37,true,true,true,false);
                for(int i=0;i<names.length;i++)if(GL11.glGetInteger(names[i])!=values[i])throw new AssertionError("unpack state");
                if(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)!=pbo)throw new AssertionError("PBO state");
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
                for(int name:names)GL11.glPixelStorei(name,name==GL11.GL_UNPACK_ALIGNMENT?4:0);
            } finally {data.end();IsometricBridge.rendering(null);}
            // A queued rainy snapshot must survive capture of the next dry frame.
            frame.scene.rain=0;frame.scene.snow=0;
            NativeViewData dry=new NativeViewData();dry.capture(frame,new Object(),0,new Object());
            dry.begin();try {sample(0,0,0,false,true,false,false);}finally{dry.end();}
            if(!baseline && ((Number)GameAccess.field("vpiso.NativeWeather","texture",GameAccess.field("vpiso.NativeViewData","weather",dry))).intValue()!=0)
                throw new AssertionError("Dry frame allocated/uploaded shelter texture");
            data.begin();try {sample(0,0,37,true,true,true,false);}finally{data.end();}
            frame.scene.snow=1;
            NativeViewData snow=new NativeViewData();snow.capture(frame,new Object(),0,new Object());
            snow.begin();try {sample(1,0,90,true,true,true,false);sample(2,0,90,true,true,false,false);}finally{snow.end();}
            for(int i=0;i<2;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+148+i);
                if(GL11.glGetInteger(i==0?GL30.GL_TEXTURE_BINDING_2D_ARRAY:GL11.GL_TEXTURE_BINDING_2D)!=oldTex[i]
                    ||GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,148+i)!=oldSampler[i])throw new AssertionError("binding restore");
            }
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println((baseline?"Previous088 sheltered rain negative PASS":"Native rain089 GPU PASS")+" pack="+pack+" cases="+checked+
                "; real native squares, same-frame mixed indoor/eaves/outdoor/visible roof/basement, missing/sky identity, yaw/jitter, native draw filtering unaffected and GL bindings restored.");
        } finally {IsometricBridge.rendering(null);if(data!=null)data.end();GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void sample(int x,int z,float yaw,boolean jitter,boolean models,boolean sheltered,boolean sky)throws Exception {
        Vector3f point=new Vector3f(frame.camX-(x+.5f),z*2.4494896f+.08f,0);
        float angle=(float)Math.toRadians(yaw);
        Matrix4f view=new Matrix4f().setLookAt(point.x+6*(float)Math.cos(angle),point.y+8,point.z+6*(float)Math.sin(angle),point.x,point.y,point.z,0,1,0);
        Matrix4f plain=new Matrix4f().setPerspective((float)Math.toRadians(60),1,.1f,100);
        Matrix4f jittered=new Matrix4f(plain);
        if(jitter)jittered.m20(jittered.m20()+.03125f).m21(jittered.m21()-.025f);
        context.viewProjection.set(jittered).mul(view);context.invViewProjection.set(context.viewProjection).invert();
        ((Matrix4f)GameAccess.field("viewpoint.render.FrameContext","invPlainViewProjection",context)).set(plain).mul(view).invert();
        Vector4f clip=context.viewProjection.transform(new Vector4f(point,1));
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,4);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,new float[]{sky?1:clip.z/clip.w*.5f+.5f});
        } finally {GL11.glPopClientAttrib();GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);}
        if(models)IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,false);else IsometricBridge.rendering(null);
        overlay.use();overlay.setInt("uRain",0);overlay.set("uPanini",0,0,0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,rain);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer pixel=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
        float expected=!baseline&&sheltered?0:.75f;
        if(Math.abs(pixel.get(3)-expected)>.001f)throw new AssertionError("Rain alpha x="+x+" z="+z+" yaw="+yaw+" models="+models+" sky="+sky+" expected="+expected+" actual="+pixel.get(3));
        checked++;
    }
    static int tex(int format,int external,int count,float[] values) {
        int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,1,1,0,external,GL11.GL_FLOAT,values);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        return t;
    }
}
