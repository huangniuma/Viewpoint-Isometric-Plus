import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.*;
import vpiso.*;

/** Controlled real-map counterexample for native roof art being clipped out of body lighting. */
public final class NativeRoof069CandidateProbe {
  public static void main(String[] args)throws Exception {
    var P=new NativeShadow068Probe();var N=P.N;
    P.pack=args.length==0?"default":args[0];
    if(!GLFW.glfwInit())throw new AssertionError("GLFW");
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
    long window=GLFW.glfwCreateWindow(N.W,N.H,"roof069 consumer discriminator",0,0);
    try {
      GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
      GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
      TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
      N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,P.NATIVE);
      P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
      GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);
      N.overlay.metadata(P.metadata);N.receiver.worldZ=0;
      int ordinary=-1,roof=-1;
      for(int flag:new int[]{0,32768}) {
        var f=P.frame();float[][] points=N.horizontal(0,2.3f);
        N.opaque=flag;Object receiver=N.mesh(f.scene,0,points);P.publish(receiver,true,0);
        N.opaque=0;Object caster=N.mesh(f.scene,1,P.blocker());
        P.draw(f,points,.0007f,new float[]{1,0,0,1},0,true);
        int dark=0;for(int i=0;i<N.W*N.H;i++)if((P.actual[i*4]&255)+3<(P.before[i*4]&255))dark++;
        System.out.println("native foreground, VP vertex flag="+flag+", externally shadowed output pixels="+dark);
        if(flag==0)ordinary=dark;else roof=dark;
        N.retire(caster);N.retire(receiver);
      }
      if(ordinary<20 || roof<20 || Math.abs(roof-ordinary)>5)throw new AssertionError("Roof receiver regression ordinary="+ordinary+" roof="+roof);
      byte[] lowerAtlas=null;
      for(int value:new int[]{100,240}) {
        var f=P.frame();float[][] points=N.horizontal(2.8f,2.3f);
        N.opaque=0;Object receiver=N.mesh(f.scene,0,points);P.publish(receiver,true,0);
        float[][] casterPoints=P.blocker();for(float[] v:casterPoints)v[1]+=2.8f;
        Object caster=N.mesh(f.scene,1,casterPoints);
        Object upstairs=N.mesh(f.scene,2,N.horizontal(-5,.05f));
        GameAccess.set("viewpoint.render.ChunkMeshData","level",upstairs,1);f.scene.meshFlags[2]=0;
        java.nio.ByteBuffer light=(java.nio.ByteBuffer)GameAccess.call("viewpoint.render.ChunkMeshData","spareLight",upstairs,new Class<?>[0]);light.clear();
        for(int i=0;i<800;i++)light.put((byte)(i<400?value:0)).put((byte)(i<400?value:0)).put((byte)(i<400?value:0)).put((byte)255);
        light.flip();GameAccess.call("viewpoint.render.ChunkMeshData","publishLight",upstairs,new Class<?>[]{java.nio.ByteBuffer.class},light);
        P.draw(f,points,.0007f,new float[]{1,0,0,1},0,true);
        if(lowerAtlas==null)lowerAtlas=P.actual.clone();else {
          int difference=P.different(lowerAtlas,P.actual,3);
          System.out.println("fixed level0 tall receiver, only level1 light grid changed: modified output pixels="+difference);
          if(difference!=0)throw new AssertionError("Tall receiver still reads upstairs light");
        }
        N.retire(upstairs);N.retire(caster);N.retire(receiver);
      }
      if(GL11.glGetError()!=0)throw new AssertionError("GL error");
      System.out.println("PASS candidate069: native roof foreground receives external shadow; tall level0 receiver ignores upstairs-only light changes. Controlled GPU fixture, not live-scene proof.");
    }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
  }
}
