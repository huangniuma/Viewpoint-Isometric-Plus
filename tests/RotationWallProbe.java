import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import vpiso.*;

/** Actual camera and production cutaway shader: foreground obstruction only, at all four yaws. */
public final class RotationWallProbe {
    public static void main(String[] args) {
        if(!GLFW.glfwInit()) throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(8,8,"VPISO rotating walls",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            String header="""
                #version 330
                uniform float vpIsoNativeOn, capability;
                uniform vec3 vpIsoNativeCam, position;
                vec4 vpIsoNativeRule(vec3 p) { return vec4(1,0,0,capability/255.0); }
                """;
            GlProgram program=GlProgram.fromSources("VPISO rotating wall regression",
                    new GlProgram.Sources(NativeVision.VERTEX,null,null,null,header+NativeWallCutaway.GLSL+"""
                    out vec4 color;
                    void main() {
                      vpIsoCutWall(position,vec3(0,position.y,0),vec3(1,0,0),0);
                      color=vec4(1);
                    }
                    """),Map.of(),Map.of(),List.of("production rotating wall GLSL"));
            GL30.glBindVertexArray(GL30.glGenVertexArrays());GL11.glViewport(0,0,8,8);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glClearColor(0,0,0,0);
            for(int q=0;q<4;q++) {
                Matrix4f projection=new Matrix4f(),view=new Matrix4f();float[] eye=new float[3];
                IsoCameraMath.matrices(new IsoCameraMath.Fit(.5,.5,8),.5f,.5f,1920,1080,projection,view,eye,q);
                var camera=new RotationCamera.Snapshot(projection,view,.5f,.5f,0,1920,1080,1,q);
                var previous=RotationLayers.begin(camera);
                program.use();NativeWallCutaway.onUse(program);
                program.set("vpIsoNativeOn",1);program.set("vpIsoNativeCam",.5f,.5f,0);program.set("capability",224);
                float side=Math.signum(eye[0]);
                check(program,side*.5f,false,"foreground wall q="+q);
                check(program,-side*.5f,true,"background wall q="+q);
                check(program,side*20.5f,true,"wall outside body region q="+q);
                program.set("capability",128);check(program,side*.5f,true,"non-cutaway object q="+q);
                RotationLayers.end(previous);
            }
            if(GL11.glGetError()!=0) throw new AssertionError("GL error");
            program.delete();
            System.out.println("Rotated wall GPU PASS: 16 foreground/background/radius/capability cases with actual four camera matrices and production shader; native fixed-view mask remains separately tested.");
        } finally { GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate(); }
    }
    private static void check(GlProgram program,float x,boolean visible,String label) {
        program.set("position",x,1.1f,0f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);ByteBuffer pixel=BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(3,3,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
        if((pixel.get(0)&255)!=(visible?255:0)) throw new AssertionError(label);
    }
}
