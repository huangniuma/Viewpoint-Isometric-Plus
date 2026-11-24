import java.nio.*;
import java.util.*;
import java.util.regex.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.SceneData;
import vpiso.*;

/** Actual plant slots and source-wall receivers against independent selected-pack shading. */
public final class NativeReceivers071Probe {
    static final NativeShadow068Probe P=new NativeShadow068Probe();
    static final NativeSurfaceReceiverProbe N=P.N;
    static boolean baseline;
    static int registry,oracleVao,oracleVbo,cases;
    static GlProgram oracle;
    static final String FLAT=P.NATIVE.replace("floor((gl_FragCoord.z+depthBias)*65535.0)/65535.0", ".5");
    public static void main(String[] args)throws Exception {
        P.pack=args.length==0?"default":args[0];baseline=Arrays.asList(args).contains("baseline070");
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"071 source receivers",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,P.NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);
            N.overlay.metadata(P.metadata);
            registry=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,P.texture(GL11.GL_RGBA8,8,8),0);
            NativeGround.begin(true);NativeGround.finish();
            oracleVao=GL30.glGenVertexArrays();oracleVbo=GL15.glGenBuffers();oracle=oracle();
            plants();walls();if(!baseline)highOwner();
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println((baseline?"Pinned070 receiver negative PASS":"Native receivers071 GPU PASS")+" pack="+P.pack+" cases="+cases
                +"; actual VP crossed plant slots/VAO/maps, upward foliage normals, source-plane wall reception independent of flat sprite depth and competing grass/floor; selected-pack reference, mixed/unknown/cutaway rejection, native alpha/depth/stencil preserved. Live visual acceptance pending.");
        } finally {NativeGround.release(registry);IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void plants()throws Exception {
        Frame f=P.frame();f.scene.originX=f.scene.originZ=0;
        Object template=N.mesh(f.scene,0,N.horizontal(-5,.05f));
        Object pages=GameAccess.field("viewpoint.render.ChunkMeshData","pages",template);
        // The real constructor packs trailing 14-float plant records into 10 RG32F texels.
        FloatBuffer records=org.lwjgl.system.MemoryUtil.memAllocFloat(28);
        for(int plane=0;plane<2;plane++)records.put(new float[]{0,0,0,Float.intBitsToFloat((2<<19)|(plane<<16)),0,0,1,1,-.7f,.7f,0,.8f,0,0});
        records.flip();Object plant=GameAccess.type("viewpoint.render.ChunkMeshData")
            .getConstructor(pages.getClass(),int[].class,int[].class,FloatBuffer.class,int.class,int[].class,int[].class)
            .newInstance(pages,new int[]{0},new int[]{0},records,0,null,null);
        GameAccess.call("viewpoint.render.ChunkMeshData","plants",plant,new Class<?>[]{int.class,int[].class,int[].class},0,new int[]{0},new int[]{2});
        ((Object[])GameAccess.field("viewpoint.render.SceneData","meshes",f.scene))[0]=plant;
        P.publish(plant,true,0);
        float[][] artwork=N.vertical(1,.9f);
        int total=0,bad=0;
        for(double shift:new double[]{0,.007,-.007}) {
            N.fit(shift);P.draw(f,artwork,.0001f,new float[]{1,0,0,1},0,true);
            NativeSurfaces s=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
            float[] pos=texture(s.texture()),norm=texture(s.normals());
            int count=0,wrong=0;
            for(int i=0;i<N.W*N.H;i++)if(Math.abs(pos[i*4+3])>.5) {
                count++;
                // 072 crown kind5 stores a solar offset in place of its implicit
                // upward normal; general foliage kind4 retains the 071 layout.
                boolean crown=Math.abs(Math.abs(pos[i*4+3])-5)<.01;
                if(crown)for(int c=0;c<3;c++)N.require(Float.isFinite(norm[i*4+c]),"Invalid crown solar offset");
                else if(Math.abs(norm[i*4])>.002 || Math.abs(norm[i*4+1]-1)>.002 || Math.abs(norm[i*4+2])>.002)wrong++;
                if(!baseline)N.require(crown || Math.abs(Math.abs(pos[i*4+3])-4)<.01,"Foliage lost receiver kind");
            }
            N.require(count>100,"Real plant fixture empty: "+count);total+=count;bad+=wrong;cases++;
        }
        if(baseline)N.require(bad>total/2,"070 plant crossed-card normals did not reproduce");
        else N.require(bad==0,"Foliage card normal seam: "+bad+"/"+total);
        System.out.println("Plant slots samples="+total+" lateralNormals="+bad);
        N.retire(plant);N.retire(template);N.fit(0);
    }
    static void walls()throws Exception {
        Frame f=P.frame();f.scene.sunX=-.6f;f.scene.sunY=.8f;
        float[][] wall={{0,0,-1},{0,2.4f,-1},{0,2.4f,0},{0,0,-1},{0,2.4f,0},{0,0,0}};
        Object floor=N.mesh(f.scene,0,N.horizontal(0,3));P.publish(floor,true,0);
        // A nearby lower grass-shaped proxy is allowed to compete; it never owns wall artwork.
        Object bush=N.mesh(f.scene,1,N.horizontal(.35f,.7f));P.publish(bush,true,0);
        float[][] cast=N.horizontal(2.8f,.65f);for(float[] v:cast){v[0]-=.7f;v[2]-=.5f;}
        Object blocker=N.mesh(f.scene,2,cast);P.publish(blocker,true,0);
        int id=baseline?2:register(3,3,0,1);
        P.nativeProgram=NativeGroundProbe.link(N.VERT,FLAT);
        P.draw(f,wall,0,owner(id),0,true);byte[] actual=P.actual.clone();
        byte[] expected=reference(f,wall,new float[]{-1,0,0});int different=P.different(expected,actual,3);
        int dark=NativeCanopy070Probe.dark(P.before,expected);N.require(dark>20,"Source wall reference lacks shadow: "+dark);
        if(baseline) {N.require(different>20,"070 wrong wall receiver did not reproduce");System.out.println("070 wall wrong-position pixels="+different);}
        else {
            N.require(different==0,"Source-plane wall selected-reference mismatches="+different);cases++;
            // Exact-source identity must survive camera motion and native depth quantization.
            for(double shift:new double[]{.006,-.008}) {
                N.fit(shift);P.draw(f,wall,0,owner(id),0,true);
                N.require(P.different(reference(f,wall,new float[]{-1,0,0}),P.actual,3)==0,"Moving source wall mismatch");cases++;
            }
            N.fit(0);
            float[][] invalid={owner(1),new float[]{-1,1022,-(1021f*1021+1023f*1023)/2,1},
                new float[]{-.5f,id*.5f,-id*id*.5f,1},new float[]{-1,1023,-1023f*1023,1},
                new float[]{1f/3,341,-348843,1},new float[]{-.5f,0,0,1},new float[]{-.5f,1,2,1}};
            for(float[] value:invalid) {
                P.draw(f,wall,0,value,0,true);P.identity("mixed, unknown, translucent or unused source wall");cases++;
            }
            P.draw(f,wall,0,owner(id),0,false);P.identity("wall effects off");cases++;
            int sentinel=NativeGroundProbe.solid(31,79,127,255),sampler=GL33.glGenSamplers();
            GL13.glActiveTexture(GL13.GL_TEXTURE12);GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinel);GL33.glBindSampler(12,sampler);
            P.draw(f,wall,0,owner(id),0,true);GL13.glActiveTexture(GL13.GL_TEXTURE12);
            N.require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==sentinel && GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,12)==sampler,"Wall lookup leaked texture unit12");
            GL33.glBindSampler(12,0);GL33.glDeleteSamplers(sampler);GL11.glDeleteTextures(sentinel);cases++;
            N.retire(blocker);f.scene.meshCount=2;
            P.draw(f,wall,0,owner(id),0,true);
            N.require(P.different(reference(f,wall,new float[]{-1,0,0}),P.actual,3)==0,"Unblocked source wall");cases++;
        }
        N.retire(bush);N.retire(floor);if(baseline)N.retire(blocker);
    }
    static int register(int x,int y,int level,int axis)throws Exception {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
        var m=NativeGround.class.getDeclaredMethod("retainWall",int.class,int.class,int.class,int.class);m.setAccessible(true);
        return (Integer)m.invoke(null,x,y,level,axis);
    }
    static void highOwner()throws Exception {
        for(int i=3;i<800;i++)N.require(register(10000+i,0,0,1)==i,"High ID fixture allocation");
        int id=register(4,3,0,1);N.require(id==800,"High source ID fixture");
        Frame f=P.frame();float[][] wall={{-1,0,-1},{-1,2.4f,-1},{-1,2.4f,0},{-1,0,-1},{-1,2.4f,0},{-1,0,0}};
        Object floor=N.mesh(f.scene,0,N.horizontal(0,3));P.publish(floor,true,0);
        float[][] cast=N.horizontal(2.8f,.65f);for(float[] v:cast){v[0]-=1.7f;v[2]-=.5f;}
        Object blocker=N.mesh(f.scene,1,cast);P.publish(blocker,true,0);
        float weight=.9999151229858398f;
        P.draw(f,wall,0,NativeMetadata074.wall(id,weight),0,true);
        byte[] correct=reference(f,wall,new float[]{-1,0,0});
        N.require(NativeCanopy070Probe.dark(P.before,correct)>20,"High ID reference empty");
        N.require(P.different(correct,P.actual,3)==0,"Nearly opaque high wall ID rejected by float roundoff");cases++;
        N.retire(blocker);N.retire(floor);
    }
    static float[] owner(int id){return NativeMetadata074.wall(id,1);}
    static float[] texture(int id){GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);float[] p=new float[N.W*N.H*4];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,p);return p;}
    static byte[] reference(Frame f,float[][] points,float[] normal)throws Exception {
        try(NativeLighting.Scope scope=P.ready(f.scene)) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
            P.raster();GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            P.vertices(points,oracleVao,oracleVbo);oracle.use();scope.uniforms(oracle,NativeShadowMaps.texture(),NativeShadowMaps.count());
            oracle.set("projection",NativeSurfaces.projection(N.receiver));oracle.set("knownNormal",normal[0],normal[1],normal[2]);
            GL30.glBindVertexArray(oracleVao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);return P.copy(N.read());
        }
    }
    static GlProgram oracle() {
        String shade=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,new Class<?>[]{String.class,ShaderPack.class,boolean[].class},"lib/shade.glsl",ShaderPacks.active().pack,new boolean[1]);
        Matcher m=Pattern.compile("\\bvec3\\s+shadeShared\\s*\\(([^)]*)\\)").matcher(shade);int args=m.find()?m.group(1).split(",").length:0;
        String selected=args==7?"shadeShared(vec3(1),light,1.0,n,lit,vec4(n,1),vec3(0))":args==6?"shadeShared(vec3(1),light,1.0,n,lit,vec4(n,1))":"shade(vec3(1),light,n,lit)";
        String fragment="""
            #version 330
            in vec3 physical;out vec4 outputColor;uniform vec3 knownNormal;
            vec3 selected(float lit){vec3 n=knownNormal;vec4 light=vec4(vec3(180.0/255.0),1);return SELECTED;}
            void main(){vec3 n=knownNormal;
              float clear=dot(n,uSunDir)>0.0?smoothstep(0.0,.3,dot(n,uSunDir))*cloudShadow(physical):0.0;
              float mapped=dot(n,uSunDir)>0.0?sunlit(physical,n,vpIsoLightingEye,false):0.0;
              vec3 a=selected(clear),b=selected(mapped),ratio=vec3(1);
              for(int c=0;c<3;c++)if(a[c]>1e-6)ratio[c]=max(b[c]/a[c],0.0);
              outputColor=vec4(vec3(.7,.55,.4)*ratio,.8);
            }
            """.replace("SELECTED",selected);
        return GlProgram.fromSources("071 physical wall reference",new GlProgram.Sources(P.ORACLE_VERTEX,null,null,null,NativeLighting.fragment(fragment)),Map.of(),Map.of(),List.of("071 oracle"));
    }
}
