import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Controlled full renderer discriminator: actual gbuffer, depth reconstruction and light pass. */
public final class ObjectPick085Probe {
    static final boolean BASELINE=Boolean.getBoolean("pick085.baseline");
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(256,256,"full model pipeline discriminator",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.select(args.length > 0 ? args[0] : "library",args.length > 0 && args[0].equals("somber") ? "quality" : "high",Map.of("upscaling","off","reflections","off"));
            String lightSource=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,
                new Class<?>[]{String.class,GameAccess.type("viewpoint.platform.ShaderPack"),boolean[].class},"light.frag",ShaderPacks.active().pack,new boolean[1]);
            System.out.println("Actual library light has contact="+lightSource.contains("contactShadowAt(")+" rewritten distance="+lightSource.contains("contactShadowAt(ivec2(gl_FragCoord.xy), length(pos - uEye))"));
            int color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,256,256,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
            int output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            Frame frame=NativeSurfaceReceiverProbe.frame();frame.camX=128;frame.camY=128;
            Object floor=NativeSurfaceReceiverProbe.mesh(frame.scene,0,ModelShadowMotionProbe.tiledFloor());
            float[][] points=ModelShadowMotionProbe.tiledFloor();float[] verts=new float[points.length*14];
            for(int i=0;i<points.length;i++){System.arraycopy(points[i],0,verts,i*14,3);verts[i*14+7]=verts[i*14+8]=1;verts[i*14+10]=1;}
            var pages=((ChunkMeshData)floor).pages;
            NativeSurfaceReceiverProbe.retire(floor);
            floor=new ChunkMeshData(pages,new int[]{0},new int[]{points.length},verts,0);frame.scene.meshes[0]=(ChunkMeshData)floor;
            frame.scene.meshFlags[0]|=1;frame.scene.meshModelSquares[0]=-1L;frame.scene.meshPlan[0]=-1;
            frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
            frame.scene.sunR=frame.scene.sunG=frame.scene.sunB=1;
            float[] h=new float[96*96];for(int z=0;z<96;z++)for(int x=0;x<96;x++)h[z*96+x]=2+.002f*((x-32)*(x-32)+(z-32)*(z-32));
            FarRelief.Field grid=FarRelief.Field.of(96,96,h,h);
            Matrix4f view=new Matrix4f();float[] eye=new float[3];
            WorldRenderer.debugView=6;
            int total=0,dark=0;
            frame.scene.meshes[0].objects(new int[]{0,0,points.length},new int[]{0,1},new int[0]);
            NativeObjectState objectState=new NativeObjectState();
            zombie.iso.IsoObject object=new zombie.iso.IsoObject();
            Object square=Interaction084Probe.UNSAFE.allocateInstance(zombie.iso.IsoGridSquare.class);
            GameAccess.set("zombie.iso.IsoObject","square",object,square);
            Class<?> sampleClass=Class.forName("vpiso.NativeObjectState$Sample");
            var ctor=sampleClass.getDeclaredConstructor(float.class,int.class);ctor.setAccessible(true);
            Object sample=ctor.newInstance(.08f,1);
            GameAccess.set("vpiso.IsometricBridge","modelsEnabled",null,true);GameAccess.set("vpiso.IsometricBridge","submitted",null,true);
            for(int step=0;step<20;step++) {
                float dx=step*.2f,dz=step*.1f;frame.camX=128+dx;frame.camY=128+dz;
                frame.scene.originX=-frame.camX;frame.scene.originZ=-frame.camY;
                frame.scene.meshOffsets[0]=dx;frame.scene.meshOffsets[2]=dz;
                frame.scene.relief(new FarRelief.Lift(grid,.5f,grid.metresAt(frame.camX,frame.camY),frame.camX,frame.camY));
                frame.number++;frame.scene.cameraCut=step==0;
                IsometricBridge.modelWorldRendering(frame,ModelShadowMotionProbe.FX,true);
                IsoCameraMath.matrices(new IsoCameraMath.Fit(128,128,new float[]{4f,8f,16f,24f,40f}[step%5]),frame.camX,frame.camY,256,256,frame.scene.projection,view,eye,new float[]{0f,45f,137f,270f}[step/5]);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,256,256);GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glClearColor(0,0,0,0);GL11.glColorMask(true,true,true,true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                MousePick.ask=null;objectState.beginCapture(frame);NativeObjectState.endCapture();
                var projected=new org.joml.Vector4f(dx+.4f,0f,dz+.2f,1f).mul(view).mul(frame.scene.projection);
                int pickX=(int)((projected.x/projected.w*.5f+.5f)*256),pickY=255-(int)((projected.y/projected.w*.5f+.5f)*256);
                GameAccess.set("vpiso.NativeObjectState","ask",objectState,new MousePick.Ask(pickX,pickY,256,256));
                ((java.util.List)GameAccess.field("vpiso.NativeObjectState","ids",objectState)).add(object);
                ((java.util.Map)GameAccess.field("vpiso.NativeObjectState","meshes",objectState)).put(0,Map.of(0,sample));
                if(!BASELINE)((BitSet)GameAccess.field("vpiso.NativeObjectState","assisted",objectState)).set(1);
                objectState.beginRender();
                if(!WorldRenderer.begin(frame.scene,frame.number,1f,view,eye[0],eye[1],eye[2]))throw new AssertionError("begin");
                WorldRenderer.drawTranslucent(frame.scene);objectState.finishPick();GL11.glFinish();
                var collect=NativeObjectState.class.getDeclaredMethod("collect");collect.setAccessible(true);collect.invoke(objectState);
                Object selected=GameAccess.field("vpiso.NativeObjectState","latest",null);
                Object selectedObject=selected==null?null:GameAccess.field(selected.getClass().getName(),"object",selected);
                
                if(selectedObject!=object)throw new AssertionError("Actual renderer lost faded interaction source");
                objectState.endRender();WorldRenderer.finish(frame.scene);
                int glError=GL11.glGetError();if(glError!=0)throw new AssertionError("GL state error "+glError);
            }
            System.out.println("Full pick085 GPU PASS: 20 cases; actual VP gbuffer and source-range upload, 8 percent faded interaction source, zoom levels=5 yaw angles=4, asynchronous selection independent of view coverage.");
            NativeSurfaceReceiverProbe.retire(floor);
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
}
