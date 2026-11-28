import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.joml.Vector4f;
import viewpoint.core.Frame;
import vpiso.*;

/** Installed PoolPit helpers, native-ray heights and exact land/foreground retention in hidden GL. */
public final class PoolPitNative120Probe {
    static int checks;
    static void require(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static Field field(Class<?> t,String n)throws Exception{Field f=t.getDeclaredField(n);f.setAccessible(true);return f;}
    public static void main(String[] args)throws Exception {
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(32,32,"120 native PoolPit water",0,0);
        try {
            require(window!=0,"context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            int color=texture(GL11.GL_RGBA8,GL11.GL_RGBA),depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");GL11.glViewport(0,0,32,32);GL30.glBindVertexArray(GL30.glGenVertexArrays());
            Class<?> water=Class.forName("poolpitvp.WaterMap");Field published=field(water,"published"),px=field(water,"pubX"),py=field(water,"pubY"),version=field(water,"version");
            Object prior=published.get(null);int ox=px.getInt(null),oy=py.getInt(null),originalVersion=version.getInt(null),ver=originalVersion;
            Frame frame=new Frame();frame.camX=frame.camY=80;frame.camZ=1;frame.scene.originX=-80;frame.scene.originY=2.4494896;frame.scene.originZ=-80;
            frame.scene.skyR=.65f;frame.scene.skyG=.7f;frame.scene.skyB=.8f;frame.scene.fogR=.3f;frame.scene.fogG=.4f;frame.scene.fogB=.5f;frame.scene.sunY=1;frame.scene.sunR=frame.scene.sunG=frame.scene.sunB=1;frame.scene.sunStrength=.8f;
            NativeReceiver receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.5,.5},{.75,.5,.5},{.5,.75,.5},{.5,.5,.4}});
            PoolPitWaterOverlay overlay=new PoolPitWaterOverlay();PoolPitWaterOverlay.Snapshot snapshot=new PoolPitWaterOverlay.Snapshot();int cases=0;
            try {
                for(float height:new float[]{0,1,2})for(int kind=1;kind<=4;kind++) {
                    float[] map=new float[160*160*4];Arrays.fill(map,-1000);for(int y=76;y<84;y++)for(int x=80;x<84;x++){int at=(y*160+x)*4;map[at]=height;map[at+1]=kind;map[at+2]=height-.9f;map[at+3]=0;}
                    published.set(null,map);px.setInt(null,0);py.setInt(null,0);version.setInt(null,++ver);snapshot.capture(frame);require(snapshot.ready(),"snapshot missing actual map");map[80*160*4+80*4+1]=0;require(((float[])field(snapshot.getClass(),"values").get(snapshot))[80*160*4+80*4+1]==kind,"snapshot retained mutable producer array");
                    ByteBuffer base=BufferUtils.createByteBuffer(32*32*4);for(int i=0;i<32*32;i++)base.put((byte)(70+i%7)).put((byte)105).put((byte)145).put((byte)201);base.flip();
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,base);
                    FloatBuffer depths=BufferUtils.createFloatBuffer(32*32);for(int y=0;y<32;y++)for(int x=0;x<32;x++)depths.put(y>=16?.2f:.95f);depths.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
                    int sentinel=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0+61);GL11.glBindTexture(GL11.GL_TEXTURE_2D,sentinel);int sampler=GL33.glGenSamplers();GL33.glBindSampler(61,sampler);GL13.glActiveTexture(GL13.GL_TEXTURE4);
                    Field wf=field(Class.forName("viewpoint.render.WorldRenderer"),"frame");Object context=wf.get(null);Field scene=field(context.getClass(),"scene");Object oldScene=scene.get(context);
                    overlay.draw(frame,receiver,snapshot);require(!(Boolean)field(PoolPitWaterOverlay.class,"unavailable").get(null),"overlay self-disabled; inspect diagnostic");
                    require(scene.get(context)==oldScene,"WorldRenderer scene leaked");require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE4,"active unit leaked");GL13.glActiveTexture(GL13.GL_TEXTURE0+61);require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==sentinel&&GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,61)==sampler,"PoolPit ripple texture/sampler state leaked");
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);ByteBuffer actual=BufferUtils.createByteBuffer(32*32*4);GL11.glReadPixels(0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);int changed=0;
                    for(int y=0;y<32;y++)for(int x=0;x<32;x++){int at=(y*32+x)*4;if(x<16||y>=16)for(int c=0;c<4;c++)require(actual.get(at+c)==base.get(at+c),"land/foreground changed h="+height+" kind="+kind+" x="+x+" y="+y);else if(actual.get(at)!=base.get(at)||actual.get(at+1)!=base.get(at+1)||actual.get(at+2)!=base.get(at+2))changed++;require(actual.get(at+3)==base.get(at+3),"native alpha changed");}
                    require(changed>180,"water not visibly shaded h="+height+" kind="+kind+" pixels="+changed);require(GL11.glGetError()==0,"GL error");GL33.glBindSampler(61,0);GL33.glDeleteSamplers(sampler);GL11.glDeleteTextures(sentinel);cases++;
                }
                ownerDepthCase(overlay,snapshot,frame,receiver,published,px,py,color,depth);
                obliqueCases(overlay,snapshot,frame,published,px,py,color,depth);
                published.set(null,null);snapshot.capture(frame);require(!snapshot.ready(),"empty map retained prior frame water");
            }finally{published.set(null,prior);px.setInt(null,ox);py.setInt(null,oy);version.setInt(null,originalVersion);}
            System.out.println("PoolPitNative120 GPU PASS cases="+cases+"+ownerDepth+black+4oblique checks="+checks+"; installed Tune functions/afterUse, kinds1..4, heights0/1/2, snapshot copy, exact native foreground/land/alpha retention, context/texture/sampler restoration, actual sourceBegin kind6-only placeholder depth replacement, strict no-metadata gate, black/alpha retention, brute-force oblique positive/negative-ray oracle; no game/attach/save");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void ownerDepthCase(PoolPitWaterOverlay overlay,PoolPitWaterOverlay.Snapshot snapshot,Frame frame,NativeReceiver receiver,Field published,Field px,Field py,int color,int depth)throws Exception {
        NativeGround.begin(true);NativeGround.setFloor(true);
        NativeGround.sourceBegin(new NativeSource(80.5f,80.5f,1,6,new float[]{-.5f,0,-.5f,.5f,.01f,.5f}));
        int pool=field(NativeGround.class,"sourceOwner").getInt(null);require(pool>=2,"kind6 floor did not retain actual source ID");NativeGround.sourceEnd();
        NativeGround.setFloor(false);NativeGround.sourceBegin(new NativeSource(80.5f,80.5f,1,3,new float[]{-.5f,0,-.5f,.5f,1,.5f}));
        int furniture=field(NativeGround.class,"sourceOwner").getInt(null);require(furniture>=2&&furniture!=pool,"ordinary source ID");NativeGround.sourceEnd();NativeGround.finish();
        float[] map=new float[160*160*4];Arrays.fill(map,-1000);for(int y=76;y<84;y++)for(int x=80;x<84;x++){int at=(y*160+x)*4;map[at]=1;map[at+1]=2;map[at+2]=.1f;map[at+3]=0;}
        published.set(null,map);px.setInt(null,0);py.setInt(null,0);snapshot.capture(frame);require(snapshot.ready(),"owner test map");
        ByteBuffer base=BufferUtils.createByteBuffer(4096);for(int i=0;i<1024;i++)base.put((byte)70).put((byte)105).put((byte)145).put((byte)201);base.flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,base);
        FloatBuffer depths=BufferUtils.createFloatBuffer(1024);for(int i=0;i<1024;i++)depths.put(.49f);depths.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
        int metadata=texture(GL30.GL_RGBA32F,GL11.GL_RGBA);FloatBuffer owners=BufferUtils.createFloatBuffer(4096);
        float ax= -(pool&255)-2,ay=pool>>8,bx= -(furniture&255)-2,by=furniture>>8;
        for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
            if(y<16&&(x==16||x==17))owners.put(ax).put(ay).put(ax*ax+ay*ay).put(1);
            else if(x==18||x==19)owners.put(bx).put(by).put(bx*bx+by*by).put(1);
            else if(x==22||x==23)owners.put((ax+bx)*.5f).put((ay+by)*.5f).put((ax*ax+ay*ay+bx*bx+by*by)*.5f).put(1);
            else owners.put(1).put(0).put(1).put(1);
        }
        owners.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_FLOAT,owners);
        ByteBuffer mask=BufferUtils.createByteBuffer(65536);NativeGround.poolWaterOwners(mask);require((mask.get(pool)&255)==255&&(mask.get(furniture)&255)==0,"actual source mask");
        overlay.draw(frame,receiver,snapshot,metadata);require(!(Boolean)field(PoolPitWaterOverlay.class,"unavailable").get(null),"owner overlay disabled");require(GL11.glGetError()==0,"owner draw GL error");
        ByteBuffer actual=BufferUtils.createByteBuffer(4096);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);int changed=0;
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){int at=(y*32+x)*4;if(y<16&&(x==16||x==17)){if(actual.get(at)!=base.get(at)||actual.get(at+1)!=base.get(at+1)||actual.get(at+2)!=base.get(at+2))changed++;}else for(int c=0;c<4;c++)require(actual.get(at+c)==base.get(at+c),"nonplaceholder owner changed x="+x+" y="+y);require(actual.get(at+3)==base.get(at+3),"owner alpha");}
        require(changed>=28,"exact placeholder did not replace front depth "+changed);require(GL11.glGetError()==0,"owner GL error");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,base);
        overlay.draw(frame,receiver,snapshot);actual.clear();GL11.glReadPixels(0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);for(int i=0;i<4096;i++)require(actual.get(i)==base.get(i),"no-metadata foreground changed");
        ByteBuffer black=BufferUtils.createByteBuffer(4096);for(int i=0;i<1024;i++)black.put((byte)0).put((byte)0).put((byte)0).put((byte)(i%2==0?0:201));black.flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,black);depths.clear();for(int i=0;i<1024;i++)depths.put(.95f);depths.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
        overlay.draw(frame,receiver,snapshot,metadata);actual.clear();GL11.glReadPixels(0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);for(int i=0;i<4096;i++)require(actual.get(i)==black.get(i),"black/alpha revealed");require(GL11.glGetError()==0,"black GL error");GL11.glDeleteTextures(metadata);
    }
    static void obliqueCases(PoolPitWaterOverlay overlay,PoolPitWaterOverlay.Snapshot snapshot,Frame frame,Field published,Field px,Field py,int color,int depth)throws Exception {
        float[] map=new float[160*160*4];Arrays.fill(map,-1000);for(int y=78;y<83;y++)for(int x=78;x<83;x++){int at=(y*160+x)*4;map[at]=((x+y)&1)==0?0:2;map[at+1]=2;map[at+2]=map[at]-.9f;map[at+3]=0;}
        published.set(null,map);px.setInt(null,0);py.setInt(null,0);snapshot.capture(frame);
        for(float sx:new float[]{-.1f,.1f})for(float sy:new float[]{-.08f,.08f}) {
            NativeReceiver receiver=new NativeReceiver();receiver.fit(new double[][]{{.5,.5,.5},{.75,.5,.5},{.5,.75,.5},{.5+sx,.5+sy,.4}});
            ByteBuffer base=BufferUtils.createByteBuffer(4096);for(int i=0;i<1024;i++)base.put((byte)70).put((byte)105).put((byte)145).put((byte)201);base.flip();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,base);
            FloatBuffer depths=BufferUtils.createFloatBuffer(1024);for(int y=0;y<32;y++)for(int x=0;x<32;x++)depths.put(y>=16?.35f:.95f);depths.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
            overlay.draw(frame,receiver,snapshot);require(!(Boolean)field(PoolPitWaterOverlay.class,"unavailable").get(null),"oblique disabled");ByteBuffer actual=BufferUtils.createByteBuffer(4096);GL11.glReadPixels(0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);int visible=0;
            Vector4f ray=receiver.inverse.transform(new Vector4f(0,0,1,0));
            for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                Vector4f a=receiver.inverse.transform(new Vector4f((x+.5f)/32-receiver.origin[0],(y+.5f)/32-receiver.origin[1],-receiver.origin[2],0));float first=2;
                for(int ty=78;ty<83;ty++)for(int tx=78;tx<83;tx++){float h=map[(ty*160+tx)*4],t=(h-frame.camZ-a.z)/ray.z;if(t>=0&&t<1&&(int)Math.floor(frame.camX+a.x+ray.x*t)==tx&&(int)Math.floor(frame.camY+a.y+ray.y*t)==ty)first=Math.min(first,t);}
                boolean water=first+.00001f<(y>=16?.35f:.95f);int at=(y*32+x)*4;boolean changed=actual.get(at)!=base.get(at)||actual.get(at+1)!=base.get(at+1)||actual.get(at+2)!=base.get(at+2);require(changed==water,"oblique oracle sx="+sx+" sy="+sy+" x="+x+" y="+y+" t="+first);require(actual.get(at+3)==base.get(at+3),"oblique alpha");if(water)visible++;
            }
            require(visible>100,"oblique coverage "+visible);require(GL11.glGetError()==0,"oblique GL error");
        }
    }
    static int texture(int format,int channels){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,32,32,0,channels,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
}
