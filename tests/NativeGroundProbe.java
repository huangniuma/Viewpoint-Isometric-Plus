import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Actual native tile source and original pole alpha: identical depth must not imply floor ownership. */
public final class NativeGroundProbe {
    static final int W=128,H=256;
    static int color, fbo, depthTexture, floorTexture, poleTexture, program, shadow;
    static final String VERTEX="""
        #version 330
        out vec4 col; out vec2 texCoord; out vec2 texCoord2;
        void main(){ vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); gl_Position=vec4(p*2-1,0,1);
          texCoord=p; texCoord2=p; col=vec4(1); }
        """;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit()) throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,H,"native ground ownership",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            String source=Files.readString(Path.of(args[0],"media/shaders/tileWithDepth.frag"));
            program=link(VERTEX,NativeGroundShaders.rewrite("tileWithDepth.frag",source));
            compileNative(args[0]);
            color=texture(W,H,null);floorTexture=solid(100,140,180,255);
            BufferedImage pole=ImageIO.read(Path.of(args[1]).toFile());
            ByteBuffer bytes=BufferUtils.createByteBuffer(W*H*4);
            for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                int v=x<pole.getWidth() && y<pole.getHeight()?pole.getRGB(x,pole.getHeight()-1-y):0;
                int a=v>>>24;bytes.put((byte)((v>>16)&255)).put((byte)((v>>8)&255)).put((byte)(v&255)).put((byte)a);
            }
            bytes.flip();poleTexture=texture(W,H,bytes);
            depthTexture=solid(128,128,128,255);
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            int depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,H);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
            GL30.glBindVertexArray(GL30.glGenVertexArrays());GL11.glViewport(0,0,W,H);
            shadow=GL11.glGenTextures();GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,shadow);
            FloatBuffer depths=BufferUtils.createFloatBuffer(8*8*3);while(depths.hasRemaining())depths.put(.2f);depths.flip();
            GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL30.GL_DEPTH_COMPONENT32F,8,8,3,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL14.GL_TEXTURE_COMPARE_MODE,GL30.GL_COMPARE_REF_TO_TEXTURE);
            NativeReceiver receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.5,.5},{.6,.5,.5},{.5,.6,.5},{.5,.5,.6}});
            NativeOverlay overlay=new NativeOverlay();int opaque=0,ground=0,edge=0;
            if(args.length>2) {
                java.net.URL jar=Path.of(args[2]).toUri().toURL();
                try(var loader=new java.net.URLClassLoader(new java.net.URL[]{jar},NativeGroundProbe.class.getClassLoader()) {
                    @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                        if(!name.equals("vpiso.NativeOverlay"))return super.loadClass(name,resolve);
                        Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
                    }
                }) {
                    String old=(String)loader.loadClass("vpiso.NativeOverlay").getField("FRAGMENT").get(null);
                    GameAccess.set("vpiso.NativeOverlay","program",overlay,viewpoint.platform.GlProgram.fromSources("017 negative",
                        new viewpoint.platform.GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,old),java.util.Map.of(),java.util.Map.of(),java.util.List.of("017 negative")));
                }
            }
            int outputColor=texture(W,H,null),outputFbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,outputColor,0);
            int outputDepth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,outputDepth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,H);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,outputDepth);
            Object chunk=GameAccess.type("zombie.iso.fboRenderChunk.FBORenderChunk").getConstructor().newInstance();
            Object texture=GameAccess.type("zombie.core.textures.Texture").getConstructor().newInstance();
            var unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
            Object unsafe=unsafeField.get(null);
            Object id=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,GameAccess.type("zombie.core.textures.TextureID"));
            GameAccess.set("zombie.core.textures.TextureID","id",id,depthTexture);
            GameAccess.set("zombie.core.textures.Texture","dataid",texture,id);
            GameAccess.set(chunk.getClass().getName(),"depth",chunk,texture);
            Object command=GameAccess.type("zombie.core.textures.TextureDraw").getConstructor().newInstance();
            GameAccess.set(command.getClass().getName(),"tex1",command,texture);
            int composite=link(VERTEX,NativeGroundShaders.rewrite("chunkShader.frag",Files.readString(Path.of(args[0],"media/shaders/chunkShader.frag"))));
            zombie.iso.IsoObject[] floors={floorObject(true,false),floorObject(true,false),floorObject(false,false),
                    floorObject(true,true),floorObject(false,true),floorObject(false,false)};
            boolean paraboloid=NativeMetadata074.v2();
            for(int frame=0;frame<12;frame++) {
                boolean receives=NativeGround.receivesSun(floors[frame%6]);
                if(receives!=(frame%6<2 || frame%6==3)) throw new AssertionError("Native exterior eligibility changed");
                int level=frame%6-2;
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
                NativeGround.begin(true);
                // Native PZO SSR binds its square classification map to unit15 during
                // the frame. The addon must preserve both that binding and its sampler.
                int ssrTexture=solid(41,73,109,255),ssrSampler=GL33.glGenSamplers();
                GL13.glActiveTexture(GL13.GL_TEXTURE15);GL11.glBindTexture(GL11.GL_TEXTURE_2D,ssrTexture);
                GL33.glBindSampler(15,ssrSampler);GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClearColor(0,0,0,0);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                NativeGround.chunkBegin(chunk,true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,0);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                applyFloor(level);draw(floorTexture,true);draw(poleTexture,false);
                NativeGround.chunkEnd();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);GL11.glDisable(GL11.GL_BLEND);
                GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                GL20.glUseProgram(composite);NativeGround.program(composite);NativeGround.chunkShader(command);
                assertSsr(ssrTexture,ssrSampler);
                GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DEPTH"),1);
                GL20.glUniform1i(GL20.glGetUniformLocation(composite,"useTexture"),1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
                GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthTexture);GL13.glActiveTexture(GL13.GL_TEXTURE0);
                // Emulate PZO resetting its own buffers after shader setup; actual draw hook must append slot7.
                GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                ByteBuffer before=read();
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+6);
                FloatBuffer occlusion=BufferUtils.createFloatBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_FLOAT,occlusion);
                for(int i=0;i<W*H;i++) {
                    float alpha=(bytes.get(i*4+3)&255)/255f,coverage=1-alpha;
                    near(occlusion.get(i*4),alpha,.0001f,"nonfloor coverage");
                    near(occlusion.get(i*4+1),level*coverage,.0001f,"floor first moment");
                    near(occlusion.get(i*4+2),level*level*coverage+(paraboloid?alpha:0),.0001f,"floor/nonfloor second moment");
                    near(occlusion.get(i*4+3),1,.0001f,"native coverage");
                }
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+7);
                ByteBuffer floorPixels=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,floorPixels);
                for(int i=0;i<W*H;i++)for(int c=0;c<3;c++) {
                    int expected=Math.round(new int[]{100,140,180}[c]*(1-(bytes.get(i*4+3)&255)/255f));
                    if(Math.abs((floorPixels.get(i*4+c)&255)-expected)>2)throw new AssertionError("Native floor RGB lost indoor/fade contribution");
                }
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                int contribution=NativeGround.texture();overlay.ground(contribution);overlay.metadata(NativeGround.occlusionTexture());NativeGround.finish();
                assertSsr(ssrTexture,ssrSampler);
                GL33.glBindSampler(15,0);GL33.glDeleteSamplers(ssrSampler);GL11.glDeleteTextures(ssrTexture);
                // A native/PZO offscreen pass may leave an auxiliary read attachment
                // selected. Capturing color must still read the original image0.
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+7);
                overlay.capture(true);
                if(GL11.glGetInteger(GL11.GL_READ_BUFFER)!=GL30.GL_COLOR_ATTACHMENT0+7)
                    throw new AssertionError("Native read-buffer state not restored");
                Matrix4f projection=new Matrix4f().set(new float[]{0,0,0,0,0,0,0,0,0,0,0,0,.5f,.5f,frame%2==0?.6f:.1f,1});
                overlay.draw(receiver,new IsometricEffects(true,.5f,0,128,1024,false,0,1,1,1),shadow,
                        new Matrix4f[]{projection,projection,projection},new float[]{.01f,.01f,.01f},100,8,1,1);
                ByteBuffer after=read();
                for(int i=0;i<W*H;i++) {
                    int a=bytes.get(i*4+3)&255;
                    if(a==255)opaque++;else if(a==0)ground++;else edge++;
                    for(int c=0;c<4;c++)if(after.get(i*4+c)!=before.get(i*4+c))
                        throw new AssertionError("Unprepared lighting changed original RGBA frame="+frame+" pixel="+i+" component="+c);
                }
                if(GL11.glGetError()!=0) throw new AssertionError("GL error");
            }
            if(opaque<1000||edge<10||ground<1000)throw new AssertionError("Unrepresentative sprite");
            mixedLevels(chunk,command,composite,outputFbo);
            straightForeground(outputFbo);
            queuedFloorLevels(outputFbo);
            NativeGround.release(fbo);NativeGround.release(outputFbo);
            System.out.println("Native ground ownership GPU PASS: actual native tile/chunk consumers; equal floor/object depth, opaque="+opaque+", floor="+ground+", translucent edges="+edge+"; indoor/covered floors retained; native coverage and signed first/second floor moments survive chunk transfer; mixed levels retain nonzero variance; straight-alpha wall preserves floor moments/coverage; queued floor levels immutable with render-square override and null-owner guard; unprepared lighting strict RGBA identity; SSR unit15, draw-buffer reset, read-buffer restore and cache release.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void applyFloor(int level) throws Exception {
        zombie.iso.IsoObject object=floorObject(true,false);
        Object square=GameAccess.field("zombie.iso.IsoObject","square",object);
        GameAccess.set("zombie.iso.IsoGridSquare","z",square,level);
        for(var command:queuedFloor(object))command.render();
    }
    static zombie.core.textures.TextureDraw.GenericDrawer[] queuedFloor(zombie.iso.IsoObject object) {
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        Object states=GameAccess.field("zombie.core.SpriteRenderer","states",sprites);
        Object queue=GameAccess.call("zombie.core.sprite.SpriteRendererStates","getPopulatingActiveState",states,new Class<?>[0]);
        int begin=((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue();
        NativeGround.queueFloor(object);
        Object array=GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","sprite",queue);
        int end=((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue();
        var commands=new zombie.core.textures.TextureDraw.GenericDrawer[end-begin];
        for(int i=begin;i<end;i++) {
            Object command=java.lang.reflect.Array.get(array,i);
            commands[i-begin]=(zombie.core.textures.TextureDraw.GenericDrawer)GameAccess.field("zombie.core.textures.TextureDraw","drawer",command);
        }
        if(commands.length!=1)throw new AssertionError("Floor did not enqueue exactly one native GenericDrawer");
        return commands;
    }
    static void mixedLevels(Object chunk,Object command,int composite,int outputFbo) throws Exception {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);NativeGround.begin(true);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDepthMask(true);GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);NativeGround.chunkBegin(chunk,true);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        applyFloor(0);draw(floorTexture,true);
        int faded=solid(50,70,90,128);applyFloor(2);draw(faded,true);NativeGround.chunkEnd();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);GL11.glDisable(GL11.GL_BLEND);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL20.glUseProgram(composite);NativeGround.program(composite);NativeGround.chunkShader(command);
        GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DEPTH"),1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthTexture);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+6);
        FloatBuffer m=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(W/2,H/2,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,m);
        float alpha=128/255f,mean=m.get(1)/(m.get(3)-m.get(0)),variance=m.get(2)/(m.get(3)-m.get(0))-mean*mean;
        near(m.get(0),0,.0001f,"mixed floor occlusion");near(m.get(1),2*alpha,.0001f,"mixed first moment");
        near(m.get(2),4*alpha,.0001f,"mixed second moment");near(m.get(3),1,.0001f,"mixed coverage");
        if(variance<.99f)throw new AssertionError("Two floors masquerade as a third floor: mean="+mean+", variance="+variance);
        NativeGround.finish();GL11.glDeleteTextures(faded);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }
    static void straightForeground(int outputFbo) throws Exception {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);NativeGround.begin(true);
        GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glDepthMask(true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{0,0,0,0});GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{0,0,0,0});
        GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE_MINUS_DST_ALPHA,GL11.GL_ONE);
        applyFloor(-2);draw(floorTexture,true);
        int wall=solid(190,40,65,128);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);draw(wall,false);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+6);
        FloatBuffer m=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(W/2,H/2,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,m);
        float alpha=128/255f,coverage=1-alpha;
        near(m.get(0),alpha,.0001f,"straight wall nonfloor coverage");
        near(m.get(1),-2*coverage,.0001f,"straight wall floor first moment");
        near(m.get(2),4*coverage+(NativeMetadata074.v2()?alpha:0),.0001f,"straight wall floor/nonfloor second moment");
        near(m.get(3),1,.0001f,"straight wall metadata coverage union");
        float mean=m.get(1)/(m.get(3)-m.get(0));near(mean,-2,.0001f,"straight wall retains floor level");
        NativeGround.finish();GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glDeleteTextures(wall);
    }
    static void near(float value,float expected,float epsilon,String label) {
        if(Math.abs(value-expected)>epsilon)throw new AssertionError(label+": "+value+" != "+expected);
    }
    static void queuedFloorLevels(int outputFbo) throws Exception {
        for(int mode=0;mode<3;mode++) {
            zombie.iso.IsoObject object=floorObject(true,false);
            Object source=GameAccess.field("zombie.iso.IsoObject","square",object);
            Object override=GameAccess.field("zombie.iso.IsoObject","square",floorObject(false,true));
            GameAccess.set("zombie.iso.IsoGridSquare","z",source,-4);
            GameAccess.set("zombie.iso.IsoGridSquare","z",override,-2);
            if(mode==1)GameAccess.set("zombie.iso.IsoObject","renderSquareOverride",object,override);
            if(mode==2)GameAccess.set("zombie.iso.IsoObject","square",object,null);
            var commands=queuedFloor(object);
            // The update thread may reuse both object and square before render-thread replay.
            GameAccess.set("zombie.iso.IsoGridSquare","z",source,5);
            GameAccess.set("zombie.iso.IsoGridSquare","z",override,6);
            GameAccess.set("zombie.iso.IsoObject","square",object,source);
            GameAccess.set("zombie.iso.IsoObject","renderSquareOverride",object,null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
            NativeGround.begin(true);GL11.glDisable(GL11.GL_BLEND);GL11.glDepthMask(true);
            GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            for(var command:commands)command.render();
            drawQueued(floorTexture);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+6);
            FloatBuffer metadata=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(W/2,H/2,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,metadata);
            int level=mode==0?-4:mode==1?-2:0;
            near(metadata.get(0),mode==2?1:0,.0001f,"queued nonfloor "+mode);
            near(metadata.get(1),level,.0001f,"immutable queued floor first moment "+mode);
            near(metadata.get(2),level*level+(NativeMetadata074.v2() && mode==2?1:0),.0001f,"immutable queued floor/nonfloor second moment "+mode);
            near(metadata.get(3),1,.0001f,"queued native coverage "+mode);
            NativeGround.finish();GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        }
    }
    static zombie.iso.IsoObject floorObject(boolean outside,boolean roof) throws Exception {
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);Object unsafe=field.get(null);
        Object object=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,GameAccess.type("zombie.iso.IsoObject"));
        Object square=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,GameAccess.type("zombie.iso.IsoGridSquare"));
        Object properties=GameAccess.type("zombie.core.properties.PropertyContainer").getConstructor().newInstance();
        GameAccess.set("zombie.iso.IsoGridSquare","properties",square,properties);
        GameAccess.set("zombie.iso.IsoGridSquare","haveRoof",square,roof);
        GameAccess.set("zombie.iso.IsoObject","square",object,square);
        if(outside) GameAccess.call("zombie.core.properties.PropertyContainer","set",properties,
                new Class<?>[]{GameAccess.type("zombie.iso.SpriteDetails.IsoFlagType")},
                GameAccess.field("zombie.iso.SpriteDetails.IsoFlagType","exterior",null));
        return (zombie.iso.IsoObject)object;
    }
    static void compileNative(String game) throws Exception {
        int count=0;
        try(var files=Files.list(Path.of(game,"media/shaders"))) {
            for(Path path:files.filter(p->p.toString().endsWith(".frag")).toList()) {
                String text=Files.readString(path);
                if(text.contains("#include"))continue;
                String rewritten=NativeGroundShaders.rewrite(path.toString(),text);
                int id=shader(GL20.GL_FRAGMENT_SHADER,rewritten);GL20.glDeleteShader(id);count++;
            }
        }
        System.out.println("Native ground shader compile PASS: "+count+" installed fragment sources.");
    }
    static void draw(int texture,boolean floor) {
        GL20.glUseProgram(program);NativeGround.setFloor(floor);
        drawQueued(texture);
    }
    static void drawQueued(int texture) {
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"DEPTH"),1);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"zDepthBlendZ"),.4f);GL20.glUniform1f(GL20.glGetUniformLocation(program,"zDepthBlendToZ"),.5f);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthTexture);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
    static void assertSsr(int texture,int sampler) {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE15);
        if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=texture || GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,15)!=sampler)
            throw new AssertionError("PZO SSR native unit15 texture/sampler overwritten by addon");
        GL13.glActiveTexture(active);
    }
    static int solid(int r,int g,int b,int a) {ByteBuffer bytes=BufferUtils.createByteBuffer(4);bytes.put((byte)r).put((byte)g).put((byte)b).put((byte)a).flip();return texture(1,1,bytes);}
    static int texture(int w,int h,ByteBuffer bytes) {int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    static ByteBuffer read() {ByteBuffer p=BufferUtils.createByteBuffer(W*H*4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);return p;}
    static int shader(int type,String source) {int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(s)+"\n"+source);return s;}
    static int link(String v,String f) {int p=GL20.glCreateProgram();GL20.glAttachShader(p,shader(GL20.GL_VERTEX_SHADER,v));GL20.glAttachShader(p,shader(GL20.GL_FRAGMENT_SHADER,f));GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(p));return p;}
}

