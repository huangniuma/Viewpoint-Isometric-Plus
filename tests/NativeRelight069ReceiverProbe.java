import java.nio.*;
import java.util.*;
import java.util.regex.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.SceneData;
import vpiso.*;

/** Adversarial native receiver consumers; selected VP resources and actual VP maps, no live map. */
public final class NativeRelight069ReceiverProbe {
    static final NativeSurfaceReceiverProbe N=new NativeSurfaceReceiverProbe();
    static final float[] ART={.7f,.55f,.4f,.8f};
    static final IsometricEffects ON=new IsometricEffects(true,1f,1f,64,1024,false,0,1,1,1);
    static final IsometricEffects OFF=new IsometricEffects(false,1f,1f,64,1024,false,0,1,1,1);
    static boolean baseline,legacyOracle;
    static int metadata,nativeProgram,oracle,oracleVao,oracleVbo;
    static int cases,oldFailures,oracleDark;
    static GlProgram expected;
    static byte[] before,actual;
    static float[] depthBefore;
    static String pack;
    static final String NATIVE="""
        #version 330
        layout(location=0) out vec4 color;
        layout(location=6) out vec4 metadata;
        layout(location=7) out vec4 ground;
        uniform vec4 owner;
        uniform float depthBias,groundShare;
        void main(){color=vec4(vec3(.7,.55,.4)*.35,1);ground=vec4(color.rgb*groundShare,1);
          metadata=owner;gl_FragDepth=floor((gl_FragCoord.z+depthBias)*65535.0)/65535.0;}
        """;
    static final String ORACLE_VERTEX="""
        #version 330
        layout(location=0) in vec3 pos;
        uniform mat4 projection;
        out vec3 physical;
        void main(){physical=pos;gl_Position=projection*vec4(pos,1.0);}
        """;

