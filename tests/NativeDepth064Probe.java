import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import vpiso.GameAccess;
import vpiso.NativeReceiver;

/** Current native chunk producer oracle, distinct from synthetic VP water/vision depth. */
public final class NativeDepth064Probe {
    private static final Class<?>[] FFFI={float.class,float.class,float.class,int.class};
    private static final Class<?>[] IIFFF={int.class,int.class,float.class,float.class,float.class};
    private static final Class<?>[] II5={int.class,int.class,int.class,int.class,int.class};
    private static final String VERT="""
        #version 330
        out vec4 col; out vec2 texCoord; out vec2 texCoord2;
        void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);
          texCoord=p;texCoord2=p;col=vec4(1);}
        """;
    private static Object camera,state,buffer,combinedOption;

    public static void main(String[] args)throws Exception {
        boolean negative=args.length>1 && args[1].equals("baseline063");
        Method world=null;
        try {world=NativeReceiver.class.getMethod("captureWorld",Frame.class,int.class);}
        catch(NoSuchMethodException ignored) {if(!negative)throw new AssertionError("Native world capture is absent");}
        setup();
        realCamera();
        cpu(world,negative);
        floorGpu(Path.of(args[0]));
        System.out.println("Native depth064 "+(negative?"063 negative control":"regression")
                +" PASS: current chunk producer coordinate/depth oracle and actual floor-art tile+chunk shaders; no map/save/settings loaded.");
    }
    private static Object field(String c,String n,Object o){return GameAccess.field(c,n,o);}
    private static float number(Object o,String n){return ((Number)field(o.getClass().getName(),n,o)).floatValue();}
    private static void set(Object o,String n,Object v){GameAccess.set(o.getClass().getName(),n,o,v);}
    private static float screen(String n,float x,float y,float z){return ((Number)GameAccess.call("zombie.iso.IsoUtils",n,null,FFFI,x,y,z,0)).floatValue();}
    private static float raw(int sx,int sy,float x,float y,float z){
        Object r=GameAccess.call("zombie.iso.IsoDepthHelper","getSquareDepthData",null,IIFFF,sx,sy,x,y,z);
        return number(r,"depthStart");
    }
    private static int group(int z){return ((Number)GameAccess.call("zombie.iso.fboRenderChunk.FBORenderLevels","calculateMinLevel",null,new Class<?>[]{int.class},z)).intValue();}
    private static float chunk(float x,float y,float z,boolean combined){
        Object r=GameAccess.call("zombie.iso.IsoDepthHelper","getChunkDepthData",null,II5,
                (int)Math.floor(number(state,"camCharacterX")/8),(int)Math.floor(number(state,"camCharacterY")/8),
                (int)Math.floor(x/8),(int)Math.floor(y/8),(int)Math.floor(z));
        if(combined)return number(r,"depthStart");
        return (number(r,"indexX")+number(r,"indexY")-number(camera,"fixJigglyModelsSquareX")
                -number(camera,"fixJigglyModelsSquareY"))/8f/40f*.46187335f-group((int)Math.floor(z))*.0028867084f;
    }
    private static float emittedDepth(float x,float y,float z,boolean combined){
        float local=((Number)GameAccess.call("zombie.iso.IsoDepthHelper","calculateDepth",null,
                new Class<?>[]{float.class,float.class,float.class},x,y,z)).floatValue();
        return chunk(x,y,z,combined)+local;
    }
    private static void setup()throws Exception {
        GameAccess.set("zombie.core.Core","debug",null,true);
        Object core=GameAccess.staticCall("zombie.core.Core","getInstance");
        buffer=GameAccess.type("zombie.core.textures.MultiTextureFBO2").getConstructor().newInstance();
        set(core,"offscreenBuffer",buffer);
        GameAccess.set("zombie.core.Core","initialWidth",null,1920f);
        GameAccess.set("zombie.core.Core","width",null,1920);GameAccess.set("zombie.core.Core","height",null,1080);
        GameAccess.set("zombie.characters.IsoPlayer","numPlayers",null,1);
        camera=((Object[])field("zombie.iso.IsoCamera","cameras",null))[0];
        state=field("zombie.iso.IsoCamera","frameState",null);
        Object debug=field("zombie.debug.DebugOptions","instance",null);
        Object options=field("zombie.debug.DebugOptions","fboRenderChunk",debug);
        combinedOption=field(options.getClass().getName(),"combinedFbo",options);
    }
    private static void realCamera()throws Exception {
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);Object unsafe=uf.get(null);
        Object player=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,GameAccess.type("zombie.characters.IsoPlayer"));
        GameAccess.set("zombie.iso.IsoCamera","isoCameraGameCharacter",null,player);
        set(camera,"width",1920);set(camera,"height",1080);set(camera,"zoom",1f);
        float max=0;
        for(int i=0;i<12;i++) {
            float x=4200.25f+i*.13f,y=5211.5f+i*.07f,z=1;
            GameAccess.set("zombie.iso.IsoMovingObject","x",player,x);GameAccess.set("zombie.iso.IsoMovingObject","y",player,y);GameAccess.set("zombie.iso.IsoMovingObject","z",player,z);
            float ox=screen("XToScreen",x,y,z)-960,oy=screen("YToScreen",x,y,z)-540;
            set(camera,"offX",ox);set(camera,"offY",oy);set(camera,"tOffX",ox);set(camera,"tOffY",oy);
            Class<?>[] sig={float.class,float.class,float.class};
            GameAccess.call("zombie.iso.PlayerCamera","calculateModelViewProjection",camera,sig,x,y,z);
            GameAccess.call("zombie.iso.PlayerCamera","calculateFixForJigglyModels",camera,sig,x,y,z);
            max=Math.max(max,Math.abs(number(camera,"fixJigglyModelsY")));
        }
        require(max>50,"Actual native model alignment fixture no longer creates material screen correction: "+max);
        System.out.println("Actual PlayerCamera producer maximum vertical correction="+max+"px");
    }
    private static void cpu(Method world,boolean negative)throws Exception {
        double worst=0,syntheticWorst=0,oracleWorst=0;int cases=0;
        double[] branchWorst=new double[2];
        for(boolean combined:new boolean[]{false,true})for(float zoom:new float[]{.5f,.75f,1,1.25f,1.5f})
        for(float shift:new float[]{0,.01f,.25f,.88f})for(int level:new int[]{-2,0,1,3})for(int step=0;step<24;step++) {
            Frame f=new Frame();f.camX=4200+step*.127f;f.camY=5211.5f+step*.077f;f.camZ=level+.2f;
            set(state,"camCharacterX",f.camX+(step%5==0?8:0));set(state,"camCharacterY",f.camY);set(state,"zoom",zoom);
            GameAccess.call("zombie.core.textures.MultiTextureFBO2","setZoomAndTargetZoom",buffer,new Class<?>[]{int.class,float.class},0,zoom);
            GameAccess.call("zombie.debug.BooleanDebugOption","setValue",combinedOption,new Class<?>[]{boolean.class},combined);
            require((Boolean)GameAccess.call("zombie.debug.BooleanDebugOption","getValue",combinedOption,new Class<?>[0])==combined,"combined option was debug-gated");
            float jx=shift,jy=shift*.6f,px=64*(jx-jy)/zoom,py=32*(jx+jy)/zoom;
            set(camera,"fixJigglyModelsSquareX",jx);set(camera,"fixJigglyModelsSquareY",jy);
            set(camera,"fixJigglyModelsX",px);set(camera,"fixJigglyModelsY",py);
            set(camera,"offX",screen("XToScreen",f.camX,f.camY,f.camZ)-960);set(camera,"offY",screen("YToScreen",f.camX,f.camY,f.camZ)-540);
            NativeReceiver actual=new NativeReceiver(),synthetic=new NativeReceiver();
            if(world!=null)world.invoke(actual,f,0);else actual.capture(f,0);
            synthetic.capture(f,0);
            int w=((Number)GameAccess.call("zombie.iso.IsoCamera","getOffscreenWidth",null,new Class<?>[]{int.class},0)).intValue();
            int h=((Number)GameAccess.call("zombie.iso.IsoCamera","getOffscreenHeight",null,new Class<?>[]{int.class},0)).intValue();
            double[][] samples=new double[4][3];
            for(int i=0;i<4;i++) {
                float x=f.camX+(i==1?1:0),y=f.camY+(i==2?1:0),z=f.camZ+(i==3?1:0);
                samples[i]=new double[]{(screen("XToScreenExact",x,y,z)+px*zoom)/w,
                        1-(screen("YToScreenExact",x,y,z)+py*zoom)/h,emittedDepth(x,y,z,combined)};
            }
            NativeReceiver oracle=new NativeReceiver();oracle.fit(samples);
            for(int i=0;i<5;i++) {
                float dx=i*2-4,dy=3-i,dz=i==0?0:1,x=f.camX+dx,y=f.camY+dy,z=f.camZ+dz;
                float u=(screen("XToScreenExact",x,y,z)/zoom+px)/1920f,v=1-(screen("YToScreenExact",x,y,z)/zoom+py)/1080f,d=emittedDepth(x,y,z,combined);
                double e=error(actual,u,v,d,dx,dy,dz);worst=Math.max(worst,e);branchWorst[combined?1:0]=Math.max(branchWorst[combined?1:0],e);
                oracleWorst=Math.max(oracleWorst,error(oracle,u,v,d,dx,dy,dz));
                syntheticWorst=Math.max(syntheticWorst,error(synthetic,screen("XToScreenExact",x,y,z)/w,1-screen("YToScreenExact",x,y,z)/h,
                        raw((int)Math.floor(f.camX),(int)Math.floor(f.camY),x,y,z),dx,dy,dz));cases++;
            }
        }
        require(oracleWorst<.001,"Independent emitted producer oracle invalid: "+oracleWorst);
        require(syntheticWorst<.001,"Synthetic water/vision receiver changed: "+syntheticWorst);
        if(negative)require(worst>.5,"063 misregistration did not reproduce");else require(worst<.001,"Native world misregistration="+worst);
        System.out.printf(Locale.ROOT,"Current native world: %,d samples; direct error=%.8f combined=%.8f; independent oracle=%.8f; synthetic isolation=%.8f%n",cases,branchWorst[0],branchWorst[1],oracleWorst,syntheticWorst);
    }
    private static double error(NativeReceiver r,float u,float v,float d,float dx,float dy,float dz){
        Vector4f q=new Vector4f(u-r.origin[0],v-r.origin[1],d-r.origin[2],0);r.inverse.transform(q);
        return Math.max(Math.abs(q.x-dx),Math.max(Math.abs(q.y-dy),Math.abs(q.z-dz)));
    }
    private static void floorGpu(Path game)throws Exception {
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(128,256,"native064 floor-depth",0,0);require(window!=0,"hidden GPU window");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            int tile=link(VERT,Files.readString(game.resolve("media/shaders/tileWithDepth.frag")));
            int compose=link(VERT,Files.readString(game.resolve("media/shaders/chunkShader.frag")));
            BufferedImage art=ImageIO.read(game.resolve("media/depthmaps/DEPTH_preset_depthmaps_01.png").toFile());
            ByteBuffer pixels=BufferUtils.createByteBuffer(128*256*4);
            for(int y=0;y<256;y++)for(int x=0;x<128;x++){int rgb=art.getRGB(x,255-y);int gray=rgb&255;pixels.put((byte)gray).put((byte)gray).put((byte)gray).put((byte)255);}pixels.flip();
            int source=texture(GL11.GL_RGBA8,GL11.GL_RGBA,pixels),white=texture(GL11.GL_RGBA8,GL11.GL_RGBA,BufferUtils.createByteBuffer(128*256*4));
            int cached=texture(GL14.GL_DEPTH_COMPONENT16,GL11.GL_DEPTH_COMPONENT,null),finalDepth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,null);
            int color=texture(GL11.GL_RGBA8,GL11.GL_RGBA,null),fbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            GL30.glBindVertexArray(GL30.glGenVertexArrays());GL11.glViewport(0,0,128,256);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_ALWAYS);GL11.glDepthMask(true);
            double worst=0;int cases=0;
            for(boolean combined:new boolean[]{false,true})for(int level:new int[]{-2,0,1,3}) {
                float x=4200,y=5211,z=level;
                set(state,"camCharacterX",4208.25f);set(state,"camCharacterY",5211.5f);
                set(camera,"fixJigglyModelsSquareX",.88f);set(camera,"fixJigglyModelsSquareY",.77f);
                Object r=GameAccess.call("zombie.iso.IsoDepthHelper","getChunkDepthData",null,II5,526,651,525,651,level);
                float rawChunk=number(r,"depthStart");
                float end=raw(4208,5211,x,y,z)-rawChunk,start=raw(4208,5211,x+1,y+1,z+1)-rawChunk;
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,cached,0);
                GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL20.glUseProgram(tile);
                GL20.glUniform1i(GL20.glGetUniformLocation(tile,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(tile,"DEPTH"),1);
                GL20.glUniform1f(GL20.glGetUniformLocation(tile,"zDepthBlendZ"),start);GL20.glUniform1f(GL20.glGetUniformLocation(tile,"zDepthBlendToZ"),end);
                bind(0,white);bind(1,source);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,finalDepth,0);
                GL20.glUseProgram(compose);GL20.glUniform1i(GL20.glGetUniformLocation(compose,"DIFFUSE"),0);GL20.glUniform1i(GL20.glGetUniformLocation(compose,"DEPTH"),1);
                float emitted=chunk(x,y,z,combined);GL20.glUniform1f(GL20.glGetUniformLocation(compose,"chunkDepth"),emitted);
                bind(0,white);bind(1,cached);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                float[] depths=new float[128*256];GL11.glReadPixels(0,0,128,256,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
                for(int py=0;py<64;py++)for(int px=0;px<128;px++) {
                    int gray=art.getRGB(px,255-py)&255;if(gray==0)continue;
                    double expected=emitted+Math.round((start+(end-start)*(gray/255.0))*65535.0)/65535.0;
                    worst=Math.max(worst,Math.abs(depths[py*128+px]-expected));cases++;
                }
            }
            require(worst<2e-5,"Actual floor source->depth16->chunk depth mismatch="+worst);
            require(GL11.glGetError()==0,"Floor GPU GL error");
            System.out.printf(Locale.ROOT,"Actual native floor art/shaders: %,d samples, direct+combined, four levels; worst final depth=%.9f%n",cases,worst);
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static void bind(int n,int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+n);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);}
    private static int texture(int internal,int format,ByteBuffer data){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,128,256,0,format,GL11.GL_UNSIGNED_BYTE,data);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);return t;}
    private static int link(String vertex,String fragment){int p=GL20.glCreateProgram();for(int type:new int[]{GL20.GL_VERTEX_SHADER,GL20.GL_FRAGMENT_SHADER}){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,type==GL20.GL_VERTEX_SHADER?vertex:fragment);GL20.glCompileShader(s);require(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(s));GL20.glAttachShader(p,s);}GL20.glLinkProgram(p);require(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
