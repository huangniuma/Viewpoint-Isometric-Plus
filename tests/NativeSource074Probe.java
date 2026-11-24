import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import vpiso.*;

/** Real blended native producer to shadow consumer, and populated high-ID lookups. */
public final class NativeSource074Probe {
    static final NativeShadow068Probe P=NativeSource073Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    static int producer,registry,cases,edgePixels;
    static final String PRODUCER="""
        #version 330
        layout(location=0) out vec4 color;
        uniform float alpha,pattern;
        void main() {
          float a=alpha;
          if(pattern>.5) {int x=int(gl_FragCoord.x)%3;a=x==0?1.0:x==1?alpha:0.0;}
          color=vec4(vec3(.7,.55,.4)*a,a);
          gl_FragDepth=.5;
        }
        """;
    public static void main(String[] args)throws Exception {
        P.pack=args[0];N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"074 native receivers",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,NativeReceivers071Probe.FLAT);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            registry=NativeWallSources071Probe.fbo();NativeGround.begin(true);
            highId();physical();edges();
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println("Native sources074 GPU PASS pack="+P.pack+" cases="+cases+" edgePixels="+edgePixels
                +"; full high-ID lookup, ordinary physical normals, unknown/mixed metadata fallback, real alpha producer to floor/body shadow compositor; controlled scene, live acceptance pending.");
        } finally {P.nativeProducer=null;NativeGround.finish();NativeGround.release(registry);NativeGround.release(N.fbo);IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static float[] owner(int id,float a) {float x=-(2+(id&255)),y=id>>>8;return new float[]{x*a,y*a,(x*x+y*y)*a,a};}
    static int register(float x,int kind,float h,float top) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
        NativeGround.sourceBegin(new NativeSource(x,3,0,kind,new float[]{-h,0,-h,h,top,h}));
        int id=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();return id;
    }
    static void highId()throws Exception {
        int last=0;
        for(int i=0;i<65534;i++)last=register(100+i,3,.3f,1.8f);
        N.require(last==65535,"Source allocation did not reach final lookup row");
        // The final record is deliberately a different world position: writing
        // the real receiver there proves addressing across all three data planes.
        float[] data=(float[])GameAccess.field("vpiso.NativeGround","SOURCE_DATA",null);
        data[last*4]=3;
        Frame f=P.frame();Object page=N.mesh(f.scene,0,N.horizontal(-5,.05f));P.publish(page,true,0);
        float[][] roof=N.horizontal(2.8f,.9f);for(float[] v:roof){v[0]-=1.2f;v[2]-=.45f;}
        Object shade=N.mesh(f.scene,1,roof);P.publish(shade,true,0);
        NativePlants072Probe.invalidate();P.draw(f,N.vertical(1.2f,2.6f),0,owner(last,1),0,true);
        N.require(NativeCanopy070Probe.dark(P.before,P.actual)>10,"Last source texture row lost external shadow");cases++;
        byte[] opaque=P.actual.clone();
        for(float a:new float[]{1f/255,.25f,.5f,254f/255}) {
            P.draw(f,N.vertical(1.2f,2.6f),0,owner(last,a),0,true);
            N.require(P.different(opaque,P.actual,1)==0,"High-ID partial coverage lost receiver precision: "+a);cases++;
        }
        N.retire(shade);N.retire(page);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);NativeGround.finish();NativeGround.begin(true);
    }
    static void physical()throws Exception {
        Frame f=P.frame();Object box=N.mesh(f.scene,0,NativeSource073Probe.box(.65f,1.8f));P.publish(box,true,0);
        float[][] roof=N.horizontal(2.8f,.9f);for(float[] v:roof){v[0]-=1.2f;v[2]-=.45f;}
        Object shade=N.mesh(f.scene,1,roof);P.publish(shade,true,0);
        int id=register(3,3,.65f,1.8f);float[][] art=N.vertical(1.2f,2.6f);
        NativePlants072Probe.invalidate();P.draw(f,art,0,new float[]{1,0,0,1},0,true);byte[] expected=P.actual.clone();
        NativeSurfaces surfaces=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
        float[] positions=NativeReceivers071Probe.texture(surfaces.texture());
        for(float[] raw:new float[][]{owner(id,1),{-4,0,17,1},owner(1,1)}) {
            P.draw(f,art,0,raw,0,true);int checked=0;
            for(int i=0;i<N.W*N.H;i++)if(Math.abs(positions[i*4+3])>.5) {
                for(int c=0;c<3;c++)N.require(Math.abs((P.actual[i*4+c]&255)-(expected[i*4+c]&255))<=1,"Ordinary valid physical reception was replaced");checked++;
            }
            N.require(checked>100,"No ordinary physical receiver samples");cases++;
        }
        N.retire(shade);N.retire(box);
    }
    static void edges()throws Exception {
        producer=NativeGroundProbe.link(N.VERT,NativeGroundShaders.rewrite("tile.frag",PRODUCER));
        Frame f=P.frame();Object page=N.mesh(f.scene,0,N.horizontal(-5,.05f));P.publish(page,true,0);
        float[][] roof=N.horizontal(2.8f,.9f);for(float[] v:roof){v[0]-=1.2f;v[2]-=.45f;}
        Object shade=N.mesh(f.scene,1,roof);P.publish(shade,true,0);
        for(int kind:new int[]{3,4}) {
            NativeSource source=new NativeSource(3,3,0,kind,new float[]{-.65f,0,-.65f,.65f,1.8f,.65f});
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);NativeGround.begin(true);
            int oldGround=N.ground;N.ground=NativeGround.texture();P.metadata=NativeGround.occlusionTexture();N.overlay.metadata(P.metadata);
            try {
                float[][] art=N.vertical(1.2f,2.6f);
                for(float a:new float[]{1f/255,.25f,.5f,254f/255}) {
                    P.nativeProducer=()->produce(source,1,false);
                    NativePlants072Probe.invalidate();P.draw(f,art,0,new float[]{1,0,0,1},0,true);byte[] body=P.actual.clone();
                    P.nativeProducer=()->produce(source,0,false);
                    P.draw(f,art,0,new float[]{1,0,0,1},0,true);byte[] floor=P.actual.clone();
                    P.nativeProducer=()->produce(source,a,true);
                    P.draw(f,art,0,new float[]{1,0,0,1},0,true);int checked=0;
                    for(int y=2;y<N.H-2;y++)for(int x=2;x<N.W-2;x++) {
                        int i=y*N.W+x;if(x%3!=1 || (P.before[i*4]&255)<170)continue;
                        // Avoid the native illustration's outside boundary.
                        if((P.before[(i-1)*4]&255)<170 || (P.before[(i+1)*4]&255)<170)continue;
                        for(int c=0;c<3;c++) {
                            int expected=Math.round((body[i*4+c]&255)*a+(floor[i*4+c]&255)*(1-a));
                            N.require(Math.abs((P.actual[i*4+c]&255)-expected)<=3,"Mixed alpha shadow gap kind="+kind+" alpha="+a+" at="+x+","+y+" component="+c+" actual="+(P.actual[i*4+c]&255)+" expected="+expected);
                        }checked++;
                    }
                    N.require(checked>500,"Empty alpha producer controls");edgePixels+=checked;cases++;
                }
            } finally {P.nativeProducer=null;NativeGround.finish();N.ground=oldGround;}
        }
        N.retire(shade);N.retire(page);
    }
    static void produce(NativeSource source,float alpha,boolean pattern) {
        GL20.glUseProgram(producer);NativeGround.setFloor(true);NativeGround.program(producer);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(producer,"projection"),false,NativeSurfaces.projection(N.receiver).get(new float[16]));
        GL20.glUniform1f(GL20.glGetUniformLocation(producer,"alpha"),1);GL20.glUniform1f(GL20.glGetUniformLocation(producer,"pattern"),0);
        GL11.glDisable(GL11.GL_BLEND);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
        NativeGround.setFloor(false);NativeGround.sourceBegin(source);
        GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glUniform1f(GL20.glGetUniformLocation(producer,"alpha"),alpha);GL20.glUniform1f(GL20.glGetUniformLocation(producer,"pattern"),pattern?1:0);
        NativeGround.program(producer);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);NativeGround.sourceEnd();GL11.glDisable(GL11.GL_BLEND);
        // Production begin() owns these MRTs; preserve P's strict stencil/alpha controls.
    }
}
