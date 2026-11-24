import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import vpiso.*;

/** Actual original FBORenderTrees and VBORenderer GPU consumer, controlled textures/world. */
@SuppressWarnings("unchecked")
public class NativeTreeRender055Probe {
    static final int SIZE=512;
    static Object renderer,template,colors,farTexture;
    static final Class<?> fixture=NativeTreeProducerProbe.class;
    static boolean baseline;
    static Path game=Path.of("D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid");
    public static void main(String[] args)throws Exception {
        baseline=args.length>0 && args[0].equals("baseline054");
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);
        var af=fixture.getDeclaredField("allocate");af.setAccessible(true);af.set(null,uf.getType().getMethod("allocateInstance",Class.class));
        var us=fixture.getDeclaredField("unsafe");us.setAccessible(true);us.set(null,uf.get(null));
        call("fixture",new Class<?>[0]);
        template=staticGet("square");
        renderer=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell","instance",null);
        Object color=Class.forName("zombie.core.textures.ColorInfo").getConstructor(float.class,float.class,float.class,float.class).newInstance(1f,1f,1f,1f);
        colors=Array.newInstance(color.getClass(),4);for(int i=0;i<4;i++)Array.set(colors,i,color);
        set(staticGet("player"),"ecsComponentMap",new HashMap<>());
        Object cell=fresh("zombie.iso.IsoCell");set(cell,"stencilAreas",new ArrayList<>());
        set(GameAccess.field("zombie.iso.IsoWorld","instance",null),"currentCell",cell);
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long win=GLFW.glfwCreateWindow(SIZE,SIZE,"actual native tree consumer",0,0);
        try{
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
            GL43.glDebugMessageCallback((source,type,id,severity,length,message,user)->{if(type==GL43.GL_DEBUG_TYPE_ERROR){System.err.println("GL ERROR "+GLDebugMessageCallback.getMessage(length,message));new Exception("GL caller").printStackTrace();}},0L);
            GL11.glEnable(GL43.GL_DEBUG_OUTPUT);GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            zombie.core.VBO.GLVertexBufferObject.init();
            setupNative();
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            attach(GL30.GL_COLOR_ATTACHMENT0,GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE);
            attach(GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
            for(boolean neighbour:new boolean[]{false,true})for(boolean stencil:new boolean[]{false,true})check(neighbour,stencil);
            System.out.println((baseline?"Pinned 054 actual native tree negative control PASS:":"Actual native tree consumer 055 GPU PASS:")+" original FBORenderTrees.render, per-tree alpha/stencil/outline passes, original VBORenderer, original shader resources, record membership churn and untouched stencil; controlled native world/textures, no live map.");
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
        }finally{GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static void setupNative()throws Exception {
        zombie.core.opengl.GLStateRenderThread.AlphaFunc.set(GL11.GL_GREATER,0f);
        zombie.core.opengl.GLStateRenderThread.AlphaTest.set(true);
        zombie.core.opengl.GLStateRenderThread.Blend.set(true);
        zombie.core.opengl.GLStateRenderThread.BlendFuncSeparate.set(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        zombie.core.opengl.GLStateRenderThread.ColorMask.set(true,true,true,true);
        zombie.core.opengl.GLStateRenderThread.DepthFunc.set(GL11.GL_LEQUAL);
        zombie.core.opengl.GLStateRenderThread.DepthMask.set(true);
        zombie.core.opengl.GLStateRenderThread.DepthTest.set(true);
        zombie.core.opengl.GLStateRenderThread.ScissorTest.set(false);
        zombie.core.opengl.GLStateRenderThread.StencilFunc.set(GL11.GL_ALWAYS,255,255);
        zombie.core.opengl.GLStateRenderThread.StencilMask.set(255);
        zombie.core.opengl.GLStateRenderThread.StencilOp.set(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);
        zombie.core.opengl.GLStateRenderThread.StencilTest.set(false);
        Object sprite=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        Object states=get(sprite,"states");Object state=GameAccess.type("zombie.core.sprite.SpriteRenderState").getConstructor(int.class).newInstance(0);
        set(states,"rendering",state);Object cam=Array.get(get(state,"playerCamera"),0);
        set(cam,"offscreenWidth",SIZE);set(cam,"offscreenHeight",SIZE);
        // Keep the native scale and real original billboard/projection matrices.
        System.out.println("Original Core.scale="+GameAccess.field("zombie.core.Core","scale",null));
        Object core=GameAccess.staticCall("zombie.core.Core","getInstance");
        Object proj=get(core,"projectionMatrixStack");
        if((Boolean)GameAccess.call(proj.getClass().getName(),"isEmpty",proj,new Class<?>[0]))GameAccess.call(proj.getClass().getName(),"push",proj,new Class<?>[]{Matrix4f.class},new Matrix4f());
        Object vbo=GameAccess.staticCall("zombie.core.opengl.VBORenderer","getInstance");
        Object p=shader("vboRenderer_PositionColorUV.vert","vboRenderer_PositionColorUV.frag");
        Object wrapper=GameAccess.type("zombie.core.opengl.VBOLinesShader").getConstructor(String.class).newInstance("fixture no files");
        Object specific=fresh("zombie.core.opengl.Shader$ShaderProgramSpecific");set(specific,"shaderProgram",p);set(wrapper,"shaderProgramSpecific",specific);set(wrapper,"shaderProgramInitialized",true);
        set(vbo,"shaderPositionColorUv",wrapper);
        Object outline=fresh("zombie.core.skinnedmodel.shader.Shader");set(outline,"shaderProgram",shader("vboRenderer_Tree_static.vert","vboRenderer_Tree.frag"));
        GameAccess.set("zombie.iso.fboRenderChunk.FBORenderTrees","shader",null,outline);
    }
    static Object shader(String vertex,String fragment)throws Exception {
        int program=NativeVisibility053Probe.program(Files.readString(game.resolve("media/shaders/"+vertex)),Files.readString(game.resolve("media/shaders/"+fragment)));
        Object p=GameAccess.call("zombie.core.opengl.ShaderProgram","createShaderProgram",null,new Class<?>[]{String.class,boolean.class,boolean.class,boolean.class},"fixture",true,false,false);
        set(p,"shaderId",program);var m=p.getClass().getDeclaredMethod("onCompileSuccess");m.setAccessible(true);m.invoke(p);return p;
    }
    static void check(boolean neighbour,boolean stencil)throws Exception {
        var bridge=new NativeTrees();
        Object red=tree("far",10,13),blue=tree("near",11,12),extra=tree("extra",12,11);
        try {
            if(neighbour)bridge.add(get(extra,"square"));
            bridge.add(get(red,"square"));bridge.add(get(blue,"square"));bridge.capture(renderer,0);
            Object batch=get(bridge,"batch");List<Object> records=(List<Object>)get(batch,"trees");
            for(Object r:records){boolean rear=get(r,"texture")==farTexture;boolean invisible=get(get(r,"texture"),"name").equals("extra");set(r,"texture",texture(invisible?"invisible":rear?"red":"blue"));set(r,"r",rear?1f:0f);set(r,"g",0f);set(r,"b",rear?0f:1f);set(r,"useStencil",stencil);set(r,"transparent",!stencil);set(r,"a",1f);set(r,"fadeAlpha",.4f);set(r,"cutawayAlpha",.4f);}
            GL11.glViewport(0,0,SIZE,SIZE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glClearStencil(stencil?128:0);GL11.glStencilMask(255);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,0f);GL11.glDisable(GL11.GL_CULL_FACE);
            GameAccess.call("zombie.iso.fboRenderChunk.FBORenderTrees","render",batch,new Class<?>[0]);
            ByteBuffer pixels=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            int both=0,rOnly=0,bOnly=0,any=0;for(int i=0;i<pixels.capacity();i+=4){int r=pixels.get(i)&255,b=pixels.get(i+2)&255;if(r>20&&b>20)both++;else if(r>20)rOnly++;else if(b>20)bOnly++;if(r>20||b>20)any++;}
            int error=GL11.glGetError();
            if(error!=0 || (!baseline || neighbour) && both<10000 || baseline && !neighbour && both!=0)throw new AssertionError("Native consumer baseline="+baseline+" neighbour="+neighbour+" both="+both+" error="+error);
            ByteBuffer mask=BufferUtils.createByteBuffer(SIZE*SIZE);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,mask);
            for(int i=0;i<mask.capacity();i++)if((mask.get(i)&255)!=(stencil?128:0))throw new AssertionError("Tree changed stencil at "+i);
            System.out.println("Actual native consumer baseline="+baseline+" neighbour="+neighbour+" stencil="+stencil+" both="+both+" redOnly="+rOnly+" blueOnly="+bOnly+" any="+any+" error="+error+" native stencil retained");
        }finally{bridge.release();}
    }
    static Object texture(String name)throws Exception {
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);ByteBuffer data=BufferUtils.createByteBuffer(384*512*4);for(int y=0;y<512;y++)for(int x=0;x<384;x++)data.put((byte)255).put((byte)255).put((byte)255).put((byte)(!name.equals("invisible")&&x>=8&&x<376&&y>=8&&y<504?255:0));data.flip();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,384,512,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        var ctor=GameAccess.type("zombie.core.textures.TextureID").getDeclaredConstructor();ctor.setAccessible(true);Object page=ctor.newInstance();set(page,"id",id);set(page,"widthHw",384);set(page,"heightHw",512);set(page,"width",384);set(page,"height",512);set(page,"glMinFilter",GL11.GL_NEAREST);set(page,"glMagFilter",GL11.GL_NEAREST);
        Object t=GameAccess.type("zombie.core.textures.Texture").getConstructor(GameAccess.type("zombie.core.textures.TextureID"),String.class).newInstance(page,name);set(t,"width",384);set(t,"height",512);set(t,"widthOrig",384);set(t,"heightOrig",512);return t;
    }
    static void attach(int at,int internal,int format,int type){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,SIZE,SIZE,0,format,type,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,at,GL11.GL_TEXTURE_2D,t,0);}
    static Object tree(String label,int x,int y)throws Exception {
        Object sq=fresh("zombie.iso.IsoGridSquare");set(sq,"x",x);set(sq,"y",y);set(sq,"z",0);
        for(String name:new String[]{"properties","nav","lighting"})set(sq,name,get(template,name));
        set(sq,"lightInfo",colors);set(sq,"targetPlayerCutawayFlags",new byte[4]);set(sq,"playerCutawayFlags",new byte[4]);set(sq,"playerCutawayFlagLockUntilTimes",new long[4]);
        Object texture=call("texture",new Class<?>[]{String.class},label);
        if(label.equals("far"))farTexture=texture;
        Object sprite=call("sprite",new Class<?>[]{String.class,Object.class},"VPISO_overlap_"+label,texture);
        set(sprite,"properties",Class.forName("zombie.core.properties.PropertyContainer").getConstructor().newInstance());
        Object tree=call("tree",new Class<?>[]{Object.class},sprite);set(tree,"square",sq);set(tree,"cutawayAlpha",.35f);set(tree,"fadeAlpha",.35f);
        set(sq,"objects",new ArrayList<>(List.of(tree)));return tree;
    }
    static Object call(String n,Class<?>[] types,Object...args)throws Exception{var m=fixture.getDeclaredMethod(n,types);m.setAccessible(true);try{return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}}
    static Object staticGet(String n)throws Exception{var f=fixture.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    static Object fresh(String name)throws Exception{return call("fresh",new Class<?>[]{String.class},name);}
    static Object get(Object o,String n)throws Exception{return call("get",new Class<?>[]{Object.class,String.class},o,n);}
    static void set(Object o,String n,Object v)throws Exception{call("set",new Class<?>[]{Object.class,String.class,Object.class},o,n,v);}
}
