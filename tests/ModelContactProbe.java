import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Real ContactShadows compute dispatch over analytically rasterized floor and box. */
public final class ModelContactProbe {
    static final int W=192;
    static float boxHeight=3;
    public static void main(String[] args)throws Exception {
        String pack=args.length>0?args[0]:"default";
        if(args.length>1)boxHeight=Float.parseFloat(args[1]);
        boolean baseline=args.length>3;
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long win=GLFW.glfwCreateWindow(W,W,"contact depth shell",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();Main.compatible=true;
            TestShaderPacks.select(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off","reflections","off","contactShadows","true"));
            GlProgram.onUse(IsometricShaders::onUse);
            Frame f=NativeSurfaceReceiverProbe.frame();f.camX=f.camY=128;
            FrameContext ctx=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
            ctx.scene=f.scene;ctx.pipeline=ShaderPacks.active().pipeline;ctx.width=ctx.height=W;ctx.sun=true;
            Matrix4f view=new Matrix4f();float[] eye=new float[3];
            IsoCameraMath.matrices(new IsoCameraMath.Fit(128,128,args.length>2?Float.parseFloat(args[2]):12),128,128,W,W,f.scene.projection,view,eye,0);
            ctx.view=view;ctx.eyeX=eye[0];ctx.eyeY=eye[1];ctx.eyeZ=eye[2];
            ctx.viewProjection.set(f.scene.projection).mul(view);ctx.invViewProjection.set(ctx.viewProjection).invert();
            var targetsCtor=Targets.class.getDeclaredConstructor();targetsCtor.setAccessible(true);Targets targets=targetsCtor.newInstance();
            FloatBuffer d=BufferUtils.createFloatBuffer(W*W),a=BufferUtils.createFloatBuffer(W*W*4),r=BufferUtils.createFloatBuffer(W*W);
            Vector3f[] floor=new Vector3f[W*W];boolean[] isFloor=new boolean[W*W];
            for(int y=0;y<W;y++)for(int x=0;x<W;x++) {
                Vector3f p=ctx.invViewProjection.transformProject(new Vector3f((x+.5f)/W*2-1,(y+.5f)/W*2-1,-1));
                Vector3f end=ctx.invViewProjection.transformProject(new Vector3f((x+.5f)/W*2-1,(y+.5f)/W*2-1,1));
                Vector3f ray=end.sub(p,new Vector3f()).normalize();
                float plane=-p.y/ray.y,box=box(p,ray),t=Math.min(plane,box);
                Vector3f hit=ray.mul(t,new Vector3f()).add(p);floor[y*W+x]=hit;isFloor[y*W+x]=plane<=box;
                d.put(ctx.viewProjection.transformProject(hit,new Vector3f()).z*.5f+.5f);a.put(1).put(1).put(1).put(0);r.put(1);
            }
            targets.depth=tex(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,d.flip());targets.albedo=tex(GL30.GL_RGBA32F,GL11.GL_RGBA,a.flip());
            GameAccess.set("viewpoint.render.Targets","relief",targets,tex(GL30.GL_R32F,GL11.GL_RED,r.flip()));
            Class<?> type=Class.forName("viewpoint.render.ContactShadows");var ctor=type.getDeclaredConstructor(Targets.class);ctor.setAccessible(true);Object contact=ctor.newInstance(targets);
            var draw=type.getDeclaredMethod("draw",FrameContext.class,boolean.class);draw.setAccessible(true);
            if(!ctx.pipeline.contactShadows) {
                if(!Set.of("library","vivid").contains(pack) || (Boolean)draw.invoke(contact,ctx,true))throw new AssertionError("unexpected contact bypass");
                System.out.println("Contact bypass PASS: pack="+pack+"; original pipeline has no contact pass; no pass forced on.");
                return;
            }
            long ordinaryHash=0,ownedHash=0;int ordinaryFalse=0,ownedFalse=0,ownedReal=0;
            for(int step=0;step<196;step++) {
                boolean owned=step>=4 && step<194;
                if(owned)IsometricBridge.modelRendering(f,ModelShadowMotionProbe.FX);
                else if(step==1)IsometricBridge.rendering(f);
                else if(step==2)IsometricBridge.modelRendering(new Frame(),ModelShadowMotionProbe.FX);
                else IsometricBridge.rendering(null);
                if(!(Boolean)draw.invoke(contact,ctx,true))throw new AssertionError("contact pass off");
                int shadow=(Integer)GameAccess.field(type.getName(),"shadow",contact);FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W);
                GL45.glGetTextureImage(shadow,0,GL11.GL_RED,GL11.GL_FLOAT,pixels);
                int valid=0,falseShadow=0,realShadow=0;double sum=0;long hash=1;
                for(int i=0;i<W*W;i++) {
                    if(!isFloor[i])continue;Vector3f p=floor[i];
                    if(Math.abs(p.x)>8 || Math.abs(p.z)>8)continue;valid++;
                    boolean occluded=Float.isFinite(box(new Vector3f(p.x,.001f,p.z),new Vector3f(f.scene.sunX,f.scene.sunY,f.scene.sunZ)));
                    float s=pixels.get(i);if(s<.5f){if(occluded)realShadow++;else falseShadow++;}sum+=1-s;
                    if(!Float.isFinite(s)||s<0||s>1)throw new AssertionError("invalid contact pixel");
                    hash=hash*31+Float.floatToIntBits(s);
                }
                if(step==0){ordinaryHash=hash;ordinaryFalse=falseShadow;}
                if(!owned && hash!=ordinaryHash)throw new AssertionError("ordinary/native/foreign scope changed contact");
                if(step==4){ownedHash=hash;ownedFalse=falseShadow;ownedReal=realShadow;}
                if(owned && hash!=ownedHash)throw new AssertionError("stationary contact changed between frames");
                if(step==0 || step==4)System.out.println("Contact sample pack="+pack+" owned="+owned+" valid="+valid+" false="+falseShadow+" real="+realShadow+" dark="+sum);
            }
            if(boxHeight>2) {
                if(ordinaryFalse<40 || (baseline?ownedFalse!=ordinaryFalse:ownedFalse>2))throw new AssertionError("tall shell discriminator ordinary="+ordinaryFalse+" owned="+ownedFalse);
            } else if(!baseline && (ownedReal<1 || ownedFalse>2))throw new AssertionError("small contact removed or false: real="+ownedReal+" false="+ownedFalse);
            System.out.println("Contact shell "+(baseline?"048 negative control":"GPU PASS")+": pack="+pack+" height="+boxHeight+" static frames=190; ordinary/native/foreign scope bit-identical; pause retains stable corrected contact; small contacts checked separately.");
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
        }finally{IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static float box(Vector3f p,Vector3f ray) {
        float lo=0,hi=Float.POSITIVE_INFINITY;
        for(int axis=0;axis<3;axis++) {
            float min=axis==1?0:-.25f,max=axis==1?boxHeight:.25f,v=ray.get(axis),o=p.get(axis);
            if(Math.abs(v)<1e-7f){if(o<min||o>max)return Float.POSITIVE_INFINITY;continue;}
            float a=(min-o)/v,b=(max-o)/v;lo=Math.max(lo,Math.min(a,b));hi=Math.min(hi,Math.max(a,b));
        }
        return hi>=lo&&hi>0?lo:Float.POSITIVE_INFINITY;
    }
    static int tex(int internal,int format,FloatBuffer data) {
        int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,W,W,0,format,GL11.GL_FLOAT,data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        return t;
    }
}
