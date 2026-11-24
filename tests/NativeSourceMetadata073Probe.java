import java.nio.*;
import java.nio.file.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Actual native shader metadata producer, including original alpha/depth and floor/wall priority. */
public final class NativeSourceMetadata073Probe {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(16,16,"073 source metadata",0,0);
        int fbo=0;
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            fbo=NativeWallSources071Probe.fbo();
            NativeGroundProbe.program=NativeGroundProbe.link(NativeGroundProbe.VERTEX,NativeGroundShaders.rewrite("tileWithDepth.frag",Files.readString(Path.of(args[0],"media/shaders/tileWithDepth.frag"))));
            NativeGroundProbe.depthTexture=NativeGroundProbe.solid(128,128,128,255);
            NativeGround.begin(true);
            for(boolean straight:new boolean[]{false,true})for(int alpha:new int[]{255,128,1}) {
                GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{0,0,0,0});
                NativeGround.sourceBegin(new NativeSource(10641.5f,9728.5f,-2,3,new float[]{-.3f,0,-.3f,.3f,1.2f,.3f}));
                int id=NativeSource073Probe.sourceOwner();float a=alpha/255f;
                int texture=NativeGroundProbe.solid(straight?100:100*alpha/255,straight?120:120*alpha/255,straight?140:140*alpha/255,alpha);
                NativeWallSources071Probe.draw(texture,straight);
                float x=NativeMetadata074.v2()?-(2+(id&255)):-2,y=NativeMetadata074.v2()?id>>>8:id;
                float second=NativeMetadata074.v2()?x*x+y*y:-id*id;
                FloatBuffer m=NativeWallSources071Probe.read();near(m.get(0),x*a,.0001f);near(m.get(1),y*a,.0001f);near(m.get(2),second*a,.001f);near(m.get(3),a,.0001f);
                NativeGround.sourceEnd();NativeGround.setFloor(true);
                NativeGround.sourceBegin(new NativeSource(1,1,0,3,new float[]{-.3f,0,-.3f,.3f,1.2f,.3f}));
                require(NativeSource073Probe.sourceOwner()==0,"Source replaced native floor owner");NativeGround.sourceEnd();NativeGround.setFloor(false);
                NativeGround.setWall(true);NativeGround.sourceBegin(null);
                require(NativeSource073Probe.sourceOwner()==0,"Source replaced source-wall plane");NativeGround.sourceEnd();NativeGround.setFloor(false);
                GL11.glDeleteTextures(texture);
            }
            // Source identity is retained by a cached destination, not recycled at a frame boundary.
            NativeGround.sourceBegin(new NativeSource(3,3,0,4,new float[]{-.5f,0,-.5f,.5f,2,.5f}));
            int id=NativeSource073Probe.sourceOwner();NativeGround.sourceEnd();long revision=NativeGround.wallRevision();
            NativeGround.finish();NativeGround.begin(false);
            // Cleared destinations retire after the world pass; draws queued
            // earlier in this pass may still carry their source IDs.
            NativeGround.finish();NativeGround.begin(false);
            int owners=NativeMetadata074.sourceOwners();
            FloatBuffer data=BufferUtils.createFloatBuffer(NativeGround.WALL_OWNERS*4+owners*12);NativeGround.ownerData(data);require(data.get((NativeGround.WALL_OWNERS+id)*4+3)==0,"Released output held stale source");
            require(NativeGround.wallRevision()>revision,"Source release did not invalidate lookup upload");
            // A full object registry must never consume the source-wall plane slots.
            int capacity=owners-2;java.util.Set<Integer> live=new java.util.HashSet<>();
            if(NativeMetadata074.v2())require(capacity==65534,"Source registry did not expand beyond old1022 capacity");
            for(int i=0;i<capacity;i++) {
                NativeGround.sourceBegin(new NativeSource(i+.5f,3.5f,0,3,new float[]{-.2f,0,-.2f,.2f,1,.2f}));
                int allocated=NativeSource073Probe.sourceOwner();
                require(allocated>=2 && allocated<owners && live.add(allocated),"Source capacity reused a live ID or exhausted early at "+i);NativeGround.sourceEnd();
            }
            NativeGround.sourceBegin(new NativeSource(4000,3,0,3,new float[]{-.2f,0,-.2f,.2f,1,.2f}));
            require(NativeSource073Probe.sourceOwner()==(NativeMetadata074.v2()?0:1),"Source capacity reused a cached live ID or lost generic fallback");NativeGround.sourceEnd();
            var wall=NativeGround.class.getDeclaredMethod("retainWall",int.class,int.class,int.class,int.class);wall.setAccessible(true);
            require((Integer)wall.invoke(null,700,0,0,1)>=2,"Object sources starved original wall reception");
            float[] edge=NativeSource.ownerKey(65535,65535,31,1023);
            require(edge[0]==16777215f && edge[1]==16777216f,"Exact24-bit source key overflow");
            require(GL11.glGetError()==0,"GL error");
            System.out.println("Native source metadata073 GPU PASS: actual native tile shader, straight/premultiplied alpha, immutable world/floor/bounds, nested source cleanup, floor and source-wall priority, cached lookup release; native draw remains unchanged.");
        }finally{NativeGround.finish();NativeGround.release(fbo);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void near(float a,float b,float e){NativeGroundProbe.near(a,b,e,"source metadata");}
    static void require(boolean c,String s){if(!c)throw new AssertionError(s);}
}
