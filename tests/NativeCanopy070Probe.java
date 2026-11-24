import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import vpiso.*;

/** A roofed yard has open-sky alpha zero but no native room: actual VP maps and compositor. */
public final class NativeCanopy070Probe {
    static final NativeShadow068Probe P=new NativeShadow068Probe();
    static final NativeSurfaceReceiverProbe N=P.N;
    static boolean baseline;
    static NativeRelightMask regions;
    static byte[] exterior;
    public static void main(String[] args)throws Exception {
        P.pack=args.length==0?"default":args[0];baseline=Arrays.asList(args).contains("baseline069");
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"canopy070 room discriminator",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,P.NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);
            N.overlay.metadata(P.metadata);N.receiver.worldZ=0;
            regions=new NativeRelightMask();regions.x=regions.y=-32;regions.z=0;
            try{exterior=(byte[])NativeRelightMask.class.getField("exterior").get(regions);}
            catch(NoSuchFieldException old){N.require(baseline,"Current room mask absent");}
            N.overlay.relight(0,0,regions); // exercise the real non-rebase fallback, including non-PZO
            P.oracleVao=GL30.glGenVertexArrays();P.oracleVbo=GL15.glGenBuffers();P.expected=oracle();
            Frame f=P.frame();float[][] floor=N.horizontal(0,2.3f);
            Object page=N.mesh(f.scene,0,floor);P.publish(page,false,60);Arrays.fill(f.scene.exposure,(byte)0);
            Object canopy=N.mesh(f.scene,1,P.blocker());
            if(exterior!=null)Arrays.fill(exterior,(byte)255);
            P.draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);
            byte[] roofed=P.actual.clone();int shadow=dark(P.before,roofed);
            byte[] expected=P.reference(f,floor,false,.75f,0,true);
            int mismatches=P.different(expected,roofed,3);
            if(baseline) {
                N.require(shadow==0 && mismatches>20,"069 roofed-yard rejection not reproduced");
                System.out.println("Pinned069 roofed-yard negative PASS: missing solar pixels="+mismatches);
                return;
            }
            N.require(shadow>20 && mismatches==0,"Roofed yard clipped: shadows="+shadow+" reference mismatches="+mismatches);
            // G=room; R stays zero throughout: solar eligibility cannot reveal native sight.
            Arrays.fill(exterior,(byte)0);P.draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);
            P.identity("known enclosed room preserves native illumination");
            Arrays.fill(exterior,(byte)255);regions.z=1;
            P.draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);P.identity("other floor cannot open solar gate");
            regions.z=0;regions.x=1000;
            P.draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);P.identity("unknown region stays protected");
            regions.x=-32;P.draw(f,floor,.0007f,new float[]{0,0,0,1},1,false);P.identity("all effects off");
            // Ordinary open sky retains the same selected shadow, regardless of room mask availability.
            N.overlay.relight(0,0,null);P.publish(page,true,0);Arrays.fill(f.scene.exposure,(byte)255);
            P.draw(f,floor,.0007f,new float[]{0,0,0,1},1,true);N.require(dark(P.before,P.actual)>20,"Open sky regressed");
            N.retire(canopy);N.retire(page);
            N.overlay.relight(0,0,regions);
            for(float height:new float[]{.8f,2.8f}) {
                f=P.frame();float[][] points=N.horizontal(height,2.3f);
                page=N.mesh(f.scene,0,points);P.publish(page,false,60);Arrays.fill(f.scene.exposure,(byte)0);
                float[][] blocker=P.blocker();for(float[] point:blocker)point[1]+=height;
                canopy=N.mesh(f.scene,1,blocker);
                Arrays.fill(exterior,(byte)255);
                P.draw(f,points,.0007f,new float[]{1,0,0,1},0,true);
                expected=P.reference(f,points,false,.75f,0,true);
                N.require(dark(P.before,P.actual)>20,"Roofed foreground lost external shadow height="+height);
                N.require(P.different(expected,P.actual,3)==0,"Roofed foreground reference mismatch height="+height);
                Arrays.fill(exterior,(byte)0);P.draw(f,points,.0007f,new float[]{1,0,0,1},0,true);
                P.identity("known room foreground preserves native illumination");
                N.retire(canopy);N.retire(page);
            }
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println("Native canopy070 GPU PASS pack="+P.pack+" shadowPixels="+shadow+" selectedReferenceMismatches=0; actual VP maps and production fallback compositor, roofed roomless yard, enclosed room, owner floor, unknown region, off and ordinary open sky; native alpha/depth/stencil preserved.");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static int dark(byte[] before,byte[] after) {
        int count=0;for(int i=0;i<N.W*N.H;i++)if((after[i*4]&255)+3<(before[i*4]&255))count++;return count;
    }
    static GlProgram oracle() {
        String shade=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,new Class<?>[]{String.class,ShaderPack.class,boolean[].class},"lib/shade.glsl",ShaderPacks.active().pack,new boolean[1]);
        Matcher signature=Pattern.compile("\\bvec3\\s+shadeShared\\s*\\(([^)]*)\\)").matcher(shade);
        int args=signature.find()?signature.group(1).split(",").length:0;
        String selected=args==7?"shadeShared(vec3(1),light,knownSkyShare,n,lit,vec4(n,1),vec3(0))"
            :args==6?"shadeShared(vec3(1),light,knownSkyShare,n,lit,vec4(n,1))":"shade(vec3(1),light,n,lit)";
        String base="""
            #version 330
            in vec3 physical;
            out vec4 outputColor;
            uniform float knownOutdoors,knownSkyShare,knownSun;
            uniform vec3 knownGrid;
            vec3 selected(float lit) {
              vec3 n=vec3(0,1,0);vec4 light=vec4(knownGrid,knownOutdoors);
              return SELECTED;
            }
            void main() {
              vec3 n=vec3(0,1,0);
              // The declared native receiver is a roomless canopy; light.a stays0.
              float clear=knownSun>0.0?smoothstep(0.0,.3,dot(n,uSunDir))*cloudShadow(physical):0.0;
              float shadowed=knownSun>0.0?sunlit(physical,n,vec3(0,10000,0),false):0.0;
              vec3 a=selected(clear),b=selected(shadowed),ratio=vec3(1);
              for(int c=0;c<3;c++)if(a[c]>1e-6)ratio[c]=max(b[c]/a[c],0.0);
              outputColor=vec4(vec3(.7,.55,.4)*ratio,.8);
            }
            """.replace("SELECTED",selected);
        return GlProgram.fromSources("070 independent roofed-yard shade",new GlProgram.Sources(P.ORACLE_VERTEX,null,null,null,NativeLighting.fragment(base)),Map.of(),Map.of(),List.of("070 actual receiver discriminator"));
    }
}
