import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Real three-card plants, production maps/receiver/compositor; no envelope algorithm in this oracle. */
public final class NativePlants072Probe {
    static final NativeShadow068Probe P=new NativeShadow068Probe();
    static final NativeSurfaceReceiverProbe N=P.N;
    static final float SQRT6=2.4494896f;
    static boolean baseline;
    static Path output;
    static float half,height,angle;
    static int cases,negative,totalExternal,sharedSwayChecks;
    static NativeSurfaces surfaces;
    static float[][] actualCards;
    static int actualPlanes=3;
    static final String VERTEX="""
        #version 330
        layout(location=0) in vec3 pos;uniform mat4 projection;out vec3 physical;
        void main(){physical=pos;gl_Position=projection*vec4(pos,1);}
        """;
    static final String NATIVE="""
        #version 330
        in vec3 physical;
        layout(location=0) out vec4 color;layout(location=6) out vec4 metadata;layout(location=7) out vec4 ground;
        uniform float cardHeight,halfWidth,depthBias,flatDepth;uniform vec2 across;uniform vec4 owner;
        void main(){float x=dot(physical.xz,across);
          vec2 uv=vec2(x/(2*halfWidth)+.5,1-physical.y/cardHeight);
          vec2 e=(uv-vec2(.5,.42))/vec2(.46,.40);bool trunk=abs(uv.x-.5)<.05&&uv.y>.5;
          if(!trunk&&dot(e,e)>1)discard;
          color=vec4(.7,.55,.4,.8);metadata=owner;ground=vec4(0);
          gl_FragDepth=floor((mix(gl_FragCoord.z,.5,flatDepth)+depthBias)*65535.0)/65535.0;}
        """;

