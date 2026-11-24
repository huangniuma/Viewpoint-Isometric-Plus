import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import static net.bytebuddy.matcher.ElementMatchers.named;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import vpiso.*;

/** Actual PZO memoryPass and native blur: color compatibility, not a map/LOS reproduction. */
public final class ModelVisionMemoryProbe {
    static final int W=320,H=180,HW=512,MHW=256;
    static int output,world,mask,depth,vao;
    static Object maskTexture,depthTexture;
    static GlProgram fallback;

    public static void main(String[] args)throws Exception {
        // This fixture explicitly tests the PZO-on branch, independently of the user's
        // options.ini. A JVM-only pin must precede Config/Overrides initialization.
        System.setProperty("pzopt.enabled","true");
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,H,"model vision color compatibility",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            setup(Path.of(args[0]));
            queuedOwnership();
            scopeAdvice();
            System.out.println("Model vision memory PASS: actual PZO memoryPass negative control, native alpha-only fallback,"
                    + " partial polygon, padded half-size mask, foreground depth clipping, rotated/unrotated queued ownership,"
                    + " native reuse, nested/exceptional scope and thread isolation; no map/LOS or FPS acceptance.");
        } finally { NativeVision.leaveVision(false);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate(); }
    }

    static void setup(Path game)throws Exception {
        GameAccess.set("zombie.core.skinnedmodel.model.VertexBufferObject","funcs",null,
                GameAccess.type("zombie.core.VBO.GLBufferObject15").getConstructor().newInstance());
        vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
        world=texture(GL11.GL_RGBA8,GL11.GL_RGBA,HW);
        ByteBuffer neutral=BufferUtils.createByteBuffer(HW*HW*4);
        for(int i=0;i<HW*HW;i++)neutral.put(new byte[]{(byte)153,(byte)153,(byte)153,(byte)255});neutral.flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,HW,HW,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,neutral);
        output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture(GL11.GL_RGBA8,GL11.GL_RGBA,HW),0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,HW),0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("output FBO");
        int maskFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,maskFbo);
        mask=texture(GL11.GL_RGBA8,GL11.GL_RGBA,MHW);depth=texture(GL14.GL_DEPTH_COMPONENT16,GL11.GL_DEPTH_COMPONENT,MHW);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,mask,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        GL11.glViewport(0,0,W/2,H/2);GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClearColor(0,0,0,0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
        String type="zombie.vispoly.VisibilityPolygon2$Drawer$PolygonData";
        var ctor=GameAccess.type(type).getDeclaredConstructor();ctor.setAccessible(true);Object polygon=ctor.newInstance();
        Object camera=GameAccess.type("zombie.iso.PlayerCamera").getConstructor(int.class).newInstance(0);
        GameAccess.set("zombie.iso.PlayerCamera","offX",camera,-W*.5f);
        GameAccess.set("zombie.iso.PlayerCamera","offY",camera,8192f-H*.5f);
        GameAccess.set(type,"camera",polygon,camera);GameAccess.set(type,"sx",polygon,128);GameAccess.set(type,"sy",polygon,128);
        var add=GameAccess.type(type).getDeclaredMethod("addVertex",float.class,float.class,float.class);add.setAccessible(true);
        for(float[] p:new float[][]{{125,125},{128,125},{128,131},{125,125},{128,131},{125,131}})add.invoke(polygon,p[0],p[1],1f);
        ((FloatBuffer)GameAccess.field(type,"data",polygon)).flip();
        GameAccess.call(type,"init",polygon,new Class<?>[0]);
        GlProgram producer=GlProgram.fromSources("actual native mask",new GlProgram.Sources(
                Files.readString(game.resolve("media/shaders/visPolygon.vert")),null,null,null,
                Files.readString(game.resolve("media/shaders/visPolygon.frag"))),Map.of(),Map.of(),List.of());
        producer.use();GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),"ModelViewProjection"),false,
                new float[]{2f/W,0,0,0,0,-2f/H,0,0,0,0,-1,0,-1,1,0,1});
        GameAccess.call(type,"draw",polygon,new Class<?>[0]);
        maskTexture=wrap(mask,W/2,H/2,MHW);depthTexture=wrap(depth,W/2,H/2,MHW);
        Object fbo=allocate("zombie.core.textures.TextureFBO");GameAccess.set("zombie.core.textures.TextureFBO","texture",fbo,wrap(world,W,H,HW));
        Object core=GameAccess.staticCall("zombie.core.Core","getInstance");
        Object multi=GameAccess.field("zombie.core.Core","offscreenBuffer",core);
        GameAccess.set("zombie.core.textures.MultiTextureFBO2","current",multi,fbo);
        GameAccess.set("pzopt.Darkness","memoryOn",null,true);GameAccess.set("pzopt.Config","MEMORY_TINT",null,true);
        GameAccess.set("pzopt.Config","MEMORY_TINT_PCT",null,40);
        if(!(Boolean)GameAccess.staticCall("pzopt.Darkness","memoryEnabled"))throw new AssertionError("Actual PZO memory gate disabled");
        fallback=GlProgram.fromSources("actual native visibility fallback",new GlProgram.Sources(NativeVision.VERTEX,null,null,null,
                Files.readString(game.resolve("media/shaders/visibilityBlur.frag"))),Map.of(),Map.of(),List.of());
    }

    @SuppressWarnings("unchecked") static void queuedOwnership()throws Exception {
        Object vision=GameAccess.staticCall("zombie.vispoly.VisibilityPolygon2","getInstance");
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        int index=((Number)GameAccess.call("zombie.core.SpriteRenderer","getMainStateIndex",sprites,new Class<?>[0])).intValue();
        Object drawer=((Object[][])GameAccess.field("zombie.vispoly.VisibilityPolygon2","drawers",vision))[0][index];
        var queue=(ThreadLocal<Boolean>)GameAccess.field("vpiso.NativeVision","MODEL_QUEUING",null);
        var rotationQueue=(ThreadLocal<RotationCamera.Snapshot>)GameAccess.field("vpiso.RotationLayers","QUEUING",null);
        Method camera=RotationLayersProbe.class.getDeclaredMethod("camera",int.class,float.class);camera.setAccessible(true);
        var snapshot=(RotationCamera.Snapshot)camera.invoke(null,1,1f);
        for(boolean rotated:new boolean[]{false,true}) {
            queue.set(true);rotationQueue.set(rotated?snapshot:null);
            Patch_RotationVisionQueue.enter(0);queue.set(false);rotationQueue.remove();
            boolean previous=NativeVision.enterVision(drawer);var previousRotation=RotationLayers.enterVision(drawer);
            try {
                if(!NativeVision.modelVision() || (RotationLayers.current()!=null)!=rotated)throw new AssertionError("Queued model ownership");
                checkPixels(true);
                boolean nested=NativeVision.enterVision(new Object());
                if(NativeVision.modelVision())throw new AssertionError("Nested native inherited model scope");
                NativeVision.leaveVision(nested);
                Thread thread=new Thread(()->{if(NativeVision.modelVision())throw new AssertionError("Scope crossed threads");});
                final Throwable[] error={null};thread.setUncaughtExceptionHandler((t,e)->error[0]=e);thread.start();thread.join();
                if(error[0]!=null)throw new AssertionError(error[0]);
            } finally {RotationLayers.end(previousRotation);NativeVision.leaveVision(previous);}
            checkPixels(false);
            queue.set(true);Patch_RotationVisionQueue.enter(0);queue.set(false);Patch_RotationVisionQueue.enter(0);
            previous=NativeVision.enterVision(drawer);
            if(NativeVision.modelVision())throw new AssertionError("Dropped model frame leaked into reused native drawer");
            NativeVision.leaveVision(previous);
        }
        System.out.println("Queued model vision PASS: rotated and unrotated; native reuse, one-shot, nested and thread isolation.");
    }

    static boolean memory() {
        return (Boolean)GameAccess.call("pzopt.Darkness","memoryPass",null,
                new Class<?>[]{GameAccess.type("zombie.core.textures.Texture"),GameAccess.type("zombie.core.textures.Texture"),
                    boolean.class,float.class,float.class,float.class,float.class,float.class,float.class,int.class},
                maskTexture,depthTexture,false,(float)W,(float)H,0f,0f,(float)HW,(float)HW,0);
    }

    static void checkPixels(boolean model) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,H);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
        GL11.glClearColor(.6f,.6f,.6f,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,W/2,H);GL11.glClearDepth(0);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        for(int i=0;i<5;i++)GL33.glBindSampler(i,0);
        boolean handled=memory();
        if(handled==model)throw new AssertionError("Actual memoryPass did not select expected branch: model="+model+" handled="+handled);
        if(!handled) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,mask);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
            fallback.use();fallback.setInt("tex",0);fallback.setInt("depth",1);fallback.set("screenSize",W,H);
            fallback.set("displayOrigin",0f,0f);fallback.set("displaySize",HW,HW);fallback.set("texSize",W/2,H/2);
            fallback.set("TextureSize",MHW,MHW);fallback.set("zoom",1f);fallback.setInt("opacityScale",2);
            GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        }
        ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        int tinted=0,dark=0,unchanged=0,occluded=0;
        for(int y=8;y<H-8;y++)for(int x=8;x<W-8;x++) {
            int offset=(y*W+x)*4,r=pixels.get(offset)&255,g=pixels.get(offset+1)&255,b=pixels.get(offset+2)&255;
            if(x<W/2){if(r!=153||g!=153||b!=153)throw new AssertionError("Foreground depth occlusion lost");occluded++;continue;}
            if(b-r>3)tinted++;
            if(r<152)dark++;else unchanged++;
            if(model && (r!=g||r!=b))throw new AssertionError("Native fallback recolored material");
        }
        if(dark<100||unchanged<100||occluded<100 || (model?tinted!=0:tinted<100))throw new AssertionError("Color/visibility coverage "+tinted+"/"+dark+"/"+unchanged);
        int error=GL11.glGetError();if(error!=0)throw new AssertionError("GL "+error);
        System.out.println("Actual vision color GPU "+(model?"fallback":"negative control")+" PASS: tint="+tinted+", dark="+dark+", visible="+unchanged+", depth-clipped="+occluded);
    }

    public static class ScopeTarget {
        public boolean render(boolean fail) {if(!NativeVision.modelVision())throw new AssertionError("Scope advice missing");if(fail)throw new IllegalStateException("fixture");return true;}
    }
    @SuppressWarnings("unchecked") static void scopeAdvice()throws Exception {
        Method convert=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
        String name="vpiso.Patch_RotationVisionScope";byte[] bytes;
        try(var input=ClassLoader.getSystemResourceAsStream(name.replace('.','/')+".class")){bytes=(byte[])convert.invoke(null,input.readAllBytes());}
        Map<String,byte[]> definitions=new HashMap<>();definitions.put(name,bytes);
        var ctor=Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class);ctor.setAccessible(true);
        Class<?> advice=((ClassLoader)ctor.newInstance(definitions)).loadClass(name);
        var locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(definitions),ClassFileLocator.ForClassLoader.ofSystemLoader());
        byte[] woven=new ByteBuddy().redefine(ScopeTarget.class).visit(Advice.to(advice,locator).on(named("render"))).make().getBytes();
        Class<?> target=((ClassLoader)ctor.newInstance(Map.of(ScopeTarget.class.getName(),woven))).loadClass(ScopeTarget.class.getName());
        Object instance=target.getConstructor().newInstance();Method render=target.getMethod("render",boolean.class);
        var queue=(ThreadLocal<Boolean>)GameAccess.field("vpiso.NativeVision","MODEL_QUEUING",null);
        for(boolean fail:new boolean[]{false,true}) {
            queue.set(true);NativeVision.beforeVision(instance);queue.set(false);
            try {render.invoke(instance,fail);if(fail)throw new AssertionError("Expected exception");}
            catch(InvocationTargetException error){if(!fail||!(error.getCause() instanceof IllegalStateException))throw error;}
            if(NativeVision.modelVision())throw new AssertionError("Advice leaked ownership");
        }
        Main.compatible=false;NativeVision.leaveVision(true);
        try{checkPixels(false);}finally{Main.compatible=true;NativeVision.leaveVision(false);}
        System.out.println("Model vision scope advice PASS: actual converted production advice, normal/exceptional restore and unsupported-VP passthrough.");
    }

    static Object allocate(String type)throws Exception {var f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object u=f.get(null);return u.getClass().getMethod("allocateInstance",Class.class).invoke(u,GameAccess.type(type));}
    static Object wrap(int id,int w,int h,int hw)throws Exception {
        var ctor=GameAccess.type("zombie.core.textures.TextureID").getDeclaredConstructor();ctor.setAccessible(true);Object page=ctor.newInstance();
        GameAccess.set("zombie.core.textures.TextureID","id",page,id);GameAccess.set("zombie.core.textures.TextureID","widthHw",page,hw);GameAccess.set("zombie.core.textures.TextureID","heightHw",page,hw);
        Object texture=GameAccess.type("zombie.core.textures.Texture").getConstructor(GameAccess.type("zombie.core.textures.TextureID"),String.class).newInstance(page,"vision compatibility fixture");
        GameAccess.set("zombie.core.textures.Texture","width",texture,w);GameAccess.set("zombie.core.textures.Texture","height",texture,h);return texture;
    }
    static int texture(int internal,int format,int size) {
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,size,size,0,format,GL11.GL_FLOAT,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return id;
    }
}
