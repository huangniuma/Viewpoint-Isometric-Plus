package vpiso;

/** Restores the native 3D-item cursor producer before the building click consumer. */
public final class NativePlacement {
    private static final String CELL = "zombie.iso.fboRenderChunk.FBORenderCell";
    private static final Class<?>[] INT = { int.class };

    public static void renderOpaque(Object cell, int playerIndex) {
        Object renderer = GameAccess.field(CELL, "instance", null);
        GameAccess.set(CELL, "cell", renderer, cell);
        GameAccess.call(CELL, "renderOpaqueObjectsEvent", renderer, INT, playerIndex);
    }

    private NativePlacement() {}
}
