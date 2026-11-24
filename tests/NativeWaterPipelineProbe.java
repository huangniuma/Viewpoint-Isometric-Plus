import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.LiveSettings;
import viewpoint.platform.ShaderPacks;
import vpiso.*;

/** Real UI/hook/queue/drawer + square/water geometry, controlled cell/camera/climate inputs. */
public final class NativeWaterPipelineProbe {
    private static final String STATE="zombie.core.sprite.GenericSpriteRenderState";
    private static final int W=32,H=24;
    private static Object square,queue,cell;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"VPISO native water route",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));viewpoint.platform.Gl.init();
            fixture();int fbo=fbo();
            toggle("water").set(true);IsometricControls.tick();
            if(afterHook()!=1) throw new AssertionError("Water-only hook did not queue a drawer");
            Object command=Array.get(GameAccess.field(STATE,"sprite",queue),0);
            NativeOverlayDrawer drawer=(NativeOverlayDrawer)GameAccess.field("zombie.core.textures.TextureDraw","drawer",command);
            if(!drawer.water.ready() || !drawer.effects.water() || drawer.effects.needsStyle() || drawer.effects.needsShadows()) throw new AssertionError("Water-only snapshot missing/other effect active");
            Frame captured=(Frame)GameAccess.field("vpiso.NativeOverlayDrawer","frame",drawer);
            if(captured.scene.skyR<=0) throw new AssertionError("Standalone captured frame never received Daylight.snapshot");
            // A later settings/square change cannot alter the already queued frame.
            toggle("water").set(false);IsometricControls.tick();
            Object geometry=GameAccess.field("zombie.iso.IsoGridSquare","water",square);
            GameAccess.set("zombie.iso.IsoWaterGeometry","hasWater",geometry,false);
            clear(fbo);byte[] original=image();
            GameAccess.call("zombie.core.textures.TextureDraw","run",command,new Class<?>[0]);
            GameAccess.call("zombie.core.textures.TextureDraw","postRender",command,new Class<?>[0]);
            byte[] changed=image();int sum=0;for(int i=0;i<changed.length;i++)sum+=Math.abs((changed[i]&255)-(original[i]&255));
            if(sum<30) throw new AssertionError("Water setting queued but did not change native image");
            if((Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null)) throw new AssertionError("Water drawer caught an exception");
            if(afterHook()!=0) throw new AssertionError("All effects off still queued water");
            toggle("water").set(true);toggle("models").set(true);IsometricControls.tick();
            if(afterHook()!=0) throw new AssertionError("Model mode also queued native-world water");
            Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
            if((targets!=null&&((Number)GameAccess.field("viewpoint.render.Targets","gbufferFbo",targets)).intValue()!=0)||GameAccess.field("vpiso.IsometricBridge","captureFit",null)!=null)throw new AssertionError("Native water-only route used VP world/GBuffer capture");
            if(GL11.glGetError()!=0)throw new AssertionError("Water pipeline GL error");
            System.out.println("Native water pipeline PASS: actual LiveSettings -> afterNativeWorld -> SpriteRenderer/TextureDraw.run -> NativeOverlayDrawer; real Snapshot.capture with actual IsoGridSquare.getWater/isValid/depth; queued independent settings+geometry; Daylight.snapshot; water-only changes native pixels without VP GBuffer/captureWorld; off/model guards. Controlled cell lookup/camera/climate, no game/map/settings writes.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    @SuppressWarnings("unchecked") private static void fixture() throws Exception {
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);Object unsafe=field.get(null);Method allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
        Object player=allocate.invoke(unsafe,GameAccess.type("zombie.characters.IsoPlayer"));
        Array.set(GameAccess.field("zombie.characters.IsoPlayer","players",null),0,player);GameAccess.set("zombie.iso.IsoCamera","isoCameraGameCharacter",null,player);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","playerIndex",GameAccess.field("zombie.iso.IsoCamera","frameState",null),0);
        square=allocate.invoke(unsafe,GameAccess.type("zombie.iso.IsoGridSquare"));cell=allocate.invoke(unsafe,GameAccess.type("zombie.iso.IsoCell"));
        Object chunk=allocate.invoke(unsafe,GameAccess.type("zombie.iso.IsoChunk"));GameAccess.set("zombie.iso.IsoGridSquare","chunk",square,chunk);
        Object geometry=GameAccess.type("zombie.iso.IsoWaterGeometry").getConstructor().newInstance();GameAccess.set("zombie.iso.IsoWaterGeometry","hasWater",geometry,true);
        java.util.Arrays.fill((float[])GameAccess.field("zombie.iso.IsoWaterGeometry","depth",geometry),1f);
        GameAccess.set("zombie.iso.IsoGridSquare","water",square,geometry);
        Map<String,Method> methods=(Map<String,Method>)GameAccess.field("vpiso.GameAccess","METHODS",null);
        Object texture=GameAccess.type("zombie.core.textures.Texture").getConstructor(GameAccess.type("zombie.core.textures.TextureID"),String.class)
                .newInstance(NativeVPWaterProbe.materialPage(),"water-material-fixture");
        GameAccess.set("zombie.core.textures.Texture","widthOrig",texture,64);GameAccess.set("zombie.core.textures.Texture","heightOrig",texture,128);
        GameAccess.set("zombie.core.textures.Texture","xEnd",texture,1f);GameAccess.set("zombie.core.textures.Texture","yEnd",texture,1f);
        Object sprite=allocate.invoke(unsafe,GameAccess.type("zombie.iso.sprite.IsoSprite"));
        GameAccess.set("zombie.iso.sprite.IsoSprite","texture",sprite,texture);
        GameAccess.set("zombie.iso.sprite.IsoSprite","def",sprite,allocate.invoke(unsafe,GameAccess.type("zombie.iso.sprite.IsoSpriteInstance")));
        Object manager=GameAccess.field("zombie.iso.sprite.IsoSpriteManager","instance",null);
        ((Map<Object,Object>)GameAccess.field("zombie.iso.sprite.IsoSpriteManager","namedMap",manager)).put("blends_natural_02_0",sprite);
        inject(methods,"zombie.core.textures.Texture","isReady",new Class<?>[0],"ready");
        inject(methods,"zombie.iso.IsoGridSquare","getFloor",new Class<?>[0],"noFloor");
        inject(methods,"zombie.iso.IsoCamera","getOffscreenWidth",new Class<?>[]{int.class},"width",int.class);
        inject(methods,"zombie.iso.IsoCamera","getOffscreenHeight",new Class<?>[]{int.class},"height",int.class);
        Class<?>[] screen={float.class,float.class,float.class,int.class};
        inject(methods,"zombie.iso.IsoUtils","XToScreenExact",screen,"screenX",screen);
        inject(methods,"zombie.iso.IsoUtils","YToScreenExact",screen,"screenY",screen);
        inject(methods,"viewpoint.environment.Weather","snapshot",new Class<?>[]{Frame.class,GameAccess.type("zombie.iso.IsoCell")},"weather",Frame.class,Object.class);
        GameAccess.call("zombie.core.random.RandStandard","init",GameAccess.field("zombie.core.random.RandStandard","INSTANCE",null),new Class<?>[0]);
        Object climate=GameAccess.staticCall("zombie.iso.weather.ClimateManager","getInstance");
        GameAccess.call("zombie.iso.weather.ClimateManager","setup",climate,new Class<?>[0]);
        GameAccess.set("zombie.iso.weather.ClimateManager$ClimateFloat","finalValue",GameAccess.field("zombie.iso.weather.ClimateManager","dayLightStrength",climate),1f);
        Object time=GameAccess.staticCall("zombie.GameTime","getInstance");
        GameAccess.set("zombie.GameTime","timeOfDay",time,12f);
        GameAccess.set("zombie.GameTime","month",time,5);GameAccess.set("zombie.GameTime","day",time,15);
        GameAccess.set("zombie.GameTime","minutesPerDay",time,60f);
        Object renderSettings=GameAccess.call("zombie.core.opengl.RenderSettings","getPlayerSettings",GameAccess.staticCall("zombie.core.opengl.RenderSettings","getInstance"),new Class<?>[]{int.class},0);
        for(String name:new String[]{"rmod","gmod","bmod","ambient"}) GameAccess.set("zombie.core.opengl.RenderSettings$PlayerRenderSettings",name,renderSettings,.6f);
        Object files=GameAccess.field("zombie.ZomboidFileSystem","instance",null);
        for(String name:new String[]{"base","workdir"}) GameAccess.call("zombie.ZomboidFileSystem$PZFolder","set",GameAccess.field("zombie.ZomboidFileSystem",name,files),new Class<?>[]{java.io.File.class},new java.io.File("research/build013-water/empty-game"));
        Class<?> bindings=GameAccess.type("vpiso.NativeVPWaterOverlay$Bindings");Object api=GameAccess.field(bindings.getName(),"API",null);
        MethodHandle lookup=MethodHandles.lookup().unreflect(NativeWaterPipelineProbe.class.getMethod("lookup",Object.class,int.class,int.class,int.class));
        GameAccess.set(bindings.getName(),"square",api,lookup);
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null),states=GameAccess.field("zombie.core.SpriteRenderer","states",sprites);
        queue=GameAccess.call("zombie.core.sprite.SpriteRendererStates","getPopulatingActiveState",states,new Class<?>[0]);
        Main.compatible=true;viewpoint.core.View.enabled=false;IsometricControls.tick();
        toggle("enabledSetting").set(true);toggle("models").set(false);toggle("shadows").set(false);toggle("style").set(false);IsometricControls.tick();
    }
    private static void inject(Map<String,Method> cache,String owner,String name,Class<?>[] abi,String fixture,Class<?>... signature)throws Exception{cache.put(owner+"."+name+java.util.Arrays.toString(abi),NativeWaterPipelineProbe.class.getMethod(fixture,signature));}
    public static int width(int p){return W;} public static int height(int p){return H;}
    public static boolean ready(){return true;} public static Object noFloor(){return null;}
    public static float screenX(float x,float y,float z,int p){return W*.5f+(x-y)*4f;}
    public static float screenY(float x,float y,float z,int p){return H*.5f+(x+y)*2f-z*12f;}
    public static Object lookup(Object ignored,int x,int y,int z){return z==0&&x>=-8&&x<8&&y>=-8&&y<8?square:null;}
    public static void weather(Frame f,Object ignored){f.scene.windX=2.4f;f.scene.windZ=.8f;f.scene.cloudTime=30f;}
    private static LiveSettings.Toggle toggle(String name){return(LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls",name,null);}
    private static int afterHook(){GameAccess.set(STATE,"numSprites",queue,0);IsometricBridge.afterNativeWorld(cell);if((Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null))throw new AssertionError("Native hook caught an error");return((Number)GameAccess.field(STATE,"numSprites",queue)).intValue();}
    private static int fbo(){int color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,W,H,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);int depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,H);int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);GL11.glViewport(0,0,W,H);return fbo;}
    private static void clear(int fbo){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL11.glClearColor(80f/255f,120f/255f,160f/255f,127f/255f);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);}
    private static byte[] image(){ByteBuffer data=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);byte[] result=new byte[data.remaining()];data.get(result);return result;}
}
