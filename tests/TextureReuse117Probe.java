import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Offline GL parity fixture for same-size native-color replacement; no game attach. */
public final class TextureReuse117Probe {
    static final String[] FIELDS={"texture","occlusion","floorMoments","relight","validity"};
    static final int[] SLOTS={7,6,3,5,4};
    static final int[] FORMATS={GL11.GL_RGBA8,GL30.GL_RGBA32F,GL30.GL_RGBA32F,GL30.GL_RGBA16F,GL30.GL_R16F};
    static final String VERT="""
        #version 330 compatibility
        void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);}
        """;
    static final String FRAG="""
        #version 330 compatibility
        layout(location=0)out vec4 color;
        uniform vec4 tint;uniform float depth;
        void main(){color=tint;gl_FragDepth=depth;}
        """;
    static DataOutputStream evidence;
    static boolean reuse;
    static int checks,cases,stable,changes,program,readback;
    static final List<String> notes=new ArrayList<>();
    static class Target {
        int fbo,color,depth,other,w,h;
        Target(int width,int height){w=width;h=height;fbo=GL30.glGenFramebuffers();replace(width,height);}
        void replace(int width,int height){
            w=width;h=height;GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            int old=color;color=texture(GL11.GL_RGBA8,w,h);attach(0,color);
            if(old!=0)GL11.glDeleteTextures(old);
            if(depth!=0)GL30.glDeleteRenderbuffers(depth);
            depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,w,h);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
            if(other!=0)GL11.glDeleteTextures(other);other=texture(GL11.GL_RGBA8,w,h);attach(1,other);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1});
            GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{.13f,.27f,.39f,.71f});
            GL30.glClearBufferfv(GL11.GL_COLOR,1,new float[]{.23f,.43f,.61f,.83f});
            GL30.glClearBufferfi(GL30.GL_DEPTH_STENCIL,0,.625f,109);
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"native FBO incomplete");
        }
        void bind(){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,w,h);}
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);reuse=Boolean.parseBoolean(args[1]);
        boolean pzo=Boolean.parseBoolean(args[2]);
        require(NativeGroundShaders.PZO_FINAL==pzo,"PZO_FINAL fixture not isolated");
        require(GLFW.glfwInit(),"GLFW init");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(32,32,"117 native storage parity",0,0);
        try(DataOutputStream stream=new DataOutputStream(Files.newOutputStream(out.resolve("pixels.bin")))) {
            evidence=stream;require(window!=0,"hidden GL window");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays());readback=GL30.glGenFramebuffers();
            program=link(NativeGroundShaders.rewrite("tile.frag",FRAG));
            Target target=new Target(8,8);int[] prior=null;int previousW=0,previousSource=0,previousWall=0;
            boolean[] masks={true,true,false,true,true,true,true};int[] sizes={8,8,8,8,12,8,8};
            for(int pass=0;pass<sizes.length;pass++) {
                if(pass>0)target.replace(sizes[pass],sizes[pass]);
                target.bind();GL20.glUseProgram(0);
                byte[] original=nativePixels(target);int nativeColor=target.color,nativeOther=target.other,nativeDepth=target.depth;
                NativeGround.begin(masks[pass]);Object surface=surface(target.fbo);int[] ids=ids(surface);
                boolean sameSize=pass>0&&previousW==sizes[pass];
                if(pass>0)for(int i=0;i<ids.length;i++)if(prior[i]!=0&&ids[i]!=0) {
                    if(sameSize&&reuse){require(ids[i]==prior[i],"same-size storage changed: "+FIELDS[i]+" pass="+pass);stable++;}
                    if(!sameSize){require(!GL43.glGetObjectLabel(GL11.GL_TEXTURE,ids[i]).startsWith("117-prior-"),"resize retained old storage: "+FIELDS[i]);changes++;}
                }
                require(Arrays.equals(original,nativePixels(target)),"begin changed native color/depth/stencil");
                require(attachment(0)==nativeColor&&attachment(1)==nativeOther,"native color attachments replaced");
                require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==nativeDepth,"native depth attachment replaced");
                for(int i=0;i<ids.length;i++)if(ids[i]!=0) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,ids[i]);
                    require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==sizes[pass],"wrong texture width "+FIELDS[i]);
                    require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)==FORMATS[i],"wrong internal format "+FIELDS[i]);
                    if(i<3)require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_NEAREST&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_NEAREST,"filter reset missing "+FIELDS[i]);
                }
                require(((Set<?>)field(surface,"walls")).isEmpty()&&((Set<?>)field(surface,"sources")).isEmpty(),"clear retained provenance refs");
                if(previousSource!=0)require(((int[])staticField("SOURCE_REFS"))[previousSource]==0,"replacement retained old source ref");
                if(previousWall!=0)require(((int[])staticField("WALL_REFS"))[previousWall]==0,"replacement retained old wall ref");
                // Only currently active attachments must be zero; inactive optional images remain inaccessible.
                for(int i=0;i<ids.length;i++)if(ids[i]!=0&&(i==0||i==1&&masks[pass]||i==2&&masks[pass]||i>=3&&pzo)) {
                    float[] pixels=readTexture(ids[i],target.w,target.h);
                    for(int at=0;at<pixels.length;at++)if(i!=4||at%4!=3)require(pixels[at]==0,"stale pixels survived clear "+FIELDS[i]+" pass="+pass+" value="+pixels[at]);
                }
                record("pass"+pass+"-clear",target,ids,masks[pass],pzo);
                // Exercise the production shader hook with a floor and source, then real source/wall refs.
                GL20.glUseProgram(program);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_ALWAYS);GL11.glDepthMask(true);
                GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilFunc(GL11.GL_ALWAYS,37+pass,255);GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_REPLACE);
                NativeGround.setFloor(true);draw(.2f,.35f,.5f,1,.4f);
                NativeGround.setFloor(false);NativeGround.sourceBegin(new NativeSource(12,13,0,3,new float[]{-.5f,0,-.5f,.5f,1,.5f},2));
                int source=((Number)staticField("sourceOwner")).intValue();require(source>=2,"source not retained");
                draw(.7f,.4f,.15f,1,.3f);NativeGround.sourceEnd();
                Method retain=NativeGround.class.getDeclaredMethod("retainWall",int.class,int.class,int.class,int.class);retain.setAccessible(true);
                int wall=((Number)retain.invoke(null,19+pass,20,0,1)).intValue();require(wall>=2,"wall not retained");
                require(((int[])staticField("SOURCE_REFS"))[source]>0&&((int[])staticField("WALL_REFS"))[wall]>0,"missing refs");
                record("pass"+pass+"-draw",target,ids,masks[pass],pzo);
                GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL20.glUseProgram(0);NativeGround.finish();
                require(attachment(0)==nativeColor&&attachment(1)==nativeOther,"finish changed native attachments");
                for(int i:new int[]{3,4,5})require(attachment(i)==0,"finish retained plugin optional attachment "+i);
                // Contaminate retained storage and its cached filters; next color generation must reset both.
                poison(ids);set(surface,"minFilter",GL11.GL_LINEAR_MIPMAP_LINEAR);set(surface,"magFilter",GL11.GL_LINEAR);
                for(int i=0;i<ids.length;i++)if(ids[i]!=0)GL43.glObjectLabel(GL11.GL_TEXTURE,ids[i],"117-prior-"+pass+"-"+i);
                prior=ids;previousW=sizes[pass];previousSource=source;previousWall=wall;cases++;
                notes.add("pass="+pass+" size="+sizes[pass]+" mask="+masks[pass]+" ids="+Arrays.toString(ids));
                require(GL11.glGetError()==0,"GL error pass="+pass);
            }
            cachedChunkReset(target);inactiveChunkReset(target);temporarilyForeignReset(target);relightOnlyReset();
            foreignAttachments();NativeGround.release(target.fbo);
            require(GL11.glGetError()==0,"final GL error");
            Files.write(out.resolve("storage-ids.txt"),notes);
            System.out.println("TextureReuse117 GPU PASS pzo="+pzo+" reuse="+reuse+" cases="+cases+" checks="+checks+" stableIds="+stable+" resizedIds="+changes+"; raw exact parity data pixels.bin");
        }finally{NativeGround.finish();GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void cachedChunkReset(Target output)throws Exception {
        // A cached chunk uses clear=false: color identity itself must force content/ref retirement.
        Target child=new Target(8,8);output.bind();NativeGround.begin(true);child.bind();
        Object chunk=allocate(Class.forName("zombie.iso.fboRenderChunk.FBORenderChunk"));
        Object tex=allocate(Class.forName("zombie.core.textures.Texture")),id=allocate(Class.forName("zombie.core.textures.TextureID"));
        GameAccess.set("zombie.core.textures.TextureID","id",id,child.color);GameAccess.set("zombie.core.textures.Texture","dataid",tex,id);GameAccess.set(chunk.getClass().getName(),"depth",chunk,tex);
        NativeGround.chunkBegin(chunk,true);Object before=surface(child.fbo);int[] old=ids(before);NativeGround.setFloor(false);
        NativeGround.sourceBegin(new NativeSource(22,23,0,3,new float[]{-.5f,0,-.5f,.5f,1,.5f},3));int source=((Number)staticField("sourceOwner")).intValue();NativeGround.sourceEnd();
        poison(old);child.replace(8,8);GameAccess.set("zombie.core.textures.TextureID","id",id,child.color);child.bind();GL20.glUseProgram(0);NativeGround.chunkBegin(chunk,false);
        int[] next=ids(surface(child.fbo));if(reuse)for(int i=0;i<3;i++){require(old[i]==next[i],"cached clear=false storage changed "+FIELDS[i]);stable++;}
        require(((int[])staticField("SOURCE_REFS"))[source]==0,"cached replacement retained source refs");
        for(int i=0;i<3;i++)for(float v:readTexture(next[i],8,8))require(v==0,"cached clear=false retained dirty pixels");
        record("cached-clear-false",child,next,true,false);NativeGround.finish();
        require(((String[])staticField("SOURCE_NAMES"))[source]==null,"source retirement not collected at finish");
        NativeGround.release(child.fbo);cases++;
    }
    static void foreignAttachments()throws Exception {
        Target t=new Target(8,8);int[] owned=new int[3];t.bind();
        for(int i=0;i<3;i++){owned[i]=texture(GL11.GL_RGBA8,8,8);attach(i+3,owned[i]);}
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,GL30.GL_COLOR_ATTACHMENT3,GL30.GL_COLOR_ATTACHMENT4,GL30.GL_COLOR_ATTACHMENT5});
        NativeGround.begin(true);require(NativeGround.floorTexture()==0&&NativeGround.relightTexture()==0,"foreign attachment adopted");
        for(int i=0;i<3;i++)require(attachment(i+3)==owned[i],"foreign attachment replaced");
        NativeGround.finish();for(int i=0;i<3;i++)require(attachment(i+3)==owned[i],"foreign attachment detached at finish");
        NativeGround.release(t.fbo);cases++;
    }
    static void inactiveChunkReset(Target output)throws Exception {
        Target child=new Target(8,8);Object chunk=allocate(Class.forName("zombie.iso.fboRenderChunk.FBORenderChunk"));
        Object tex=allocate(Class.forName("zombie.core.textures.Texture")),id=allocate(Class.forName("zombie.core.textures.TextureID"));
        GameAccess.set("zombie.core.textures.TextureID","id",id,child.color);GameAccess.set("zombie.core.textures.Texture","dataid",tex,id);GameAccess.set(chunk.getClass().getName(),"depth",chunk,tex);
        output.bind();GL20.glUseProgram(0);NativeGround.begin(true);child.bind();NativeGround.chunkBegin(chunk,true);
        int[] prior=ids(surface(child.fbo));poison(prior);NativeGround.finish();
        child.replace(8,8);GameAccess.set("zombie.core.textures.TextureID","id",id,child.color);
        output.bind();NativeGround.begin(false);child.bind();NativeGround.chunkBegin(chunk,false);
        int[] hidden=ids(surface(child.fbo));if(reuse)for(int i=0;i<3;i++)require(hidden[i]==prior[i],"inactive replacement changed storage "+FIELDS[i]);
        NativeGround.finish();output.bind();NativeGround.begin(true);child.bind();NativeGround.chunkBegin(chunk,false);
        int[] visible=ids(surface(child.fbo));
        for(int i=0;i<3;i++)for(float v:readTexture(visible[i],8,8))require(v==0,"inactive color replacement resurrected stale "+FIELDS[i]+" value="+v);
        record("inactive-false-true-clear-false",child,visible,true,false);
        // Cached chunk consumers may choose mip filtering; chunkEnd must replace poisoned old mips.
        NativeGround.chunkEnd();for(int i=0;i<3;i++){
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,visible[i]);FloatBuffer mip=BufferUtils.createFloatBuffer(4*4*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,1,GL11.GL_RGBA,GL11.GL_FLOAT,mip);
            for(int at=0;at<mip.capacity();at++)require(mip.get(at)==0,"chunkEnd kept stale mip "+FIELDS[i]);
        }
        NativeGround.finish();NativeGround.release(child.fbo);cases++;
    }
    static void temporarilyForeignReset(Target ignored)throws Exception {
        if(!NativeGroundShaders.PZO_FINAL)return;
        Target t=new Target(8,8);t.bind();GL20.glUseProgram(0);NativeGround.begin(true);
        int[] prior=ids(surface(t.fbo));poison(prior);t.replace(8,8);
        int[] foreign=new int[3];for(int i=0;i<3;i++){foreign[i]=texture(GL11.GL_RGBA8,8,8);attach(i+3,foreign[i]);}
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,GL30.GL_COLOR_ATTACHMENT3,GL30.GL_COLOR_ATTACHMENT4,GL30.GL_COLOR_ATTACHMENT5});
        // Invoke the actual attach path as a cached destination; the depth object is merely its lookup key.
        Object chunk=allocate(Class.forName("zombie.iso.fboRenderChunk.FBORenderChunk"));
        Object tex=allocate(Class.forName("zombie.core.textures.Texture")),id=allocate(Class.forName("zombie.core.textures.TextureID"));
        GameAccess.set("zombie.core.textures.TextureID","id",id,t.color);GameAccess.set("zombie.core.textures.Texture","dataid",tex,id);GameAccess.set(chunk.getClass().getName(),"depth",chunk,tex);
        NativeGround.chunkBegin(chunk,false);for(int i=0;i<3;i++)require(attachment(i+3)==foreign[i],"color-change detached foreign slot "+(i+3));
        require(NativeGround.floorTexture()==0&&NativeGround.relightTexture()==0,"foreign optional capture became usable");
        for(int i=3;i<=5;i++)attach(i,0);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);NativeGround.chunkBegin(chunk,false);
        int[] next=ids(surface(t.fbo));if(reuse)for(int i=0;i<5;i++)require(next[i]==prior[i],"temporary foreign changed storage "+FIELDS[i]);
        for(int i=2;i<5;i++){float[] values=readTexture(next[i],8,8);for(int at=0;at<values.length;at++)if(i!=4||at%4!=3)require(values[at]==0,"temporary foreign resurrected stale "+FIELDS[i]);}
        record("temporary-foreign-reactivation",t,next,true,true);NativeGround.finish();NativeGround.release(t.fbo);cases++;
    }
    static void relightOnlyReset()throws Exception {
        if(!NativeGroundShaders.PZO_FINAL)return;
        Target t=new Target(8,8);t.bind();GL20.glUseProgram(0);NativeGround.begin(true);int[] prior=ids(surface(t.fbo));poison(prior);t.replace(8,8);
        int a=texture(GL11.GL_RGBA8,8,8),b=texture(GL11.GL_RGBA8,8,8);attach(4,a);attach(5,b);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,0,GL30.GL_COLOR_ATTACHMENT4,GL30.GL_COLOR_ATTACHMENT5});
        Object chunk=allocate(Class.forName("zombie.iso.fboRenderChunk.FBORenderChunk"));Object tex=allocate(Class.forName("zombie.core.textures.Texture")),id=allocate(Class.forName("zombie.core.textures.TextureID"));
        GameAccess.set("zombie.core.textures.TextureID","id",id,t.color);GameAccess.set("zombie.core.textures.Texture","dataid",tex,id);GameAccess.set(chunk.getClass().getName(),"depth",chunk,tex);
        NativeGround.chunkBegin(chunk,false);require(attachment(4)==a&&attachment(5)==b,"relight-only foreign attachments replaced");
        GL20.glUseProgram(program);NativeGround.setFloor(true);draw(.2f,.4f,.6f,1,.4f);NativeGround.setFloor(false);
        NativeGround.sourceBegin(new NativeSource(32,33,0,3,new float[]{-.5f,0,-.5f,.5f,1,.5f},4));int source=((Number)staticField("sourceOwner")).intValue();draw(.6f,.4f,.2f,1,.3f);NativeGround.sourceEnd();
        int[] blocked=ids(surface(t.fbo));float[][] saved=new float[3][];for(int i=0;i<3;i++)saved[i]=readTexture(blocked[i],8,8);
        attach(4,0);attach(5,0);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);GL20.glUseProgram(0);NativeGround.chunkBegin(chunk,false);
        int[] next=ids(surface(t.fbo));require(((int[])staticField("SOURCE_REFS"))[source]>0&&((Set<?>)field(surface(t.fbo),"sources")).contains(source),"relight-only activation reset provenance");
        for(int i=0;i<3;i++)require(Arrays.equals(saved[i],readTexture(next[i],8,8)),"relight-only activation cleared valid "+FIELDS[i]);
        for(int i=3;i<5;i++){float[] values=readTexture(next[i],8,8);for(int at=0;at<values.length;at++)if(i!=4||at%4!=3)require(values[at]==0,"relight-only activation retained stale "+FIELDS[i]);}
        record("relight-only-reactivation",t,next,true,true);NativeGround.finish();NativeGround.release(t.fbo);cases++;
    }
    static void draw(float r,float g,float b,float a,float z){GL20.glUniform4f(GL20.glGetUniformLocation(program,"tint"),r,g,b,a);GL20.glUniform1f(GL20.glGetUniformLocation(program,"depth"),z);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
    static void record(String name,Target t,int[] ids,boolean mask,boolean pzo)throws Exception {
        evidence.writeUTF(name);byte[] nativeData=nativePixels(t);evidence.writeInt(nativeData.length);evidence.write(nativeData);
        for(int i=0;i<ids.length;i++){boolean active=ids[i]!=0&&(i==0||i==1&&mask||i==2&&mask||i>=3&&pzo);evidence.writeBoolean(active);if(active){float[] values=readTexture(ids[i],t.w,t.h);evidence.writeInt(values.length);for(float v:values)evidence.writeInt(Float.floatToRawIntBits(v));}}
    }
    static byte[] nativePixels(Target t)throws Exception {
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),source=GL11.glGetInteger(GL11.GL_READ_BUFFER);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,t.fbo);
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        for(int slot=0;slot<2;slot++){GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+slot);ByteBuffer b=BufferUtils.createByteBuffer(t.w*t.h*4);GL11.glReadPixels(0,0,t.w,t.h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[] a=new byte[b.capacity()];b.get(a);result.write(a);}
        FloatBuffer d=BufferUtils.createFloatBuffer(t.w*t.h);GL11.glReadPixels(0,0,t.w,t.h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,d);DataOutputStream data=new DataOutputStream(result);for(int i=0;i<d.capacity();i++)data.writeInt(Float.floatToRawIntBits(d.get(i)));
        ByteBuffer s=BufferUtils.createByteBuffer(t.w*t.h);GL11.glReadPixels(0,0,t.w,t.h,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,s);byte[] a=new byte[s.capacity()];s.get(a);result.write(a);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glReadBuffer(source);return result.toByteArray();
    }
    static float[] readTexture(int texture,int w,int h){
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);FloatBuffer b=BufferUtils.createFloatBuffer(w*h*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,b);float[] a=new float[b.capacity()];b.get(a);GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);return a;
    }
    static void poison(int[] ids){for(int i=0;i<ids.length;i++)if(ids[i]!=0){GL44.glClearTexImage(ids[i],0,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{.9f,.7f,.5f,.3f});GL11.glBindTexture(GL11.GL_TEXTURE_2D,ids[i]);if(i<3){GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);}}}
    static int texture(int format,int w,int h){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    static void attach(int slot,int texture){GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL11.GL_TEXTURE_2D,texture,0);}
    static int attachment(int slot){return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);}
    static Object surface(int fbo)throws Exception{return ((Map<?,?>)staticField("SURFACES")).get(fbo);}
    static int[] ids(Object o)throws Exception{int[] a=new int[FIELDS.length];for(int i=0;i<a.length;i++)a[i]=((Number)field(o,FIELDS[i])).intValue();return a;}
    static Object staticField(String name)throws Exception{Field f=NativeGround.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static Object allocate(Class<?> c)throws Exception{Field f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object u=f.get(null);return u.getClass().getMethod("allocateInstance",Class.class).invoke(u,c);}
    static int link(String fragment){int p=GL20.glCreateProgram();for(int type:new int[]{GL20.GL_VERTEX_SHADER,GL20.GL_FRAGMENT_SHADER}){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,type==GL20.GL_VERTEX_SHADER?VERT:fragment);GL20.glCompileShader(s);require(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(s));GL20.glAttachShader(p,s);}GL20.glLinkProgram(p);require(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
