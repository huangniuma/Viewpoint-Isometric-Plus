import java.nio.file.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Production cached chunks, native filtering order and matching linear owner coverage. */
public final class NativeFloorSampling081Probe {
    static final int S=128,W=64;
    static final String VERT="""
        #version 330
        out vec4 col;out vec2 texCoord;
        uniform float scale;uniform vec2 shift;
        void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);
          texCoord=(p-.5)*scale+.5+shift;col=vec4(1);}
        """;
    static final String TILE="""
        #version 330
        in vec2 texCoord;layout(location=0)out vec4 color;uniform sampler2D DIFFUSE;
        void main(){color=texture(DIFFUSE,texCoord);}
        """;
    static final String ORACLE="""
        #version 330
        in vec2 texCoord;layout(location=0)out vec4 ground;layout(location=1)out vec4 moments;layout(location=2)out vec4 floorMoments;
        uniform sampler2D floorOracle,metadataOracle,floorMomentsOracle;
        void main(){ground=texture(floorOracle,texCoord);moments=texture(metadataOracle,texCoord);floorMoments=texture(floorMomentsOracle,texCoord);}
        """;
    static int output,chunkFbo,chunkColor,depth,tile,composite,oracle,oracleFbo,unit;
    static int sentinelFloor,sentinelFloorSampler;static float level;
    static int sentinelGround,sentinelMask,sentinelGroundSampler,sentinelMaskSampler;
    static Object chunk,command;
    static long groundChecks,metadataChecks,floorChecks;static int cases;
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(W,W,"081 floor chunk sampling",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            unit=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)-1;
            output=fbo(texture(W,GL11.GL_RGBA8,null));chunkColor=texture(S,GL11.GL_RGBA8,null);chunkFbo=fbo(chunkColor);
            depth=texture(S,GL30.GL_R32F,new float[S*S*4]);
            tile=NativeGroundProbe.link(VERT,NativeGroundShaders.rewrite("tile.frag",TILE));
            composite=NativeGroundProbe.link(VERT,NativeGroundShaders.rewrite("chunkShader.frag",Files.readString(Path.of(args[0],"media/shaders/chunkShader.frag"))));
            oracle=NativeGroundProbe.link(VERT,ORACLE);oracleFbo=fbo(texture(W,GL30.GL_RGBA32F,null));
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT1,GL11.GL_TEXTURE_2D,texture(W,GL30.GL_RGBA32F,null),0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT2,GL11.GL_TEXTURE_2D,texture(W,GL30.GL_RGBA32F,null),0);
            chunk=GameAccess.type("zombie.iso.fboRenderChunk.FBORenderChunk").getConstructor().newInstance();
            Object depthObject=textureObject(depth);GameAccess.set(chunk.getClass().getName(),"depth",chunk,depthObject);
            command=GameAccess.type("zombie.core.textures.TextureDraw").getConstructor().newInstance();GameAccess.set(command.getClass().getName(),"tex1",command,depthObject);
            sentinelFloor=texture(1,GL30.GL_RGBA32F,null);sentinelFloorSampler=GL33.glGenSamplers();
            sentinelGround=texture(1,GL11.GL_RGBA8,null);sentinelMask=texture(1,GL11.GL_RGBA8,null);
            sentinelGroundSampler=GL33.glGenSamplers();sentinelMaskSampler=GL33.glGenSamplers();
            // Reuse the same cached surface while the mask changes false -> true.
            scenario(false);scenario(true);level=-2;scenario(true);level=31;scenario(true);level=0;
            // Native FBO/texture recycling must reset the sampling cache for new auxiliary images.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,chunkFbo);int old=chunkColor;chunkColor=texture(S,GL11.GL_RGBA8,null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,chunkColor,0);GL11.glDeleteTextures(old);
            scenario(true);
            unknownShader();ownedSlot();NativeGround.release(chunkFbo);NativeGround.release(output);
            require(GL11.glGetError()==0,"GL error");
            System.out.println("Native chunk floor sampling081 GPU PASS cases="+cases+" groundChannels="+groundChecks+" signedMetadataChannels="+metadataChecks+" floorChannels="+floorChecks
                +"; production begin/chunkBegin/chunkEnd/chunkShader/beforeDraw/finish; zoom .75,2,2.25,2.5; source texture bind after shader setup; native nearest/trilinear and sampler objects; false-to-true mask and texture recycling; state restored.");
        }finally{NativeGround.finish();GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void scenario(boolean mask)throws Exception {
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinelGround);GL33.glBindSampler(unit,sentinelGroundSampler);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinelMask);GL33.glBindSampler(unit-1,sentinelMaskSampler);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-2);GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinelFloor);GL33.glBindSampler(unit-2,sentinelFloorSampler);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL20.glUseProgram(0);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        NativeGround.begin(mask);GameAccess.set("vpiso.NativeGround","floorLevel",null,level);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,chunkFbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,S,S);
        GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        NativeGround.chunkBegin(chunk,true);
        float[] pixels=new float[S*S*4];for(int i=0;i<S*S;i++){pixels[i*4]=.4f;pixels[i*4+1]=.55f;pixels[i*4+2]=.7f;pixels[i*4+3]=1;}
        int floor=texture(S,GL11.GL_RGBA8,pixels);produce(floor,true);
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){int i=(y*S+x)*4;float a=((x*7+y*11)%16)/16f;pixels[i]=.15f*a;pixels[i+1]=.25f*a;pixels[i+2]=.2f*a;pixels[i+3]=a;}
        int art=texture(S,GL11.GL_RGBA8,pixels);NativeGround.sourceBegin(new NativeSource(12,13,0,3,new float[]{-.5f,0,-.5f,.5f,1,.5f}));
        GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        produce(art,false);NativeGround.sourceEnd();GL11.glDisable(GL11.GL_BLEND);
        int ground=attachment(7),metadata=mask?attachment(6):0,floorMoments=mask?attachment(3):0;
        if(mask) {
            require(floorMoments!=0,"Independent floor moments missing");
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,floorMoments);float[] data=new float[S*S*4];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
            for(int i=0;i<S*S;i++) {
                float a=Math.round(pixels[i*4+3]*255)/255f,c=1-a;
                float[] expected={level*c,level*level*c,c};
                for(int k=0;k<3;k++)require(Math.abs(data[i*4+k]-expected[k])<.0003,"Floor moments depend on source identity or lose native blend at level="+level+" pixel="+i+" channel="+k+" got="+data[i*4+k]+" expected="+expected[k]);
            }
        }
        NativeGround.chunkEnd();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,chunkColor);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        int expectedFloor=mask?copy(floorMoments,GL30.GL_RGBA32F):texture(S,GL30.GL_RGBA32F,null);
        int expectedGround=copy(ground,GL11.GL_RGBA8),expectedMetadata=mask?copy(metadata,GL30.GL_RGBA32F):texture(S,GL30.GL_RGBA32F,null);
        for(int mode=0;mode<3;mode++)for(float zoom:new float[]{.75f,2f,2.25f,2.5f})for(float shift:new float[]{0,.007f}) {
            int min=mode==0?GL11.GL_NEAREST:GL11.GL_LINEAR_MIPMAP_LINEAR,mag=mode==0?GL11.GL_NEAREST:GL11.GL_LINEAR;
            int sampler=mode==2?GL33.glGenSamplers():0;
            if(sampler!=0){GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL33.glSamplerParameterf(sampler,GL14.GL_TEXTURE_LOD_BIAS,-.2f);}
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,W);GL20.glUseProgram(composite);
            GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(composite,"DEPTH"),1);
            uniforms(composite,zoom,shift);
            NativeGround.chunkShader(command); // Native Texture.bind() follows this operation.
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,chunkColor);params(min,mag);GL33.glBindSampler(0,sampler);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL33.glBindSampler(1,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);NativeGround.beforeDraw();
            require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE3,"beforeDraw active texture changed");
            for(int aux=0;aux<(mask?3:1);aux++){
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-aux);require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==(aux==0?ground:aux==1?metadata:floorMoments),"Aux texture bound incorrectly");
                require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==min,"Aux min filter not native");
                require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==mag,"Aux mag filter not native");
                require(GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit-aux)==sampler,"Aux sampler not native");
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==chunkColor && GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0)==sampler,"Native input changed");
            GL13.glActiveTexture(GL13.GL_TEXTURE3);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            float[] actualGround=read(7),actualMetadata=mask?read(6):null,actualFloor=mask?read(3):null;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oracleFbo);GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1,GL30.GL_COLOR_ATTACHMENT2});GL20.glUseProgram(oracle);uniforms(oracle,zoom,shift);
            GL20.glUniform1i(GL20.glGetUniformLocation(oracle,"floorOracle"),0);GL20.glUniform1i(GL20.glGetUniformLocation(oracle,"metadataOracle"),1);GL20.glUniform1i(GL20.glGetUniformLocation(oracle,"floorMomentsOracle"),2);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,expectedGround);params(min,mag);GL33.glBindSampler(0,sampler);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,expectedMetadata);params(min,mag);GL33.glBindSampler(1,sampler);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);GL11.glBindTexture(GL11.GL_TEXTURE_2D,expectedFloor);params(min,mag);GL33.glBindSampler(2,sampler);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);float[] wantedGround=read(0),wantedMetadata=mask?read(1):null,wantedFloor=mask?read(2):null;
            for(int i=0;i<actualGround.length;i++){
                if(i%4!=3){require(Math.abs(actualGround[i]-wantedGround[i])<=2f/255f,"Native filtered ground coverage mismatch mode="+mode+" zoom="+zoom+" i="+i+" actual="+actualGround[i]+" expected="+wantedGround[i]);groundChecks++;}
                if(mask){require(Math.abs(actualMetadata[i]-wantedMetadata[i])<=Math.max(.0001f,Math.abs(wantedMetadata[i])*.000002f),"Signed owner moment mismatch "+i);metadataChecks++;require(Math.abs(actualFloor[i]-wantedFloor[i])<=Math.max(.0001f,Math.abs(wantedFloor[i])*.000002f),"Independent floor mip/filter mismatch "+i);floorChecks++;}
            }
            // An unrelated source on the native unit must not reuse this chunk's sampling cache.
            GL20.glUseProgram(composite);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,expectedGround);params(GL11.GL_NEAREST,GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL13.glActiveTexture(GL13.GL_TEXTURE3);NativeGround.beforeDraw();
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==min,"Unrelated source changed cached filters");
            GL33.glBindSampler(0,0);GL33.glBindSampler(1,0);GL33.glBindSampler(2,0);if(sampler!=0)GL33.glDeleteSamplers(sampler);cases++;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE3);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);NativeGround.finish();
        require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE3,"finish active unit changed");
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==sentinelGround && GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit)==sentinelGroundSampler,"finish ground state not restored");
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-1);require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==sentinelMask && GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit-1)==sentinelMaskSampler,"finish metadata state not restored");
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-2);require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==sentinelFloor && GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit-2)==sentinelFloorSampler,"finish floor state not restored");
        GL11.glDeleteTextures(expectedFloor);GL11.glDeleteTextures(floor);GL11.glDeleteTextures(art);GL11.glDeleteTextures(expectedGround);GL11.glDeleteTextures(expectedMetadata);
    }
    static void ownedSlot()throws Exception {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);int owned=texture(W,GL30.GL_RGBA32F,null);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT3,GL11.GL_TEXTURE_2D,owned,0);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,GL30.GL_COLOR_ATTACHMENT3});
        NativeGround.begin(true);require(NativeGround.floorTexture()==0,"Borrowed an occupied native MRT");
        int p=NativeGroundProbe.link(VERT,NativeGroundShaders.rewrite("owned.frag","#version 330\nlayout(location=0)out vec4 color;layout(location=3)out vec4 nativeAux;void main(){color=vec4(.1,.2,.3,1);nativeAux=vec4(.2,.6,.4,1);}"));
        GL20.glUseProgram(p);NativeGround.setFloor(true);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);NativeGround.finish();
        float[] aux=read(3);require(Math.abs(aux[0]-.2)<.0001 && Math.abs(aux[1]-.6)<.0001 && Math.abs(aux[2]-.4)<.0001,"Existing native MRT output overwritten");
        require(attachment(3)==owned,"Detached native MRT attachment");GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT3,GL11.GL_TEXTURE_2D,0,0);GL11.glDeleteTextures(owned);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
    }
    static void unknownShader()throws Exception {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);NativeGround.begin(true);GL11.glViewport(0,0,W,W);
        GL20.glUseProgram(tile);NativeGround.setFloor(true);GL20.glUniform1i(GL20.glGetUniformLocation(tile,"DIFFUSE"),0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,chunkColor);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        float[] before=read(6),floor=read(3);
        int unpatched=NativeGroundProbe.link(VERT,"#version 330\nvoid main(){gl_FragColor=vec4(.1,.2,.3,1);}");GL20.glUseProgram(unpatched);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        require(java.util.Arrays.equals(before,read(6)) && java.util.Arrays.equals(floor,read(3)),"Unknown legacy shader broadcast color into receiver metadata");
        require(Math.abs(read(0)[0]-.1)<.005,"Unknown native shader color changed");NativeGround.finish();
    }
    static void produce(int texture,boolean floor){GL20.glUseProgram(tile);uniforms(tile,2,0);NativeGround.setFloor(floor);GL20.glUniform1i(GL20.glGetUniformLocation(tile,"DIFFUSE"),0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL33.glBindSampler(0,0);NativeGround.beforeDraw();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
    static void uniforms(int p,float zoom,float shift){GL20.glUniform1f(GL20.glGetUniformLocation(p,"scale"),zoom*.5f);GL20.glUniform2f(GL20.glGetUniformLocation(p,"shift"),shift,shift*.7f);}
    static Object textureObject(int texture)throws Exception {Object t=GameAccess.type("zombie.core.textures.Texture").getConstructor().newInstance();var f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object u=f.get(null);Object id=u.getClass().getMethod("allocateInstance",Class.class).invoke(u,GameAccess.type("zombie.core.textures.TextureID"));GameAccess.set("zombie.core.textures.TextureID","id",id,texture);GameAccess.set("zombie.core.textures.Texture","dataid",t,id);return t;}
    static int texture(int size,int format,float[] pixels){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,size,size,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);params(GL11.GL_NEAREST,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return t;}
    static int copy(int source,int format){int t=texture(S,format,null);GL43.glCopyImageSubData(source,GL11.GL_TEXTURE_2D,0,0,0,0,t,GL11.GL_TEXTURE_2D,0,0,0,0,S,S,1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);return t;}
    static void params(int min,int mag){GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,min);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,mag);}
    static int fbo(int color){int f=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO incomplete");return f;}
    static int attachment(int slot){return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);}
    static float[] read(int slot){float[] p=new float[W*W*4];GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+slot);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,p);return p;}
}
