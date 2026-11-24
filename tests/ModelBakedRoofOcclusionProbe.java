import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.*;

/** Real VP ceiling producer and cold special page metadata, not ordinary textured roof pages. */
public final class ModelBakedRoofOcclusionProbe {
    static final int W=ModelRoomOcclusionProbe.W;
    static boolean baseline;
    static Method ceilingPut,quadPack;

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        baseline=args.length>0 && args[0].equals("baseline042");
        String cut=ModelCutawayProbe.CUT;
        ModelCutawayProbe.cutaways=GameAccess.staticCall(cut,"getInstance");
        Object per=((Object[])GameAccess.field(cut,"perPlayerData",ModelCutawayProbe.cutaways))[0];
        Object btc=GameAccess.field(cut+"$PerPlayerData","buildingsToCollapse",per);
        ModelCutawayProbe.collapsed=(ArrayList<Object>)GameAccess.field(cut+"$BuildingsToCollapse","buildingsToCollapse",btc);
        ModelCutawayProbe.building=ModelCutawayProbe.allocate(BuildingDef.class);
        ModelCutawayProbe.collapsed.add(ModelCutawayProbe.building);ModelCutawayProbe.camera(0);
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",ModelRoomOcclusionProbe.class.getMethod("squareAt",int.class,int.class,int.class));
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"cold generated roof solar occlusion",0,0);
        if(window==0)throw new AssertionError("Hidden GL context");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));GlProgram.onUse(IsometricShaders::onUse);
            ceilingPut=GameAccess.type("viewpoint.world.CeilingFaces").getDeclaredMethod("put",float[].class,int.class,float.class,float.class,float.class,int.class,boolean.class,int.class,int.class);
            ceilingPut.setAccessible(true);
            quadPack=GameAccess.type("viewpoint.platform.Quads").getDeclaredMethod("pack",float[].class,int.class,int.class);quadPack.setAccessible(true);
            ModelRoomOcclusionProbe.setup();
            int cases=0,minAperture=Integer.MAX_VALUE;
            for(String kind:new String[]{"ceiling-down","ceiling-up","floor-page"})for(int direction=0;direction<4;direction++) {
                Frame frame=NativeSurfaceReceiverProbe.frame();
                frame.scene.sunX=direction==0?-.6f:direction==1?.6f:0;
                frame.scene.sunZ=direction==2?-.6f:direction==3?.6f:0;
                frame.scene.sunY=.8f;frame.scene.sunStrength=direction<2?1f:.25f;
                Object walls=NativeSurfaceReceiverProbe.mesh(frame.scene,0,ModelRoomOcclusionProbe.walls());
                Object roof=roof(frame,kind,false);
                float[] shown=renderStrength(frame,0,1f),hidden=renderStrength(frame,1,1f),partial=renderStrength(frame,2,1f);
                int lit=ModelRoomOcclusionProbe.bright(hidden),covered=ModelRoomOcclusionProbe.dark(hidden);
                if(baseline) {
                    if(lit<100)throw new AssertionError("Pinned042 omitted special roof page not reproduced: "+kind+"/"+direction+" lit="+lit);
                    System.out.println("Cold special roof negative case PASS: pinned042 kind="+kind+" direction="+direction+" indoorDirect="+lit+"; no baked colour texture");
                }else {
                    if(covered!=W*W || lit!=0 || ModelRoomOcclusionProbe.mismatch(shown,hidden)!=0 || ModelRoomOcclusionProbe.mismatch(shown,partial)!=0)
                        throw new AssertionError("Generated/cold roof leak: "+kind+"/"+direction+" covered="+covered+" direct="+lit);
                    checkMetadata(roof,kind);
                    float[] blendShown=renderStrength(frame,0,.55f),blendHidden=renderStrength(frame,1,.55f),blendPartial=renderStrength(frame,2,.55f);
                    for(int i=0;i<W*W;i++)if(Math.abs(blendHidden[i*4]-.45f)>.0001f || Math.abs(blendHidden[i*4+1]-.45f)>.0001f || blendHidden[i*4+2]<.05f)
                        throw new AssertionError("Strength.55 should have uniform aesthetic visibility.45, retained indoor RGB and no exterior silhouette: "+Arrays.toString(Arrays.copyOfRange(blendHidden,i*4,i*4+4)));
                    if(ModelRoomOcclusionProbe.mismatch(blendShown,blendHidden)!=0 || ModelRoomOcclusionProbe.mismatch(blendShown,blendPartial)!=0)
                        throw new AssertionError("Strength.55 camera-cut mismatch");
                    checkBucketScope(frame,(ChunkMeshData)roof,(ChunkMeshData)walls);
                    NativeSurfaceReceiverProbe.retire(roof);roof=roof(frame,kind,true);
                    int admitted=ModelRoomOcclusionProbe.bright(renderStrength(frame,1,1f));
                    if(admitted<100)throw new AssertionError("Physical special roof aperture blocked: "+kind+"/"+direction+" direct="+admitted);
                    minAperture=Math.min(minAperture,admitted);checkMetadata(roof,kind);
                    System.out.println("Cold special roof GPU case PASS: kind="+kind+" direction="+direction+" covered="+covered+" hiddenLeaks="+lit+" apertureDirect="+admitted+"; strength.55 uniformVisibility=.45; source metadata retained; color/nonowned/ordinary bucket controls");
                }
                NativeSurfaceReceiverProbe.retire(roof);NativeSurfaceReceiverProbe.retire(walls);cases++;
            }
            if(GL11.glGetError()!=0)throw new AssertionError("GL error");
            System.out.println((baseline?"Cold special roof negative control PASS: pinned042 ":"Cold special roof GPU PASS: ")+cases+" cases; actual CeilingFaces.put vertices and flags1048695, grouped UP/DOWN ceilings and floorPage, no baked texture/source, four sun/moon directions, camera cuts"+(baseline?" reproduce omitted physical caps.":", real aperture minDirect="+minAperture+" and metadata preserved. Controlled fixture, no live map acceptance."));
        }finally {
            Visibility109Bindings.set(api,"square",previous);
            IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }

    static float[] renderStrength(Frame frame,int cameraCut,float strength)throws Exception {
        ModelRoomOcclusionProbe.render(frame,cameraCut);
        IsometricBridge.modelRendering(frame,new IsometricEffects(true,strength,0f,64f,1024,false,1,1,1,1));
        ModelRoomOcclusionProbe.shader.use();
        // WorldRenderer.prepare installs its own onUse hook during first initialization.
        // This Meshes-only fixture explicitly invokes the addon's woven uniform advice.
        IsometricShaders.onUse(ModelRoomOcclusionProbe.shader);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        FloatBuffer pixels=org.lwjgl.BufferUtils.createFloatBuffer(W*W*4);
        GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        float[] result=new float[W*W*4];pixels.get(result);return result;
    }

    static void checkBucketScope(Frame frame,ChunkMeshData roof,ChunkMeshData ordinary)throws Exception {
        Method bucket=GameAccess.type("viewpoint.render.Meshes").getDeclaredMethod("bucket",ChunkMeshData.class,int.class,int.class);bucket.setAccessible(true);
        if(ModelSolarGeometry.physicalPage(roof,1,0))throw new AssertionError("Solar scope leaked after successful GPU draw");
        if(bucket.invoke(null,roof,1,0)!=null)throw new AssertionError("Cold color roof acquired a physical-only bucket");
        for(byte mask:new byte[]{SceneData.CASTS_SHADOW,SceneData.CASTS_MID,SceneData.CASTS_FAR}) {
            SceneData previous=ModelSolarGeometry.enter(frame.scene,mask);
            try {
                if(!ModelSolarGeometry.physicalPage(roof,1,0) || ModelSolarGeometry.physicalPage(ordinary,0,0))throw new AssertionError("Owned solar metadata classification");
                if(bucket.invoke(null,roof,1,0)==null)throw new AssertionError("Owned physical roof bucket omitted");
                if(bucket.invoke(null,ordinary,0,0)==null)throw new AssertionError("Ordinary texture bucket changed");
                SceneData nested=ModelSolarGeometry.enter(frame.scene,SceneData.VISIBLE);
                try {if(ModelSolarGeometry.physicalPage(roof,1,0) || bucket.invoke(null,roof,1,0)!=null)throw new AssertionError("Nested camera draw changed cold color roof");}
                finally {ModelSolarGeometry.restore(nested);}
                Frame foreign=new Frame();foreign.scene.meshCount=2;foreign.scene.meshes[1]=roof;
                SceneData other=ModelSolarGeometry.enter(foreign.scene,mask);
                try {if(ModelSolarGeometry.physicalPage(roof,1,0) || bucket.invoke(null,roof,1,0)!=null)throw new AssertionError("Foreign solar scene bypassed color-bake ownership");}
                finally {ModelSolarGeometry.restore(other);}
            }finally {ModelSolarGeometry.restore(previous);}
        }
    }

    static Object roof(Frame frame,String kind,boolean aperture)throws Exception {
        float[] vertices=new float[64*8*14];int at=0;ArrayList<Integer> runs=new ArrayList<>();
        boolean floor=kind.equals("floor-page"),up=kind.equals("ceiling-up");
        for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
            if(aperture && x>=2 && x<6 && y>=2 && y<6)continue;
            runs.add(((y*8+x)<<2)|(floor?0:3));runs.add(at/14);runs.add(0);
            int start=at;
            int triangles=(Integer)ceilingPut.invoke(null,vertices,at,x+.5f,y+.5f,ModelRoomOcclusionProbe.ROOF,0,up,0,0);
            at=(Integer)quadPack.invoke(null,vertices,start,triangles);
            if(at-start!=4*14 && at-start!=8*14)throw new AssertionError("Actual VP quad packing changed");
            runs.set(runs.size()-1,(at-start)/14);
        }
        for(int i=13;i<at;i+=14)if(Float.floatToRawIntBits(vertices[i])!=1048695)throw new AssertionError("Actual ceiling flags changed");
        if(!aperture) {
            float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
            for(int i=0;i<at;i+=14)for(int d=0;d<3;d++){min[d]=Math.min(min[d],vertices[i+d]);max[d]=Math.max(max[d],vertices[i+d]);}
            if(min[0]!=-8 || min[1]!=ModelRoomOcclusionProbe.ROOF || min[2]!=-8 || max[0]!=0 || max[1]!=ModelRoomOcclusionProbe.ROOF || max[2]!=0)throw new AssertionError("Actual physical ceiling bounds changed");
        }
        FloatBuffer data=org.lwjgl.system.MemoryUtil.memAllocFloat(at);data.put(vertices,0,at).flip();
        Object pages=java.lang.reflect.Array.newInstance(GameAccess.type("zombie.core.textures.TextureID"),1);
        ChunkMeshData mesh=(ChunkMeshData)GameAccess.type("viewpoint.render.ChunkMeshData")
            .getConstructor(pages.getClass(),int[].class,int[].class,FloatBuffer.class,int.class,int[].class,int[].class)
            .newInstance(pages,new int[]{0},new int[]{at/14},data,floor?1:0,runs.stream().mapToInt(Integer::intValue).toArray(),new int[]{0,runs.size()/3});
        if(floor)mesh.floorPage=0;else mesh.ceilingGroups=new int[]{0};
        frame.scene.meshes[1]=mesh;frame.scene.meshCount=2;frame.scene.meshFlags[1]=1|2|8|16;
        frame.scene.meshFade[2]=0;frame.scene.meshFade[3]=2;
        if(frame.scene.meshFloor[1]!=null)throw new AssertionError("Fixture accidentally supplied floor bake source");
        checkMetadata(mesh,kind);return mesh;
    }
    static void checkMetadata(Object value,String kind) {
        ChunkMeshData mesh=(ChunkMeshData)value;
        if(mesh.pages.length!=1 || mesh.pages[0]!=null)throw new AssertionError("Cold roof acquired source texture");
        if(kind.equals("floor-page")) {
            if(mesh.floorPage!=0 || mesh.ceilingGroups!=null)throw new AssertionError("Floor metadata mutated");
        }else if(mesh.floorPage!=-1 || !Arrays.equals(mesh.ceilingGroups,new int[]{0}))throw new AssertionError("Ceiling metadata mutated");
    }
}
