import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.*;

/** Original native collapsed-building decisions, real VP roof runs and settled GPU cascades. */
public final class ModelShadowCutawayProbe {
    static IsoGridSquare[] squares=new IsoGridSquare[64];
    static int visible;
    static boolean baseline;
    static boolean baseline041;
    public static IsoGridSquare squareAt(int x,int y,int z) {
        if(z!=1 || x<0 || x>=8 || y<0 || y>=8)return null;
        int at=y*8+x;return squares[at];
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        baseline=args.length>0 && args[0].equals("baseline037");
        baseline041=args.length>0 && args[0].equals("baseline041");
        String cut=ModelCutawayProbe.CUT;
        ModelCutawayProbe.cutaways=GameAccess.staticCall(cut,"getInstance");
        Object per=((Object[])GameAccess.field(cut,"perPlayerData",ModelCutawayProbe.cutaways))[0];
        Object btc=GameAccess.field(cut+"$PerPlayerData","buildingsToCollapse",per);
        ModelCutawayProbe.collapsed=(ArrayList<Object>)GameAccess.field(cut+"$BuildingsToCollapse","buildingsToCollapse",btc);
        ModelCutawayProbe.building=ModelCutawayProbe.allocate(BuildingDef.class);
        ModelCutawayProbe.collapsed.add(ModelCutawayProbe.building);ModelCutawayProbe.camera(0);
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",ModelShadowCutawayProbe.class.getMethod("squareAt",int.class,int.class,int.class));
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(128,128,"upper caster native cutaway",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));GlProgram.onUse(IsometricShaders::onUse);
            Method expand=ModelEffectsProbe.class.getDeclaredMethod("expand",String.class,HashSet.class);expand.setAccessible(true);
            String sun=(String)expand.invoke(null,"lib/sun.glsl",new HashSet<String>());
            String defines=(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active());
            ModelShadowBasisProbe.shader=GlProgram.fromSources("native roof shadow receiver",new GlProgram.Sources(
                "#version 330\nout vec2 vUv;void main(){vUv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(vUv*2.-1.,0,1);}",null,null,null,
                "#version 450\n"+defines+sun+"\nin vec2 vUv;out vec4 result;void main(){vec3 p=vec3((vUv.x-.5)*24.,0,(vUv.y-.5)*24.);result=vec4(sunlit(p,vec3(0,1,0),vec3(0,1.5,0),false),sunAt(p),0,1);}"),Map.of(),Map.of(),List.of());
            int color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,128,128,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
            ModelShadowBasisProbe.fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,ModelShadowBasisProbe.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            ModelShadowBasisProbe.vao=GL30.glGenVertexArrays();
            Frame frame=NativeSurfaceReceiverProbe.frame();
            float[][] roof=new float[12][3];
            for(int tile=0;tile<2;tile++) {
                float[][] q={{-tile,2.4494896f,0},{-tile-1,2.4494896f,0},{-tile-1,2.4494896f,-1},
                             {-tile,2.4494896f,0},{-tile-1,2.4494896f,-1},{-tile,2.4494896f,-1}};
                System.arraycopy(q,0,roof,tile*6,6);
            }
            Object mesh=NativeSurfaceReceiverProbe.mesh(frame.scene,0,roof);
            GameAccess.set("viewpoint.render.ChunkMeshData","level",mesh,1);
            GameAccess.set("viewpoint.render.ChunkMeshData","runs",mesh,new int[]{0,0,6,4,6,6});
            GameAccess.set("viewpoint.render.ChunkMeshData","pageRuns",mesh,new int[]{0,2});
            int shown=render(frame,3,0),hidden=render(frame,0,0);
            if(shown<20)throw new AssertionError("Roof fixture did not cast: "+shown);
            if(baseline) {
                if(hidden!=shown)throw new AssertionError("037 retained upper shadow not reproduced");
                System.out.println("Upper caster negative control PASS: pinned037 native collapsed roof retains "+hidden+" lower-floor shadow pixels");return;
            }
            if(baseline041) {
                if(hidden!=0)throw new AssertionError("041 physical roof deletion not reproduced: "+hidden);
                System.out.println("Roof solar negative control PASS: pinned041 camera cutaway deletes physical roof occlusion; shown="+shown+", hidden="+hidden);return;
            }
            if(hidden!=shown)throw new AssertionError("Camera cutaway lost physical roof occlusion: "+hidden+" vs "+shown);
            int partial=render(frame,1,0),restored=render(frame,3,0),upstairs=render(frame,0,1),downstairs=render(frame,0,0);
            if(!(partial==shown && restored==shown && upstairs==shown && downstairs==shown))
                throw new AssertionError("Cutaway/layer recovery: "+shown+","+partial+","+restored+","+upstairs+","+downstairs);
            // No invalidation when only a new native capture of the same masks is submitted.
            Object pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
            boolean[] valid=(boolean[])GameAccess.field("viewpoint.render.ShadowPass","valid",pass);
            Arrays.fill(valid,true);NativeShadowMaps.prepareBasis(pass,frame.scene);
            for(boolean value:valid)if(!value)throw new AssertionError("Unchanged cuts invalidated caches");
            NativeSurfaceReceiverProbe.retire(mesh);
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println("Physical roof caster GPU PASS: shown="+shown+", cameraHidden="+hidden+", partialCutaway="+partial+", restored="+restored+", upstairs="+upstairs+", downstairs="+downstairs+"; native camera cuts do not change physical solar occlusion, main-view plans retained; settled caches and stairs recovery. No user map acceptance.");
        }finally {
            Visibility109Bindings.set(api,"square",previous);
            IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
    static int render(Frame frame,int mask,int playerLevel)throws Exception {
        for(int i=0;i<64;i++) {
            squares[i]=ModelCutawayProbe.square(1,(i>=2 || (mask&(1<<i))==0));
            squares[i].x=i%8;squares[i].y=i/8;
            GameAccess.set("zombie.iso.IsoChunk","wx",squares[i].chunk,0);
            GameAccess.set("zombie.iso.IsoChunk","wy",squares[i].chunk,0);
        }
        ModelCutawayProbe.camera(playerLevel);frame.camZ=playerLevel;
        frame.scene.meshFlags[0]=2|8|16; // Includes upper meshes captured for shadows alone.
        IsometricVisibility.apply(frame,new Object(),0);
        return ModelShadowBasisProbe.dark(ModelShadowBasisProbe.draw(frame,++frame.number));
    }
}
