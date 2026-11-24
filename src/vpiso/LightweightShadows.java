package vpiso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import viewpoint.core.Frame;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.ModelDraws;
import viewpoint.render.SceneData;
import zombie.core.textures.TextureID;

/** Cached caster ranges. Colour, receiving geometry and shared VP meshes remain intact. */
public final class LightweightShadows {
    private static final Class<?>[] NONE = {};
    private static final int[] EMPTY = {};
    private static final String COOKER = "viewpoint.world.Cook$Cooker";
    private static final String OP = "viewpoint.world.Recipe$Op";
    private static final Map<Object, Set<Integer>> SOURCES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ChunkMeshData, Selection> SELECTIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Builder> COOKING = new ThreadLocal<>();
    private static final ThreadLocal<SceneData> CASTING = new ThreadLocal<>();
    private record Selection(int[] vertices, int[] plants, int[] models) {}

    public static boolean buildingType(String type) {
        return type.equals("wall") || type.startsWith("stairs") || type.startsWith("door")
                || type.startsWith("window") || type.startsWith("WestRoof");
    }

    public static boolean building(Object object) {
        if (object == null) return false;
        if (GameAccess.type("zombie.iso.objects.IsoDoor").isInstance(object)
                || GameAccess.type("zombie.iso.objects.IsoWindow").isInstance(object)) return true;
        Object sprite = GameAccess.call("zombie.iso.IsoObject", "getSprite", object, NONE);
        if (sprite == null) return false;
        String type = String.valueOf(GameAccess.call("zombie.iso.sprite.IsoSprite", "getType", sprite, NONE));
        return buildingType(type) || (Boolean) GameAccess.field("zombie.iso.sprite.IsoSprite", "solidfloor", sprite);
    }

    /** Gather thread: retain integer ownership only, never a live world object. */
    public static void source(Object object) {
        if (!GameAccess.type("zombie.iso.objects.IsoTree").isInstance(object) && !building(object)) return;
        Object recipe = GameAccess.field("viewpoint.world.WorldMesher", "recipe", null);
        int owner = number("viewpoint.world.WorldMesher", "object", null);
        if (recipe != null && owner >= 0) synchronized (SOURCES) {
            SOURCES.computeIfAbsent(recipe, k -> new HashSet<>()).add(owner);
        }
    }

    public static Builder begin(Object recipe) {
        Builder previous = COOKING.get();
        Set<Integer> owners;
        synchronized (SOURCES) { owners = Set.copyOf(SOURCES.getOrDefault(recipe, Set.of())); }
        COOKING.set(new Builder(owners));
        return previous;
    }
    public static void finish(Object cooker, ChunkMeshData mesh, Builder previous) {
        try {
            Builder builder = COOKING.get();
            if (builder != null && mesh != null) {
                float[] data = (float[]) GameAccess.field(COOKER, "instances", cooker);
                int count = number(COOKER, "modelFloats", cooker) / 14;
                int[] objects = (int[]) GameAccess.field(COOKER, "modelObjects", cooker);
                int[] runs = (int[]) GameAccess.field("viewpoint.render.ChunkMeshData", "modelRuns", mesh);
                ArrayList<Integer> models = new ArrayList<>();
                if (runs != null) for (int r = 0; r < runs.length; r += 3) {
                    int end = runs[r + 1] + runs[r + 2], start = -1;
                    for (int i = runs[r + 1]; i <= end; i++) {
                        boolean keep = i < end && i < count && (builder.owners.contains(objects == null ? -1 : objects[i])
                                || (Float.floatToRawIntBits(data[i * 14 + 6]) & (TreeModelScope.TREE | 16384 | 32768)) != 0);
                        if (keep && start < 0) start = i;
                        if (!keep && start >= 0) { add(models, runs[r], start, i - start); start = -1; }
                    }
                }
                SELECTIONS.put(mesh, new Selection(array(builder.vertices), array(builder.plants), array(models)));
            }
        } finally { if (previous == null) COOKING.remove(); else COOKING.set(previous); }
    }
    public static void page(Object batch, int page) {
        Builder builder = COOKING.get();
        if (builder != null) {
            builder.page = page;
            builder.floor = (Boolean) GameAccess.field("viewpoint.world.Recipe$Batch", "floor", batch);
        }
    }
    public static void operation(Object op) {
        Builder builder = COOKING.get();
        if (builder != null) builder.keep = builder.floor || builder.owners.contains(number(OP, "object", op))
                || structural(number(OP, "kind", op), number(OP, "source", op), number(OP, "flags", op));
    }
    public static boolean structural(int kind, int source, int flags) {
        // Actual Recipe operation kinds and MeshSources; CROPS/PLANTS/OBJECTS stay excluded.
        return kind == 6 || kind == 7 || kind == 8 || kind == 9
                || source == 1 || source == 2 || source == 3 || source == 5 || source == 6 || source == 7 || source == 12
                || (flags & (16384 | 32768)) != 0;
    }
    public static void vertices(int first, int end) {
        Builder builder = COOKING.get();
        if (builder != null && builder.keep) add(builder.vertices, builder.page, first, end - first);
    }
    public static void plant(Object op, int first, int end) {
        Builder builder = COOKING.get();
        if (builder != null && (builder.owners.contains(number(OP, "object", op))
                || (number(OP, "flags", op) & TreeModelScope.TREE) != 0))
            add(builder.plants, builder.page, first / 14, (end - first) / 14);
    }

