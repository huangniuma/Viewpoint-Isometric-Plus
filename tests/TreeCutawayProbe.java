import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import viewpoint.platform.GlProgram;
import vpiso.NativeTreeCutaway;
import vpiso.NativeViewData;

/** Production suppression GLSL; original sprites supply the main-view trees now. */
public final class TreeCutawayProbe {
    private static final int X=11,Y=19,W=192,H=128;
    private static final int TREE=3<<19,MODEL_TREE=1<<28;
    private static GlProgram program;
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(256,256,"VPISO native tree suppression fixture",0,0);
        if (window==0) throw new AssertionError("Hidden GL window unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            String vertex="""
                #version 450 compatibility
                void main() {
                  vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
                  gl_Position=vec4(p*2.0-1.0,0.0,1.0);
                }
                """;
            String fragment="#version 450 compatibility\n"+NativeViewData.GLSL+NativeTreeCutaway.GLSL+"""
                uniform int plants;
                uniform int flags;
                uniform vec3 rootScene;
                out vec4 result;
                void main() { vpIsoCutTree(rootScene,plants,flags); result=vec4(1.0); }
                """;
            program=GlProgram.fromSources("VPISO tree suppression fixture",
                    new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of("result",0),List.of("tree suppression fixture"));
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,GL30.glGenFramebuffers());
            int color=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,256,256,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new AssertionError("Tree framebuffer incomplete");
            GL13.glActiveTexture(GL13.GL_TEXTURE0+142);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,GL11.glGenTextures());
            GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA8,1,1,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glViewport(X,Y,W,H); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glClearColor(0,0,0,0);
            program.use();
            program.setInt("vpIsoNativeTiles",142); program.set("vpIsoNativeOn",1f);
            program.set("vpIsoNativeCam",0f,0f,0f); program.set("vpIsoNativeOrigin",0f,0f,0f);
            program.set("vpIsoNativeSize",1f,1f,1f); program.set("rootScene",-.5f,0f,-.5f);
            rule(31); check("native tree",1,TREE,false);
            rule(1); check("any native tree marker",1,TREE,false);
            rule(0); check("no native tree",1,TREE,true);
            rule(31); check("attached tree leaves",1,TREE|2,false);
            check("grass on tree square",1,TREE|4096,true);
            check("facing plant on tree square",1,TREE|512,true);
            check("non-tree plant",1,1<<19,true);
            check("ordinary geometry",0,TREE,true);
            program.set("rootScene",-1.5f,0f,-.5f);
            check("root outside native tile",1,TREE,true);
            program.set("rootScene",-.5f,0f,-.5f);
            check("ordinary model on tree square",2,0,true);
            check("tree-shaped flags on untagged model",2,TREE,true);
            rule(0); program.set("rootScene",-100f,30f,-100f);
            check("tagged model tree without tile/root match",2,MODEL_TREE,false);
            check("tagged model tree attachment",2,MODEL_TREE|2,false);
            check("same-square ordinary grass model",2,4096,true);
            program.set("vpIsoNativeOn",0f);
            check("disabled tagged model",2,MODEL_TREE,true);
            program.set("rootScene",-.5f,0f,-.5f); rule(31);
            check("disabled plant tree",1,TREE,true);
            if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("Tree GL error");
            System.out.println("Tree suppression GPU PASS: "+checks+" cases at offset/scaled viewport; actual production GLSL, native-present/absent, leaves, grass/facing/geometry, model identity and disabled branches. Native tree rendering and map appearance are separate tests.");
        } finally {
            if (program!=null) program.delete();
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    private static void check(String label,int plants,int flags,boolean preserved) {
        program.setInt("plants",plants); program.setInt("flags",flags);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);
        GL11.glReadPixels(X,Y,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        int expected=preserved?255:0;
        for (int i=0;i<W*H;i++) for (int c=0;c<4;c++)
            if ((pixels.get(i*4+c)&255)!=expected) throw new AssertionError(label+" pixel "+i+" component "+c+" expected "+expected+" got "+(pixels.get(i*4+c)&255));
        checks++;
    }

    private static void rule(int encoded) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0+142);
        ByteBuffer pixel=BufferUtils.createByteBuffer(4).put((byte)255).put((byte)207).put((byte)224).put((byte)(128|encoded)).flip();
        GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,0,0,0,1,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
    }
}
