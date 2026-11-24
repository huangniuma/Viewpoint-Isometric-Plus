import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;

/** Actual woven cook/collect/cascades: selective shadows, receiving surfaces and switch restoration. */
public final class LightweightShadow082Probe {
    static final Class<?>[] NONE = {};
    static final NativeSurfaceReceiverProbe N = new NativeSurfaceReceiverProbe();
    static final IsometricEffects FULL = new IsometricEffects(true,1,1,64,256,false,0,1,1,1,false,false,false);
    static final IsometricEffects LITE = new IsometricEffects(true,1,1,64,256,false,0,1,1,1,false,false,true);
    static Object page;
    static int cases;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(64,64,"lightweight shadows082",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(args[0],args[0].equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();
            Frame template=N.frame();Object m=N.mesh(template.scene,0,N.horizontal(-4,.01f));
            page=((Object[])GameAccess.field("viewpoint.render.ChunkMeshData","pages",m))[0];
            geometry();plants();packed();models();lampCache();lampMaps();
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println("Lightweight shadows082 GPU PASS pack="+args[0]+" cases="+cases+"; real woven recipes, mixed material ranges, plant cards, native/model sun maps, caster cache toggles, receiver preservation, rigid item/furniture classification, lamp cache invalidation and ordinary VP isolation.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void geometry()throws Exception {
        Frame f=N.frame();
        Object recipe=recipe();Object batch=batch(recipe);
        raw(batch,-5,1,0);raw(batch,5,0,1); // Wall art and furniture share one texture page.
        ChunkMeshData mixed=cook(recipe);install(f,mixed);
        int[] first=mixed.first.clone(),counts=mixed.count.clone();
        float[] full=draw(f,FULL,false),lite=draw(f,LITE,false),restored=draw(f,FULL,false);
        require(dark(full)>dark(lite)+10 && dark(lite)>10,"Mixed furniture caster was not selectively removed");
        require(difference(full,restored)==0,"Turning lightweight off retained stale solar depth");
        Object only=recipe();raw(batch(only),-5,1,0);ChunkMeshData wall=cook(only);install(f,wall);
        NativePlants072Probe.invalidate();float[] expected=draw(f,FULL,false);require(difference(expected,lite)==0,"Lightweight altered retained building projection diff="+difference(expected,lite)+" expected="+dark(expected)+" lite="+dark(lite));
        install(f,mixed);NativePlants072Probe.invalidate();require(difference(draw(f,LITE,true),lite)==0,"Model and native caster selection disagree");
        IsometricBridge.rendering(f);LightweightShadows.enter(f.scene,LITE);
        var previous=LightweightShadows.forMask(SceneData.RAIN);
        require(!LightweightShadows.active(),"Rain coverage was filtered as a shadow");LightweightShadows.restore(previous);
        LightweightShadows.restore(null);IsometricBridge.rendering(null);
        // Receiver draw uses the same CASTS_SHADOW mask, but must remain outside caster scope.
        GameAccess.call("viewpoint.render.Meshes","prepare",null,new Class<?>[]{SceneData.class},f.scene);
        GameAccess.set("viewpoint.render.Meshes","bucketCount",null,0);
        ((Map<?,?>)GameAccess.field("viewpoint.render.Meshes","byPage",null)).clear();
        require(!LightweightShadows.collect(f.scene,0),"Receiver collection intercepted outside shadow scope");
        GameAccess.call("viewpoint.render.Meshes","collect",null,new Class<?>[]{SceneData.class,int.class,boolean.class},f.scene,0,false);
        int submitted=0;
        var buckets=(List<?>)GameAccess.field("viewpoint.render.Meshes","buckets",null);
        int bucketCount=((Number)GameAccess.field("viewpoint.render.Meshes","bucketCount",null)).intValue();
        for(int i=0;i<bucketCount;i++) {
            Object b=buckets.get(i);int n=((Number)GameAccess.field("viewpoint.render.Meshes$Bucket","count",b)).intValue();
            int[] commands=(int[])GameAccess.field("viewpoint.render.Meshes$Bucket","commands",b);
            for(int j=0;j<n;j++)submitted+=commands[j*5];
        }
        require(submitted==Arrays.stream(counts).sum()/4*6,"Excluded furniture stopped receiving shadows");
        require(Arrays.equals(first,mixed.first)&&Arrays.equals(counts,mixed.count),"Shared visible mesh changed");
        N.retire(mixed);N.retire(wall);cases+=6;
    }
    static void plants()throws Exception {
        Frame f=N.frame();Object recipe=recipe();Object batch=batch(recipe);
        Object tree=N.allocate(GameAccess.type("zombie.iso.objects.IsoTree"));
        GameAccess.set("viewpoint.world.WorldMesher","recipe",null,recipe);
        GameAccess.set("viewpoint.world.WorldMesher","object",null,5);LightweightShadows.source(tree);
        plant(batch,-5,5);plant(batch,5,6);ChunkMeshData mixed=cook(recipe);install(f,mixed);
        float[] full=draw(f,FULL,false),lite=draw(f,LITE,false);
        require(dark(full)>dark(lite)+5&&dark(lite)>5,"Trees and bushes sharing plant page were not separated");
        Object only=recipe();Object onlyBatch=batch(only);
        GameAccess.set("viewpoint.world.WorldMesher","recipe",null,only);LightweightShadows.source(tree);
        plant(onlyBatch,-5,5);ChunkMeshData trees=cook(only);install(f,trees);
        NativePlants072Probe.invalidate();require(difference(draw(f,FULL,false),lite)==0,"Tree shape or sway changed in lightweight shadows");
        N.retire(mixed);N.retire(trees);cases+=2;
    }
    static void models()throws Exception {
        // Only Models.own is filtered. Skinned characters/animals and vehicles use other entries.
        IsometricControls.tick();var setting=(LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","lightweightShadows",null);
        setting.set(true);IsometricControls.tick();
        Frame f=N.frame();N.addModel(f.scene,N.horizontal(2,1),false,false);
        Object furniture=N.allocate(GameAccess.type("zombie.iso.IsoObject"));
        LightweightShadows.objectModels(f,furniture,null,0);
        require((f.scene.models.flags(0)&ModelDraws.SHADOW)==0&&(f.scene.models.flags(0)&ModelDraws.COLOUR)!=0,"Rigid furniture colour/shadow separation failed");
        N.addModel(f.scene,N.horizontal(2,1),false,false);
        Object item=N.allocate(GameAccess.type("zombie.inventory.InventoryItem"));
        LightweightShadows.objectModels(f,furniture,item,1);
        require((f.scene.models.flags(1)&ModelDraws.SHADOW)!=0,"Placed inventory item shadow excluded");
        N.addModel(f.scene,N.horizontal(2,1),true,false);N.addModel(f.scene,N.horizontal(2,1),false,true);
        require((f.scene.models.flags(2)&ModelDraws.SHADOW)!=0&&(f.scene.models.flags(3)&ModelDraws.SHADOW)!=0,"Character/animal or vehicle shadow excluded");
        for(String type:new String[]{"wall","stairsBN","doorN","doorFrW","windowFW","WestRoofT"})require(LightweightShadows.buildingType(type),"Building type excluded: "+type);
        for(String type:new String[]{"normal","isMoveAbleObject","radio","tree"})require(!LightweightShadows.buildingType(type),"Nonbuilding type included: "+type);
        require(LITE.withWater(true).lightweightShadows()&&!LITE.waterOnly().needsShadows(),"Queued settings copy failed");
        setting.set(false);IsometricControls.tick();cases+=5;
    }
    static void packed()throws Exception {
        var file=NativePackedPlants072Probe.ROOT.resolve(NativePackedPlants072Probe.FILES[2]).toFile();
        PackModel model=PackModels.of(file);
        PackData data=(PackData)GameAccess.call("viewpoint.packs.PackLoads","load",null,
                new Class<?>[]{java.io.File.class,java.nio.file.Path.class},file,NativePackedPlants072Probe.ROOT);
        PackModels.onItsWay();model.read(data);PackModels.publish();
        Object recipe=recipe();packedOp(recipe,model,-5,TreeModelScope.TREE);packedOp(recipe,model,5,TreeModelScope.BUSH);
        ChunkMeshData mixed=cook(recipe);Frame f=N.frame();install(f,mixed);
        int[] original=(int[])GameAccess.field("viewpoint.render.ChunkMeshData","modelRuns",mixed);
        int[] saved=original.clone();
        float[] full=draw(f,FULL,false),lite=draw(f,LITE,false),restored=draw(f,FULL,false);
        require(dark(full)>dark(lite)+3&&dark(lite)>3,"Packed trees and props were not filtered");
        require(difference(full,restored)==0,"Packed collection cache retained lightweight runs");
        require(GameAccess.field("viewpoint.render.ChunkMeshData","modelRuns",mixed)==original&&Arrays.equals(saved,original),"Packed run table was not restored");
        Object only=recipe();packedOp(only,model,-5,TreeModelScope.TREE);ChunkMeshData tree=cook(only);install(f,tree);
        NativePlants072Probe.invalidate();require(difference(draw(f,FULL,false),lite)==0,"Retained packed tree changed");
        N.retire(mixed);N.retire(tree);cases+=4;
    }
    static void packedOp(Object recipe,PackModel model,float x,int flags) {
        Object bind=new viewpoint.packs.PackBind(model,0,0,0,0,1);
        GameAccess.call("viewpoint.world.Recipe","model",null,
                new Class<?>[]{recipe.getClass(),viewpoint.packs.PackBind.class,boolean.class,float.class,float.class,float.class,int.class,int.class,int.class,int.class},
                recipe,bind,false,-x,0f,0f,0,flags,0,0);
    }
    static void lampCache()throws Exception {
        Frame f=N.frame();Object recipe=recipe();raw(batch(recipe),0,1,0);install(f,cook(recipe));
        draw(f,FULL,false);Object pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        Object lamps=GameAccess.field("viewpoint.render.ShadowPass","lamps",pass);
        boolean[] drawn=(boolean[])GameAccess.field("viewpoint.render.LampShadows","drawn",lamps);
        for(IsometricEffects effect:new IsometricEffects[]{LITE,FULL}) {
            Arrays.fill(drawn,true);IsometricBridge.rendering(f);var previous=LightweightShadows.enter(f.scene,effect);
            NativeShadowMaps.prepareBasis(pass,f.scene);LightweightShadows.restore(previous);
            for(boolean value:drawn)require(!value,"Lamp map retained previous caster mode");cases++;
        }
        IsometricBridge.rendering(null);require(!LightweightShadows.active(f.scene),"Ordinary VP inherited filter");
        N.retire(f.scene.meshes[0]);
    }
    static void lampMaps()throws Exception {
        if(ShaderPacks.active().pipeline.lampShadows==0)return;
        Frame f=N.frame();f.scene.sunStrength=0;
        NativeShadow068Probe.lamp(f.scene,0,0,true,4);f.scene.lightCount=f.scene.lampShadowCount=1;
        Object recipe=recipe();Object batch=batch(recipe);raw(batch,-3,1,0);raw(batch,3,0,1);
        ChunkMeshData mixed=cook(recipe);install(f,mixed);
        float[] full=lamps(f,FULL),lite=lamps(f,LITE),restored=lamps(f,FULL);
        require(dark(full)>dark(lite)+5&&dark(lite)>5,"Lamp caster mask bypassed lightweight filtering");
        require(difference(full,restored)==0,"Lamp cache failed to restore full casters");
        N.retire(mixed);cases+=2;
    }
    static float[] lamps(Frame f,IsometricEffects effect) {
        for(int i=0;i<12;i++) {
            f.number++;IsometricBridge.rendering(f);
            require(NativeShadowMaps.draw(f.scene,256,1,null,effect),"Lamp map not ready");
        }
        IsometricBridge.rendering(null);
        Object pass=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
        Object lamps=GameAccess.field("viewpoint.render.ShadowPass","lamps",pass);
        int texture=((Number)GameAccess.field("viewpoint.render.LampShadows","texture",lamps)).intValue();
        int size=((Number)GameAccess.field("viewpoint.render.LampShadows","size",lamps)).intValue();
        float[] data=new float[size*size*6];GL45.glGetTextureSubImage(texture,0,0,0,0,size,size,6,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,data);
        return data;
    }
    static float[] draw(Frame frame,IsometricEffects effects,boolean model) {
        frame.number++;frame.scene.cameraCut=true;
        if(model)IsometricBridge.modelRendering(frame,effects);else IsometricBridge.rendering(frame);
        if(model) {
            // Prepare the actual model uploads using the same native producer, then exercise the 3D entry.
            require(NativeShadowMaps.draw(frame.scene,256,1,null,effects),"Map not ready");
            FrameContext context=new FrameContext();context.scene=frame.scene;context.pipeline=ShaderPacks.active().pipeline;context.sun=true;
            Patch_ModelShadows.draw(GameAccess.field("viewpoint.render.WorldRenderer","shadows",null),context,effects);
        } else require(NativeShadowMaps.draw(frame.scene,256,1,null,effects),"Map not ready");
        IsometricBridge.rendering(null);
        float[] data=new float[256*256*3];GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,NativeShadowMaps.texture());
        GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,data);
        return Arrays.copyOf(data,256*256);
    }
    static void install(Frame f,ChunkMeshData mesh) {f.scene.meshes[0]=mesh;f.scene.meshCount=1;f.scene.meshFlags[0]=2|8|16;f.scene.meshFade[1]=2;}
    static Object recipe()throws Exception{return NativePackedPlants072Probe.fresh("viewpoint.world.Recipe",0);}
    @SuppressWarnings("unchecked") static Object batch(Object recipe)throws Exception {
        Object b=NativePackedPlants072Probe.fresh("viewpoint.world.Recipe$Batch");GameAccess.set(b.getClass().getName(),"page",b,page);
        ((List<Object>)GameAccess.field("viewpoint.world.Recipe","batches",recipe)).add(b);return b;
    }
    @SuppressWarnings("unchecked") static Object op(Object batch,int kind,int object)throws Exception {
        Object op=NativePackedPlants072Probe.fresh("viewpoint.world.Recipe$Op");String type=op.getClass().getName();
        GameAccess.set(type,"kind",op,kind);GameAccess.set(type,"object",op,object);
        ((List<Object>)GameAccess.field(batch.getClass().getName(),"ops",batch)).add(op);return op;
    }
    static void raw(Object batch,float x,int source,int object)throws Exception {
        Object op=op(batch,3,object);String type=op.getClass().getName();GameAccess.set(type,"source",op,(byte)source);
        float[][] points=N.horizontal(2.8f,1.3f);float[] v=new float[points.length*14];
        for(int i=0;i<points.length;i++){System.arraycopy(points[i],0,v,i*14,3);v[i*14]+=x;v[i*14+7]=v[i*14+8]=v[i*14+10]=1;v[i*14+13]=Float.intBitsToFloat(64);}
        GameAccess.set(type,"raw",op,v);
    }
    static void plant(Object batch,float x,int object)throws Exception {
        Object op=op(batch,4,object);String type=op.getClass().getName();
        GameAccess.set(type,"dx",op,-x);GameAccess.set(type,"flags",op,3<<19);
        GameAccess.set(type,"card",op,new float[]{-.7f,.7f,0,3});GameAccess.set(type,"map",op,new float[]{0,0,1,1});
    }
    static ChunkMeshData cook(Object recipe){return (ChunkMeshData)GameAccess.call("viewpoint.world.Cook","mesh",null,new Class<?>[]{GameAccess.type("viewpoint.world.Recipe")},recipe);}
    static int dark(float[] data){int n=0;for(float d:data)if(d<.99999f)n++;return n;}
    static int difference(float[] a,float[] b){int n=0;for(int i=0;i<a.length;i++)if(Math.abs(a[i]-b[i])>1e-5f)n++;return n;}
    static void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
}

