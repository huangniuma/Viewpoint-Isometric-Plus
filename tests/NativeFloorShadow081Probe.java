import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import vpiso.*;

/** Real blended native producer to shadow consumer, and populated high-ID lookups. */
public final class NativeFloorShadow081Probe {
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
        long window=GLFW.glfwCreateWindow(N.W,N.H,"081 overlapping alpha receivers",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,NativeReceivers071Probe.FLAT);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            registry=NativeWallSources071Probe.fbo();NativeGround.begin(true);
            edges();
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println("Native floor shadow081 GPU PASS pack="+P.pack+" cases="+cases+" edgePixels="+edgePixels
                +"; independent floor at tiny alpha and overlapping owners; zoom .75/2.5; actual alpha producers and selected VP shadows; controlled scene, live acceptance pending.");
        } finally {P.nativeProducer=null;NativeGround.finish();NativeGround.release(registry);NativeGround.release(N.fbo);IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void edges()throws Exception {
        producer=NativeGroundProbe.link(N.VERT,NativeGroundShaders.rewrite("tile.frag",PRODUCER));
        Frame f=P.frame();Object page=N.mesh(f.scene,0,N.horizontal(-5,.05f));P.publish(page,true,0);
        float[][] roof=N.horizontal(2.8f,.9f);for(float[] v:roof){v[0]-=1.2f;v[2]-=.45f;}
        Object shade=N.mesh(f.scene,1,roof);P.publish(shade,true,0);
        for(int kind:new int[]{3,4}) for(double shift:new double[]{0,.007}) for(double zoom:new double[]{.75,2.5}) {
            N.receiver.fit(new double[][]{{.5+shift,.32,.5},{.5+.12/zoom+shift,.32-.06/zoom,.4985566458},{.5-.12/zoom+shift,.32-.06/zoom,.4985566458},{.5+shift,.32+.36/zoom,.4971132916}});
            NativeSource source=new NativeSource(3,3,0,kind,new float[]{-.65f,0,-.65f,.65f,1.8f,.65f});
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);NativeGround.begin(true);
            int oldGround=N.ground;N.ground=NativeGround.texture();P.metadata=NativeGround.occlusionTexture();N.overlay.metadata(P.metadata);
            int floorMap=0;try {floorMap=(Integer)NativeGround.class.getMethod("floorTexture").invoke(null);NativeOverlay.class.getMethod("floor",int.class).invoke(N.overlay,floorMap);}catch(NoSuchMethodException old){}
            try {
                float[][] art=N.vertical(1.2f,2.6f);
                for(float a:new float[]{.00016f,.02f,.25f,.5f,.9f}) {
                    P.nativeProducer=()->produce(source,1,false);
                    NativePlants072Probe.invalidate();P.draw(f,art,0,new float[]{1,0,0,1},0,true);byte[] body=P.before.clone();
                    P.nativeProducer=()->produce(source,0,false);
                    P.draw(f,art,0,new float[]{1,0,0,1},0,true);byte[] floor=P.actual.clone();
                    P.nativeProducer=()->produce(source,a,false);
                    P.draw(f,art,0,new float[]{1,0,0,1},0,true);int checked=0;
                    for(int y=2;y<N.H-2;y++)for(int x=2;x<N.W-2;x++) {
                        int i=y*N.W+x;if((P.before[i*4]&255)<170)continue;
                        // Avoid the native illustration's outside boundary.
                        if((P.before[(i-1)*4]&255)<170 || (P.before[(i+1)*4]&255)<170)continue;
                        for(int c=0;c<3;c++) {
                            int expected=Math.round((body[i*4+c]&255)*a+(floor[i*4+c]&255)*(1-a));
                            N.require(Math.abs((P.actual[i*4+c]&255)-expected)<=3,"Overlapping foreground skipped floor shadow kind="+kind+" alpha="+a+" at="+x+","+y+" component="+c+" actual="+(P.actual[i*4+c]&255)+" expected="+expected);
                        }checked++;
                    }
                    N.require(checked>300,"Empty alpha producer controls");edgePixels+=checked;cases++;
                }
            } finally {P.nativeProducer=null;NativeGround.finish();N.ground=oldGround;}
        }
        N.retire(shade);N.retire(page);
    }
    static void produce(NativeSource source,float alpha,boolean pattern) {
        if(!NativeGround.active())NativeGround.begin(true);
        GL20.glUseProgram(producer);NativeGround.setFloor(true);NativeGround.program(producer);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(producer,"projection"),false,NativeSurfaces.projection(N.receiver).get(new float[16]));
        GL20.glUniform1f(GL20.glGetUniformLocation(producer,"alpha"),1);GL20.glUniform1f(GL20.glGetUniformLocation(producer,"pattern"),0);
        GL11.glDisable(GL11.GL_BLEND);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
        NativeGround.setFloor(false);NativeGround.sourceBegin(source);
        GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glUniform1f(GL20.glGetUniformLocation(producer,"alpha"),1f-(float)Math.sqrt(1f-alpha));GL20.glUniform1f(GL20.glGetUniformLocation(producer,"pattern"),pattern?1:0);
        NativeGround.program(producer);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);NativeGround.sourceEnd();
        NativeGround.sourceBegin(source.withOwner(new float[]{123,456}));
        NativeGround.program(producer);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);NativeGround.sourceEnd();GL11.glDisable(GL11.GL_BLEND);
        NativeGround.finish(); // Production detaches capture MRTs before copying and composition.
        // Production begin() owns these MRTs; preserve P's strict stencil/alpha controls.
    }
}
