import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.Settings;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FarRelief;
import vpiso.*;

/** Actual Relief advice plus real VP terrain/cascade shaders over a flat native receiver. */
public final class NativeShadowReliefProbe {
    private static final int SIDE = 96;
    private static Method relief;
    private static ThreadLocal<Boolean> scope;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Main.compatible = true;
        scope = (ThreadLocal<Boolean>) GameAccess.field("vpiso.IsometricBridge", "NATIVE_SHADOW_CAPTURE", null);
        relief = wovenRelief();
        checkScopes();
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(SIDE, SIDE, "VPISO native terrain shadow regression", 0, 0);
        require(window != 0, "Hidden GL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null, new Class<?>[]{java.io.File.class}, (Object) null);
            ShaderPacks.useBuiltIn("library", "high", Map.of("upscaling", "off"));
            checkTerrain();
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }

    @SuppressWarnings("unchecked")
    private static void checkScopes() throws Exception {
        Frame frame = new Frame();
        FarRelief.Lift lift = new FarRelief.Lift(null, .5f, 10f, 128d, 128d);
        frame.scene.relief(lift);
        // Execute the actual original body outside the owned isometric capture scope.
        relief.invoke(null, frame);
        require(frame.scene.relief == lift, "Ordinary VP relief was changed");
        var capture = (ThreadLocal<Boolean>) GameAccess.field("vpiso.IsometricBridge", "CAPTURING", null);
        capture.set(true);
        try {
            relief.invoke(null, frame);
            require(frame.scene.relief == null && GameAccess.field("viewpoint.render.ModelDraws", "relief", frame.scene.models) == null,
                    "Isometric model/native layers use different terrain heights");
            Main.compatible=false;frame.scene.relief(lift);relief.invoke(null,frame);
            require(frame.scene.relief==lift,"Unsupported model capture was changed");
        }
        finally { Main.compatible=true;capture.remove(); }
        frame.scene.relief(lift);
        scope.set(true);
        try {
            relief.invoke(null, frame);
            require(frame.scene.relief == null && GameAccess.field("viewpoint.render.ModelDraws", "relief", frame.scene.models) == null,
                    "Native scene/model relief was retained");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread other = new Thread(() -> {
                try { require(!IsometricBridge.nativeShadowCapture(), "Native capture scope crossed threads"); }
                catch (Throwable error) { failure.set(error); }
            });
            other.start(); other.join();
            if (failure.get() != null) throw new AssertionError(failure.get());
            Main.compatible = false; frame.scene.relief(lift);
            relief.invoke(null, frame);
            require(frame.scene.relief == lift, "Unsupported VP fingerprint changed");
        } finally { Main.compatible = true; scope.remove(); }
        try { relief.invoke(null, (Frame) null); throw new AssertionError("Original Relief.frame was skipped"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof NullPointerException, "Unexpected original failure"); }
        boolean rooms = Settings.rooms;
        Method captureMethod = IsometricBridge.class.getDeclaredMethod("captureShadows", Frame.class, Object.class, Object.class, int.class, float.class);
        captureMethod.setAccessible(true);
        try { captureMethod.invoke(null, new Frame(), null, null, 0, 64f); throw new AssertionError("Missing environment unexpectedly captured"); }
        catch (InvocationTargetException expected) { /* Intentional unavailable-world fixture. */ }
        require(!IsometricBridge.nativeShadowCapture() && !capture.get() && Settings.rooms == rooms, "Capture exception leaked scope/settings");
        System.out.println("Native relief scope PASS: actual woven Relief.frame; scene and model relief cleared for supported isometric model/native-shadow capture; ordinary VP, other thread and unsupported target preserved; original body and capture finally executed.");
    }

