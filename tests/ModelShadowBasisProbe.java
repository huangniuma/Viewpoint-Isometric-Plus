import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FarRelief;
import vpiso.*;

/** Actual VP shadow cache regression, including a pinned previous-artifact negative control. */
class ModelShadowBasisProbe {
    static boolean baseline;
    static final int W=128;
    static int fbo,vao;
    static GlProgram shader;
    static final IsometricEffects effects=new IsometricEffects(true,1f,0f,64f,1024,false,1,1,1,1);
    public static void main(String[] args) throws Exception {
        baseline=args.length>0 && args[0].equals("baseline034");
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"upper shadow discriminator",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));
            GlProgram.onUse(IsometricShaders::onUse);
            Method expand=ModelEffectsProbe.class.getDeclaredMethod("expand",String.class,HashSet.class);expand.setAccessible(true);
            String sun=(String)expand.invoke(null,"lib/sun.glsl",new HashSet<String>());
            String relief=(String)expand.invoke(null,"lib/far_relief.glsl",new HashSet<String>());
            String defines=(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active());
            String vertex="#version 330\nout vec2 vUv;void main(){vUv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(vUv*2.-1.,0,1);}";
            String fragment="#version 450\n"+defines+relief+sun+"\nin vec2 vUv;out vec4 result;void main(){vec3 p=vec3((vUv.x-.5)*24.,0,(vUv.y-.5)*24.);p=reliefLifted(p);vec3 n=reliefNormal(vec3(0,1,0),p);result=vec4(sunlit(p,n,vec3(0,1.5,0),false),sunAt(p),0,1);}";
            shader=GlProgram.fromSources("upper roof discriminator",new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of(),List.of());
            int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,W,W,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
            vao=GL30.glGenVertexArrays();
            Frame frame=NativeSurfaceReceiverProbe.frame();
            Object mesh=NativeSurfaceReceiverProbe.mesh(frame.scene,0,NativeSurfaceReceiverProbe.horizontal(2.4494896f,6f));
            frame.scene.meshFlags[0]=2|16|8;
            float[] hidden=draw(frame,1);
            frame.scene.meshFlags[0]|=1;
            float[] visible=draw(frame,2);
            int changed=0,covered=0;
            for(int i=0;i<W*W;i++) {if(Math.abs(hidden[i*4]-visible[i*4])>.001)changed++;if(hidden[i*4]<.5)covered++;}
            if(covered<2000 || changed!=0)throw new AssertionError("roof expected stable: "+covered+" / "+changed);
            frame.scene.projection.setPerspective(.002f,1f,5744f,6256f);
            float[] zoom=draw(frame,3);int zoomChanged=0;
            for(int i=0;i<W*W;i++)if(Math.abs(hidden[i*4]-zoom[i*4])>.001)zoomChanged++;
            if(zoomChanged!=0)throw new AssertionError("sun projection depends on camera "+zoomChanged);
            frame.scene.meshFlags[0]=1;
            float[] stale=draw(frame,4);int retained=0;
            for(int i=0;i<W*W;i++)if(stale[i*4]<.5)retained++;
            Object shadows=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
            Object layers=GameAccess.field("viewpoint.render.ShadowPass","layers",shadows);
            GameAccess.call("viewpoint.render.CascadeLayers","release",layers,new Class<?>[0]);
            float[] removed=draw(frame,5);int without=0;
            for(int i=0;i<W*W;i++)if(removed[i*4]<.5)without++;
            if(without!=0)throw new AssertionError("removed roof still casts "+without);
            NativeSurfaceReceiverProbe.retire(mesh);
            System.out.println("Upper-roof GPU discriminator PASS: actual uploaded VP opaque roof, actual cascades and rewritten sunlit/sunAt; hidden main-view roof shadow pixels="+covered+", visible-vs-hidden changed="+changed+", camera projection changed="+zoomChanged+", shadow flag removed but settled cache retained="+retained+", after cascade invalidation dark pixels="+without+". Stable roof projection; native vision fan, full model lighting and user map not simulated.");
            checkReliefSwitch();
            checkMainMesh(expand,defines,sun);
            checkFlatToRoofRelief();
            if(!baseline) checkBasisReuse();
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("GL error");
            System.out.println("Model shadow basis GPU PASS: " + (baseline ? "034 negative control reproduced" : "035 height transitions corrected; roof occlusion and stable cache preserved"));
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void clearLayers() {
        Object shadows=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        Object layers=GameAccess.field("viewpoint.render.ShadowPass","layers",shadows);
        GameAccess.call("viewpoint.render.CascadeLayers","release",layers,new Class<?>[0]);
        java.util.Arrays.fill((boolean[])GameAccess.field("viewpoint.render.ShadowPass","valid",shadows),false);
    }
    static void checkFlatToRoofRelief() throws Exception {
        Frame frame=NativeSurfaceReceiverProbe.frame();frame.scene.originX=-128;frame.scene.originZ=-128;
        Object roof=NativeSurfaceReceiverProbe.mesh(frame.scene,0,NativeSurfaceReceiverProbe.horizontal(2.4494896f,6f));
        float[] h=new float[96*96];
        for(int slope:new int[]{1,-1}) {
            frame.scene.relief(null);clearLayers();draw(frame,200+slope);
            for(int y=0;y<96;y++)for(int x=0;x<96;x++)h[y*96+x]=2f+slope*((x-32)*.025f+(y-32)*.015f);
            FarRelief.Field field=FarRelief.Field.of(96,96,h,h);
            frame.scene.relief(new FarRelief.Lift(field,.5f,field.metresAt(128,128),128,128));
            float[] reused=draw(frame,210+slope);
            clearLayers();float[] fresh=draw(frame,220+slope);
            int mismatch=0,missing=0,extra=0;
            for(int i=0;i<W*W;i++) {
                if(Math.abs(reused[i*4]-fresh[i*4])>.05)mismatch++;
                if(reused[i*4]>.5 && fresh[i*4]<.5)missing++;
                if(reused[i*4]<.5 && fresh[i*4]>.5)extra++;
            }
            if(baseline ? missing<4000 : mismatch!=0)
                throw new AssertionError("Roof transition expected "+(baseline?"stale negative control":"fresh-equivalent projection")+": "+mismatch+" / "+missing);
            if(dark(fresh)<4000)throw new AssertionError("Fresh roof occlusion lost");
            System.out.println("Flat->model roof terrain sign="+slope+" receiver actual-relief: reused shadow pixels="+dark(reused)+", fresh shadow pixels="+dark(fresh)+", mismatch="+mismatch+", missing="+missing+", extra="+extra);
        }
        NativeSurfaceReceiverProbe.retire(roof);
    }
    static void checkBasisReuse() {
        Frame frame=NativeSurfaceReceiverProbe.frame();
        float[] h=new float[96*96];
        FarRelief.Field grid=FarRelief.Field.of(96,96,h,h);
        frame.scene.relief(new FarRelief.Lift(grid,.5f,0,128,128));
        Object pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        NativeShadowMaps.prepareBasis(pass,frame.scene);
        boolean[] valid=(boolean[])GameAccess.field("viewpoint.render.ShadowPass","valid",pass);
        java.util.Arrays.fill(valid,true);
        // Ground/camera drift and a new Lift wrapper do not change terrain geometry.
        frame.scene.relief(new FarRelief.Lift(grid,.5f,.001f,128.1,128.1));
        frame.scene.originX+=.1;frame.scene.originZ+=.1;
        NativeShadowMaps.prepareBasis(pass,frame.scene);
        for(boolean value:valid)if(!value)throw new AssertionError("Stable terrain needlessly invalidated");
        Main.compatible=true;IsometricBridge.rendering(null);
        viewpoint.render.FrameContext context=new viewpoint.render.FrameContext();context.scene=frame.scene;
        frame.scene.relief(new FarRelief.Lift(grid,.7f,0,128,128,new float[]{0f,1f,2f}));
        if(Patch_ModelShadows.enter(pass,context))throw new AssertionError("Ordinary VP body skipped");
        for(boolean value:valid)if(value)throw new AssertionError("Ordinary VP scale transition retained old map");
        java.util.Arrays.fill(valid,true);
        float[] bands=frame.scene.relief.bands();bands[0]+=.01f;
        NativeShadowMaps.prepareBasis(pass,frame.scene);
        for(boolean value:valid)if(value)throw new AssertionError("In-place bands change retained old map");
        java.util.Arrays.fill(valid,true);
        frame.scene.relief(null);
        IsometricBridge.modelRendering(frame,effects);
        context.pipeline=ShaderPacks.active().pipeline;context.sun=true;
        if(!Patch_ModelShadows.enter(pass,context))throw new AssertionError("Model draw body not used");
        System.out.println("Basis lifetime PASS: stable terrain cache survives new frame/wrapper and camera drift; ordinary VP scale, mutable bands and model transitions observed");
    }
    static int dark(float[] pixels) {int n=0;for(int i=0;i<W*W;i++)if(pixels[i*4]<.5)n++;return n;}
    static void checkReliefSwitch() throws Exception {
        Frame frame=NativeSurfaceReceiverProbe.frame();
        frame.scene.originX=-128;frame.scene.originZ=-128;
        Method make=NativeShadowReliefProbe.class.getDeclaredMethod("mesh",Frame.class,int.class,float.class,float.class);make.setAccessible(true);
        Object floor=make.invoke(null,frame,0,0f,32f);
        clearLayers();int flat=dark(draw(frame,10));
        float[] a=new float[96*96],b=new float[96*96];
        for(int y=0;y<96;y++)for(int x=0;x<96;x++) {a[y*96+x]=2f+(x-32)*.025f+(y-32)*.015f;b[y*96+x]=2f-(x-32)*.025f-(y-32)*.015f;}
        FarRelief.Field af=FarRelief.Field.of(96,96,a,a),bf=FarRelief.Field.of(96,96,b,b);
        frame.scene.relief(new FarRelief.Lift(af,.5f,af.metresAt(128,128),128,128));
        int staleA=dark(draw(frame,11));clearLayers();int freshA=dark(draw(frame,12));
        frame.scene.relief(new FarRelief.Lift(bf,.5f,bf.metresAt(128,128),128,128));
        int staleB=dark(draw(frame,13));clearLayers();int freshB=dark(draw(frame,14));
        frame.scene.relief(null);int staleFlat=dark(draw(frame,15));clearLayers();int freshFlat=dark(draw(frame,16));
        if(flat!=0 || freshA!=0 || freshB!=0 || freshFlat!=0)throw new AssertionError("Fresh floor self-shadow");
        if(baseline ? staleB<500 || staleFlat<16000 : staleB!=0 || staleFlat!=0)
            throw new AssertionError("Terrain transition: "+staleB+" / "+staleFlat);
        NativeSurfaceReceiverProbe.retire(floor);
        System.out.println("Relief same-mesh cache switch GPU observation: initial flat="+flat+", flat->slopeA reused="+staleA+" fresh="+freshA+", slopeA->slopeB reused="+staleB+" fresh="+freshB+", slopeB->flat reused="+staleFlat+" fresh="+freshFlat+". Receiver height/normal use actual reliefLifted/reliefNormal; same mesh identity; no native visibility polygon.");
    }
    static void checkMainMesh(Method expand,String defines,String sun)throws Exception {
        String vertex=(String)expand.invoke(null,"mesh.vert",new HashSet<String>());
        vertex=vertex.replaceFirst("#version 330","#version 450\n"+defines);
        String fragment="#version 450\n"+defines+sun+"\nin vec3 vPos;in vec3 vNormal;out vec4 result;void main(){result=vec4(sunlit(vPos,normalize(vNormal),vec3(0,1.5,0),false),sunAt(vPos),0,1);}";
        GlProgram main=GlProgram.fromSources("actual main mesh receiver",new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of(),List.of());
        Method make=NativeShadowReliefProbe.class.getDeclaredMethod("mesh",Frame.class,int.class,float.class,float.class);make.setAccessible(true);
        Frame frame=NativeSurfaceReceiverProbe.frame();frame.scene.originX=-128;frame.scene.originZ=-128;frame.camX=128;frame.camY=128;
        Object floor=make.invoke(null,frame,0,0f,32f);frame.scene.meshFlags[0]|=1;
        float[] h=new float[96*96];for(int y=0;y<96;y++)for(int x=0;x<96;x++)h[y*96+x]=2f+(x-32)*.025f+(y-32)*.015f;
        FarRelief.Field field=FarRelief.Field.of(96,96,h,h);
        for(int terrain=0;terrain<3;terrain++) {
            boolean relief=terrain>0;
            if(terrain==2) {
                for(int y=0;y<96;y++)for(int x=0;x<96;x++)h[y*96+x]=2f+.002f*((x-32)*(x-32)+(y-32)*(y-32));
                field=FarRelief.Field.of(96,96,h,h);
            }
            frame.scene.relief(relief?new FarRelief.Lift(field,.5f,field.metresAt(128,128),128,128):null);
            int allDark=0,allPixels=0;
            for(float zoom:new float[]{8,16,24})for(float yaw:new float[]{0,90,180,270}) {
                clearLayers();frame.number++;
                IsometricBridge.modelRendering(frame,effects);
                if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
                int[] counts=mainReceiver(frame,main,zoom,yaw);int dark=counts[0],covered=counts[1];
                allDark+=dark;allPixels+=covered;
                System.out.println("Main mesh receiver: relief="+relief+" halfHeight="+zoom+" yaw="+yaw+" pixels="+covered+" dark="+dark);
            }
            if(allDark!=0 || allPixels<100000)throw new AssertionError("Actual main mesh floor coverage/self-shadow");
            System.out.println("Actual mesh.vert fresh-cascade floor summary: terrain="+terrain+" relief="+relief+" frames=12 pixels="+allPixels+" false dark="+allDark);
        }
        frame.scene.relief(null);
        frame.number++;
        if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
        int[] stale=mainReceiver(frame,main,16,0);
        clearLayers();frame.number++;
        if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
        int[] fresh=mainReceiver(frame,main,16,0);
        if(fresh[0]!=0 || (baseline ? stale[0]<16000 : stale[0]!=0))throw new AssertionError("Main receiver cache mismatch");
        System.out.println("Actual main mesh relief->flat cache: reused dark="+stale[0]+"/"+stale[1]+", fresh dark="+fresh[0]+"/"+fresh[1]);
        for(int slope:new int[]{1,-1}) {
            frame.scene.relief(null);clearLayers();frame.number++;
            if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
            for(int y=0;y<96;y++)for(int x=0;x<96;x++)h[y*96+x]=2f+slope*((x-32)*.025f+(y-32)*.015f);
            FarRelief.Field target=FarRelief.Field.of(96,96,h,h);
            frame.scene.relief(new FarRelief.Lift(target,.5f,target.metresAt(128,128),128,128));
            frame.number++;if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
            int[] reused=mainReceiver(frame,main,16,0);
            clearLayers();frame.number++;if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
            int[] cleared=mainReceiver(frame,main,16,0);
            System.out.println("Actual main mesh flat->slope sign="+slope+" cache: reused dark="+reused[0]+"/"+reused[1]+", fresh dark="+cleared[0]+"/"+cleared[1]);
        }
        NativeSurfaceReceiverProbe.retire(floor);main.delete();
    }
    static int[] mainReceiver(Frame frame,GlProgram main,float zoom,float yaw) {
                Matrix4f view=new Matrix4f();float[] eye=new float[3];
                IsoCameraMath.matrices(new IsoCameraMath.Fit(128,128,zoom),128,128,W,W,frame.scene.projection,view,eye,yaw);
                Matrix4f pv=new Matrix4f(frame.scene.projection).mul(view);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glColorMask(true,true,true,true);
                main.use();main.set("uViewProjection",pv);main.set("uEye",eye[0],eye[1],eye[2]);main.setInt("uShadowCascades",6);main.set("uSunDir",frame.scene.sunX,frame.scene.sunY,frame.scene.sunZ);main.set("uSunStrength",1f);main.set("uShadowsOff",0f);
                for(int i=0;i<2;i++) {main.set("uShadowMatrix["+i+"]",NativeShadowMaps.matrix(i));GL20.glUniform4f(main.location("uShadowMaps["+i+"]"),2f*NativeShadowMaps.reach(i)/1024,NativeShadowMaps.depthRange(),1f/1024,0);}
                GL13.glActiveTexture(GL13.GL_TEXTURE0+6);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());GL33.glBindSampler(6,0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GameAccess.call("viewpoint.render.Meshes","draw",null,new Class<?>[]{viewpoint.render.SceneData.class,GlProgram.class,byte.class},frame.scene,main,(byte)1);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                int dark=0,covered=0;for(int i=0;i<W*W;i++)if(pixels.get(i*4+3)>.5){covered++;if(pixels.get(i*4)<.5)dark++;}
        return new int[]{dark,covered};
    }
    static float[] draw(Frame frame,long number) {
        frame.number=number;IsometricBridge.modelRendering(frame,effects);
        if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);GL30.glBindVertexArray(vao);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glColorMask(true,true,true,true);
        shader.use();shader.setInt("uShadowCascades",6);shader.set("uSunDir",frame.scene.sunX,frame.scene.sunY,frame.scene.sunZ);shader.set("uSunStrength",1f);shader.set("uShadowsOff",0f);
        for(int i=0;i<2;i++) {shader.set("uShadowMatrix["+i+"]",NativeShadowMaps.matrix(i));GL20.glUniform4f(shader.location("uShadowMaps["+i+"]"),2f*NativeShadowMaps.reach(i)/1024,NativeShadowMaps.depthRange(),1f/1024,0);}
        GL13.glActiveTexture(GL13.GL_TEXTURE0+6);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());GL33.glBindSampler(6,0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);float[] result=new float[W*W*4];pixels.get(result);return result;
    }
}
