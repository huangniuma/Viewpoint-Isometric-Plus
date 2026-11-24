import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import vpiso.*;

/** Exercises the production gbuffer rewrite and scope uniforms, including discarded water depth. */
public final class ModelWaterMaterialProbe {
    public static void main(String[] args) {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(16, 16, "VPISO native water material", 0, 0);
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            String vertex = """
                #version 330
                flat out vec3 vpIsoTreeRoot;
                flat out int vpIsoPlants;
                void main() {
                  vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
                  gl_Position=vec4(p*2.0-1.0,0.0,1.0);
                  vpIsoTreeRoot=vec3(0.0); vpIsoPlants=0;
                }
                """;
            String fragment = IsometricShaders.rewrite("gbuffer.frag", """
                #version 330
                #define FLAG_WATER 2048
                uniform int vFlags;
                uniform vec3 vPos, vLocal, vNormal;
                out vec4 fragColor;
                void main() { fragColor=vec4(1.0); }
                """);
            GlProgram.onUse(IsometricShaders::onUse);
            GlProgram program = GlProgram.fromSources("VPISO water material ownership",
                    new GlProgram.Sources(vertex,null,null,null,fragment), Map.of(),Map.of(),List.of("production rewrite"));
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, GL30.glGenFramebuffers());
            int color=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            int depth=GL30.glGenRenderbuffers(); GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL14.GL_DEPTH_COMPONENT24,1,1);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete FBO");
            GL11.glViewport(0,0,1,1); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LESS);
            GL11.glDepthMask(true); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_CULL_FACE);
            Frame frame=new Frame();
            IsometricBridge.rendering(null); check(program,2048,true,"ordinary VP");
            IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT.withWater(true));
            if (IsometricBridge.waterRendering()) throw new AssertionError("Model world still runs duplicate VP water optics");
            check(program,2048,false,"model world water");
            check(program,4,true,"model world land");
            check(program,1024,true,"model world glass");
            IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,false);
            check(program,2048,true,"native shader disabled/pending fallback");
            IsometricBridge.rendering(frame); check(program,2048,true,"native shadow capture");
            if (IsometricBridge.usesNativeWater()) throw new AssertionError("Water ownership scope leaked");
            IsometricBridge.rendering(null); check(program,2048,true,"ordinary VP after model");
            if (GL11.glGetError()!=0) throw new AssertionError("Material scope GL error");
            System.out.println("Model native-water material GPU PASS: production gbuffer rewrite/onUse; water writes neither color nor depth only in actual model-world scope; land/glass and ordinary VP preserved; model wave branch disabled, scope restored.");
        } finally { IsometricBridge.rendering(null); GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void check(GlProgram program,int flags,boolean rendered,String label) {
        GL11.glClearColor(0,0,0,0); GL11.glClearDepth(1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        program.use(); program.setInt("vFlags",flags); GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer pixel=BufferUtils.createFloatBuffer(4), depth=BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
        GL11.glReadPixels(0,0,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
        if (Math.abs(pixel.get(0)-(rendered?1:0))>1e-6 || Math.abs(depth.get(0)-(rendered?.5:1))>1e-6)
            throw new AssertionError(label+": color="+pixel.get(0)+", depth="+depth.get(0));
    }
}