    public static void main(String[] args)throws Exception {
        P.pack=args.length==0?"default":args[0];baseline=Arrays.asList(args).contains("baseline071");
        output=Path.of(args.length>1?args[1]:"build/tests072");Files.createDirectories(output);
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"072 crown solar reception",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();P.nativeProgram=NativeGroundProbe.link(VERTEX,NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            if(Arrays.asList(args).contains("actualParts")){actualParts();return;}
            if(Arrays.asList(args).contains("actualBush")){actualBush();return;}
            for(boolean tree:new boolean[]{true,false}) {
                half=tree?1.8f:.70710677f;height=tree?5.4f:.62513024f;
                actualCards=tree?null:new float[][]{{-.70710677f,.70710677f,-.30618623f,.62513024f}};actualPlanes=tree?3:2;
                for(int state=0;state<(baseline?1:3);state++) {
                    Frame f=frame(state);fit(tree,state);
                    Object[] objects=plant(f,tree?0:4096);float[][] art=billboard();
                    draw(f,art,tree);
                    int dark=dark(P.actual),seams=seams(P.actual);validatePayload();
                    if(baseline){N.require(dark>50&&seams>2,"071 crossed-card seam negative did not reproduce: "+dark+"/"+seams);negative++;}
                    else N.require(dark==0&&seams==0,"Plant self reception seam tree="+tree+" state="+state+" dark="+dark+" seams="+seams);
                    cases++;System.out.println("Crown072 tree="+tree+" state="+state+" selfDark="+dark+" seams="+seams);
                    if(state==0&&!baseline)external(f,art,tree);
                    for(Object o:objects)N.retire(o);
                }
            }
            if(!baseline){actualParts();actualCards=null;actualPlanes=3;reservedPlants();}lampCompatibility();
            N.require(GL11.glGetError()==0,"GL error end");
            System.out.println((baseline?"Pinned071 plant negative PASS":"Native plants072 GPU PASS")+" pack="+P.pack+" cases="+cases+" negatives="+negative+" externalPixels="+totalExternal
                +" sharedSwayChecks="+sharedSwayChecks+"; real plant slots/maps, high tree, observed multipart crown and two-plane grass-flag bush, changing sun/camera and scene-wind inputs with actual caster/receiver sway equality, independent no-own-caster external-map oracle, independent continuous-surface lamp reference, native RGBA/depth/stencil preserved; controlled fixture, live visual acceptance pending.");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static Frame frame(int state){
        Frame f=P.frame();f.scene.originX=f.scene.originY=f.scene.originZ=0;
        float[][] suns={{-.92355883f,.28082022f,.2611112f},{-.4f,.8f,.4472136f},{.8f,.5f,-.33166248f}};
        f.scene.sunX=suns[state][0];f.scene.sunY=suns[state][1];f.scene.sunZ=suns[state][2];
        f.scene.windX=state==0?0:state==1?2:5;f.scene.windZ=state==0?0:state==1?.8f:-3;
        f.scene.gusts=state==0?0:.6f;f.scene.time=f.scene.cloudTime=state*1.7f;
        return f;
    }
    static void fit(boolean tree,int state){
        angle=state*.45f;double theta=angle+Math.PI*.75,s=tree?.055:.27,base=tree?.15:.28;
        double ax=Math.cos(theta),az=Math.sin(theta),bx=-az,bz=ax;
        N.receiver.fit(new double[][]{{.5,base,.5},{.5-ax*s,base+bx*s*.5,.5+bx*.001},
            {.5-az*s,base+bz*s*.5,.5+bz*.001},{.5,base+SQRT6*s*.9,.5-SQRT6*.0012}});
        N.require(N.receiver.inverse.m22()<0,"Native fixture must view from above, like captured game camera");
    }
    static float[][] billboard(){
        float a=(float)Math.cos(angle+Math.PI*.75)*half,b=(float)Math.sin(angle+Math.PI*.75)*half;
        return new float[][]{{-a,0,-b},{-a,height,-b},{a,height,b},{-a,0,-b},{a,height,b},{a,0,b}};
    }
    static Object[] plant(Frame f)throws Exception{return plant(f,0);}
    static Object[] plant(Frame f,int extraFlags)throws Exception{
        Object template=N.mesh(f.scene,0,N.horizontal(-5,.05f));
        Object pages=GameAccess.field("viewpoint.render.ChunkMeshData","pages",template);
        Object page=java.lang.reflect.Array.get(pages,0);GameAccess.set("zombie.core.textures.TextureID","id",page,texture());
        float[][] cards=actualCards==null?new float[][]{{-half,half,0,height}}:actualCards;
        FloatBuffer records=org.lwjgl.system.MemoryUtil.memAllocFloat(14*actualPlanes*cards.length);
        for(int part=0;part<cards.length;part++)for(int plane=0;plane<actualPlanes;plane++)records.put(new float[]{0,0,0,Float.intBitsToFloat((actualPlanes<<19)|(plane<<16)|extraFlags|(part==0?0:2)),0,0,1,1,cards[part][0],cards[part][1],cards[part][2],cards[part][3],0,0});
        records.flip();Object plant=GameAccess.type("viewpoint.render.ChunkMeshData")
            .getConstructor(pages.getClass(),int[].class,int[].class,FloatBuffer.class,int.class,int[].class,int[].class)
            .newInstance(pages,new int[]{0},new int[]{0},records,0,null,null);
        GameAccess.call("viewpoint.render.ChunkMeshData","plants",plant,new Class<?>[]{int.class,int[].class,int[].class},0,new int[]{0},new int[]{actualPlanes*cards.length});
        ((Object[])GameAccess.field("viewpoint.render.SceneData","meshes",f.scene))[0]=plant;P.publish(plant,true,0);
        return new Object[]{plant,template};
    }
    static int texture(){
        int side=256;ByteBuffer data=BufferUtils.createByteBuffer(side*side*4);
        for(int y=0;y<side;y++)for(int x=0;x<side;x++){
            float u=(x+.5f)/side,v=(y+.5f)/side,ex=(u-.5f)/.46f,ey=(v-.42f)/.4f;
            boolean inside=ex*ex+ey*ey<=1||Math.abs(u-.5)<.05&&v>.5;
            data.put((byte)255).put((byte)255).put((byte)255).put((byte)(inside?255:0));
        }data.flip();int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,side,side,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);return t;
    }
    static void invalidate(){
        Object pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        if(pass!=null){
            Arrays.fill((boolean[])GameAccess.field("viewpoint.render.ShadowPass","valid",pass),false);
            GameAccess.call("viewpoint.render.CascadeLayers","release",GameAccess.field("viewpoint.render.ShadowPass","layers",pass),new Class<?>[0]);
        }
    }
    static void draw(Frame f,float[][] art,boolean tree)throws Exception{
        GL20.glUseProgram(P.nativeProgram);
        GL20.glUniform1f(GL20.glGetUniformLocation(P.nativeProgram,"cardHeight"),height);
        GL20.glUniform1f(GL20.glGetUniformLocation(P.nativeProgram,"halfWidth"),half);
        GL20.glUniform1f(GL20.glGetUniformLocation(P.nativeProgram,"flatDepth"),tree?0:1);
        GL20.glUniform2f(GL20.glGetUniformLocation(P.nativeProgram,"across"),(float)Math.cos(angle+Math.PI*.75),(float)Math.sin(angle+Math.PI*.75));
        invalidate();P.draw(f,art,0,new float[]{1,0,0,1},0,true);
        surfaces=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
        // Actual shipped shadow programs currently retain default sway even as scene
        // wind inputs vary. Compare bindings rather than inventing wind movement.
        GlProgram receiver=(GlProgram)GameAccess.field("vpiso.NativeSurfaces","mesh",surfaces);
        Object pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        GlProgram caster=(GlProgram)GameAccess.field("viewpoint.render.ShadowPass","program",pass);
        N.require(Arrays.equals(sway(receiver),sway(caster)),"Receiver/caster plant sway diverged");sharedSwayChecks++;
    }
    static void external(Frame f,float[][] art,boolean tree)throws Exception{
        float dy=height*.5f+.5f;
        float span=half+Math.max(Math.abs(f.scene.sunX),Math.abs(f.scene.sunZ))/f.scene.sunY*height*.5f;
        float[][] points=N.horizontal(height+.5f,span*.65f);
        for(float[] p:points){p[0]+=f.scene.sunX/f.scene.sunY*dy;p[2]+=f.scene.sunZ/f.scene.sunY*dy;}
        Object caster=N.mesh(f.scene,1,points);draw(f,art,tree);byte[] actual=P.actual.clone();int dark=dark(actual);
        N.require(dark>5,"External crown reception empty: "+dark);totalExternal+=dark;
        // Independent discriminator: regenerate the actual VP maps without this crown as caster.
        // The production receiver/payload stays unchanged; no cylinder math is reimplemented.
        byte flags=f.scene.meshFlags[0];f.scene.meshFlags[0]=0;invalidate();IsometricBridge.rendering(f);
        try{N.require(NativeShadowMaps.draw(f.scene,1024,2,null),"External-only maps empty");}
        finally{IsometricBridge.rendering(null);f.scene.meshFlags[0]=flags;}
        recompose(f);byte[] expected=P.copy(N.read());
        N.require(P.different(expected,actual,3)==0,"Plant self caster changed exterior-envelope projection");
        cases++;N.retire(caster);f.scene.meshCount=1;
    }
    static void recompose(Frame f){
        int count=NativeShadowMaps.count();Matrix4f[] m={new Matrix4f(),new Matrix4f(),new Matrix4f()};float[] t=new float[3];
        for(int i=0;i<count;i++){m[i].set(NativeShadowMaps.matrix(i));t[i]=2*NativeShadowMaps.reach(i)/1024;}
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
        N.overlay.draw(N.receiver,P.ON,count>0?NativeShadowMaps.texture():0,m,t,count>0?NativeShadowMaps.depthRange():1,1024,count,f.scene.sunStrength);
        P.preserved();
    }
    static float[] sway(GlProgram program){float[] x=new float[4];int at=GL20.glGetUniformLocation(program.id(),"uPlantSway");if(at>=0)GL20.glGetUniformfv(program.id(),at,x);return x;}
    static void validatePayload(){
        float[] pos=NativeReceivers071Probe.texture(surfaces.texture()),normal=NativeReceivers071Probe.texture(surfaces.normals());int count=0;
        for(int i=0;i<N.W*N.H;i++)if(Math.abs(pos[i*4+3])>3.5){count++;
            N.require(Math.abs(Math.abs(pos[i*4+3])-(baseline?4:5))<.01,"Wrong plant receiver payload kind");
            for(int c=0;c<3;c++)N.require(Float.isFinite(normal[i*4+c]),"Nonfinite crown payload");
            N.require(Math.abs(normal[i*4+3])<.01,"Plant owner floor changed");
            if(baseline)N.require(Math.abs(normal[i*4])<.002&&Math.abs(normal[i*4+1]-1)<.002&&Math.abs(normal[i*4+2])<.002,"071 upward normal control changed");
        }N.require(count>100,"Empty plant receiver");
    }
    static int dark(byte[] rgb){int n=0;for(int i=0;i<N.W*N.H;i++)if((P.before[i*4]&255)>170&&(rgb[i*4]&255)+3<(P.before[i*4]&255))n++;return n;}
    static int seams(byte[] rgb){int n=0;for(int i=0;i<N.W*N.H-1;i++)if(i%N.W+1<N.W&&(P.before[i*4]&255)>170&&(P.before[(i+1)*4]&255)>170&&Math.abs((rgb[i*4]&255)-(rgb[(i+1)*4]&255))>20)n++;return n;}
    static void reservedPlants()throws Exception{
        half=.7f;height=.7f;fit(false,0);
        for(int flag:new int[]{4096,512}){
            Frame f=frame(0);Object[] objects=plant(f,flag);draw(f,billboard(),false);
            float[] pos=NativeReceivers071Probe.texture(surfaces.texture());int accepted=0;
            for(int i=0;i<N.W*N.H;i++)if(Math.abs(pos[i*4+3])>.5){accepted++;N.require(Math.abs(Math.abs(pos[i*4+3])-(flag==4096?5:4))<.01,"Grass bush or facing plant has wrong crown payload");}
            N.require(accepted>100,"Reserved plant empty");for(Object o:objects)N.retire(o);cases++;
        }
    }
    static void actualParts()throws Exception{
        actualCards=new float[][]{{-2.9278640747070312f,2.5190677642822266f,-.19136638939380646f,10.81857967376709f},
            {-3.1046407222747803f,2.773184299468994f,-.19136638939380646f,11.073735237121582f}};
        actualPlanes=3;half=3.1046407f;height=11.073735f;fit(true,0);Frame f=frame(0);
        f.scene.sunX=-.888565f;f.scene.sunY=.4372087f;f.scene.sunZ=.13892712f;Object[] objects=plant(f);
        draw(f,billboard(),true);validatePayload();
        int dark=dark(P.actual),seams=seams(P.actual);
        System.out.println("Actual observed two-part three-plane crown: selfDark="+dark+" seams="+seams);
        if(!baseline){N.require(dark==0&&seams==0,"Observed multi-part crown retains seam");cases++;external(f,billboard(),true);}
        Files.write(output.resolve("actual-multipart-"+(baseline?"071":"072")+".rgba8"),P.actual);
        for(Object o:objects)N.retire(o);
    }
    static void actualBush()throws Exception{
        actualCards=new float[][]{{-.70710677f,.70710677f,-.30618623f,.62513024f}};actualPlanes=2;
        half=.70710677f;height=.62513024f;fit(false,0);Frame f=frame(0);Object[] objects=plant(f,4096);
        draw(f,billboard(),false);float[] pos=NativeReceivers071Probe.texture(surfaces.texture());int kind4=0,kind5=0;
        for(int i=0;i<N.W*N.H;i++){if(Math.abs(Math.abs(pos[i*4+3])-4)<.01)kind4++;if(Math.abs(Math.abs(pos[i*4+3])-5)<.01)kind5++;}
        System.out.println("Actual observed grass-flag bush: kind4="+kind4+" kind5="+kind5+" selfDark="+dark(P.actual)+" seams="+seams(P.actual));
        if(!baseline){N.require(kind5>100&&kind4==0,"Actual grass-flag bush missed crown payload");N.require(dark(P.actual)==0&&seams(P.actual)==0,"Actual bush retains crossed-card seam");external(f,billboard(),false);}
        for(Object o:objects)N.retire(o);
    }
    static void lampCompatibility()throws Exception{
        actualCards=null;actualPlanes=3;half=.7f;height=.7f;fit(false,0);Frame f=frame(0);Object[] plants=plant(f);float[][] art=billboard();
        f.scene.sunStrength=0;P.lamp(f.scene,0,3,true,4);f.scene.lightCount=f.scene.lampShadowCount=1;
        float[][] blocker=N.horizontal(1.7f,.5f);for(float[] p:blocker)p[0]+=.87f;
        Object caster=N.mesh(f.scene,1,blocker);
        for(int i=0;i<12;i++)draw(f,art,false);
        Path pinned=output.resolve("lamp071-"+P.pack+".rgba8");
        if(baseline)Files.write(pinned,P.actual);
        else lampReference(f);
        System.out.println("Lamp-only "+(baseline?"071 reference saved":"continuous crown selected-light reference PASS")+" darkPixels="+dark(P.actual));cases++;
        N.retire(caster);for(Object o:plants)N.retire(o);
    }
    /** Continuous103 receivers intentionally replace interior card positions.
     * Evaluate the selected lamp functions directly at each actual surface;
     * the historical071 bytes above remain an immutable interior-card reference. */
    static void lampReference(Frame f)throws Exception {
        float[] positions=NativeReceivers071Probe.texture(surfaces.texture());
        List<float[]> points=new ArrayList<>();List<Integer> pixels=new ArrayList<>();
        // This fixture publishes one light-grid chunk (map0..7). Restrict the
        // independent constant-grid oracle to that chunk's actual coverage.
        for(int i=0;i<N.W*N.H;i++)if(Math.abs(Math.abs(positions[i*4+3])-5)<.01 && (P.before[i*4]&255)>170
                && positions[i*4]<-.01 && positions[i*4+2]<-.01) {
            points.add(Arrays.copyOfRange(positions,i*4,i*4+3));pixels.add(i);
        }
        byte[] actual=P.actual.clone();GlProgram oracle=P.oracle();
        int vao=GL30.glGenVertexArrays(),vbo=GL15.glGenBuffers();
        try(NativeLighting.Scope scope=P.ready(f.scene)) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0,0,N.W,N.H);P.raster();GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            P.vertices(points.toArray(float[][]::new),vao,vbo);oracle.use();scope.uniforms(oracle,0,0);NativeLampShadows.uniforms(oracle);
            oracle.set("projection",NativeSurfaces.projection(N.receiver));oracle.set("knownOutdoors",1f);
            oracle.set("knownSkyShare",1f);oracle.set("knownGrid",180f/255,180f/255,180f/255);
            oracle.set("oracleOwnerOutdoors",1f);oracle.setInt("oracleGlobalReference",0);
            oracle.set("knownSun",0f);oracle.setInt("knownLamps",1);
            GL11.glPointSize(1);GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_POINTS,0,points.size());
            byte[] expected=P.copy(N.read());int wrong=0;
            for(int i:pixels)for(int c=0;c<3;c++)if(Math.abs((actual[i*4+c]&255)-(expected[i*4+c]&255))>3){wrong++;break;}
            N.require(pixels.size()>100 && wrong==0,"Continuous crown lamp reference mismatch="+wrong+" samples="+pixels.size());
        } finally {oracle.delete();GL30.glDeleteVertexArrays(vao);GL15.glDeleteBuffers(vbo);}
    }
}
