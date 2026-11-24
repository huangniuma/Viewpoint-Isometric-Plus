import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;

/** Production tree capture order and original alpha/depth-write semantics, with overlapping cards. */
public final class NativeTreeDepth055Probe {
    static Class<?> fixture=NativeTreeProducerProbe.class;
    static Object renderer,template,colors,cell,farTexture;
    static int shader,vao,fbo;
    static boolean baseline;
    public static void main(String[] args) throws Exception {
        baseline=args.length>0 && args[0].equals("baseline054");
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);
        var af=fixture.getDeclaredField("allocate");af.setAccessible(true);af.set(null,uf.getType().getMethod("allocateInstance",Class.class));
        var us=fixture.getDeclaredField("unsafe");us.setAccessible(true);us.set(null,uf.get(null));
        call("fixture",new Class<?>[0]);
        template=staticGet("square");
        renderer=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell","instance",null);
        Object color=Class.forName("zombie.core.textures.ColorInfo").getConstructor(float.class,float.class,float.class,float.class).newInstance(.7f,.8f,.9f,1f);
        colors=Array.newInstance(color.getClass(),4);for(int i=0;i<4;i++)Array.set(colors,i,color);
        set(staticGet("player"),"ecsComponentMap",new HashMap<>());
        cell=fresh("zombie.iso.IsoCell");set(cell,"stencilAreas",new ArrayList<>());
        set(GameAccess.field("zombie.iso.IsoWorld","instance",null),"currentCell",cell);
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long win=GLFW.glfwCreateWindow(32,32,"native crown overlap",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            attach(GL30.GL_COLOR_ATTACHMENT0,GL11.GL_RGBA8,GL11.GL_RGBA);
            attach(GL30.GL_DEPTH_ATTACHMENT,GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT);
            shader=NativeVisibility053Probe.program("""
                #version 330
                void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);}
                ""","""
                #version 330
                uniform float nativeDepth; uniform vec4 nativeColor;
                out vec4 c;
                void main(){gl_FragDepth=nativeDepth;c=nativeColor;}
                """);
            int bad=0;
            for(int[] pair:new int[][]{{10,13,11,12},{9,16,10,15},{15,12,16,11}}) {
                for(boolean reverse:new boolean[]{false,true})if(!overlap(pair,reverse))bad++;
            }
            if(baseline && Boolean.getBoolean("tree055.third")) { if(bad!=0)throw new AssertionError("Third-tree churn recovery fixture");System.out.println("Pinned 054 third-tree churn: unchanged pair reappears when neighbour changes depth offsets, 6/6"); }
            else if(baseline){if(bad==0)throw new AssertionError("054 same-row depth-offset competition did not reproduce");System.out.println("Pinned 054 same-row depth-offset negative control PASS: "+bad+"/6 pairs lose the rear crown");}
            else {if(bad!=0)throw new AssertionError("Rear crowns missing: "+bad);System.out.println("Native tree actual-depth 055 GPU PASS: 6 actual production captures, order-independent input, real native alpha and depths; both overlapping crowns retained");}
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
        } finally {GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static boolean overlap(int[] xy,boolean reverse) throws Exception {
        var bridge=new NativeTrees();
        Object far=tree("far",xy[0],xy[1]),near=tree("near",xy[2],xy[3]);
        Object extra=tree("extra",xy[2]+1,xy[3]-1);
        boolean third=Boolean.getBoolean("tree055.third");
        try {
            if(third)bridge.add(get(extra,"square"));
            bridge.add(get(reverse?near:far,"square"));bridge.add(get(reverse?far:near,"square"));bridge.capture(renderer,0);
            List<?> records=(List<?>)get(get(bridge,"batch"),"trees");
            if(records.size()!=(third?3:2))throw new AssertionError("Expected two native records, got "+records.size());
            GL11.glViewport(0,0,32,32);GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL20.glUseProgram(shader);
            for(Object record:records) {
                if(get(get(record,"texture"),"name").equals("extra"))continue;
                boolean rear=get(record,"texture")==farTexture;
                float alpha=(Boolean)get(record,"transparent")?Math.min((Float)get(record,"cutawayAlpha"),(Float)get(record,"fadeAlpha")):1;
                if(!(alpha>0&&alpha<1))throw new AssertionError("Crown fixture must be partly transparent: "+alpha);
                Object d=GameAccess.call("zombie.iso.IsoDepthHelper","getSquareDepthData",null,new Class<?>[]{int.class,int.class,float.class,float.class,float.class},10,12,(Float)get(record,"x")-.5f,(Float)get(record,"y")-.5f,(Float)get(record,"z"));
                float depth=(Float)GameAccess.field("zombie.iso.IsoDepthHelper$Results","depthStart",d) + ((Integer)get(record,"depthOffset"))*.0002f;
                System.out.println("record "+(rear?"red":"blue")+" offset="+get(record,"depthOffset")+" depth="+depth);
                GL20.glUniform1f(GL20.glGetUniformLocation(shader,"nativeDepth"),depth);
                GL20.glUniform4f(GL20.glGetUniformLocation(shader,"nativeColor"),rear?1:0,0,rear?0:1,alpha);
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            }
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            boolean ok=(pixel.get(0)&255)>20&&(pixel.get(2)&255)>20;
            System.out.println("Overlapping native crowns "+Arrays.toString(xy)+" reversed="+reverse+" rear="+(pixel.get(0)&255)+" front="+(pixel.get(2)&255));
            return ok;
        }finally{bridge.release();}
    }
    static float depth(Object record) {
        try {
            Object d=GameAccess.call("zombie.iso.IsoDepthHelper","getSquareDepthData",null,new Class<?>[]{int.class,int.class,float.class,float.class,float.class},10,12,(Float)get(record,"x")-.5f,(Float)get(record,"y")-.5f,(Float)get(record,"z"));
            return (Float)GameAccess.field("zombie.iso.IsoDepthHelper$Results","depthStart",d)+((Integer)get(record,"depthOffset"))*.0002f;
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    static Object tree(String label,int x,int y)throws Exception {
        Object sq=fresh("zombie.iso.IsoGridSquare");set(sq,"x",x);set(sq,"y",y);set(sq,"z",0);
        for(String name:new String[]{"properties","nav","lighting"})set(sq,name,get(template,name));
        set(sq,"lightInfo",colors);set(sq,"targetPlayerCutawayFlags",new byte[4]);set(sq,"playerCutawayFlags",new byte[4]);set(sq,"playerCutawayFlagLockUntilTimes",new long[4]);
        Object texture=call("texture",new Class<?>[]{String.class},label);
        if(label.equals("far"))farTexture=texture;
        Object sprite=call("sprite",new Class<?>[]{String.class,Object.class},"VPISO_overlap_"+label,texture);
        set(sprite,"properties",Class.forName("zombie.core.properties.PropertyContainer").getConstructor().newInstance());
        Object tree=call("tree",new Class<?>[]{Object.class},sprite);set(tree,"square",sq);set(tree,"cutawayAlpha",.35f);set(tree,"fadeAlpha",.35f);
        set(sq,"objects",new ArrayList<>(List.of(tree)));return tree;
    }
    static Object call(String n,Class<?>[] types,Object...args)throws Exception{var m=fixture.getDeclaredMethod(n,types);m.setAccessible(true);try{return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}}
    static Object staticGet(String n)throws Exception{var f=fixture.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    static Object fresh(String name)throws Exception{return call("fresh",new Class<?>[]{String.class},name);}
    static Object get(Object o,String n)throws Exception{return call("get",new Class<?>[]{Object.class,String.class},o,n);}
    static void set(Object o,String n,Object v)throws Exception{call("set",new Class<?>[]{Object.class,String.class,Object.class},o,n,v);}
    static void attach(int at,int internal,int format){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,32,32,0,format,GL11.GL_FLOAT,(ByteBuffer)null);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,at,GL11.GL_TEXTURE_2D,t,0);}
}