    static boolean relightExpected=true;
    public static void main(String[] args)throws Exception {
        pack=args.length==0?"default":args[0];
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"native069 end-to-end relight",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);nativeProgram=NativeGroundProbe.link(N.VERT,NATIVE);
            metadata=texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,metadata,0);
            N.overlay.metadata(metadata);N.receiver.worldZ=0;
            int base=texture(GL30.GL_RGBA16F,N.W,N.H),valid=texture(GL30.GL_R16F,N.W,N.H);
            float[] pixels=new float[N.W*N.H*4];for(int k=0;k<N.W*N.H;k++){pixels[k*4]=.7f;pixels[k*4+1]=.55f;pixels[k*4+2]=.4f;pixels[k*4+3]=1;}
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,base);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,N.W,N.H,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            Arrays.fill(pixels,1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,valid);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,N.W,N.H,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            NativeRelightMask mask=new NativeRelightMask();mask.x=mask.y=-32;mask.z=0;Arrays.fill(mask.pixels,(byte)255);
            N.overlay.relight(base,valid,mask);oracleVao=GL30.glGenVertexArrays();oracleVbo=GL15.glGenBuffers();expected=oracle();
            Frame f=frame();float[][] floor=N.horizontal(0,2.3f);Object page=N.mesh(f.scene,0,N.horizontal(-5,.05f));publish(page,true,0);
            Object canopy=N.mesh(f.scene,1,blocker());
            draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);
            compare("full floor light reconstruction",reference(f,floor,true,1,0,true),3,false);
            byte[] first=actual.clone();
            // Not currently visible: keep native dimming and old ratio, never recover hidden albedo.
            Arrays.fill(mask.pixels,(byte)0);relightExpected=false;expected.delete();expected=oracle();
            draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);
            compare("native sight-edge fallback",reference(f,floor,true,1,0,true),3,false);
            N.require(different(first,actual,3)>20,"Relight vs old ratio countercontrol absent");
            draw(f,floor,.0007f,new float[]{0,0,0,1},1,false);identity("all effects disabled");
            // Same visible albedo in a closed room with no owner lamp must stay native.
            publish(page,false,60);Arrays.fill(f.scene.exposure,(byte)0);Arrays.fill(mask.pixels,(byte)255);
            draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);identity("closed room without its own lamp");
            N.retire(canopy);N.retire(page);
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println("Native relight069 end-to-end PASS pack="+pack+"; actual VP maps, real production compositor, prelit native input vs independent albedo*selected-shade target, sight fallback, closed-room no-lamp identity, all-effects-off identity, native alpha/depth/stencil unchanged. Controlled GPU fixture, no live map.");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }

    static Frame frame() {
        Frame f=N.frame();SceneData s=f.scene;s.originX=s.originZ=-3;
        s.skyR=.35f;s.skyG=.4f;s.skyB=.45f;s.sunR=1;s.sunG=.92f;s.sunB=.8f;
        s.fogR=.6f;s.fogG=.7f;s.fogB=.8f;s.zenithR=.4f;s.zenithG=.55f;s.zenithB=.8f;
        s.sunTrueX=s.skySunX=s.sunX;s.sunTrueY=s.skySunY=s.sunY;
        s.sunSine=.8f;s.dayFraction=.5f;s.eyeSky=1;s.eyeExpected=1;s.adapted=1;
        s.airSkyLevel[0]=s.skyR;s.airSkyLevel[1]=s.skyG;s.airSkyLevel[2]=s.skyB;
        s.skyBaseZenith[0]=.4f;s.skyBaseZenith[1]=.55f;s.skyBaseZenith[2]=.8f;
        s.skyBaseHorizon[0]=.6f;s.skyBaseHorizon[1]=.7f;s.skyBaseHorizon[2]=.8f;
        return f;
    }

    static void canopy()throws Exception {
        Frame f=frame();float[][] floor=N.horizontal(0,2.3f);
        Object page=N.mesh(f.scene,0,N.horizontal(-5,.05f));publish(page,true,0);
        Object canopy=N.mesh(f.scene,1,blocker());
        for(float height:new float[]{0,.25f,.7f,1.4f}) {
            Object falseReceiver=null;
            if(height>0) {
                // A differently shaped VP foreground is eligible at the same native floor pixels.
                falseReceiver=N.mesh(f.scene,2,N.horizontal(height,1.25f));
            }
            for(float bias:new float[]{.0002f,.0007f}) {
                draw(f,floor,bias,new float[]{0,0,0,1},1,true);
                compare("canopy floor, VP receiver height="+height+" bias="+bias,reference(f,floor,true,1,0,true),3,true);
            }
            if(falseReceiver!=null){N.retire(falseReceiver);f.scene.meshCount=2;}
        }
        draw(f,floor,.0007f,new float[]{0,0,0,1},1,false);identity("effect off");
        N.retire(canopy);f.scene.meshCount=1;
        draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);identity("removed canopy");
        N.retire(page);
    }

    static void variance()throws Exception {
        Frame f=frame();float[][] floor=N.horizontal(0,2.3f);
        Object page=N.mesh(f.scene,0,floor);publish(page,true,0);Object canopy=N.mesh(f.scene,1,blocker());
        // Equal native contributions from levels0 and2: mean1 is integral but variance1.
        draw(f,floor,.0007f,new float[]{0,1,2,1},1,true);
        discriminate("mixed floor levels reject the fictitious mean floor",different(before,actual,2)==0,true);
        N.retire(canopy);N.retire(page);
    }

    static void indoor()throws Exception {
        Frame f=frame();float[][] floor=N.horizontal(0,2.3f);
        Object page=N.mesh(f.scene,0,floor);publish(page,false,60);Arrays.fill(f.scene.exposure,(byte)0);
        Object canopy=N.mesh(f.scene,1,blocker());
        // A known indoor receiver still has a nonzero native floor RGB contribution.
        draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);
        discriminate("indoor negative receiver with nonzero floor RGB",different(before,actual,2)==0,true);
        compare("indoor selected lighting reference",reference(f,floor,false,1-60f/240f,0,true),3,true);
        N.retire(canopy);N.retire(page);
    }

    static void lamps()throws Exception {
        Frame f=frame();f.scene.sunStrength=0;float[][] floor=N.horizontal(0,2.3f);
        Object page=N.mesh(f.scene,0,floor);publish(page,false,60);Arrays.fill(f.scene.exposure,(byte)0);
        Object canopy=N.mesh(f.scene,1,blocker());
        float[][] outer=N.horizontal(2.8f,.9f);for(float[] p:outer)p[0]+=1.65f;
        Object outsideWall=N.mesh(f.scene,2,outer);
        lamp(f.scene,0,-3,false,.55f);f.scene.lightCount=f.scene.lampShadowCount=1;
        settle(f,floor);byte[] indoor=actual.clone();
        byte[] refOne=reference(f,floor,false,.75f,1,true);
        compare("indoor lamp with skyShare=.75 but outdoors=0",refOne,4,true);
        lamp(f.scene,1,3,true,4);f.scene.lightKeys[1]=456;f.scene.lightCount=f.scene.lampShadowCount=2;
        settle(f,floor);
        byte[] correct=reference(f,floor,false,.75f,2,true);
        compare("two lamps: exterior lamp excluded by native indoor owner",correct,4,true);
        legacyOracle=true;byte[] imaginary=reference(f,floor,false,.75f,2,true);legacyOracle=false;
        int domainDifference=different(correct,imaginary,2);
        System.out.println("Cross-domain denominator oracle negative-control pixels="+domainDifference);
        if(domainDifference==0)for(String name:new String[]{"uLightCount","uLightRoom[1]","uLightOutdoors[1]","uLightPos[1]","uLightColor[1]","uLampShadowCount","uLampShadowSlot[1]","oracleOwnerOutdoors","oracleGlobalReference"}) {
            int at=GL20.glGetUniformLocation(expected.id(),name);float[] value=new float[4];if(at>=0)GL20.glGetUniformfv(expected.id(),at,value);System.out.println("Oracle "+name+"="+Arrays.toString(value));
        }
        N.require(domainDifference>20,"Two-lamp fixture cannot discriminate imaginary exterior light");
        int reduced=0;for(int i=0;i<N.W*N.H;i++)for(int c=0;c<3;c++)
            if((actual[i*4+c]&255)+3<(indoor[i*4+c]&255)){reduced++;break;}
        System.out.println("Indoor two-lamp external toggle darkened pixels="+reduced);
        discriminate("outside room lamp does not darken independent indoor illumination",reduced==0,true);
        f.scene.lightCount=f.scene.lampShadowCount=0;
        draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);identity("extinguished lamps");
        N.retire(outsideWall);N.retire(canopy);N.retire(page);
    }

    static float[][] blocker() {
        float[][] p=N.horizontal(3.4f,.65f);for(float[] v:p)v[0]-=1.65f;return p;
    }
    static void settle(Frame f,float[][] floor)throws Exception {
        for(int i=0;i<12;i++)draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);
    }
    static void publish(Object mesh,boolean outdoors,int darker) {
        ByteBuffer b=(ByteBuffer)GameAccess.call("viewpoint.render.ChunkMeshData","spareLight",mesh,new Class<?>[0]);b.clear();
        for(int i=0;i<800;i++)b.put((byte)(i<400?180:darker)).put((byte)180).put((byte)180).put((byte)(outdoors?255:0));
        b.flip();GameAccess.call("viewpoint.render.ChunkMeshData","publishLight",mesh,new Class<?>[]{ByteBuffer.class},b);
    }
    static void lamp(SceneData s,int slot,float x,boolean outdoors,float strength) {
        int a=slot*20;Arrays.fill(s.lights,a,a+20,0);s.lightKeys[slot]=-123-slot;
        float[] l=s.lights;l[a]=l[a+16]=x;l[a+1]=l[a+17]=5;l[a+2]=l[a+18]=0;l[a+3]=l[a+19]=12;
        l[a+4]=l[a+5]=l[a+6]=1;l[a+7]=strength;l[a+8]=outdoors?1:0;l[a+9]=1;l[a+10]=5;
    }

    static void draw(Frame f,float[][] points,float bias,float[] owner,float floorShare,boolean enabled)throws Exception {
        f.number=++N.number;f.scene.cameraCut=true;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glViewport(0,0,N.W,N.H);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,0,0,0,GL30.GL_COLOR_ATTACHMENT6,GL30.GL_COLOR_ATTACHMENT7});
        raster();GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);
        GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClearDepth(1);GL11.glStencilMask(255);GL11.glClearStencil(31);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GL30.glClearBufferfv(GL11.GL_COLOR,6,new float[]{0,0,0,0});GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{0,0,0,0});
        vertices(points,N.vao,N.vbo);GL20.glUseProgram(nativeProgram);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(nativeProgram,"projection"),false,NativeSurfaces.projection(N.receiver).get(new float[16]));
        GL20.glUniform1f(GL20.glGetUniformLocation(nativeProgram,"depthBias"),bias);
        GL20.glUniform1f(GL20.glGetUniformLocation(nativeProgram,"groundShare"),floorShare);
        GL20.glUniform4fv(GL20.glGetUniformLocation(nativeProgram,"owner"),NativeMetadata074.convert(owner));GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);
        before=copy(N.read());depthBefore=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthBefore);
        N.overlay.ground(N.ground);N.overlay.capture(true);IsometricBridge.rendering(f);
        boolean produced;
        try{produced=NativeShadowMaps.draw(f.scene,1024,2,N.overlay.surfaces(N.receiver));}
        finally{IsometricBridge.rendering(null);}
        if(!produced && f.scene.sunStrength>.02f)throw new AssertionError("No actual shadow maps");
        int count=NativeShadowMaps.count();int map=count>0?NativeShadowMaps.texture():0;
        Matrix4f[] m={new Matrix4f(),new Matrix4f(),new Matrix4f()};float[] t=new float[3];
        for(int i=0;i<count;i++){m[i].set(NativeShadowMaps.matrix(i));t[i]=2*NativeShadowMaps.reach(i)/1024;}
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
        N.overlay.draw(N.receiver,enabled?ON:OFF,map,m,t,produced?NativeShadowMaps.depthRange():1,1024,count,f.scene.sunStrength);
        actual=copy(N.read());preserved();N.require(GL11.glGetError()==0,"GL error draw frame="+N.number);
    }

    /** Independent physical positions and direct selected shade/sun calls; no vpIsoLightingRatio or native decoder. */
    static byte[] reference(Frame f,float[][] points,boolean outdoors,float skyShare,int lamps,boolean enabled)throws Exception {
        try(NativeLighting.Scope scope=ready(f.scene)) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
            raster();GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            vertices(points,oracleVao,oracleVbo);expected.use();
            scope.uniforms(expected,NativeShadowMaps.count()>0?NativeShadowMaps.texture():0,NativeShadowMaps.count());
            NativeLampShadows.uniforms(expected);
            expected.set("projection",NativeSurfaces.projection(N.receiver));expected.set("knownOutdoors",outdoors?1f:0f);
            expected.set("knownSkyShare",skyShare);expected.set("knownGrid",180f/255,180f/255,180f/255);
            expected.set("oracleOwnerOutdoors",outdoors?1f:0f);
            expected.setInt("oracleGlobalReference",legacyOracle?1:0);
            expected.set("knownSun",enabled?f.scene.sunStrength:0f);expected.setInt("knownLamps",enabled?lamps:0);
            GL30.glBindVertexArray(oracleVao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);
            byte[] result=copy(N.read());int dark=0;for(int i=0;i<N.W*N.H;i++)if((result[i*4]&255)+3<(before[i*4]&255))dark++;
            oracleDark+=dark;return result;
        }
    }
    static NativeLighting.Scope ready(SceneData scene)throws Exception {
        long until=System.nanoTime()+60_000_000_000L;
        while(true){NativeLighting.Scope s=NativeLighting.open(scene,N.receiver);if(s.ready())return s;s.close();
            if(System.nanoTime()>until)throw new AssertionError("Selected pack lighting resources not ready: "+pack);Thread.sleep(10);}
    }

    static GlProgram oracle() {
        String shade=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,
                new Class<?>[]{String.class,ShaderPack.class,boolean[].class},"lib/shade.glsl",ShaderPacks.active().pack,new boolean[1]);
        Matcher m=Pattern.compile("\\bvec3\\s+shadeShared\\s*\\(([^)]*)\\)").matcher(shade);
        int args=m.find()?m.group(1).split(",").length:0;
        String selected=args==7?"shadeShared(vec3(1),light,knownSkyShare,n,lit,vec4(n,1),vec3(0))"
                :args==6?"shadeShared(vec3(1),light,knownSkyShare,n,lit,vec4(n,1))":"shade(vec3(1),light,n,lit)";
        String lamp=args==0?"light.rgb=lampShadowed(light.rgb,physical,n,knownOutdoors);"
                +"light.rgb=max(light.rgb,min(lamps(physical,n),light.rgb*1.35+0.02));"
                :"vec3 sky=skyPartOf(light.rgb,knownSkyShare);vec2 parts;"
                +"vec3 direct=lampsShadowed(physical,n,light.rgb-sky,knownOutdoors,parts);"
                +"light.rgb=sky+(light.rgb-sky)*(1.0-parts.x*(1.0-parts.y));"
                +"light.rgb=max(light.rgb,min(direct,light.rgb*1.35+0.02));";
        String main="""
            #version 330
            in vec3 physical;
            out vec4 outputColor;
            uniform float knownOutdoors,knownSkyShare,knownSun;
            uniform vec3 knownGrid;
            uniform int knownLamps;
            vec3 selected(float lit,float mapped) {
              vec3 n=vec3(0,1,0);vec4 light=vec4(knownGrid,knownOutdoors);
              vpIsoLampEvaluation=mapped;
              if(knownLamps>0) { VP_LAMP }
              return VP_SHADE;
            }
            void main(){
              vec3 n=vec3(0,1,0);
              float clear=knownOutdoors>.5 && knownSun>0.0?smoothstep(0.0,.3,dot(n,uSunDir))*cloudShadow(physical):0.0;
              float shadowed=knownOutdoors>.5 && knownSun>0.0?sunlit(physical,n,vec3(0,10000,0),false):0.0;
              vec3 lit=selected(clear,0.0),dim=selected(shadowed,1.0);
              vec3 ratio=vec3(1);for(int c=0;c<3;c++)if(lit[c]>1e-6)ratio[c]=max(dim[c]/lit[c],0.0);
              outputColor=vec4(vec3(.7,.55,.4)*RECEIVER_EXPECTED,1);
            }
            """.replace("VP_LAMP",lamp).replace("VP_SHADE",selected).replace("RECEIVER_EXPECTED",relightExpected?"dim":".35*ratio");
        // Only resource expansion/uniform setup is shared; the oracle directly evaluates
        // selected functions at interpolated physical vertices, never production decoding/ratio.
        String fragment=NativeLighting.fragment(main);
        // Independent denominator policy: an outside lamp already blocked by a wall
        // does not become imaginary unoccluded energy in an indoor owner's reference.
        // Keep the real selected mapped visibility for the other room domain in both
        // evaluations. Do not call the production wrapper/owner selector here.
        String wrapper="float lampVisibility(int i,vec3 pos,vec3 n) {";
        N.require(fragment.indexOf(wrapper)>=0,"Selected lamp visibility wrapper absent");
        fragment=fragment.replace(wrapper,"float vpIsoUnusedProductionVisibility(int i,vec3 pos,vec3 n) {");
        fragment=fragment.replace("#define VPISO_NATIVE_LIGHTING 1","#define VPISO_NATIVE_LIGHTING 1\nuniform float oracleOwnerOutdoors;uniform int oracleGlobalReference;");
        String own="""
            float lampVisibility(int i,vec3 pos,vec3 n) {
              float mapped=vpIsoMappedLampVisibility(i,pos,n);
              bool sameRoomDomain=(uLightOutdoors[i]>.5)==(oracleOwnerOutdoors>.5);
              return sameRoomDomain || oracleGlobalReference!=0?mix(1.0,mapped,vpIsoLampEvaluation):mapped;
            }
            """;
        int entry=fragment.lastIndexOf("void main()");fragment=fragment.substring(0,entry)+own+fragment.substring(entry);
        return GlProgram.fromSources("068 selected physical lighting reference",new GlProgram.Sources(ORACLE_VERTEX,null,null,null,fragment),Map.of(),Map.of(),List.of("068 oracle"));
    }

    static void compare(String label,byte[] ref,int tolerance,boolean negative) {
        int count=different(ref,actual,tolerance);cases++;
        if(label.startsWith("canopy")) {
            int dark=0;for(int i=0;i<N.W*N.H;i++)if((ref[i*4]&255)+3<(before[i*4]&255))dark++;
            N.require(dark>20,"Selected physical canopy reference is empty: "+label);
        }
        if(count>0 && !baseline) {
            for(int i=0;i<N.W*N.H;i++)if(Math.abs((ref[i*4]&255)-(actual[i*4]&255))>tolerance) {
                System.out.println("Mismatch "+label+" pixel="+(i%N.W)+","+(i/N.W)+" before="+Arrays.toString(Arrays.copyOfRange(before,i*4,i*4+4))+" actual="+Arrays.toString(Arrays.copyOfRange(actual,i*4,i*4+4))+" expected="+Arrays.toString(Arrays.copyOfRange(ref,i*4,i*4+4)));break;
            }
        }
        if(baseline && negative){if(count>8)oldFailures++;System.out.println("067 discriminator "+label+" mismatches="+count);}
        else N.require(count==0,label+" selected-reference mismatches="+count);
    }
    static void discriminate(String label,boolean holds,boolean negative) {
        cases++;if(baseline && negative){if(!holds)oldFailures++;System.out.println("067 discriminator "+label+" reproduced="+!holds);}
        else N.require(holds,label);
    }
    static void identity(String label){cases++;N.require(different(before,actual,1)==0,label+" modified native image");}
    static int different(byte[] a,byte[] b,int tolerance) {
        int n=0;for(int i=0;i<N.W*N.H;i++)for(int c=0;c<3;c++)if(Math.abs((a[i*4+c]&255)-(b[i*4+c]&255))>tolerance){n++;break;}return n;
    }
    static void preserved() {
        for(int i=0;i<N.W*N.H;i++)N.require(before[i*4+3]==actual[i*4+3],"native alpha changed");
        float[] after=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,after);
        N.require(Arrays.equals(depthBefore,after),"native depth changed");
        ByteBuffer s=BufferUtils.createByteBuffer(N.W*N.H);GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,s);
        for(int i=0;i<N.W*N.H;i++)N.require(s.get(i)==31,"native stencil changed");
    }
    static void vertices(float[][] points,int vao,int vbo) {
        FloatBuffer b=BufferUtils.createFloatBuffer(points.length*3);for(float[] p:points)b.put(p);b.flip();
        GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,b,GL15.GL_STREAM_DRAW);
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0L);
    }
    static void raster(){GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glColorMask(true,true,true,true);}
    static byte[] copy(ByteBuffer b){byte[] x=new byte[b.capacity()];for(int i=0;i<x.length;i++)x[i]=b.get(i);return x;}
    static int texture(int internal,int w,int h){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
}
