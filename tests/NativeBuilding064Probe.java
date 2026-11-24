import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** Native and model captures must cast the same physical canopy onto the same ground. */
public final class NativeBuilding064Probe {
    static final int W=ModelRoomOcclusionProbe.W;
    static long number;
    public static void main(String[] args)throws Exception {
        boolean baseline=Arrays.asList(args).contains("baseline063");
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"native canopy physical parity064",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));
            ModelRoomOcclusionProbe.setup();
            ModelBakedRoofOcclusionProbe.ceilingPut=GameAccess.type("viewpoint.world.CeilingFaces").getDeclaredMethod("put",float[].class,int.class,float.class,float.class,float.class,int.class,boolean.class,int.class,int.class);
            ModelBakedRoofOcclusionProbe.ceilingPut.setAccessible(true);
            ModelBakedRoofOcclusionProbe.quadPack=GameAccess.type("viewpoint.platform.Quads").getDeclaredMethod("pack",float[].class,int.class,int.class);
            ModelBakedRoofOcclusionProbe.quadPack.setAccessible(true);
            int cases=0;
            for(String kind:new String[]{"ceiling-down","ceiling-up","floor-page"})for(int direction=0;direction<4;direction++) {
                Frame frame=NativeSurfaceReceiverProbe.frame();
                frame.scene.sunX=direction==0?-.6f:direction==1?.6f:0;
                frame.scene.sunZ=direction==2?-.6f:direction==3?.6f:0;
                frame.scene.sunY=.8f;frame.scene.sunStrength=direction<2?1f:.25f;
                // A distant ordinary page keeps the scene and both capture paths identical.
                float[][] distant=NativeSurfaceReceiverProbe.horizontal(0,.1f);
                for(float[] p:distant){p[0]+=30;p[2]+=30;}
                Object ordinary=NativeSurfaceReceiverProbe.mesh(frame.scene,0,distant);
                Object roof=ModelBakedRoofOcclusionProbe.roof(frame,kind,false);
                float[] model=render(frame,true),nativeView=render(frame,false);
                int mismatches=ModelRoomOcclusionProbe.mismatch(model,nativeView);
                if(ModelRoomOcclusionProbe.dark(model)!=W*W)throw new AssertionError("Model reference canopy absent");
                if(baseline) {
                    if(mismatches<W*W/2)throw new AssertionError("Pinned063 missing native slab not reproduced: "+mismatches);
                }else if(mismatches!=0)throw new AssertionError("Native/model physical canopy mismatch="+mismatches);
                NativeSurfaceReceiverProbe.retire(roof);
                roof=ModelBakedRoofOcclusionProbe.roof(frame,kind,true);
                float[] modelHole=render(frame,true),nativeHole=render(frame,false);
                int hole=ModelRoomOcclusionProbe.bright(modelHole);
                if(hole<100)throw new AssertionError("Physical opening control blocked");
                if(!baseline && ModelRoomOcclusionProbe.mismatch(modelHole,nativeHole)!=0)throw new AssertionError("Native aperture footprint differs from3D");
                ModelBakedRoofOcclusionProbe.checkMetadata(roof,kind);
                NativeSurfaceReceiverProbe.retire(roof);NativeSurfaceReceiverProbe.retire(ordinary);cases++;
                System.out.println("Canopy064 "+kind+" direction="+direction+" native/model mismatch="+mismatches+" aperture="+hole);
            }
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println((baseline?"Pinned063 native canopy negative PASS: ":"Native building064 GPU PASS: ")+cases+" actual cold floor/ceiling pages, four sun/moon directions, matched3D shadow reference, physical apertures and unchanged page metadata; controlled fixture, no live map.");
        }finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }

    static float[] render(Frame frame,boolean model)throws Exception {
        frame.number=++number;frame.scene.cameraCut=true;
        if(model)IsometricBridge.modelRendering(frame,ModelRoomOcclusionProbe.EFFECTS);
        else IsometricBridge.rendering(frame);
        if(GameAccess.field("viewpoint.render.WorldRenderer","shadows",null)!=null)ModelShadowBasisProbe.clearLayers();
        if(!NativeShadowMaps.draw(frame.scene,1024,2))throw new AssertionError("Maps unavailable");
        // Compare the physical maps with one identical consumer; model colour
        // shading has separate artistic strength/bias uniforms outside this test.
        IsometricBridge.rendering(null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,ModelRoomOcclusionProbe.fbo);GL11.glViewport(0,0,W,W);GL30.glBindVertexArray(ModelRoomOcclusionProbe.vao);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glColorMask(true,true,true,true);
        GlProgram shader=ModelRoomOcclusionProbe.shader;
        shader.use();shader.setInt("uShadowCascades",6);shader.set("uSunDir",frame.scene.sunX,frame.scene.sunY,frame.scene.sunZ);shader.set("uSunColor",1f,1f,1f);shader.set("uFogColor",.6f,.7f,.8f);shader.set("uSunStrength",frame.scene.sunStrength);shader.set("uShadowsOff",0f);
        for(int i=0;i<2;i++) {
            shader.set("uShadowMatrix["+i+"]",NativeShadowMaps.matrix(i));
            GL20.glUniform4f(shader.location("uShadowMaps["+i+"]"),2f*NativeShadowMaps.reach(i)/1024,NativeShadowMaps.depthRange(),1f/1024,0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0+6);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());GL33.glBindSampler(6,0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        float[] result=new float[W*W*4];pixels.get(result);return result;
    }
}
