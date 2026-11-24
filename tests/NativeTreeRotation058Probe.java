import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;
import java.nio.file.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.bytebuddy.*;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

/** Isolated actual IsoTree -> native Tree records -> native GPU dual-texture check. */
@SuppressWarnings("unchecked")
public class NativeTreeRotation058Probe {
    static final int SIZE=512;
    static final Class<?> F=NativeTreeProducerProbe.class;
    static Object renderer,player;
    public static String mode;
    static RotationCamera.Snapshot snapshot;
    static float yaw=17.25f;
    static int backgroundProgram,backgroundVbo,totalWrong,boundChecks;
    static void weave(String source,String overrides,String target,boolean old)throws Exception {
        Path folder=Path.of(target); Map<String,byte[]> raw=new HashMap<>();
        try(var jar=new java.util.jar.JarFile(source)) {
            for(var en=jar.entries();en.hasMoreElements();) {
                var e=en.nextElement();if(!(e.getName().startsWith("zombie/iso/fboRenderChunk/")||e.getName().startsWith("zombie/core/opengl/"))||!e.getName().endsWith(".class"))continue;
                raw.put(e.getName().substring(0,e.getName().length()-6).replace('/','.'),jar.getInputStream(e).readAllBytes());
            }
        }
        if(!overrides.equals("-"))try(var paths=Files.walk(Path.of(overrides,"zombie/iso/fboRenderChunk"))) {
            for(Path p:paths.filter(p->p.getFileName().toString().startsWith("FBORenderTrees")&&p.toString().endsWith(".class")).toList()) {
                String n=Path.of(overrides).relativize(p).toString().replace('\\','.').replace('/','.');
                raw.put(n.substring(0,n.length()-6),Files.readAllBytes(p));
            }
        }
        for(var e:raw.entrySet()){Path p=folder.resolve(e.getKey().replace('.','/')+".class");Files.createDirectories(p.getParent());Files.write(p,e.getValue());}
        var converter=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class);converter.setAccessible(true);
        Map<String,byte[]> advice=new HashMap<>();
        boolean light=ClassLoader.getSystemResource("vpiso/NativeTreeLighting.class")!=null;
        for(String p:new String[]{"Depth","Projection","View"}) {
            String n="vpiso.Patch_RotationTree"+p;byte[] bytes;
            if(old&&p.equals("Depth"))try(var jar=new java.util.jar.JarFile("build/checkpoints/057/VPIsometricLighting.jar")){bytes=jar.getInputStream(jar.getEntry(n.replace('.','/')+".class")).readAllBytes();}
            else try(var in=NativeTreeRotation058Probe.class.getClassLoader().getResourceAsStream(n.replace('.','/')+".class")){bytes=in.readAllBytes();}
            advice.put(n,(byte[])converter.invoke(null,(Object)bytes));
        }
        if(light)for(String n:List.of("vpiso.Patch_NativeSourceTree","vpiso.Patch_NativeGroundVBODraw"))
            try(var in=ClassLoader.getSystemResourceAsStream(n.replace('.','/')+".class")){advice.put(n,(byte[])converter.invoke(null,(Object)in.readAllBytes()));}
        ClassLoader loader=new ClassLoader(NativeTreeRotation058Probe.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException {if(!advice.containsKey(n))return super.loadClass(n,resolve);Class<?> c=findLoadedClass(n);if(c==null)c=defineClass(n,advice.get(n),0,advice.get(n).length);if(resolve)resolveClass(c);return c;}
        };
        var loc=new ClassFileLocator.Compound(new ClassFileLocator.Simple(raw),new ClassFileLocator.Simple(advice),ClassFileLocator.ForClassLoader.ofSystemLoader());
        var builder=new ByteBuddy().redefine(TypePool.Default.of(loc).describe("zombie.iso.fboRenderChunk.FBORenderTrees").resolve(),loc);
        Method matcher=PatchWeavingProbe.class.getDeclaredMethod("matcher",Class.class,String.class,boolean.class);matcher.setAccessible(true);
        for(String[] p:new String[][]{{"Depth","calculateDepth"},{"Projection","pushProjectionMatrix"},{"View","pushModelViewMatrix"}}) {
            Class<?> a=loader.loadClass("vpiso.Patch_RotationTree"+p[0]);
            var match=(net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription>)matcher.invoke(null,a,p[1],true);
            builder=builder.visit(Advice.to(a,loc).on(match));
        }
        if(light) {
            Class<?> a=loader.loadClass("vpiso.Patch_NativeSourceTree");
            builder=builder.visit(Advice.to(a,loc).on((net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription>)matcher.invoke(null,a,"renderTree",true)));
            byte[] vbo;
            try(var jar=new java.util.jar.JarFile(source)){vbo=jar.getInputStream(jar.getEntry("zombie/core/opengl/VBORenderer.class")).readAllBytes();}
            var vloc=new ClassFileLocator.Compound(new ClassFileLocator.Simple(Map.of("zombie.core.opengl.VBORenderer",vbo)),loc);
            Class<?> a2=loader.loadClass("vpiso.Patch_NativeGroundVBODraw");
            byte[] woven=new ByteBuddy().redefine(TypePool.Default.of(vloc).describe("zombie.core.opengl.VBORenderer").resolve(),vloc)
                    .visit(Advice.to(a2,vloc).on((net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription>)matcher.invoke(null,a2,"renderRun",true))).make().getBytes();
            Path vfile=folder.resolve("zombie/core/opengl/VBORenderer.class");Files.createDirectories(vfile.getParent());Files.write(vfile,woven);
        }
        Files.write(folder.resolve("zombie/iso/fboRenderChunk/FBORenderTrees.class"),builder.make().getBytes());
        System.out.println("Wrote actual "+(old?"057 depth negative control":"058 production advice")+" with consistent "+(overrides.equals("-")?"official":"PZO")+" original package identity: "+folder);
    }
    static RotationCamera.Snapshot rotation(int frame) {
        float x=10f+frame*.015f,y=12f+frame*.01f;
        Matrix4f p=new Matrix4f(),v=new Matrix4f();
        IsoCameraMath.matrices(new IsoCameraMath.Fit(x,y,8f),x,y,SIZE,SIZE,p,v,new float[3],yaw);
        return new RotationCamera.Snapshot(p,v,x,y,0,SIZE,SIZE,1f,yaw);
    }
    static final Class<?>[] NONE=new Class<?>[0];
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("weave")){weave(args[1],args[2],args[3],args[4].equals("old"));return;}
        mode=args.length>0?args[0]:"fixed";
        System.out.println("ROTATED LEAF DEPTH MODE="+mode);
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);
        var af=F.getDeclaredField("allocate");af.setAccessible(true);af.set(null,uf.getType().getMethod("allocateInstance",Class.class));
        var us=F.getDeclaredField("unsafe");us.setAccessible(true);us.set(null,uf.get(null));
        call("fixture",NONE);player=NativeTreeRender055Probe.staticGet("player");
        renderer=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell","instance",null);
        NativeTreeRender055Probe.template=NativeTreeRender055Probe.staticGet("square");
        NativeTreeRender055Probe.renderer=renderer;
        Object color=GameAccess.type("zombie.core.textures.ColorInfo").getConstructor(float.class,float.class,float.class,float.class).newInstance(1f,1f,1f,1f);
        Object colors=Array.newInstance(color.getClass(),4);for(int i=0;i<4;i++)Array.set(colors,i,color);
        NativeTreeRender055Probe.colors=colors;
        set(player,"ecsComponentMap",new HashMap<>());
        Object cell=fresh("zombie.iso.IsoCell");set(cell,"stencilAreas",new ArrayList<>());
        set(GameAccess.field("zombie.iso.IsoWorld","instance",null),"currentCell",cell);
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long win=GLFW.glfwCreateWindow(SIZE,SIZE,"native actual dual tree textures",0,0);
        try {
            GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();GL30.glBindVertexArray(GL30.glGenVertexArrays());
            zombie.core.VBO.GLVertexBufferObject.init();NativeTreeRender055Probe.setupNative();
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            NativeTreeRender055Probe.attach(GL30.GL_COLOR_ATTACHMENT0,GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE);
            NativeTreeRender055Probe.attach(GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH_STENCIL,GL30.GL_UNSIGNED_INT_24_8);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
            for(float angle:new float[]{17.25f,90f,180f,270f}) {yaw=angle;System.out.println("ROTATION YAW="+yaw);for(boolean xl:new boolean[]{false,true})check(xl);}
            if(mode.equals("old")&&totalWrong==0)throw new AssertionError("057 negative control failed to reproduce");
            if(!mode.equals("old")&&totalWrong!=0)throw new AssertionError("058 production rotation tree layers overlap: "+totalWrong);
            System.out.println("ROTATED NATIVE TREE 058 "+mode+" PASS: actual production-woven native render with ground depth; four yaws, 48 moving frames each, normal and XL dual textures; wrongFront="+totalWrong+"; foreground depth bounds="+boundChecks);
        } finally {GLFW.glfwDestroyWindow(win);GLFW.glfwTerminate();}
    }
    static void check(boolean xl)throws Exception {
        String label=xl?"VPISO_layers058_XL":"VPISO_layers058_normal";
        Object tree=NativeTreeRender055Probe.tree(label,10,12);
        Object bark=texture(label+"_bark",false),leaves=texture(label+"_leaves",true);
        List<Object> barkTextures=new ArrayList<>(List.of(bark));
        Set<Object> leafTextures=Collections.newSetFromMap(new IdentityHashMap<>());leafTextures.add(leaves);
        Object sprite=call("sprite",new Class<?>[]{String.class,Object.class},label,bark);
        set(sprite,"properties",GameAccess.type("zombie.core.properties.PropertyContainer").getConstructor().newInstance());set(tree,"sprite",sprite);
        Object leafSprite=call("sprite",new Class<?>[]{String.class,Object.class},label+"_leaf",leaves);
        Object attached=fresh("zombie.iso.sprite.IsoSpriteInstance");set(attached,"parentSprite",leafSprite);
        set(tree,"attachedAnimSprite",new ArrayList<>(List.of(attached)));
        if(xl) {
            for(String part:new String[]{"trunk","crown"}) {
                int offset=part.equals("trunk")?300:0,height=part.equals("trunk")?212:303;
                Object partBark=trim(texture(label+"_"+part+"_bark",false),offset,height);
                Object partLeaves=trim(texture(label+"_"+part+"_leaves",true),offset,height);
                barkTextures.add(partBark);leafTextures.add(partLeaves);
                Object p=call("sprite",new Class<?>[]{String.class,Object.class},label+"_"+part,partBark);
                call("registerSprite",new Class<?>[]{String.class,Object.class},label+"_"+part,p);
                Object q=call("sprite",new Class<?>[]{String.class,Object.class},label+"_"+part+"_leaf",partLeaves);
                call("registerSprite",new Class<?>[]{String.class,Object.class},label+"_"+part+"_leaf",q);
            }
            call("registerDescription",new Class<?>[]{String.class,String.class,String.class},label,label+"_crown",label+"_trunk");
            call("registerDescription",new Class<?>[]{String.class,String.class,String.class},label+"_leaf",label+"_crown_leaf",label+"_trunk_leaf");
        }
        set(tree,"fadeAlpha",1f);set(tree,"cutawayAlpha",1f);
        set(player,"numNearbyBuildingsRooms",xl?1f:0f);
        Object state=GameAccess.field("zombie.iso.IsoCamera","frameState",null);
        int maxWrong=0,minGreen=Integer.MAX_VALUE,prevWrong=-1;
        for(int frame=0;frame<48;frame++) {
            set(state,"camCharacterX",10f+frame*.015f);set(state,"camCharacterY",12f+frame*.01f);
            NativeTrees bridge=new NativeTrees();snapshot=rotation(frame);bridge.rotation=snapshot;bridge.add(get(tree,"square"));bridge.capture(renderer,0);
            Object batch=get(bridge,"batch");List<Object> records=(List<Object>)get(batch,"trees");
            if(records.size()!=(xl?2:1))throw new AssertionError("Actual producer expected "+(xl?2:1)+" records: "+records.size());
            for(Object r:records)if(!leafTextures.contains(get(r,"texture2")))throw new AssertionError("Real producer leaves selection changed");
            if(!mode.equals("old"))for(Object r:records)checkForegroundBound(batch,r);
            ByteBuffer combined=render(bridge);
            for(Object b:barkTextures)upload(b,false,true);ByteBuffer leafOnly=render(bridge);for(Object b:barkTextures)upload(b,false,false);
            int wrong=0,green=0,red=0;
            for(int i=0;i<SIZE*SIZE;i++) {
                int r=combined.get(i*4)&255,g=combined.get(i*4+1)&255;
                int expectedG=leafOnly.get(i*4+1)&255;
                if(expectedG>128) {green++;if(g<expectedG-8)wrong++;}
                if(r>128&&g<64)red++;
            }
            maxWrong=Math.max(maxWrong,wrong);minGreen=Math.min(minGreen,green);
            if(frame==0||wrong!=prevWrong||frame==47)System.out.println("native layers xl="+xl+" frame="+frame+" leafPixels="+green+" redPixels="+red+" wrongFront="+wrong+" records="+records.size()+" alpha="+get(records.get(records.size()-1),"cutawayAlpha")+" stencil="+get(records.get(records.size()-1),"useStencil"));
            prevWrong=wrong;bridge.release();
        }
        System.out.println("native layers result xl="+xl+" maxWrong="+maxWrong+" minLeafPixels="+minGreen);
        if(minGreen<=0)throw new AssertionError("No visible leaves; do not accept a vacuous render");
        totalWrong+=maxWrong;
        System.out.println("ROTATION READOUT mode="+mode+" yaw="+yaw+" xl="+xl+" wrong="+maxWrong+" leaves="+minGreen);
    }
    static ByteBuffer render(NativeTrees bridge)throws Exception {
        GL11.glViewport(0,0,SIZE,SIZE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glClearStencil(0);GL11.glStencilMask(255);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,0f);GL11.glDisable(GL11.GL_CULL_FACE);
        drawGround();GL14.glBlendEquation(GL14.GL_FUNC_ADD);bridge.render();
        if(Boolean.TRUE.equals(getStatic(IsometricBridge.class,"failed")))throw new AssertionError("NativeTrees.render failed; inspect cause logged above");
        ByteBuffer pixels=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        if(GL11.glGetError()!=0)throw new AssertionError("GL error");return pixels;
    }
    // Check that actual woven leaf bias is at least 100 times smaller than the
    // projected gap to a surface 0.1 world units toward the camera. This bounds
    // the fix across the full tree-height range, rather than removing depth tests.
    static void checkForegroundBound(Object batch,Object tree)throws Exception {
        Method m=batch.getClass().getDeclaredMethod("calculateDepth",tree.getClass(),boolean.class,boolean.class,boolean.class);m.setAccessible(true);
        float bias;
        var previous=RotationTrees.begin(snapshot);
        try {bias=(Float)m.invoke(batch,tree,true,false,false);if((Float)m.invoke(batch,tree,false,false,false)!=0f)throw new AssertionError("Bark world depth moved");}
        finally {RotationTrees.end(previous);}
        if(!(bias<0))throw new AssertionError("Leaf depth must move toward camera");
        float x=((Number)get(tree,"x")).floatValue(),y=((Number)get(tree,"y")).floatValue(),z=((Number)get(tree,"z")).floatValue();
        float rootZ=snapshot.view().transform(new Vector4f(snapshot.camX()-x,(z-snapshot.camZ())*2.4494896f,snapshot.camY()-y,1)).z;
        Matrix4f p=snapshot.projection();
        for(double dz:new double[]{-64,-16,0,16,64}) {
            double vz=rootZ+dz,behind=windowDepth(p,vz),front=windowDepth(p,vz+.1);
            double w=p.m23()*vz+p.m33(),windowBias=Math.abs(bias/w);
            if(!(behind-front>windowBias*100))throw new AssertionError("Leaf bias crosses foreground depth bound: bias="+windowBias+" gap="+(behind-front));
            boundChecks++;
        }
    }
    static double windowDepth(Matrix4f p,double z){return ((p.m22()*z+p.m32())/(p.m23()*z+p.m33()))*.5+.5;}
    static void drawGround() {
        if(backgroundProgram==0) {
            // The oracle compares the green leaf channel. Keep ground green zero so
            // legitimate translucent foliage over red bark has the same reference.
            backgroundProgram=NativeVisibility053Probe.program("#version 330\nlayout(location=0)in vec3 p;uniform mat4 m;void main(){gl_Position=m*vec4(p,1);}","#version 330\nout vec4 c;void main(){c=vec4(.2,0,.2,1);}");
            backgroundVbo=GL15.glGenBuffers();
            float[] plane={-64,0,-64,64,0,-64,64,0,64,-64,0,-64,64,0,64,-64,0,64};
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,backgroundVbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,plane,GL15.GL_STATIC_DRAW);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);
        GL20.glUseProgram(backgroundProgram);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(backgroundProgram,"m"),false,new Matrix4f(snapshot.projection()).mul(snapshot.view()).get(BufferUtils.createFloatBuffer(16)));
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,backgroundVbo);GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0L);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
        GameAccess.set("zombie.core.textures.Texture","lastTextureID",null,-1);GameAccess.staticCall("zombie.core.ShaderHelper","forgetCurrentlyBound");
    }
    static Object texture(String name,boolean leaves)throws Exception {Object t=NativeTreeRender055Probe.texture(name);upload(t,leaves,false);if(!leaves)GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);return t;}
    static void upload(Object t,boolean leaves,boolean invisible)throws Exception {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,(Integer)get(get(t,"dataid"),"id"));
        ByteBuffer b=BufferUtils.createByteBuffer(384*512*4);
        for(int y=0;y<512;y++)for(int x=0;x<384;x++) {
            boolean coverage=leaves? x>=64&&x<320&&y>=24&&y<380 : x>=150&&x<234&&y>=12&&y<488;
            b.put((byte)(leaves?0:255)).put((byte)(leaves?255:0)).put((byte)0).put((byte)(coverage&&!invisible?255:0));
        }
        b.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,384,512,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
    }
    static Object trim(Object t,int offset,int height)throws Exception {
        set(t,"offsetY",(float)offset);set(t,"height",height);
        set(t,"yStart",offset/512f);set(t,"yEnd",(offset+height)/512f);return t;
    }
    static Object getStatic(Class<?> c,String n)throws Exception {Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    static Object call(String n,Class<?>[] t,Object...v)throws Exception{return NativeTreeRender055Probe.call(n,t,v);}
    static Object fresh(String n)throws Exception{return NativeTreeRender055Probe.fresh(n);}
    static Object get(Object o,String n)throws Exception{return NativeTreeRender055Probe.get(o,n);}
    static void set(Object o,String n,Object v)throws Exception{NativeTreeRender055Probe.set(o,n,v);}
}
