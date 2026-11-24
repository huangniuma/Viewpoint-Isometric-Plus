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

/** Native same-floor final consumer versus mesh and independent-model visibility. */
public final class Furniture075Probe {
    private static int cases;
    private static boolean baseline;

    public static void main(String[] args)throws Exception {
        baseline=Arrays.asList(args).contains("baseline074");
        // Reuse the real native/light fixture and isolated final renderer, without
        // changing the shipped classification/alpha routines under comparison.
        UpperObjects064Probe.main(new String[]{"caps"});
        call("nativeConsumer",new Class<?>[0]);
        IsoGridSquare square=(IsoGridSquare)call("square",new Class<?>[]{int.class,int.class,int.class,boolean.class},0,0,0,false);
        IsoGridSquare opposite=(IsoGridSquare)call("square",new Class<?>[]{int.class,int.class,int.class,boolean.class},0,-1,0,false);
        square.setAdjacentSquare(IsoDirections.N,opposite);
        IsoWindowFrame panel=new UpperObjects064Probe.FixturePanel(square);
        IsoObject partition=new IsoObject(null,square,new IsoSprite());
        IsoObject neighbour=new IsoObject(null,square,new IsoSprite());
        square.getObjects().addAll(List.of(panel,partition,neighbour));
        Object cuts=GameAccess.staticCall("zombie.iso.fboRenderChunk.FBORenderCutaways","getInstance");
        require(!(Boolean)GameAccess.call(cuts.getClass().getName(),"isForceRenderSquare",cuts,
            new Class<?>[]{int.class,IsoGridSquare.class},0,square),"Must test an ordinary same-floor square, not the old forced fixture");
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object lookup=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",UpperObjects064Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        Class<?> nativeType=(Class<?>)field("nativeType");Object renderer=field("nativeRenderer");
        Method classify=nativeType.getDeclaredMethod("calculateObjectRenderInfo",int.class,IsoGridSquare.class,IsoObject.class);classify.setAccessible(true);
        Method draw=nativeType.getMethod("renderTranslucent",IsoObject.class);
        ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[1],new int[]{0},new int[]{9},new float[126],0);
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
                require(contains(frame,0)==(baseline||nativeAlpha>0),"Same-floor native hidden object leaked; native final alpha="+nativeAlpha);
                require(contains(frame,3)==(baseline||showPartition),"Same-floor furniture ignored native None");
                require(contains(frame,6),"Visible neighbour was incorrectly removed");
                require((frame.scene.meshFlags[0]&SceneData.CASTS_SHADOW)!=0,"Physical caster changed");
                for(IsoObject object:List.of(panel,partition,neighbour)) {
                    panel.setAlphaAndTarget(0,0);
                    Frame models=frame(mesh);models.scene.models.add(null,null,null,new Matrix4f(),-1,-1,0,ModelDraws.COLOUR|ModelDraws.SHADOW);
                    IsometricVisibility.applyModels(models,square,object,0);
                    boolean expected=object==panel?alpha>0:object!=partition||showPartition;
                    require(((models.scene.models.flags(0)&ModelDraws.COLOUR)!=0)==(baseline||expected),"Independent same-floor model diverged from native object");
                    require((models.scene.models.flags(0)&ModelDraws.SHADOW)!=0,"Independent model lost physical caster");cases++;
                }
                // Restoring native inputs must recover the same cached mesh on the same floor.
                // Restore native object inputs too: same-floor visibility is no longer
                // an unconditional bypass of a hidden object's own native decision.
                call("lighting",new Class<?>[]{IsoGridSquare.class,float.class},opposite,.5f);
                partition.setDoRender(true);panel.setAlphaAndTarget(0,0);
                Frame upstairs=frame(mesh);upstairs.camZ=0;ModelCutawayProbe.camera(0);
                IsometricVisibility.apply(upstairs,new Object(),0);
                require(contains(upstairs,0)&&contains(upstairs,3),"Upper-floor recovery mutated cached geometry");
                ModelCutawayProbe.camera(0);cases++;
            }
            require(Arrays.equals((int[])GameAccess.field(ChunkMeshData.class.getName(),"spans",mesh),
                new int[]{0,0,3,1,3,3,2,6,3}),"Cached object ownership changed");
        } finally {Visibility109Bindings.set(api,"square",lookup);}
        System.out.println((baseline?"Pinned074 furniture negative control PASS: ":"Furniture075 native parity PASS: ")+cases+
            " cases; native final translucent consumer, same-floor native-alpha/None furniture and panels, mesh and independent-model colour parity, neighbours, same-floor recovery and physical casters. CPU fixture; no live map.");
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
    private static void require(boolean ok,String text) {if(!ok)throw new AssertionError(text);}
}
