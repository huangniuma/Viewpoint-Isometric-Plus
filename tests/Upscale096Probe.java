package viewpoint.render;

import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.upscale.*;
import vpiso.*;

/** Real vendor input shaders, woven camera/temporal hooks and native GL/Vulkan FSR dispatch. */
public final class Upscale096Probe {
    static final Frame frame = new Frame();
    static FrameContext c;
    static Targets targets;
    static VendorInputs inputs;
    static int checks, dispatches;
    static boolean negative;

    public static void main(String[] args) throws Exception {
        negative = args.length > 0 && args[0].equals("baseline095");
        check(GLFW.glfwInit(), "GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        long window = GLFW.glfwCreateWindow(32,32,"upscale096 isolated GPU",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            c = (FrameContext) GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
            targets = new Targets();
            GameAccess.set("viewpoint.render.WorldRenderer","targets",null,targets);
            Main.compatible = true;
            inputs = new VendorInputs(targets);
            configure("default","fsr3",67,192,128);
            c.jitterX=.25f; c.jitterY=-.375f; c.frameSeconds=1f/60;
            IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);
            UpscaleFrame f = inputs.describe(c,true);
            if (args.length>0 && args[0].equals("vision095")) { vision(); return; }
            if (negative) {
                check(IsometricBridge.requestedBlockReason().contains("upscaling"), "095 guard not reproduced");
                near(f.near(),.05f,0); near(f.far(),400,0);
                System.out.println("Previous095 upscaler negative PASS: selected FSR blocked and vendor near/far remain .05/400.");
                return;
            }
            cameraScope();
            temporal();
            vendorInputs();
            vision();
            nativeFsr();
            check(GL11.glGetError()==0,"GL error after fixtures");
            System.out.println("Upscale096 checks PASS: checks="+checks+" nativeFsrDispatches="+dispatches+"; isolated GPU/native SDK fixtures, no live game or XeSS/DLSS native run.");
        } finally {
            RotationLayers.end(null); IsometricBridge.rendering(null);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    static void configure(String pack,String mode,int scale,int w,int h) {
        ShaderPacks.useBuiltIn(pack,pack.equals("somber")?"quality":"high",
                Map.of("upscaling",mode,"renderScale",Integer.toString(scale),"reflections","off"));
        c.scene=frame.scene; c.pipeline=ShaderPacks.active().pipeline;
        c.width=Math.max(1,Math.round(w*scale/100f)); c.height=Math.max(1,Math.round(h*scale/100f));
        c.outputWidth=w; c.outputHeight=h; c.upscaled=!mode.equals("off");
        float[] eye=new float[3]; Matrix4f view=new Matrix4f();
        IsoCameraMath.matrices(new IsoCameraMath.Fit(0,0,8),0,0,w,h,frame.scene.projection,view,eye,73f);
        c.look(view,eye[0],eye[1],eye[2]);
        frame.scene.projection.mul(view,c.plainViewProjection); c.invPlainViewProjection.set(c.plainViewProjection).invert();
        c.viewProjection.set(c.plainViewProjection); c.invViewProjection.set(c.invPlainViewProjection);
        c.jitteredProjection.set(frame.scene.projection); c.reprojection.set(c.plainViewProjection);
    }

    static void cameraScope() {
        for (int scope=0;scope<5;scope++) {
            Main.compatible=scope!=4;
            if(scope==0) IsometricBridge.rendering(null);
            if(scope==1) IsometricBridge.rendering(frame);
            if(scope==2) IsometricBridge.modelWorldRendering(new Frame(),IsometricEffects.DEFAULT,true);
            if(scope>=3) IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);
            UpscaleFrame f=inputs.describe(c,true);
            near(f.near(),scope==3?IsoCameraMath.NEAR:.05f,0); near(f.far(),scope==3?IsoCameraMath.FAR:400f,0);
            near(f.jitterX(),-.25f,0); near(f.jitterY(),.375f,0);
            near(f.motionScaleX(),c.width,0); near(f.motionScaleY(),c.height,0);
            near(f.fovY(),2f*(float)Math.atan(1/frame.scene.projection.m11()),.000001f);
            check(f.reset()&&f.matrices().length==64&&f.camera().length==12,"Vendor record data lost");
            near(f.sharpness(),FrameEnd.fsrSharpness(Graphics.upscaleSharpness()),0);
        }
        Main.compatible=true;
        System.out.println("Upscale096 vendor camera scope PASS: owned range, jitter sign, FOV, time, sharpness, motion sizes, arrays and stock/foreign/native passthrough.");
    }

