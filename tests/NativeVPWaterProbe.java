import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.ShaderPacks;
import vpiso.*;

/** Real VP WaveTile/RainTextures, native image and untouched depth/stencil on an offset viewport. */
public final class NativeVPWaterProbe {
    private static final int W=48,H=40,X=5,Y=7,VW=32,VH=24;
    private static int fbo,color,depth;
    private static Frame frame;
    private static NativeReceiver receiver;
    private static NativeVPWaterOverlay overlay;
    private static NativeVPWaterOverlay.Snapshot water;
    private static Object materialPage;
    private static boolean optics;
    private static boolean live;
    private static String selectedPack;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(96,96,"VPISO independent water",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            String pack=args.length==0?"library":args[0];
            boolean baseline=args.length>1 && args[1].equals("baseline036");
            boolean baseline053=args.length>1 && args[1].equals("baseline053-live");
            live=baseline053 || args.length>1 && (args[1].equals("live054") || args[1].contains("state055")); selectedPack=pack;
            select(pack,args.length>1&&!baseline&&!live?Map.of("upscaling","off","toneCurve",args[1]):Map.of("upscaling","off")); viewpoint.platform.Gl.init();
            if(live && !((String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active())).contains("uPackOptions"))
                throw new AssertionError("Live pack options not active");
            optics=ShaderPacks.active().pipeline.water;
            frame=new Frame(); environment(true); frame.scene.cloudTime=30f; frame.scene.windX=2.4f; frame.scene.windZ=.8f;
            setup(); overlay=new NativeVPWaterOverlay(); water=new NativeVPWaterOverlay.Snapshot();
            water.fit(-4,-4,8,8,0,0,0,grid(true));
            materialPage=materialPage(); material();
            receiver=new NativeReceiver();
            // Real water plane depth=.5, complete native screen coverage with no water depth write.
            receiver.fit(new double[][]{{.5,.5,.5},{.625,.5,.5},{.5,.625,.5},{.5,.5,.6}});
            reset(1f,false); byte[] original=image(); overlay.draw(frame,receiver,water,false); near(image(),original,"water off");
            IsometricBridge.rendering(frame); // Native route; model scope is deliberately absent.
            if(args.length>1 && args[1].contains("state055")) { producerState(args[1].startsWith("baseline")); return; }
            reset(1f,false); sentinelState();
            try { overlay.draw(frame,receiver,water,true); }
            catch(IllegalStateException error) {
                checkState();
                if(!baseline053 || !error.getMessage().contains("'uPackOptions' : undeclared identifier")) throw error;
                System.out.println("Pinned 053 live water negative control PASS ["+pack+"]: reported undeclared uPackOptions compile failure reproduced; GL state restored.");
                return;
            }
            if(baseline053) throw new AssertionError("Pinned 053 did not reproduce reported live-option compile failure");
            checkState(); byte[] enabled=image(); difference(enabled,original,"models off/water on"); invariants();
            if(live) liveOptions();
            reset(1f,false);GL11.glClearColor(.65f,.65f,.65f,127f/255f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            overlay.draw(frame,receiver,water,true);nearViewport(image(),enabled,"opaque water retained native grey/foam colour");
            frame.scene.cloudTime=32f; reset(1f,false); overlay.draw(frame,receiver,water,true);
            if(optics) difference(image(),enabled,"actual VP wave drift"); else near(image(),enabled,"Library sprite must not acquire Default waves");
            water.fit(-4,-4,8,8,0,0,0,grid(false)); reset(1f,false); overlay.draw(frame,receiver,water,true); near(image(),original,"non-water");
            water.fit(-4,-4,8,8,0,0,0,grid(true)); material(); reset(.49f,false); overlay.draw(frame,receiver,water,true); near(image(),original,"native foreground occlusion");
            reset(1f,true); byte[] black=image(); overlay.draw(frame,receiver,water,true); near(image(),black,"original black vision");
            reset(.5005f,false); overlay.draw(frame,receiver,water,true,-.001f); near(image(),original,"low model foreground on body water");
            float[] shoreGrid=grid(true); for(int i=0;i<64;i++) shoreGrid[i*4+1]=1;
            water.fit(-4,-4,8,8,0,0,0,shoreGrid);
            material();
            reset(.5f,false); overlay.draw(frame,receiver,water,true,-.001f); difference(image(),original,"model-shore receiver bias");
            float[] corners=new float[8*8*4]; water.cornerDepth(corners);
            reset(1f,false); overlay.draw(frame,receiver,water,true); near(image(),original,"zero shore geometry coverage");
            corners=new float[8*8*4]; java.util.Arrays.fill(corners,1f); water.cornerDepth(corners);
            environment(false); material(); reset(1f,false); overlay.draw(frame,receiver,water,true); byte[] night=image();
            if(average(night)>=average(enabled)) throw new AssertionError("Night water does not follow VP daylight");
            rotatedWater();
            if(baseline) {
                try { mrtCapture(); }
                catch(AssertionError error) {
                    if(!error.getMessage().contains("Water copied auxiliary attachment 1")) throw error;
                    System.out.println("Water MRT negative control PASS: pinned036 copied auxiliary attachment1 instead of visible color.");
                    return;
                }
                throw new AssertionError("Pinned036 did not reproduce the water MRT defect");
            }
            mrtCapture();
            if(GL11.glGetError()!=0) throw new AssertionError("GL error");
            if(pack.equals("library")) {
                environment(true);material();reset(1f,false);overlay.draw(frame,receiver,water,true);byte[] library=image();
                select("default",Map.of("upscaling","off"));reset(1f,false);overlay.draw(frame,receiver,water,true);difference(image(),library,"active water pack switch");
                select("library",Map.of("upscaling","off"));reset(1f,false);overlay.draw(frame,receiver,water,true);near(image(),library,"water pack switch-back");
            }
            System.out.println("Independent native VP water GPU PASS ["+pack+"]: pack-specific material/optics, models off/water on, off identity, native-tint isolation, appropriate animation, nonwater, original depth occlusion, black vision, native shore coverage, model-water depth bias, day/night, pack reload, GL state/samplers and alpha/depth/stencil/offset viewport preservation.");
            if(live) System.out.println("Live water options 054 GPU PASS ["+pack+"]: production VP generic macros, option block, changing values, native-only drawing and UBO range/state preservation.");
        } finally { IsometricBridge.rendering(null); GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void producerState(boolean baseline) {
        reset(1f,false); overlay.draw(frame,receiver,water,true); byte[] clean=image();
        int failures=0;
        for(int state=0;state<3;state++) {
            overlay=new NativeVPWaterOverlay();reset(1f,false);
            if(state==0) {GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,0f);}
            if(state==1) {GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(3,4,5,6);}
            if(state==2) {GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilFunc(GL11.GL_NEVER,1,255);}
            overlay.draw(frame,receiver,water,true);byte[] dirty=image();
            boolean same=java.util.Arrays.equals(clean,dirty);
            System.out.println("Water producer inherited state "+state+" identical="+same+" mean="+average(dirty)+" clean="+average(clean));
            if(!same)failures++;
            if(state==0&&!GL11.glIsEnabled(GL11.GL_ALPHA_TEST)||state==1&&!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)||state==2&&!GL11.glIsEnabled(GL11.GL_STENCIL_TEST))throw new AssertionError("Caller state lost");
            GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        if(baseline) {if(failures==0)throw new AssertionError("Pinned054 producer state did not reproduce");System.out.println("Pinned 054 water producer state negative control PASS: "+failures+"/3 states corrupt wave texture");}
        else {if(failures>0)throw new AssertionError("Inherited state corrupts water");System.out.println("Water producer state 055 GPU PASS: original alpha/scissor/stencil state cannot alter wave production; caller state restored");}
    }
    private static void select(String pack,Map<String,String> values) {
        String preset=pack.equals("somber")?"quality":"high";
        if(live) TestShaderPacks.selectLive(pack,preset,values);
        else TestShaderPacks.select(pack,preset,values);
    }
    private static void liveOptions() {
        if(ShaderPacks.active().pack.option("exposure")==null) return;
        reset(1f,false);overlay.draw(frame,receiver,water,true);byte[] normal=image();
        Object before=ShaderPacks.active();
        var program=(viewpoint.platform.GlProgram)GameAccess.field("vpiso.NativeVPWaterOverlay","program",overlay);
        int id=program.id();
        if(GL31.glGetUniformBlockIndex(id,"PackOptions")==GL31.GL_INVALID_INDEX)
            throw new AssertionError("Live option block missing from water program");
        select(selectedPack,Map.of("upscaling","off","exposure","0.2"));
        boolean same=ShaderPacks.active()==before;
        reset(1f,false);overlay.draw(frame,receiver,water,true);checkState();byte[] low=image();
        difference(low,normal,"live exposure slider");
        if(average(low)>=average(normal)) throw new AssertionError("Live exposure did not darken water");
        if(same && ((viewpoint.platform.GlProgram)GameAccess.field("vpiso.NativeVPWaterOverlay","program",overlay)).id()!=id)
            throw new AssertionError("Live-only edit recompiled water shader");
        select(selectedPack,Map.of("upscaling","off"));
        reset(1f,false);overlay.draw(frame,receiver,water,true);near(image(),normal,"live exposure restore");
        System.out.println("Live water exposure PASS ["+selectedPack+"]: mean="+average(normal)+" -> "+average(low)+"; same active="+same);
    }
    private static void mrtCapture() {
        environment(true); water.fit(-4,-4,8,8,0,0,0,grid(true)); material();
        reset(1f,false); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        overlay.draw(frame,receiver,water,true); byte[] reference=image();
        int auxiliary=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,auxiliary);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,W,H,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        int other=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,other);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+1,GL11.GL_TEXTURE_2D,auxiliary,0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+1);
        for(int slot:new int[]{1,7}) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL11.GL_TEXTURE_2D,auxiliary,0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0+slot); GL11.glClearColor(.025f,.035f,.045f,.2f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            reset(1f,false); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+slot);
            overlay.draw(frame,receiver,water,true);
            if(GL11.glGetInteger(GL11.GL_READ_BUFFER)!=GL30.GL_COLOR_ATTACHMENT0+slot)
                throw new AssertionError("Water changed the source framebuffer read buffer: slot="+slot+", actual="+GL11.glGetInteger(GL11.GL_READ_BUFFER)+", error="+GL11.glGetError());
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0); byte[] actual=image();
            nearViewport(actual,reference,"Water copied auxiliary attachment "+slot+" instead of visible color");
            invariants();
            reset(1f,false); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+slot);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,other);
            overlay.draw(frame,receiver,water,true);
            if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=other
                    || GL11.glGetInteger(GL11.GL_READ_BUFFER)!=GL30.GL_COLOR_ATTACHMENT0+1)
                throw new AssertionError("Water changed the independent read framebuffer");
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);
            if(GL11.glGetInteger(GL11.GL_READ_BUFFER)!=GL30.GL_COLOR_ATTACHMENT0+slot)
                throw new AssertionError("Water changed the draw framebuffer's read buffer");
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            nearViewport(image(),reference,"Water captured the independent read framebuffer");
            invariants();
        }
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        System.out.println("Water MRT capture PASS: inherited attachments1/7 match visible attachment0; separate read/draw framebuffer selections, alpha, depth, stencil and viewport preserved.");
    }
    private static final int[] UNITS={0,1,2,3,4,5,6,7,8,9,122,125,126};
    private static void rotatedWater() {
        environment(true);
        float[] oneTile=grid(false); oneTile[(4*8+5)*4]=1; // world tile (1,0), not screen-space.
        water.fit(-4,-4,8,8,0,0,0,oneTile); material();
        for(float angle:new float[]{13.25f,47.5f,90f,128.75f,180f,270f,359.5f})
          for(float zoom:new float[]{.75f,1f,1.25f}) {
            org.joml.Matrix4f projection=new org.joml.Matrix4f(),view=new org.joml.Matrix4f();
            IsoCameraMath.matrices(new IsoCameraMath.Fit(0,0,2.5f*zoom),0,0,VW,VH,projection,view,new float[3],angle);
            var camera=new RotationCamera.Snapshot(projection,view,0,0,0,VW,VH,zoom,angle);
            reset(1f,false); byte[] before=image();
            overlay.draw(frame,receiver,water,true,-.00001f,camera); byte[] after=image();
            int changed=0; double[] world=new double[2];
            for(int y=0;y<VH;y++) for(int x=0;x<VW;x++) {
                camera.ground(x+.5,VH-y-.5,0,world);
                int at=((Y+y)*W+X+x)*4;
                boolean different=false;
                for(int c=0;c<3;c++) different|=before[at+c]!=after[at+c];
                if(different) {
                    if(world[0]<.99||world[0]>2.01||world[1]<-.01||world[1]>1.01)
                        throw new AssertionError("Rotated VP water escaped its world tile: "+angle+" "+world[0]+","+world[1]);
                    changed++;
                }
            }
            if(changed<3) throw new AssertionError("Rotated VP water missing angle="+angle+", zoom="+zoom+", pixels="+changed);
            invariants();
            reset(0f,false); before=image(); overlay.draw(frame,receiver,water,true,-.00001f,camera);
            near(image(),before,"rotated VP water foreground depth");
        }
        water.fit(-4,-4,8,8,0,0,0,grid(true));material();
        System.out.println("Rotated VP water GPU PASS: production overlay at seven cardinal/arbitrary yaw angles and three zooms follows one known world tile; actual standard depth occlusion; alpha/depth/stencil/offset viewport preserved.");
    }
    private static void material() { water.material(materialPage,new float[]{0,0,1,1,0,0,1f/64,1f/128},new float[]{frame.scene.skyR,frame.scene.skyG,frame.scene.skyB}); }
    public static Object materialPage() throws Exception {
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        FloatBuffer pixels=BufferUtils.createFloatBuffer(64*128*4);
        for(int y=0;y<128;y++)for(int x=0;x<64;x++) { float k=.9f+.1f*(float)Math.sin(x*.15f+y*.12f);pixels.put(.10f*k).put(.22f*k).put(.21f*k).put(1f); }
        pixels.flip();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,64,128,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        var ctor=GameAccess.type("zombie.core.textures.TextureID").getDeclaredConstructor();ctor.setAccessible(true);Object page=ctor.newInstance();
        GameAccess.set("zombie.core.textures.TextureID","id",page,texture);
        GameAccess.set("zombie.core.textures.TextureID","widthHw",page,64);GameAccess.set("zombie.core.textures.TextureID","heightHw",page,128);
        return page;
    }
    private static int sentinelTexture,sentinelArray,sentinelVolume,sentinelSampler,sentinelVao,sentinelUniform,sentinelOptions,optionOffset;
    private static void sentinelState() {
        sentinelTexture=GL11.glGenTextures();sentinelArray=GL11.glGenTextures();sentinelVolume=GL11.glGenTextures();sentinelSampler=GL33.glGenSamplers();sentinelVao=GL30.glGenVertexArrays();
        for(int unit:UNITS) { GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinelTexture);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,sentinelArray);GL11.glBindTexture(GL12.GL_TEXTURE_3D,sentinelVolume);GL33.glBindSampler(unit,sentinelSampler); }
        GL13.glActiveTexture(GL13.GL_TEXTURE0+126);GL30.glBindVertexArray(sentinelVao);GL20.glUseProgram(0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_GREATER);GL11.glEnable(GL11.GL_BLEND);GL11.glDepthMask(true);
        optionOffset=GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
        sentinelOptions=GL15.glGenBuffers();GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,sentinelOptions);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,optionOffset+256,GL15.GL_STATIC_DRAW);
        GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER,viewpoint.platform.Gl.BLOCK_PACK_OPTIONS,sentinelOptions,optionOffset,256);
        sentinelUniform=GL15.glGenBuffers();GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,sentinelUniform);
    }
    private static void checkState() {
        int binding=viewpoint.platform.Gl.BLOCK_PACK_OPTIONS;
        if(GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING)!=sentinelUniform
                || GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING,binding)!=sentinelOptions
                || GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START,binding)!=optionOffset
                || GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE,binding)!=256)
            throw new AssertionError("Water leaked uniform buffer binding/range");
        if(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE0+126 || GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)!=sentinelVao || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=0
                || !GL11.glIsEnabled(GL11.GL_DEPTH_TEST) || !GL11.glIsEnabled(GL11.GL_BLEND) || GL11.glGetInteger(GL11.GL_DEPTH_FUNC)!=GL11.GL_GREATER || !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)) throw new AssertionError("Water leaked render state");
        for(int unit:UNITS) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=sentinelTexture || GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY)!=sentinelArray || GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D)!=sentinelVolume || GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit)!=sentinelSampler) throw new AssertionError("Water leaked texture/sampler "+unit);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0+126);
    }
    private static float[] grid(boolean water) {
        float[] result=new float[8*8*4];
        for(int i=0;i<64;i++) { result[i*4]=water?1f:0; result[i*4+2]=5; result[i*4+3]=1; }
        return result;
    }
    private static void environment(boolean day) {
        frame.scene.sunTrueY=day?1f:-1f; frame.scene.sunY=1f; frame.scene.skySunY=1f;
        frame.scene.sunR=1;frame.scene.sunG=1;frame.scene.sunB=.95f;
        frame.scene.sunStrength=day?1f:.001f; frame.scene.skyGlow=day?1f:.001f; frame.scene.nightLight=.6666667f;
        GameAccess.call("viewpoint.environment.Atmosphere","skyColours",null,new Class<?>[]{viewpoint.render.SceneData.class,float.class,float.class,float.class},frame.scene,day?1f:0f,0f,0f);
        GameAccess.call("viewpoint.environment.Atmosphere","airLight",null,new Class<?>[]{viewpoint.render.SceneData.class},frame.scene);
        GameAccess.set("viewpoint.light.Daylight","fromAir",null,false); GameAccess.set("viewpoint.light.Daylight","level",null,day?0xCFBFAB:0x202028);
        viewpoint.light.Daylight.snapshot(frame.scene);
    }
    private static void setup() {
        color=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,W,H,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,H);
        fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("FBO incomplete");
    }
    private static void reset(float d,boolean black) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glStencilMask(255);GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(black?0:80f/255f,black?0:120f/255f,black?0:160f/255f,127f/255f);GL11.glClearDepth(d);GL11.glClearStencil(37);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);GL11.glViewport(X,Y,VW,VH);
    }
    private static byte[] image() {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);ByteBuffer data=BufferUtils.createByteBuffer(W*H*4);
        GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);byte[] result=new byte[data.remaining()];data.get(result);return result;
    }
    private static void near(byte[] a,byte[] b,String reason) { if(!java.util.Arrays.equals(a,b)) throw new AssertionError("Changed "+reason); }
    private static void nearViewport(byte[] a,byte[] b,String reason) { for(int y=Y;y<Y+VH;y++)for(int x=X;x<X+VW;x++)for(int c=0;c<4;c++)if(a[(y*W+x)*4+c]!=b[(y*W+x)*4+c])throw new AssertionError(reason); }
    private static void difference(byte[] a,byte[] b,String reason) { int sum=0;for(int i=0;i<a.length;i++) sum+=Math.abs((a[i]&255)-(b[i]&255));if(sum<30) throw new AssertionError("No effect: "+reason+" ("+sum+")"); }
    private static float average(byte[] pixels) { int sum=0;for(int y=Y;y<Y+VH;y++)for(int x=X;x<X+VW;x++)for(int c=0;c<3;c++)sum+=pixels[(y*W+x)*4+c]&255;return sum/(float)(VW*VH*3); }
    private static void invariants() {
        byte[] pixels=image();FloatBuffer depths=BufferUtils.createFloatBuffer(W*H);ByteBuffer stencil=BufferUtils.createByteBuffer(W*H);
        GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);GL11.glReadPixels(0,0,W,H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencil);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if(!java.util.Arrays.equals(viewport,new int[]{X,Y,VW,VH})) throw new AssertionError("Viewport changed");
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            int i=y*W+x;
            if((pixels[i*4+3]&255)!=127 || Math.abs(depths.get(i)-1)>1e-6 || (stencil.get(i)&255)!=37) throw new AssertionError("Alpha/depth/stencil changed");
            if(x<X||x>=X+VW||y<Y||y>=Y+VH) for(int c=0;c<3;c++)if((pixels[i*4+c]&255)!=new int[]{80,120,160}[c]) throw new AssertionError("Outside viewport changed");
        }
    }
}
