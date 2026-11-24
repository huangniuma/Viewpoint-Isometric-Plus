import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import vpiso.*;

/** Real VP spot maps over the native floor; no game process or world colour pass. */
public final class NativeMobileShadow083Probe {
    static final NativeShadow068Probe P=NativeSource073Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    static int cases;
    static IsometricEffects mode=NativeShadow068Probe.ON;
    public static void main(String[] args)throws Exception {
        P.pack=args[0];N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"native mobile shadows083",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,NativeShadow068Probe.NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            for(String kind:new String[]{"flashlight","headlight"})if(args.length<2 || args[1].equals(kind))mobile(kind);
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println("Native mobile083 GPU PASS pack="+P.pack+" cases="+cases+"; flashlight/headlight maps without sun or lamps, blockers, removal, switch-off, UBO2 and texture21 restoration, no native alpha/depth/stencil changes.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void mobile(String kind)throws Exception {
        Frame f=P.frame();f.scene.sunStrength=0;f.scene.lightCount=f.scene.lampShadowCount=0;
        f.scene.skyR=f.scene.skyG=f.scene.skyB=.1f;
        Object floor=N.mesh(f.scene,0,N.horizontal(-5,.05f));P.publish(floor,true,0);
        if(kind.equals("flashlight")) {
            f.scene.spots.flashOn=true;f.scene.spots.flashAtItem=true;
            f.scene.spots.flashItemX=0;f.scene.spots.flashItemY=3;f.scene.spots.flashItemZ=2;
            f.scene.spots.flashBeam=new viewpoint.render.TorchBeam(10,16,.4f,1.15f,1,.2f,0,0,0,.3f,1);
        } else {
            f.scene.spots.lampCount=1;f.scene.spots.carMapCount=1;
            float[] l=f.scene.spots.lamps;
            float[] record={0,3,2,16, 0,-.6f,-.8f,1, 40,35,30,1, 0,.8f,-.6f,.4f, 0,-.6f,-.8f,.03f, .1f,0,0,0};
            System.arraycopy(record,0,l,0,24);
            Arrays.fill(f.scene.spots.carMaps,-1);
            float[] map={1,.5f,0,3,2,0,-.6f,-.8f,16,1,1};
            System.arraycopy(map,0,f.scene.spots.carMaps,11,11);
        }
        float[][] art=N.horizontal(0,2.3f);
        draw(f,art,true);byte[] open=P.actual.clone();
        N.require(P.different(P.before,open,2)==0,kind+" darkens native picture without a caster");cases++;
        float[][] roof=N.horizontal(1.5f,.55f);Object blocker=N.mesh(f.scene,1,roof);
        int offset=GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
        int ubo=GL15.glGenBuffers();GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,ubo);GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,offset+64,GL15.GL_DYNAMIC_DRAW);GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER,2,ubo,offset,64);
        int texture=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0+21);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,texture);
        int sampler=GL33.glGenSamplers();GL33.glBindSampler(21,sampler);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        draw(f,art,true);
        int dark=P.different(open,P.actual,2);
        N.require(dark>20,"Missing native "+kind+" shadows: changed pixels="+dark);cases++;
        N.require(GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING,2)==ubo && GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START,2)==offset && GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE,2)==64,"Spot UBO range not restored");
        GL13.glActiveTexture(GL13.GL_TEXTURE0+21);
        N.require(GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY)==texture && GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,21)==sampler,"Spot texture/sampler not restored");GL13.glActiveTexture(GL13.GL_TEXTURE0);cases++;
        if(!Boolean.getBoolean("mobile083.unwoven")) {
            // A cached headlight map must also change when its caster selection changes.
            if(kind.equals("headlight"))f.scene.spots.carMaps[11]=0;
            Object building=selectable(f,floor,1,roof),decoration=selectable(f,floor,0,roof);
            f.scene.meshes[1]=(viewpoint.render.ChunkMeshData)building;
            mode=new IsometricEffects(true,1,1,64,1024,false,0,1,1,1,false,false,true);
            draw(f,art,true);N.require(P.different(open,P.actual,2)>20,"Lightweight mode lost "+kind+" shadows");cases++;
            f.scene.meshes[1]=(viewpoint.render.ChunkMeshData)decoration;
            if(kind.equals("headlight"))f.scene.spots.carMaps[11]=1;
            draw(f,art,true);N.require(P.different(P.before,P.actual,2)==0,"Lightweight "+kind+" retained decorative casters");cases++;
            mode=NativeShadow068Probe.ON;
            draw(f,art,true);N.require(P.different(open,P.actual,2)>20,"Full "+kind+" casters not restored");cases++;
            f.scene.meshes[1]=(viewpoint.render.ChunkMeshData)blocker;N.retire(building);N.retire(decoration);
        }
        draw(f,art,false);N.require(P.different(P.before,P.actual,0)==0,"Shadows remain with switch off");cases++;
        f.scene.spots.flashOn=false;f.scene.spots.lampCount=f.scene.spots.carMapCount=0;
        draw(f,art,true);N.require(P.different(P.before,P.actual,0)==0,"Stale mobile shadow after light off");cases++;
        N.retire(blocker);N.retire(floor);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,2,0);GL15.glDeleteBuffers(ubo);GL33.glBindSampler(21,0);GL33.glDeleteSamplers(sampler);GL11.glDeleteTextures(texture);
        System.out.println(kind+" native shadow pixels="+dark);
    }
    static void draw(Frame f,float[][] art,boolean enabled)throws Exception {
        P.draw(f,art,.0007f,new float[]{0,0,0,1},1,false);
        IsometricBridge.rendering(f);
        try {NativeShadowMaps.draw(f.scene,1024,2,N.overlay.surfaces(N.receiver),mode);}
        finally {IsometricBridge.rendering(null);}
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
        N.overlay.draw(N.receiver,enabled?mode:NativeShadow068Probe.OFF,0,new org.joml.Matrix4f[]{new org.joml.Matrix4f(),new org.joml.Matrix4f(),new org.joml.Matrix4f()},new float[3],1,1024,0,0);
        P.actual=P.copy(N.read());P.preserved();N.require(GL11.glGetError()==0,"GL error in mobile composition");
    }
    static Object selectable(Frame f,Object floor,int source,float[][] points)throws Exception {
        LightweightShadow082Probe.page=((Object[])GameAccess.field("viewpoint.render.ChunkMeshData","pages",floor))[0];
        Object recipe=LightweightShadow082Probe.recipe(),batch=LightweightShadow082Probe.batch(recipe);
        LightweightShadow082Probe.raw(batch,0,source,source==0?1:0);
        Object op=((java.util.List<?>)GameAccess.field(batch.getClass().getName(),"ops",batch)).get(0);
        float[] raw=(float[])GameAccess.field(op.getClass().getName(),"raw",op);
        for(int i=0;i<points.length;i++)System.arraycopy(points[i],0,raw,i*14,3);
        return LightweightShadow082Probe.cook(recipe);
    }
}
