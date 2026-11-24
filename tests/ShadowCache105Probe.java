import java.lang.reflect.Constructor;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.FarRelief;
import vpiso.*;

/** Real settled VP cascades versus fresh depth maps; no running-game attachment. */
public final class ShadowCache105Probe {
    static final int SIZE=256, CASCADES=2;
    static final IsometricEffects FULL=new IsometricEffects(true,1,0,64,SIZE,false,1,1,1,1);
    static final IsometricEffects LITE=new IsometricEffects(true,1,0,64,SIZE,false,1,1,1,1,false,false,true);
    static Object pass,layers,lamps,spots;
    static boolean[] originalLampFlags;
    static int cases,depthComparisons;
    public static void main(String[] args)throws Exception {
        boolean baseline=args.length>0&&args[0].equals("baseline104");
        String pack=args.length>(baseline?1:0)?args[baseline?1:0]:"library";
        require(GLFW.glfwInit(),"GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(64,64,"shadow105 cache lifetime",0,0);
        try {
            require(window!=0,"GLFW window");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));GlProgram.onUse(IsometricShaders::onUse);
            Frame frame=NativeSurfaceReceiverProbe.frame();
            ChunkMeshData roof=(ChunkMeshData)NativeSurfaceReceiverProbe.mesh(frame.scene,0,roof(-4,2.45f));
            frame.scene.meshModelSquares[0]=-1L;draw(frame,3);
            pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
            layers=field("ShadowPass","layers",pass);lamps=field("ShadowPass","lamps",pass);spots=field("ShadowPass","spots",pass);
            // No lamps exist in this fixture. Seed a nonempty drawn table so its
            // invalidator is exercised instead of vacuously accepting length zero.
            originalLampFlags=(boolean[])field("LampShadows","drawn",lamps);
            GameAccess.set("viewpoint.render.LampShadows","drawn",lamps,new boolean[2]);
            Object[] cache=(Object[])field("CascadeLayers","layers",layers);
            int[] texture=cacheTextures(cache);require(texture[0]!=0&&texture[1]!=0,"Settled cascade textures not allocated");
            Set<?>[] held=held();require(!held[0].isEmpty()&&!held[1].isEmpty(),"Fixture did not enter held caster caches");
            float[] first=depth();require(covered(first)>20,"Empty actual sun depth map");
            seed(pass);frame.scene.meshModelSquares[0]=0L;
            NativeShadowMaps.prepareBasis(pass,frame.scene);
            if(baseline) {
                require(!all(flags("ShadowPass",pass),true),"104 needless valid reset not reproduced");
                require(cacheTextures(cache)[0]==0&&held()[0].isEmpty(),"104 full settled-cache release not reproduced");
                System.out.println("Pinned104 shadow-cache negative PASS: camera-only mask change cleared valid flags, freed settled depth textures and dropped held casters.");return;
            }
            assertKept(cache,texture,held,"first camera cut");
            for(long cut:new long[]{-1L,0L,1L,3L,0x5555555555555555L,-1L}) {
                frame.scene.meshModelSquares[0]=cut;
                frame.scene.meshPlan[0]=0;frame.scene.meshPlanLength[0]=cut==0?0:1;
                seed(pass);NativeShadowMaps.prepareBasis(pass,frame.scene);
                assertKept(cache,texture,held,"camera cut="+cut);
                draw(frame,1);equal(first,depth(),"camera cut changed physical depth");
                ModelShadowBasisProbe.clearLayers();draw(frame,1);equal(first,depth(),"fresh camera-cut oracle");
                cache=(Object[])field("CascadeLayers","layers",layers);texture=cacheTextures(cache);held=held();cases++;
            }
            // A screen-external second roof still contributes to physical depth.
            ChunkMeshData extra=(ChunkMeshData)NativeSurfaceReceiverProbe.mesh(frame.scene,1,roof(8,3.5f));
            frame.scene.meshModelSquares[1]=0;frame.scene.meshFlags[1]&=~1;
            freshEquivalent(frame,"new offscreen caster");require(covered(depth())>covered(first),"Offscreen roof did not contribute");
            frame.scene.meshes[0]=extra;frame.scene.meshes[1]=roof;
            freshEquivalent(frame,"caster order swap");
            NativeSurfaceReceiverProbe.retire(extra);frame.scene.meshes[0]=roof;frame.scene.meshes[1]=null;frame.scene.meshCount=1;
            freshEquivalent(frame,"retired caster removal");equal(first,depth(),"Removed caster depth persisted");
            ChunkMeshData replacement=(ChunkMeshData)NativeSurfaceReceiverProbe.mesh(frame.scene,0,roof(-4,4.5f));
            NativeSurfaceReceiverProbe.retire(roof);freshEquivalent(frame,"same-slot caster replacement");
            require(!Arrays.equals(first,depth()),"Replacement roof height had no physical effect");
            basisInvalidators(frame);
            NativeSurfaceReceiverProbe.retire(replacement);
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error");
            System.out.println("Shadow cache105 GPU PASS pack="+pack+" cases="+cases+" exactDepthComparisons="+depthComparisons
                +"; camera-only cuts retain sun/lamp/spot flags and settled texture/held identities, exact fresh sun-depth equality, offscreen add/order/remove/replace, pass/grid/levels/mutable-bands/lightweight invalidation preserved. Controlled fixture; no game FPS claim.");
        } finally {
            if(lamps!=null&&originalLampFlags!=null)GameAccess.set("viewpoint.render.LampShadows","drawn",lamps,originalLampFlags);
            IsometricBridge.rendering(null);if(window!=0)GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
    static float[][] roof(float x,float height) {
        float[][] p=NativeSurfaceReceiverProbe.horizontal(height,2.5f);for(float[] v:p)v[0]+=x;return p;
    }
    static void draw(Frame frame,int times) {
        for(int i=0;i<times;i++) {frame.number++;IsometricBridge.modelRendering(frame,FULL);
            require(NativeShadowMaps.draw(frame.scene,SIZE,CASCADES,null,FULL),"Actual shadow maps unavailable");}
    }
    static void freshEquivalent(Frame frame,String name) {
        draw(frame,16);float[] reused=depth();ModelShadowBasisProbe.clearLayers();draw(frame,1);equal(reused,depth(),name);cases++;
    }
    static float[] depth() {
        float[] data=new float[SIZE*SIZE*CASCADES];
        GL45.glGetTextureSubImage(NativeShadowMaps.texture(),0,0,0,0,SIZE,SIZE,CASCADES,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,data);
        return data;
    }
    static int covered(float[] data) {int n=0;for(float v:data)if(v<.99999f)n++;return n;}
    static void equal(float[] a,float[] b,String message) {
        int different=0;float worst=0;for(int i=0;i<a.length;i++)if(Float.floatToIntBits(a[i])!=Float.floatToIntBits(b[i])){different++;worst=Math.max(worst,Math.abs(a[i]-b[i]));}
        require(different==0,message+": different="+different+" worst="+worst);depthComparisons++;
    }
    static Object field(String type,String name,Object value){return GameAccess.field("viewpoint.render."+type,name,value);}
    static boolean[] flags(String type,Object value){return (boolean[])field(type,type.equals("ShadowPass")?"valid":"drawn",value);}
    static boolean all(boolean[] flags,boolean value){for(boolean flag:flags)if(flag!=value)return false;return flags.length>0;}
    static void seed(Object p) {
        Arrays.fill(flags("ShadowPass",p),true);
        Arrays.fill(flags("LampShadows",field("ShadowPass","lamps",p)),true);
        Arrays.fill(flags("Spots",field("ShadowPass","spots",p)),true);
    }
    static int[] cacheTextures(Object[] cache) {
        int[] ids=new int[cache.length];for(int i=0;i<ids.length;i++)ids[i]=((Number)field("CascadeLayer","texture",cache[i])).intValue();return ids;
    }
    static Set<?>[] held() {
        Object[] casters=(Object[])field("CascadeLayers","casters",layers);Set<?>[] data=new Set<?>[casters.length];
        for(int i=0;i<data.length;i++)data[i]=Set.copyOf((Set<?>)field("LayerCasters","held",casters[i]));return data;
    }
    static void assertKept(Object[] cache,int[] texture,Set<?>[] held,String name) {
        require(all(flags("ShadowPass",pass),true),name+" reset solar validity");
        require(all(flags("LampShadows",lamps),true),name+" reset lamp validity");
        require(all(flags("Spots",spots),true),name+" reset spot validity");
        require(Arrays.equals(texture,cacheTextures(cache)),name+" freed settled textures");
        Set<?>[] now=held();for(int i=0;i<now.length;i++)require(now[i].equals(held[i]),name+" dropped held caster identities");
        for(Object value:cache)require((Boolean)field("CascadeLayer","valid",value),name+" invalidated settled layer");
    }
    static void changed(Frame frame,String name) {
        seed(pass);NativeShadowMaps.prepareBasis(pass,frame.scene);
        require(all(flags("ShadowPass",pass),false),name+" retained solar validity");
        require(all(flags("LampShadows",lamps),false),name+" retained lamp validity");
        require(all(flags("Spots",spots),false),name+" retained spot validity");
        for(int id:cacheTextures((Object[])field("CascadeLayers","layers",layers)))require(id==0,name+" retained settled texture");
        cases++;
    }
    static void basisInvalidators(Frame frame)throws Exception {
        float[] h=new float[96*96];FarRelief.Field grid=FarRelief.Field.of(96,96,h,h);
        frame.scene.relief(new FarRelief.Lift(grid,.5f,0,0,0,new float[]{0,1,2}));changed(frame,"new relief grid");
        draw(frame,1);frame.scene.relief(new FarRelief.Lift(grid,.5f,.01f,.1,.1,new float[]{0,1,2}));
        seed(pass);int[] ids=cacheTextures((Object[])field("CascadeLayers","layers",layers));NativeShadowMaps.prepareBasis(pass,frame.scene);
        require(all(flags("ShadowPass",pass),true)&&Arrays.equals(ids,cacheTextures((Object[])field("CascadeLayers","layers",layers))),"Equivalent Lift wrapper invalidated");cases++;
        frame.scene.relief(new FarRelief.Lift(grid,.7f,0,0,0,new float[]{0,1,2}));changed(frame,"relief levels");
        draw(frame,1);frame.scene.relief.bands()[1]+=.125f;changed(frame,"mutable relief bands");
        draw(frame,1);frame.scene.relief(new FarRelief.Lift(FarRelief.Field.of(96,96,h,h),.7f,0,0,0,new float[]{0,1.125f,2}));changed(frame,"replacement grid");
        draw(frame,1);IsometricBridge.modelRendering(frame,LITE);var previous=LightweightShadows.enter(frame.scene,LITE);
        try {changed(frame,"lightweight caster mode");}finally{LightweightShadows.restore(previous);}
        draw(frame,1);frame.scene.relief(null);changed(frame,"relief removed");draw(frame,1);
        Constructor<?> constructor=GameAccess.type("viewpoint.render.ShadowPass").getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object other=constructor.newInstance(field("ShadowPass","models",pass),field("ShadowPass","farMaps",pass),field("ShadowPass","weatherMap",pass),field("CascadeLayers","snow",layers));
        NativeShadowMaps.prepareBasis(other,frame.scene);changed(frame,"different pass identity");
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
