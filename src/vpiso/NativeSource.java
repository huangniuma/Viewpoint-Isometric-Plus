package vpiso;

import zombie.iso.IsoObject;
import zombie.iso.IsoDirections;
import zombie.iso.sprite.IsoSprite;

/** Immutable native draw provenance. No live object is retained by the render queue. */
public final class NativeSource {
    private static final Class<?>[] NONE=new Class<?>[0];
    public final float x,y,level;
    public final int kind;
    public final float[] bounds;
    public final float[] owner;
    public final String key;

    public NativeSource(float x,float y,float level,int kind,float[] bounds) {
        this(x,y,level,kind,bounds,-1);
    }
    public NativeSource(float x,float y,float level,int kind,float[] bounds,int objectIndex) {
        this(x,y,level,kind,bounds,ownerKey((int)Math.floor(x),(int)Math.floor(y),(int)level,objectIndex));
    }
    private NativeSource(float x,float y,float level,int kind,float[] bounds,float[] owner) {
        this.x=x;this.y=y;this.level=level;this.kind=kind;this.bounds=bounds.clone();
        this.owner=owner;
        key="object:"+Float.floatToIntBits(x)+":"+Float.floatToIntBits(y)+":"
                +Float.floatToIntBits(level)+":"+kind+":"+java.util.Arrays.toString(owner)+":"+java.util.Arrays.toString(this.bounds);
    }
    public static NativeSource sprite(IsoSprite sprite,Object instance,IsoObject object,float x,float y,float level,IsoDirections direction) {
        if(object==null || sprite==null)return null;
        int frame=((Number)GameAccess.call("zombie.iso.sprite.IsoSprite","getCurrentSpriteFrame",sprite,
                new Class<?>[]{GameAccess.type("zombie.iso.sprite.IsoSpriteInstance")},instance)).intValue();
        boolean snow=(Boolean)GameAccess.call("zombie.iso.IsoObject","isUseSnowSprite",object,NONE);
        Object texture=GameAccess.call("zombie.iso.sprite.IsoSprite","getTextureForFrame",sprite,
                new Class<?>[]{int.class,IsoDirections.class,boolean.class},frame,direction,snow);
        if(texture==null)return null;
        String name=(String)GameAccess.field("zombie.iso.sprite.IsoSprite","name",sprite);
        boolean foliage=name!=null && (name.startsWith("f_bushes_") || name.startsWith("vegetation_ornamental_"));
        float[] bounds=packed(object,sprite);
        if(bounds==null)bounds=art(texture);
        if(bounds==null)return null;
        float rise=((Number)GameAccess.call("zombie.iso.IsoObject","getRenderYOffset",object,NONE)).floatValue()/96f*2.4494896f;
        bounds[1]+=rise;bounds[4]+=rise;
        Object square=GameAccess.call("zombie.iso.IsoObject","getSquare",object,NONE);
        int index=square==null?-1:((java.util.List<?>)GameAccess.call("zombie.iso.IsoGridSquare","getObjects",square,NONE)).indexOf(object);
        float[] owner=square==null?new float[2]:ownerKey((int)number("zombie.iso.IsoGridSquare","x",square),
                (int)number("zombie.iso.IsoGridSquare","y",square),(int)number("zombie.iso.IsoGridSquare","z",square),index);
        // Native picnic-table sections have ItemHeight and CustomName=Table,
        // but deliberately lack IsTable. Include that native furniture role.
        boolean table=!foliage && ((Boolean)GameAccess.call("zombie.iso.IsoObject","isTableSurface",object,NONE)
                || "Table".equals(GameAccess.call("zombie.iso.IsoObject","getProperty",object,new Class<?>[]{String.class},"CustomName")));
        return new NativeSource(x+.5f,y+.5f,level,foliage?4:table?5:3,bounds,owner);
    }
    /** Batch coordinates are native square+1, not the VP square centre. */
    public static NativeSource tree(Object tree) {
        String type="zombie.iso.fboRenderChunk.FBORenderTrees$Tree";
        Object texture=GameAccess.field(type,"texture",tree),second=GameAccess.field(type,"texture2",tree);
        float[] bounds=texture==null?null:art(texture),extra=second==null?null:art(second);
        if(bounds==null)bounds=extra;
        else if(extra!=null)for(int axis=0;axis<3;axis++) {bounds[axis]=Math.min(bounds[axis],extra[axis]);bounds[axis+3]=Math.max(bounds[axis+3],extra[axis+3]);}
        return bounds==null?null:new NativeSource(number(type,"x",tree)-.5f,number(type,"y",tree)-.5f,
                number(type,"z",tree),4,bounds);
    }
    private static float[] art(Object texture) {
        // Public, pure geometry conversion; do not enter PlantGather's worker cache.
        float[] card=(float[])GameAccess.call("viewpoint.world.TileMeshes","plantCard",null,
                new Class<?>[]{GameAccess.type("zombie.core.textures.Texture")},texture);
        float r=Math.max(Math.abs(card[0]),Math.abs(card[1]));
        if(!Float.isFinite(r) || r<.001f || !Float.isFinite(card[2]) || !Float.isFinite(card[3]) || card[3]-card[2]<.001f)return null;
        return new float[]{-r,card[2],-r,r,card[3],r};
    }
    private static float[] packed(IsoObject object,IsoSprite sprite) {
        Object bind=GameAccess.call("viewpoint.packs.ModelPacks","bind",null,new Class<?>[]{IsoObject.class,IsoSprite.class},object,sprite);
        if(bind==null)return null;
        Object model=GameAccess.field("viewpoint.packs.PackBind","model",bind);
        float[] source=(float[])GameAccess.call("viewpoint.render.PackModel","bounds",model,NONE);
        if(source==null || source.length!=6)return null;
        float[] bounds={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        boolean turns=(Boolean)GameAccess.call("viewpoint.packs.PackBind","turns",null,new Class<?>[]{IsoSprite.class},sprite);
        float[] point=new float[3];
        for(int corner=0;corner<8;corner++) {
            GameAccess.call("viewpoint.packs.PackBind","place",bind,
                    new Class<?>[]{float.class,float.class,float.class,boolean.class,float[].class},
                    source[(corner&1)==0?0:3],source[(corner&2)==0?1:4],source[(corner&4)==0?2:5],turns,point);
            // Recipe.model stores native dx/dy, then Cook negates the two map axes.
            point[0]-=number("viewpoint.packs.PackBind","x",bind);
            point[1]+=number("viewpoint.packs.PackBind","z",bind);
            point[2]-=number("viewpoint.packs.PackBind","y",bind);
            for(int axis=0;axis<3;axis++) {bounds[axis]=Math.min(bounds[axis],point[axis]);bounds[axis+3]=Math.max(bounds[axis+3],point[axis]);}
        }
        for(int axis=0;axis<3;axis++)if(!Float.isFinite(bounds[axis]) || !Float.isFinite(bounds[axis+3]) || bounds[axis+3]-bounds[axis]<.001f)return null;
        return bounds;
    }
    private static float number(String type,String field,Object object) {return ((Number)GameAccess.field(type,field,object)).floatValue();}
    /** Two exact24-bit values, not a hash. Unsupported ranges use the own-source shell. */
    public static float[] ownerKey(int x,int y,int level,int index) {
        if(x<0 || x>65535 || y<0 || y>65535 || level< -32 || level>=32 || index<0 || index>=1024)return new float[2];
        return new float[]{x|((y&255)<<16),((y>>>8)|((level+32)<<8)|(index<<14))+1};
    }
    public NativeSource withOwner(float[] key) {return new NativeSource(x,y,level,kind,bounds,key.clone());}
    public static void plant(Object cooker,Object op,int start) {
        int object=((Number)GameAccess.field("viewpoint.world.Recipe$Op","object",op)).intValue();
        int end=((Number)GameAccess.field("viewpoint.world.Cook$Cooker","plantFloats",cooker)).intValue();
        float[] data=(float[])GameAccess.field("viewpoint.world.Cook$Cooker","plants",cooker);
        for(int at=start;at<end;at+=14)data[at+13]=object>=0 && object<0x400000?object+1:0;
    }
}
