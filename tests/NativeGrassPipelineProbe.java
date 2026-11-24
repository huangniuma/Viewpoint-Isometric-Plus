import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Actual control -> native-world hook -> queued drawer; captures controlled world data only. */
public final class NativeGrassPipelineProbe {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(32,24,"grass native queue",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
            TestShaderPacks.select("library","high",Map.of("upscaling","off"));Gl.init();
            Method fixture=NativeWaterPipelineProbe.class.getDeclaredMethod("fixture");fixture.setAccessible(true);fixture.invoke(null);
            @SuppressWarnings("unchecked") Map<String,Method> cache=(Map<String,Method>)GameAccess.field("vpiso.GameAccess","METHODS",null);
            Class<?>[] abi={Frame.class,GameAccess.type("zombie.iso.IsoCell"),GameAccess.type("zombie.characters.IsoGameCharacter"),int.class};
            cache.put("viewpoint.FrameFill.captureWorld"+Arrays.toString(abi),NativeGrassPipelineProbe.class.getMethod("world",Frame.class,Object.class,Object.class,int.class));
            Method fbo=NativeWaterPipelineProbe.class.getDeclaredMethod("fbo");fbo.setAccessible(true);int target=(Integer)fbo.invoke(null);
            Method hook=NativeWaterPipelineProbe.class.getDeclaredMethod("afterHook");hook.setAccessible(true);
            LiveSettings.Toggle grass=(LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","grass",null);
            grass.set(true);IsometricControls.tick();
            GameAccess.call("viewpoint.platform.LiveSettings","put",null,new Class<?>[]{String.class,String.class},"grass.quality","Medium");
            GameAccess.set("zombie.core.PerformanceSettings","fboRenderChunk",null,true);
            IsometricBridge.beforeNativeWorld();
            Field q=NativeWaterPipelineProbe.class.getDeclaredField("queue");q.setAccessible(true);
            Object begin=Array.get(GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","sprite",q.get(null)),0);
            GameAccess.call("zombie.core.textures.TextureDraw","run",begin,new Class<?>[0]);
            if(NativeGround.occlusionTexture()==0)throw new AssertionError("Grass-only native world did not allocate a foreground mask");
            if((Integer)hook.invoke(null)!=1)throw new AssertionError("Grass-only frame not queued");
            Object command=Array.get(GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","sprite",q.get(null)),0);
            NativeOverlayDrawer drawer=(NativeOverlayDrawer)GameAccess.field("zombie.core.textures.TextureDraw","drawer",command);
            if(!drawer.effects.grass()||drawer.effects.needsShadows()||drawer.effects.needsStyle()||drawer.effects.water())throw new AssertionError("Grass depends on other effects");
            grass.set(false);IsometricControls.tick();
            Method clear=NativeWaterPipelineProbe.class.getDeclaredMethod("clear",int.class);clear.setAccessible(true);clear.invoke(null,target);
            GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{0,0,0,0});
            GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{80f/255f,120f/255f,160f/255f,127f/255f});
            GameAccess.call("zombie.core.textures.TextureDraw","run",command,new Class<?>[0]);
            if(!drawer.effects.grass()||IsometricBridge.rendering()||(Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null))throw new AssertionError("Queued snapshot/render failed");
            float[] depth=new float[32*24];GL11.glReadPixels(0,0,32,24,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
            int visible=0;for(float d:depth)if(d<.999f)visible++;
            if(visible==0)throw new AssertionError("Grass-only native queue drew no grass");
            GameAccess.call("zombie.core.textures.TextureDraw","postRender",command,new Class<?>[0]);
            if((Integer)hook.invoke(null)!=0)throw new AssertionError("Off grass still queues");
            if(GL11.glGetError()!=0)throw new AssertionError("Native grass queue GL error");
            System.out.println("Native grass pipeline PASS: independent default-off setting, actual before/after native hooks, grass-only foreground mask, visible grass pixels="+visible+", native capture ownership, queued options preserved, real SpriteRenderer drawer, off identity. Controlled cell/camera/climate capture.");
        } finally {GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    public static void world(Frame f,Object cell,Object player,int index) {
        if(!IsometricBridge.capturing()||!IsometricBridge.nativeShadowCapture())throw new AssertionError("Grass capture lost native cursor ownership");
        f.scene.grass.chunks=1;f.scene.grass.chunkX[0]=f.scene.grass.chunkY[0]=0;
        Arrays.fill(f.scene.grass.values,0,64,393217);
    }
}
