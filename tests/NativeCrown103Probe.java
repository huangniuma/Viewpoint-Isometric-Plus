import java.nio.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Real overlapping VP plant cards: receivers must lie on their own continuous skin. */
public final class NativeCrown103Probe {
    static final NativeShadow068Probe P=NativePlants072Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    static final float[][] ROOTS={{-.35f,0,-.15f},{.35f,0,.15f}};
    public static void main(String[] args)throws Exception {
        boolean old=Arrays.asList(args).contains("baseline102");P.pack=args[0];
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(N.W,N.H,"103 overlapping crowns",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();P.nativeProgram=NativeGroundProbe.link(NativePlants072Probe.VERTEX,NativePlants072Probe.NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            int samples=0,interior=0,frames=0;
            NativePlants072Probe.half=1.0f;NativePlants072Probe.height=2.4f;
            for(int state=0;state<3;state++)for(int order=0;order<2;order++) {
                Frame f=NativePlants072Probe.frame(state);NativePlants072Probe.fit(false,state);
                Object[] objects=plants(f,order!=0);
                for(int step=0;step<5;step++) {
                    // Subpixel translation of the real fitted native projection.
                    N.receiver.origin[0]+=.00013f;
                    NativePlants072Probe.draw(f,NativePlants072Probe.billboard(),true);
                    float[] p=NativeReceivers071Probe.texture(NativePlants072Probe.surfaces.texture());
                    float[] key=NativeReceivers071Probe.texture(NativePlants072Probe.surfaces.solar());
                    for(int i=0;i<N.W*N.H;i++)if(Math.abs(Math.abs(p[i*4+3])-5)<.01) {
                        int owner=key[i*4+3]>16000?1:0;float[] root=ROOTS[owner];
                        double radial=Math.hypot(p[i*4]-root[0],p[i*4+2]-root[2]);
                        // Calm VP sway expands the unit crown by less than two percent.
                        double skin=Math.min(Math.abs(radial-1),Math.min(Math.abs(p[i*4+1]),Math.abs(p[i*4+1]-2.4)));
                        samples++;if(skin>.02)interior++;
                    }
                    frames++;
                }
                for(Object o:objects)N.retire(o);
            }
            N.require(samples>1000,"Empty overlap fixture");
            if(old)N.require(interior>samples/3,"102 interior-card negative absent: "+interior+"/"+samples);
            else N.require(interior==0,"Receiver left its owning crown skin: "+interior+"/"+samples);
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println((old?"Previous102 crown negative PASS":"Continuous crown103 GPU PASS")+" pack="+P.pack+" frames="+frames+" interior="+interior+" samples="+samples);
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static Object[] plants(Frame f,boolean reverse)throws Exception {
        Object template=N.mesh(f.scene,0,N.horizontal(-5,.05f));Object pages=GameAccess.field("viewpoint.render.ChunkMeshData","pages",template);
        GameAccess.set("zombie.core.textures.TextureID","id",java.lang.reflect.Array.get(pages,0),NativePlants072Probe.texture());
        FloatBuffer data=org.lwjgl.system.MemoryUtil.memAllocFloat(14*6);
        for(int n=0;n<2;n++) {
            int owner=reverse?1-n:n;
            for(int plane=0;plane<3;plane++)data.put(new float[]{ROOTS[owner][0],0,ROOTS[owner][2],Float.intBitsToFloat((3<<19)|(plane<<16)),0,0,1,1,-1,1,0,2.4f,0,owner+1});
        }
        data.flip();Object plants=GameAccess.type("viewpoint.render.ChunkMeshData")
            .getConstructor(pages.getClass(),int[].class,int[].class,FloatBuffer.class,int.class,int[].class,int[].class)
            .newInstance(pages,new int[]{0},new int[]{0},data,0,null,null);
        GameAccess.call("viewpoint.render.ChunkMeshData","plants",plants,new Class<?>[]{int.class,int[].class,int[].class},0,new int[]{0},new int[]{6});
        ((Object[])GameAccess.field("viewpoint.render.SceneData","meshes",f.scene))[0]=plants;P.publish(plants,true,0);
        return new Object[]{plants,template};
    }
}
