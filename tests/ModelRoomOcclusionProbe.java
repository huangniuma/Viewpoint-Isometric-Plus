import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;
import zombie.iso.*;

/** Physical closed-room roof vs camera cutaway, with actual VP maps and lighting shaders. */
public final class ModelRoomOcclusionProbe {
    static final int W=128;
    static final float ROOF=2.4494896f;
    static final IsoGridSquare[] squares=new IsoGridSquare[64];
    static GlProgram shader;
    static int fbo,vao;
    static long number;
    static boolean baseline;
    static final IsometricEffects EFFECTS=new IsometricEffects(true,1f,0f,64f,1024,false,1,1,1,1);

    public static IsoGridSquare squareAt(int x,int y,int z) {
        return z==1 && x>=0 && x<8 && y>=0 && y<8?squares[y*8+x]:null;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        baseline=args.length>0 && args[0].equals("baseline041");
        String cut=ModelCutawayProbe.CUT;
        ModelCutawayProbe.cutaways=GameAccess.staticCall(cut,"getInstance");
        Object per=((Object[])GameAccess.field(cut,"perPlayerData",ModelCutawayProbe.cutaways))[0];
        Object btc=GameAccess.field(cut+"$PerPlayerData","buildingsToCollapse",per);
        ModelCutawayProbe.collapsed=(ArrayList<Object>)GameAccess.field(cut+"$BuildingsToCollapse","buildingsToCollapse",btc);
        ModelCutawayProbe.building=ModelCutawayProbe.allocate(BuildingDef.class);
        ModelCutawayProbe.collapsed.add(ModelCutawayProbe.building);ModelCutawayProbe.camera(0);
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",ModelRoomOcclusionProbe.class.getMethod("squareAt",int.class,int.class,int.class));
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"physical room sun/moon occlusion",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));GlProgram.onUse(IsometricShaders::onUse);
            setup();
            int cases=0;
            for(int direction=0;direction<4;direction++) {
                Frame frame=NativeSurfaceReceiverProbe.frame();
                frame.scene.sunX=direction==0?-.6f:direction==1?.6f:0;
                frame.scene.sunZ=direction==2?-.6f:direction==3?.6f:0;
                frame.scene.sunY=.8f;frame.scene.sunStrength=direction<2?1f:.25f;
                Object walls=NativeSurfaceReceiverProbe.mesh(frame.scene,0,walls());
                GameAccess.set("viewpoint.render.ChunkMeshData","level",walls,0);
                Object roof=roof(frame,false);
                float[] shown=render(frame,0),hidden=render(frame,1),partial=render(frame,2),restored=render(frame,0);
                int covered=dark(shown),leaked=bright(hidden),cutMismatch=mismatch(shown,hidden),partialMismatch=mismatch(shown,partial);
                if(covered<W*W-10)throw new AssertionError("Closed physical room leaks before camera cuts: "+covered);
                if(baseline) {
                    if(leaked<100 || cutMismatch<100)throw new AssertionError("Pinned041 cut-roof leak not reproduced: "+leaked+" / "+cutMismatch);
                    System.out.println("Closed room solar negative control PASS: pinned041 direction="+direction+" cameraHiddenDirectPixels="+leaked+"; real enclosing walls retained, physical roof wrongly removed by native camera cut");
                }else {
                    if(leaked!=0 || cutMismatch!=0 || partialMismatch!=0 || mismatch(shown,restored)!=0)
                        throw new AssertionError("Camera cuts changed physical enclosure: "+leaked+","+cutMismatch+","+partialMismatch);
                    NativeSurfaceReceiverProbe.retire(roof);roof=roof(frame,true);
                    float[] aperture=render(frame,1);int admitted=bright(aperture);
                    if(admitted<100)throw new AssertionError("Real roof aperture did not admit direct light: "+admitted);
                    for(int i=0;i<W*W;i++) {
                        if(hidden[i*4+2]<.05f)throw new AssertionError("Retained indoor RGB erased by physical occlusion");
                        if(Math.abs(hidden[i*4+2]-shown[i*4+2])>1e-6f)throw new AssertionError("Camera cut changed indoor lighting");
                    }
                    System.out.println("Closed room solar GPU case PASS: "+(direction<2?"sun":"moon")+" direction="+direction+" closedDark="+covered+" cameraHiddenLeaks="+leaked+" partialMismatch="+partialMismatch+" realApertureDirect="+admitted+" indoorRGBRetained=true");
                }
                NativeSurfaceReceiverProbe.retire(roof);NativeSurfaceReceiverProbe.retire(walls);cases++;
            }
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println((baseline?"Closed room solar negative controls PASS: ":"Closed room solar GPU PASS: ")+cases+" incoming sun/moon directions; actual roof/wall meshes, native camera cuts and VP sunlit/sunAt/shade; "+(baseline?"pinned041 cutaway deletion reproduced.":"woven solar draw, physical opening control and indoor RGB preserved.")+" Controlled enclosure, no user-map acceptance or full lamp-source simulation.");
        }finally {
            Visibility109Bindings.set(api,"square",previous);
            IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }

    static void setup()throws Exception {
        Method expand=ModelEffectsProbe.class.getDeclaredMethod("expand",String.class,HashSet.class);expand.setAccessible(true);
        String sun=(String)expand.invoke(null,"lib/sun.glsl",new HashSet<String>());
        String shade=(String)expand.invoke(null,"lib/shade.glsl",new HashSet<String>());
        String defines=(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active());
        shader=GlProgram.fromSources("physical enclosure receiver",new GlProgram.Sources(
            "#version 330\nout vec2 vUv;void main(){vUv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(vUv*2.-1.,0,1);}",null,null,null,
            "#version 450\n"+defines+sun+shade+"\nin vec2 vUv;out vec4 result;void main(){vec3 p=vec3(-6.+vUv.x*4.,0,-6.+vUv.y*4.);float lit=sunlit(p,vec3(0,1,0),vec3(0,1.5,0),false);vec3 indoor=shade(vec3(1),vec4(.18,.25,.32,0),vec3(0,1,0),lit);result=vec4(lit,sunAt(p),indoor.b,1);}"),Map.of(),Map.of(),List.of());
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,W,W,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
        fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
        vao=GL30.glGenVertexArrays();
    }

    static Object roof(Frame frame,boolean aperture)throws Exception {
        ArrayList<float[]> vertices=new ArrayList<>();ArrayList<Integer> runs=new ArrayList<>();
        for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
            if(aperture && x>=2 && x<6 && y>=2 && y<6)continue;
            runs.add((y*8+x)<<2);runs.add(vertices.size());runs.add(6);
            quad(vertices,new float[]{-x,ROOF,-y},new float[]{-x-1,ROOF,-y},new float[]{-x-1,ROOF,-y-1},new float[]{-x,ROOF,-y-1});
        }
        Object mesh=NativeSurfaceReceiverProbe.mesh(frame.scene,1,vertices.toArray(float[][]::new));
        GameAccess.set("viewpoint.render.ChunkMeshData","level",mesh,1);
        GameAccess.set("viewpoint.render.ChunkMeshData","runs",mesh,runs.stream().mapToInt(Integer::intValue).toArray());
        GameAccess.set("viewpoint.render.ChunkMeshData","pageRuns",mesh,new int[]{0,runs.size()/3});
        return mesh;
    }
    static float[][] walls() {
        ArrayList<float[]> v=new ArrayList<>();
        quad(v,new float[]{0,0,0},new float[]{0,ROOF,0},new float[]{-8,ROOF,0},new float[]{-8,0,0});
        quad(v,new float[]{-8,0,0},new float[]{-8,ROOF,0},new float[]{-8,ROOF,-8},new float[]{-8,0,-8});
        quad(v,new float[]{-8,0,-8},new float[]{-8,ROOF,-8},new float[]{0,ROOF,-8},new float[]{0,0,-8});
        quad(v,new float[]{0,0,-8},new float[]{0,ROOF,-8},new float[]{0,ROOF,0},new float[]{0,0,0});
        return v.toArray(float[][]::new);
    }
    static void quad(ArrayList<float[]> vertices,float[] a,float[] b,float[] c,float[] d) {
        Collections.addAll(vertices,a,b,c,a,c,d);
    }
    static float[] render(Frame frame,int cameraCut)throws Exception {
        for(int i=0;i<64;i++) {
            squares[i]=ModelCutawayProbe.square(1,cameraCut==1 || (cameraCut==2 && i%8>=4));
            squares[i].x=i%8;squares[i].y=i/8;
            GameAccess.set("zombie.iso.IsoChunk","wx",squares[i].chunk,0);
            GameAccess.set("zombie.iso.IsoChunk","wy",squares[i].chunk,0);
        }
        ModelCutawayProbe.camera(0);frame.camZ=0;
        frame.scene.meshFlags[0]=frame.scene.meshFlags[1]=1|2|8|16;
        IsometricVisibility.apply(frame,new Object(),0);
        frame.number=++number;IsometricBridge.modelRendering(frame,EFFECTS);
        if(GameAccess.field("viewpoint.render.WorldRenderer","shadows",null)!=null)ModelShadowBasisProbe.clearLayers();
        if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("Maps unavailable");
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);GL30.glBindVertexArray(vao);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glColorMask(true,true,true,true);
        shader.use();shader.setInt("uShadowCascades",6);shader.set("uSunDir",frame.scene.sunX,frame.scene.sunY,frame.scene.sunZ);shader.set("uSunColor",1f,1f,1f);shader.set("uFogColor",.6f,.7f,.8f);shader.set("uSunStrength",frame.scene.sunStrength);shader.set("uShadowsOff",0f);
        for(int i=0;i<2;i++) {
            shader.set("uShadowMatrix["+i+"]",NativeShadowMaps.matrix(i));
            GL20.glUniform4f(shader.location("uShadowMaps["+i+"]"),2f*NativeShadowMaps.reach(i)/1024,NativeShadowMaps.depthRange(),1f/1024,0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0+6);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());GL33.glBindSampler(6,0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);float[] result=new float[W*W*4];pixels.get(result);return result;
    }
    static int dark(float[] p){int n=0;for(int i=0;i<W*W;i++)if(p[i*4]<.01f && p[i*4+1]<.01f)n++;return n;}
    static int bright(float[] p){int n=0;for(int i=0;i<W*W;i++)if(p[i*4]>.9f && p[i*4+1]>.9f)n++;return n;}
    static int mismatch(float[] a,float[] b){int n=0;for(int i=0;i<W*W;i++)if(Math.abs(a[i*4]-b[i*4])>.001f || Math.abs(a[i*4+1]-b[i*4+1])>.001f)n++;return n;}
}
