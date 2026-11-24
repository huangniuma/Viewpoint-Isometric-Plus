package vpiso;

/** Tags native vegetation before pack offsets erase its original object identity. */
public final class TreeModelScope {
    public static final int TREE = 1 << 28;
    public static final int BUSH = 1 << 27;
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
    private TreeModelScope() {}
}
