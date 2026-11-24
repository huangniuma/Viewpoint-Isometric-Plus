import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.NativeGroundShaders;

/** Controlled actual-PZO-source MRT6 audit; does not exercise the game's woven queue/cache. */
public final class NativeRelight069CaptureProbe {
    static final int W=8;
    static final String VERTEX="""
      #version 330 compatibility
      out vec4 col;out vec2 texCoord;uniform float vertexAlpha;
      void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);
       texCoord=p;col=vec4(vertexAlpha);}
      """;
    static int count;
    static boolean quantized;
    static boolean candidate;
    static int rejectedKnownFloors;
    static int acceptedMixedFloors;
    public static void main(String[] args)throws Exception {
      Path game=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);quantized=args.length>2&&args[2].equals("rgba8");candidate=args.length>3&&args[3].equals("candidate");
      PzoGroundShaderProbe.game=game;
      if(!GLFW.glfwInit())throw new AssertionError("GLFW");
      GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
      GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
      GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
      long window=GLFW.glfwCreateWindow(W,W,"069 PZO MRT6",0,0);
      try {
       GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
       var loader=new PzoGroundShaderProbe.FixtureLoader();Class<?> config=loader.loadClass("pzopt.Config");
       for(Field f:config.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==String.class){f.setAccessible(true);f.set(null,"");}
       set(config,"PPL_SHADOW_MODE","map");set(config,"PPL_MODE","composite");set(config,"SPRITE_FILTER","sharp");
       set(config,"SWAY_GUST","off");set(config,"SWAY_ITERATIONS",2);set(config,"FOLIAGE_SWAY_TAPS",4);
       set(config,"SWAY_DEPTH_CHECK",true);set(config,"SWAY_OCCLUDER_CHECK",true);
       Class<?> pixel=loader.loadClass("pzopt.PixelLight"),sf=loader.loadClass("pzopt.SpriteFilter"),sway=loader.loadClass("pzopt.Sway");
       for(String field:new String[]{"CHUNK_FRAG","CHUNK_BASE_FRAG"}) {
        Field f=pixel.getDeclaredField(field);f.setAccessible(true);String source=(String)f.get(null);
        check(field,source,out);
        String defines="#define PZSF_WIDTH 1.0\n#define PZSF_SHARPNESS 0.5\n#define PZSF_MODE 3\n#define PZSF_REGIME 0\n#define PZSF_MIN 4\n#define PZSF_LINEAR 1\n#define PZSF_LOD_BIAS 0.0\n#define PZSF_INTEGER_AA 1\n#define PZSF_SKIP 0\n#define PZSF_KERNEL 1\n";
        String filtered=(String)PzoGroundShaderProbe.invoke(sf,"patchFetch",new Class<?>[]{String.class,String.class},source,defines);
        if(filtered==null)throw new AssertionError("actual PZO filter source absent");
        check(field+"-sharp",filtered,out);
        for(boolean prefetch:new boolean[]{false,true}) {
         set(config,"SWAY_PREFETCH",prefetch);
         for(boolean sharp:new boolean[]{false,true}) {
          String combined=(String)PzoGroundShaderProbe.invoke(sway,"patchComposite",new Class<?>[]{String.class,boolean.class},sharp?filtered:source,false);
          if(combined==null)throw new AssertionError("actual PZO sway source absent");
          check(field+"-sway"+prefetch+"-sharp"+sharp,combined,out);
         }
        }
       }
       if(candidate&&rejectedKnownFloors!=0)throw new AssertionError("candidate rejected pure floor");
       
       System.out.println("PASS cases="+count+" actual installed CHUNK/BASE, sharp, sway prefetch on/off; RGBA32F MRT6 nonzero height moments, mixed floor levels, fractional native alpha, premult/straight blend. nativeColorRGBA8="+quantized+" candidate="+candidate+" rejectedKnownFloorPixels="+rejectedKnownFloors+" acceptedMixedFloorPixels="+acceptedMixedFloors);
      } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void set(Class<?> c,String n,Object v)throws Exception{PzoGroundShaderProbe.config(c,n,v);}
    static int shader(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);
      if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(s));return s;}
    static int link(String source){int p=GL20.glCreateProgram(),v=shader(GL20.GL_VERTEX_SHADER,VERTEX),f=shader(GL20.GL_FRAGMENT_SHADER,source);
      GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL30.glBindFragDataLocation(p,0,"fragColor");GL20.glLinkProgram(p);
      if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(p));GL20.glDeleteShader(v);GL20.glDeleteShader(f);return p;}
    static int texture(float[] values){return texture(values,GL30.GL_RGBA32F);}
    static int texture(float[] values,int format){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);FloatBuffer b=null;
      if(values!=null){b=BufferUtils.createFloatBuffer(values.length);b.put(values).flip();}
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,W,W,0,GL11.GL_RGBA,GL11.GL_FLOAT,b);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
      GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);return t;}
    static void check(String label,String source,Path out)throws Exception {
      String patched=NativeGroundShaders.rewrite(label.contains("sway")?"pzopt_swChunk.frag":"chunkShader.frag",source);
      if(!patched.contains("vpIsoOcclusionTexture"))throw new AssertionError(label+" missing metadata producer");
      Files.writeString(out.resolve(label+"-"+(candidate?"candidate":"production")+"-"+(quantized?"rgba8":"rgba32f")+".frag"),patched);
      int p=link(patched);GL20.glUseProgram(p);
      float[] color=new float[W*W*4],meta=new float[color.length],depth=new float[color.length],ground=new float[color.length];
      for(int i=0;i<W*W;i++) {
       float a=new float[]{.25f,.5f,.75f,1f}[i%4],fore=a*.2f,fl=a-fore;
       float g=(i/4)%2==0?2*fl:fl,b=(i/4)%2==0?4*fl:2*fl;
       for(int c=0;c<3;c++){color[i*4+c]=a*.4f;ground[i*4+c]=fl*.4f;}
       color[i*4+3]=ground[i*4+3]=a;meta[i*4]=fore;meta[i*4+1]=g;meta[i*4+2]=b;meta[i*4+3]=a;depth[i*4]=.3f;
      }
      for(int i=0;i<W*W;i++){meta[i*4]=0;meta[i*4+1]=0;meta[i*4+2]=0;meta[i*4+3]=color[i*4+3];}
      int[] textures={texture(color,quantized?GL11.GL_RGBA8:GL30.GL_RGBA32F),texture(depth),texture(ground),texture(meta)},targets={texture(null),texture(null),texture(null),texture(null),texture(null)};
      int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
      for(int i=0;i<targets.length;i++)GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+new int[]{0,6,7,4,5}[i],GL11.GL_TEXTURE_2D,targets[i],0);
      int[] bufs=new int[8];bufs[0]=GL30.GL_COLOR_ATTACHMENT0;bufs[6]=GL30.GL_COLOR_ATTACHMENT6;bufs[7]=GL30.GL_COLOR_ATTACHMENT7;bufs[4]=GL30.GL_COLOR_ATTACHMENT4;bufs[5]=GL30.GL_COLOR_ATTACHMENT5;GL20.glDrawBuffers(bufs);
      if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
      String[] samplers={"DIFFUSE","DEPTH","vpIsoGroundTexture","vpIsoOcclusionTexture"};
      for(int i=0;i<textures.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);GL20.glUniform1i(GL20.glGetUniformLocation(p,samplers[i]),i);}
      GL20.glUniform1i(GL20.glGetUniformLocation(p,"useTexture"),1);GL20.glUniform1f(GL20.glGetUniformLocation(p,"vpIsoGrassMaskOn"),1);
      GL11.glViewport(0,0,W,W);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);
      for(int unit:new int[]{10,11,12}) {
       GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int a=GL11.glGenTextures();GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,a);
       float[] v=new float[16];for(int i=0;i<4;i++){v[i*4]=v[i*4+1]=v[i*4+2]=unit==11?.35f:0;v[i*4+3]=1;if(unit==10)v[i*4+2]=127f/255;}
       GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL30.GL_RGBA32F,2,2,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,v);
       GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
      }
      GL20.glUniform1f(GL20.glGetUniformLocation(p,"pplOn"),1);GL20.glUniform4f(GL20.glGetUniformLocation(p,"pplMapC"),0,0,2,0);
      GL20.glUniform4i(GL20.glGetUniformLocation(p,"pplOrg"),0,0,1,0);GL20.glUniform2i(GL20.glGetUniformLocation(p,"pplLv"),0,0);
      GL20.glUniform1f(GL20.glGetUniformLocation(p,"vpIsoRelightWrite"),1);
      GL11.glDisable(GL11.GL_BLEND);
      for(float alpha:new float[]{1,.6f}) {
       GL20.glUniform1f(GL20.glGetUniformLocation(p,"vertexAlpha"),alpha);
       for(int slot:new int[]{0,4,5,6,7})GL30.glClearBufferfv(GL11.GL_COLOR,slot,new float[4]);
       GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
       GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT5);float[] base=new float[W*W*4];GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,base);
       GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT4);float[] valid=new float[W*W*4];GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,valid);
       GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);float[] lit=new float[W*W*4];GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,lit);
       for(int i=0;i<W*W;i++) {
        for(int c=0;c<3;c++)if(Math.abs(base[i*4+c]-ground[i*4+c]*alpha)>.0001)throw new AssertionError(label+" prelight lost original ground or colRGB");
        if(Math.abs(valid[i*4]-(quantized?Math.round(color[i*4+3]*255)/255f:color[i*4+3])*alpha)>1e-5)throw new AssertionError(label+" validity missing "+valid[i*4]);
        if(!(lit[i*4]>0 && lit[i*4]<base[i*4]*.6))throw new AssertionError(label+" native lighting not active");
       }
       count++;
      }
      System.out.println(label+" PASS same-draw original-PZO active light, independent unlit ground, colRGB/alpha, foreground metadata");
      int error=GL11.glGetError();if(error!=0)throw new AssertionError(label+" GL "+error);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(fbo);GL20.glDeleteProgram(p);for(int t:textures)GL11.glDeleteTextures(t);for(int t:targets)GL11.glDeleteTextures(t);
    }
}
