import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.render.MousePick;
import vpiso.*;
import zombie.iso.*;
import zombie.iso.sprite.*;

/** Actual source sample/table decoder and production cutaway helper, with118 negative control. */
public final class FurnitureWall119Probe {
    static final int SIDE=16,ELIGIBLE=0x02000000;
    static Method sample;static int checks,cases,negative,buffer,table;
    static String vertex,fragment;
    static final String HEADER="""
        #version 430
        uniform float vpIsoNativeOn,capability;
        uniform vec3 vpIsoNativeCam,position,normal;
        uniform int inspect,flags,useOverload;
        flat in vec4 parent;
        flat in float eligible;
        flat in vec3 vpIsoObjectInfo;
        vec4 vpIsoNativeRule(vec3 p){return vec4(1,0,0,capability/255.0);}
        """;
    static final String BODY="""
        layout(location=0)out vec4 color;
        void main(){
          if(inspect!=0){color=vec4(vpIsoObjectInfo,eligible);return;}
          if(useOverload!=0)vpIsoCutWall(position,vec3(0,position.y,0),normal,flags);
          else vpIsoCutWall(position,vec3(0,position.y,0),normal,flags,parent);color=vec4(1);
        }
        """;
    record Source(IsoObject object,float alpha,int id){}
    public static void main(String[] args)throws Exception {
        require(args.length>0,"118 baseline artifact required");
        sample=NativeObjectState.class.getDeclaredMethod("sample",Object.class,float.class);sample.setAccessible(true);
        Field v=NativeObjectShaders.class.getDeclaredField("VERTEX");v.setAccessible(true);vertex=(String)v.get(null);
        require(vertex.contains("vpIsoWallEligible"),"missing production eligibility decoder");
        String rewritten=NativeObjectShaders.rewrite("mesh.vert",readMesh());require(rewritten.contains("-vpIsoWallEligible"),"production mesh.vert did not wire eligibility sentinel");
        String old=legacy(Path.of(args[0]));require(!old.contains("parent.w == 0"),"baseline already119");
        zombie.core.random.RandStandard.INSTANCE.init();
        List<Source> furniture=new ArrayList<>(),walls=new ArrayList<>(),attached=new ArrayList<>();
        for(float alpha:new float[]{0,.25f,.65f,1}) {
            Source f=source(0,false,alpha,false);require((f.id&ELIGIBLE)==0,"furniture gained wall bit");furniture.add(f);
            for(int faces:new int[]{1,2,3}){Source w=source(faces,false,alpha,false);require((w.id&ELIGIBLE)!=0&&((w.id>>>16)&511)==0,"plain wall source/parent classification");walls.add(w);}
            Source a=source(3,true,alpha,false);require((a.id&ELIGIBLE)!=0&&((a.id>>>16)&511)==((3<<6|7*8+4)+1),"attachment parent encoding changed");attached.add(a);
        }
        for(Class<?> type:new Class<?>[]{zombie.iso.objects.IsoDoor.class,zombie.iso.objects.IsoWindow.class}){
            IsoObject object=(IsoObject)allocate(type);GameAccess.set("zombie.iso.IsoObject","square",object,square());GameAccess.set("zombie.iso.IsoObject","sprite",object,new IsoSprite());object.noPicking=true;
            require((sampleId(new NativeObjectState(),object,.65f)&ELIGIBLE)!=0,"door/window without cut flags lost eligibility");
        }
        for(boolean wall:new boolean[]{false,true}){
            Source picked=source(wall?2:0,false,.65f,true);require((picked.id&65535)==1,"eligibility changed low16 mouse ID");require(((picked.id>>>30)&3)==0,"eligibility overlaps assisted bits");
        }
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(SIDE,SIDE,"119 furniture source guard",0,0);
        try {
            require(window!=0,"hidden GL context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);int color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,SIDE,SIDE,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");GL11.glViewport(0,0,SIDE,SIDE);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(0,0,0,0);
            buffer=GL15.glGenBuffers();table=GL11.glGenTextures();int mask=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,mask);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R8,1,1,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,BufferUtils.createByteBuffer(1));GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GlProgram candidate=program(NativeWallCutaway.GLSL),baseline=program(old);
            for(int kind:new int[]{0,1})for(Source f:furniture)for(float away:new float[]{0,.03f,.079f,.081f,.24f})for(float nx:new float[]{1,.95f,.89f}){
                bind(candidate,kind,f,false);plain(candidate,away,nx);require(visible(candidate),"furniture cut kind="+kind+" away="+away+" normal="+nx);
                bind(baseline,kind,f,false);plain(baseline,away,nx);boolean oldVisible=visible(baseline);
                if(away<=.079f&&nx>.9f){require(!oldVisible,"118 negative furniture cut not reproduced");negative++;}cases++;
            }
            for(int kind:new int[]{0,1})for(Source w:walls)for(float away:new float[]{0,.03f,.079f,.081f}){
                bind(candidate,kind,w,false);plain(candidate,away,1);boolean actual=visible(candidate);bind(baseline,kind,w,false);plain(baseline,away,1);require(actual==visible(baseline),"real wall changed relative118");require(actual==(away>.08f),"real wall eligibility/plane mismatch");cases++;
            }
            for(Source a:attached)for(float away:new float[]{0,.03f,.1f,.24f,.49f}){
                bind(candidate,1,a,false);plain(candidate,away,1);require(!visible(candidate),"attached parent escaped mask");cases++;
            }
            for(int kind:new int[]{0,1})for(Source s:new Source[]{furniture.get(2),walls.get(4),attached.get(2)}){
                bind(candidate,kind,s,true);candidate.setInt("inspect",1);FloatBuffer info=render(candidate);require(info.get(0)==s.alpha,"source alpha changed");require(info.get(1)==(s.id&65535),"shader low16 changed");require(info.get(2)==1f,"assisted decoder collided with bit25");require(info.get(3)==((s.id&ELIGIBLE)!=0?1f:0f),"wall bit decode changed");
            }
            require(viewpoint.platform.VertexFormat.BAKED_WALL==2097153,"pinned baked wall constant changed");
            for(int flags:new int[]{0,1,2,64,1024,2097152,2097153}){
                bind(candidate,0,furniture.get(3),false);candidate.setInt("vpIsoObjectsOn",0);plain(candidate,.03f,1);candidate.setInt("flags",flags);
                require(visible(candidate)==(flags!=2097153),"baked-only unsourced fallback flags="+flags);cases++;
            }
            bind(candidate,0,furniture.get(3),false);plain(candidate,.03f,1);candidate.setInt("useOverload",1);require(!visible(candidate),"4arg legacy overload changed");
            for(float yaw:new float[]{37,90,180,270}){
                Matrix4f p=new Matrix4f(),view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(.5,.5,8),.5f,.5f,1920,1080,p,view,eye,yaw);var camera=new RotationCamera.Snapshot(p,view,.5f,.5f,0,1920,1080,1,yaw);var previous=RotationLayers.begin(camera);
                try{for(int kind:new int[]{0,1}){
                    bind(candidate,kind,furniture.get(3),false);rotated(candidate,camera,Math.signum(eye[0])*.5f);require(visible(candidate),"rotated furniture cut");
                    bind(baseline,kind,furniture.get(3),false);rotated(baseline,camera,Math.signum(eye[0])*.5f);require(!visible(baseline),"rotated118 negative not reproduced");negative++;
                    bind(candidate,kind,walls.get(3),false);rotated(candidate,camera,Math.signum(eye[0])*.5f);require(!visible(candidate),"rotated real wall no longer cut");candidate.set("position",-Math.signum(eye[0])*.5f,1.1f,0f);require(visible(candidate),"background wall cut");candidate.set("vpIsoNativeOn",0f);require(visible(candidate),"native-off changed");cases++;
                }}finally{RotationLayers.end(previous);}
            }
            require(GL11.glGetError()==0,"GL error");candidate.delete();baseline.delete();
            System.out.println("FurnitureWall119 GPU PASS cases="+cases+" checks="+checks+" reproduced118="+negative+"; actual source sample, kind0/1 table decode, furniture side planes protected, truewalls/doors/windows eligible, parent unchanged, alpha/low16/assist unchanged; .00/.03/.079/.081/.24 boundaries and normals, 37/90/180/270 yaw; baseline="+args[0]);
        }finally{RotationLayers.end(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void plain(GlProgram p,float away,float nx){p.set("vpIsoRotated",0f);p.set("vpIsoNativeCam",0f,0f,0f);p.set("position",-4-away,1.1f,-7.5f);p.set("normal",nx,0f,0f);}
    static void rotated(GlProgram p,RotationCamera.Snapshot camera,float x){NativeWallCutaway.onUse(p);p.set("vpIsoNativeCam",.5f,.5f,0f);p.set("position",x,1.1f,0f);p.set("normal",1f,0f,0f);}
    static void bind(GlProgram p,int kind,Source s,boolean assist){
        IntBuffer words=BufferUtils.createIntBuffer(4);words.put(0).put(3).put(Float.floatToRawIntBits(s.alpha)).put(s.id|(assist?0x40000000:0)).flip();GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER,buffer);GL15.glBufferData(GL31.GL_TEXTURE_BUFFER,words,GL15.GL_STREAM_DRAW);GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,table);GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER,GL30.GL_RGBA32UI,buffer);p.use();p.setInt("vpIsoObjectsOn",1);p.setInt("vpIsoObjectTable",1);for(int i=0;i<3;i++){p.setInt("vpIsoObjectStart"+i,0);p.setInt("vpIsoObjectCount"+i,1);}p.setInt("kind",kind);p.setInt("vpIsoWallMask",0);p.set("vpIsoWallMaskSize",1f,1f);p.set("vpIsoCutawayScale",1f);p.set("vpIsoNativeOn",1f);p.set("capability",224f);p.setInt("inspect",0);p.setInt("flags",0);p.setInt("useOverload",0);
    }
    static boolean visible(GlProgram p){return render(p).get(0)>.5f;}
    static FloatBuffer render(GlProgram p){GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);FloatBuffer value=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(8,8,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,value);return value;}
    static GlProgram program(String helper){String vs="#version 430\n"+vertex+"\nuniform int kind;flat out vec4 parent;flat out float eligible;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);vpIsoObjectInfo=vpIsoSource(kind,0);eligible=vpIsoWallEligible;parent=vec4(0,0,0,-vpIsoWallEligible);if(kind==1&&vpIsoWallCode>.5){int code=int(vpIsoWallCode)-1,tile=code&63;parent=vec4(-float(tile&7),0,-float(tile>>3),float(code>>6));}}";return GlProgram.fromSources("119 source qualification",new GlProgram.Sources(vs,null,null,null,HEADER+helper+BODY),Map.of(),Map.of(),List.of("production119 helpers"));}
    static Source source(int faces,boolean child,float alpha,boolean picking)throws Exception {
        IsoGridSquare square=square();IsoSprite sprite=new IsoSprite();sprite.cutN=(faces&1)!=0;sprite.cutW=(faces&2)!=0;IsoObject object=new IsoObject(null,square,sprite);object.noPicking=!picking;
        if(child){IsoSprite drawing=new IsoSprite();drawing.name="wall_art119";drawing.getProperties().set(zombie.iso.SpriteDetails.IsoFlagType.WallOverlay);IsoSpriteInstance instance=new IsoSpriteInstance();instance.parentSprite=drawing;GameAccess.set("zombie.iso.IsoObject","attachedAnimSprite",object,new ArrayList<>(List.of(instance)));}
        NativeObjectState state=new NativeObjectState();if(picking){GameAccess.set("vpiso.NativeObjectState","ask",state,new MousePick.Ask(0,0,16,16));GameAccess.set("vpiso.NativeObjectState","pickX",state,4f);GameAccess.set("vpiso.NativeObjectState","pickY",state,7f);((List<?>)GameAccess.field("vpiso.NativeObjectState","ids",state)).clear();((List<Object>)GameAccess.field("vpiso.NativeObjectState","ids",state)).add(null);}
        int id=sampleId(state,object,alpha);require(object.getSprite()==sprite&&sprite.cutN==((faces&1)!=0)&&sprite.cutW==((faces&2)!=0),"source sprite flags mutated");return new Source(object,alpha,id);
    }
    static int sampleId(NativeObjectState state,IsoObject object,float alpha)throws Exception{Object value=sample.invoke(state,object,alpha);require((Float)GameAccess.field(value.getClass().getName(),"alpha",value)==alpha,"CPU sample alpha changed");return (Integer)GameAccess.field(value.getClass().getName(),"id",value);}
    static IsoGridSquare square()throws Exception{IsoGridSquare s=(IsoGridSquare)allocate(IsoGridSquare.class);s.x=4;s.y=7;s.z=0;return s;}
    static Object allocate(Class<?> c)throws Exception{Field f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);Object u=f.get(null);return u.getClass().getMethod("allocateInstance",Class.class).invoke(u,c);}
    static String readMesh()throws Exception{try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(Path.of(GlProgram.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toFile())){return new String(zip.getInputStream(zip.getEntry("viewpoint/shaders/mesh.vert")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    static String legacy(Path p)throws Exception{try(URLClassLoader loader=new URLClassLoader(new URL[]{p.toUri().toURL()},FurnitureWall119Probe.class.getClassLoader()){@Override protected Class<?> loadClass(String n,boolean r)throws ClassNotFoundException{if(!n.equals("vpiso.NativeWallCutaway"))return super.loadClass(n,r);Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(r)resolveClass(c);return c;}}){return(String)loader.loadClass("vpiso.NativeWallCutaway").getField("GLSL").get(null);}}
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
}
