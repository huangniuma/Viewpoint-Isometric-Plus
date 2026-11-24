import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Actual native tree consumer -> production renderWorld -> selected VP finish, with a 074 late-pass control. */
@SuppressWarnings("unchecked")
public final class NativeTreeStyle075Probe {
    static final int W=256, H=256, X=7, Y=11, OW=W+19, OH=H+23;
    static final Class<?>[] NONE=new Class<?>[0];
    static String pack="library";
    static boolean baseline;
    static Method world;
    static Object renderer,bark,leaves,overlapTexture,invisibleTexture;
    static int output,color,depth;
    static long number;
    static int cases,treeSamples,leafSamples,barkSamples;
    static boolean overlap,neighbour,reverse;
    static int jitter;

    record Image(float[] pixels,float[] raw,boolean[] tree,int width,int height) {}

    public static void main(String[] args)throws Exception {
        baseline=args.length>0 && args[0].equals("baseline074");
        if(args.length>(baseline?1:0))pack=args[baseline?1:0];
        auditOrdering();
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(OW,OH,"native tree selected post 075",0,0);
        if(window==0)throw new AssertionError("Hidden GL context");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            Main.compatible=true;
            GlProgram.onUse(IsometricShaders::onUse);
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.ofEntries(
                    Map.entry("upscaling","off"),Map.entry("antialiasing","off"),Map.entry("renderScale","100"),
                    Map.entry("reflections","off"),Map.entry("volumetrics","false"),Map.entry("bloom","false"),
                    Map.entry("clouds","false"),Map.entry("bounceLight","false"),Map.entry("sunShafts","false"),
                    Map.entry("depthOfField","false"),Map.entry("motionBlur","false"),Map.entry("filmGrain","false")));
            fixture();
            try {world=NativeTrees.class.getMethod("renderWorld",Frame.class);}catch(NoSuchMethodException expected){if(!baseline)throw expected;}
            if(baseline && world!=null)throw new AssertionError("074 negative control loaded current NativeTrees");
            for(float yaw:new float[]{0,37,90,180,270}) {
                Image neutral=render(yaw,false,false,false,false,1);
                Image styled=render(yaw,false,true,false,false,1);
                Image fogged=render(yaw,true,false,false,false,1);
                double style=error(neutral,styled),fog=error(neutral,fogged);
                if(baseline) {
                    if(style>.012 || fog>.012)throw new AssertionError("074 late-tree bypass not reproduced: style="+style+" fog="+fog);
                } else {
                    // Actual selected material lighting can be much darker than the old raw RGB fixture.
                    if(style<.005 || fog<.03)throw new AssertionError("Tree bypasses selected post: "+pack+" yaw="+yaw+" style="+style+" fog="+fog);
                }
                Image opaqueStencil=render(yaw,false,false,true,false,1);
                Image faded=render(yaw,false,false,true,true,1);
                double alpha=error(opaqueStencil,faded);
                if(alpha<.025)throw new AssertionError("Native transparency/stencil pass is empty: "+alpha);
                // Both color layers must survive: leaves retain their tiny depth lead over bark.
                requireLayers(neutral);
                System.out.println("Tree075 pack="+pack+" yaw="+yaw+" style="+style+" fog="+fog+" stencilFade="+alpha);
            }
            if(!baseline) {
                // Native output and VP target can differ in size. Exercise the production stencil-only scale copy.
                Image scaled=render(0,false,false,true,false,.75f);
                if(scaled.width==W && scaled.height==H)throw new AssertionError("Render-scale fixture did not change the VP target");
                requireLayers(scaled);
                Image scaledFade=render(0,false,false,true,true,.75f);
                if(error(scaled,scaledFade)<.005)throw new AssertionError("Scaled stencil fade missing");
                checkOverlap();
            }
            if(treeSamples<1000 || !baseline && (leafSamples<100 || barkSamples<100))throw new AssertionError("Vacuous native trees");
            gl("completed");
            System.out.println((baseline?"Pinned074 late native trees negative control PASS":"Native tree style075 GPU PASS")
                    +": pack="+pack+" cases="+cases+" treeSamples="+treeSamples+" leafSamples="+leafSamples+" barkSamples="+barkSamples
                    +"; actual IsoTree/native record producer, actual woven FBORenderTrees + VBO shaders, "+(baseline?"074 late-pass ordering":"production renderWorld camera/stencil")+","
                    +" actual selected VP finish (including pack COMPOSITE/FINAL), fog/style on/off, zero/rotated yaw, alpha/leaf/bark, offset/scaled viewport,"
                    +" source native stencil preserved and "+(baseline?"074 late-draw bytecode confirmed":"bytecode prevents second late tree draw")+". Controlled scene, no live-map/FPS acceptance.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }

    static void fixture()throws Exception {
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);
        var af=NativeTreeProducerProbe.class.getDeclaredField("allocate");af.setAccessible(true);af.set(null,uf.getType().getMethod("allocateInstance",Class.class));
        var us=NativeTreeProducerProbe.class.getDeclaredField("unsafe");us.setAccessible(true);us.set(null,uf.get(null));
        NativeTreeRender055Probe.call("fixture",NONE);
        NativeTreeRender055Probe.template=NativeTreeRender055Probe.staticGet("square");
        renderer=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell","instance",null);
        NativeTreeRender055Probe.renderer=renderer;
        Object white=GameAccess.type("zombie.core.textures.ColorInfo").getConstructor(float.class,float.class,float.class,float.class).newInstance(1f,1f,1f,1f);
        Object colors=Array.newInstance(white.getClass(),4);for(int i=0;i<4;i++)Array.set(colors,i,white);
        NativeTreeRender055Probe.colors=colors;
        set(NativeTreeRender055Probe.staticGet("player"),"ecsComponentMap",new HashMap<>());
        Object cell=NativeTreeRender055Probe.fresh("zombie.iso.IsoCell");set(cell,"stencilAreas",new ArrayList<>());
        set(GameAccess.field("zombie.iso.IsoWorld","instance",null),"currentCell",cell);
        GL30.glBindVertexArray(GL30.glGenVertexArrays());zombie.core.VBO.GLVertexBufferObject.init();
        NativeTreeRender055Probe.setupNative();
        bark=NativeTreeRotation058Probe.texture("style075_bark",false);
        leaves=NativeTreeRotation058Probe.texture("style075_leaves",true);
        overlapTexture=NativeTreeRender055Probe.texture("style075_overlap");
        invisibleTexture=NativeTreeRender055Probe.texture("invisible");
        color=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,GL11.GL_FLOAT,OW,OH);
        depth=texture(GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8,OW,OH);
        output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Output FBO");
    }

    static Image render(float yaw,boolean fog,boolean style,boolean stencil,boolean faded,float scale)throws Exception {
        Frame frame=new Frame();frame.camX=11;frame.camY=13;frame.camZ=0;frame.number=++number;
        SceneData scene=frame.scene;scene.originX=-11;scene.originZ=-13;scene.cameraCut=true;
        scene.skyR=.3f;scene.skyG=.4f;scene.skyB=.5f;
        scene.fogR=.08f;scene.fogG=.2f;scene.fogB=.8f;scene.fogStart=fog?0:10000;scene.fogEnd=fog?.5f:20000;
        scene.sunY=scene.sunTrueY=scene.skySunY=1;scene.sunR=scene.sunG=scene.sunB=1;scene.sunStrength=0;
        scene.skyGlow=1;scene.nightLight=.6666667f;scene.shadowReach=64;
        Arrays.fill(scene.exposure,(byte)255);scene.exposureAX=scene.exposureAY=32;
        Matrix4f view=new Matrix4f();float[] eye=new float[3];
        IsoCameraMath.matrices(new IsoCameraMath.Fit(11,13,6f),11,13,W,H,scene.projection,view,eye,yaw);
        if(jitter!=0)scene.projection.m20(scene.projection.m20()+((jitter&1)==0?.375f:-.375f)*2f/W)
                .m21(scene.projection.m21()+((jitter&2)==0?.25f:-.25f)*2f/H);
        var snapshot=new RotationCamera.Snapshot(scene.projection,view,11,13,0,W,H,1f,yaw);
        NativeTrees tree=new NativeTrees();
        RotationCamera.Snapshot capturedRotation=yaw==0?null:snapshot;
        tree.rotation=capturedRotation;
        try {
            Object object=NativeTreeRender055Probe.tree("style075",overlap?11:10,12);
            // Yaw zero keeps the native predicate; rotated capture keeps its actual rotated sorting/predicate.
            if(overlap) {
                Object far=NativeTreeRender055Probe.tree("overlap_far",10,13);
                if(reverse){tree.add(get(object,"square"));tree.add(get(far,"square"));}
                else {tree.add(get(far,"square"));tree.add(get(object,"square"));}
                if(neighbour)tree.add(get(NativeTreeRender055Probe.tree("overlap_extra",12,11),"square"));
            } else tree.add(get(object,"square"));
            tree.capture(renderer,0);
            List<Object> records=(List<Object>)get(get(tree,"batch"),"trees");
            if(records.size()!=(overlap?(neighbour?3:2):1))throw new AssertionError("Native producer membership");
            for(Object r:records) {
                if(overlap) {
                    float x=((Number)get(r,"x")).floatValue();
                    set(r,"texture",x>12?invisibleTexture:overlapTexture);set(r,"texture2",null);
                    set(r,"r",x==11?.8f:0f);set(r,"g",0f);set(r,"b",x==12?.8f:0f);
                    set(r,"a",1f);set(r,"useStencil",false);set(r,"transparent",true);
                    set(r,"cutawayAlpha",.4f);set(r,"fadeAlpha",.4f);
                } else {
                    set(r,"texture",bark);set(r,"texture2",leaves);
                    set(r,"r",.8f);set(r,"g",.8f);set(r,"b",.8f);set(r,"a",1f);
                    set(r,"useStencil",stencil);set(r,"transparent",false);
                    set(r,"cutawayAlpha",faded?.35f:1f);set(r,"fadeAlpha",faded?.35f:1f);
                }
            }
            if(overlap)syntheticRecordColors(tree,records);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(X,Y,W,H);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glStencilMask(255);
            GL11.glClearColor(.09f,.13f,.17f,1);GL11.glClearDepth(1);GL11.glClearStencil(stencil?128:37);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
            float[] sourceDepth=readDepth(output,OW,OH);byte[] sourceStencil=readStencil(output,OW,OH);
            IsometricBridge.modelWorldRendering(frame,new IsometricEffects(false,0,1,64,256,style,1,1.6f,0,1.2f),false);
            Object pipeline=ShaderPacks.active().pipeline;
            Object oldScale=GameAccess.field("viewpoint.platform.Pipeline","renderScale",pipeline);
            GameAccess.set("viewpoint.platform.Pipeline","renderScale",pipeline,Math.round(scale*100));
            try {
                if(!WorldRenderer.begin(scene,frame.number,1f,view,eye[0],eye[1],eye[2]))throw new AssertionError("VP begin");
            } finally {GameAccess.set("viewpoint.platform.Pipeline","renderScale",pipeline,oldScale);}
            WorldRenderer.drawTranslucent(scene);WorldRenderer.drawWeather(scene,0,1.5f,0);
            FrameContext context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
            Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
            WorldRenderer.bindTarget();GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
            GL11.glClearColor(.09f,.13f,.17f,1);GL11.glClearDepth(1);GL11.glStencilMask(255);GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
            int vpDepth=((Number)GameAccess.field("viewpoint.render.Targets","depth",targets)).intValue();
            int hdrFbo=((Number)GameAccess.field("viewpoint.render.Targets","hdrFbo",targets)).intValue();
            if(!baseline) {
                world.invoke(tree,frame);
                if(tree.rotation!=capturedRotation)throw new AssertionError("Draw camera leaked into capture");
            }
            gl("native tree renderWorld");
            float[] raw=readColor(hdrFbo,context.width,context.height);
            boolean[] mask=new boolean[context.width*context.height];
            float[] visibleDepth=readDepth(hdrFbo,context.width,context.height);
            for(int i=0;i<mask.length;i++)mask[i]=visibleDepth[i]<.99999f;
            if(!baseline) {
                byte[] copied=readStencil(hdrFbo,context.width,context.height);
                for(byte b:copied)if((b&255)!=(stencil?128:37))throw new AssertionError("Stencil-only copy lost native stencil");
                int written=0;GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,vpDepth);
                FloatBuffer d=BufferUtils.createFloatBuffer(mask.length);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,d);
                for(int i=0;i<mask.length;i++)if(mask[i] && d.get(i)<1)written++;
                if(written<100)throw new AssertionError("Native tree did not enter VP world depth: "+written);
            }
            WorldRenderer.finish(scene);
            if(baseline) {
                // Execute the actual 074 late native consumer, with the same draw camera to isolate ordering.
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(X,Y,W,H);
                tree.rotation=snapshot;tree.render();tree.rotation=capturedRotation;
                raw=readColor(output,OW,OH);mask=new boolean[W*H];
                float[] treeDepth=readDepth(output,OW,OH);
                for(int y=0;y<H;y++)for(int x=0;x<W;x++){int a=(y+Y)*OW+x+X;mask[y*W+x]=treeDepth[a]<.99999f;}
            }
            gl("selected finish");
            if(!Arrays.equals(sourceStencil,readStencil(output,OW,OH)))throw new AssertionError("Native output stencil changed");
            if(!baseline && !Arrays.equals(sourceDepth,readDepth(output,OW,OH)))throw new AssertionError("Stencil blit changed native output depth");
            float[] image=readColor(output,OW,OH),crop=new float[W*H*4];
            for(int y=0;y<H;y++)System.arraycopy(image,((y+Y)*OW+X)*4,crop,y*W*4,W*4);
            if(!baseline && (context.width!=W || context.height!=H)) {
                boolean[] resized=new boolean[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++)resized[y*W+x]=mask[Math.min(context.height-1,y*context.height/H)*context.width+Math.min(context.width-1,x*context.width/W)];mask=resized;
            }
            int samples=0;for(boolean b:mask)if(b)samples++;treeSamples+=samples;
            if(samples<100)throw new AssertionError("Empty tree sample "+pack+" yaw="+yaw+" baseline="+baseline);
            if(Boolean.TRUE.equals(GameAccess.field("vpiso.IsometricBridge","failed",null)))throw new AssertionError("Native tree bridge failed");
            cases++;return new Image(crop,raw,mask,context.width,context.height);
        }finally{tree.release();IsometricBridge.rendering(null);}
    }

    static void requireLayers(Image image) {
        if(baseline)return;
        int red=0,green=0;for(int i=0;i<image.raw.length/4;i++) {
            if(image.raw[i*4]>.001f && image.raw[i*4+1]<image.raw[i*4]*.4f)red++;
            if(image.raw[i*4+1]>.001f && image.raw[i*4]<image.raw[i*4+1]*.4f)green++;
        }
        if(red<100 || green<100)throw new AssertionError("Leaf/bark depth loss red="+red+" green="+green);
        barkSamples+=red;leafSamples+=green;
    }
    static void checkOverlap()throws Exception {
        overlap=true;int totalBoth=0;
        try {
            for(float yaw:new float[]{0,37,90})for(int variation=0;variation<4;variation++) {
                neighbour=(variation&1)!=0;reverse=(variation&2)!=0;jitter=variation+1;
                Image image=render(yaw,false,false,false,true,1);
                int both=0,red=0,blue=0;
                for(int i=0;i<image.raw.length/4;i++) {
                    float r=image.raw[i*4],b=image.raw[i*4+2];
                    if(r>.16f && b>.22f)both++;
                    if(r>.25f)red++;if(b>.3f)blue++;
                }
                if(both<100 || red<100 || blue<100)throw new AssertionError("Direct-world overlapping crowns lost: yaw="+yaw+" variation="+variation+" both="+both+" red="+red+" blue="+blue);
                totalBoth+=both;
            }
        } finally {overlap=neighbour=reverse=false;jitter=0;}
        System.out.println("Tree075 direct-world overlapping native trees PASS: 12 yaw/membership/input-order/jitter cases; blended dual-tree pixels="+totalBoth);
    }
    static double error(Image a,Image b) {
        double error=0;int count=0;
        for(int i=0;i<a.tree.length;i++)if(a.tree[i] && b.tree[i]) {
            for(int c=0;c<3;c++)error+=Math.abs(a.pixels[i*4+c]-b.pixels[i*4+c]);count+=3;
        }
        if(count<300)throw new AssertionError("Empty paired tree mask");return error/count;
    }
    /** Ownership fixtures encode identities in record RGB; use the preserved highlight colour path. */
    static void syntheticRecordColors(NativeTrees tree,List<?> records)throws Exception {
        try {((Set<Object>)get(tree,"highlighted")).addAll(records);}
        catch(NoSuchFieldException historical) { }
    }
    static void auditOrdering()throws Exception {
        List<String> draw=new ArrayList<>(),queue=new ArrayList<>(),release=new ArrayList<>();
        for(String name:List.of("vpiso/IsoDrawer","vpiso/IsometricBridge"))try(var in=ClassLoader.getSystemResourceAsStream(name+".class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] ex) {
                    return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String called,String descriptor,boolean itf) {
                        if(name.endsWith("IsoDrawer")&&method.equals("render")&&(owner.equals("vpiso/NativeTrees")||owner.equals("viewpoint/render/WorldRenderer")))draw.add(owner+"."+called);
                        if(name.endsWith("IsometricBridge")&&owner.equals("vpiso/NativeTrees")&&called.equals("queue"))queue.add(called);
                        if(name.endsWith("IsoDrawer")&&method.equals("postRender")&&owner.equals("vpiso/NativeTrees")&&called.equals("release"))release.add(called);
                    }};
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        int at=draw.indexOf("vpiso/NativeTrees.renderWorld"),end=draw.indexOf("viewpoint/render/WorldRenderer.finish");
        if(baseline) {if(at>=0 || queue.size()!=1)throw new AssertionError("Not actual074 ordering");}
        else if(at<0 || at>end || Collections.frequency(draw,"vpiso/NativeTrees.renderWorld")!=1 || !queue.isEmpty() || release.size()!=1)
            throw new AssertionError("Second late tree draw / bad lifetime/order: "+draw+" queue="+queue+" release="+release);
    }
    static int texture(int internal,int format,int type,int w,int h){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,type,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    static float[] readColor(int fbo,int w,int h){GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);FloatBuffer b=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,b);float[] a=new float[b.capacity()];b.get(a);for(float v:a)if(!Float.isFinite(v))throw new AssertionError("Nonfinite tree/color");return a;}
    static float[] readDepth(int fbo,int w,int h){GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);FloatBuffer b=BufferUtils.createFloatBuffer(w*h);GL11.glReadPixels(0,0,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,b);float[] a=new float[b.capacity()];b.get(a);return a;}
    static byte[] readStencil(int fbo,int w,int h){GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);ByteBuffer b=BufferUtils.createByteBuffer(w*h);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,w,h,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,b);byte[] a=new byte[b.capacity()];b.get(a);return a;}
    static Object get(Object o,String n)throws Exception{return NativeTreeRender055Probe.get(o,n);}
    static void set(Object o,String n,Object v)throws Exception{NativeTreeRender055Probe.set(o,n,v);}
    static void gl(String at){int e=GL11.glGetError();if(e!=0)throw new AssertionError("GL "+Integer.toHexString(e)+" at "+at);}
}
