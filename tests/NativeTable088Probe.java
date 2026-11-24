import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.packs.PackBind;
import viewpoint.render.*;
import vpiso.*;

/** Actual installed table geometry, billboard floor depth and real VP external shadows. */
public final class NativeTable088Probe {
    static final NativeShadow068Probe P=NativeSource073Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    static final Path ROOT=NativePackedPlants072Probe.ROOT;
    static final String[] FILES={
        "b4674d7f-042b-4bee-af38-5ff0167a6545/r1-1790392244507/section-2.obj",
        "4af3f8d7-e4b8-4533-a1b6-1821635066a4/h-fc4accefd9/i0-s1.obj"};
    static final float[] TOP={.86686f,1f};
    static final String VERTEX=NativePlants072Probe.VERTEX;
    static final String ART="""
        #version 330
        in vec3 physical;
        uniform mat4 projection;uniform vec3 screenRay;uniform vec4 owner;
        layout(location=0)out vec4 color;layout(location=6)out vec4 metadata;layout(location=7)out vec4 ground;
        void main(){
          vec3 foot=physical-screenRay*(physical.y/screenRay.y);
          vec4 p=projection*vec4(foot,1);
          color=vec4(.7,.55,.4,.8);metadata=owner;ground=vec4(0);
          gl_FragDepth=floor((p.z/p.w*.5+.5)*65535.0)/65535.0;
        }
        """;
    static boolean baseline;
    public static void main(String[] args)throws Exception {
        P.pack=args[0];baseline=Arrays.asList(args).contains("baseline087");
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"088 native tables",0,0);int registry=0;
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();P.nativeProgram=NativeGroundProbe.link(VERTEX,ART);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            registry=NativeWallSources071Probe.fbo();NativeGround.begin(true);
            int frames=0,external=0;
            for(int i=0;i<FILES.length;i++) {
                Path file=ROOT.resolve(FILES[i]);PackModel model=PackModels.of(file.toFile());
                PackData data=(PackData)GameAccess.call("viewpoint.packs.PackLoads","load",null,new Class<?>[]{java.io.File.class,Path.class},file.toFile(),ROOT);
                PackModels.onItsWay();model.read(data);PackModels.publish();
                for(double zoom:new double[]{.75,1,2.5}) {
                    Frame frame=P.frame();frame.scene.sunX=-.67136335f;frame.scene.sunY=.73516107f;frame.scene.sunZ=-.09385877f;
                    N.receiver.fit(new double[][]{{.5,.3,.5},{.5+.16/zoom,.3-.08/zoom,.499},{.5-.16/zoom,.3-.08/zoom,.499},{.5,.3+.32/zoom,.499}});
                    Object mesh=mesh(frame,model);float[] b=model.bounds();float[][] art=top(b,TOP[i]);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
                    NativeGround.sourceBegin(new NativeSource(0,0,0,5,b).withOwner(NativeSource.ownerKey(0,0,0,0)));
                    int id=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();float[] owner={-2,id,-id*id,1};
                    float[] inv=N.receiver.inverse.get(new float[16]);
                    GL20.glUseProgram(P.nativeProgram);GL20.glUniform3f(GL20.glGetUniformLocation(P.nativeProgram,"screenRay"),-inv[8],inv[10]*2.4494896f,-inv[9]);
                    NativePlants072Probe.invalidate();P.draw(frame,art,0,owner,0,true);
                    NativeSurfaces s=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
                    float[] pos=NativeReceivers071Probe.texture(s.texture()),norm=NativeReceivers071Probe.texture(s.normals());
                    int tops=0;for(int k=0;k<N.W*N.H;k++)if(Math.abs(pos[k*4+1]-TOP[i])<.002 && norm[k*4+1]>.99)tops++;
                    System.out.println("Table088 "+i+" zoom="+zoom+" realTopPixels="+tops+" baseline="+baseline);
                    N.require(baseline?tops==0:tops>20,"Billboard floor depth selected table underside: tops="+tops);frames++;
                    if(!baseline) {
                        float[][] roof=N.horizontal(2.8f,.55f);for(float[] v:roof){v[0]-=1.0f;v[2]-=.15f;}
                        Object caster=N.mesh(frame.scene,1,roof);P.publish(caster,true,0);
                        NativePlants072Probe.invalidate();P.draw(frame,art,0,owner,0,true);
                        int dark=NativeCanopy070Probe.dark(P.before,P.actual);N.require(dark>5,"Table lost other objects' physical shadows: "+dark);external+=dark;frames++;
                        N.retire(caster);
                    }
                    N.retire(mesh);
                }
            }
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println((baseline?"Previous087 table rejection negative PASS":"Tables088 GPU PASS")+" pack="+P.pack+" frames="+frames+" externalShadowPixels="+external+"; installed OBJ/MTL, original floor-depth representation and zoom, actual VP caster/receiver/maps, native alpha/depth/stencil preserved.");
        } finally {NativeGround.finish();if(registry!=0)NativeGround.release(registry);IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static Object mesh(Frame frame,PackModel model)throws Exception {
        Object recipe=NativePackedPlants072Probe.fresh("viewpoint.world.Recipe",0);
        Object op=GameAccess.call("viewpoint.world.Recipe","model",null,
            new Class<?>[]{recipe.getClass(),PackBind.class,boolean.class,float.class,float.class,float.class,int.class,int.class,int.class,int.class},
            recipe,new PackBind(model,0,0,0,0,1),false,0f,0f,0f,0,0,0,0);
        GameAccess.set("viewpoint.world.Recipe$Op","object",op,0);
        List<Object> ops=List.of(op);Object cooker=NativePackedPlants072Probe.fresh("viewpoint.world.Cook$Cooker");
        int[] runs=(int[])GameAccess.call(cooker.getClass().getName(),"models",cooker,new Class<?>[]{List.class},ops);
        PlantModelBounds.cooked(cooker,ops);
        int length=((Number)GameAccess.field(cooker.getClass().getName(),"modelFloats",cooker)).intValue();
        float[] records=(float[])GameAccess.field(cooker.getClass().getName(),"instances",cooker);
        FloatBuffer data=org.lwjgl.system.MemoryUtil.memAllocFloat(length);data.put(records,0,length).flip();
        Object pages=java.lang.reflect.Array.newInstance(GameAccess.type("zombie.core.textures.TextureID"),0);
        Object mesh=GameAccess.type("viewpoint.render.ChunkMeshData").getConstructor(pages.getClass(),int[].class,int[].class,FloatBuffer.class,int.class,int[].class,int[].class)
            .newInstance(pages,new int[0],new int[0],data,0,null,null);
        GameAccess.call(mesh.getClass().getName(),"models",mesh,new Class<?>[]{int.class,int[].class},0,runs);
        frame.scene.meshes[0]=(ChunkMeshData)mesh;frame.scene.meshCount=1;frame.scene.meshFlags[0]=2|8|16;
        frame.scene.meshFade[0]=0;frame.scene.meshFade[1]=2;P.publish(mesh,true,0);return mesh;
    }
    static float[][] top(float[] bounds,float height) {
        int[] corners={0,1,3,0,3,2};float[][] out=new float[6][3];
        for(int i=0;i<6;i++)out[i]=new float[]{bounds[(corners[i]&1)==0?0:3],height,bounds[(corners[i]&2)==0?2:5]};return out;
    }
}
