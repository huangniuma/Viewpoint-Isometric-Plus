package vpiso;

import org.joml.Matrix4f;
import java.util.*;

/** Changes only the native tree camera; texture selection, wind and stencil drawing remain native. */
public final class RotationTrees {
    private static final ThreadLocal<RotationCamera.Snapshot> DRAWING = new ThreadLocal<>();
    private static final ThreadLocal<Order> ORDER = new ThreadLocal<>();
    private static final String TREE = "zombie.iso.fboRenderChunk.FBORenderTrees$Tree";

    /** Same-plane roots need one ordering for both alpha blending and actual depth. */
    public static Order order(List<Object> records) {
        RotationCamera.Snapshot snapshot = DRAWING.get();
        Order order = new Order(ORDER.get(), records);
        if (snapshot == null || records.size() < 2) { ORDER.set(order); return order; }
        Map<Root, Double> planes = new HashMap<>();
        IdentityHashMap<Object, Root> owners = new IdentityHashMap<>();
        Matrix4f view = snapshot.view();
        for (Object record : records) {
            Root root = new Root(value(record,"x"), value(record,"y"), value(record,"z"));
            owners.put(record, root);
            // Upright native billboards span Y; their plane depends on the two map
            // axes, including XL crown/trunk records sharing the same root.
            planes.put(root, view.m02() * (snapshot.camX() - (double) root.x)
                    + view.m22() * (snapshot.camY() - (double) root.y));
        }
        List<Root> roots = new ArrayList<>(planes.keySet());
        roots.sort(Comparator.comparingDouble(planes::get));
        Map<Root, Float> offsets = new HashMap<>();
        Comparator<Root> stable = Comparator.comparingDouble(Root::x)
                .thenComparingDouble(Root::y).thenComparingDouble(Root::z);
        for (int first = 0; first < roots.size();) {
            int end = first + 1;
            while (end < roots.size() && planes.get(roots.get(end)) - planes.get(roots.get(end - 1)) < .001) end++;
            roots.subList(first, end).sort(stable);
            float step = Math.min(16f, 512f / Math.max(1, end - first - 1));
            for (int i = first; i < end; i++) {
                Root root = roots.get(i);
                offsets.put(root, -(i - first - (end - first - 1) * .5f) * step * Math.ulp(.5f));
                order.leaves.put(root, Math.min(8f, step * .5f) * Math.ulp(.5f));
            }
            first = end;
        }
        for (Object record : records) {
            Root root = owners.get(record);
            order.depths.put(record, offsets.get(root));
            order.leafDepths.put(record, order.leaves.get(root));
        }
        Matrix4f projection = snapshot.projection();
        float viewZ = view.get(new float[16])[14], projectionZ = projection.get(new float[16])[14];
        Map<Root,Double> depths = new HashMap<>();
        for (Root root : roots) {
            double z = planes.get(root) + viewZ;
            double depth = (projection.m22() * z + projectionZ) / (projection.m23() * z + projection.m33()) * .5 + .5;
            depths.put(root, depth + offsets.get(root));
        }
        // Root skirts lie on the same ground plane after attachment. Preserve
        // the complete root order there, including roots outside a coplanar group.
        List<Root> ground = new ArrayList<>(roots);
        ground.sort(Comparator.comparingDouble((Root root)->-depths.get(root)).thenComparing(stable));
        Map<Root,Float> groundOffsets=new HashMap<>();
        float groundStep=Math.min(16f,512f/Math.max(1,ground.size()-1));
        for(int i=0;i<ground.size();i++)groundOffsets.put(ground.get(i),
                -i*groundStep*Math.ulp(.5f));
        for(Object record:records)order.groundDepths.put(record,groundOffsets.get(owners.get(record)));
        records.sort(Comparator.comparingDouble((Object record) -> -depths.get(owners.get(record)))
                .thenComparing(record -> owners.get(record), stable));
        ORDER.set(order);
        return order;
    }

    public static void endOrder(Order order) {
        order.records.clear(); order.records.addAll(order.original);
        if (order.previous == null) ORDER.remove(); else ORDER.set(order.previous);
    }

    /** Window-depth ULPs are converted to the native shader's pre-divide offset. */
    public static float treeDepth(Object tree) {
        Order order = ORDER.get();
        float offset = order == null ? 0f : order.depths.getOrDefault(tree, 0f);
        if (offset == 0f) return 0f;
        return offset * DRAWING.get().clipW(value(tree,"x"),value(tree,"y"),value(tree,"z"));
    }
    private static float value(Object tree,String field) {
        return ((Number) GameAccess.field(TREE,field,tree)).floatValue();
    }
    private record Root(float x,float y,float z) {}
    public static final class Order {
        private final Order previous;
        private final List<Object> records, original;
        private final IdentityHashMap<Object,Float> depths = new IdentityHashMap<>(), leafDepths = new IdentityHashMap<>(), groundDepths=new IdentityHashMap<>();
        private final Map<Root,Float> leaves = new HashMap<>();
        private Order(Order previous,List<Object> records) {
            this.previous=previous; this.records=records; this.original=new ArrayList<>(records);
        }
    }

