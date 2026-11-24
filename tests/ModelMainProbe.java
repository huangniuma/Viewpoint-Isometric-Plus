import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Controlled full renderer discriminator: actual gbuffer, depth reconstruction and light pass. */
public final class ModelMainProbe {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(256,256,"full model pipeline discriminator",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.select(args.length > 0 ? args[0] : "library",args.length > 0 && args[0].equals("somber") ? "quality" : "high",Map.of("upscaling","off","reflections","off"));
            String lightSource=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,
                new Class<?>[]{String.class,GameAccess.type("viewpoint.platform.ShaderPack"),boolean[].class},"light.frag",ShaderPacks.active().pack,new boolean[1]);
            System.out.println("Actual library light has contact="+lightSource.contains("contactShadowAt(")+" rewritten distance="+lightSource.contains("contactShadowAt(ivec2(gl_FragCoord.xy), length(pos - uEye))"));
            int color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,256,256,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
            int output=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            Frame frame=NativeSurfaceReceiverProbe.frame();frame.camX=128;frame.camY=128;
            Object floor=NativeSurfaceReceiverProbe.mesh(frame.scene,0,ModelShadowMotionProbe.tiledFloor());
            float[][] points=ModelShadowMotionProbe.tiledFloor();float[] verts=new float[points.length*14];
            for(int i=0;i<points.length;i++){System.arraycopy(points[i],0,verts,i*14,3);verts[i*14+7]=verts[i*14+8]=1;verts[i*14+10]=1;}
            var pages=((ChunkMeshData)floor).pages;
            NativeSurfaceReceiverProbe.retire(floor);
            floor=new ChunkMeshData(pages,new int[]{0},new int[]{points.length},verts,0);frame.scene.meshes[0]=(ChunkMeshData)floor;
            frame.scene.meshFlags[0]|=1;frame.scene.meshModelSquares[0]=-1L;frame.scene.meshPlan[0]=-1;
            frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
            frame.scene.sunR=frame.scene.sunG=frame.scene.sunB=1;
            float[] h=new float[96*96];for(int z=0;z<96;z++)for(int x=0;x<96;x++)h[z*96+x]=2+.002f*((x-32)*(x-32)+(z-32)*(z-32));
            FarRelief.Field grid=FarRelief.Field.of(96,96,h,h);
            Matrix4f view=new Matrix4f();float[] eye=new float[3];
            WorldRenderer.debugView=6;
            int total=0,dark=0;
            for(int step=0;step<80;step++) {
                float dx=step*.2f,dz=step*.1f;frame.camX=128+dx;frame.camY=128+dz;
                frame.scene.originX=-frame.camX;frame.scene.originZ=-frame.camY;
                frame.scene.meshOffsets[0]=dx;frame.scene.meshOffsets[2]=dz;
                frame.scene.relief(new FarRelief.Lift(grid,.5f,grid.metresAt(frame.camX,frame.camY),frame.camX,frame.camY));
                frame.number++;frame.scene.cameraCut=step==0;
                IsometricBridge.modelWorldRendering(frame,ModelShadowMotionProbe.FX,true);
                IsoCameraMath.matrices(new IsoCameraMath.Fit(128,128,16),frame.camX,frame.camY,256,256,frame.scene.projection,view,eye,0);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,256,256);GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glClearColor(0,0,0,0);GL11.glColorMask(true,true,true,true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                if(!WorldRenderer.begin(frame.scene,frame.number,1f,view,eye[0],eye[1],eye[2]))throw new AssertionError("begin");
                WorldRenderer.drawTranslucent(frame.scene);WorldRenderer.finish(frame.scene);
                Object renderedTargets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
                int hdr=((Number)GameAccess.field("viewpoint.render.Targets","hdr",renderedTargets)).intValue();
                FloatBuffer actualLight=BufferUtils.createFloatBuffer(256*256*4);
                GL45.glGetTextureImage(hdr,0,GL11.GL_RGBA,GL11.GL_FLOAT,actualLight);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(256*256*4);GL11.glReadPixels(0,0,256,256,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                for(int y=80;y<176;y++)for(int x=80;x<176;x++){int i=(y*256+x)*4;total++;if(actualLight.get(i)<.5f)dark++;}
                if(step%20==0) {
                    Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
                    for(String name:List.of("depth","albedo","normal","hdr","taaInput","antialiased")) {
                        int texture=((Number)GameAccess.field("viewpoint.render.Targets",name,targets)).intValue();
                        if(texture==0)continue;
                        FloatBuffer data=BufferUtils.createFloatBuffer(256*256*4);
                        GL45.glGetTextureImage(texture,0,name.equals("depth")?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,GL11.GL_FLOAT,data);
                        int at=(128*256+128)*(name.equals("depth")?1:4);
                        System.out.println("Full model frame="+step+" "+name+"="+data.get(at)+","+data.get(at+1)+","+data.get(at+2)+" GL="+GL11.glGetError());
                    }
                }
            }
            if(total!=737280 || dark!=0)throw new AssertionError("Unexpected full model sun coverage: "+dark+"/"+total);
            System.out.println("Full model sun GPU PASS: debug="+WorldRenderer.debugView+" frames=80 covered="+total+" falseShadow="+dark+"; actual mesh gbuffer, pulled raster depth, inverse camera, original SurfacePass sun light, moving camera/relief; no native vision or user map acceptance.");
            NativeSurfaceReceiverProbe.retire(floor);
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
}
