import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import vpiso.*;

/** Actual VP exposure producer and native physical receiver under an outdoor canopy. */
public final class NativeCanopy062Probe {
    static final NativeSurfaceReceiverProbe N=new NativeSurfaceReceiverProbe();
    static byte[] image;
    static byte open,covered,indoor;
    public static void main(String[] args)throws Exception {
        boolean baseline=Arrays.asList(args).contains("baseline061");
        exposureProducer();
        N.require(GLFW.glfwInit(),"GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"canopy062",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(args.length>0?args[0]:"library",args.length>0 && args[0].equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();int cases=0,total=0;
            for(String kind:new String[]{"column","wall","furniture","rigid","skinned","vehicle"}) {
                Frame f=N.frame();
                boolean model=kind.equals("rigid")||kind.equals("skinned")||kind.equals("vehicle");
                float[][] points=kind.equals("column")?N.vertical(.22f,3f):kind.equals("wall")?N.vertical(1.4f,2.8f):N.horizontal(.8f,1.4f);
                Object surface=null;
                if(model)N.addModel(f.scene,points,kind.equals("skinned"),kind.equals("vehicle"));
                else surface=N.mesh(f.scene,0,points);
                for(int step=0;step<8;step++) {
                    N.fit((step%4)*.003);f.scene.cameraCut=true;
                    f.scene.sunX=-.3f-step*.04f;f.scene.sunY=(float)Math.sqrt(1-f.scene.sunX*f.scene.sunX);
                    Arrays.fill(f.scene.exposure,covered);
                    N.require(draw(f,points,true)==0,kind+" canopy own shadow at moving phase="+step);cases++;
                }
                float[][] roof=N.horizontal(3.4f,.65f);for(float[] p:roof)p[0]-=1.65f;
                Object caster=N.mesh(f.scene,model?0:1,roof);
                for(int step=0;step<8;step++) {
                    N.fit((step%4)*.003);f.scene.cameraCut=true;
                    f.scene.sunX=-.4f-step*.03f;f.scene.sunY=(float)Math.sqrt(1-f.scene.sunX*f.scene.sunX);
                    Arrays.fill(f.scene.exposure,open);int exposed=draw(f,points,true);byte[] expected=image.clone();
                    N.require(exposed>5,kind+" missing reference projection="+exposed);
                    Arrays.fill(f.scene.exposure,covered);int canopy=draw(f,points,true);
                    if(baseline)N.require(canopy==0,kind+" old covered rejection did not reproduce");
                    else N.require(Arrays.equals(expected,image),kind+" canopy clipping="+canopy+" reference="+exposed);
                    Arrays.fill(f.scene.exposure,indoor);N.require(draw(f,points,true)==0,kind+" sunlight leaked into native interior");
                    cases+=3;total+=canopy;
                }
                Arrays.fill(f.scene.exposure,covered);
                N.require(draw(f,points,false)==0,kind+" disabled effect modified image");cases++;
                // Native unload retires the old page; list removal alone leaves
                // VP's settled held-caster cache valid.
                N.retire(caster);f.scene.meshCount=model?0:1;f.scene.cameraCut=true;
                N.require(draw(f,points,true)==0,kind+" removed canopy retained shadow");cases++;
                if(surface!=null)N.retire(surface);
            }
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println((baseline?"Pinned061 canopy negative control PASS:":"Native canopy receiver062 GPU PASS:")+" cases="+cases+" coveredShadowPixels="+total+"; actual Weather.read codes255/100/0, six receiver kinds, eight moving sun/camera phases, original alpha/depth/stencil, own/off/removed-caster identity, indoor sunlight exclusion; no live map.");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void exposureProducer()throws Exception {
        zombie.iso.IsoCell cell=(zombie.iso.IsoCell)N.allocate(zombie.iso.IsoCell.class);
        zombie.iso.IsoChunkMap map=(zombie.iso.IsoChunkMap)N.allocate(zombie.iso.IsoChunkMap.class);
        zombie.iso.IsoChunk chunk=(zombie.iso.IsoChunk)N.allocate(zombie.iso.IsoChunk.class);
        zombie.iso.IsoGridSquare[][] squares=new zombie.iso.IsoGridSquare[1][64];
        GameAccess.set("zombie.characters.IsoPlayer","numPlayers",null,1);
        GameAccess.set("zombie.iso.IsoChunkMap","chunkGridWidth",null,1);
        GameAccess.set("zombie.iso.IsoChunkMap","chunkWidthInTiles",null,8);
        GameAccess.set("zombie.iso.IsoChunkMap","chunksSwapB",map,new zombie.iso.IsoChunk[]{chunk});
        GameAccess.set("zombie.iso.IsoChunk","loaded",chunk,true);
        GameAccess.set("zombie.iso.IsoChunk","squares",chunk,squares);
        GameAccess.set("zombie.iso.IsoCell","chunkMap",cell,new zombie.iso.IsoChunkMap[]{map});
        GameAccess.set("viewpoint.environment.Weather","gridX",null,0);
        GameAccess.set("viewpoint.environment.Weather","gridY",null,0);
        GameAccess.set("viewpoint.environment.Weather","gridZ",null,0);
        byte[] values=new byte[3];int i=0;
        for(boolean[] state:new boolean[][]{{true,false},{true,true},{false,true}}) {
            squares[0][0]=(zombie.iso.IsoGridSquare)GameAccess.field("zombie.iso.IsoObject","square",NativeGroundProbe.floorObject(state[0],state[1]));
            GameAccess.call("viewpoint.environment.Weather","read",null,new Class<?>[]{zombie.iso.IsoCell.class,int.class,int.class},cell,0,0);
            values[i++]=((byte[])GameAccess.field("viewpoint.environment.Weather","exposure",null))[0];
        }
        open=values[0];covered=values[1];indoor=values[2];
        N.require((open&255)==255 && (covered&255)==100 && indoor==0,"VP exposure ABI changed: "+Arrays.toString(values));
        System.out.println("Actual VP Weather.read exposure PASS: open=255, outside+roof=100, interior=0.");
    }
    static int draw(Frame f,float[][] points,boolean enabled) {
        f.number=++N.number;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glViewport(0,0,N.W,N.H);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClearDepth(1);GL11.glStencilMask(255);GL11.glClearStencil(31);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{0,0,0,0});
        FloatBuffer data=BufferUtils.createFloatBuffer(points.length*3);for(float[] p:points)data.put(p);data.flip();
        GL30.glBindVertexArray(N.vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,N.vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glUseProgram(N.nativeProgram);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(N.nativeProgram,"projection"),false,NativeSurfaces.projection(N.receiver).get(new float[16]));
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0L);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);
        ByteBuffer before=N.read();float[] depthBefore=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthBefore);
        N.overlay.ground(N.ground);N.overlay.capture(true);
        IsometricBridge.rendering(f);
        try{N.require(NativeShadowMaps.draw(f.scene,1024,2,N.overlay.surfaces(N.receiver)),"maps unavailable");}
        finally{IsometricBridge.rendering(null);}
        Matrix4f[] matrices={new Matrix4f(NativeShadowMaps.matrix(0)),new Matrix4f(NativeShadowMaps.matrix(1)),new Matrix4f()};
        float[] texels={2*NativeShadowMaps.reach(0)/1024,2*NativeShadowMaps.reach(1)/1024,0};
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
        N.overlay.draw(N.receiver,new IsometricEffects(enabled,.55f,1,64,1024,false,0,1,1,1),NativeShadowMaps.texture(),matrices,texels,NativeShadowMaps.depthRange(),1024,2,1);
        ByteBuffer after=N.read();image=new byte[after.capacity()];after.get(image);int dark=0;
        for(int i=0;i<N.W*N.H;i++) {
            if((image[i*4]&255)+3<(before.get(i*4)&255))dark++;
            N.require(image[i*4+3]==before.get(i*4+3),"native alpha changed");
        }
        float[] depthAfter=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthAfter);
        N.require(Arrays.equals(depthBefore,depthAfter),"native depth changed");
        ByteBuffer stencil=BufferUtils.createByteBuffer(N.W*N.H);GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencil);
        for(int i=0;i<N.W*N.H;i++)N.require(stencil.get(i)==31,"native stencil changed");
        N.require(GL11.glGetError()==0,"GL error at frame="+N.number);return dark;
    }
}
