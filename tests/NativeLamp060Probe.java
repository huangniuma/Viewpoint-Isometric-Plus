import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.*;
import vpiso.*;

/** Actual VP lamp cube producer, captured light atlas and native receiver/compositor. */
public final class NativeLamp060Probe {
    static final NativeSurfaceReceiverProbe N=new NativeSurfaceReceiverProbe();
    static boolean shadows=true,baseline;
    static ByteBuffer last;
    static final int[] UNITS={5,49,110,111,152};
    static final int[] TARGETS={GL11.GL_TEXTURE_2D,GL30.GL_TEXTURE_2D_ARRAY,GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_3D};
    static final int[] BINDINGS={GL11.GL_TEXTURE_BINDING_2D,GL30.GL_TEXTURE_BINDING_2D_ARRAY,GL30.GL_TEXTURE_BINDING_2D_ARRAY,GL11.GL_TEXTURE_BINDING_2D,GL12.GL_TEXTURE_BINDING_3D};
    static final int[] TEXTURES=new int[5],SAMPLERS=new int[5];
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(256,256,"lamp060",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            String pack=args.length>0?args[0]:"library";
            baseline=args.length>1 && args[1].equals("baseline059");
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            if(Arrays.asList(args).contains("live"))TestShaderPacks.selectLive(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            else TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);
            for(int i=0;i<5;i++){TEXTURES[i]=GL11.glGenTextures();SAMPLERS[i]=GL33.glGenSamplers();}
            float[][] points=N.horizontal(.8f,1.4f);
            Frame frame=N.frame();frame.scene.sunStrength=0;
            frame.scene.originX=frame.scene.originZ=-3;
            frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=.05f;
            Object receiver=N.mesh(frame.scene,0,points);publish(receiver,true);
            lamp(frame.scene,-3,true);
            int own=settle(frame,points);
            if(own!=0)diagnose();
            N.require(own==0,"lamp self-shadow: "+own);
            float[][] blocker=N.horizontal(3.4f,.65f);for(float[] p:blocker)p[0]-=1.65f;
            Object caster=N.mesh(frame.scene,1,blocker);
            invalidate();int cross=settle(frame,points);byte[] left=copy(last);
            if(baseline) {
                N.require(cross==0,"059 negative control unexpectedly has lamp shadows");
                System.out.println("Pinned059 lamp negative control PASS: night lamp/caster produces no shadow, corrected build receives projection.");return;
            }
            if(cross==0)diagnose();
            N.require(cross>5,"missing night lamp projection: "+cross);
            lamp(frame.scene,3,true);invalidate();int moved=settle(frame,points);
            N.require(!Arrays.equals(left,copy(last)),"lamp movement did not change shadow");
            lamp(frame.scene,-3,false);publish(receiver,false);Arrays.fill(frame.scene.exposure,(byte)0);invalidate();
            int indoor=settle(frame,points);
            N.require(indoor>5,"missing indoor lamp projection: "+indoor);
            shadows=false;int off=draw(frame,points);shadows=true;
            N.require(off==0,"effect off changed artwork: "+off);
            frame.scene.lightCount=frame.scene.lampShadowCount=0;
            N.require(draw(frame,points)==0,"extinguished lamp retained shadow");
            lamp(frame.scene,-3,false);frame.scene.meshCount=1;invalidate();
            N.require(settle(frame,points)==0,"removed caster retained shadow");
            N.retire(caster);N.retire(receiver);
            for(String kind:new String[]{"floor","wall","rigid","skinned","vehicle","skinned caster"}) {
                Frame f=N.frame();f.scene.sunStrength=0;f.scene.originX=f.scene.originZ=-3;
                f.scene.skyR=f.scene.skyG=f.scene.skyB=.05f;
                float[][] surface=kind.equals("wall")?N.vertical(1.4f,1.8f):N.horizontal(kind.equals("floor")?0:.8f,1.4f);
                boolean model=kind.equals("rigid")||kind.equals("skinned")||kind.equals("vehicle");
                Object page=N.mesh(f.scene,0,model?N.horizontal(-5,.1f):surface);publish(page,false);Arrays.fill(f.scene.exposure,(byte)0);
                if(model)N.addModel(f.scene,surface,kind.equals("skinned"),kind.equals("vehicle"));
                lamp(f.scene,-3,false);invalidate();int self=settle(f,surface);
                N.require(self==0,kind+" self shadow: "+self);
                Object blockerMesh=null;
                if(kind.equals("skinned caster"))N.addModel(f.scene,blocker,true,false);
                else blockerMesh=N.mesh(f.scene,1,blocker);
                invalidate();int projected=settle(f,surface);
                N.require(projected>5,kind+" missing lamp projection: "+projected);
                lamp(f.scene,-3,true);N.require(settle(f,surface)==0,kind+" outdoor lamp leaked into indoor light");
                N.retire(page);if(blockerMesh!=null)N.retire(blockerMesh);
                System.out.println("Lamp receiver="+kind+" own="+self+" external="+projected+" outdoor-to-indoor=0");
            }
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println("Native lamps060 GPU PASS pack="+pack+" own="+own+" night="+cross+" moved="+moved+" indoor="+indoor+"; real cube maps + captured atlas + physical native receiver; off/extinguished/removed caster identity; RGBA/depth/stencil preserved.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void lamp(SceneData s,float x,boolean outdoors) {
        s.lightCount=s.lampShadowCount=1;s.lightKeys[0]=123;
        float[] l=s.lights;Arrays.fill(l,0,20,0);
        l[0]=l[16]=x;l[1]=l[17]=5;l[2]=l[18]=0;l[3]=l[19]=12;
        l[4]=l[5]=l[6]=l[7]=l[9]=1;l[8]=outdoors?1:0;l[10]=5;
    }
    static void publish(Object mesh,boolean outdoors) {
        ByteBuffer light=(ByteBuffer)GameAccess.call("viewpoint.render.ChunkMeshData","spareLight",mesh,new Class<?>[0]);light.clear();
        for(int i=0;i<800;i++)light.put((byte)(i<400?180:outdoors?0:255)).put((byte)180).put((byte)180).put((byte)(outdoors?255:0));
        light.flip();GameAccess.call("viewpoint.render.ChunkMeshData","publishLight",mesh,new Class<?>[]{ByteBuffer.class},light);
    }
    static void invalidate() {
        if(baseline)return;
        Object pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        if(pass!=null)NativeLampShadows.invalidate(pass);
    }
    static int settle(Frame frame,float[][] points) {int dark=0;for(int i=0;i<10;i++)dark=draw(frame,points);return dark;}
    static byte[] copy(ByteBuffer data) {byte[] bytes=new byte[data.capacity()];for(int i=0;i<bytes.length;i++)bytes[i]=data.get(i);return bytes;}
    static void diagnose() {
        var p=(viewpoint.platform.GlProgram)GameAccess.field("vpiso.NativeOverlay","program",N.overlay);
        for(String name:new String[]{"vpIsoLampsOn","uLampShadowCount","uLampShadowSlot[0]","uLightPos[0]","uLightColor[0]","uLightOutdoors[0]","uLightRise[0]","uLampShadowAt[0]","uSceneOrigin","uLightAtlasSize"}) {
            float[] value=new float[4];GL20.glGetUniformfv(p.id(),GL20.glGetUniformLocation(p.id(),name),value);System.out.println(name+"="+Arrays.toString(value));
        }
        String source=NativeLighting.fragment(NativeOverlay.FRAGMENT);
        source=source.substring(0,source.lastIndexOf("void main()"))+"""
          void main(){vec4 s=texture(nativeSurfaces,uv);if(abs(s.a)<.5){outputColor=vec4(0);return;}
          vec3 n=normalize(cross(dFdx(s.xyz),dFdy(s.xyz)));
          bool found=nearLightFound(-(uSceneOrigin.xz+s.xz)-n.xz*.3,int(floor((s.y+uSceneOrigin.y)/SQRT6+1e-4)));
          outputColor=vec4(found?1:0,lampShadow(0,s.xyz,n),lampField(0,s.xyz,n),1);}
          """;
        var debug=viewpoint.platform.GlProgram.fromSources("test lamp debug",new viewpoint.platform.GlProgram.Sources(NativeOverlay.VERTEX,null,null,null,source),Map.of(),Map.of(),List.of());
        debug.use();NativeLampShadows.uniforms(debug);debug.setInt("nativeSurfaces",4);
        Object surface=GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,((NativeSurfaces)surface).texture());
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);ByteBuffer b=N.read();int found=0,cube=0,field=0,total=0;
        for(int i=0;i<N.W*N.H;i++)if(b.get(i*4+3)!=0){total++;if(b.get(i*4)!=0)found++;if((b.get(i*4+1)&255)<240)cube++;if((b.get(i*4+2)&255)<240)field++;}
        System.out.println("debug total="+total+" nearFound="+found+" cubeDark="+cube+" fieldDark="+field);debug.delete();
    }
    static int draw(Frame frame,float[][] points) {
        frame.number=++N.number;
        if(!baseline)for(int i=0;i<5;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);GL11.glBindTexture(TARGETS[i],TEXTURES[i]);GL33.glBindSampler(UNITS[i],SAMPLERS[i]);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glViewport(0,0,N.W,N.H);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClearDepth(1);GL11.glStencilMask(255);GL11.glClearStencil(31);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{0,0,0,0});
        FloatBuffer data=BufferUtils.createFloatBuffer(points.length*3);for(float[] p:points)data.put(p);data.flip();
        GL30.glBindVertexArray(N.vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,N.vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glUseProgram(N.nativeProgram);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(N.nativeProgram,"projection"),false,NativeSurfaces.projection(N.receiver).get(new float[16]));
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0L);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);
        ByteBuffer before=N.read();float[] depthBefore=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthBefore);
        N.overlay.ground(N.ground);N.overlay.capture(true);
        IsometricBridge.rendering(frame);
        try {NativeShadowMaps.draw(frame.scene,1024,2,N.overlay.surfaces(N.receiver));}
        finally {IsometricBridge.rendering(null);}
        Matrix4f[] matrices={new Matrix4f(),new Matrix4f(),new Matrix4f()};float[] texels={0,0,0};
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
        N.overlay.draw(N.receiver,new IsometricEffects(shadows,.55f,1,64,1024,false,0,1,1,1),0,matrices,texels,128,1024,0,0);
        if(!baseline)for(int i=0;i<5;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+UNITS[i]);
            N.require(GL11.glGetInteger(BINDINGS[i])==TEXTURES[i],"lamp texture binding leaked unit="+UNITS[i]);
            N.require(GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,UNITS[i])==SAMPLERS[i],"lamp sampler leaked unit="+UNITS[i]);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        last=N.read();int dark=0;
        for(int i=0;i<N.W*N.H;i++) {
            if((last.get(i*4)&255)+3<(before.get(i*4)&255))dark++;
            N.require(last.get(i*4+3)==before.get(i*4+3),"native alpha changed");
        }
        float[] depthAfter=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthAfter);
        N.require(Arrays.equals(depthBefore,depthAfter),"native depth changed");
        ByteBuffer stencil=BufferUtils.createByteBuffer(N.W*N.H);GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencil);
        for(int i=0;i<N.W*N.H;i++)N.require(stencil.get(i)==31,"native stencil changed");
        N.require(GL11.glGetError()==0,"GL error frame="+N.number);return dark;
    }
}
