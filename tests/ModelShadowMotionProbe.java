import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Two complete world-space camera paths: retained terrain cascades versus fresh maps. */
public final class ModelShadowMotionProbe {
    static final int W=128, STEPS=161;
    static GlProgram receiver;
    static int fbo,vao;
    static final IsometricEffects FX=new IsometricEffects(true,1,0,64,1024,false,1,1,1,1);
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"world-space shadow motion",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));
            GlProgram.onUse(IsometricShaders::onUse);
            Method expand=ModelEffectsProbe.class.getDeclaredMethod("expand",String.class,HashSet.class);expand.setAccessible(true);
            String sun=(String)expand.invoke(null,"lib/sun.glsl",new HashSet<String>());
            String relief=(String)expand.invoke(null,"lib/far_relief.glsl",new HashSet<String>());
            String defines=(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active());
            receiver=GlProgram.fromSources("moving actual world receiver",new GlProgram.Sources(
                "#version 330\nout vec2 uv;void main(){uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(uv*2.-1.,0,1);}",null,null,null,
                "#version 450\n"+defines+relief+sun+"\nin vec2 uv;uniform vec2 shift;out vec4 result;void main(){vec3 p=reliefLifted(vec3((uv.x-.5)*24.+shift.x,0,(uv.y-.5)*24.+shift.y));vec3 n=reliefNormal(vec3(0,1,0),p);result=vec4(sunlit(p,n,vec3(0,1.5,0),false),sunAt(p),0,1);}"),Map.of(),Map.of(),List.of());
            int color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,W,W,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            vao=GL30.glGenVertexArrays();
            WorldRenderer.prepare(Long.MAX_VALUE);
            for(int terrain=0;terrain<2;terrain++) {
                Frame frame=NativeSurfaceReceiverProbe.frame();frame.camX=128;frame.camY=128;
                Object floor=NativeSurfaceReceiverProbe.mesh(frame.scene,0,tiledFloor());
                float[] h=new float[96*96];for(int y=0;y<96;y++)for(int x=0;x<96;x++)
                    h[y*96+x]=terrain==0?2f+(x-32)*.025f+(y-32)*.015f:2f+.002f*((x-32)*(x-32)+(y-32)*(y-32));
                FarRelief.Field grid=FarRelief.Field.of(96,96,h,h);
                float[][] retained=new float[STEPS][];
                ModelShadowBasisProbe.clearLayers();
                int differing=0,extra=0,retainedDark=0,freshDark=0;
                for(int path=0;path<2;path++)for(int step=0;step<STEPS;step++) {
                    float dx=step*.15f,dz=step*.08f;
                    frame.camX=128+dx;frame.camY=128+dz;
                    frame.scene.originX=-frame.camX;frame.scene.originZ=-frame.camY;
                    frame.scene.meshOffsets[0]=dx;frame.scene.meshOffsets[2]=dz;
                    frame.scene.relief(new FarRelief.Lift(grid,.5f,grid.metresAt(frame.camX,frame.camY),frame.camX,frame.camY));
                    frame.number++;IsometricBridge.modelRendering(frame,FX);
                    if(path==1)ModelShadowBasisProbe.clearLayers();
                    if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("maps");
                    float[] values=sample(frame,dx,dz);
                    if(path==0)retained[step]=values;
                    else for(int p=0;p<W*W;p++) {
                        float a=retained[step][p*4],b=values[p*4];
                        if(Math.abs(a-b)>.05)differing++;
                        if(a<.5f && b>.5f)extra++;
                        if(a<.5f)retainedDark++;if(b<.5f)freshDark++;
                    }
                }
                System.out.println("Terrain="+terrain+" paths="+2+" steps="+STEPS+" travel=24,12.8 retainedDark="+retainedDark+" freshDark="+freshDark+" different="+differing+" extra="+extra);
                if(differing!=0 || freshDark!=0)throw new AssertionError("World-space moving floor shadows");
                NativeSurfaceReceiverProbe.retire(floor);
            }
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println("Model shadow motion PASS: 644 actual GPU frames, same mesh/field, two retained/fresh complete tracks, changing camera/ground/offsets across cache margins; no false floor shadows. No live map acceptance.");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static float[][] tiledFloor() {
        float[][] points=new float[64*64*6][3];int n=0;
        for(int z=-32;z<32;z++)for(int x=-32;x<32;x++)
            for(float[] p:new float[][]{{x,0,z},{x+1,0,z},{x+1,0,z+1},{x,0,z},{x+1,0,z+1},{x,0,z+1}})points[n++]=p;
        return points;
    }
    static float[] sample(Frame frame,float dx,float dz) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,W,W);GL30.glBindVertexArray(vao);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glColorMask(true,true,true,true);
        receiver.use();receiver.set("shift",dx,dz);receiver.setInt("uShadowCascades",6);receiver.set("uSunDir",frame.scene.sunX,frame.scene.sunY,frame.scene.sunZ);receiver.set("uSunStrength",1f);receiver.set("uShadowsOff",0f);
        for(int i=0;i<2;i++){receiver.set("uShadowMatrix["+i+"]",NativeShadowMaps.matrix(i));GL20.glUniform4f(receiver.location("uShadowMaps["+i+"]"),2*NativeShadowMaps.reach(i)/1024,NativeShadowMaps.depthRange(),1f/1024,0);}
        GL13.glActiveTexture(GL13.GL_TEXTURE0+6);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());GL33.glBindSampler(6,0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer result=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,result);float[] values=new float[W*W*4];result.get(values);return values;
    }
}
