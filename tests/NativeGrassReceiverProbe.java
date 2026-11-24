import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Actual native tile/chunk masks, real VP blades/maps and production drawer composition. */
public final class NativeGrassReceiverProbe {
    static final int W=128;
    static int fbo,chunk,chunkColor,chunkDepth,depthMap,floor,foreground,tile,compose,vao;
    static Frame frame;
    static NativeReceiver receiver;
    static Object drawer;
    static byte[] before;
    static int serial;
    record Sample(byte[] color,float[] depth) {}

    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"native grass receiver",0,0);
        URLClassLoader oldDrawer=null;
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            Method fixture=NativeWaterPipelineProbe.class.getDeclaredMethod("fixture");fixture.setAccessible(true);fixture.invoke(null);
            TestShaderPacks.select(args[0],args[0].equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off"));Gl.init();
            NativeGrassAppearanceProbe.setting("grass.quality","Medium");NativeGrassAppearanceProbe.setting("grass.style","Realistic");
            frame=NativeSurfaceReceiverProbe.frame();frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;frame.scene.time=1;
            // The selected shader evaluates native receivers against VP's real light grid.
            // Put the visible native patch inside chunk0 and publish that chunk's actual light upload.
            frame.scene.originX=frame.scene.originZ=-3;
            for(int y=-1;y<1;y++)for(int x=-1;x<1;x++) {
                int c=frame.scene.grass.chunks++;frame.scene.grass.chunkX[c]=x;frame.scene.grass.chunkY[c]=y;
                int value=(Integer)GameAccess.call("viewpoint.light.GrassMap","value",null,new Class<?>[]{int.class,int.class,boolean.class,boolean.class,int.class,int.class},1,2,true,false,x,y);
                Arrays.fill(frame.scene.grass.values,c*64,c*64+64,value);
            }
            drawer=new NativeOverlayDrawer(frame);
            if(!args[1].equals("current")) {
                // Isolate the pinned 051 drawer: its late, unmasked two-argument grass call.
                // Keep the pinned JAR open for its own lazily resolved dependencies.
                oldDrawer=new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()},NativeGrassReceiverProbe.class.getClassLoader()) {
                    @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                        if(!name.equals("vpiso.NativeOverlayDrawer"))return super.loadClass(name,resolve);
                        Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
                    }
                };
                drawer=oldDrawer.loadClass("vpiso.NativeOverlayDrawer").getConstructor(Frame.class).newInstance(frame);
            }
            receiver=(NativeReceiver)drawer.getClass().getField("receiver").get(drawer);
            receiver.fit(new double[][]{{.5,.32,.5},{.62,.26,.4985566458},{.38,.26,.4985566458},{.5,.68,.4971132916}});
            NativeGrassAppearanceProbe.receiver=receiver;
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            chunk=target();chunkColor=attached(GL30.GL_COLOR_ATTACHMENT0);chunkDepth=attached(GL30.GL_DEPTH_ATTACHMENT);
            fbo=target();
            depthMap=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthMap);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R32F,W,W,0,GL11.GL_RED,GL11.GL_FLOAT,NativeGrassAppearanceProbe.floorDepth());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            floor=NativeGroundProbe.solid(80,110,45,255);
            tile=NativeGroundProbe.link(NativeGroundProbe.VERTEX,NativeGroundShaders.rewrite("tileWithDepth.frag",Files.readString(Path.of(args[2],"media/shaders/tileWithDepth.frag"))));
            compose=NativeGroundProbe.link(NativeGroundProbe.VERTEX,NativeGroundShaders.rewrite("chunkShader.frag",Files.readString(Path.of(args[2],"media/shaders/chunkShader.frag"))));
            GameAccess.staticCall("viewpoint.render.TilePalette","update");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,(Integer)GameAccess.field("viewpoint.render.TilePalette","texture",null));
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,1,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{.2f,.5f,.1f,1});
            // A real elevated VP mesh casts a broad shadow across the grass.
            NativeSurfaceReceiverProbe.opaque=0;
            Object canopy=NativeSurfaceReceiverProbe.mesh(frame.scene,0,NativeSurfaceReceiverProbe.horizontal(3.4f,2f));
            ByteBuffer light=(ByteBuffer)GameAccess.call("viewpoint.render.ChunkMeshData","spareLight",canopy,new Class<?>[0]);light.clear();
            for(int i=0;i<800;i++)light.put((byte)180).put((byte)180).put((byte)180).put((byte)255);
            light.flip();GameAccess.call("viewpoint.render.ChunkMeshData","publishLight",canopy,new Class<?>[]{ByteBuffer.class},light);
            Sample clear=draw(false,false),shadow=draw(true,false);
            int changed=0,dark=0;
            float[] floorDepth=NativeGrassAppearanceProbe.floorDepth();
            for(int i=0;i<W*W;i++)if(clear.depth[i]<floorDepth[i]-1e-6f) {
                changed++;int a=luma(clear.color,i),b=luma(shadow.color,i);if(a-b>12)dark++;
            }
            if(changed<100)throw new AssertionError("No grass in production drawer: "+changed);
            int overwritten=0,edgeError=0,opaque=0,edges=0;
            for(boolean wall:new boolean[]{false,true}) {
                ByteBuffer tex=BufferUtils.createByteBuffer(W*W*4);
                for(int y=0;y<W;y++)for(int x=0;x<W;x++) {
                    int alpha=alpha(x,y);tex.put((byte)Math.round(190*alpha/255f)).put((byte)Math.round(40*alpha/255f)).put((byte)Math.round(65*alpha/255f)).put((byte)alpha);
                }
                tex.flip();foreground=NativeGroundProbe.texture(W,W,tex);
                Sample object=draw(false,wall);
                for(int i=0;i<W*W;i++) {
                    int a=alpha(i%W,i/W);
                    if(a==255) {
                        opaque++;if(error(object.color,before,i)>0)overwritten++;
                    } else if(a==128 && clear.depth[i]<.99f) {
                        edges++;
                        for(int c=0;c<3;c++) {
                            int expected=Math.round(new int[]{190,40,65}[c]*a/255f+(clear.color[4*i+c]&255)*(1-a/255f));
                            if(Math.abs((object.color[4*i+c]&255)-expected)>3)edgeError++;
                        }
                    }
                }
                GL11.glDeleteTextures(foreground);foreground=0;
            }
            System.out.println("Grass receiver measured: grass="+changed+", shadowed="+dark+", opaque="+opaque+", overwritten="+overwritten+", alpha edges="+edges+", edge errors="+edgeError);
            if(args.length>1 && !args[1].equals("current")) {
                if(overwritten<100 || dark>changed/20)throw new AssertionError("Pinned 051 did not reproduce both defects");
                System.out.println("Pinned 051 grass receiver negative control PASS: missing shadows and foreground overwrite reproduced.");
            } else {
                if(dark<100)throw new AssertionError("Grass did not receive real VP shadow map: "+dark);
                if(overwritten!=0 || edgeError!=0)throw new AssertionError("Grass overwrote native material/alpha: "+overwritten+"/"+edgeError);
                if(GL11.glGetError()!=0)throw new AssertionError("Grass receiver GL error");
                System.out.println("Native grass receiver GPU PASS: "+args[0]+"; actual VP maps and production drawer; native chunk mask, equal floor/object depth, opaque objects/walls and premultiplied alpha edges.");
            }
        } finally {
            try {if(oldDrawer!=null)oldDrawer.close();}
            finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        }
    }
    static int alpha(int x,int y){return x>42&&x<85?255:x>90?128:0;}
    static int luma(byte[] color,int i){return (color[4*i]&255)+(color[4*i+1]&255)+(color[4*i+2]&255);}
    static int error(byte[] a,byte[] b,int i){int e=0;for(int c=0;c<4;c++)e+=Math.abs((a[4*i+c]&255)-(b[4*i+c]&255));return e;}
    static int attached(int slot){return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);}
    static int target(){int result=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,result);for(boolean depth:new boolean[]{false,true}){int t=NativeGrassAppearanceProbe.attachment(depth);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);}return result;}
    static void tile(int texture,boolean isFloor,boolean wall) {
        GL20.glUseProgram(tile);if(wall)NativeGround.setWall(true);else NativeGround.setFloor(isFloor);
        GL20.glUniform1i(GL20.glGetUniformLocation(tile,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(tile,"DEPTH"),1);
        GL20.glUniform1f(GL20.glGetUniformLocation(tile,"zDepthBlendZ"),0);GL20.glUniform1f(GL20.glGetUniformLocation(tile,"zDepthBlendToZ"),1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthMap);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
    static Sample draw(boolean shadow,boolean wall)throws Exception {
        GL30.glBindVertexArray(vao);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,W,W);
        NativeGround.begin(true);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,chunk);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);NativeGround.chunkBegin(chunkObject(),true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        tile(floor,true,false);if(foreground!=0)tile(foreground,false,wall);NativeGround.chunkEnd();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glDisable(GL11.GL_BLEND);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL20.glUseProgram(compose);NativeGround.program(compose);
        Object command=GameAccess.type("zombie.core.textures.TextureDraw").getConstructor().newInstance();GameAccess.set(command.getClass().getName(),"tex1",command,nativeDepthTexture());NativeGround.chunkShader(command);
        GL20.glUniform1i(GL20.glGetUniformLocation(compose,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(compose,"DEPTH"),1);GL20.glUniform1i(GL20.glGetUniformLocation(compose,"useTexture"),1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,chunkColor);GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,chunkDepth);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);before=image();
        drawer.getClass().getField("effects").set(drawer,new IsometricEffects(shadow,.7f,0,64,1024,false,0,1,1,1,false,true));frame.number=++serial;
        drawer.getClass().getMethod("render").invoke(drawer);frame.scene.grass.chunks=0;
        if((Boolean)GameAccess.field("vpiso.IsometricBridge","failed",null))throw new AssertionError("Drawer failed");
        float[] depths=new float[W*W];GL11.glReadPixels(0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
        return new Sample(image(),depths);
    }
    static Object nativeDepthTexture()throws Exception {
        Object texture=NativeSurfaceReceiverProbe.allocate(GameAccess.type("zombie.core.textures.Texture"));
        Object id=NativeSurfaceReceiverProbe.allocate(GameAccess.type("zombie.core.textures.TextureID"));GameAccess.set(id.getClass().getName(),"id",id,chunkDepth);GameAccess.set(texture.getClass().getName(),"dataid",texture,id);return texture;
    }
    static Object chunkObject()throws Exception{Object result=NativeSurfaceReceiverProbe.allocate(GameAccess.type("zombie.iso.fboRenderChunk.FBORenderChunk"));GameAccess.set(result.getClass().getName(),"depth",result,nativeDepthTexture());return result;}
    static byte[] image(){ByteBuffer bytes=BufferUtils.createByteBuffer(W*W*4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);byte[] result=new byte[bytes.remaining()];bytes.get(result);return result;}
}
