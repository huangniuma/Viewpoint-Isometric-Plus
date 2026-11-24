import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Actual external passes and original-source GPU reference; no game or personal writes. */
public final class TrueWeathersProbe {
    private static final String VERTEX="""
        #version 330
        out vec2 vUv;
        void main() {
          vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
          vUv=p;gl_Position=vec4(p*2.-1.,0.,1.);
        }
        """;
    private static final float[] COLOR={.32f,.48f,.70f,1f};
    private static int color,depth,black;
    public static void main(String[] args)throws Exception {
        Main.compatible=true;
        TestShaderPacks.select("trueweathers","high",Map.of("upscaling","off","reflections","off"));
        ShaderPack external=ShaderPacks.active().pack;
        if(external.builtIn || !IsometricShaders.supports(external.id) || IsometricBridge.requestedBlockReason()!=null)
            throw new AssertionError("External supported pack blocked");
        checkProfile(external);
        TestShaderPacks.select("trueweathers","high",Map.of("upscaling","off","reflections","off"));
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(16,16,"VPISO True Weathers verification",0,0);
        if(window==0)throw new AssertionError("Hidden GL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            Frame frame=new Frame();frame.scene.sunX=.6f;frame.scene.sunY=.8f;
            frame.scene.sunStrength=1f;frame.scene.shadowReach=64f;
            if(!NativeShadowMaps.draw(frame.scene,256,2))throw new AssertionError("External native maps not ready");
            GlProgram.onUse(IsometricShaders::onUse);
            if(!GlProgram.pack().pack.id.equals("trueweathers"))throw new AssertionError("External pack not linked");
            var ids=ShaderPacks.active().passes.stream().map(p->p.id).toList();
            if(!ids.containsAll(List.of("twGrade","twAtmosphere")))throw new AssertionError("Weather pass graph lost");
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            int output=texture(COLOR);color=texture(COLOR);depth=texture(new float[]{1,1,1,1});black=texture(new float[]{0,0,0,0});
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
            GL11.glViewport(0,0,1,1);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);
            GlProgram grade=GlProgram.pass("twGrade"),atmosphere=GlProgram.pass("twAtmosphere");
            String original=Files.readString(TestShaderPacks.FOLDER.toPath().resolve("trueweathers/shaders/tw_grade.frag"));
            String defines=(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active());
            int at=original.indexOf('\n');original=original.substring(0,at+1)+defines+original.substring(at+1);
            GlProgram reference=GlProgram.fromSources("original True Weathers grade",new GlProgram.Sources(VERTEX,null,null,null,original),Map.of(),Map.of("fragColor",0),List.of());
            IsometricBridge.rendering(null);
            float[] ordinary=draw(grade,true),expected=draw(reference,true);
            near(ordinary,expected,"ordinary VP original weather grade");
            IsometricBridge.modelRendering(frame,effects(true,1f));
            near(draw(grade,true),expected,"full style keeps original weather grade");
            IsometricBridge.modelRendering(frame,effects(false,1f));
            near(draw(grade,true),COLOR,"style off bypasses weather grade");
            IsometricBridge.modelRendering(frame,effects(true,0f));
            near(draw(grade,true),COLOR,"zero style strength bypasses weather grade");
            IsometricBridge.modelRendering(frame,effects(true,.5f));
            float[] half=draw(grade,true);
            for(int i=0;i<3;i++)if(Math.abs(half[i]-(COLOR[i]+expected[i])*.5f)>2e-5f)throw new AssertionError("Weather grade strength");
            IsometricBridge.modelRendering(frame,effects(true,1f));
            float[] storm=draw(grade,true),sunny=draw(grade,false);
            different(storm,sunny,"weather-driven grade");
            IsometricBridge.rendering(null);
            near(draw(grade,true),ordinary,"model style scope isolated from ordinary VP");
            float[] sky=draw(atmosphere,true);different(sky,COLOR,"storm sky custom composite");
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,BufferUtils.createFloatBuffer(4).put(new float[]{.5f,.5f,.5f,1f}).flip());
            near(draw(atmosphere,true),COLOR,"storm sky leaves foreground geometry intact");
            reference.delete();
            System.out.println("True Weathers custom GPU PASS: actual twGrade/twAtmosphere passes, original-source grade equality, storm response, foreground sky exclusion, style off/strength and ordinary VP isolation.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static void checkProfile(ShaderPack external) {
        ShaderPack library=(ShaderPack)GameAccess.call("viewpoint.platform.ShaderPacks","find",null,new Class<?>[]{String.class},"library");
        GameAccess.set("viewpoint.platform.Graphics","modern",null,List.of(external,library));
        GameAccess.set("viewpoint.platform.Graphics","iris",null,List.of());
        LiveSettings.Choice mode=LiveSettings.choice("graphics.mode","Mode","fixture",new String[]{"Vanilla","Modern"},1);
        GameAccess.set("viewpoint.platform.Graphics","mode",null,mode);
        GameAccess.call("viewpoint.platform.Graphics","declarePack",null,new Class<?>[0]);
        LiveSettings.Choice choice=(LiveSettings.Choice)GameAccess.field("viewpoint.platform.Graphics","pack",null);choice.set(0);
        var upscale=LiveSettings.choice("pack.trueweathers.upscaling","Upscaling","fixture",new String[]{"off","fsr3"},1);
        var ssr=LiveSettings.choice("pack.trueweathers.reflections","SSR","fixture",new String[]{"off","high"},1);
        Graphics.FRAME_GENERATION.set(true);IsometricControls.tick();
        if(choice.get()!=0 || upscale.get()!=1 || ssr.get()!=1 || !Graphics.FRAME_GENERATION.get())throw new AssertionError("Normal tick changed graphics");
        Object button=GameAccess.field("vpiso.IsometricControls","prepareButton",null);
        GameAccess.call("viewpoint.platform.SettingsWindow$Button","press",button,new Class<?>[0]);IsometricControls.tick();
        if(choice.get()!=0 || upscale.get()!=0 || ssr.get()!=0 || Graphics.FRAME_GENERATION.get())throw new AssertionError("Explicit action lost external pack or restrictions");
        System.out.println("True Weathers profile PASS: external pack kept by explicit compatibility action; normal ticks leave selections untouched.");
    }
    private static IsometricEffects effects(boolean style,float strength) {return new IsometricEffects(true,.55f,1f,64f,256,style,strength,1f,1f,1f);}
    private static int texture(float[] pixel) {
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,BufferUtils.createFloatBuffer(4).put(pixel).flip());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return id;
    }
    private static float[] draw(GlProgram program,boolean storm) {
        program.use();
        if(program.has("uColor"))program.setInt("uColor",0);
        if(program.has("uBloom"))program.setInt("uBloom",2);
        for(String name:List.of("uDepth","uFarDepth"))if(program.has(name))program.setInt(name,1);
        if(program.has("uResolution"))program.set("uResolution",1f,1f);
        if(program.has("uFarRange"))program.set("uFarRange",0f,400f);
        if(program.has("uSkySun"))program.set("uSkySun",0f,.1f,0f);
        for(String name:List.of("uTWStorm","uTWRain","uRainStrength"))if(program.has(name))program.set(name,storm?1f:0f);
        if(program.has("uTWClass"))program.set("uTWClass",6f);
        for(String name:List.of("uTWActive","uSunStrength"))if(program.has(name))program.set(name,1f);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);GL11.glBindTexture(GL11.GL_TEXTURE_2D,black);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer data=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,data);
        float[] pixel=new float[4];data.get(pixel);for(float x:pixel)if(!Float.isFinite(x))throw new AssertionError("Nonfinite pixel");return pixel;
    }
    private static void near(float[] actual,float[] expected,String label) {
        for(int i=0;i<4;i++)if(Math.abs(actual[i]-expected[i])>2e-5f)throw new AssertionError(label+": "+Arrays.toString(actual)+" != "+Arrays.toString(expected));
    }
    private static void different(float[] a,float[] b,String label) {
        float error=0;for(int i=0;i<3;i++)error+=Math.abs(a[i]-b[i]);if(error<.02f)throw new AssertionError(label+" inactive");
    }
}
