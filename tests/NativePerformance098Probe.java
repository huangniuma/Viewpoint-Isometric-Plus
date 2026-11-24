import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.*;
import vpiso.*;

/** Same captured inputs, real selected-pack lighting/maps, alternating GPU queries. No game attach. */
public final class NativePerformance098Probe {
    static final NativeShadow068Probe P=NativeTable088Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    static final int W=1280,H=720;
    static GlProgram previous,current;
    static int target,ground,metadata,depth,base,validity;
    static Frame frame;
    static final IsometricEffects effects=new IsometricEffects(true,1,1,64,1024,true,.7f,1,1,1);
    static final Matrix4f[] matrices={new Matrix4f(),new Matrix4f(),new Matrix4f()};
    static final float[] texels=new float[3];
    static int count,map;
    static float range;
    static boolean measuring;
    static final List<Integer> queries=new ArrayList<>();
    public static void main(String[] args)throws Exception {
        P.pack=args[0];
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,H,"098 GPU parity and timing",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            System.out.println("GPU098 renderer="+GL11.glGetString(GL11.GL_RENDERER));
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            boolean cold=args.length>2 && args[2].equals("compile-only");
            String salt=cold?"\n//098 cold compile control "+System.nanoTime()+"\n":"";
            System.out.println("GPU098 compiling baseline097");long compileAt=System.nanoTime();
            previous=compile(oldFragment(args[1])+salt);System.out.println("GPU098 baseline compile_ms="+(System.nanoTime()-compileAt)/1e6);
            System.out.println("GPU098 compiling candidate098");compileAt=System.nanoTime();
            current=compile(NativeOverlay.FRAGMENT+salt);System.out.println("GPU098 candidate compile_ms="+(System.nanoTime()-compileAt)/1e6);
            if(cold){System.out.println("GPU098 cold compile PASS: "+P.pack+"; identical fresh comment salt, no disk-cache comparison");return;}
            N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,P.NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);
            N.overlay.metadata(P.metadata);
            target=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);
            attach(0,P.texture(GL11.GL_RGBA8,W,H));metadata=P.texture(GL30.GL_RGBA32F,W,H);attach(6,metadata);
            ground=P.texture(GL11.GL_RGBA8,W,H);attach(7,ground);
            depth=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH24_STENCIL8,W,H,0,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8,0L);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            N.require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"high FBO");
            base=P.texture(GL30.GL_RGBA16F,W,H);validity=P.texture(GL30.GL_R16F,W,H);
            int registry=NativeWallSources071Probe.fbo();NativeGround.begin(true);
            try {
                for(String kind:new String[]{"floor","relight","table","source","outside-range"})run(kind,registry);
            } finally {NativeGround.finish();NativeGround.release(registry);}
            N.require(GL11.glGetError()==0,"GL error final");
            System.out.println("Native performance098 GPU PASS: pack="+P.pack+"; identical captured inputs, full-resolution selected lighting, alpha/depth/stencil and original097 comparison; timings exclude capture/maps, not whole-game FPS.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void run(String kind,int registry)throws Exception {
        frame=P.frame();N.fit(0);
        Object surface;
        float[][] art;float[] owner={0,0,0,1};float share=1;
        if(kind.equals("table")) {
            Path file=NativeTable088Probe.ROOT.resolve(NativeTable088Probe.FILES[0]);
            PackModel model=PackModels.of(file.toFile());
            PackData data=(PackData)GameAccess.call("viewpoint.packs.PackLoads","load",null,new Class<?>[]{java.io.File.class,Path.class},file.toFile(),NativeTable088Probe.ROOT);
            PackModels.onItsWay();model.read(data);PackModels.publish();
            N.receiver.fit(new double[][]{{.5,.3,.5},{.82,.14,.499},{.18,.14,.499},{.5,.94,.499}});
            surface=NativeTable088Probe.mesh(frame,model);art=NativeTable088Probe.top(model.bounds(),NativeTable088Probe.TOP[0]);share=0;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
            NativeGround.sourceBegin(new NativeSource(0,0,0,5,model.bounds()).withOwner(NativeSource.ownerKey(0,0,0,0)));
            int id=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();owner=new float[]{-2,id,-id*id,1};
        } else {
            art=N.horizontal(kind.equals("source")?.8f:0,40);
            surface=N.mesh(frame.scene,0,kind.equals("source")?NativeSource073Probe.box(.7f,1.8f):art);P.publish(surface,true,0);
            if(kind.equals("source")) {
                share=0;GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
                NativeGround.sourceBegin(new NativeSource(3,3,0,3,new float[]{-.7f,0,-.7f,.7f,1.8f,.7f}));
                int id=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();owner=new float[]{-2,id,-id*id,1};
            }
        }
        Object caster=N.mesh(frame.scene,1,P.blocker());
        for(int i=0;i<4;i++)P.draw(frame,art,.0007f,owner,share,true);
        // Upscale the same native color/depth/metadata inputs before either program.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,N.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target);
        for(int attachment:new int[]{0,6,7}) {
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+attachment);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0+attachment);
            GL30.glBlitFramebuffer(0,0,N.W,N.H,0,0,W,H,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        }
        GL30.glBlitFramebuffer(0,0,N.W,N.H,0,0,W,H,GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT,GL11.GL_NEAREST);
        N.overlay.ground(ground);N.overlay.metadata(metadata);N.overlay.relight(0,0,null);
        if(kind.equals("relight")) {
            NativeRelightMask mask=new NativeRelightMask();mask.x=mask.y=-32;mask.z=0;
            Arrays.fill(mask.pixels,(byte)255);Arrays.fill(mask.exterior,(byte)255);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);
            GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{0,0,0,1});
            clearTexture(base,new float[]{.7f,.55f,.4f,1});clearTexture(validity,new float[]{1,1,1,1});
            N.overlay.relight(base,validity,mask);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,W,H);
        N.overlay.capture(true);IsometricBridge.rendering(frame);
        try {NativeShadowMaps.draw(frame.scene,1024,2,N.overlay.surfaces(N.receiver));}finally{IsometricBridge.rendering(null);}
        count=NativeShadowMaps.count();map=NativeShadowMaps.texture();range=NativeShadowMaps.depthRange();
        for(int i=0;i<count;i++){matrices[i].set(NativeShadowMaps.matrix(i));texels[i]=2*NativeShadowMaps.reach(i)/1024;}
        if(kind.equals("outside-range"))N.receiver.origin[0]+=200;
        draw(previous);byte[] old=read();float[] oldDepth=new float[W*H];byte[] oldStencil=new byte[W*H];
        GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,oldDepth);
        ByteBuffer stencil=BufferUtils.createByteBuffer(W*H);GL11.glReadPixels(0,0,W,H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencil);stencil.get(oldStencil);
        draw(current);byte[] now=read();float[] newDepth=new float[W*H];
        GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,newDepth);
        stencil.clear();GL11.glReadPixels(0,0,W,H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencil);
        N.require(Arrays.equals(oldDepth,newDepth),"depth parity");for(int i=0;i<oldStencil.length;i++)N.require(oldStencil[i]==stencil.get(i),"stencil parity");
        int changed=0,max=0;
        for(int i=0;i<old.length;i++) {int diff=Math.abs((old[i]&255)-(now[i]&255));max=Math.max(max,diff);if(diff>1)changed++;if(i%4==3)N.require(diff==0,"alpha");}
        N.require(changed==0,"097 pixel parity "+kind+" changed="+changed+" max="+max);
        for(int i=0;i<16;i++){draw(previous);draw(current);}GL11.glFinish();
        double[] a=new double[9],b=new double[9];
        for(int i=0;i<a.length;i++) {
            if(i%2==0){a[i]=time(previous);b[i]=time(current);}else{b[i]=time(current);a[i]=time(previous);}
        }
        Arrays.sort(a);Arrays.sort(b);
        System.out.printf(Locale.ROOT,"GPU098 pack=%s case=%s %dx%d old_ms=%.4f new_ms=%.4f saving_pct=%.2f maxPixelDelta=%d%n",P.pack,kind,W,H,a[4],b[4],100*(a[4]-b[4])/a[4],max);
        N.require(GL11.glGetError()==0,"GL error "+kind);N.retire(caster);N.retire(surface);
    }
    static void draw(GlProgram program) {
        GameAccess.set("vpiso.NativeOverlay","program",N.overlay,program);GameAccess.set("vpiso.NativeOverlay","programPack",N.overlay,GlProgram.pack());
        N.overlay.draw(N.receiver,effects,map,matrices,texels,range,1024,count,frame.scene.sunStrength);
    }
    static double time(GlProgram program) {
        measuring=true;
        try {for(int i=0;i<16;i++)draw(program);}finally{measuring=false;}
        N.require(queries.size()==16,"Isolated draw timer weave missing");
        long ns=0;for(int query:queries){ns+=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT);GL15.glDeleteQueries(query);}
        queries.clear();return ns/(16e6*16);
    }
    public static void timedDraw(int mode,int first,int length) {
        if(!measuring){GL11.glDrawArrays(mode,first,length);return;}
        int query=GL15.glGenQueries();GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
        // Keep the GPU busy inside each query instead of allowing Java state
        // submission gaps to change its clocks between tiny old/new draws.
        for(int i=0;i<16;i++)GL11.glDrawArrays(mode,first,length);
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);queries.add(query);
    }
    static byte[] read() {ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] out=new byte[pixels.remaining()];pixels.get(out);return out;}
    static void attach(int slot,int texture){GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL11.GL_TEXTURE_2D,texture,0);}
    static void clearTexture(int texture,float[] color) {GL44.glClearTexImage(texture,0,GL11.GL_RGBA,GL11.GL_FLOAT,color);}
    static GlProgram compile(String fragment){return GlProgram.fromSources("098 parity",new GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,NativeLighting.fragment(fragment)),Map.of(),Map.of(),List.of("098"));}
    static String oldFragment(String jar)throws Exception {
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(jar).toUri().toURL()},NativePerformance098Probe.class.getClassLoader()) {
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.equals("vpiso.NativeOverlay"))return super.loadClass(name,resolve);
                Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
            }
        }) {return (String)loader.loadClass("vpiso.NativeOverlay").getField("FRAGMENT").get(null);}
    }
}
