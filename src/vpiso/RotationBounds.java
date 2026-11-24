package vpiso;

/** Adds rotated visibility to native CPU prepasses without broadening the game globally. */
public final class RotationBounds {
    private static final ThreadLocal<RotationCamera.Snapshot> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<float[]> POINT = ThreadLocal.withInitial(() -> new float[3]);

    public static RotationCamera.Snapshot begin(RotationCamera.Snapshot snapshot) {
        RotationCamera.Snapshot previous = CURRENT.get();
        if (snapshot == null) CURRENT.remove(); else CURRENT.set(snapshot);
        return previous;
    }

    public static void end(RotationCamera.Snapshot previous) {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }

    public static boolean chunk(Object chunk, boolean original) {
        RotationCamera.Snapshot snapshot = CURRENT.get();
        if (original || snapshot == null) return original;
        int x = integer("zombie.iso.IsoChunk", "wx", chunk) * 8;
        int y = integer("zombie.iso.IsoChunk", "wy", chunk) * 8;
        int min = integer("zombie.iso.IsoChunk", "minLevel", chunk);
        int max = integer("zombie.iso.IsoChunk", "maxLevel", chunk);
        // Large native trees extend about five storeys above their root and 3.5 tiles sideways.
        return visible(snapshot, x - 4, y - 4, min - .25, x + 12, y + 12, max + 6);
    }

    public static boolean level(Object levels, int z, boolean original) {
        RotationCamera.Snapshot snapshot = CURRENT.get();
        if (original || snapshot == null) return original;
        Object chunk = GameAccess.field("zombie.iso.fboRenderChunk.FBORenderLevels", "chunk", levels);
        int x = integer("zombie.iso.IsoChunk", "wx", chunk) * 8;
        int y = integer("zombie.iso.IsoChunk", "wy", chunk) * 8;
        // B42.22 groups two floors in each native FBO level; retain the tree/wall halo.
        return visible(snapshot, x - 4, y - 4, z - .25, x + 12, y + 12, z + 7);
    }

    public static boolean square(Object square, boolean original) {
        RotationCamera.Snapshot snapshot = CURRENT.get();
        if (original || snapshot == null) return original;
        int x = integer("zombie.iso.IsoGridSquare", "x", square);
        int y = integer("zombie.iso.IsoGridSquare", "y", square);
        int z = integer("zombie.iso.IsoGridSquare", "z", square);
        boolean tree = (Boolean) GameAccess.field("zombie.iso.IsoGridSquare", "hasTree", square);
        double pad = tree ? 4 : .5;
        return visible(snapshot, x - pad, y - pad, z - .25, x + 1 + pad, y + 1 + pad,
                z + (tree ? 6 : 1.5));
    }

    /** Conservative projected AABB test; all loaded chunks still undergo a bounded frustum test. */
    public static boolean visible(RotationCamera.Snapshot snapshot, double x1, double y1, double z1,
                                   double x2, double y2, double z2) {
        float[] point = POINT.get();
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY, near = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY, far = Float.NEGATIVE_INFINITY;
        int projected = 0;
        for (int corner = 0; corner < 8; corner++) {
            if (!snapshot.project((corner & 1) == 0 ? x1 : x2, (corner & 2) == 0 ? y1 : y2,
                    (corner & 4) == 0 ? z1 : z2, point)) continue;
            projected++;
            left = Math.min(left, point[0]); right = Math.max(right, point[0]);
            top = Math.min(top, point[1]); bottom = Math.max(bottom, point[1]);
            near = Math.min(near, point[2]); far = Math.max(far, point[2]);
        }
        if (projected == 0) return false;
        if (projected < 8) return true; // A box crossing the camera plane needs clipping, so keep it.
        float margin = 64f / Math.max(.1f, snapshot.zoom());
        return right >= -margin && left <= snapshot.width() + margin
                && bottom >= -margin && top <= snapshot.height() + margin && far >= 0f && near <= 1f;
    }

    private static int integer(String owner, String field, Object value) {
        return ((Number) GameAccess.field(owner, field, value)).intValue();
    }

    private RotationBounds() {}
}
