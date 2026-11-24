import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.*;
import org.lwjgl.BufferUtils;
import net.bytebuddy.jar.asm.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.NativeGroundShaders;

/** Calls installed PZO shader generators with an isolated, inert Config class. */
public final class PzoGroundShaderProbe {
    static Path game,out;
    static int emptyAux;
    static final class FixtureLoader extends ClassLoader {
        FixtureLoader(){super(PzoGroundShaderProbe.class.getClassLoader());}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
            if(!(name.startsWith("pzopt.")||name.startsWith("zombie.")||name.startsWith("fmod.")))return super.loadClass(name,resolve);
            Class<?> type=findLoadedClass(name);
            if(type==null)try{
                String resource=name.replace('.','/')+".class";
                Path file=game.resolve(resource);
                byte[] bytes;
                if(Files.isRegularFile(file))bytes=Files.readAllBytes(file);
                else try(InputStream in=getParent().getResourceAsStream(resource)){if(in==null)throw new ClassNotFoundException(name);bytes=in.readAllBytes();}
                if(name.equals("pzopt.Config")){
                    ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(0);
                    reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                        @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){ return super.visitField(a & ~Opcodes.ACC_FINAL,n,d,s,v); }
                        @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                            return n.equals("<clinit>")?null:super.visitMethod(a,n,d,s,e);
                        }
                    },0);
                    bytes=writer.toByteArray();
                }
                type=defineClass(name,bytes,0,bytes.length);
            }catch(IOException e){throw new ClassNotFoundException(name,e);}
            if(resolve)resolveClass(type);return type;
        }
    }
    static void config(Class<?> type,String name,Object value)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    static Object invoke(Class<?> type,String name,Class<?>[] params,Object... args)throws Exception{
        Method m=type.getDeclaredMethod(name,params);m.setAccessible(true);
        try{return m.invoke(null,args);}catch(InvocationTargetException e){throw new AssertionError(name,e.getCause());}
    }
    static void compile(String label,String path,String source)throws Exception {
        if(source==null)throw new AssertionError(label+" generator returned null");
        Files.writeString(out.resolve(label+"-generated.frag"),source);
        shader(label+" baseline",source);
        String patched=NativeGroundShaders.rewrite(path,source);
        Files.writeString(out.resolve(label+"-addon.frag"),patched);
        if(!patched.contains("VPISO_GROUND_CONTRIBUTION"))throw new AssertionError(label+" wrapper absent");
        shader(label+" addon",patched);
        readback(label,source,patched);
        if(label.equals("sway-prefetch-true")){
            String negative=patched.replace("vpIsoGroundFetch(vpIsoGroundTexture", "pzSwFetch(vpIsoGroundTexture");
            boolean rejected=false;
            try{readback(label+" old-prefetch-negative",source,negative);}
            catch(AssertionError expected){
                if(!expected.getMessage().contains("ground ownership mismatch"))throw expected;
                rejected=true;System.out.println("PZO cached DIFFUSE negative control reproduced: "+expected.getMessage());
            }
            if(!rejected)throw new AssertionError("Prefetch ownership negative control was not rejected");
        }
        System.out.println(label+" PASS generated="+source.length()+" addon="+patched.length()+" pzSwT="+source.contains("pzSwT")+" pzSwFetch="+source.contains("pzSwFetch")+" pzsfFetch="+source.contains("pzsfFetch"));
    }
    static void shader(String label,String source){int id=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);try{GL20.glShaderSource(id,source);GL20.glCompileShader(id);if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(label+" compile failed: "+GL20.glGetShaderInfoLog(id));}finally{GL20.glDeleteShader(id);}}
    static final String VERTEX="""
        #version 330 compatibility
        out vec4 col;
        out vec2 texCoord;
        uniform float fixtureScale;
        uniform vec2 fixtureOffset;
        void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);
          texCoord=(p-.5)*fixtureScale+.5+fixtureOffset;col=vec4(.7,.8,.9,.75);}
        """;
    static int link(String source){
        int p=GL20.glCreateProgram(),v=GL20.glCreateShader(GL20.GL_VERTEX_SHADER),f=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(v,VERTEX);GL20.glCompileShader(v);GL20.glShaderSource(f,source);GL20.glCompileShader(f);
        GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);
        GL20.glDeleteShader(v);GL20.glDeleteShader(f);
        if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError("PZO link: "+GL20.glGetProgramInfoLog(p));
        return p;
    }
    static int texture(int size,ByteBuffer pixels){
        int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);return t;
    }
    static void uniform4(int p,String name,float x,float y,float z,float w){GL20.glUniform4f(GL20.glGetUniformLocation(p,name),x,y,z,w);}
    static ByteBuffer read(int slot,int size){ByteBuffer pixels=BufferUtils.createByteBuffer(size*size*4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+slot);GL11.glReadPixels(0,0,size,size,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);return pixels;}
    static void draw(int p,int diffuse,int ground,int depth,int aux,int gust,int size,int state,float scale,int lod,boolean addon){
        GL11.glViewport(0,0,size,size);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);
        int[] buffers=new int[8];buffers[0]=GL30.GL_COLOR_ATTACHMENT0;if(addon)buffers[7]=GL30.GL_COLOR_ATTACHMENT0+7;GL20.glDrawBuffers(buffers);
        GL20.glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p,"useTexture"),1);
        GL20.glUniform1f(GL20.glGetUniformLocation(p,"fixtureScale"),scale);
        GL20.glUniform2f(GL20.glGetUniformLocation(p,"fixtureOffset"),state*.017f,-state*.013f);
        uniform4(p,"pzSwOn",1,1f/16,1f/16,lod);uniform4(p,"pzSwWind",state==0?0:.5f,.2f,1,.1f);
        uniform4(p,"pzSwMap",1,0,1,0);uniform4(p,"pzSwPh",.4f,.8f,1.2f,1.6f);uniform4(p,"pzSwGust",0,0,0,0);
        String[] names={"DIFFUSE","vpIsoGroundTexture","DEPTH","pzSwAux","pzSwGustTex"};
        int[] textures={diffuse,ground,depth,state==0?emptyAux:aux,gust};
        for(int i=0;i<textures.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);GL20.glUniform1i(GL20.glGetUniformLocation(p,names[i]),i);}
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
    static void readback(String label,String original,String patched){
        int baseline=link(original),candidate=link(patched),size=48;
        ByteBuffer color=BufferUtils.createByteBuffer(16*16*4),ground=BufferUtils.createByteBuffer(16*16*4),aux=BufferUtils.createByteBuffer(16*16*4);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            boolean foreground=x>=5&&x<11&&y>=2&&y<14;float visible=foreground?0:x==4||x==11?.4f:1;
            for(int c=0;c<3;c++){int floor=new int[]{80,120,180}[c];color.put((byte)(foreground?new int[]{160,130,100}[c]:floor));ground.put((byte)Math.round(floor*visible));}
            color.put((byte)255);ground.put((byte)255);aux.put((byte)252).put((byte)1).put((byte)0).put((byte)255);
        }
        color.flip();ground.flip();aux.flip();
        int colorTexture=texture(16,color),groundTexture=texture(16,ground),auxTexture=texture(16,aux);
        emptyAux=texture(1,BufferUtils.createByteBuffer(4));
        int gust=texture(1,BufferUtils.createByteBuffer(4).put(new byte[]{100,0,0,(byte)255}).flip());
        int depth=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
        FloatBuffer depths=BufferUtils.createFloatBuffer(16*16);while(depths.hasRemaining())depths.put(32769f/65535f);depths.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R32F,16,16,0,GL11.GL_RED,GL11.GL_FLOAT,depths);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        int output=texture(size,null),contribution=texture(size,null),fbo=GL30.glGenFramebuffers(),vao=GL30.glGenVertexArrays();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glBindVertexArray(vao);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+7,GL11.GL_TEXTURE_2D,contribution,0);
        int cases=0,unchanged=0,occluded=0;long movingHash=0,stationaryHash=0;
        for(int state=0;state<2;state++)for(float scale:new float[]{.75f,1.5f,4.5f})for(int lod:new int[]{0,1,2}){
            draw(baseline,groundTexture,groundTexture,depth,auxTexture,gust,size,state,scale,lod,false);ByteBuffer expected=read(0,size);
            draw(baseline,colorTexture,groundTexture,depth,auxTexture,gust,size,state,scale,lod,false);ByteBuffer before=read(0,size);
            draw(candidate,colorTexture,groundTexture,depth,auxTexture,gust,size,state,scale,lod,true);ByteBuffer after=read(0,size),actual=read(7,size);
            for(int i=0;i<size*size;i++)for(int c=0;c<3;c++){
                int index=i*4+c,e=expected.get(index)&255,a=actual.get(index)&255;
                if(before.get(index)!=after.get(index))throw new AssertionError(label+" changes original color state="+state+" scale="+scale+" lod="+lod+" pixel="+i);
                unchanged++;
                if(Math.abs(e-a)>1)throw new AssertionError(label+" ground ownership mismatch state="+state+" scale="+scale+" lod="+lod+" pixel="+i+" expected="+e+" actual="+a);
                if(e==0)occluded++;
                if(state==0)stationaryHash+=e*(index+1L);else movingHash+=e*(index+1L);
            }
            cases++;
        }
        if(occluded<1000||movingHash==stationaryHash)throw new AssertionError(label+" degenerate motion/occlusion fixture");
        if(GL11.glGetError()!=0)throw new AssertionError(label+" OpenGL error");
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(fbo);GL30.glDeleteVertexArrays(vao);
        for(int t:new int[]{colorTexture,groundTexture,auxTexture,emptyAux,gust,depth,output,contribution})GL11.glDeleteTextures(t);
        GL20.glDeleteProgram(baseline);GL20.glDeleteProgram(candidate);
        System.out.println(label+" GPU READBACK PASS cases="+cases+" nativeColorBytesUnchanged="+unchanged+" opaqueForegroundExclusion="+occluded+" stationary+moving, 3 scales, 3 mip levels; actual installed PZO source vs independent original-source floor oracle.");
    }
    public static void main(String[] args)throws Exception{
        game=Path.of(args[0]);out=Path.of(args[1]);Files.createDirectories(out);
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(32,32,"PZO generated native shader",0,0);
        try{
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            FixtureLoader loader=new FixtureLoader();Class<?> c=loader.loadClass("pzopt.Config");
            for(Field f:c.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==String.class&&!Modifier.isFinal(f.getModifiers())){f.setAccessible(true);f.set(null,"");}
            config(c,"SWAY_GUST","off");config(c,"SWAY_ITERATIONS",2);config(c,"FOLIAGE_SWAY_TAPS",4);config(c,"SWAY_DEPTH_CHECK",true);config(c,"SWAY_OCCLUDER_CHECK",true);
            config(c,"SPRITE_FILTER","sharp");
            Class<?> sway=loader.loadClass("pzopt.Sway");String chunk=Files.readString(game.resolve("media/shaders/chunkShader.frag"));
            for(boolean prefetch:new boolean[]{false,true}){
                config(c,"SWAY_PREFETCH",prefetch);
                String generated=(String)invoke(sway,"patchComposite",new Class<?>[]{String.class,boolean.class},chunk,false);
                compile("sway-prefetch-"+prefetch,"pzopt_swChunk.frag",generated);
            }
            Class<?> sf=loader.loadClass("pzopt.SpriteFilter");
            invoke(sf,"patchShader",new Class<?>[]{String.class,String.class},"media/shaders/chunkShader.frag",chunk);
            Field defines=sf.getDeclaredField("compilingDefines");defines.setAccessible(true);defines.set(null,"#define PZSF_WIDTH 1.0\n#define PZSF_SHARPNESS 0.5\n#define PZSF_MODE 3\n#define PZSF_REGIME 0\n#define PZSF_MIN 4\n#define PZSF_LINEAR 1\n#define PZSF_LOD_BIAS 0.0\n#define PZSF_INTEGER_AA 1\n#define PZSF_SKIP 0\n#define PZSF_KERNEL 1\n");
            String filtered=(String)invoke(sf,"patchShader",new Class<?>[]{String.class,String.class},"media/shaders/pzopt_sfChunk.frag","#version 330\nvoid main(){}\n");
            compile("spritefilter","pzopt_sfChunk.frag",filtered);
            String both=(String)invoke(sway,"patchComposite",new Class<?>[]{String.class,boolean.class},filtered,false);
            compile("sway-spritefilter","pzopt_swChunk.frag",both);
            System.out.println("PZO actual generated shader verification PASS; Config initializer stripped in isolated child loader; no user config read/write.");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}

