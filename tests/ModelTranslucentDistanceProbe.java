import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import vpiso.*;

/** Actual VP forward OIT outputs -> actual VP post, including nonempty translucent layers. */
public final class ModelTranslucentDistanceProbe {
    private static final Pattern INCLUDE=Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final float[] ART={.50f,.42f,.20f}, BACK={.25f,.20f,.12f}, FOG={.08f,.25f,.75f};
    private static final String TRIANGLE="""
        #version 330
        out vec2 vUv;
        void main() {
          vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
          vUv=p; gl_Position=vec4(p*2.-1.,0.,1.);
        }
        """;
    private static final String FORWARD_VERTEX="""
        #version 330
        uniform vec3 testPos;
        uniform vec3 testNormal;
        out vec2 vUv;
        out vec3 vLocal;
        flat out vec4 vRect;
        out vec3 vNormal;
        out vec3 vPos;
        out float vDistance;
        flat out float vFill;
        flat out int vFlags;
        flat out float vFloorLayer;
        flat out vec2 vFade;
        flat out vec2 vLightOrigin;
        flat out vec3 vpIsoTreeRoot;
        flat out int vpIsoPlants;
        void main() {
          vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
          vUv=p; vLocal=vec3(p.x,1.,p.y);
          vPos=testPos+vec3((p.x-.5)*.01,(p.y-.5)*.01,0.);
          vNormal=testNormal; vDistance=1.; vFill=0.; vFlags=0; vFloorLayer=0.;
          vRect=vec4(0.,0.,1.,1.); vFade=vec2(0.,1.); vLightOrigin=vec2(0.);
          vpIsoTreeRoot=vec3(0.); vpIsoPlants=0;
          gl_Position=vec4(p*2.-1.,0.,1.);
        }
        """;
    private static String defines;
    private static String pack="library";
    private static boolean baselineMode;
    private static int vao,oitFbo,postFbo,art,background,depth,clear,revealInput,output;
    private static final int[] oit=new int[3];
    private static GlProgram forward,post;