    /** Render thread: scope only the real shadow maps, never the receiver pass (also mask 2). */
    public static SceneData enter(SceneData scene, IsometricEffects effects) {
        SceneData previous = CASTING.get();
        if (effects != null && effects.lightweightShadows() && IsometricBridge.renderFrame() != null
                && IsometricBridge.renderFrame().scene == scene) CASTING.set(scene);
        else CASTING.remove();
        return previous;
    }
    public static void restore(SceneData previous) { if (previous == null) CASTING.remove(); else CASTING.set(previous); }
    public static SceneData forMask(byte mask) {
        SceneData previous = CASTING.get();
        // Local maps use lamp bit128, spot cone bit32, or VISIBLE|SNOW (65).
        if ((mask & (SceneData.CASTS_SHADOW | SceneData.CASTS_MID | SceneData.CASTS_FAR)) == 0
                && mask != (byte)128 && mask != 32 && mask != 65) CASTING.remove();
        return previous;
    }
    public static boolean active(SceneData scene) { return CASTING.get() == scene && scene != null; }
    public static boolean active() { return CASTING.get() != null; }

    public static boolean collect(SceneData scene, int index) {
        if (!active(scene)) return false;
        ChunkMeshData mesh = scene.meshes[index];
        Selection selection = SELECTIONS.get(mesh);
        if (selection != null) for (int r = 0; r < selection.vertices.length; r += 3)
            submit(scene, index, selection.vertices[r], selection.vertices[r + 1], selection.vertices[r + 2]);
        else for (int page = 0; page < mesh.first.length; page++) {
            // External mesh providers can still identify their physical architectural pages.
            if (page == mesh.floorPage || mesh.ceilingGroups != null && mesh.ceilingGroups[page] >= 0
                    || mesh.wallPages != null && mesh.wallPages[page] >= 0)
                submit(scene, index, page, mesh.first[page], mesh.count[page]);
        }
        return true;
    }
    private static void submit(SceneData scene, int index, int page, int first, int count) {
        if (count <= 0) return;
        ChunkMeshData mesh = scene.meshes[index];
        Object bucket = GameAccess.call("viewpoint.render.Meshes", "bucket", null,
                new Class<?>[]{ChunkMeshData.class, int.class, int.class}, mesh, index, page);
        if (bucket != null) GameAccess.call("viewpoint.render.Meshes$Bucket", "add", bucket,
                new Class<?>[]{int.class, int.class, int.class}, count, mesh.arenaFirst + first, index);
    }
    public static boolean plants(SceneData scene, byte mask, int layer) {
        if (!active(scene)) return false;
        GameAccess.set("viewpoint.render.Meshes", "bucketCount", null, 0);
        ((Map<?, ?>) GameAccess.field("viewpoint.render.Meshes", "byPage", null)).clear();
        for (int index = 0; index < scene.meshCount; index++) {
            if (!(Boolean) GameAccess.call("viewpoint.render.Meshes", "drawn", null,
                    new Class<?>[]{SceneData.class, int.class, byte.class, int.class}, scene, index, mask, layer)) continue;
            ChunkMeshData mesh = scene.meshes[index];
            Selection selection = SELECTIONS.get(mesh);
            if (selection == null) continue;
            int base = number("viewpoint.render.ChunkMeshData", "plantBase", mesh);
            for (int r = 0; r < selection.plants.length; r += 3) {
                TextureID page = mesh.pages[selection.plants[r]];
                int slot = ((Number) GameAccess.call("viewpoint.render.ChunkMeshData", "slot", mesh,
                        new Class<?>[]{int.class}, base + selection.plants[r + 1])).intValue();
                Object bucket = GameAccess.call("viewpoint.render.Meshes", "bucket", null,
                        new Class<?>[]{Object.class, TextureID.class, int.class, int.class, boolean.class}, page, page, 0, 0, false);
                GameAccess.call("viewpoint.render.Meshes$Bucket", "addSlots", bucket,
                        new Class<?>[]{int.class, int.class, int.class}, selection.plants[r + 2] * 6, slot * 6, index);
            }
        }
        return true;
    }
    public static int[] models(ChunkMeshData mesh) {
        Selection selection = SELECTIONS.get(mesh);
        return selection == null ? EMPTY : selection.models;
    }

    /** Models.own contains rigid furniture, building parts and world inventory items only.
     * Characters/animals and vehicles keep their original independent capture paths. */
    public static void objectModels(Frame frame, Object object, Object item, int first) {
        if (first < 0 || !IsometricControls.effects().lightweightShadows() || building(object) || item != null) return;
        ModelDraws draws = frame.scene.models;
        int[] flags = (int[]) GameAccess.field("viewpoint.render.ModelDraws", "flags", draws);
        for (int i = first; i < draws.count(); i++) {
            flags[i] &= ~ModelDraws.SHADOW;
            GameAccess.call("viewpoint.render.ModelDraws", "indices", draws, new Class<?>[]{int.class}, i);
        }
    }
    private static int number(String type, String field, Object object) { return ((Number) GameAccess.field(type, field, object)).intValue(); }
    private static void add(ArrayList<Integer> values, int page, int first, int count) {
        if (count <= 0) return;
        int n = values.size();
        if (n >= 3 && values.get(n - 3) == page && values.get(n - 2) + values.get(n - 1) == first)
            values.set(n - 1, values.get(n - 1) + count);
        else { values.add(page); values.add(first); values.add(count); }
    }
    private static int[] array(ArrayList<Integer> values) { return values.stream().mapToInt(Integer::intValue).toArray(); }
    public static final class Builder {
        final Set<Integer> owners;
        final ArrayList<Integer> vertices = new ArrayList<>(), plants = new ArrayList<>();
        int page;
        boolean floor, keep;
        Builder(Set<Integer> owners) { this.owners = owners; }
    }
    private LightweightShadows() {}
}
