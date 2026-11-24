import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import viewpoint.core.Frame;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import vpiso.*;

/** Real woven TemporalPass.begin/remember, including passthrough and history ownership. */
public final class RotationTemporalProbe {
    private static Object temporal;
    private static Frame frame;
    private static FrameContext context;
    private static RotationCamera.Snapshot camera;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(64,64,"rotation temporal scope",0,0);
        Object clock=GameAccess.field("viewpoint.render.WorldRenderer","clock",null);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.set("viewpoint.render.WorldRenderer","clock",null,(java.util.function.LongSupplier)()->1_000_000_000L);
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            for(String aa:new String[]{"taa","smaaT2x","off"}) {
                ShaderPacks.useBuiltIn("default","high",Map.of("upscaling","off","antialiasing",aa));
                var targets=Class.forName("viewpoint.render.Targets").getConstructor().newInstance();
                var ctor=Class.forName("viewpoint.render.TemporalPass").getDeclaredConstructor(targets.getClass());ctor.setAccessible(true);temporal=ctor.newInstance(targets);
                frame=new Frame();context=new FrameContext();context.scene=frame.scene;context.pipeline=ShaderPacks.active().pipeline;
                ((FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null)).pipeline=context.pipeline;
                context.width=context.height=context.outputWidth=context.outputHeight=192;
                Matrix4f v=new Matrix4f();float[] eye=new float[3];
                IsoCameraMath.matrices(new IsoCameraMath.Fit(1.5,1.5,3),1.5f,1.5f,192,192,frame.scene.projection,v,eye,90f);
                GameAccess.call("viewpoint.render.FrameContext","look",context,new Class<?>[]{Matrix4f.class,float.class,float.class,float.class},v,eye[0],eye[1],eye[2]);
                camera=new RotationCamera.Snapshot(frame.scene.projection,v,1.5f,1.5f,0,192,192,1f,90f);
                boolean temporalAA=context.pipeline.taa||context.pipeline.smaaTemporal;
                if(aa.equals("taa")&&!context.pipeline.taa)throw new AssertionError("Missing TAA fixture");
                if(aa.equals("smaaT2x")&&!context.pipeline.smaaTemporal)throw new AssertionError("Missing paired SMAA fixture");
                for(int kind=0;kind<7;kind++) {
                    IsometricBridge.rendering(null);RotationLayers.end(null);Main.compatible=kind!=4;
                    if(kind==1||kind==2||kind==3||kind==4||kind==6)IsometricBridge.modelWorldRendering(kind==3?new Frame():frame,IsometricEffects.DEFAULT,true);
                    if(kind==5)IsometricBridge.rendering(frame);
                    if(kind>=2)RotationLayers.begin(camera);
                    boolean owned=kind==2||kind==6;
                    begin();
                    boolean zero=jitter("jitterX")==0 && jitter("jitterY")==0;
                    if(zero!=(owned||!temporalAA))throw new AssertionError("Temporal scope aa="+aa+" kind="+kind+" zero="+zero);
                    if(owned) {
                        same(context.jitteredProjection,frame.scene.projection,"projection");
                        same(context.viewProjection,matrix("plainViewProjection"),"plain current");
                        same(context.invViewProjection,matrix("invPlainViewProjection"),"plain inverse");
                    }
                }
                Main.compatible=true;IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);RotationLayers.begin(camera);
                begin();Matrix4f previous=new Matrix4f(matrix("plainViewProjection"));
                GameAccess.call("viewpoint.render.TemporalPass","remember",temporal,new Class<?>[]{FrameContext.class},context);
                GameAccess.set("viewpoint.render.TemporalPass","historyValid",temporal,true);
                frame.scene.originX+=.25;frame.scene.originY+=.1;frame.scene.originZ-=.125;
                frame.scene.projection.m00(frame.scene.projection.m00()*1.02f);
                begin();
                if(!(Boolean)GameAccess.field("viewpoint.render.FrameContext","previousValid",context)
                        || !(Boolean)GameAccess.field("viewpoint.render.TemporalPass","historyValid",temporal))throw new AssertionError("History invalidated every frame");
                same(matrix("previousViewProjection"),previous,"previous VP");
                same(matrix("reprojection"),new Matrix4f(previous).translate(.25f,.1f,-.125f),"origin-compensated reprojection");
                same(context.viewProjection,new Matrix4f(frame.scene.projection).mul(v),"moving current VP");
                GameAccess.set("viewpoint.render.FrameContext","noiseFrame",context,37);
                begin();
                if(((Number)GameAccess.field("viewpoint.render.FrameContext","noiseFrame",context)).intValue()!=(temporalAA?38:37))throw new AssertionError("Original noise advance changed");
                frame.scene.cameraCut=true;begin();
                if((Boolean)GameAccess.field("viewpoint.render.FrameContext","previousValid",context)
                        || (Boolean)GameAccess.field("viewpoint.render.TemporalPass","historyValid",temporal)
                        || ((Number)GameAccess.field("viewpoint.render.FrameContext","noiseFrame",context)).intValue()!=(temporalAA?0:-1))throw new AssertionError("Original camera-cut lifecycle skipped");
                IsometricBridge.rendering(null);RotationLayers.end(null);frame.scene.cameraCut=false;begin();
                if(temporalAA&&jitter("jitterX")==0&&jitter("jitterY")==0)throw new AssertionError("Rotation scope leaked into VP");
                System.out.println("Rotation temporal scope PASS ["+aa+"]: actual woven begin/remember; owned camera matrices+resolve jitter aligned; ordinary/unrotated/foreign/native/unsupported scopes pass through; history, origin motion, noise and camera-cut lifecycle retained.");
            }
        } finally {GameAccess.set("viewpoint.render.WorldRenderer","clock",null,clock);Main.compatible=true;IsometricBridge.rendering(null);RotationLayers.end(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static void begin() {
        GameAccess.set("viewpoint.render.TemporalPass","lastFrameNanos",temporal,1_000_000_000L);
        GameAccess.call("viewpoint.render.TemporalPass","begin",temporal,new Class<?>[]{FrameContext.class},context);
    }
    private static float jitter(String name){return ((Number)GameAccess.field("viewpoint.render.FrameContext",name,context)).floatValue();}
    private static Matrix4f matrix(String name){return (Matrix4f)GameAccess.field("viewpoint.render.FrameContext",name,context);}
    private static void same(Matrix4f a,Matrix4f b,String message){float[] x=a.get(new float[16]),y=b.get(new float[16]);for(int i=0;i<16;i++)if(Math.abs(x[i]-y[i])>1e-5f)throw new AssertionError(message+" changed");}
}