    public static void main(String[] args)throws Exception {
        boolean baseline=args.length>0&&args[0].equals("baseline041");
        baselineMode=baseline;
        if(args.length>0&&!baseline)pack=args[0];
        if(!pack.equals("library")) Main.compatible=true;
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(16,16,"VPISO translucent distance",0,0);
        if(window==0)throw new AssertionError("Hidden GL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off"));
            defines=(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active());
            GlProgram.onUse(IsometricShaders::onUse);
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            forward=program("actual-forward-oit",FORWARD_VERTEX,expand("forward.frag",new HashSet<>()));
            post=program("actual-post-oit",TRIANGLE,expand("post.frag",new HashSet<>()));
            assignSamplers(forward);assignSamplers(post);
            art=texture(new float[]{ART[0],ART[1],ART[2],.5f});
            background=texture(new float[]{0,0,0,1});depth=texture(new float[]{.5f,.5f,.5f,1});
            clear=texture(new float[]{0,0,0,0});revealInput=texture(new float[]{1,1,1,1});output=texture(new float[]{0,0,0,0});
            oitFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oitFbo);
            for(int i=0;i<3;i++) {oit[i]=texture(new float[]{0,0,0,0});GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+i,GL11.GL_TEXTURE_2D,oit[i],0);}
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1,GL30.GL_COLOR_ATTACHMENT2});
            complete();postFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,postFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);complete();
            auditProducers(baseline);
            int cases=0;float maxNearError=0,maxOpaqueError=0,maxFarError=0,maxBadBlue=0;
            for(float camera:new float[]{2000,6000,12000})for(float yaw:new float[]{0,.7f,1.8f,3.4f})
                for(float alpha:new float[]{0,.08f,.25f,.5f,.8f})for(boolean foliage:new boolean[]{false,true}) {
                    float[] got=render(camera,yaw,20,alpha,foliage,true);
                    float[] expected=pack.equals("library")?composite(alpha):render(40,yaw,20,alpha,foliage,true);
                    float error=error(got,expected);
                    if(alpha==0)maxOpaqueError=Math.max(maxOpaqueError,error);
                    else {maxNearError=Math.max(maxNearError,error);maxBadBlue=Math.max(maxBadBlue,got[2]-expected[2]);}
                    if(!baseline&&error>.0015f)throw new AssertionError("Nearby alpha surface fog-tinted: D="+camera+" yaw="+yaw+" alpha="+alpha+" foliage="+foliage+" RGB="+java.util.Arrays.toString(got)+" error="+error);
                    cases++;
                }
            for(float alpha:new float[]{0,.08f,.5f,.8f}) {
                float[] got=render(6000,.7f,300,alpha,true,true);
                float[] expected=pack.equals("library")?FOG:render(40,.7f,300,alpha,true,true);
                maxFarError=Math.max(maxFarError,error(got,expected));
                if(error(got,expected)>.0015f)throw new AssertionError("Real distant fog disabled: "+java.util.Arrays.toString(got));
            }
            // A conventional VP eye uses the original camera distances, with addon scopes off.
            if(pack.equals("library")) for(float yaw:new float[]{0,.7f,1.8f})for(float alpha:new float[]{0,.25f,.8f}) {
                float[] got=render(40,yaw,20,alpha,false,false);
                if(error(got,composite(alpha))>.0015f)throw new AssertionError("Ordinary VP scope changed");
            }
            if(baseline) {
                if(maxNearError<.25f||maxBadBlue<.25f||maxOpaqueError>.0015f)
                    throw new AssertionError("Pinned041 alpha-only blue failure not reproduced: "+maxNearError+","+maxBadBlue+","+maxOpaqueError);
                System.out.println("Translucent blue negative control PASS: pinned041 actual forward OIT -> actual post; cases="+cases+" nearError="+maxNearError+" addedBlue="+maxBadBlue+" opaqueError="+maxOpaqueError);
            } else System.out.println("Translucent distance GPU PASS: pack="+pack+"; actual forward OIT -> actual post; cases="+cases+" nearError="+maxNearError+" opaqueError="+maxOpaqueError+" farFogError="+maxFarError+"; 3 render-eye distances, 4 yaws, 5 alpha values, fence/foliage normals; physical distance storage, logical fog, ordinary VP isolation; producer audit includes glass/flame/particle/fishing line. No live-map acceptance.");
        }finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }

    private static float[] render(float distance,float yaw,float logicalX,float alpha,boolean foliage,boolean owned)throws Exception {
        if(owned)IsometricBridge.modelRendering(new Frame(),new IsometricEffects(false,1,0,64,256,false,1,1,1,1));
        else IsometricBridge.rendering(null);
        Vector3f pos=new Vector3f(logicalX,1.5f,0),direction=new Vector3f((float)Math.cos(yaw),-.45f,(float)Math.sin(yaw)).normalize();
        Vector3f eye=new Vector3f(direction).mul(-distance).add(pos),back=new Vector3f(direction).mul(8).add(pos);
        Matrix4f inverse=new Matrix4f().set(new float[]{1,0,0,0,0,1,0,0,
                direction.x*100,direction.y*100,direction.z*100,0,back.x,back.y,back.z,1});
        update(art,new float[]{ART[0],ART[1],ART[2],alpha>0?alpha:.5f});
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oitFbo);GL11.glViewport(0,0,1,1);GL30.glBindVertexArray(vao);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,0,0});GL30.glClearBufferfv(GL11.GL_COLOR,1,new float[]{0,0,0,0});GL30.glClearBufferfv(GL11.GL_COLOR,2,new float[]{0,0,0,0});
        if(alpha>0) {
            forward.use();bind(0,art);forward.setInt("uTexture",0);forward.setInt("uLight",0);
            forward.setInt("uDebugView",1);forward.set("uAlphaRange",.04f,.85f);forward.set("uLightAtlasSize",1,1);
            forward.set("uEye",eye.x,eye.y,eye.z);forward.set("testPos",pos.x,pos.y,pos.z);
            forward.set("testNormal",foliage?0:1,foliage?.2f:0,0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            GL11.glFinish();GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT2);
            float[] at=read();float stored=at[0]/at[1];
            if(!Float.isFinite(stored)||stored<1)throw new AssertionError("Actual forward OIT layer absent: "+stored+" GL="+GL11.glGetError());
            // Check individual distances in the corrected candidate; the pinned control is
            // validated by its end-to-end blue output instead of expecting the new invariant.
            if(!baselineMode&&Math.abs(stored-distance)>.02f)
                throw new AssertionError("OIT physical ray distance: "+stored+" != "+distance);
        }
        update(background,new float[]{ART[0]*alpha+BACK[0]*(1-alpha),ART[1]*alpha+BACK[1]*(1-alpha),ART[2]*alpha+BACK[2]*(1-alpha),1});
        update(revealInput,new float[]{1-alpha,1-alpha,1-alpha,1});
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,postFbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        post.use();post.setInt("uColor",0);post.setInt("uDepth",1);post.setInt("uVolume",2);post.setInt("uBloom",2);
        post.setInt("uTranslucentSum",3);post.setInt("uTranslucentReveal",4);post.setInt("uTranslucentDistance",5);
        if(post.has("uSunShafts"))post.setInt("uSunShafts",2);
        post.set("uExposure",1);post.set("uVolumeOn",0);post.set("uBloomStrength",0);post.set("uHalfSize",1,1);
        post.set("uFogRange",100,200);post.set("uHaze",1.0e20f,0);post.set("uFogColor",FOG[0],FOG[1],FOG[2]);
        post.set("uEye",eye.x,eye.y,eye.z);post.set("uInvViewProjection",inverse);post.set("uCamera",1,20000,1,1);
        bind(0,background);bind(1,depth);bind(2,clear);bind(3,oit[0]);bind(4,revealInput);bind(5,oit[2]);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);float[] got=read();
        if(GL11.glGetError()!=0)throw new AssertionError("Actual OIT/post GL error");
        // Comparisons tolerate the actual post's shared display dither.
        return got;
    }
    private static float[] composite(float a){return new float[]{ART[0]*a+BACK[0]*(1-a),ART[1]*a+BACK[1]*(1-a),ART[2]*a+BACK[2]*(1-a)};}
    private static float error(float[]a,float[]b){return Math.max(Math.max(Math.abs(a[0]-b[0]),Math.abs(a[1]-b[1])),Math.abs(a[2]-b[2]));}
    private static void auditProducers(boolean baseline)throws Exception {
        int changedPhysicalDistances=0;
        Pattern rewritten=Pattern.compile("vpIsoDistance\\(\\s*(length|distance)\\(");
        for(String path:new String[]{"forward.frag","model_glass.frag","vehicle_glass.frag","flame.vert","particle.vert","particle_sim.vert","fishing_line.vert"}) {
            String raw=resource(path),fixed=IsometricShaders.rewrite(path,raw);
            if(!raw.contains("uEye")||!(raw.contains("distance(")||raw.contains("length(")))
                throw new AssertionError("Actual producer distance layout changed: "+path);
            if(rewritten.matcher(fixed).find())changedPhysicalDistances++;
        }
        if(changedPhysicalDistances!=(baseline?4:0))
            throw new AssertionError("Producer ray distances incorrectly rewritten: "+changedPhysicalDistances);
        String composite=IsometricShaders.rewrite("post.frag",resource("post.frag"));
        if(composite.contains("vpIsoDistance(distance(pos, uEye), pos)")!=baseline)
            throw new AssertionError("Post reconstruction/volume ratio camera distance regression");
        String accumulator=resource("lib/translucent.glsl");
        if(!accumulator.contains("translucentDistance = vec2(fromEye, 1.0) * (alpha * alpha)"))
            throw new AssertionError("OIT distance accumulation format changed");
    }
    private static void assignSamplers(GlProgram p) {
        p.use();IntBuffer size=BufferUtils.createIntBuffer(1),type=BufferUtils.createIntBuffer(1);
        for(int i=0;i<GL20.glGetProgrami(p.id(),GL20.GL_ACTIVE_UNIFORMS);i++) {
            String name=GL20.glGetActiveUniform(p.id(),i,size,type);int t=type.get(0),unit=-1;
            if(t==GL20.GL_SAMPLER_2D)unit=0;
            else if(t==GL30.GL_SAMPLER_2D_ARRAY)unit=10;
            else if(t==GL30.GL_SAMPLER_2D_ARRAY_SHADOW)unit=11;
            else if(t==GL20.GL_SAMPLER_3D)unit=12;
            else if(t==GL31.GL_SAMPLER_BUFFER)unit=13;
            else if(t==GL31.GL_UNSIGNED_INT_SAMPLER_BUFFER)unit=14;
            else if(t==GL31.GL_INT_SAMPLER_BUFFER)unit=15;
            if(unit>=0)for(int n=0;n<size.get(0);n++)GL20.glUniform1i(GL20.glGetUniformLocation(p.id(),name.replace("[0]","["+n+"]")),unit);
        }
    }
    private static GlProgram program(String name,String vertex,String fragment) {
        int at=fragment.indexOf('\n');fragment=fragment.substring(0,at+1)
                +"#extension GL_ARB_shader_storage_buffer_object : enable\n#extension GL_ARB_shader_image_load_store : enable\n"
                +defines+fragment.substring(at+1);
        return GlProgram.fromSources(name,new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of(),java.util.List.of());
    }
    private static String resource(String path)throws Exception {
        if(path.equals("constants.glsl"))return(String)GameAccess.staticCall("viewpoint.platform.Shaders","constants");
        if(!pack.equals("library"))return(String)GameAccess.call("viewpoint.platform.Shaders","read",null,new Class<?>[]{String.class,viewpoint.platform.ShaderPack.class,boolean[].class},path,ShaderPacks.active().pack,new boolean[1]);
        try(InputStream in=ClassLoader.getSystemResourceAsStream("viewpoint/shaders/"+path)){
            if(in==null)throw new AssertionError("Missing actual VP resource "+path);
            return new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }
    }
    private static String expand(String path,HashSet<String> stack)throws Exception {
        if(!stack.add(path))throw new AssertionError("Include cycle "+path);
        String source=IsometricShaders.rewrite(path,resource(path));StringBuilder out=new StringBuilder();
        for(String line:source.split("\n",-1)){var m=INCLUDE.matcher(line);if(m.matches())out.append(expand(m.group(1),stack));else out.append(line).append('\n');}
        stack.remove(path);return out.toString();
    }
    private static int texture(float[]rgba){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);FloatBuffer b=BufferUtils.createFloatBuffer(4);b.put(rgba).flip();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,b);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    private static void update(int texture,float[]rgba){GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);FloatBuffer b=BufferUtils.createFloatBuffer(4);b.put(rgba).flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,b);}
    private static void bind(int unit,int t){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);}
    private static void complete(){if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO incomplete");}
    private static float[] read(){FloatBuffer b=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,b);return new float[]{b.get(0),b.get(1),b.get(2),b.get(3)};}
}
