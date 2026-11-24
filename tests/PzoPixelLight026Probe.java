import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.NativeGroundShaders;

/** Compile the installed PZO pixel-light chunk source, including its optional depth-only fetch. */
public final class PzoPixelLight026Probe {
    public static void main(String[] args) throws Exception {
        PzoGroundShaderProbe.game=Path.of(args[0]);
        Path output=Path.of(args[1]); Files.createDirectories(output);
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(8,8,"PZO actual pixel light",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            for(int skip:new int[]{0,1}) {
                var loader=new PzoGroundShaderProbe.FixtureLoader();
                Class<?> config=loader.loadClass("pzopt.Config");
                for(Field field:config.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==String.class){
                    field.setAccessible(true);field.set(null,"");
                }
                PzoGroundShaderProbe.config(config,"DEV_COMPOSITE_EMPTY_SKIP",skip);
                PzoGroundShaderProbe.config(config,"PPL_SHADOW_MODE","map");
                PzoGroundShaderProbe.config(config,"PPL_MODE","composite");
                for(String flag:new String[]{"PPL_OWN_LEVEL_LIGHTS","PPL_TEXEL_POS","PPL_TEXEL_HEIGHT",
                        "PPL_FLOOR_SNAP","PPL_SEEN_EDGE","PPL_UNSEEN_AMBIENT","PPL_UNSEEN_BLEND","PPL_WALL_EDGE"})
                    PzoGroundShaderProbe.config(config,flag,true);
                PzoGroundShaderProbe.config(config,"PPL_NORMAL_SPAN",2);
                PzoGroundShaderProbe.config(config,"PPL_NORMAL_SPAN_WIDE",4);
                PzoGroundShaderProbe.config(config,"SWAY_GUST","off");
                PzoGroundShaderProbe.config(config,"SWAY_ITERATIONS",2);
                PzoGroundShaderProbe.config(config,"FOLIAGE_SWAY_TAPS",4);
                PzoGroundShaderProbe.config(config,"SWAY_DEPTH_CHECK",true);
                PzoGroundShaderProbe.config(config,"SWAY_OCCLUDER_CHECK",true);
                Class<?> pixel=loader.loadClass("pzopt.PixelLight");
                for(String field:new String[]{"CHUNK_FRAG","CHUNK_BASE_FRAG"}) {
                    Field sourceField=pixel.getDeclaredField(field);sourceField.setAccessible(true);
                    String source=(String)sourceField.get(null);
                    String label=field+"-skip"+skip;
                    Files.writeString(output.resolve(label+"-original.frag"),source);
                    compile(label+" original",source);
                    link(label+" original",args[0],source);
                    String rewritten=NativeGroundShaders.rewrite("chunkShader.frag",source);
                    Files.writeString(output.resolve(label+"-addon.frag"),rewritten);
                    compile(label+" addon",rewritten);
                    link(label+" addon",args[0],rewritten);
                    pixels(label,args[0],source,rewritten);
                    for(boolean prefetch:new boolean[]{false,true}) {
                        PzoGroundShaderProbe.config(config,"SWAY_PREFETCH",prefetch);
                        String swayed=(String)PzoGroundShaderProbe.invoke(loader.loadClass("pzopt.Sway"),"patchComposite",
                                new Class<?>[]{String.class,boolean.class},source,false);
                        String swayLabel=label+"-sway-prefetch"+prefetch;
                        Files.writeString(output.resolve(swayLabel+"-original.frag"),swayed);
                        String withGround=NativeGroundShaders.rewrite("pzopt_swChunk.frag",swayed);
                        Files.writeString(output.resolve(swayLabel+"-addon.frag"),withGround);
                        compile(swayLabel,withGround);link(swayLabel,args[0],withGround);
                        pixels(swayLabel,args[0],swayed,withGround);
                    }
                }
            }
            System.out.println("PASS: actual PZO PixelLight chunk/base variants compile, link with native chunk vertex, and validate with empty-skip disabled/enabled.");
        } finally { GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate(); }
    }
    static void compile(String label,String source) {
        int shader=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        int status=GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS);
        String log=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);
        if(status==0)throw new AssertionError(label+": "+log);
        System.out.println(label+" compiled");
    }
    static int linked(String label,String game,String source) throws Exception {
        int program=GL20.glCreateProgram(),vertex=GL20.glCreateShader(GL20.GL_VERTEX_SHADER),fragment=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        try {
            GL20.glShaderSource(vertex,Files.readString(Path.of(game,"media/shaders/chunkShader.vert")));
            GL20.glCompileShader(vertex);
            GL20.glShaderSource(fragment,source);GL20.glCompileShader(fragment);
            GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);
            GL30.glBindFragDataLocation(program,0,"fragColor");
            GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(label+" link: "+GL20.glGetProgramInfoLog(program));
            GL20.glValidateProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_VALIDATE_STATUS)==0)throw new AssertionError(label+" validate: "+GL20.glGetProgramInfoLog(program));
            return program;
        } finally {GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);}
    }
    static void link(String label,String game,String source) throws Exception {
        GL20.glDeleteProgram(linked(label,game,source));
        System.out.println(label+" native link+validate passed");
    }
    static int tex(int format,int layout,int type,ByteBuffer bytes) {
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,8,8,0,layout,type,bytes);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);return texture;
    }
    static void pixels(String label,String game,String original,String changed) throws Exception {
        ByteBuffer colors=BufferUtils.createByteBuffer(256),floors=BufferUtils.createByteBuffer(256);
        ByteBuffer depths=BufferUtils.createByteBuffer(256);
        for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
            colors.put((byte)(80+x*8)).put((byte)(100+y*8)).put((byte)180).put((byte)255);
            boolean foreground=x>=3&&x<5;
            floors.put((byte)(foreground?0:80+x*8)).put((byte)(foreground?0:100+y*8)).put((byte)(foreground?0:180)).put((byte)255);
            depths.putFloat(.2f+.01f*x+.015f*y);
        }
        colors.flip();floors.flip();depths.flip();
        int diffuse=tex(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,colors);
        int floor=tex(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,floors);
        int depth=tex(GL30.GL_R32F,GL11.GL_RED,GL11.GL_FLOAT,depths);
        int output=tex(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,null);
        int contribution=tex(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,null);
        int worldDepth=tex(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,null);
        int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+7,GL11.GL_TEXTURE_2D,contribution,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,worldDepth,0);
        int[] buffers=new int[8];buffers[0]=GL30.GL_COLOR_ATTACHMENT0;buffers[7]=GL30.GL_COLOR_ATTACHMENT0+7;GL20.glDrawBuffers(buffers);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
        int vao=GL30.glGenVertexArrays(),vbo=GL15.glGenBuffers();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,new float[]{-1,-1,0,0,1,1,1,1,3,-1,2,0,1,1,1,1,-1,3,0,2,1,1,1,1},GL15.GL_STATIC_DRAW);
        for(int i=0;i<3;i++){GL20.glEnableVertexAttribArray(i);GL20.glVertexAttribPointer(i,i==2?4:2,GL11.GL_FLOAT,false,32,i==0?0:i==1?8:16);}
        ByteBuffer baseline=BufferUtils.createByteBuffer(256);FloatBuffer baselineDepth=BufferUtils.createFloatBuffer(64);
        for(int mode=0;mode<2;mode++) {
            int program=linked(label,game,mode==0?original:changed);GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ModelViewProjection"),false,new Matrix4f().get(new float[16]));
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"useTexture"),1);
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"chunkDepth"),.15f);
            String[] samplers={"DIFFUSE","DEPTH","vpIsoGroundTexture"};int[] textures={diffuse,depth,floor};
            for(int i=0;i<3;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);GL20.glUniform1i(GL20.glGetUniformLocation(program,samplers[i]),i);}
            GL11.glViewport(0,0,8,8);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glDepthFunc(GL11.GL_LESS);
            GL11.glDisable(GL11.GL_BLEND);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            ByteBuffer color=BufferUtils.createByteBuffer(256);FloatBuffer actualDepth=BufferUtils.createFloatBuffer(64);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,8,8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,color);
            GL11.glReadPixels(0,0,8,8,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,actualDepth);
            for(int i=0;i<256;i++)if(color.get(i)!=colors.get(i))throw new AssertionError(label+" native color changed mode="+mode+" byte="+i);
            for(int i=0;i<64;i++)if(Math.abs(actualDepth.get(i)-(.15f+depths.getFloat(i*4)))>1e-6f)throw new AssertionError(label+" flat/incorrect native depth at "+i);
            if(mode==0){baseline.put(color).flip();baselineDepth.put(actualDepth).flip();}
            else {
                for(int i=0;i<64;i++)if(actualDepth.get(i)!=baselineDepth.get(i))throw new AssertionError(label+" addon depth changed");
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+7);GL11.glReadPixels(0,0,8,8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,color);
                for(int i=0;i<256;i++)if(color.get(i)!=floors.get(i))throw new AssertionError(label+" ground contribution changed at "+i);
            }
            GL20.glDeleteProgram(program);
        }
        if(GL11.glGetError()!=0)throw new AssertionError(label+" GL error");
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(fbo);GL30.glDeleteVertexArrays(vao);GL15.glDeleteBuffers(vbo);
        for(int t:new int[]{diffuse,floor,depth,output,contribution,worldDepth})GL11.glDeleteTextures(t);
        System.out.println(label+" native color/depth readback PASS: varying per-pixel depth preserved, opaque foreground ground contribution zero.");
    }
}
