import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.FarRelief;
import viewpoint.render.SceneData;
import vpiso.*;

/** Local production flow texture versus the installed VP function and actual SSBO uploader. */
public final class NativeWaterFlow055Probe {
    private static final String VERTEX="""
        #version 330
        void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}
        """;
    private static GlProgram oracle,local;
    private static int framebuffer,target,vao,flowTexture,comparisons;
    private static boolean baseline;
    private static final Class<?>[] APPLY={GlProgram.class,SceneData.class,boolean.class};
    public static void main(String[] args) throws Exception {
        baseline=args.length>0&&args[0].equals("baseline054");
        if(!GLFW.glfwInit())throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(64,64,"water flow oracle 055",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            TestShaderPacks.select("default","high",Map.of("upscaling","off"));viewpoint.platform.Gl.init();
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            framebuffer=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
            target=texture(GL30.GL_RGBA32F,1,1);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);
            flowTexture=texture(GL11.GL_RGBA8,1,1);
            String actual=resource("/viewpoint/shaders/lib/far_relief.glsl");
            String constants=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,new Class<?>[]{String.class},"constants.glsl");
            String header="#version 430\nuniform vec2 probePos,uReliefCamera,uReliefFlowCells;\nuniform vec4 uReliefGrid;\n"+constants;
            String body="\nout vec4 result;void main(){result=uReliefGrid.x>0.0?reliefFlow(uReliefCamera-probePos):vec4(0.0,0.0,2.0,1.0);}\n";
            oracle=program(header+"\nlayout(std430) readonly buffer ReliefFlow {int uReliefFlow[];};\nlayout(std140) uniform ByteValues {vec4 uByteValues[64];};\n"+function(actual,"reliefBytes")+function(actual,"reliefFlow")+body);
            String production=NativeWaterShader.overlayLibrary();
            local=program(header+"\nuniform sampler2D nativeFlow;uniform vec4 nativeFlowGrid;\n"
                    +(baseline?"layout(std430) readonly buffer ReliefFlow {int uReliefFlow[];};\nlayout(std140) uniform ByteValues {vec4 uByteValues[64];};\n"+function(actual,"reliefBytes"):"")
                    +function(production,"reliefFlow")+body);
            FarRelief.Field field=fixture();
            if(baseline){landmarkNegative();return;}
            for(int[] rectangle:new int[][]{{-20,-20,40,40},{0,0,19,19},{112,80,55,49},{235,175,48,40},{-160,-96,192,192},{280,220,24,24}})
                compareRectangle(field,rectangle);
            landmark();nullCases(field);snapshotOwnership(field);overlayAppearance();
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("GL error after flow verification");
            System.out.println("Native water flow 055 GPU PASS: "+comparisons+" official SSBO/local-texture comparisons; clipped and negative bounds, camera/yaw invariance, nearest non-land kinds, reported pond, null/no-lift, owned snapshot and normal water appearance.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static FarRelief.Field fixture(){
        int w=65,h=49,fw=FarRelief.Field.flowCells(w),fh=FarRelief.Field.flowCells(h);byte[] bytes=new byte[fw*fh*4];
        Random random=new Random(550117);
        for(int y=0;y<fh;y++)for(int x=0;x<fw;x++){
            int i=(y*fw+x)*4;bytes[i]=(byte)random.nextInt(256);bytes[i+1]=(byte)random.nextInt(256);
            bytes[i+2]=(byte)(((x+2*y)%5)*60);bytes[i+3]=(byte)random.nextInt(256);
        }
        return new FarRelief.Field(w,h,new short[w*h],new short[w*h],new byte[w*h],bytes,new long[(w*h+63)/64]);
    }
    private static FarRelief.Lift lift(FarRelief.Field field,float cx,float cy){return new FarRelief.Lift(field,0f,0f,cx,cy);}
    private static NativeVPWaterOverlay.Snapshot snapshot(int x,int y,int w,int h,FarRelief.Lift lift){
        var snapshot=new NativeVPWaterOverlay.Snapshot();float[] rgba=new float[w*h*4];
        for(int i=0;i<w*h;i++){rgba[i*4]=1;rgba[i*4+2]=5;rgba[i*4+3]=1;}
        snapshot.fit(x,y,w,h,0,0,0,rgba);snapshot.captureFlow(lift);return snapshot;
    }
    private static void compareRectangle(FarRelief.Field field,int[] rect){
        var source=lift(field,9876,4321);var snapshot=snapshot(rect[0],rect[1],rect[2],rect[3],source);
        float[][] points={{rect[0],rect[1]},{rect[0]+.125f,rect[1]+.375f},{rect[0]+rect[2]-.125f,rect[1]+rect[3]-.25f},
                {rect[0]+rect[2]*.5f,rect[1]+rect[3]*.5f},{rect[0]+.999f,rect[1]+.001f}};
        for(int y=rect[1];y<=rect[1]+rect[3];y+=4)for(int x=rect[0];x<=rect[0]+rect[2];x+=4)
            compare(source,snapshot,x+.5f,y+.5f,17.25f,-11.5f);
        for(float[] point:points)for(float yaw:new float[]{0,45,90,180,270})for(float[] camera:new float[][]{{133.25f,77.75f},{-63.75f,211.5f}}){
            org.joml.Matrix4f projection=new org.joml.Matrix4f(),view=new org.joml.Matrix4f();
            IsoCameraMath.matrices(new IsoCameraMath.Fit(point[0],point[1],20f),camera[0],camera[1],256,192,projection,view,new float[3],yaw);
            var rotation=new RotationCamera.Snapshot(projection,view,camera[0],camera[1],0,256,192,1f,yaw);
            float[] screen=new float[2];double[] world=new double[2];
            if(!rotation.project(point[0],point[1],0,screen)||!rotation.ground(screen[0],screen[1],0,world)
                    ||Math.abs(world[0]-point[0])>.006||Math.abs(world[1]-point[1])>.006)
                throw new AssertionError("Production rotated-camera world point changed: "+yaw+" "+Arrays.toString(point)+" -> "+Arrays.toString(world));
            compare(source,snapshot,(float)world[0],(float)world[1],camera[0],camera[1]);
        }
    }
    private static void compare(FarRelief.Lift source,NativeVPWaterOverlay.Snapshot snapshot,float x,float y,float cx,float cy){
        float[] expected=sampleOracle(source,x,y,cx,cy),actual=sampleLocal(snapshot,x,y,cx,cy);
        for(int i=0;i<4;i++)if(Math.abs(expected[i]-actual[i])>2e-5f)
            throw new AssertionError("Flow mismatch at "+x+","+y+" component="+i+" official="+Arrays.toString(expected)+" local="+Arrays.toString(actual));
        comparisons++;
    }
    private static float[] sampleOracle(FarRelief.Lift source,float x,float y,float cx,float cy){
        SceneData scene=new SceneData();scene.relief(source==null?null:lift(source.grid(),cx,cy));
        oracle.use();GameAccess.call("viewpoint.render.FarRelief","apply",null,APPLY,oracle,scene,false);
        int index=GL43.glGetProgramResourceIndex(oracle.id(),GL43.GL_SHADER_STORAGE_BLOCK,"ReliefFlow");
        if(index!=GL43.GL_INVALID_INDEX)GL43.glShaderStorageBlockBinding(oracle.id(),index,13);
        index=GL31.glGetUniformBlockIndex(oracle.id(),"ByteValues");if(index!=GL31.GL_INVALID_INDEX)GL31.glUniformBlockBinding(oracle.id(),index,3);
        oracle.set("uReliefCamera",cx,cy);oracle.set("probePos",cx-x,cy-y);return sample();
    }
    private static float[] sampleLocal(NativeVPWaterOverlay.Snapshot snapshot,float x,float y,float cx,float cy){
        int w=integer(snapshot,"flowWidth"),h=integer(snapshot,"flowHeight");
        ByteBuffer bytes=(ByteBuffer)GameAccess.call("vpiso.NativeVPWaterOverlay$Snapshot","flowPixels",snapshot,new Class<?>[0]);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+9);GL11.glBindTexture(GL11.GL_TEXTURE_2D,flowTexture);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        local.use();local.setInt("nativeFlow",9);local.set("nativeFlowGrid",(float)integer(snapshot,"flowX"),(float)integer(snapshot,"flowY"),(float)w,(float)h);
        local.set("uReliefFlowCells",(float)integer(snapshot,"flowCellsX"),(float)integer(snapshot,"flowCellsY"));
        local.set("uReliefGrid",(Boolean)field(snapshot,"hasFlow")?1f:0f,0f,0f,0f);local.set("uReliefCamera",cx,cy);local.set("probePos",cx-x,cy-y);
        return sample();
    }
    private static float[] sample(){
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);GL11.glViewport(0,0,1,1);GL30.glBindVertexArray(vao);
        GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true,true,true,true);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer values=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,values);float[] result=new float[4];values.get(result);return result;
    }
    private static FarRelief.Field pond(){
        // Actual shipped/cache flow cells at world (8160,11712), (8176,11712), (8160,11728), (8176,11728), translated by a whole coarse-cell origin.
        return new FarRelief.Field(5,5,new short[25],new short[25],new byte[25],new byte[]{(byte)128,(byte)128,0,0,(byte)128,(byte)128,60,(byte)175,(byte)128,(byte)128,0,0,(byte)128,(byte)128,60,(byte)143},new long[1]);
    }
    private static void landmark(){
        var source=lift(pond(),0,0);var snapshot=snapshot(0,0,16,16,source);
        compare(source,snapshot,16,16,111,222);float[] result=sampleLocal(snapshot,16,16,0,0);
        if(result[2]!=1)throw new AssertionError("Reported pond was not kind1: "+Arrays.toString(result));
        System.out.println("Reported location pond GPU PASS: actual data kind1 retained, previous forced lake kind2 rejected.");
    }
    private static void landmarkNegative(){
        float[] expected=sampleOracle(lift(pond(),0,0),16,16,0,0);
        local.use();local.set("uReliefGrid",0f,0f,0f,0f);local.set("probePos",-16f,-16f);local.set("uReliefCamera",0f,0f);float[] old=sample();
        if(expected[2]!=1||old[2]!=2)throw new AssertionError("Pinned054 did not reproduce pond/lake mismatch");
        System.out.println("Pinned054 water body negative control PASS: actual pond kind1 versus production fallback lake kind2.");
    }
    private static void nullCases(FarRelief.Field field){
        var missing=snapshot(-4,-4,8,8,null);compare(null,missing,0,0,0,0);
        var empty=new FarRelief.Field(field.width(),field.height(),field.ground(),field.water(),field.walls(),null,field.wet());
        var source=lift(empty,0,0);var data=snapshot(-4,-4,8,8,source);compare(source,data,-20,-50,15,25);
        if(sampleLocal(missing,0,0,0,0)[2]!=2||sampleLocal(data,0,0,0,0)[2]!=0)throw new AssertionError("Missing field/null flow distinction lost");
    }
    private static void snapshotOwnership(FarRelief.Field field){
        var source=lift(field,0,0);var snapshot=snapshot(112,80,55,49,source);
        byte[] captured=((byte[])field(snapshot,"flowData")).clone();byte[] original=field.flow().clone();
        Arrays.fill(field.flow(),(byte)0);if(!Arrays.equals(captured,(byte[])field(snapshot,"flowData")))throw new AssertionError("Snapshot retained mutable source flow");
        System.arraycopy(original,0,field.flow(),0,original.length);
        int bytes=integer(snapshot,"flowWidth")*integer(snapshot,"flowHeight")*4;
        if(bytes>=field.flow().length)throw new AssertionError("Expected a clipped local copy");
    }
    private static void overlayAppearance(){
        Frame frame=new Frame();frame.scene.cloudTime=30f;frame.scene.windX=2.4f;frame.scene.windZ=.8f;
        frame.scene.sunTrueY=frame.scene.sunY=frame.scene.skySunY=1;frame.scene.sunR=frame.scene.sunG=1;frame.scene.sunB=.95f;frame.scene.sunStrength=frame.scene.skyGlow=1;frame.scene.nightLight=.6666667f;
        GameAccess.call("viewpoint.environment.Atmosphere","skyColours",null,new Class<?>[]{SceneData.class,float.class,float.class,float.class},frame.scene,1f,0f,0f);
        GameAccess.call("viewpoint.environment.Atmosphere","airLight",null,new Class<?>[]{SceneData.class},frame.scene);
        GameAccess.set("viewpoint.light.Daylight","fromAir",null,false);GameAccess.set("viewpoint.light.Daylight","level",null,0xCFBFAB);viewpoint.light.Daylight.snapshot(frame.scene);
        var receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.5,.5},{.625,.5,.5},{.5,.625,.5},{.5,.5,.6}});
        var water=snapshot(-4,-4,8,8,null);var overlay=new NativeVPWaterOverlay();
        int color=texture(GL11.GL_RGBA8,32,32),depth=GL30.glGenRenderbuffers(),fb=GL30.glGenFramebuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,32,32);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fb);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
        IsometricBridge.rendering(frame);draw(overlay,frame,receiver,water,fb);frame.scene.cloudTime=35f;draw(overlay,frame,receiver,water,fb);
        byte[] lake=draw(overlay,frame,receiver,water,fb);
        water.captureFlow(lift(uniformBody(1),0,0));byte[] pond=draw(overlay,frame,receiver,water,fb);
        water.captureFlow(lift(uniformBody(2),0,0));byte[] lakeData=draw(overlay,frame,receiver,water,fb);
        if(!Arrays.equals(lake,lakeData))throw new AssertionError("Real lake data does not match VP no-field lake fallback");
        int changed=0;for(int i=0;i<lake.length;i++)changed+=Math.abs((lake[i]&255)-(pond[i]&255));if(changed<100)throw new AssertionError("Actual water appearance ignores captured kind: "+changed);
        if(frame.scene.relief!=null)throw new AssertionError("Optical flow changed native terrain");
        System.out.println("Normal production water kind GPU PASS: pond versus lake pixel difference="+changed+"; models absent, flat native terrain, lake fallback unchanged.");
    }
    private static FarRelief.Field uniformBody(int kind){return new FarRelief.Field(1,1,new short[1],new short[1],new byte[1],new byte[]{(byte)128,(byte)128,(byte)(kind*60),(byte)255},new long[1]);}
    private static byte[] draw(NativeVPWaterOverlay overlay,Frame frame,NativeReceiver receiver,NativeVPWaterOverlay.Snapshot water,int fb){
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fb);GL11.glViewport(0,0,32,32);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        GL11.glClearColor(80f/255,120f/255,160f/255,.5f);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        overlay.draw(frame,receiver,water,true);ByteBuffer bytes=BufferUtils.createByteBuffer(32*32*4);GL11.glReadPixels(0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);byte[] result=new byte[bytes.remaining()];bytes.get(result);return result;
    }
    private static Object field(Object object,String name){return GameAccess.field("vpiso.NativeVPWaterOverlay$Snapshot",name,object);}
    private static int integer(Object object,String name){return ((Number)field(object,name)).intValue();}
    private static GlProgram program(String fragment){return GlProgram.fromSources("055 flow oracle",new GlProgram.Sources(VERTEX,null,null,null,fragment),Map.of(),Map.of(),List.of("actual installed VP flow"));}
    private static int texture(int format,int w,int h){int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return id;}
    private static String resource(String path)throws Exception{try(InputStream in=GlProgram.class.getResourceAsStream(path)){if(in==null)throw new AssertionError(path);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private static String function(String source,String name){int nameAt=source.indexOf(" "+name+"("),start=source.lastIndexOf('\n',nameAt)+1,open=source.indexOf('{',nameAt),depth=1,end=open+1;if(nameAt<0||open<0)throw new AssertionError("Missing "+name);for(;depth>0;end++){char c=source.charAt(end);if(c=='{')depth++;else if(c=='}')depth--;}return source.substring(start,end)+"\n";}
}
