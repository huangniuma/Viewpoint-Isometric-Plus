import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;
import zombie.iso.*;
import zombie.core.textures.TextureDraw;

/** Native draw provenance, linear metadata transfer and persistent cache ownership. */
public final class NativeWallSources071Probe {
    static final int SIZE=16;
    static int output,a,b,tile,composite;
    static Object chunk,transfer;
    static String game;
    public static void main(String[] args)throws Exception {
        game=args[0];if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(SIZE,SIZE,"071 native wall sources",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            output=fbo();a=fbo();b=fbo();
            tile=NativeGroundProbe.link(NativeGroundProbe.VERTEX,NativeGroundShaders.rewrite("tileWithDepth.frag",Files.readString(Path.of(game,"media/shaders/tileWithDepth.frag"))));
            composite=NativeGroundProbe.link(NativeGroundProbe.VERTEX,NativeGroundShaders.rewrite("chunkShader.frag",Files.readString(Path.of(game,"media/shaders/chunkShader.frag"))));
            NativeGroundProbe.program=tile;NativeGroundProbe.depthTexture=NativeGroundProbe.solid(128,128,128,255);
            chunk=GameAccess.type("zombie.iso.fboRenderChunk.FBORenderChunk").getConstructor().newInstance();
            Object texture=GameAccess.type("zombie.core.textures.Texture").getConstructor().newInstance();
            Object id=NativeSurfaceReceiverProbe.allocate(GameAccess.type("zombie.core.textures.TextureID"));
            GameAccess.set("zombie.core.textures.TextureID","id",id,NativeGroundProbe.depthTexture);
            GameAccess.set("zombie.core.textures.Texture","dataid",texture,id);GameAccess.set(chunk.getClass().getName(),"depth",chunk,texture);
            transfer=GameAccess.type("zombie.core.textures.TextureDraw").getConstructor().newInstance();GameAccess.set(transfer.getClass().getName(),"tex1",transfer,texture);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);NativeGround.begin(true);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,a);NativeGround.chunkBegin(chunk,true);
            IsoObject object=object(7,9,-2);TextureDraw.GenericDrawer queued=queue(object,null,zombie.iso.sprite.shapers.WallShaperW.instance);
            Object square=GameAccess.field("zombie.iso.IsoObject","square",object);
            GameAccess.set("zombie.iso.IsoGridSquare","x",square,700);GameAccess.set("zombie.iso.IsoGridSquare","z",square,4);
            queued.render();int owner=owner();assertPlane(owner,7,0,-2,1);
            int opaque=NativeGroundProbe.solid(100,130,160,255);
            draw(opaque,false);assertMetadata(owner,1);
            long revision=NativeGround.wallRevision();NativeGround.chunkBegin(chunk,false);
            require(NativeGround.wallRevision()==revision,"Incremental chunk dropped live source IDs");
            NativeGround.chunkEnd();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL20.glUseProgram(composite);NativeGround.chunkShader(transfer);
            // Transferred metadata is persistent in its destination even if the source clears.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,a);NativeGround.chunkBegin(chunk,true);
            assertPlane(owner,7,0,-2,1);
            queue(object(8,9,-2),null,zombie.iso.sprite.shapers.WallShaperW.instance).render();int other=owner();require(other!=owner,"Reused an ID still held by destination");
            NativeGround.finish();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);NativeGround.begin(true);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,b);NativeGround.chunkBegin(chunk,true);
            queue(object(7,10,-2),null,zombie.iso.sprite.shapers.WallShaperW.instance).render();int recycled=owner();require(recycled==owner,"Cleared source ID was not recycled");
            queue(object(7,11,-2),null,zombie.iso.sprite.shapers.WallShaperW.instance).render();require(owner()==recycled,"Coplanar neighboring wall created zoom seam");
            queue(object(7,11,-2),null,zombie.iso.sprite.shapers.WallShaperN.instance).render();require(owner()!=recycled,"Perpendicular planes were merged");
            queue(object(7,11,-2),IsoDirections.NW,null).render();require(owner()==1,"Ambiguous corner borrowed a plane");
            // Straight and premultiplied native draw paths carry the same weighted moments.
            for(boolean straight:new boolean[]{false,true})for(int alpha:new int[]{255,128,1}) {
                NativeGround.chunkBegin(chunk,true);queue(object(7,9,-2),IsoDirections.W,null).render();int source=owner();
                int rgba=straight?NativeGroundProbe.solid(100,130,160,alpha):NativeGroundProbe.solid(100*alpha/255,130*alpha/255,160*alpha/255,alpha);
                GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{0,0,0,0});draw(rgba,straight);assertMetadata(source,alpha/255f);GL11.glDeleteTextures(rgba);
            }
            NativeGround.chunkBegin(chunk,true);queue(object(7,9,-2),null,zombie.iso.sprite.shapers.WallShaperN.instance).render();int north=owner();assertPlane(north,0,9,-2,2);
            draw(opaque,false);NativeGround.chunkEnd();
            // Actual native chunk transfer uses linear signed moments with original UV/alpha.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL20.glUseProgram(composite);NativeGround.program(composite);NativeGround.chunkShader(transfer);
            GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DEPTH"),1);
            bind(0,opaque);bind(1,NativeGroundProbe.depthTexture);GL11.glDisable(GL11.GL_BLEND);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);assertMetadata(north,1);
            NativeGround.finish();NativeGround.release(a);NativeGround.release(b);NativeGround.release(output);
            capacity();pzo();
            require(GL11.glGetError()==0,"GL error");
            System.out.println("Native wall sources071 GPU PASS: null direction/actual shaper, immutable source, negative floor, both alpha conventions, native and PZO linear transfer, coplanar zoom continuity, incremental/destination-held IDs, capacity fallback and recycling.");
        } finally {NativeGround.finish();NativeGround.release(a);NativeGround.release(b);NativeGround.release(output);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void capacity()throws Exception {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);NativeGround.begin(true);
        Set<Integer> live=new HashSet<>();
        for(int i=0;i<1022;i++){queue(object(i,0,0),IsoDirections.W,null).render();require(owner()>=2 && live.add(owner()),"Capacity reused live wall source");}
        queue(object(5000,0,0),IsoDirections.W,null).render();require(owner()==1,"Capacity fallback lost wall classification");
        for(int id:live)require(data()[id*4+3]==1,"Capacity evicted live source");
        NativeGround.finish();NativeGround.release(output);
    }
    static void pzo()throws Exception {
        int target=fbo(),metadata=NativeShadow068Probe.texture(GL30.GL_RGBA32F,SIZE,SIZE);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,metadata,0);
        float second=NativeMetadata074.v2()?1+1023*1023:-1023*1023;
        float[] signed=new float[SIZE*SIZE*4];for(int i=0;i<SIZE*SIZE;i++){signed[i*4]=-1;signed[i*4+1]=1023;signed[i*4+2]=second;signed[i*4+3]=1;}
        int source=NativeShadow068Probe.texture(GL30.GL_RGBA32F,SIZE,SIZE);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_FLOAT,signed);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        int white=NativeGroundProbe.solid(180,180,180,255),zero=NativeGroundProbe.solid(0,0,0,0);PzoGroundShaderProbe.emptyAux=zero;
        for(String name:new String[]{"sway-prefetch-false","sway-prefetch-true","spritefilter","sway-spritefilter"}) {
            Path path=Path.of("research/build026-pzo-generated/"+name+"-generated.frag");
            String original=Files.readString(path);int program=PzoGroundShaderProbe.link(NativeGroundShaders.rewrite("pzopt_swChunk.frag",original));
            for(int motion=0;motion<2;motion++)for(float scale:new float[]{.75f,1.5f,4.5f}) {
                PzoGroundShaderProbe.draw(program,white,white,NativeGroundProbe.depthTexture,zero,zero,SIZE,motion,scale,0,false);
                bind(5,source);GL20.glUniform1i(GL20.glGetUniformLocation(program,"vpIsoOcclusionTexture"),5);GL20.glUniform1f(GL20.glGetUniformLocation(program,"vpIsoGrassMaskOn"),1);
                GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,0,0,0,GL30.GL_COLOR_ATTACHMENT6});GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                FloatBuffer value=read();float weight=value.get(3);
                require(weight>.1,"PZO empty transfer "+name);near(value.get(0),-weight,.001f,"PZO wall coverage "+name);
                near(value.get(1),weight*1023,.05f,"PZO first moment "+name);near(value.get(2),weight*second,32,"PZO second moment "+name);
            }
            GL20.glDeleteProgram(program);
        }
        GL30.glDeleteFramebuffers(target);GL11.glDeleteTextures(source);GL11.glDeleteTextures(metadata);
    }
    static IsoObject object(int x,int y,int z)throws Exception {
        IsoObject object=NativeGroundProbe.floorObject(true,false);Object s=GameAccess.field("zombie.iso.IsoObject","square",object);
        GameAccess.set("zombie.iso.IsoGridSquare","x",s,x);GameAccess.set("zombie.iso.IsoGridSquare","y",s,y);GameAccess.set("zombie.iso.IsoGridSquare","z",s,z);return object;
    }
    static TextureDraw.GenericDrawer queue(IsoObject object,IsoDirections direction,Object shaper) {
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null),states=GameAccess.field("zombie.core.SpriteRenderer","states",sprites);
        Object queue=GameAccess.call("zombie.core.sprite.SpriteRendererStates","getPopulatingActiveState",states,new Class<?>[0]);
        int at=((Number)GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","numSprites",queue)).intValue();NativeGround.queueWall(object,direction,shaper);
        Object command=java.lang.reflect.Array.get(GameAccess.field("zombie.core.sprite.GenericSpriteRenderState","sprite",queue),at);
        return (TextureDraw.GenericDrawer)GameAccess.field("zombie.core.textures.TextureDraw","drawer",command);
    }
    static int owner(){return ((Number)GameAccess.field("vpiso.NativeGround","wallOwner",null)).intValue();}
    static float[] data(){FloatBuffer b=BufferUtils.createFloatBuffer(4096);NativeGround.wallData(b);float[] a=new float[4096];b.get(a);return a;}
    static void assertPlane(int id,float x,float y,float z,float axis){float[] d=data();near(d[id*4],x,0,"source x");near(d[id*4+1],y,0,"source y");near(d[id*4+2],z,0,"source floor");near(d[id*4+3],axis,0,"source axis");}
    static int fbo(){int f=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,NativeGroundProbe.texture(SIZE,SIZE,null),0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);return f;}
    static void draw(int texture,boolean straight){GL11.glViewport(0,0,SIZE,SIZE);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(straight?GL11.GL_SRC_ALPHA:GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);NativeGroundProbe.drawQueued(texture);}
    static void assertMetadata(int id,float weight){FloatBuffer m=read();near(m.get(0),-weight,.0001f,"wall coverage");near(m.get(1),id*weight,.0001f,"source first moment");near(m.get(2),(NativeMetadata074.v2()?1+id*id:-id*id)*weight,.001f,"source second moment");near(m.get(3),weight,.0001f,"native alpha coverage");}
    static FloatBuffer read(){FloatBuffer b=BufferUtils.createFloatBuffer(4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT6);GL11.glReadPixels(SIZE/2,SIZE/2,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,b);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);return b;}
    static void bind(int unit,int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);}
    static void near(float a,float b,float e,String message){NativeGroundProbe.near(a,b,e,message);}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
