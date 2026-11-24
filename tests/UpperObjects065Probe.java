import java.lang.reflect.*;
import java.util.*;
import org.joml.Matrix4f;
import viewpoint.core.Frame;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.*;
import zombie.iso.objects.IsoWindowFrame;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.fboRenderChunk.*;
import net.bytebuddy.jar.asm.*;

/** Same native square remains visible while individual upper window/partition decisions change. */
public final class UpperObjects065Probe {
    private static int cases;
    private static boolean baseline;

    public static void main(String[] args)throws Exception {
        baseline=Arrays.asList(args).contains("baseline064");
        nativeOrder();
        // Reuse the real native/light fixture and isolated final renderer, without
        // changing the shipped classification/alpha routines under comparison.
        UpperObjects064Probe.main(new String[]{"caps"});
        call("nativeConsumer",new Class<?>[0]);
        IsoGridSquare square=(IsoGridSquare)call("square",new Class<?>[]{int.class,int.class,int.class,boolean.class},0,0,1,false);
        IsoGridSquare opposite=(IsoGridSquare)call("square",new Class<?>[]{int.class,int.class,int.class,boolean.class},0,-1,1,false);
        square.setAdjacentSquare(IsoDirections.N,opposite);
        IsoWindowFrame panel=new UpperObjects064Probe.FixturePanel(square);
        IsoObject partition=new IsoObject(null,square,new IsoSprite());
        IsoObject neighbour=new IsoObject(null,square,new IsoSprite());
        square.getObjects().addAll(List.of(panel,partition,neighbour));
        Object cuts=GameAccess.staticCall("zombie.iso.fboRenderChunk.FBORenderCutaways","getInstance");
        require(!(Boolean)GameAccess.call(cuts.getClass().getName(),"isForceRenderSquare",cuts,
            new Class<?>[]{int.class,IsoGridSquare.class},0,square),"Must test an ordinary upper square, not the old forced fixture");
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object lookup=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",UpperObjects064Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        Class<?> nativeType=(Class<?>)field("nativeType");Object renderer=field("nativeRenderer");
        Method classify=nativeType.getDeclaredMethod("calculateObjectRenderInfo",int.class,IsoGridSquare.class,IsoObject.class);classify.setAccessible(true);
        Method draw=nativeType.getMethod("renderTranslucent",IsoObject.class);
        ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[1],new int[]{0},new int[]{9},new float[126],1);
        GameAccess.set(ChunkMeshData.class.getName(),"runs",mesh,new int[]{0,0,9});
        GameAccess.set(ChunkMeshData.class.getName(),"pageRuns",mesh,new int[]{0,1});
        mesh.objects(new int[]{0,0,3, 1,3,3, 2,6,3},new int[]{0,3},new int[]{0,1,2});
        try {
            for(float alpha:new float[]{0,.4f,.6f,1,0})for(boolean showPartition:new boolean[]{false,true}) {
                call("lighting",new Class<?>[]{IsoGridSquare.class,float.class},opposite,alpha*.5f);
                panel.setAlphaAndTarget(0,0);partition.setDoRender(showPartition);
                classify.invoke(renderer,0,square,panel);classify.invoke(renderer,0,square,partition);
                require(panel.getRenderInfo(0).layer==(alpha<1?ObjectRenderLayer.Translucent:ObjectRenderLayer.MinusFloor),
                    "Unexpected native window layer for alpha="+alpha+": "+panel.getRenderInfo(0).layer);
                require((partition.getRenderInfo(0).layer!=ObjectRenderLayer.None)==showPartition,"Native partition fixture failed");
                UpperObjects064Probe.finalAlpha.clear();draw.invoke(renderer,panel);
                float nativeAlpha=UpperObjects064Probe.finalAlpha.get(panel);
                require(Math.abs(nativeAlpha-alpha)<.0001f,"Native final window draw did not match input "+alpha);
                panel.setAlphaAndTarget(0,0);
                Frame frame=frame(mesh);IsometricVisibility.apply(frame,new Object(),0);
                require(contains(frame,0)==(baseline||nativeAlpha>0),"Ordinary upper window leaked; native final alpha="+nativeAlpha);
                require(contains(frame,3)==(baseline||showPartition),"Ordinary upper partition ignored native None");
                require(contains(frame,6),"Visible neighbour was incorrectly removed");
                require((frame.scene.meshFlags[0]&SceneData.CASTS_SHADOW)!=0,"Physical caster changed");
                for(IsoObject object:List.of(panel,partition,neighbour)) {
                    panel.setAlphaAndTarget(0,0);
                    Frame models=frame(mesh);models.scene.models.add(null,null,null,new Matrix4f(),-1,-1,0,ModelDraws.COLOUR|ModelDraws.SHADOW);
                    IsometricVisibility.applyModels(models,square,object,0);
                    boolean expected=object==panel?alpha>0:object!=partition||showPartition;
                    require(((models.scene.models.flags(0)&ModelDraws.COLOUR)!=0)==(baseline||expected),"Independent upper model diverged from native object");
                    require((models.scene.models.flags(0)&ModelDraws.SHADOW)!=0,"Independent model lost physical caster");cases++;
                }
                // Moving upstairs must restore the very same cached mesh.
                // Restore native object inputs too: same-floor visibility is no longer
                // an unconditional bypass of a hidden object's own native decision.
                call("lighting",new Class<?>[]{IsoGridSquare.class,float.class},opposite,.5f);
                partition.setDoRender(true);panel.setAlphaAndTarget(0,0);
                Frame upstairs=frame(mesh);upstairs.camZ=1;ModelCutawayProbe.camera(1);
                IsometricVisibility.apply(upstairs,new Object(),0);
                require(contains(upstairs,0)&&contains(upstairs,3),"Upper-floor recovery mutated cached geometry");
                ModelCutawayProbe.camera(0);cases++;
            }
            require(Arrays.equals((int[])GameAccess.field(ChunkMeshData.class.getName(),"spans",mesh),
                new int[]{0,0,3,1,3,3,2,6,3}),"Cached object ownership changed");
        } finally {Visibility109Bindings.set(api,"square",lookup);}
        System.out.println((baseline?"Pinned064 ordinary upper negative control PASS: ":"Upper ordinary objects regression065 PASS: ")+cases+
            " cases; native final translucent consumer, ordinary non-forced upper windows/partitions, mesh and independent-model colour parity, neighbours, upstairs recovery and physical casters. CPU fixture; no live map.");
    }
    private static Frame frame(ChunkMeshData mesh) {
        Frame f=new Frame();f.scene.meshCount=1;f.scene.meshes[0]=mesh;
        f.scene.meshFlags[0]=SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW;return f;
    }
    private static boolean contains(Frame f,int vertex) {
        int start=f.scene.meshPlan[0];if(start<0)return (f.scene.meshFlags[0]&SceneData.VISIBLE)!=0;
        for(int i=start;i<start+f.scene.meshPlanLength[0];i++)if(vertex>=f.scene.plan[i*3+1]&&vertex<f.scene.plan[i*3+1]+f.scene.plan[i*3+2])return true;
        return false;
    }
    private static Object call(String name,Class<?>[] types,Object...args)throws Exception {
        Method m=UpperObjects064Probe.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);
    }
    private static Object field(String name)throws Exception {
        Field f=UpperObjects064Probe.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }
    private static void nativeOrder()throws Exception {
        List<String> nativeCalls=new ArrayList<>(),bridgeCalls=new ArrayList<>();
        Set<String> selected=Set.of("checkOccludedRooms","prepareChunksForUpdating","doCutawayVisitSquares");
        try(var input=ClassLoader.getSystemResourceAsStream("zombie/iso/fboRenderChunk/FBORenderCell.class")) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                    if(!n.equals("renderTilesInternal"))return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf) {
                            if(selected.contains(name))nativeCalls.add(name);
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        try(var input=ClassLoader.getSystemResourceAsStream("vpiso/NativeIsoState.class")) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                    if(!n.equals("refreshNative"))return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitLdcInsn(Object value) {
                            if(value instanceof String&&selected.contains(value))bridgeCalls.add((String)value);
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        require(nativeCalls.equals(List.of("checkOccludedRooms","prepareChunksForUpdating","doCutawayVisitSquares")),
            "Shipped native sequence changed: "+nativeCalls);
        require(bridgeCalls.equals(baseline?List.of("prepareChunksForUpdating","checkOccludedRooms","doCutawayVisitSquares"):nativeCalls),
            "Bridge/native refresh order differs: "+bridgeCalls+" versus "+nativeCalls);
        System.out.println("Shipped native refresh order="+nativeCalls+"; artifact="+bridgeCalls);
    }
    private static void require(boolean ok,String text) {if(!ok)throw new AssertionError(text);}
}