    static void temporal() {
        Object clock=GameAccess.field("viewpoint.render.WorldRenderer","clock",null);
        GameAccess.set("viewpoint.render.WorldRenderer","clock",null,(java.util.function.LongSupplier)()->1_000_000_000L);
        try {
            for(String pack:List.of("default","library","vivid","somber"))
            for(String mode:List.of("fsr3","xess","dlss"))
            for(int scale:new int[]{33,50,67,100})
            for(boolean rotate:new boolean[]{false,true}) {
                configure(pack,mode,scale,257,143);
                TemporalPass t=new TemporalPass(targets);
                IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);
                RotationLayers.begin(rotate?new RotationCamera.Snapshot(frame.scene.projection,c.view,0,0,0,257,143,1f,73f):null);
                for(int i=0;i<4;i++) {
                    frame.scene.cameraCut=i==0;
                    GameAccess.set("viewpoint.render.TemporalPass","lastFrameNanos",t,1_000_000_000L);
                    t.begin(c);
                    check(c.jitterX!=0||c.jitterY!=0,"Vendor lost temporal jitter: "+mode+" "+rotate);
                    near(c.jitteredProjection.m20()-frame.scene.projection.m20(),2*c.jitterX/c.width,.0000001f);
                    near(c.jitteredProjection.m21()-frame.scene.projection.m21(),2*c.jitterY/c.height,.0000001f);
                    UpscaleFrame f=inputs.describe(c,!c.previousValid);
                    near(f.near(),IsoCameraMath.NEAR,0); near(f.jitterX(),-c.jitterX,0);
                    check(f.reset()==(i==0),"Vendor camera-cut/reset lifecycle");
                    t.remember(c); t.historyValid=true;
                }
                frame.scene.cameraCut=true;t.begin(c);check(!c.previousValid,"Cut did not invalidate history");
                frame.scene.cameraCut=false; RotationLayers.end(null);
            }
        } finally {GameAccess.set("viewpoint.render.WorldRenderer","clock",null,clock); RotationLayers.end(null);}
        System.out.println("Upscale096 temporal PASS: 384 pack/vendor/scale/rotation cases, actual woven begin/remember, nonzero jitter and camera-cut reset. XeSS/DLSS input ABI only.");
    }

    static void vendorInputs() {
        configure("default","fsr3",50,192,128);
        IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);
        int depth=texture(GL30.GL_R32F,GL11.GL_RED,c.width,c.height,null);
        int motion=texture(GL30.GL_RG32F,GL30.GL_RG,c.width,c.height,null);
        int fbo=VendorInputs.framebuffer(depth,motion,"096 input fixture");
        targets.depth=texture(GL30.GL_R32F,GL11.GL_RED,c.width,c.height,null);
        targets.farDepth=texture(GL30.GL_R32F,GL11.GL_RED,1,1,new float[]{1});
        targets.velocity=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,c.width,c.height,null);
        for(float d:new float[]{.001f,.15f,.5f,.9f,.999f})
        for(int move=0;move<3;move++) {
            upload(targets.depth,c.width,c.height,GL11.GL_RED,1,new float[]{d});
            upload(targets.velocity,c.width,c.height,GL11.GL_RGBA,4,move==1?new float[]{.04f,-.02f,0,1}:new float[4]);
            c.reprojection.set(c.plainViewProjection); if(move==2)c.reprojection.m30(c.reprojection.m30()+.2f);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glColorMask(true,true,true,true);
            inputs.draw(c,fbo);
            float[] depths=readTexture(depth,c.width*c.height,GL11.GL_RED),vectors=readTexture(motion,c.width*c.height*2,GL30.GL_RG);
            int x=c.width/2,y=c.height/2,at=y*c.width+x;
            near(depths[at],d,.00001f);
            if(move<2) {near(vectors[at*2],move==1?-.04f:0,.00001f);near(vectors[at*2+1],move==1?.02f:0,.00001f);}
            else {
                float u=(x+.5f)/c.width,v=(y+.5f)/c.height;
                Vector4f p=new Vector4f(u*2-1,v*2-1,d*2-1,1).mul(c.invPlainViewProjection);p.div(p.w).mul(c.reprojection);
                near(vectors[at*2],p.x/p.w*.5f+.5f-u,.0001f);
            }
        }
        GL30.glDeleteFramebuffers(fbo);
        System.out.println("Upscale096 GPU inputs PASS: actual vendor shader projected-depth roundtrip, marked-object motion and analytical camera reprojection.");
    }

    static void vision() {
        int p=link(NativeVision.VERTEX,"#version 330\nuniform vec2 size,jitter;void main(){vec2 uv=(gl_FragCoord.xy+jitter)/size;gl_FragDepth=.3+.05*uv.x+.03*uv.y;}");
        NativeVision vision=new NativeVision();
        for(int scale:new int[]{33,50,67,100}) for(boolean rotate:new boolean[]{false,true}) {
            configure("default","fsr3",scale,257,143);
            c.jitterX=.375f;c.jitterY=-.25f;
            c.jitteredProjection.set(frame.scene.projection).m20(frame.scene.projection.m20()+2*c.jitterX/c.width).m21(frame.scene.projection.m21()+2*c.jitterY/c.height);
            c.jitteredProjection.mul(c.view,c.viewProjection);c.invViewProjection.set(c.viewProjection).invert();
            targets.depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,c.width,c.height,null);
            int src=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,src);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,targets.depth,0);GL11.glDrawBuffer(GL11.GL_NONE);GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glViewport(0,0,c.width,c.height);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_ALWAYS);GL11.glDepthMask(true);
            GL20.glUseProgram(p);GL20.glUniform2f(GL20.glGetUniformLocation(p,"size"),c.width,c.height);GL20.glUniform2f(GL20.glGetUniformLocation(p,"jitter"),c.jitterX,c.jitterY);Gl.screenTriangle();
            int ow=c.outputWidth+16,oh=c.outputHeight+20, dst=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,ow,oh,null);
            int out=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,out);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,dst,0);GL11.glDrawBuffer(GL11.GL_NONE);GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);c.outputDrawFbo=out;c.viewport[0]=7;c.viewport[1]=9;c.viewport[2]=c.outputWidth;c.viewport[3]=c.outputHeight;
            float[] coefficients=(float[])GameAccess.field("vpiso.NativeVision","depth",vision);System.arraycopy(new float[]{.4f,.003f,-.002f,.01f},0,coefficients,0,4);
            vision.rotation=rotate?new RotationCamera.Snapshot(frame.scene.projection,c.view,0,0,0,c.outputWidth,c.outputHeight,1,73):null;
            vision.writeDepth();
            float[] depths=readTexture(dst,ow*oh,GL11.GL_DEPTH_COMPONENT);near(depths[0],1,0);
            for(int y=16;y<c.outputHeight-16;y+=11)for(int x=16;x<c.outputWidth-16;x+=13) {
                float u=(x+.5f)/c.outputWidth,v=(y+.5f)/c.outputHeight,d=.3f+.05f*u+.03f*v,expected=d;
                if(!rotate){Vector4f point=new Vector4f(u*2-1,v*2-1,d*2-1,1).mul(c.invPlainViewProjection);point.div(point.w);expected=.4f-.003f*point.x+.002f*point.z+.01f*point.y/2.4494896f;}
                near(depths[(y+9)*ow+x+7],expected,.00006f);
            }
            GL30.glDeleteFramebuffers(src);GL30.glDeleteFramebuffers(out);GL11.glDeleteTextures(dst);GL11.glDeleteTextures(targets.depth);
        }
        GL11.glColorMask(true,true,true,true);GL11.glDisable(GL11.GL_DEPTH_TEST);
        System.out.println("Upscale096 GPU visibility PASS: 33/50/67/100 input scales, plain native/rotated depth, jitter removal, asymmetric output and offset viewport.");
    }

    static void nativeFsr() {
        String unavailable=Upscaling.Kind.FSR3.unavailable();check(unavailable==null,"Native FSR unavailable: "+unavailable);
        UpscalePass pass=new UpscalePass(inputs);
        try {
            for(int[] size:new int[][]{{192,128},{257,143},{1920,1080},{3440,1440}})
            for(int scale:size[0]>300?new int[]{50,67}:new int[]{33,50,67,100}) {
                configure("default","fsr3",scale,size[0],size[1]);
                Upscaling vendor=pass.fit(c.pipeline,c.outputWidth,c.outputHeight);check(vendor!=null,"FSR fit failed");
                check(vendor.frameWidth==c.width&&vendor.frameHeight==c.height,"Actual vendor input sizing");
                check(pass.fit(c.pipeline,c.outputWidth,c.outputHeight)==vendor,"Unchanged vendor recreated");
                targets.depth=texture(GL30.GL_R32F,GL11.GL_RED,c.width,c.height,null);
                targets.velocity=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,c.width,c.height,null);
                targets.farDepth=texture(GL30.GL_R32F,GL11.GL_RED,1,1,new float[]{1});
                int color=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,c.width,c.height,null);
                IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);
                for(int i=0;i<5;i++) {
                    c.previousValid=i>0;c.jitterX=TemporalPass.halton(i+1,2)-.5f;c.jitterY=TemporalPass.halton(i+1,3)-.5f;c.frameSeconds=1f/60;
                    float[] value=i==4?new float[]{.65f,.2f,.1f,1}:new float[]{.2f,.4f,.6f,1};if(i==4)c.previousValid=false;
                    upload(color,c.width,c.height,GL11.GL_RGBA,4,value);
                    upload(targets.depth,c.width,c.height,GL11.GL_RED,1,new float[]{.5f});upload(targets.velocity,c.width,c.height,GL11.GL_RGBA,4,new float[4]);
                    c.reprojection.set(c.plainViewProjection);
                    int out=pass.run(c,color);dispatches++;
                    check(out==vendor.output(),"FSR output texture");
                    float[] pixels=readTexture(out,c.outputWidth*c.outputHeight*4,GL11.GL_RGBA);
                    for(int y=8;y<c.outputHeight-8;y+=9)for(int x=8;x<c.outputWidth-8;x+=13)for(int channel=0;channel<3;channel++)
                        near(pixels[(y*c.outputWidth+x)*4+channel],value[channel],.055f);
                }
                System.out.println("Upscale096 native FSR GPU PASS: output="+size[0]+"x"+size[1]+" scale="+scale+" input="+c.width+"x"+c.height+" reset/history/colour-swap=5");
                GL11.glDeleteTextures(color);GL11.glDeleteTextures(targets.depth);GL11.glDeleteTextures(targets.velocity);GL11.glDeleteTextures(targets.farDepth);
            }
        } finally {pass.release();}
    }

    static int texture(int internal,int format,int w,int h,float[] value) {
        int t=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,GL11.GL_FLOAT,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        if(value!=null)upload(t,w,h,format,value.length,value);return t;
    }
    static void upload(int t,int w,int h,int format,int channels,float[] value) {float[] p=new float[w*h*channels];for(int i=0;i<w*h;i++)System.arraycopy(value,0,p,i*channels,channels);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,format,GL11.GL_FLOAT,p);}
    static float[] readTexture(int t,int count,int format){float[] p=new float[count];GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,format,GL11.GL_FLOAT,p);return p;}
    static int link(String v,String f){int p=GL20.glCreateProgram();for(int type:new int[]{GL20.GL_VERTEX_SHADER,GL20.GL_FRAGMENT_SHADER}){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,type==GL20.GL_VERTEX_SHADER?v:f);GL20.glCompileShader(s);check(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(s));GL20.glAttachShader(p,s);}GL20.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}
    static void near(float a,float b,float epsilon){check(Float.isFinite(a)&&Math.abs(a-b)<=epsilon,a+" != "+b+" tolerance="+epsilon);}
    static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
}
