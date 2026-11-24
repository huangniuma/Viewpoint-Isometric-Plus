import java.util.*;
import java.lang.reflect.*;
import viewpoint.core.Frame;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.*;

/** A prepared native square mask can hide upper objects while building collapse permits them. */
public final class ModelUpper061Probe {
    static IsoGridSquare square;
    public static IsoGridSquare squareAt(int x,int y,int z){return square!=null&&x==square.x&&y==square.y&&z==square.z?square:null;}
    public static void main(String[] args)throws Exception {
        boolean old=args.length>0&&args[0].equals("baseline060");
        ModelCutawayProbe.cutaways=GameAccess.staticCall(ModelCutawayProbe.CUT,"getInstance");
        ModelCutawayProbe.producer=ModelCutawayProbe.producer(true);
        ModelCutawayProbe.own=ModelCutawayProbe.producer.getDeclaredMethod("own",Frame.class,int.class,IsoGridSquare.class,IsoObject.class,zombie.inventory.InventoryItem.class);
        ModelCutawayProbe.own.setAccessible(true);ModelCutawayProbe.camera(0);Main.compatible=true;
        square=ModelCutawayProbe.square(1,false);square.x=square.y=0;square.chunk.wx=square.chunk.wy=0;
        Object level=GameAccess.call("zombie.iso.IsoChunk","getCutawayDataForLevel",square.chunk,new Class<?>[]{int.class},1);
        byte[][] masks=(byte[][])GameAccess.field(ModelCutawayProbe.CUT+"$ChunkLevelData","squareFlags",level);
        Arrays.fill(masks[0],(byte)0);
        ModelCutawayProbe.require(ModelCutawayProbe.nativeVisible(square),"building-only control must permit square");
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",ModelUpper061Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        try {
            int cases=0;
            for(int playerLevel:new int[]{0,1,0})for(boolean shown:new boolean[]{false,true}) {
                masks[0][0]=(byte)(shown?1:0);ModelCutawayProbe.camera(playerLevel);
                Frame f=ModelCutawayProbe.run(square,playerLevel,1,false);
                // 075 also respects a prepared native-hidden square on the player's
                // own floor; only the pinned060 control bypasses that square mask.
                boolean visible=old||shown;
                ModelCutawayProbe.require(((f.scene.models.flags(1)&ModelDraws.COLOUR)!=0)==visible,"upper rigid model mask not respected");
                ModelCutawayProbe.require((f.scene.models.flags(1)&ModelDraws.SHADOW)!=0,"physical upper blocker removed");
                checkMesh(f,visible);cases++;
            }
            masks[0][0]=0;
            for(int mode:new int[]{0,2}) {
                Frame f=ModelCutawayProbe.run(square,0,mode,false);
                ModelCutawayProbe.require((f.scene.models.flags(1)&ModelDraws.COLOUR)!=0,"ordinary VP or shadow capture changed");cases++;
            }
            System.out.println((old?"Pinned060 upper mask negative control PASS:":"Upper square mask regression061 PASS:")+" "+cases+" cases; real prepared native square flags vs building-only decision, actual woven rigid door producer, owned baked mesh plans, stairs recovery, physical shadows and ordinary VP/native-shadow capture isolation.");
        } finally {Visibility109Bindings.set(api,"square",previous);ModelCutawayProbe.scope(0);}
    }
    static void checkMesh(Frame frame,boolean visible) {
        ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[1],new int[]{0},new int[]{6},new float[84],1);
        GameAccess.set("viewpoint.render.ChunkMeshData","runs",mesh,new int[]{0,0,6});
        GameAccess.set("viewpoint.render.ChunkMeshData","pageRuns",mesh,new int[]{0,1});
        frame.camX=frame.camY=0;frame.scene.meshes[0]=mesh;frame.scene.meshCount=1;
        frame.scene.meshFlags[0]=SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW;
        IsometricVisibility.apply(frame,new Object(),0);
        ModelCutawayProbe.require(((frame.scene.meshFlags[0]&(SceneData.VISIBLE|SceneData.NEAR))!=0)==visible,"baked mesh kept hidden native square");
        ModelCutawayProbe.require((frame.scene.meshFlags[0]&SceneData.CASTS_SHADOW)!=0,"physical baked blocker removed");
    }
}
