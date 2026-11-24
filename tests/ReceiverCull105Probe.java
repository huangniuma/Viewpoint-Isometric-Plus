import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Production-woven cook/collect; full receiver and final-image parity with filtering disabled. */
public final class ReceiverCull105Probe {
    static final NativeShadow068Probe P=NativePlants072Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    public static int primitives;
    static int cases,saved,total;
    static Map<ChunkMeshData,Object> bounds;
    static float[][] art=N.horizontal(0,2.3f);
    record Image(byte[] color,float[] pos,float[] normal,float[] solar,float[] depth,float[] shadows,int primitives) {}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(N.W,N.H,"105 exact receiver cull",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            P.pack=args[0];TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            bounds=(Map<ChunkMeshData,Object>)GameAccess.field("vpiso.NativeReceiverCull","BOUNDS",null);
            N.fixture();P.nativeProgram=NativeGroundProbe.link(N.VERT,P.NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            Frame f=P.frame();f.scene.originX=f.scene.originY=f.scene.originZ=0;
            ArrayList<ChunkMeshData> meshes=new ArrayList<>();
            meshes.add(mesh(f,N.horizontal(0,2.3f)));
            // This roof is outside the close camera but casts into its visible centre.
            float[][] roof=N.horizontal(8,.55f);for(float[] p:roof)p[0]-=6;
            meshes.add(mesh(f,roof));
            for(int i=0;i<12;i++) {
                float[][] q=N.vertical(.4f,3);for(float[] p:q){p[0]+=24+i;p[2]-=24+i;}
                meshes.add(mesh(f,q));
            }
            // Real Cook produces a mesh with offscreen ordinary triangles and an onscreen tree.
            LightweightShadow082Probe.page=meshes.get(0).pages[0];Object recipe=LightweightShadow082Probe.recipe();
            Object batch=LightweightShadow082Probe.batch(recipe);LightweightShadow082Probe.raw(batch,40,0,1);
            LightweightShadow082Probe.plant(batch,0,2);
            List<?> ops=(List<?>)GameAccess.field("viewpoint.world.Recipe$Batch","ops",batch);
            GameAccess.set("viewpoint.world.Recipe$Op","face",ops.get(ops.size()-1),3);
            ChunkMeshData mixed=LightweightShadow082Probe.cook(recipe);
            install(f,mixed);meshes.add(mixed);N.require(bounds.containsKey(mixed),"Production Cook did not publish bounds");
            N.addModel(f.scene,N.horizontal(.7f,.3f),true,false);N.addModel(f.scene,N.horizontal(.4f,.2f),false,true);
            Map<ChunkMeshData,Object> known=new WeakHashMap<>(bounds);
            f.scene.sunX=-.6f;f.scene.sunY=.8f;f.scene.sunZ=0;
            int visibleLeaves=0;
            for(int zoom:new int[]{25,50,100,150,250})for(int yaw=0;yaw<3;yaw++) {
                fit(zoom,yaw,0);Image before=draw(f,known,false);Image after=draw(f,known,true);
                same(before,after);N.require(after.primitives<=before.primitives,"Submission grew");
                saved+=before.primitives-after.primitives;total+=before.primitives;cases++;
                for(int i=3;i<after.pos.length;i+=4)if(Math.abs(after.pos[i])>4.5)visibleLeaves++;
                fit(zoom,yaw,.00011);same(draw(f,known,false),draw(f,known,true));cases++;
            }
            // Force native tree artwork depth, instead of a ground-only depth image.
            Frame tree=P.frame();tree.scene.originX=tree.scene.originY=tree.scene.originZ=0;install(tree,mixed);
            NativePlants072Probe.half=.7f;NativePlants072Probe.height=3;NativePlants072Probe.angle=0;
            NativePlants072Probe.fit(false,0);art=NativePlants072Probe.billboard();
            Image ta=draw(tree,known,false),tb=draw(tree,known,true);same(ta,tb);cases++;
            for(int i=3;i<tb.pos.length;i+=4)if(Math.abs(tb.pos[i])>4.5)visibleLeaves++;
            N.require(tb.primitives<ta.primitives,"Mixed chunk ordinary triangles were not excluded");
            N.require(saved>0 && visibleLeaves>100,"Empty culling or mixed-tree discriminator: "+saved+"/"+visibleLeaves);
            // Exact screen-edge intersection, malformed/unknown provider and relief fallback.
            guardTests(f,meshes.get(0),known);
            N.require(!NativeReceiverCull.skip(f.scene,0),"Receiver scope leaked to shadow passes");
            for(ChunkMeshData m:meshes)N.retire(m);
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println("Receiver cull105 GPU PASS pack="+P.pack+" cases="+cases+" receiverPrimitives="+total+" saved="+saved+" leafPixels="+visibleLeaves+"; exact positions/normals/solar/depth/finalRGBA/shadow maps, 25-250 zoom/rotations/subpixels, production mixed Cook and actor/vehicle paths; no live FPS claim.");
        } finally {NativeReceiverCull.restore(null);IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static ChunkMeshData mesh(Frame f,float[][] p)throws Exception {
        ChunkMeshData m=(ChunkMeshData)N.mesh(f.scene,f.scene.meshCount,p);NativeReceiverCull.cooked(m);return m;
    }
    static void install(Frame f,ChunkMeshData m) {
        int i=f.scene.meshCount++;f.scene.meshes[i]=m;f.scene.meshFlags[i]=2|8|16;f.scene.meshFade[i*2+1]=2;
    }
    static void fit(int zoom,int yaw,double shift) {
        double theta=yaw*.65+Math.PI*.75,s=.12*100/zoom,ax=Math.cos(theta),az=Math.sin(theta);
        N.receiver.fit(new double[][]{{.5+shift,.32,.5},{.5+shift-ax*s,.32-az*s*.5,.5-az*.001},
                {.5+shift-az*s,.32+ax*s*.5,.5+ax*.001},{.5+shift,.32+2.4494896*s*.9,.5-2.4494896*.0012}});
    }
    static Image draw(Frame f,Map<ChunkMeshData,Object> known,boolean filter)throws Exception {
        bounds.clear();if(filter)bounds.putAll(known);NativePlants072Probe.invalidate();
        P.draw(f,art,0,new float[]{0,0,0,1},1,true);
        NativeSurfaces surfaces=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
        float[] depth=new float[N.W*N.H];GL45.glGetTextureImage(((Number)GameAccess.field("vpiso.NativeSurfaces","depth",surfaces)).intValue(),0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
        int size=NativeShadowMaps.size();float[] maps=new float[size*size*3];
        GL45.glGetTextureImage(NativeShadowMaps.texture(),0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,maps);
        return new Image(P.actual.clone(),NativeReceivers071Probe.texture(surfaces.texture()),NativeReceivers071Probe.texture(surfaces.normals()),NativeReceivers071Probe.texture(surfaces.solar()),depth,maps,primitives);
    }
    static void same(Image a,Image b) {
        N.require(Arrays.equals(a.color,b.color),"Final native RGBA changed");
        N.require(Arrays.equals(a.pos,b.pos)&&Arrays.equals(a.normal,b.normal)&&Arrays.equals(a.solar,b.solar)&&Arrays.equals(a.depth,b.depth),"Receiver MRT/depth changed");
        N.require(Arrays.equals(a.shadows,b.shadows),"Physical shadow maps changed");
    }
    static void guardTests(Frame f,ChunkMeshData mesh,Map<ChunkMeshData,Object> known) {
        bounds.clear();bounds.putAll(known);NativeReceiverCull c=new NativeReceiverCull();
        Matrix4f p=new Matrix4f().translate(100,100,0);NativeReceiverCull old=c.enter(f.scene,p,192,192);
        N.require(NativeReceiverCull.skip(f.scene,0),"Offscreen ordinary not excluded");NativeReceiverCull.restore(old);
        bounds.remove(mesh);old=c.enter(f.scene,p,192,192);N.require(!NativeReceiverCull.skip(f.scene,0),"Unknown external mesh excluded");NativeReceiverCull.restore(old);
        bounds.putAll(known);p=new Matrix4f().translate(1,0,0);old=c.enter(f.scene,p,192,192);N.require(!NativeReceiverCull.skip(f.scene,0),"Intersecting edge excluded");NativeReceiverCull.restore(old);
        p=new Matrix4f().perspective(1,1,.1f,100);old=c.enter(f.scene,p,192,192);N.require(!NativeReceiverCull.skip(f.scene,0),"Perspective unknown excluded");NativeReceiverCull.restore(old);
        float[] h=new float[96*96];FarRelief.Field grid=FarRelief.Field.of(96,96,h,h);f.scene.relief(new FarRelief.Lift(grid,.5f,0,0,0));
        old=c.enter(f.scene,new Matrix4f().translate(100,100,0),192,192);N.require(!NativeReceiverCull.skip(f.scene,0),"Relief geometry excluded");NativeReceiverCull.restore(old);f.scene.relief(null);
    }
}
