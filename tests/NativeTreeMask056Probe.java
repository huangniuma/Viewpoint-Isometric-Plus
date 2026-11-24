import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Original mask producer and sprite ring-buffer consumer with raw-GL shader churn. */
public class NativeTreeMask056Probe extends NativeTreeRender055Probe {
    static Object sprites,states,queue,ring,cell,defaultProgram;
    static int defaultId;
    public static void main(String[] args)throws Exception {
        baseline=args.length>0&&args[0].equals("baseline055");
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);
        var af=fixture.getDeclaredField("allocate");af.setAccessible(true);af.set(null,uf.getType().getMethod("allocateInstance",Class.class));
        var us=fixture.getDeclaredField("unsafe");us.setAccessible(true);us.set(null,uf.get(null));
        call("fixture",new Class<?>[0]);
        set(staticGet("player"),"ecsComponentMap",new HashMap<>());
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long win=GLFW.glfwCreateWindow(SIZE,SIZE,"original cutaway mask",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
            GL43.glDebugMessageCallback((source,type,id,severity,length,message,user)->{if(type==GL43.GL_DEBUG_TYPE_ERROR)System.err.println("GL ERROR "+GLDebugMessageCallback.getMessage(length,message));},0L);
            GL11.glEnable(GL43.GL_DEBUG_OUTPUT);GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            zombie.core.VBO.GLVertexBufferObject.init();
            setup();
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            attach(GL30.GL_COLOR_ATTACHMENT0,GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE);
            attach(GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
            for(int frame=0;frame<32;frame++)checkMask(frame,(frame&1)==0,(frame&2)!=0);
            System.out.println((baseline?"Pinned 055 negative control":"Production 056 helper queue")+" PASS: 32 alternating raw/default-program frames, both helper-cache states, original public mask producer and native sprite ring-buffer consumer; controlled texture/matrix, no game process or map.");
        } finally {GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static void setup()throws Exception {
        sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        states=get(sprites,"states");
        queue=GameAccess.type("zombie.core.sprite.SpriteRenderState").getConstructor(int.class).newInstance(0);
        set(states,"populating",queue);set(states,"rendering",queue);
        ring=GameAccess.field("zombie.core.SpriteRenderer","ringBuffer",null);
        invoke(ring,"create",new Class<?>[0]);
        defaultProgram=shader("default.vert","default.frag");
        defaultId=((Number)get(defaultProgram,"shaderId")).intValue();
        Object wrapper=fresh("zombie.core.DefaultShader");
        var constructor=GameAccess.type("zombie.core.opengl.Shader$ShaderProgramSpecific").getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object specific=constructor.newInstance(wrapper);set(specific,"shaderProgram",defaultProgram);
        set(wrapper,"shaderProgramSpecific",specific);set(wrapper,"shaderProgramInitialized",true);
        GameAccess.call("zombie.core.DefaultShader","onCompileSuccess",wrapper,new Class<?>[]{defaultProgram.getClass()},defaultProgram);
        GameAccess.set("zombie.core.SceneShaderStore","defaultShader",null,wrapper);
        GameAccess.set("zombie.core.SceneShaderStore","defaultShaderId",null,defaultId);
        GameAccess.call("zombie.core.opengl.ShaderPrograms","registerProgram",GameAccess.staticCall("zombie.core.opengl.ShaderPrograms","getInstance"),new Class<?>[]{defaultProgram.getClass()},defaultProgram);
        Object core=GameAccess.staticCall("zombie.core.Core","getInstance");
        GameAccess.call("zombie.core.opengl.MatrixStack","push",get(core,"projectionMatrixStack"),new Class<?>[]{Matrix4f.class},new Matrix4f().setOrtho2D(0,SIZE,SIZE,0));
        GameAccess.call("zombie.core.opengl.MatrixStack","push",get(core,"modelViewMatrixStack"),new Class<?>[]{Matrix4f.class},new Matrix4f());
        GameAccess.call("zombie.core.textures.MultiTextureFBO2","setZoomAndTargetZoom",get(core,"offscreenBuffer"),new Class<?>[]{int.class,float.class},0,1f);
        GameAccess.set("zombie.core.Core","width",null,SIZE);GameAccess.set("zombie.core.Core","height",null,SIZE);
        GL20.glUseProgram(defaultId);NativeVisibility053Probe.matrix(defaultId,"ModelViewProjection",new Matrix4f().setOrtho2D(0,SIZE,SIZE,0));
        cell=fresh("zombie.iso.IsoCell");set(cell,"stencilAreas",new ArrayList<>());
        Object texture=texture("actual mask");
        set(texture,"width",64);set(texture,"height",64);
        set(cell,"stencilTexture",new HashMap<>(Map.of("media/mask_transparency_player.png",texture)));
    }
    static void checkMask(int frame,boolean raw,boolean staleCache)throws Exception {
        GameAccess.call(queue.getClass().getName(),"clear",queue,new Class<?>[0]);
        GameAccess.staticCall("zombie.core.opengl.GLState","startFrame");
        GameAccess.staticCall("zombie.core.opengl.GLStateRenderThread","startFrame");
        GL11.glViewport(0,0,SIZE,SIZE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glStencilMask(255);
        GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GameAccess.staticCall("zombie.core.ShaderHelper","forgetCurrentlyBound");
        GameAccess.call("zombie.core.ShaderHelper","glUseProgramObjectARB",null,new Class<?>[]{int.class},0);
        if(raw)GL20.glUseProgram(0);
        if(!staleCache)GameAccess.staticCall("zombie.core.ShaderHelper","forgetCurrentlyBound");
        GameAccess.call("zombie.core.SpriteRenderer","glDepthMask",sprites,new Class<?>[]{boolean.class},true);
        GameAccess.call("zombie.core.SpriteRenderer","glStencilMask",sprites,new Class<?>[]{int.class},255);
        if(baseline)invoke(cell,"drawStencilMask",new Class<?>[0]);
        else GameAccess.call("vpiso.NativeTreeCutaway","drawStencilMask",null,new Class<?>[]{Object.class,Object.class},cell,sprites);
        int count=((Number)get(queue,"numSprites")).intValue();
        invoke(ring,"begin",new Class<?>[0]);
        invoke(sprites,"buildDrawBuffer",new Class<?>[]{GameAccess.type("[Lzombie.core.textures.TextureDraw;"),GameAccess.type("[Lzombie.core.Styles.Style;"),int.class},get(queue,"sprite"),get(queue,"style"),count);
        invoke(ring,"render",new Class<?>[0]);
        ByteBuffer mask=BufferUtils.createByteBuffer(SIZE*SIZE);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,mask);
        int painted=0;for(int i=0;i<mask.capacity();i++)if((mask.get(i)&128)!=0)painted++;
        int error=GL11.glGetError();
        System.out.println("frame="+frame+" baseline055="+baseline+" raw="+raw+" staleCache="+staleCache+" queued="+count+" stencil128Pixels="+painted+" glError="+error+" boundProgram="+GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        int expected=baseline&&raw?0:15128,expectedError=baseline&&raw&&staleCache?GL11.GL_INVALID_OPERATION:0;
        if(error!=expectedError||painted!=expected)throw new AssertionError("Native mask shader-state discriminator");
    }
    static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try {var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
}
