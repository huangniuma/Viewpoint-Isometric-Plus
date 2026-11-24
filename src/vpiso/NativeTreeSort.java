package vpiso;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Capture-owned keys: retain native stable order without rereading each comparator's coordinates. */
final class NativeTreeSort {
    private static final Class<?>[] NONE = new Class<?>[0];
    private static final Comparator<Key> FIXED = Comparator.comparingDouble((Key k) -> k.fixed)
            .thenComparingDouble(k -> k.y).thenComparingDouble(k -> k.x);
    private static final Comparator<Key> PROJECTED = Comparator.comparingDouble(k -> k.projected);
    private Key[] keys = new Key[0];
    private final float[] point = new float[3];

    private static final class Key {
        Object tree;
        float x, y;
        double fixed, projected;
    }

    void sort(List<Object> trees, RotationCamera.Snapshot rotation) {
        int count = trees.size();
        if (count < 2) return;
        if (count > keys.length) {
            int old = keys.length;
            keys = Arrays.copyOf(keys, Math.max(count, Math.max(16, old * 2)));
            for (int i = old; i < keys.length; i++) keys[i] = new Key();
        }
        try {
            for (int i = 0; i < count; i++) {
                Object tree = trees.get(i);
                Key key = keys[i];
                key.tree = tree;
                key.x = coordinate(tree, "getX");
                key.y = coordinate(tree, "getY");
                float z = coordinate(tree, "getZ");
                // Preserve float x+y before double 2*z and both original stable sorts.
                key.fixed = key.x + key.y + 2d * z;
                key.projected = rotation != null && rotation.project(key.x, key.y, z, point)
                        ? -point[2] : 0;
            }
            Arrays.sort(keys, 0, count, FIXED);
            if (rotation != null) Arrays.sort(keys, 0, count, PROJECTED);
            for (int i = 0; i < count; i++) trees.set(i, keys[i].tree);
        } finally {
            for (int i = 0; i < count; i++) keys[i].tree = null;
        }
    }

    private static float coordinate(Object tree, String method) {
        return ((Number) GameAccess.call("zombie.iso.IsoObject", method, tree, NONE)).floatValue();
    }
}
