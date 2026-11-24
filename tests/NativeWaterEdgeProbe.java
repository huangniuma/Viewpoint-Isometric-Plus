import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.WorldRenderer;
import vpiso.*;

/** Real native water producers, projection, vertex shader and VBO consumer at a coplanar bank. */
public final class NativeWaterEdgeProbe {
    private static final int SIZE=192;
    private static int sourceSize=SIZE;
    private static final Class<?>[] NONE={};
    private static Object water,renderer,states,queue,bodyData,shoreData;
    private static int sourceFbo,sourceDepth,outputFbo,groundProgram,waterProgram,vao;
    private static boolean actualModels,baseline;
    private static Frame modelFrame;
    private static ChunkMeshData bankMesh,opaqueMesh;
    private static NativeVPWaterOverlay overlay;
    private static NativeVPWaterOverlay.Snapshot overlayWater;
    private static final NativeReceiver overlayReceiver=new NativeReceiver();
    private static String pack="library";
    private static Object previousClock;
    private static boolean pullCheck, pullBaseline, omitBank;
    private static boolean offsetCheck;
    private static boolean offsetBaseline;
    private static float logicalOffset;
    private static float fixtureZoom=1f;
    private static int floorFlags=4;
    private static final String GROUND="""
        #version 330
        in vec2 uv;
        uniform mat4 inverseCamera, camera;
        uniform float elevation;
        out vec4 color;
        void main() {
          vec4 a=inverseCamera*vec4(uv*2.0-1.0,-1,1), b=inverseCamera*vec4(uv*2.0-1.0,1,1);
          a/=a.w; b/=b.w;
          vec3 p=mix(a.xyz,b.xyz,(elevation-a.y)/(b.y-a.y));
          vec2 world=vec2(1.5)-p.xz;
          vec4 q=camera*vec4(p,1);
          // VP water material is suppressed: the centre has no terrain depth.
          gl_FragDepth=(elevation==0 && all(greaterThan(world,vec2(1))) && all(lessThan(world,vec2(2))))
              ? 1.0 : (q.z/q.w+1.0)*0.5;
          color=vec4(0);
        }
        """;
    public static void main(String[] args) throws Exception {
        actualModels=args.length>0; baseline=actualModels && args[0].equals("baseline038");
        offsetBaseline=actualModels && args[0].equals("baseline040-offset");
        offsetCheck=actualModels && (args[0].equals("offset041") || offsetBaseline);
        pullCheck=actualModels && (args[0].equals("pull040") || args[0].equals("baseline039-pull") || offsetCheck);
        pullBaseline=pullCheck && args[0].equals("baseline039-pull");
        if(args.length>1)pack=args[1];
        Field unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Object unsafe=unsafeField.get(null);
        set(NativeWaterProbe.class,"unsafe",null,unsafe);
        set(NativeWaterProbe.class,"allocate",null,unsafe.getClass().getMethod("allocateInstance",Class.class));
        Method fixture=NativeWaterProbe.class.getDeclaredMethod("fixture"); fixture.setAccessible(true); fixture.invoke(null);
        for(String name:new String[]{"water","renderer","states","queue","bodyData","shoreData"}) {
            Field f=NativeWaterProbe.class.getDeclaredField(name); f.setAccessible(true);
            NativeWaterEdgeProbe.class.getDeclaredField(name).set(null,f.get(null));
        }
        Method square=NativeWaterProbe.class.getDeclaredMethod("square",int.class,int.class,boolean.class,boolean.class); square.setAccessible(true);
        Object chunk=get(NativeWaterProbe.class,"chunk",null);
        Object[] squares=(Object[])((Object[])get(chunk.getClass(),"squares",chunk))[0]; Arrays.fill(squares,null);
        for(int y=0;y<3;y++) for(int x=0;x<3;x++) {
            boolean bank=x!=1||y!=1;
            Object s=square.invoke(null,x,y,bank,!bank),g=GameAccess.call("zombie.iso.IsoGridSquare","getWater",s,NONE);
            float[] gx=new float[4],gy=new float[4];
            int[][] corners={{x,y},{x,y+1},{x+1,y+1},{x+1,y}};
            for(int i=0;i<4;i++) for(String axis:new String[]{"X","Y"}) {
                float v=((Number)GameAccess.call("zombie.iso.IsoUtils",axis+"ToScreen",null,
                    new Class<?>[]{float.class,float.class,float.class,int.class},(float)corners[i][0],(float)corners[i][1],0f,0)).floatValue();
                (axis.equals("X")?gx:gy)[i]=v;
            }
            GameAccess.set("zombie.iso.IsoWaterGeometry","x",g,gx); GameAccess.set("zombie.iso.IsoWaterGeometry","y",g,gy);
            squares[x+y*8]=s;
        }
        Object mainCamera=((Object[])GameAccess.field("zombie.iso.IsoCamera","cameras",null))[0];
        for(String name:new String[]{"offX","offY","rightClickX","rightClickY"}) GameAccess.set("zombie.iso.PlayerCamera",name,mainCamera,0f);
        if(offsetCheck) {
            GameAccess.set("zombie.iso.PlayerCamera","offX",mainCamera,-115000f);
            GameAccess.set("zombie.iso.PlayerCamera","offY",mainCamera,632000f);
        }
        GameAccess.set("zombie.iso.PlayerCamera","zoom",mainCamera,1f);
        if(!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(SIZE,SIZE,"native shoreline temporal regression",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            Class.forName("zombie.core.VBO.GLVertexBufferObject").getMethod("init").invoke(null);
            GameAccess.call("zombie.core.opengl.SharedVertexBufferObjects","startFrame",GameAccess.field("zombie.iso.IsoPuddles","VBOs",null),NONE);
            set(states.getClass(),"rendering",states,queue);
            Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
            Object queuedCamera=GameAccess.call("zombie.core.SpriteRenderer","getRenderingPlayerCamera",sprites,new Class<?>[]{int.class},0);
            for(String name:new String[]{"offX","offY","rightClickX","rightClickY"}) GameAccess.set("zombie.iso.PlayerCamera",name,queuedCamera,0f);
            if(offsetCheck) {
                GameAccess.set("zombie.iso.PlayerCamera","offX",queuedCamera,-115000f);
                GameAccess.set("zombie.iso.PlayerCamera","offY",queuedCamera,632000f);
            }
            GameAccess.set("zombie.iso.PlayerCamera","zoom",queuedCamera,1f);
            GameAccess.set("zombie.iso.PlayerCamera","offscreenWidth",queuedCamera,SIZE);
            GameAccess.set("zombie.iso.PlayerCamera","offscreenHeight",queuedCamera,SIZE);
            vao=GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            groundProgram=program(NativeVision.VERTEX,GROUND);
            String vertex=Files.readString(Path.of("D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid/media/shaders/water.vert"));
            waterProgram=program(vertex,"#version 330\nin float vDepth;out vec4 color;void main(){gl_FragDepth=vDepth;color=vec4(0,0,1,1);}");
            sourceFbo=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,sourceFbo);
            sourceDepth=texture(GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL11.GL_TEXTURE_2D,sourceDepth,0);
            GL11.glDrawBuffer(GL11.GL_NONE); GL11.glReadBuffer(GL11.GL_NONE);
            outputFbo=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE),0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,texture(offsetCheck?GL14.GL_DEPTH_COMPONENT24:GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT),0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("FBO");
            GL11.glViewport(0,0,SIZE,SIZE); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GameAccess.set("viewpoint.render.WorldRenderer","targets",null,Class.forName("viewpoint.render.Targets").getConstructor().newInstance());
            if(actualModels) {
                Main.compatible=true;
                GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
                boolean fsr=Boolean.getBoolean("water096.fsr");
                ShaderPacks.useBuiltIn(pack,"high",Map.of("upscaling",fsr?"fsr3":"off","renderScale",fsr?"50":"100","antialiasing","taa"));
                if(!fsr&&!ShaderPacks.active().pipeline.taa) throw new AssertionError("Fixture must exercise real TAA");
                modelFrame=new Frame(); modelFrame.camX=modelFrame.camY=1.5f;
                previousClock=GameAccess.field("viewpoint.render.WorldRenderer","clock",null);
                GameAccess.set("viewpoint.render.WorldRenderer","clock",null,(java.util.function.LongSupplier)()->1_000_000_000L+modelFrame.number*16_000_000L);
                modelFrame.scene.originX=modelFrame.scene.originZ=-1.5;
                modelFrame.scene.meshCount=1; modelFrame.scene.meshFlags[0]=1;
                modelFrame.scene.meshPlan[0]=-1; modelFrame.scene.meshModelSquares[0]=-1L;
                modelFrame.scene.meshFade[1]=2; modelFrame.scene.skyR=modelFrame.scene.skyG=modelFrame.scene.skyB=1;
                bankMesh=bankMesh(false); opaqueMesh=bankMesh(true);
                if(args.length>1) {
                    overlay=new NativeVPWaterOverlay();overlayWater=new NativeVPWaterOverlay.Snapshot();
                    float[] rules=new float[36],depths=new float[36];
                    for(int y=0;y<3;y++)for(int x=0;x<3;x++) {
                        int i=x+y*3;boolean shore=x!=1||y!=1;
                        rules[i*4]=rules[i*4+3]=1;rules[i*4+1]=shore?1:0;rules[i*4+2]=shore?0:16;
                        Object s=squares[x+y*8],g=GameAccess.call("zombie.iso.IsoGridSquare","getWater",s,NONE);
                        System.arraycopy((float[])GameAccess.field("zombie.iso.IsoWaterGeometry","depth",g),0,depths,i*4,4);
                    }
                    overlayWater.fit(0,0,3,3,1.5f,1.5f,0,rules);overlayWater.cornerDepth(depths);
                    overlayWater.material(NativeVPWaterProbe.materialPage(),new float[]{0,0,1,1,0,0,1f/64,1f/128},new float[]{1,1,1});
                    modelFrame.scene.sunTrueY=modelFrame.scene.sunY=modelFrame.scene.skySunY=1;
                    modelFrame.scene.sunR=modelFrame.scene.sunG=modelFrame.scene.sunB=1;modelFrame.scene.sunStrength=1;
                    GameAccess.call("viewpoint.environment.Atmosphere","skyColours",null,new Class<?>[]{viewpoint.render.SceneData.class,float.class,float.class,float.class},modelFrame.scene,1f,0f,0f);
                    GameAccess.call("viewpoint.environment.Atmosphere","airLight",null,new Class<?>[]{viewpoint.render.SceneData.class},modelFrame.scene);
                }
            }
            if(pullCheck) { checkFloorPull(); return; }
            int cases=0;
            int worst=0;
            for(int resolution:new int[]{192,128,256}) for(float yaw:new float[]{4.8112965f,17.25f,90f,123.75f,225f,270f,315.25f}) {
                sourceSize=resolution; GL11.glBindTexture(GL11.GL_TEXTURE_2D,sourceDepth);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH24_STENCIL8,sourceSize,sourceSize,0,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8,0L);
                Matrix4f p=new Matrix4f(),v=new Matrix4f();
                var fit=IsoCameraMath.fit(1.5,1.5,SIZE*.5,SIZE*.5,32,16,-32,16,SIZE,SIZE);
                IsoCameraMath.matrices(fit,1.5f,1.5f,SIZE,SIZE,p,v,new float[3],yaw);
                var camera=new RotationCamera.Snapshot(p,v,1.5f,1.5f,0f,SIZE,SIZE,1f,yaw);
                NativeWater.queue(renderer,0,camera);
                if((Integer)GameAccess.field("zombie.iso.IsoWater$RenderData","numSquares",shoreData)!=8) throw new AssertionError("shore producer");
                byte[] reference=render(camera,p,v,0,0,0);
                int area=area(reference); if(area<(overlay==null?5000:2000)) throw new AssertionError("reference water coverage "+area);
                int maxChanged=0,minArea=area,maxArea=area;
                for(int phase=1;phase<=16;phase++) {
                    float jx=halton(phase,2)-.5f,jy=halton(phase,3)-.5f;
                    byte[] image=render(camera,p,v,jx,jy,0); int changed=0;
                    for(int i=2;i<image.length;i+=4) if(image[i]!=reference[i]) changed++;
                    maxChanged=Math.max(maxChanged,changed); minArea=Math.min(minArea,area(image)); maxArea=Math.max(maxArea,area(image));
                    cases++;
                }
                System.out.println("shore render="+sourceSize+" yaw="+yaw+" area="+minArea+".."+maxArea+" changed="+maxChanged);
                worst=Math.max(worst,maxChanged);
                if(actualModels && !baseline && !Boolean.getBoolean("water096.fsr") && maxChanged!=0) throw new AssertionError("Actual mesh shoreline must be identical: "+maxChanged+" changed pixels");
                if(Boolean.getBoolean("water096.fsr") && maxChanged>area*.035) throw new AssertionError("FSR shoreline coverage moved too far: "+maxChanged+"/"+area);
                if(!actualModels && maxChanged>area*.035) throw new AssertionError("Temporal shoreline coverage: "+maxChanged+"/"+area+" pixels change; area="+minArea+".."+maxArea);
                byte[] occluded=render(camera,p,v,.25f,-.3f,.5f);
                if(area(occluded)!=0) throw new AssertionError("Elevated opaque plane must still occlude water");
            }
            if(!actualModels) thinSilhouettes();
            if(GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("GL error");
            if(baseline && worst==0) throw new AssertionError("Pinned038 did not reproduce moving shoreline");
            System.out.println((actualModels?(baseline?"Actual model shoreline negative control PASS: pinned038":"Actual model shoreline GPU PASS:"):"Native shoreline analytical GPU PASS:")+" "+cases+" frames; maxChanged="+worst+"; water="+(overlay==null?"native":"VP/"+pack)+"; real native producers/waterProjection/water.vert/waterGeometry; "+(actualModels?"actual VP TemporalPass/mesh/GBuffer/pulled depth":"synthetic jittered plane depth (residual motion allowed)")+"; elevated occluder retained; fixed camera, 7 yaw angles, 3 render resolutions, 16 temporal phases. Controlled pond fixture, no live map.");
        } finally { if(previousClock!=null)GameAccess.set("viewpoint.render.WorldRenderer","clock",null,previousClock); GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static byte[] render(RotationCamera.Snapshot snapshot,Matrix4f p,Matrix4f v,float jx,float jy,float elevation) {
        if(actualModels) return renderModels(snapshot,p,v,elevation);
        float[] elements=p.get(new float[16]); elements[8]+=jx*2/sourceSize; elements[9]+=jy*2/sourceSize;
        Matrix4f jittered=new Matrix4f().set(elements);
        Matrix4f combined=new Matrix4f(jittered).mul(v);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,sourceFbo); GL11.glViewport(0,0,sourceSize,sourceSize);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_ALWAYS); GL11.glDepthMask(true);
        GL11.glClearDepth(1); GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); GL20.glUseProgram(groundProgram);
        matrix(groundProgram,"camera",combined); matrix(groundProgram,"inverseCamera",new Matrix4f(combined).invert());
        GL20.glUniform1f(GL20.glGetUniformLocation(groundProgram,"elevation"),elevation); GL30.glBindVertexArray(vao); GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        Object context=GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        GameAccess.set("viewpoint.render.FrameContext","outputDrawFbo",context,outputFbo);
        GameAccess.set("viewpoint.render.FrameContext","width",context,sourceSize); GameAccess.set("viewpoint.render.FrameContext","height",context,sourceSize);
        GameAccess.set("viewpoint.render.FrameContext","jitterX",context,jx); GameAccess.set("viewpoint.render.FrameContext","jitterY",context,jy);
        int[] viewport=(int[])GameAccess.field("viewpoint.render.FrameContext","viewport",context); viewport[0]=viewport[1]=0; viewport[2]=viewport[3]=SIZE;
        ((Matrix4f)GameAccess.field("viewpoint.render.FrameContext","invViewProjection",context)).set(combined).invert();
        Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null); GameAccess.set("viewpoint.render.Targets","depth",targets,sourceDepth);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo); GL11.glClearColor(0,0,0,0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        NativeVision bridge=new NativeVision(); bridge.rotation=snapshot; bridge.writeDepth();
        GL11.glColorMask(true,true,true,true); GL11.glDisable(GL11.GL_BLEND); GL20.glUseProgram(waterProgram);
        GL30.glBindVertexArray(vao);
        for(int i=0;i<4;i++) GL20.glEnableVertexAttribArray(i);
        drawNativeWater();
        ByteBuffer pixels=BufferUtils.createByteBuffer(SIZE*SIZE*4); GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        byte[] result=new byte[pixels.capacity()]; pixels.get(result); return result;
    }
    private static byte[] renderModels(RotationCamera.Snapshot snapshot,Matrix4f p,Matrix4f v,float elevation) {
        modelFrame.scene.meshCount=omitBank?0:1;
        modelFrame.scene.projection.set(p); modelFrame.scene.meshes[0]=elevation>0?opaqueMesh:bankMesh;
        modelFrame.scene.meshOffsets[1]=elevation; modelFrame.number++; modelFrame.scene.cameraCut=false;
        float[] eye=new float[3]; IsoCameraMath.matrices(IsoCameraMath.fit(1.5,1.5,SIZE*.5,SIZE*.5,32,16,-32,16,SIZE,SIZE),1.5f+logicalOffset,1.5f,SIZE,SIZE,new Matrix4f(),new Matrix4f(),eye,snapshot.yawDegrees());
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo); GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0,0,sourceSize,sourceSize); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        IsometricBridge.modelWorldRendering(modelFrame,IsometricEffects.DEFAULT,true);
        var previous=RotationLayers.begin(snapshot);
        try {
            if(!WorldRenderer.begin(modelFrame.scene,modelFrame.number,1f,v,eye[0],eye[1],eye[2])) throw new AssertionError("Actual world begin");
            WorldRenderer.drawTranslucent(modelFrame.scene); WorldRenderer.finish(modelFrame.scene);
        } finally { RotationLayers.end(previous); IsometricBridge.rendering(null); }
        Object context=GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        int[] viewport=(int[])GameAccess.field("viewpoint.render.FrameContext","viewport",context); viewport[0]=viewport[1]=0;viewport[2]=viewport[3]=SIZE;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFbo);GL11.glViewport(0,0,SIZE,SIZE);
        GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        NativeVision vision=new NativeVision();vision.rotation=snapshot;vision.writeDepth();
        GL11.glColorMask(true,true,true,true);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LESS);GL11.glDepthMask(false);
        GL20.glUseProgram(waterProgram);GL30.glBindVertexArray(vao);for(int i=0;i<4;i++)GL20.glEnableVertexAttribArray(i);
        drawNativeWater();
        ByteBuffer pixels=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        byte[] result=new byte[pixels.capacity()];pixels.get(result);
        if(overlay!=null) {
            IsometricBridge.rendering(modelFrame);
            try{overlay.draw(modelFrame,overlayReceiver,overlayWater,true,-.00001f,snapshot);}finally{IsometricBridge.rendering(null);}
            pixels.clear();GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            byte[] mask=new byte[result.length];
            for(int i=0;i<result.length;i+=4)if(pixels.get(i)!=result[i]||pixels.get(i+1)!=result[i+1]||pixels.get(i+2)!=result[i+2])mask[i+2]=(byte)255;
            return mask;
        }
        return result;
    }
    private static void checkFloorPull() {
        int worstMissing=0,cases=0;
        for(float zoom:offsetCheck?new float[]{.75f,1f,1.5f}:new float[]{1f})
        for(float[] nativeOffset:offsetCheck?new float[][]{{-115000,632000},{-230480,632907},{-231200,632502},{0,0}}:new float[][]{{0,0}}) {
        fixtureZoom=zoom;
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        Object mainCamera=((Object[])GameAccess.field("zombie.iso.IsoCamera","cameras",null))[0];
        Object queuedCamera=GameAccess.call("zombie.core.SpriteRenderer","getRenderingPlayerCamera",sprites,new Class<?>[]{int.class},0);
        for(Object nativeCamera:new Object[]{mainCamera,queuedCamera}) {
            GameAccess.set("zombie.iso.PlayerCamera","offX",nativeCamera,nativeOffset[0]);
            GameAccess.set("zombie.iso.PlayerCamera","offY",nativeCamera,nativeOffset[1]);
            GameAccess.set("zombie.iso.PlayerCamera","zoom",nativeCamera,zoom);
            GameAccess.set("zombie.iso.PlayerCamera","offscreenWidth",nativeCamera,Math.round(SIZE*zoom));
            GameAccess.set("zombie.iso.PlayerCamera","offscreenHeight",nativeCamera,Math.round(SIZE*zoom));
        }
        for(int flags:new int[]{4,68}) {
        floorFlags=flags;bankMesh=bankMesh(false);opaqueMesh=bankMesh(true);
        for(float offset:new float[]{0,4,8,16,24,32,48,64}) for(float yaw:new float[]{4.8112965f,17.25f,90,123.75f,225,270,315.25f}) {
            logicalOffset=offset;sourceSize=SIZE;modelFrame.camX=1.5f+offset;
            modelFrame.scene.originX=-modelFrame.camX;modelFrame.scene.meshOffsets[0]=offset;
            var fit=IsoCameraMath.fit(1.5,1.5,SIZE*.5,SIZE*.5,32,16,-32,16,SIZE,SIZE);
            Matrix4f p=new Matrix4f(),v=new Matrix4f();
            IsoCameraMath.matrices(fit,modelFrame.camX,1.5f,SIZE,SIZE,p,v,new float[3],yaw);
            var camera=new RotationCamera.Snapshot(p,v,modelFrame.camX,1.5f,0,SIZE,SIZE,fixtureZoom,yaw);
            NativeWater.queue(renderer,0,camera);
            omitBank=true;byte[] expected=renderModels(camera,p,v,0);omitBank=false;
            byte[] actual=renderModels(camera,p,v,0);int lost=0,extra=0;
            for(int i=2;i<expected.length;i+=4){if(expected[i]!=0 && actual[i]==0)lost++;if(expected[i]==0 && actual[i]!=0)extra++;}
            worstMissing=Math.max(worstMissing,lost);cases++;
            if(!pullBaseline && !offsetBaseline && (lost>32 || extra>32))throw new AssertionError("Shore cut into flat bank at distance="+offset+" yaw="+yaw+" zoom="+zoom+" nativeOff="+Arrays.toString(nativeOffset)+" lost="+lost+" extra="+extra);
            if(area(renderModels(camera,p,v,.5f))!=0)throw new AssertionError("Elevated foreground water occlusion");
            System.out.println("Floor flags="+flags+" distance="+offset+" yaw="+yaw+" zoom="+zoom+" nativeOff="+Arrays.toString(nativeOffset)+" expected="+area(expected)+" lost="+lost+" extra="+extra);
        }
        }
        }
        if(pullBaseline && worstMissing<100)throw new AssertionError("Pinned039 did not reproduce bank intersections");
        if(offsetBaseline && worstMissing<100)throw new AssertionError("Pinned040 did not reproduce map-coordinate intersections");
        System.out.println("Shore "+(offsetCheck?"map offset":"floor pull")+" "+(pullBaseline||offsetBaseline?"negative control":"GPU")+" PASS: cases="+cases+" maxMissing="+worstMissing+" water="+(overlay==null?"native":"VP/"+pack)+"; actual mesh raster, native water producer and elevated foreground; controlled coverage, no live map.");
    }
    private static void drawNativeWater() {
        for(boolean shore:new boolean[]{false,true}) {
            Object previous=RotationWater.enter(0,0,shore);
            try {
                Matrix4f projection=new Matrix4f();
                GameAccess.call("zombie.iso.IsoWater","waterProjection",water,new Class<?>[]{Matrix4f.class},projection);
                matrix(waterProgram,"ModelViewProjection",projection);
                GameAccess.call("zombie.iso.IsoWater","waterGeometry",water,new Class<?>[]{int.class,int.class,boolean.class},0,shore?8:1,shore);
            } finally { RotationWater.leave(previous); }
        }
    }
    private static ChunkMeshData bankMesh(boolean full) {
        var vertices=new ArrayList<Float>();
        for(int y=-3;y<6;y++)for(int x=-3;x<6;x++) {
            if(!full && x==1 && y==1)continue;
            int[][] corners={{x,y},{x+1,y},{x+1,y+1},{x,y},{x+1,y+1},{x,y+1}};
            for(int[] c:corners){float[] a=new float[14];a[0]=1.5f-c[0];a[2]=1.5f-c[1];a[7]=a[8]=a[10]=1;a[13]=Float.intBitsToFloat(pullCheck?floorFlags:64);for(float f:a)vertices.add(f);}
        }
        float[] values=new float[vertices.size()];for(int i=0;i<values.length;i++)values[i]=vertices.get(i);
        return new ChunkMeshData(new zombie.core.textures.TextureID[1],new int[]{0},new int[]{values.length/14},values,0);
    }
    private static void thinSilhouettes() {
        Object context=GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        GameAccess.set("viewpoint.render.FrameContext","width",context,SIZE); GameAccess.set("viewpoint.render.FrameContext","height",context,SIZE);
        NativeVision bridge=new NativeVision(); bridge.rotation=new RotationCamera.Snapshot(new Matrix4f(),new Matrix4f(),0,0,0,SIZE,SIZE,1f,90f);
        for(boolean vertical:new boolean[]{true,false}) for(float jitter:new float[]{-.25f,.25f}) {
            IntBuffer packed=BufferUtils.createIntBuffer(SIZE*SIZE);
            for(int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) packed.put(((vertical?x:y)==SIZE/2?Math.round(.49f*0xffffff):0xffffff)<<8);
            packed.flip(); GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D,sourceDepth);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH24_STENCIL8,SIZE,SIZE,0,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8,packed);
            GameAccess.set("viewpoint.render.FrameContext","jitterX",context,vertical?jitter:0f);
            GameAccess.set("viewpoint.render.FrameContext","jitterY",context,vertical?0f:jitter);
            bridge.writeDepth();
            FloatBuffer depth=BufferUtils.createFloatBuffer(1); GL11.glReadPixels(SIZE/2,SIZE/2,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
            if(Math.abs(depth.get(0)-.49f)>1e-5f) throw new AssertionError("Thin silhouette depth must not extrapolate into sky: "+depth.get(0));
        }
        System.out.println("Thin silhouette depth GPU PASS: 1-source-texel vertical/horizontal occluders retain depth against sky at positive/negative jitter.");
    }
    private static int area(byte[] image){int n=0;for(int i=2;i<image.length;i+=4) if(image[i]!=0)n++;return n;}
    private static float halton(int index,int base){float f=1,r=0;while(index>0){f/=base;r+=f*(index%base);index/=base;}return r;}
    private static void matrix(int program,String name,Matrix4f matrix){GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,name),false,matrix.get(new float[16]));}
    private static int texture(int internal,int format,int type){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,SIZE,SIZE,0,format,type,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    private static int program(String vertex,String fragment){int p=GL20.glCreateProgram();for(int i=0;i<2;i++){int s=GL20.glCreateShader(i==0?GL20.GL_VERTEX_SHADER:GL20.GL_FRAGMENT_SHADER);GL20.glShaderSource(s,i==0?vertex:fragment);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(s));GL20.glAttachShader(p,s);}GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(p));return p;}
    private static Object get(Class<?> c,String name,Object target)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Class<?> c,String name,Object target,Object value)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
