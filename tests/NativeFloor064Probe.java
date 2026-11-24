import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import vpiso.*;

/** Exercise the native ground RGB branch, including matched physical floors and biased tile depth. */
public final class NativeFloor064Probe {
    static final NativeSurfaceReceiverProbe N=new NativeSurfaceReceiverProbe();
    static final String FRAGMENT="""
        #version 330
        layout(location=0) out vec4 color;
        layout(location=7) out vec4 ground;
        uniform float depthBias,groundOn;
        void main(){color=vec4(.7,.55,.4,.8);ground=vec4(color.rgb*groundOn,1);
          gl_FragDepth=floor((gl_FragCoord.z+depthBias)*65535.0)/65535.0;}
        """;
    public static void main(String[] args)throws Exception {
        boolean baseline=Arrays.asList(args).contains("baseline063");
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"physical native ground064",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            String pack=args.length>0?args[0]:"library";
            TestShaderPacks.selectLive(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.nativeProgram=NativeGroundProbe.link(N.VERT,FRAGMENT);
            int cases=0,mismatches=0;
            for(int direction=0;direction<4;direction++) {
                Frame frame=N.frame();
                frame.scene.sunX=direction==0?-.6f:direction==1?.6f:0;
                frame.scene.sunZ=direction==2?-.6f:direction==3?.6f:0;
                frame.scene.sunY=.8f;
                float[][] floor=N.horizontal(0,2.3f);N.opaque=4;
                Object receiver=N.mesh(frame.scene,0,floor);N.opaque=0;
                float[][] roof=N.horizontal(1.5f,.7f);
                Object caster=N.mesh(frame.scene,1,roof);
                Arrays.fill(frame.scene.exposure,(byte)255);
                byte[] reference=draw(frame,floor,0,true,true);
                int dark=0;for(int i=0;i<N.W*N.H;i++)if((reference[i*4]&255)<150 && (reference[i*4]&255)>80)dark++;
                N.require(dark>20,"Ground projection absent");
                for(float bias:new float[]{.0004f,.0007f,.001f}) {
                    byte[] actual=draw(frame,floor,bias,true,true);
                    int mismatch=0;for(int i=0;i<actual.length;i++)if(actual[i]!=reference[i])mismatch++;
                    mismatches+=mismatch;
                    if(!baseline)N.require(mismatch==0,"Physical floor footprint follows billboard depth instead of floor: "+mismatch);
                    cases++;
                }
                byte[] biasedOpen=draw(frame,floor,.0007f,true,true);
                Arrays.fill(frame.scene.exposure,(byte)100);
                N.require(Arrays.equals(biasedOpen,draw(frame,floor,.0007f,true,true)),"Covered exterior floor clipping");cases++;
                byte[] disabled=draw(frame,floor,0,true,false);
                Arrays.fill(frame.scene.exposure,(byte)0);
                N.require(Arrays.equals(disabled,draw(frame,floor,.0007f,false,true)),"Interior ground darkened");cases++;
                N.retire(caster);N.retire(receiver);
            }
            if(baseline)N.require(mismatches>100,"Pinned063 ground-position negative control not reproduced");
            N.require(NativeGround.receivesSun(NativeGroundProbe.floorObject(true,true))!=baseline,"Outdoor covered native floor tagging");
            N.require(!NativeGround.receivesSun(NativeGroundProbe.floorObject(false,true)),"Indoor native floor tagging");
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println((baseline?"Pinned063 native floor negative PASS: ":"Native floor064 GPU PASS: ")+cases+" cases; native RGB contribution branch, actual physical floor receiver, four shadow directions, depth-bias footprint mismatches="+mismatches+", covered/open parity, indoor/off controls, native RGBA/depth/stencil preserved. Controlled fixture, no live map.");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static byte[] draw(Frame f,float[][] points,float bias,boolean groundOn,boolean enabled) {
        f.number=++N.number;f.scene.cameraCut=true;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glViewport(0,0,N.W,N.H);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,0,0,0,0,GL30.GL_COLOR_ATTACHMENT7});
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(.2f,.3f,.4f,.8f);GL11.glClearDepth(1);GL11.glStencilMask(255);GL11.glClearStencil(31);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GL30.glClearBufferfv(GL11.GL_COLOR,7,new float[]{0,0,0,0});
        FloatBuffer data=BufferUtils.createFloatBuffer(points.length*3);for(float[] p:points)data.put(p);data.flip();
        GL30.glBindVertexArray(N.vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,N.vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glUseProgram(N.nativeProgram);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(N.nativeProgram,"projection"),false,NativeSurfaces.projection(N.receiver).get(new float[16]));
        GL20.glUniform1f(GL20.glGetUniformLocation(N.nativeProgram,"depthBias"),bias);GL20.glUniform1f(GL20.glGetUniformLocation(N.nativeProgram,"groundOn"),groundOn?1:0);
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0L);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,points.length);
        byte[] before=new byte[N.W*N.H*4];N.read().get(before);
        float[] depthBefore=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthBefore);
        N.overlay.ground(N.ground);N.overlay.capture(true);
        IsometricBridge.rendering(f);
        try{N.require(NativeShadowMaps.draw(f.scene,1024,2,N.overlay.surfaces(N.receiver)),"maps unavailable");}
        finally{IsometricBridge.rendering(null);}
        Matrix4f[] matrices={new Matrix4f(NativeShadowMaps.matrix(0)),new Matrix4f(NativeShadowMaps.matrix(1)),new Matrix4f()};
        float[] texels={2*NativeShadowMaps.reach(0)/1024,2*NativeShadowMaps.reach(1)/1024,0};
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,N.W,N.H);
        N.overlay.draw(N.receiver,new IsometricEffects(enabled,.55f,1,64,1024,false,0,1,1,1),NativeShadowMaps.texture(),matrices,texels,NativeShadowMaps.depthRange(),1024,2,1);
        byte[] after=new byte[before.length];N.read().get(after);
        for(int i=0;i<N.W*N.H;i++)N.require(after[i*4+3]==before[i*4+3],"native alpha changed");
        float[] depthAfter=new float[N.W*N.H];GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthAfter);
        N.require(Arrays.equals(depthBefore,depthAfter),"native depth changed");
        ByteBuffer stencil=BufferUtils.createByteBuffer(N.W*N.H);GL11.glReadPixels(0,0,N.W,N.H,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,stencil);
        for(int i=0;i<N.W*N.H;i++)N.require(stencil.get(i)==31,"native stencil changed");
        return after;
    }
}
