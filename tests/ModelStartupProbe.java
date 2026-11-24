import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.render.*;
import vpiso.*;

/** Actual addon drawer after VP's hidden loading frame, with no PZO classes/overrides. */
public final class ModelStartupProbe {
    static final int W=128;
    public static void main(String[] args)throws Exception {
        String pack=args.length>0?args[0]:"library";
        boolean baseline=args.length>1;
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(W,W,"cold addon drawer, vanilla game",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
            var loadMask=NativeWallCutaway.class.getDeclaredMethod("loadMask",java.nio.file.Path.class);loadMask.setAccessible(true);
            loadMask.invoke(null,java.nio.file.Path.of("D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid/media/wallcutaways.png"));
            TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off","antialiasing","off"));
            int output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
            for(boolean depth:new boolean[]{false,true}) {
                int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,depth?GL30.GL_DEPTH_COMPONENT32F:GL30.GL_RGBA32F,W,W,0,depth?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,GL11.GL_FLOAT,0L);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,depth?GL30.GL_DEPTH_ATTACHMENT:GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,t,0);
            }
            Frame frame=NativeSurfaceReceiverProbe.frame();frame.camX=128;frame.camY=128;
            float[][] points=ModelShadowMotionProbe.tiledFloor();float[] vertices=new float[points.length*14];
            for(int i=0;i<points.length;i++){System.arraycopy(points[i],0,vertices,i*14,3);vertices[i*14+7]=vertices[i*14+8]=1;vertices[i*14+10]=1;}
            ChunkMeshData floor=new ChunkMeshData(new zombie.core.textures.TextureID[1],new int[]{0},new int[]{points.length},vertices,0);
            frame.scene.meshes[0]=floor;frame.scene.meshCount=1;frame.scene.meshFlags[0]=1;
            frame.scene.meshPlan[0]=-1;frame.scene.meshModelSquares[0]=-1L;frame.scene.meshFade[1]=2;
            frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
            var constructor=IsoDrawer.class.getDeclaredConstructor(Frame.class);constructor.setAccessible(true);
            IsoDrawer drawer=constructor.newInstance(frame);drawer.effects=ModelShadowMotionProbe.FX;
            IsoCameraMath.matrices(new IsoCameraMath.Fit(128,128,12),128,128,W,W,frame.scene.projection,drawer.view,drawer.eye,0);
            WorldRenderer.debugView=6;
            int cold=0,warm=0;
            for(int step=0;step<4;step++) {
                frame.number++;frame.scene.cameraCut=true;
                // SceneDrawer leaves this process-global flag set after a hidden loading frame.
                WorldRenderer.hidden=step<2;
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,W);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
                GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                drawer.render();
                if((Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null))throw new AssertionError("drawer failed");
                if(WorldRenderer.hidden!=(step<2))throw new AssertionError("previous VP hidden state not restored");
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                int lit=0;for(int y=32;y<96;y++)for(int x=32;x<96;x++)if(pixels.get((y*W+x)*4)>.1f)lit++;
                if(step<2)cold+=lit;else warm+=lit;
            }
            NativeSurfaceReceiverProbe.retire(floor);
            if(warm<4000 || (baseline?cold!=0:cold!=warm))throw new AssertionError("cold="+cold+" warm="+warm+" baseline="+baseline);
            System.out.println("Model startup "+(baseline?"048 negative control":"GPU PASS")+": pack="+pack+" cold="+cold+" warm="+warm+"; actual IsoDrawer and VP renderer; no PZO; no view toggle or settings writes.");
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
}