    private static void checkTerrain() throws Exception {
        float[] heights = new float[96 * 96];
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) heights[y * 96 + x] = 10f + x * .18f + y * .12f;
        FarRelief.Field terrain = FarRelief.Field.of(96, 96, heights, heights);
        Frame frame = new Frame();
        frame.scene.sunX = .6f; frame.scene.sunY = .8f; frame.scene.sunStrength = 1f;
        frame.scene.sunR=1f;frame.scene.sunG=.92f;frame.scene.sunB=.8f;
        frame.scene.skyR=.35f;frame.scene.skyG=.4f;frame.scene.skyB=.45f;
        frame.scene.fogR=.6f;frame.scene.fogG=.7f;frame.scene.fogB=.8f;
        frame.scene.zenithR=.4f;frame.scene.zenithG=.55f;frame.scene.zenithB=.8f;
        frame.scene.sunTrueX=frame.scene.skySunX=.6f;frame.scene.sunTrueY=frame.scene.skySunY=.8f;
        frame.scene.sunSine=.8f;frame.scene.dayFraction=.5f;frame.scene.eyeSky=1f;
        frame.scene.shadowReach = 64f;
        Object floor = mesh(frame, 0, 0f, 32f);
        publishLight(floor);
        // Retain the original40x40 native observation plane, with actual light pages
        // for every world chunk it crosses. Empty helper geometry cannot cast shadows.
        java.util.List<Object> lightPages=new java.util.ArrayList<>();
        for(int cy=13;cy<=19;cy++)for(int cx=13;cx<=19;cx++) {
            int index=frame.scene.meshCount;Object page=mesh(frame,index,-100f,0f);
            frame.scene.meshFlags[index]=0;publishLight(page);lightPages.add(page);
        }
        NativeReceiver receiver = new NativeReceiver();
        receiver.worldZ=0f;
        receiver.fit(new double[][]{{.5,.5,.5},{.5+1.0/40,.5,.5},{.5,.5+1.0/40,.5},{.5,.5,.51}});
        NativeOverlay overlay = new NativeOverlay();
        int fbo = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        int color = texture(), ground = texture();
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT7, GL11.GL_TEXTURE_2D, ground, 0);
        int metadata=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,metadata);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,SIDE,SIDE,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,metadata,0);
        overlay.metadata(metadata);
        int depth = GL30.glGenRenderbuffers(); GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, SIDE, SIDE);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
        require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Receiver FBO incomplete");
        int bad = 0, flat = 0, real = 0;
        for (int step = 0; step < 12; step++) {
            double x = 128 + step * .25, y = 128 - step * .3;
            frame.scene.originX = -x; frame.scene.originZ = -y;
            int index=1;
            for(int cy=13;cy<=19;cy++)for(int cx=13;cx<=19;cx++,index++) {
                frame.scene.meshOffsets[index*3]=(float)(x-cx*8);
                frame.scene.meshOffsets[index*3+2]=(float)(y-cy*8);
            }
            FarRelief.Lift lift = new FarRelief.Lift(terrain, .5f, terrain.metresAt(x, y), x, y);
            frame.scene.relief(lift); frame.scene.cameraCut = true;
            bad += draw(frame, overlay, receiver, fbo, ground);
            scope.set(true);
            try { relief.invoke(null, frame); } finally { scope.remove(); }
            require(frame.scene.relief == null, "Terrain remained on native shadow scene");
            flat += draw(frame, overlay, receiver, fbo, ground);
        }
        Object caster = mesh(frame, frame.scene.meshCount, 3f, 2f);
        for (int step = 0; step < 4; step++) real += draw(frame, overlay, receiver, fbo, ground);
        require(bad > SIDE * SIDE, "Baseline did not reproduce terrain shadows on flat ground: " + bad);
        require(flat == 0, "Flat native floor still self-shadowed: " + flat);
        require(real > 10 && real < SIDE * SIDE, "Real elevated caster shadows were lost or flooded the image: " + real);
        GameAccess.call("viewpoint.render.ChunkMeshData", "retire", floor, new Class<?>[0]);
        GameAccess.call("viewpoint.render.ChunkMeshData", "retire", caster, new Class<?>[0]);
        for(Object page:lightPages)GameAccess.call("viewpoint.render.ChunkMeshData","retire",page,new Class<?>[0]);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Terrain shadow probe GL error");
        System.out.println("Native terrain shadow GPU PASS: 12 moving frames; real VP relief/cascade shaders and production native compositor; erroneous flat-ground shadow pixels=" + bad
                + ", corrected=" + flat + ", real elevated-caster pixels=" + real + "; no game/map or personal settings writes.");
    }

    private static int draw(Frame frame, NativeOverlay overlay, NativeReceiver receiver, int fbo, int ground) {
        frame.number++;
        IsometricBridge.rendering(frame);
        try { require(NativeShadowMaps.draw(frame.scene, 512, 2), "Actual shadow producer unavailable"); }
        finally { IsometricBridge.rendering(null); }
        Matrix4f[] matrices = {new Matrix4f(NativeShadowMaps.matrix(0)), new Matrix4f(NativeShadowMaps.matrix(1)), new Matrix4f()};
        float[] texels = {2f * NativeShadowMaps.reach(0) / 512, 2f * NativeShadowMaps.reach(1) / 512, 0f};
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo); GL11.glViewport(0, 0, SIDE, SIDE);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL11.GL_NONE,GL11.GL_NONE,GL11.GL_NONE,GL11.GL_NONE,GL11.GL_NONE,GL30.GL_COLOR_ATTACHMENT6,GL30.GL_COLOR_ATTACHMENT7});
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDepthMask(true); GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(180f/255f,180f/255f,180f/255f,1f); GL11.glClearDepth(.5);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        // Exact fixture floor ownership: no foreground coverage, level0 first/second
        // moments0, coverage1. This describes the known native plane, not guessed VP depth.
        GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{0,0,0,1});
        overlay.ground(ground); overlay.capture(true);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        overlay.draw(receiver, new IsometricEffects(true,.55f,1f,64f,512,false,0f,1f,1f,1f), NativeShadowMaps.texture(),
                matrices, texels, NativeShadowMaps.depthRange(), 512, 2, 1f);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(SIDE * SIDE * 4);
        GL11.glReadPixels(0,0,SIDE,SIDE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        int dark = 0;
        for (int i = 0; i < SIDE * SIDE; i++) if ((pixels.get(i * 4) & 255) < 170) dark++;
        return dark;
    }

    private static Object mesh(Frame frame, int index, float height, float half) throws Exception {
        float[][] points={{-half,height,-half},{half,height,-half},{half,height,half},{-half,height,-half},{half,height,half},{-half,height,half}};
        float[] vertices=new float[6*14];
        for(int i=0;i<6;i++) { System.arraycopy(points[i],0,vertices,i*14,3); vertices[i*14+7]=vertices[i*14+8]=1f; vertices[i*14+10]=1f; vertices[i*14+13]=Float.intBitsToFloat(64); }
        Object pages=java.lang.reflect.Array.newInstance(GameAccess.type("zombie.core.textures.TextureID"),1);
        Object result=GameAccess.type("viewpoint.render.ChunkMeshData").getConstructor(pages.getClass(),int[].class,int[].class,float[].class,int.class)
                .newInstance(pages,new int[]{0},new int[]{6},vertices,0);
        ((Object[])GameAccess.field("viewpoint.render.SceneData","meshes",frame.scene))[index]=result;
        frame.scene.meshCount=index+1; frame.scene.meshFlags[index]=2|8|16;
        frame.scene.meshFade[index*2]=0f; frame.scene.meshFade[index*2+1]=2f;
        return result;
    }
    private static void publishLight(Object mesh) {
        ByteBuffer light=(ByteBuffer)GameAccess.call("viewpoint.render.ChunkMeshData","spareLight",mesh,new Class<?>[0]);
        light.clear();for(int i=0;i<800;i++)light.put((byte)(i<400?180:0)).put((byte)180).put((byte)180).put((byte)255);
        light.flip();GameAccess.call("viewpoint.render.ChunkMeshData","publishLight",mesh,new Class<?>[]{ByteBuffer.class},light);
    }
    private static int texture() {
        int id=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,SIDE,SIDE,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST); return id;
    }
    private static Method wovenRelief() throws Exception {
        Method convert=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class); convert.setAccessible(true);
        byte[] adviceBytes;
        try(var input=vpiso.Patch_NativeShadowRelief.class.getResourceAsStream("Patch_NativeShadowRelief.class")) {
            adviceBytes=input.readAllBytes();
        }
        byte[] bytes=(byte[])convert.invoke(null,(Object)adviceBytes);
        var converted=Map.of("vpiso.Patch_NativeShadowRelief",bytes);
        var locator=new net.bytebuddy.dynamic.ClassFileLocator.Compound(new net.bytebuddy.dynamic.ClassFileLocator.Simple(converted),net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.ofSystemLoader());
        Class<?> loaderType=Class.forName("PatchWeavingProbe$BytesLoader");
        var ctor=loaderType.getDeclaredConstructor(Map.class,boolean.class); ctor.setAccessible(true);
        ClassLoader advice=(ClassLoader)ctor.newInstance(converted,false);
        var target=net.bytebuddy.pool.TypePool.Default.of(locator).describe("viewpoint.far.Relief").resolve();
        byte[] woven=new net.bytebuddy.ByteBuddy().redefine(target,locator).visit(net.bytebuddy.asm.Advice.to(advice.loadClass("vpiso.Patch_NativeShadowRelief"),locator)
                .on(net.bytebuddy.matcher.ElementMatchers.named("frame"))).make().getBytes();
        // VP's far package contains package-private helpers: retain one runtime package.
        Map<String,byte[]> targets=new java.util.HashMap<>();
        var connection=(java.net.JarURLConnection)ClassLoader.getSystemResource("viewpoint/far/Relief.class").openConnection();
        try(var jar=connection.getJarFile()) {
            for(var entry:jar.stream().filter(e->e.getName().startsWith("viewpoint/far/") && e.getName().endsWith(".class")).toList())
                targets.put(entry.getName().replace('/','.').replaceAll("\\.class$",""),jar.getInputStream(entry).readAllBytes());
        }
        targets.put("viewpoint.far.Relief",woven);
        ClassLoader loader=(ClassLoader)ctor.newInstance(targets,false);
        return loader.loadClass("viewpoint.far.Relief").getMethod("frame",Frame.class);
    }
    private static void require(boolean okay,String message) { if(!okay) throw new AssertionError(message); }
}
