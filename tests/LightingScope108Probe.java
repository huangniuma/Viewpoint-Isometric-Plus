import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import com.sun.management.ThreadMXBean;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Fresh107 scope versus reused108 scope on real lighting/maps, including nested GL/context restoration. */
public final class LightingScope108Probe {
    static final int[] UNITS={0,5,6,14,18,21,30,49,95,96,97,98,101,102,103,110,111,130,131,152};
    static final int[] PIXELS={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES};
    static final NativeSurfaceReceiverProbe N=NativeShadow068Probe.N;
    static final NativeShadow068Probe P=NativePlants072Probe.P;
    static Method oldOpen,newOpen;
    static GlProgram program;
    static int vao,cases;
    public static void main(String[] args)throws Exception {
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"108 lighting scope",0,0);
        try(var loader=new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()},LightingScope108Probe.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.startsWith("vpiso.NativeLighting"))return super.loadClass(name,resolve);
                Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
            }
        }) {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(args[0],args[0].equals("somber")?"quality":"high",Map.of("upscaling","off"));
            oldOpen=loader.loadClass("vpiso.NativeLighting").getMethod("open",SceneData.class,NativeReceiver.class);
            newOpen=NativeLighting.class.getMethod("open",SceneData.class,NativeReceiver.class);
            N.fixture();N.fit(0);vao=GL30.glGenVertexArrays();
            String fs=NativeLighting.fragment("""
                #version 330
                in vec2 uv;out vec4 color;
                void main(){vec3 p=vec3((uv.x-.5)*8.,.0,(uv.y-.5)*8.);
                  color=vec4(vpIsoLightingRatio(p,vec3(0,1,0),-1.0),1);}
                """);
            program=GlProgram.fromSources("108 scope parity",new GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,fs),Map.of(),Map.of(),List.of("scope108"));
            Frame f=P.frame();f.scene.timeNanos=0;
            Object floor=N.mesh(f.scene,0,N.horizontal(0,4)),roof=N.mesh(f.scene,1,P.blocker());P.publish(floor,true,0);
            for(int i=0;i<12;i++){f.number++;IsometricBridge.rendering(f);NativeShadowMaps.draw(f.scene,256,2);}
            IsometricBridge.rendering(null);ready(oldOpen,f.scene);ready(newOpen,f.scene);
            for(int i=0;i<12;i++) {
                f.scene.time=i*.37f;f.scene.cloudTime=i*.61f;f.scene.rain=(i%3)*.3f;f.scene.eyeSky=i%2;
                f.scene.originX=-3-(i%3);f.scene.skyR=.3f+i*.01f;f.scene.skyG=.4f;f.scene.skyB=.5f;
                // Frozen timeNanos gives identical uniforms despite different wall-clock call times.
                byte[] old=render(oldOpen,f.scene),current=render(newOpen,f.scene);
                require(Arrays.equals(old,current),"Fresh/reused selected lighting differs state="+i);cases++;
                nested(f.scene);cases++;
            }
            ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
            long thread=Thread.currentThread().getId();
            for(Method method:new Method[]{oldOpen,newOpen}) {
                for(int i=0;i<20;i++)try(AutoCloseable scope=(AutoCloseable)method.invoke(null,f.scene,N.receiver)){}
                long start=bean.getThreadAllocatedBytes(thread);
                for(int i=0;i<120;i++)try(AutoCloseable scope=(AutoCloseable)method.invoke(null,f.scene,N.receiver)){}
                long bytes=(bean.getThreadAllocatedBytes(thread)-start)/120;
                System.out.println("LIGHTSCOPE108 ALLOC mode="+(method==oldOpen?"baseline":"candidate")+" bytes="+bytes+"; actual nonnull open/prepare/close, no game FPS claim.");
            }
            edgeCases(f.scene);seededState(f.scene);
            Object free=GameAccess.field("vpiso.NativeLighting","FREE",null);
            for(Object scratch:(Iterable<?>)free)require(((FrameContext)GameAccess.field(scratch.getClass().getName(),"context",scratch)).scene==null,"Pool retained scene");
            require(GL11.glGetError()==0,"GL error");N.retire(floor);N.retire(roof);
            System.out.println("Lighting scope108 GPU PASS pack="+args[0]+" cases="+cases+"; exact selected lighting RGBA, fresh/reused scene/time/weather inputs, nested distinct contexts, UBO/texture/pixel/raster restoration and no retained scene.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void edgeCases(SceneData scene)throws Exception {
        Object prior=GameAccess.field("viewpoint.render.FrameUniforms","current",null);int[] before=state();
        NativeLighting.Scope empty=NativeLighting.open(null,null);require(!empty.ready(),"Null scene ready");empty.uniforms(program,0,0);empty.close();empty.close();
        require(prior==GameAccess.field("viewpoint.render.FrameUniforms","current",null)&&Arrays.equals(before,state()),"Null scene state differs");
        NativeLighting.Scope used=NativeLighting.open(scene,N.receiver);used.close();used.close();
        try(NativeLighting.Scope first=NativeLighting.open(scene,N.receiver);NativeLighting.Scope second=NativeLighting.open(scene,N.receiver)) {
            require(GameAccess.field("vpiso.NativeLighting$Scope","context",first)!=GameAccess.field("vpiso.NativeLighting$Scope","context",second),"Double close returned scratch twice");
        }
        org.joml.Matrix4f saved=new org.joml.Matrix4f(N.receiver.inverse);N.receiver.inverse.zero();
        try {require(Arrays.equals(render(oldOpen,scene),render(newOpen,scene)),"Degenerate inverse differs");}
        finally {N.receiver.inverse.set(saved);}
        cases+=3;
    }
    static void seededState(SceneData scene)throws Exception {
        int[] targets={GL11.GL_TEXTURE_2D,GL30.GL_TEXTURE_2D_ARRAY,GL12.GL_TEXTURE_3D};int[] textures=new int[3];
        int sampler=GL33.glGenSamplers(),buffer=GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,buffer);GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,1024,GL15.GL_DYNAMIC_DRAW);
        for(int unit:new int[]{1,2,4})GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER,unit,buffer,0,256);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,buffer);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);
        for(int i=0;i<targets.length;i++)textures[i]=GL11.glGenTextures();
        for(int unit:UNITS){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);for(int i=0;i<targets.length;i++)GL11.glBindTexture(targets[i],textures[i]);GL33.glBindSampler(unit,sampler);}
        GL13.glActiveTexture(GL13.GL_TEXTURE0+49);
        for(int pixel:PIXELS)GL11.glPixelStorei(pixel,pixel==GL11.GL_PACK_ALIGNMENT||pixel==GL11.GL_UNPACK_ALIGNMENT?8:1);
        GL11.glEnable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glViewport(3,4,173,119);GL11.glScissor(5,6,90,80);GL11.glDepthMask(true);GL11.glColorMask(false,true,false,true);
        try {nested(scene);cases++;} finally {
            for(int unit:UNITS){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);for(int target:targets)GL11.glBindTexture(target,0);GL33.glBindSampler(unit,0);}
            for(int texture:textures)GL11.glDeleteTextures(texture);GL33.glDeleteSamplers(sampler);
            for(int unit:new int[]{1,2,4})GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,unit,0);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,0);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL15.glDeleteBuffers(buffer);
            for(int pixel:PIXELS)GL11.glPixelStorei(pixel,pixel==GL11.GL_PACK_ALIGNMENT||pixel==GL11.GL_UNPACK_ALIGNMENT?4:0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }
    static void ready(Method method,SceneData scene)throws Exception {
        long until=System.nanoTime()+60_000_000_000L;
        while(true){Object scope=method.invoke(null,scene,N.receiver);boolean ready=(Boolean)scope.getClass().getMethod("ready").invoke(scope);((AutoCloseable)scope).close();if(ready)return;if(System.nanoTime()>until)throw new AssertionError("Lighting resources not ready");Thread.sleep(10);}
    }
    static byte[] render(Method method,SceneData scene)throws Exception {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
        GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);
        try(AutoCloseable scope=(AutoCloseable)method.invoke(null,scene,N.receiver)) {
            program.use();scope.getClass().getMethod("uniforms",GlProgram.class,int.class,int.class).invoke(scope,program,NativeShadowMaps.texture(),2);
            program.setInt("shadowsOn",1);program.set("shadowStrength",1f);program.setInt("vpIsoLampsOn",0);program.setInt("vpIsoSpotsOn",0);program.set("vpIsoNativeShadowSoftness",1f);
            GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            return NativeShadow068Probe.copy(N.read());
        }
    }
    static void nested(SceneData scene)throws Exception {
        Object previous=GameAccess.field("viewpoint.render.FrameUniforms","current",null);int[] before=state();
        try(NativeLighting.Scope outer=NativeLighting.open(scene,N.receiver)) {
            Object a=GameAccess.field("viewpoint.render.FrameUniforms","current",null);int[] outerState=state();
            try(NativeLighting.Scope inner=NativeLighting.open(scene,N.receiver)) {
                Object b=GameAccess.field("viewpoint.render.FrameUniforms","current",null);require(a!=b,"Nested scope shared context");
                inner.uniforms(program,NativeShadowMaps.texture(),2);
            }
            require(GameAccess.field("viewpoint.render.FrameUniforms","current",null)==a,"Inner context restore failed");
            require(Arrays.equals(outerState,state()),"Nested scope GL restore differs");
        }
        require(GameAccess.field("viewpoint.render.FrameUniforms","current",null)==previous,"Outer context restore failed");
        require(Arrays.equals(before,state()),"Scope GL restore differs");
    }
    static int[] state() {
        ArrayList<Integer> s=new ArrayList<>();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);s.add(active);
        for(int unit:UNITS){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);s.add(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));s.add(GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY));s.add(GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D));s.add(GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit));}
        GL13.glActiveTexture(active);
        for(int pixel:PIXELS)s.add(GL11.glGetInteger(pixel));
        for(int unit:new int[]{1,2,4}){s.add(GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING,unit));s.add((int)GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START,unit));s.add((int)GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE,unit));}
        for(int name:new int[]{GL31.GL_UNIFORM_BUFFER_BINDING,GL21.GL_PIXEL_PACK_BUFFER_BINDING,GL21.GL_PIXEL_UNPACK_BUFFER_BINDING,GL11.GL_PACK_ALIGNMENT,GL11.GL_UNPACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_UNPACK_ROW_LENGTH,GL30.GL_DRAW_FRAMEBUFFER_BINDING,GL30.GL_READ_FRAMEBUFFER_BINDING,GL30.GL_VERTEX_ARRAY_BINDING,GL20.GL_CURRENT_PROGRAM})s.add(GL11.glGetInteger(name));
        for(int flag:new int[]{GL11.GL_BLEND,GL11.GL_SCISSOR_TEST,GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_DEPTH_WRITEMASK})s.add(GL11.glGetBoolean(flag)?1:0);
        for(int key:new int[]{GL11.GL_VIEWPORT,GL11.GL_SCISSOR_BOX,GL11.GL_COLOR_WRITEMASK}){IntBuffer b=org.lwjgl.BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(key,b);for(int i=0;i<4;i++)s.add(b.get(i));}
        return s.stream().mapToInt(Integer::intValue).toArray();
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
