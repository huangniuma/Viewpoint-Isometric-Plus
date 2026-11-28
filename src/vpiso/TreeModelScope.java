package vpiso;

/** Tags native vegetation before pack offsets erase its original object identity. */
public final class TreeModelScope {
    // PVNV owns bits27 (tree) and28 (falling leaves).
    public static final int TREE = 1 << 26;
    public static final int BUSH = 1 << 25;
    private static final ThreadLocal<Integer> ACTIVE = ThreadLocal.withInitial(() -> 0);

    public static int enter(Object object) {
        int previous=ACTIVE.get(), tag=0;
        if(GameAccess.type("zombie.iso.objects.IsoTree").isInstance(object))tag=TREE;
        else if(GameAccess.type("zombie.iso.IsoObject").isInstance(object)) {
            Object sprite=GameAccess.call("zombie.iso.IsoObject","getSprite",object,new Class<?>[0]);
            String name=sprite==null?null:(String)GameAccess.field("zombie.iso.sprite.IsoSprite","name",sprite);
            if(name!=null && (name.startsWith("f_bushes_") || name.startsWith("vegetation_ornamental_")))tag=BUSH;
        }
        ACTIVE.set(tag);
        return previous;
    }
    public static void restore(int previous) { ACTIVE.set(previous); }
    public static int flags(int flags) { return flags | ACTIVE.get(); }
    public static int flags(int flags, viewpoint.packs.PackBind bind) {
        int kind=bind==null?0:NearVegetationCompat.kind(bind.model);
        if(kind==2)return flags; // Falling leaves must never inherit the enclosing tree's tag.
        return kind==1?flags | TREE | NearVegetationCompat.FLAG:flags(flags);
    }
    private TreeModelScope() {}
}
