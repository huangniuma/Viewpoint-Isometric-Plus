import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.*;
import vpiso.*;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.skinnedmodel.model.VertexBufferObject.VertexFormat;
import zombie.core.skinnedmodel.model.VertexBufferObject.VertexType;
import zombie.core.textures.Texture;

/** Real VP maps + receiver geometry + native colour, including own/external/indoor separation. */
public final class NativeSurfaceReceiverProbe {
    static final int W=192,H=192;
    static int fbo,color,ground,depth,vao,vbo,nativeProgram;
    static final NativeReceiver receiver=new NativeReceiver();
    static final NativeOverlay overlay=new NativeOverlay();
    static int opaque;
    static long number;
    static final String VERT="""
        #version 330
        layout(location=0) in vec3 pos;
        uniform mat4 projection;
        void main(){gl_Position=projection*vec4(pos,1.0);}
        """;
    static final String FRAG="""
        #version 330
        layout(location=0) out vec4 color;
        void main(){color=vec4(.7,.55,.4,.8);gl_FragDepth=floor((gl_FragCoord.z+.0007)*65535.0)/65535.0;}
        """;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,H,"native external receiver",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));
            String constants=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,new Class<?>[]{String.class},"constants.glsl");
            Files.writeString(Path.of("research/build034-constants.glsl"),constants);
            opaque=0;
            fixture();
            viewpoint.platform.GlProgram oldProgram=null;
            if(args.length>0) {
                try(var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()},NativeSurfaceReceiverProbe.class.getClassLoader()) {
                    @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                        if(!name.equals("vpiso.NativeOverlay"))return super.loadClass(name,resolve);
                        Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
                    }
                }) {
                    String source=(String)loader.loadClass("vpiso.NativeOverlay").getField("FRAGMENT").get(null);
                    oldProgram=viewpoint.platform.GlProgram.fromSources("017 self-shadow baseline",new viewpoint.platform.GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,source),Map.of(),Map.of(),List.of("017 compositor"));
                }
            }
            int frames=0,received=0;
            for(String kind:new String[]{"pole","wall","furniture"}) {
                float[][] points=kind.equals("furniture")?horizontal(.8f,1.4f):vertical(kind.equals("pole")?.22f:1.4f,kind.equals("pole")?3f:2.8f);
                Frame frame=frame();Object surface=mesh(frame.scene,0,points);
                for(int i=0;i<12;i++) {
                    double shift=(i%6)*.003;
                    fit(shift);frame.scene.cameraCut=true;
                    frame.scene.sunX=-.3f-i*.04f;frame.scene.sunY=(float)Math.sqrt(1-frame.scene.sunX*frame.scene.sunX);
                    int own=draw(frame,points,true);
                    if(own!=0)throw new AssertionError(kind+" self-shadowed "+own+" frame="+i);
                    frames++;
                }
                frame.scene.sunX=-.6f;frame.scene.sunY=.8f;
                if(oldProgram!=null && kind.equals("pole")) {
                    Object current=GameAccess.field("vpiso.NativeOverlay","program",overlay);
                    GameAccess.set("vpiso.NativeOverlay","program",overlay,oldProgram);
                    int self;
                    try {self=draw(frame,points,true);}finally{GameAccess.set("vpiso.NativeOverlay","program",overlay,current);}
                    require(self>10,"017 billboard self-cover negative control did not reproduce: "+self);
                    System.out.println("017 original compositor negative control: own pole dark pixels="+self+", corrected=0");
                }
                // Separate elevated caster, displaced toward the light source.
                float[][] blocker=horizontal(3.4f,.65f);
                for(float[] p:blocker)p[0]-=1.65f;
                Object caster=mesh(frame.scene,1,blocker);
                fit(0);frame.scene.cameraCut=true;
                int cross=draw(frame,points,true);received+=cross;
                if(cross<5)throw new AssertionError(kind+" rejected external shadow: "+cross);
                frames++;
                Arrays.fill(frame.scene.exposure,(byte)0);
                if(draw(frame,points,true)!=0)throw new AssertionError(kind+" darkened native indoor lamps");
                frames++;
                Arrays.fill(frame.scene.exposure,(byte)255);
                if(draw(frame,points,false)!=0)throw new AssertionError(kind+" changed without surface receiver (033 baseline)");
                frames++;
                retire(caster);retire(surface);
                System.out.println("Receiver "+kind+": own=0, external="+cross+", indoor=0, 033 protected foreground=0");
            }
            for(String kind:new String[]{"rigid model","skinned model","vehicle"}) {
                fit(0);Frame frame=frame();float[][] points=horizontal(.8f,1.4f);
                addModel(frame.scene,points,kind.equals("skinned model"),kind.equals("vehicle"));
                require(draw(frame,points,true)==0,kind+" own model darkened");frames++;
                float[][] blocker=horizontal(3.4f,.65f);for(float[] p:blocker)p[0]-=1.65f;
                Object caster=mesh(frame.scene,0,blocker);frame.scene.cameraCut=true;
                int cross=draw(frame,points,true);require(cross>5,kind+" did not receive external shadow: "+cross);frames++;
                received+=cross;
                Arrays.fill(frame.scene.exposure,(byte)0);require(draw(frame,points,true)==0,kind+" indoor light changed");frames++;
                retire(caster);System.out.println("Receiver "+kind+": own=0, external="+cross+", indoor=0");
            }
            Frame character=frame();float[][] tabletop=horizontal(.8f,1.4f);
            Object furniture=mesh(character.scene,0,tabletop);
            float[][] pose=horizontal(3.4f,.65f);for(float[] p:pose)p[0]-=1.65f;
            addModel(character.scene,pose,true,false);
            int characterShadow=draw(character,tabletop,true);
            require(characterShadow>5,"Real skinned caster failed to project onto furniture: "+characterShadow);
            frames++;received+=characterShadow;retire(furniture);
            System.out.println("Actual skinned model caster -> native furniture shadow pixels="+characterShadow);
            require(GL11.glGetError()==0,"GL error");
            System.out.println("Native surface shadows GPU PASS: "+frames+" frames; actual VP meshes/models/vehicle materials/maps/surface shaders and production compositor; moving camera/sun with quantized, biased billboard depth: own-pole/wall/furniture surfaces stay lit; external shadow pixels="+received+", native indoor light and RGBA/depth/stencil preserved; 033 foreground reproduces missing reception.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static Frame frame() {
        Frame f=new Frame();f.scene.sunX=-.6f;f.scene.sunY=.8f;f.scene.sunStrength=1;f.scene.shadowReach=64;
        f.scene.skyR=f.scene.skyG=f.scene.skyB=.6f;
        f.scene.fogR=f.scene.fogG=f.scene.fogB=.6f;
        f.scene.sunR=f.scene.sunG=f.scene.sunB=1f;
        f.scene.exposureAX=32;f.scene.exposureAY=32;Arrays.fill(f.scene.exposure,(byte)255);return f;
    }
    static void fit(double shift) {
        receiver.fit(new double[][]{{.5+shift,.32,.5},{.62+shift,.26,.4985566458},{.38+shift,.26,.4985566458},{.5+shift,.68,.4971132916}});
    }
    static int draw(Frame frame,float[][] points,boolean enabled) {
        frame.number=++number;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,H);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClearDepth(1);GL11.glStencilMask(255);GL11.glClearStencil(31);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{0,0,0,0});
        FloatBuffer data=BufferUtils.createFloatBuffer(points.length*3);for(float[] p:points)data.put(p);data.flip();
        GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glUseProgram(nativeProgram);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(nativeProgram,"projection"),false,NativeSurfaces.projection(receiver).get(new float[16]));
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0L);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);
        ByteBuffer before=read();float[] depthBefore=new float[W*H];GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthBefore);
        overlay.ground(ground);overlay.capture(true);
        IsometricBridge.rendering(frame);
        try {require(NativeShadowMaps.draw(frame.scene,1024,2,enabled?overlay.surfaces(receiver):null),"maps unavailable");}
        finally {IsometricBridge.rendering(null);}
        if(enabled) {
            NativeSurfaces surfaces=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",overlay);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,surfaces.texture());
            float[] samples=new float[W*H*4];GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,samples);
            int visible=0,any=0;for(int i=0;i<W*H;i++){if(samples[i*4+3]>.5)visible++;if(samples[i*4]!=0 || samples[i*4+1]!=0 || samples[i*4+2]!=0)any++;}
            if(frame.scene.exposure[0]!=0 && visible<10) {
                Object prog=GameAccess.field("vpiso.NativeSurfaces","mesh",surfaces);int id=((viewpoint.platform.GlProgram)prog).id();
                for(String uniform:new String[]{"vpIsoFloor","vpIsoExposureOrigin","vpIsoSize","vpIsoExposure"}) {
                    float[] values=new float[4];GL20.glGetUniformfv(id,GL20.glGetUniformLocation(id,uniform),values);
                    System.out.println(uniform+"="+Arrays.toString(values));
                }
                System.out.println("samplers="+GameAccess.field("viewpoint.platform.GlProgram","samplers",prog));
                int tex=((Number)GameAccess.field("vpiso.NativeSurfaces","exposure",surfaces)).intValue();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);ByteBuffer exp=BufferUtils.createByteBuffer(4096);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,exp);
                System.out.println("exposure[2080]="+(exp.get(2080)&255));
                for(int i=0;i<W*H;i++)if(samples[i*4+1]!=0){System.out.println("sample="+Arrays.toString(Arrays.copyOfRange(samples,i*4,i*4+4)));break;}
                throw new AssertionError("Surface map empty: "+visible+" written="+any+" GL="+GL11.glGetError());
            }
        }
        Matrix4f[] matrices={new Matrix4f(NativeShadowMaps.matrix(0)),new Matrix4f(NativeShadowMaps.matrix(1)),new Matrix4f()};
        float[] texels={2*NativeShadowMaps.reach(0)/1024,2*NativeShadowMaps.reach(1)/1024,0};
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,W,H);
        // The pinned017 shader is deliberately injected into the current consumer.
        // Its legacy uniforms are no longer production068's lighting API.
        var legacy=(viewpoint.platform.GlProgram)GameAccess.field("vpiso.NativeOverlay","program",overlay);
        if(legacy!=null && legacy.has("shadowCount")) {
            legacy.use();legacy.setInt("shadowCount",2);
            for(int i=0;i<3;i++)legacy.set("shadowMatrix["+i+"]",matrices[i]);
            legacy.set("shadowTexel",texels[0],texels[1],texels[2]);
            legacy.set("shadowRange",NativeShadowMaps.depthRange());legacy.set("shadowPixel",1f/1024);
            legacy.set("shadowSoftness",1f);
        }
        overlay.draw(receiver,new IsometricEffects(true,.55f,1,64,1024,false,0,1,1,1),NativeShadowMaps.texture(),matrices,texels,NativeShadowMaps.depthRange(),1024,2,1);
        ByteBuffer after=read();int dark=0;
        for(int i=0;i<W*H;i++) {
            if((after.get(i*4)&255)+3<(before.get(i*4)&255))dark++;
            require(after.get(i*4+3)==before.get(i*4+3),"native alpha changed");
        }
        float[] depthAfter=new float[W*H];GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthAfter);
        require(Arrays.equals(depthBefore,depthAfter),"native depth changed");
        ByteBuffer stencil=BufferUtils.createByteBuffer(W*H);GL11.glReadPixels(0,0,W,H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencil);
        for(int i=0;i<W*H;i++)require(stencil.get(i)==31,"native stencil changed");
        int error=GL11.glGetError();require(error==0,"GL error "+error+" frame="+number);
        return dark;
    }
    static Object mesh(SceneData scene,int index,float[][] points) throws Exception {
        float[] vertices=new float[points.length*14];
        org.joml.Vector3f normal=new org.joml.Vector3f(points[1][0]-points[0][0],points[1][1]-points[0][1],points[1][2]-points[0][2])
                .cross(new org.joml.Vector3f(points[2][0]-points[0][0],points[2][1]-points[0][1],points[2][2]-points[0][2])).normalize();
        if(normal.y<0)normal.negate();
        for(int i=0;i<points.length;i++) {
            System.arraycopy(points[i],0,vertices,i*14,3);vertices[i*14+7]=vertices[i*14+8]=1;
            vertices[i*14+9]=normal.x;vertices[i*14+10]=normal.y;vertices[i*14+11]=normal.z;vertices[i*14+13]=Float.intBitsToFloat(opaque);
        }
        Object pages=java.lang.reflect.Array.newInstance(GameAccess.type("zombie.core.textures.TextureID"),1);
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);Object unsafe=uf.get(null);
        Object page=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,GameAccess.type("zombie.core.textures.TextureID"));
        GameAccess.set("zombie.core.textures.TextureID","id",page,NativeGroundProbe.solid(255,255,255,255));
        java.lang.reflect.Array.set(pages,0,page);
        Object mesh=GameAccess.type("viewpoint.render.ChunkMeshData").getConstructor(pages.getClass(),int[].class,int[].class,float[].class,int.class)
                .newInstance(pages,new int[]{0},new int[]{points.length},vertices,0);
        ((Object[])GameAccess.field("viewpoint.render.SceneData","meshes",scene))[index]=mesh;
        scene.meshCount=index+1;scene.meshFlags[index]=2|8|16;scene.meshFade[index*2]=0;scene.meshFade[index*2+1]=2;return mesh;
    }
    static float[][] vertical(float half,float height) {
        return new float[][]{{0,0,-half},{0,height,-half},{0,height,half},{0,0,-half},{0,height,half},{0,0,half}};
    }
    static float[][] horizontal(float height,float half) {
        return new float[][]{{-half,height,-half},{half,height,-half},{half,height,half},{-half,height,-half},{half,height,half},{-half,height,half}};
    }
    static void retire(Object mesh){GameAccess.call("viewpoint.render.ChunkMeshData","retire",mesh,new Class<?>[0]);}
    static Object allocate(Class<?> type) throws Exception {
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);Object unsafe=uf.get(null);
        return unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type);
    }
    static void addModel(SceneData scene,float[][] points,boolean skin,boolean vehicle) throws Exception {
        VertexFormat format=new VertexFormat(skin?5:3);
        format.setElement(0,VertexType.VertexArray,12);format.setElement(1,VertexType.NormalArray,12);
        format.setElement(2,VertexType.TextureCoordArray,8);
        if(skin){format.setElement(3,VertexType.BlendWeightArray,16);format.setElement(4,VertexType.BlendIndexArray,16);}format.calculate();
        FloatBuffer values=BufferUtils.createFloatBuffer(points.length*(skin?16:8));
        for(float[] p:points){values.put(p).put(0).put(1).put(0).put(.5f).put(.5f);if(skin)values.put(new float[]{1,0,0,0,0,0,0,0});}values.flip();
        int vb=GL15.glGenBuffers(),eb=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vb);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,values,GL15.GL_STATIC_DRAW);
        IntBuffer indices=BufferUtils.createIntBuffer(points.length);for(int i=0;i<points.length;i++)indices.put(i);indices.flip();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,eb);GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER,indices,GL15.GL_STATIC_DRAW);
        VertexBufferObject object=(VertexBufferObject)allocate(VertexBufferObject.class);
        VertexBufferObject.Vbo handle=new VertexBufferObject.Vbo();handle.vboId=vb;handle.eboId=eb;handle.numElements=points.length;handle.vertexStride=format.getStride();
        GameAccess.set(VertexBufferObject.class.getName(),"handle",object,handle);GameAccess.set(VertexBufferObject.class.getName(),"vertexFormat",object,format);
        ModelMesh model=(ModelMesh)allocate(ModelMesh.class);model.vb=object;
        Texture texture=new Texture(){@Override public boolean isReady(){return true;}};Object page=allocate(GameAccess.type("zombie.core.textures.TextureID"));
        GameAccess.set("zombie.core.textures.TextureID","id",page,NativeGroundProbe.solid(255,255,255,255));
        GameAccess.set("zombie.core.textures.TextureID","glMinFilter",page,GL11.GL_NEAREST);
        GameAccess.set("zombie.core.textures.TextureID","glMagFilter",page,GL11.GL_NEAREST);
        GameAccess.set("zombie.core.textures.Texture","dataid",texture,page);
        int palette=skin?scene.models.palette(new float[]{1,0,0,0,0,1,0,0,0,0,1,0},1):-1;
        int index=scene.models.add(model,null,texture,new Matrix4f(),palette,palette,GL11.GL_NONE,ModelDraws.COLOUR|ModelDraws.SHADOW);
        scene.models.bounds(index,0,.8f,0,2);scene.models.material(index,1,1,1,1);scene.models.light(index,1,1,1,1);
        if(vehicle) {
            float[][] switches=new float[12][16];
            for(float[] matrix:switches){matrix[0]=matrix[5]=matrix[10]=matrix[15]=1;}
            int material=scene.models.vehicles.add(new Texture[7],switches,0,1,1,1,VehicleMaterials.NO_PAINT,0);
            scene.models.vehicle(index,material);
        }
    }
    static void fixture() {
        color=NativeGroundProbe.texture(W,H,null);ground=NativeGroundProbe.texture(W,H,null);
        fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT7,GL11.GL_TEXTURE_2D,ground,0);
        depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,H);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
        vao=GL30.glGenVertexArrays();vbo=GL15.glGenBuffers();nativeProgram=NativeGroundProbe.link(VERT,FRAG);fit(0);
    }
    static ByteBuffer read(){ByteBuffer p=BufferUtils.createByteBuffer(W*H*4);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);return p;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
