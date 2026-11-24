import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;
import zombie.core.textures.TextureDraw;
import zombie.iso.IsoDirections;
import zombie.iso.IsoObject;

/** Original wall planes and real advice/queue: walls stay foreground, floors keep their own contribution. */
public final class NativeWallReceiverProbe {
    static final int W=128,H=256;
    static Object sprites,queue;
    static int wallTexture,wallDepth,program;
    public static boolean fail;
    public static class WallFixture extends IsoObject {
        public void wall(IsoDirections direction,float x,float y,float z,Object color,Object shader,Object shaper) {
            NativeWallReceiverProbe.enqueueWall();
            if(NativeWallReceiverProbe.fail)throw new IllegalArgumentException("wall scope fixture");
        }
    }
    public static void enqueueWall() { enqueue(new TextureDraw.GenericDrawer() {
        @Override public void render() { draw(wallTexture,wallDepth); }
    }); }
    static void enqueue(TextureDraw.GenericDrawer drawer) {
        GameAccess.call("zombie.core.SpriteRenderer","drawGeneric",sprites,
                new Class<?>[]{TextureDraw.GenericDrawer.class},drawer);
    }
    static int count() { return ((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue(); }
    static void replay(int begin) {
        Object array=GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","sprite",queue);
        for(int i=begin;i<count();i++) {
            Object command=Array.get(array,i);
            ((TextureDraw.GenericDrawer)GameAccess.field("zombie.core.textures.TextureDraw","drawer",command)).render();
        }
    }
    static Class<?> fixture(boolean baseline) throws Exception {
        if(baseline)return WallFixture.class;
        Class<?> probe=Class.forName("PatchWeavingProbe");
        Method convert=probe.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
        Method matcher=probe.getDeclaredMethod("matcher",Class.class,String.class,boolean.class);matcher.setAccessible(true);
        var constructor=Class.forName("PatchWeavingProbe$BytesLoader").getDeclaredConstructor(Map.class,boolean.class);constructor.setAccessible(true);
        String adviceName="vpiso.Patch_NativeGroundWall";
        byte[] adviceBytes;
        try(var stream=NativeWallReceiverProbe.class.getClassLoader().getResourceAsStream(adviceName.replace('.','/')+".class")) {
            adviceBytes=(byte[])convert.invoke(null,stream.readAllBytes());
        }
        var locator=new net.bytebuddy.dynamic.ClassFileLocator.Compound(
            new net.bytebuddy.dynamic.ClassFileLocator.Simple(Map.of(adviceName,adviceBytes)),
            net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
        ClassLoader loader=(ClassLoader)constructor.newInstance(Map.of(adviceName,adviceBytes),false);
        Class<?> advice=loader.loadClass(adviceName);
        @SuppressWarnings("unchecked") var match=(net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription>)matcher.invoke(null,advice,"renderWallTileOnly",true);
        var nativeType=net.bytebuddy.pool.TypePool.Default.of(locator).describe("zombie.iso.IsoObject").resolve();
        if(nativeType.getDeclaredMethods().filter(match).size()!=1)throw new AssertionError("Original wall-only ABI changed");
        // Verify the exact shipped method with the production advice, then exercise its scope
        // on a minimal IsoObject subclass without loading a world or invoking lighting globals.
        new net.bytebuddy.ByteBuddy().redefine(nativeType,locator).visit(net.bytebuddy.asm.Advice.to(advice,locator).on(match)).make();
        byte[] bytes=new net.bytebuddy.ByteBuddy().redefine(WallFixture.class)
            .visit(net.bytebuddy.asm.Advice.to(advice,locator).on(net.bytebuddy.matcher.ElementMatchers.named("wall"))).make().getBytes();
        return ((ClassLoader)constructor.newInstance(Map.of(WallFixture.class.getName(),bytes),false)).loadClass(WallFixture.class.getName());
    }
    static Object wallObject(Class<?> type,boolean outside,boolean roof) throws Exception {
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        Object object=((sun.misc.Unsafe)field.get(null)).allocateInstance(type);
        Object template=NativeGroundProbe.floorObject(outside,roof);
        GameAccess.set("zombie.iso.IsoObject","square",object,GameAccess.field("zombie.iso.IsoObject","square",template));
        return object;
    }
    public static void main(String[] args) throws Exception {
        boolean baseline=args.length>1 && args[1].equals("baseline029");
        Class<?> type=fixture(baseline);Method wall=type.getMethod("wall",IsoDirections.class,float.class,float.class,float.class,Object.class,Object.class,Object.class);
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,H,"native wall receiver verification",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
            Object states=GameAccess.field("zombie.core.SpriteRenderer","states",sprites);
            queue=GameAccess.call("zombie.core.sprite.SpriteRendererStates","getPopulatingActiveState",states,new Class<?>[0]);
            program=NativeGroundProbe.link(NativeGroundProbe.VERTEX,NativeGroundShaders.rewrite("tileWithDepth.frag",
                Files.readString(Path.of(args[0],"media/shaders/tileWithDepth.frag"))));
            int color=NativeGroundProbe.texture(W,H,null),fbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            int depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,H);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
            GL30.glBindVertexArray(GL30.glGenVertexArrays());GL11.glViewport(0,0,W,H);
            int foreground=NativeGroundProbe.solid(29,61,83,255),floorTexture=NativeGroundProbe.solid(45,70,90,255),floorDepth=NativeGroundProbe.solid(128,128,128,255),foregroundDepth=NativeGroundProbe.solid(1,1,1,255);
            NativeReceiver receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.5,.45},{.6,.5,.45},{.5,.6,.45},{.5,.5,.46}});
            NativeOverlay overlay=new NativeOverlay();
            BufferedImage presets=ImageIO.read(Path.of(args[0],"media/depthmaps/DEPTH_preset_depthmaps_01.png").toFile());
            int shadow=GL11.glGenTextures();GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,shadow);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL14.GL_TEXTURE_COMPARE_MODE,GL30.GL_COMPARE_REF_TO_TEXTURE);
            int foregroundPixels=0,protectedPixels=0,frames=0,scopePixels=0;
            for(int side=4;side<=6;side++) {
                ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4),depths=BufferUtils.createByteBuffer(W*H*4);
                for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                    int p=presets.getRGB(side*W+x,H-1-y),a=p>>>24,d=(p>>16)&255;
                    pixels.put((byte)(140*a/255)).put((byte)(170*a/255)).put((byte)(200*a/255)).put((byte)a);
                    depths.put((byte)d).put((byte)d).put((byte)d).put((byte)255);
                }
                pixels.flip();depths.flip();wallTexture=NativeGroundProbe.texture(W,H,pixels);wallDepth=NativeGroundProbe.texture(W,H,depths);
                for(int mode=0;mode<6;mode++)for(int step=0;step<4;step++) {
                    boolean outside=mode!=1,roof=mode==2,requested=mode!=3,capture=mode==4,throwsBody=mode==5;
                    boolean active=requested&&!capture;
                    GameAccess.set("vpiso.IsometricBridge","groundRequested",null,requested);
                    @SuppressWarnings("unchecked") ThreadLocal<Boolean> capturing=(ThreadLocal<Boolean>)GameAccess.field("vpiso.IsometricBridge","CAPTURING",null);
                    capturing.set(capture);fail=throwsBody;
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
                    GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glDepthMask(true);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);NativeGround.begin(true);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                    // Start with a known floor contribution; a wall scope must stop labeling it as floor.
                    // Retain the floor sentinel through enter to make a missing wall patch observable.
                    NativeGround.setFloor(true);draw(floorTexture,floorDepth);
                    int begin=count();
                    try { wall.invoke(wallObject(type,outside,roof),IsoDirections.N,0f,0f,0f,null,null,null);if(throwsBody)throw new AssertionError("Missing fixture throw"); }
                    catch(InvocationTargetException error){if(!throwsBody || !(error.getCause() instanceof IllegalArgumentException))throw error;}
                    replay(begin);capturing.remove();
                    // Attachments/furniture follow the scope and must stay foreground after normal/throw exit.
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(20,80,12,80);draw(foreground,foregroundDepth);GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    ByteBuffer before=NativeGroundProbe.read();
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+7);
                    ByteBuffer floorPixels=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,floorPixels);
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+6);
                    FloatBuffer metadata=BufferUtils.createFloatBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_FLOAT,metadata);
                    for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                        int index=y*W+x,i=index*4;
                        boolean attached=x>=20&&x<32&&y>=80&&y<160;
                        // The real preset depth is tested against the background floor at 128/255.
                        // A preset pixel behind that floor is not a rendered wall fragment.
                        int nativeDepth=depths.get(i)&255;
                        float alpha=nativeDepth>0 && nativeDepth<=128?(pixels.get(i+3)&255)/255f:0;
                        if(active && attached && (before.get(i)&255)==29) {
                            for(int c=0;c<3;c++)if((floorPixels.get(i+c)&255)!=0)throw new AssertionError("Wall exit left attachment labeled as floor");
                            NativeGroundProbe.near(metadata.get(i),1,.0001f,"attached nonfloor coverage");scopePixels++;
                        } else if(active && !attached && alpha>.01f) {
                            for(int c=0;c<3;c++) {
                                int expected=Math.round(new int[]{45,70,90}[c]*(1-alpha));
                                if(Math.abs((floorPixels.get(i+c)&255)-expected)>2)throw new AssertionError("Wall contaminated floor RGB side="+side+" mode="+mode+" pixel="+index+" component="+c+": "+(floorPixels.get(i+c)&255)+" != "+expected);
                            }
                            NativeGroundProbe.near(metadata.get(i),-alpha,.0001f,"native wall coverage");
                            float weight=-metadata.get(i),owner=metadata.get(i+1)/weight;
                            NativeGroundProbe.near(owner,Math.round(owner),.0001f,"wall source integer");
                            NativeGroundProbe.near(metadata.get(i+2),weight*(NativeMetadata074.v2()?1+owner*owner:-owner*owner),.001f,"wall source second moment");
                            foregroundPixels++;
                        }
                    }
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    int contribution=NativeGround.texture();overlay.ground(contribution);overlay.metadata(NativeGround.occlusionTexture());NativeGround.finish();
                    overlay.capture(true);
                    FloatBuffer map=BufferUtils.createFloatBuffer(32*32*3);
                    for(int layer=0;layer<3;layer++)for(int y=0;y<32;y++)for(int x=0;x<32;x++)map.put(x<8+step*4?.2f:.8f);
                    map.flip();GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,shadow);
                    GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL30.GL_DEPTH_COMPONENT32F,32,32,3,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,map);
                    Matrix4f projection=new Matrix4f().set(new float[]{-.1f,0,0,0,0,0,0,0,0,-.1f,0,0,.5f,.5f,.6f,1});
                    overlay.draw(receiver,new IsometricEffects(true,.5f,0,128,1024,false,0,1,1,1),shadow,
                        new Matrix4f[]{projection,projection,projection},new float[]{.01f,.01f,.01f},100,32,1,1);
                    ByteBuffer after=NativeGroundProbe.read();
                    for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                        int i=(y*W+x)*4;
                        for(int c=0;c<4;c++)if(before.get(i+c)!=after.get(i+c))
                            throw new AssertionError("Unprepared lighting changed original wall/attached RGBA mode="+mode);
                        protectedPixels++;
                    }
                    frames++;
                }
            }
            if(foregroundPixels<1000 || scopePixels<1000)throw new AssertionError("Wall foreground/scope coverage missing: "+foregroundPixels+"/"+scopePixels+"; baseline="+baseline);
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("GL error");
            System.out.println("Native wall ownership GPU PASS: "+frames+" frames, original W/N/NW planes and actual advice/ordered GenericDrawer queue; walls stay out of floor RGB (indoor, roofed and outside), foreground samples="+foregroundPixels+"; normal/throw exit attachment checks="+scopePixels+"; unprepared selected-pack lighting preserves original RGBA="+protectedPixels+". Actual projected sun/lamp reception is verified by NativeShadow068Probe; no map.");
        } finally {GameAccess.set("vpiso.IsometricBridge","groundRequested",null,false);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void draw(int diffuse,int depth) {
        GL20.glUseProgram(program);NativeGround.program(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"DEPTH"),1);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"zDepthBlendZ"),.4f);GL20.glUniform1f(GL20.glGetUniformLocation(program,"zDepthBlendToZ"),.5f);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,diffuse);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
}
