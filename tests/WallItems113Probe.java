import java.lang.reflect.Method;
import java.util.*;
import viewpoint.core.Frame;
import viewpoint.render.ModelDraws;
import viewpoint.render.MousePick;
import vpiso.*;
import zombie.iso.*;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.fboRenderChunk.FBORenderCell;
import zombie.iso.objects.IsoDoor;
import zombie.iso.sprite.IsoSprite;

/** CPU evidence: shipped FBO producer versus captured attachment alpha. No game attach or GL. */
public final class WallItems113Probe {
    static Method factory;
    static int cases;
    public static void main(String[] args) throws Exception {
        zombie.core.random.RandStandard.INSTANCE.init();
        UpperObjects064Probe.main(new String[]{"caps"});
        factory=UpperObjects064Probe.class.getDeclaredMethod("square",int.class,int.class,int.class,boolean.class);
        factory.setAccessible(true);MousePick.ask=null;
        for(String kind:List.of("attachedN","attachedW","attachedS","attachedE","ordinary","containerN","plumbedN","structuralN","doorN","switchN"))
            for(int cut:new int[]{0,1,2,3}) for(int caps:new int[]{0,4,8,12}) inspect(kind,cut,caps);
        for(int cut:new int[]{0,1,2,3})for(int caps:new int[]{0,4,8,12})inspect("attachedNW",cut,caps);
        transition(true);transition(false);
        System.out.println("WALLITEMS113 OBSERVED cases="+cases+"; real FBO classifier and addon objectAlpha, 80 captured frames each; no GPU/map/live visual claim.");
    }
    static void inspect(String kind,int cut,int caps) throws Exception {
        IsoGridSquare square=(IsoGridSquare)factory.invoke(null,4,4,0,false);
        GameAccess.set("zombie.iso.IsoGridSquare","targetPlayerCutawayFlags",square,new byte[]{(byte)cut,0,0,0});
        GameAccess.set("zombie.iso.IsoGridSquare","playerCutawayFlags",square,new byte[]{(byte)cut,0,0,0});
        if((caps&4)!=0)wall(square,true);
        if((caps&8)!=0)wall(square,false);
        IsoSprite sprite=new IsoSprite();
        IsoFlagType attached=kind.endsWith("N")||kind.equals("attachedNW")?IsoFlagType.attachedN:kind.equals("attachedW")?IsoFlagType.attachedW:
            kind.equals("attachedS")?IsoFlagType.attachedS:kind.equals("attachedE")?IsoFlagType.attachedE:null;
        if(attached!=null)sprite.getProperties().set(attached);
        if(kind.equals("attachedNW"))sprite.getProperties().set(IsoFlagType.attachedW);
        if(kind.equals("plumbedN"))sprite.getProperties().set(IsoFlagType.waterPiped);
        if(kind.equals("structuralN"))sprite.cutN=true;
        if(kind.equals("switchN"))sprite.setType(zombie.iso.SpriteDetails.IsoObjectType.lightswitch);
        IsoObject object=kind.equals("doorN")?new IsoDoor(null):new IsoObject(null,square,sprite);
        object.square=square;object.sprite=sprite;
        if(kind.equals("containerN"))object.container=(zombie.inventory.ItemContainer)ModelCutawayProbe.allocate(zombie.inventory.ItemContainer.class);
        object.neverDoneAlpha=false;object.setAlphaAndTarget(0,1f);square.getObjects().add(object);
        Object camera=GameAccess.field("zombie.iso.IsoCamera","frameState",null);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","camCharacterSquare",camera,square);
        GameAccess.set("zombie.iso.fboRenderChunk.FBORenderCell","currentTimeMillis",FBORenderCell.instance,System.currentTimeMillis());
        Method classify=FBORenderCell.class.getDeclaredMethod("calculateObjectRenderInfo",int.class,IsoGridSquare.class,IsoObject.class);
        classify.setAccessible(true);classify.invoke(FBORenderCell.instance,0,square,object);
        float nativeTarget=object.getRenderInfo(0).targetAlpha;
        String layer=object.getRenderInfo(0).layer.toString();
        NativeObjectState state=new NativeObjectState();Frame frame=new Frame();frame.camZ=0;
        float first=Float.NaN,last=Float.NaN;
        for(int i=0;i<80;i++) {
            state.beginCapture(frame);
            try {
                float alpha=IsometricVisibility.objectAlpha(frame,square,object,0);
                if(i==0)first=alpha;last=alpha;
                float again=IsometricVisibility.objectAlpha(frame,square,object,0);
                if(Float.floatToIntBits(alpha)!=Float.floatToIntBits(again))throw new AssertionError("Double alpha step");
            } finally {NativeObjectState.endCapture();}
        }
        if(NativeWallCaps.caps(square)!=caps)throw new AssertionError("Native wall capability fixture mismatch");
        System.out.println((kind.equals("attachedNW")?"dual=":"case=")+kind+" cut="+cut+" caps="+caps+" layer="+layer+" nativeTarget="+nativeTarget+" alphaFirst="+first+" alpha80="+last);
        if(!kind.equals("attachedNW"))cases++;
    }
    static void wall(IsoGridSquare square,boolean north) {
        square.getProperties().set(north?IsoFlagType.WallN:IsoFlagType.WallW);
        square.getProperties().set(north?IsoFlagType.cutN:IsoFlagType.cutW);
        IsoSprite wall=new IsoSprite();wall.cutN=north;wall.cutW=!north;
        square.getObjects().add(new IsoObject(null,square,wall));
    }
    static void transition(boolean north) throws Exception {
        IsoGridSquare square=(IsoGridSquare)factory.invoke(null,4,4,0,false);wall(square,north);
        IsoSprite sprite=new IsoSprite();sprite.getProperties().set(north?IsoFlagType.attachedN:IsoFlagType.attachedW);
        IsoObject source=new IsoObject(null,square,sprite);source.neverDoneAlpha=false;source.setAlphaAndTarget(0,1f);
        square.getObjects().add(source);
        Object camera=GameAccess.field("zombie.iso.IsoCamera","frameState",null);
        GameAccess.set("zombie.iso.IsoCamera$FrameState","camCharacterSquare",camera,square);
        Frame frame=new Frame();frame.scene.models.add(null,null,null,new org.joml.Matrix4f(),-1,-1,0,ModelDraws.COLOUR|ModelDraws.SHADOW);
        NativeObjectState state=new NativeObjectState();
        for(int cut:new int[]{0,1,2,3,0}) {
            GameAccess.set("zombie.iso.IsoGridSquare","targetPlayerCutawayFlags",square,new byte[]{(byte)cut,0,0,0});
            GameAccess.set("zombie.iso.IsoGridSquare","playerCutawayFlags",square,new byte[]{(byte)cut,0,0,0});
            float first=Float.NaN,last=Float.NaN;boolean colour=true;
            for(int i=0;i<80;i++) {
                GameAccess.set("viewpoint.render.ModelDraws","flags",frame.scene.models,new int[]{ModelDraws.COLOUR|ModelDraws.SHADOW});
                state.beginCapture(frame);
                try {
                    float alpha=IsometricVisibility.objectAlpha(frame,square,source,0);if(i==0)first=alpha;last=alpha;
                    IsometricVisibility.applyModels(frame,square,source,0);
                    int flags=frame.scene.models.flags(0);
                    if((flags&ModelDraws.SHADOW)==0)throw new AssertionError("Attachment lost physical shadow flag");
                    colour=(flags&ModelDraws.COLOUR)!=0;
                } finally {NativeObjectState.endCapture();}
            }
            System.out.println("transition="+(north?"N":"W")+" cut="+cut+" alphaFirst="+first+" alpha80="+last+" colour="+colour);
        }
        // Lower levels use the existing floor bypass, even after a source was cut on its own level.
        int hiddenCut=north?1:2;
        GameAccess.set("zombie.iso.IsoGridSquare","targetPlayerCutawayFlags",square,new byte[]{(byte)hiddenCut,0,0,0});
        GameAccess.set("zombie.iso.IsoGridSquare","playerCutawayFlags",square,new byte[]{(byte)hiddenCut,0,0,0});
        for(int i=0;i<80;i++) {
            state.beginCapture(frame);
            try {IsometricVisibility.objectAlpha(frame,square,source,0);}
            finally {NativeObjectState.endCapture();}
        }
        frame.camZ=1;
        GameAccess.set("viewpoint.render.ModelDraws","flags",frame.scene.models,new int[]{ModelDraws.COLOUR|ModelDraws.SHADOW});
        state.beginCapture(frame);
        try {
            IsometricVisibility.applyModels(frame,square,source,0);
            if((frame.scene.models.flags(0)&ModelDraws.COLOUR)==0)throw new AssertionError("Lower-floor source suppressed");
        } finally {NativeObjectState.endCapture();}
    }
}
