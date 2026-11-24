import java.lang.reflect.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;
import viewpoint.core.Frame;
import zombie.core.ShaderHelper;

/** Exercise the packaged drawer boundary with a native shader still in use. */
public final class NativeShaderState080Probe {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(16,16,"native shader cache boundary",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            int program=link();
            var constructor=zombie.core.opengl.ShaderProgram.class.getDeclaredConstructor(String.class,boolean.class,boolean.class);
            constructor.setAccessible(true);
            var nativeProgram=constructor.newInstance("native-cache080",false,false);
            var id=zombie.core.opengl.ShaderProgram.class.getDeclaredField("shaderId");id.setAccessible(true);id.setInt(nativeProgram,program);
            var uniform=new zombie.core.opengl.ShaderProgram.Uniform();
            uniform.name="ModelViewProjection";uniform.type=GL20.GL_FLOAT_MAT4;uniform.loc=GL20.glGetUniformLocation(program,uniform.name);uniform.size=1;
            var uniforms=zombie.core.opengl.ShaderProgram.class.getDeclaredField("uniformsByName");uniforms.setAccessible(true);
            @SuppressWarnings("unchecked") var nativeUniforms=(java.util.Map<String,zombie.core.opengl.ShaderProgram.Uniform>)uniforms.get(nativeProgram);
            nativeUniforms.put(uniform.name,uniform);
            zombie.core.opengl.ShaderPrograms.getInstance().registerProgram(nativeProgram);
            var core=zombie.core.Core.getInstance();
            core.modelViewMatrixStack.push(new org.joml.Matrix4f());
            core.projectionMatrixStack.push(new org.joml.Matrix4f());
            var cached=ShaderHelper.class.getDeclaredField("currentlyBound");cached.setAccessible(true);
            NativeOverlayDrawer drawer=new NativeOverlayDrawer(new Frame());
            drawer.effects=new IsometricEffects(false,0,1,64,2048,false,0,1,1,1);
            drawer.releaseScene=false;
            for(int frame=0;frame<24;frame++) {
                GL20.glUseProgram(program);cached.setInt(null,program);
                boolean nativeDefault=(frame&1)==0;
                GameAccess.set("zombie.core.DefaultShader","isActive",null,nativeDefault);
                drawer.render();
                if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)throw new AssertionError("Native GPU program changed");
                float scale=.25f+frame*.1f;
                core.projectionMatrixStack.peek().identity().scaling(scale,scale,1);
                ShaderHelper.setModelViewProjection();
                float[] matrix=new float[16];GL20.glGetUniformfv(program,GL20.glGetUniformLocation(program,"ModelViewProjection"),matrix);
                if(Math.abs(matrix[0]-scale)>.00001f)throw new AssertionError("Native zoom matrix skipped after drawer: expected="+scale+" actual="+matrix[0]+" cache="+cached.getInt(null));
                if(cached.getInt(null)!=program)throw new AssertionError("Native shader cache lost after drawer: actual="+program+" cached="+cached.getInt(null));
                if(!Boolean.valueOf(nativeDefault).equals(GameAccess.field("zombie.core.DefaultShader","isActive",null)))throw new AssertionError("Default shader state changed");
                if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            }
            System.out.println("Native shader state080 GPU PASS: 24 actual drawer boundaries preserve GPU program, native binding cache, default-shader status and subsequent native zoom matrix updates.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static int shader(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(s));return s;}
    static int link(){int p=GL20.glCreateProgram();GL20.glAttachShader(p,shader(GL20.GL_VERTEX_SHADER,"#version 330\nuniform mat4 ModelViewProjection;void main(){gl_Position=ModelViewProjection*vec4(.25,.5,0,1);}"));GL20.glAttachShader(p,shader(GL20.GL_FRAGMENT_SHADER,"#version 330\nout vec4 color;void main(){color=vec4(1);}"));GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(p));return p;}
}