    public static RotationCamera.Snapshot begin(RotationCamera.Snapshot snapshot) {
        RotationCamera.Snapshot previous = DRAWING.get();
        if (snapshot == null) DRAWING.remove(); else DRAWING.set(snapshot);
        return previous;
    }

    public static void end(RotationCamera.Snapshot previous) {
        if (previous == null) DRAWING.remove(); else DRAWING.set(previous);
    }

    public static boolean active() { return DRAWING.get() != null; }

    public static float groundDepth(Object tree) {
        Order order=ORDER.get();return order==null?0f:order.groundDepths.getOrDefault(tree,0f)-order.depths.getOrDefault(tree,0f);
    }

    /** Separate the native leaf overlay from its own bark without moving the tree's world depth. */
    public static float leafDepth(Object tree) {
        RotationCamera.Snapshot snapshot = DRAWING.get();
        float x = ((Number) GameAccess.field("zombie.iso.fboRenderChunk.FBORenderTrees$Tree", "x", tree)).floatValue();
        float y = ((Number) GameAccess.field("zombie.iso.fboRenderChunk.FBORenderTrees$Tree", "y", tree)).floatValue();
        float z = ((Number) GameAccess.field("zombie.iso.fboRenderChunk.FBORenderTrees$Tree", "z", tree)).floatValue();
        // The native shader adds userDepth before perspective division. Scale by W
        // so a small, stable window-depth separation survives the remote camera.
        Order order = ORDER.get();
        float lead = order == null ? 8f * Math.ulp(.5f) : order.leafDepths.getOrDefault(tree, 8f * Math.ulp(.5f));
        return -lead * snapshot.clipW(x, y, z);
    }

    /** Native candidate test with its foreground quadrant and square anchor rotated. */
    public static boolean obstructs(RotationCamera.Snapshot snapshot, Object tree) {
        Object square = GameAccess.call("zombie.iso.IsoObject", "getSquare", tree, new Class<?>[0]);
        if (snapshot == null || square == null) return false;
        int x = ((Number) GameAccess.field("zombie.iso.IsoGridSquare", "x", square)).intValue();
        int y = ((Number) GameAccess.field("zombie.iso.IsoGridSquare", "y", square)).intValue();
        int z = ((Number) GameAccess.field("zombie.iso.IsoGridSquare", "z", square)).intValue();
        Object player = ((Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null))[0];
        if (player == null) return false;
        boolean unrestricted = (Boolean) GameAccess.call("zombie.characters.IsoPlayer", "isAnyAimKeyDown", player,
                new Class<?>[0]) || GameAccess.call("zombie.characters.IsoPlayer", "getVehicle", player,
                new Class<?>[0]) != null;
        Matrix4f view = snapshot.view();
        if (!unrestricted) {
            int px = (int) Math.floor(snapshot.camX()), py = (int) Math.floor(snapshot.camY());
            if (view.m02() < 0f ? x < px : x > px) return false;
            if (view.m22() < 0f ? y < py : y > py) return false;
        }
        float[] screen = new float[3];
        if (!snapshot.project(x, y, z, screen)) return false;
        Object world = GameAccess.field("zombie.iso.IsoWorld", "instance", null);
        Object cell = GameAccess.field("zombie.iso.IsoWorld", "currentCell", world);
        return (Boolean) GameAccess.call("zombie.iso.IsoCell", "isInStencil", cell,
                new Class<?>[] { float.class, float.class }, screen[0] * snapshot.zoom(), screen[1] * snapshot.zoom());
    }

    public static boolean projection() {
        RotationCamera.Snapshot snapshot = DRAWING.get();
        if (snapshot == null) return false;
        Object stack = stack("projectionMatrixStack");
        Matrix4f matrix = (Matrix4f) GameAccess.call("zombie.core.opengl.MatrixStack", "alloc", stack, new Class<?>[0]);
        matrix.set(snapshot.projection());
        GameAccess.call("zombie.core.opengl.MatrixStack", "push", stack, new Class<?>[] { Matrix4f.class }, matrix);
        return true;
    }

    public static boolean modelView(float x, float y, float z) {
        RotationCamera.Snapshot snapshot = DRAWING.get();
        if (snapshot == null) return false;
        Object stack = stack("modelViewMatrixStack");
        Matrix4f matrix = (Matrix4f) GameAccess.call("zombie.core.opengl.MatrixStack", "alloc", stack, new Class<?>[0]);
        Matrix4f view = snapshot.view();
        float rx = view.m00(), rz = view.m20();
        float length = (float) Math.hypot(rx, rz);
        rx /= length; rz /= length;
        Matrix4f billboard = new Matrix4f().m00(rx).m02(rz).m20(-rz).m22(rx);
        matrix.set(view).translate((float) (snapshot.camX() - x), (float) ((z - snapshot.camZ()) * 2.4494896),
                (float) (snapshot.camY() - y)).mul(billboard).translate(0f, -.71999997f, 0f);
        GameAccess.call("zombie.core.opengl.MatrixStack", "push", stack, new Class<?>[] { Matrix4f.class }, matrix);
        return true;
    }

    private static Object stack(String field) {
        return GameAccess.field("zombie.core.Core", field, GameAccess.staticCall("zombie.core.Core", "getInstance"));
    }

    private RotationTrees() {}
}
