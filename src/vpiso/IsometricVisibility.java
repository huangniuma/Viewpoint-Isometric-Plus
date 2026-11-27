package vpiso;

import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import viewpoint.core.Frame;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.ModelDraws;
import viewpoint.render.SceneData;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.fboRenderChunk.ObjectRenderInfo;
import zombie.iso.fboRenderChunk.ObjectRenderLayer;

/** Builds this frame's main-view draw list from the game's current cutaway state. */
public final class IsometricVisibility {
    private static final int MAIN_VIEW_FLAGS = SceneData.VISIBLE | SceneData.NEAR;
    private static final int SHADOW_FLAGS = SceneData.CASTS_SHADOW | SceneData.CASTS_MID | SceneData.CASTS_FAR;
    private static final Map<SceneData, Map<Integer, Set<Integer>>> PACKED_HIDDEN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> PACKED_COLOUR = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<CaptureQueries> QUERIES = new ThreadLocal<>();

    // Owned by one drawer, used only between the native prepass and frame submission.
    // No visibility decision survives a capture, player change or square replacement.
    static final class CaptureQueries {
        final IdentityHashMap<Object, SquareDecision> squares = new IdentityHashMap<>();
        final IdentityHashMap<Object, ObjectDecision> objects = new IdentityHashMap<>();
        void clear() { squares.clear(); objects.clear(); }
    }
    private static final class SquareDecision {
        final int player;
        final boolean visible;
        int forced = -1;
        SquareDecision(int player, boolean visible) { this.player = player; this.visible = visible; }
    }
    private record ObjectDecision(Object square, int player, boolean forced, boolean allowed) {}

    static void beginCapture(CaptureQueries queries) { queries.clear(); QUERIES.set(queries); }
    static void endCapture() {
        CaptureQueries queries = QUERIES.get();
        if (queries != null) queries.clear();
        QUERIES.remove();
    }

    private IsometricVisibility() {}

    /** Apply the same native floor decision to independent objects, including static doors. */
    public static void applyModels(Frame frame, Object square, Object object, int first) {
        if (first < 0 || square == null || !Main.compatible) return;
        ModelDraws models = frame.scene.models;
        if (first >= models.count()) return;
        Bindings api = BindingsHolder.API;
        if (readLevel(api, square) < (int) Math.floor(frame.camZ + .0001f)) {
            NativeObjectState.model(frame, object, first, 1f);
            return;
        }
        float alpha = objectAlpha(frame, square, object, 0);
        NativeObjectState.model(frame, object, first, alpha);
        // This bridge captures only local player zero, after NativeIsoState.refresh.
        if (alpha > 0f) return;
        int[] flags = (int[]) GameAccess.field("viewpoint.render.ModelDraws", "flags", models);
        for (int i = first; i < models.count(); i++) {
            flags[i] &= ~ModelDraws.COLOUR;
            GameAccess.call("viewpoint.render.ModelDraws", "indices", models, new Class<?>[] { int.class }, i);
        }
    }

    /**
     * Call after captureWorld, before submitting the frame to the render thread.
     * The caller must disable VP's perspective room/portal culling for this capture.
     * NativeIsoState must already have refreshed the original view rules. Only this frame is
     * changed, not cached VP meshes. Camera-hidden structures still block physical light.
     */
    public static void apply(Frame frame, Object cell, int playerIndex) {
        Bindings api = BindingsHolder.API;
        Object cutaways = zombie.iso.fboRenderChunk.FBORenderCutaways.getInstance();
        SceneData scene = frame.scene;
        int playerLevel = (int) Math.floor(frame.camZ + 0.0001f);
        Map<ChunkLevel, Masks> masks = new HashMap<>();
        Map<Integer, Set<Integer>> packedHidden = new HashMap<>();
        PACKED_HIDDEN.put(scene, packedHidden);

        // Perspective room plans are replaced for this owned Frame only.
        scene.planEntries = 0;
        for (int index = 0; index < scene.meshCount; index++) {
            scene.meshPlan[index] = -1;
            scene.meshPlanLength[index] = 0;
            scene.meshModelSquares[index] = -1L;
            if ((scene.meshFlags[index] & (MAIN_VIEW_FLAGS | SHADOW_FLAGS)) == 0) {
                continue;
            }

            ChunkMeshData mesh = scene.meshes[index];
            if (!IsometricBridge.inView(scene.meshOffsets[index * 3] - 4f,
                    scene.meshOffsets[index * 3 + 1] + (mesh.level + .5f) * 2.4494896f,
                    scene.meshOffsets[index * 3 + 2] - 4f)) {
                scene.meshFlags[index] &= (byte) ~MAIN_VIEW_FLAGS;
            }
            if ((scene.meshFlags[index] & MAIN_VIEW_FLAGS) == 0) {
                continue;
            }
            // VP vertices use (-worldX, up, -worldY), relative to frame.cam*.
            int baseX = Math.round(frame.camX - scene.meshOffsets[index * 3]);
            int baseY = Math.round(frame.camY - scene.meshOffsets[index * 3 + 2]);
            if (mesh.level < playerLevel) continue;
            NativeObjectState.mesh(frame, cell, index, baseX, baseY, playerIndex);
            ChunkLevel key = new ChunkLevel(baseX, baseY, mesh.level);
            Masks visible = masks.computeIfAbsent(key, k -> readMasks(
                    api, cutaways, cell, playerIndex, k));
            scene.meshModelSquares[index] = visible.squares;
            Set<Integer> hiddenPacked = new HashSet<>(visible.hiddenObjects);
            int[] modelObjects = (int[]) GameAccess.field(
                    "viewpoint.render.ChunkMeshData", "modelObjects", mesh);
            if (modelObjects != null) for (int object : modelObjects) {
                // VP keeps a wall model if either side of its edge is visible. A native
                // hidden source object must not reappear through its exterior neighbour.
                if (object >= 0 && (object >>> 16) < 64
                        && (visible.squares & (1L << (object >>> 16))) == 0)
                    hiddenPacked.add(object);
            }
            if (!hiddenPacked.isEmpty()) packedHidden.put(index, hiddenPacked);

            if (visible.squares == 0L) {
                scene.meshFlags[index] &= (byte) ~MAIN_VIEW_FLAGS;
                continue;
            }
            if (visible.squares == -1L && visible.hiddenObjects.isEmpty()) {
                continue;
            }
            makePlan(scene, index, mesh, visible);
        }
    }

    public static boolean enterPacked(boolean planned) {
        boolean previous = PACKED_COLOUR.get();
        PACKED_COLOUR.set(planned);
        return previous;
    }

    public static void leavePacked(boolean previous) { PACKED_COLOUR.set(previous); }

    /** Capture an optional overlay's native source decision on the game thread. */
    public static float objectAlpha(Frame frame, Object square, Object object, int player) {
        if (square == null) return 0f;
        Bindings api = BindingsHolder.API;
        Object cutaways = zombie.iso.fboRenderChunk.FBORenderCutaways.getInstance();
        if (!shouldRender(api, cutaways, player, square)
                || !allowObject(api, square, object, player,
                    forcedSquare(api, cutaways, player, square))) return 0f;
        if (object == null) return 1f;
        ObjectRenderInfo info = ((IsoObject) object).getRenderInfo(player);
        float alpha = objectTargetAlpha((IsoObject) object, player, info.targetAlpha);
        alpha = Float.isFinite(alpha) ? Math.max(0f, Math.min(1f, alpha)) : 0f;
        return NativeObjectState.alpha(object, player, alpha);
    }

    /** Use VP's exact object-instance collector only for mixed upper-square colour draws. */
    public static boolean collectPacked(SceneData scene, int index, long squares) {
        if (!Main.compatible || !PACKED_COLOUR.get()) return false;
        Map<Integer, Set<Integer>> byMesh = PACKED_HIDDEN.get(scene);
        Set<Integer> hidden = byMesh == null ? null : byMesh.get(index);
        if (hidden == null) return false;
        if (squares == 0L) return true;
        int[] objects = (int[]) GameAccess.field("viewpoint.render.ChunkMeshData", "modelObjects", scene.meshes[index]);
        if (objects == null) return false;
        Set<Integer> owners = new LinkedHashSet<>();
        for (int object : objects) if (!hidden.contains(object)) owners.add(object);
        for (int owner : owners) GameAccess.call("viewpoint.render.PackDraws", "collectObject", null,
                new Class<?>[] {SceneData.class, int.class, long.class, int.class}, scene, index, squares, owner);
        return true;
    }

    private static Masks readMasks(Bindings api, Object cutaways, Object cell,
                                   int playerIndex, ChunkLevel key) {
        long squares = 0L;
        Set<Integer> hiddenObjects = new HashSet<>();
        for (int square = 0; square < 64; square++) {
            Object nativeSquare = readSquare(api, cell,
                    key.x + square % 8, key.y + square / 8, key.level);
            long bit = 1L << square;
            // Above the player, a missing square is not a visible building square.
            // This also covers roof faces VP emits into otherwise empty squares.
            if (nativeSquare == null) {
                continue;
            }
            if (shouldRender(api, cutaways, playerIndex, nativeSquare)) {
                squares |= bit;
                boolean forced = forcedSquare(api, cutaways, playerIndex, nativeSquare);
                List<?> objects = ((IsoGridSquare) nativeSquare).getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    if (!allowObject(api, nativeSquare, objects.get(i), playerIndex, forced))
                        hiddenObjects.add(square << 16 | i);
                }
            }
        }
        // Wall shape/partial alpha still comes from NativeWallCutaway. Object None/zero
        // decisions also apply on the player's floor (including hidden furniture).
        return new Masks(squares, hiddenObjects);
    }

    /** Match native object classification even when the containing upper square stays visible. */
    private static boolean allowObject(Bindings api, Object square, Object object, int player, boolean forced) {
        CaptureQueries queries = QUERIES.get();
        if (queries == null || object == null) return classifyObject(api, square, object, player, forced);
        ObjectDecision prior = queries.objects.get(object);
        if (prior != null && prior.square == square && prior.player == player && prior.forced == forced)
            return prior.allowed;
        boolean allowed = classifyObject(api, square, object, player, forced);
        queries.objects.put(object, new ObjectDecision(square, player, forced, allowed));
        return allowed;
    }

    private static boolean classifyObject(Bindings api, Object square, Object object, int player, boolean forced) {
        if (object == null) return true;
        // Some independent model records have no native sprite object to classify.
        IsoObject nativeObject = (IsoObject) object;
        if (nativeObject.getSquare() == null) return true;
        Object renderer = zombie.iso.fboRenderChunk.FBORenderCell.instance;
        // Reuse the native producer, including its opaque-to-translucent conversion.
        // Testing only the sprite type would incorrectly hide translucent window panels.
        try { api.objectInfoHandle.invokeExact(renderer, player, square, object); }
        catch (Throwable error) { throw new IllegalStateException("Native cutaway query failed: " + api.objectInfo.getName(), error); }
        ObjectRenderInfo info = nativeObject.getRenderInfo(player);
        ObjectRenderLayer layer = info.layer;
        if (layer == ObjectRenderLayer.None) return false;
        float target = objectTargetAlpha(nativeObject, player, info.targetAlpha);
        if (NativeObjectState.alpha(object, player, target) <= 0f) return false;
        // Only the native opaque MinusFloor consumer has this force-square gate.
        // Translucent windows and the south/east consumer have their own rules.
        if (!forced || layer != ObjectRenderLayer.MinusFloor) return true;
        var grid = nativeObject.getSpriteGrid();
        return grid != null && grid.getLevels() != 1;
    }

    /** Native wall attachment visibility is a draw-consumer rule, after FBO classification. */
    private static float objectTargetAlpha(IsoObject object, int player, float target) {
        var sprite = object.getSprite();
        if (sprite == null || sprite.getTileType() != zombie.iso.SpriteDetails.IsoObjectType.MAX
                || sprite.cutN || sprite.cutW
                || object instanceof zombie.iso.objects.IsoDoor || object instanceof zombie.iso.objects.IsoWindow
                || object.container != null) return target;
        var properties = sprite.getProperties();
        if (properties.has(zombie.iso.SpriteDetails.IsoFlagType.waterPiped)) return target;
        boolean north = properties.has(zombie.iso.SpriteDetails.IsoFlagType.attachedN);
        boolean west = properties.has(zombie.iso.SpriteDetails.IsoFlagType.attachedW);
        if (!north && !west) return target;
        var square = object.getSquare();
        if (square == null) return target;
        int caps = NativeWallCaps.caps(square);
        long now = ((Number) GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell", "currentTimeMillis",
                zombie.iso.fboRenderChunk.FBORenderCell.instance)).longValue();
        int cutaway = square.getPlayerCutawayFlag(player, now);
        return north && (caps & 4) != 0 && (cutaway & 1) != 0
                || west && (caps & 8) != 0 && (cutaway & 2) != 0 ? 0f : target;
    }

    private static boolean shouldRender(Bindings api, Object cutaways, int player, Object square) {
        CaptureQueries queries = QUERIES.get();
        if (queries == null) return readShouldRender(api, cutaways, player, square);
        SquareDecision prior = queries.squares.get(square);
        if (prior != null && prior.player == player) return prior.visible;
        boolean visible = readShouldRender(api, cutaways, player, square);
        queries.squares.put(square, new SquareDecision(player, visible));
        return visible;
    }

    private static boolean forcedSquare(Bindings api, Object cutaways, int player, Object square) {
        CaptureQueries queries = QUERIES.get();
        SquareDecision prior = queries == null ? null : queries.squares.get(square);
        if (prior == null || prior.player != player) return readForced(api, cutaways, player, square);
        if (prior.forced < 0) prior.forced = readForced(api, cutaways, player, square) ? 1 : 0;
        return prior.forced != 0;
    }

    private static boolean readShouldRender(Bindings api, Object cutaways, int player, Object square) {
        if (!readBuilding(api, cutaways, player, square)) return false;
        IsoGridSquare nativeSquare = (IsoGridSquare) square;
        var chunk = nativeSquare.getChunk();
        if (chunk == null) return false;
        var data = chunk.getCutawayDataForLevel(nativeSquare.getZ());
        return data.shouldRenderSquare(player, nativeSquare);
    }

    private static void makePlan(SceneData scene, int index, ChunkMeshData mesh,
                                 Masks visible) {
        int start = scene.planEntries;
        boolean changed = false;
        int[] spans = (int[]) GameAccess.field(
                "viewpoint.render.ChunkMeshData", "spans", mesh);
        int[] pageSpans = spans == null ? null : (int[]) GameAccess.field(
                "viewpoint.render.ChunkMeshData", "pageSpans", mesh);
        boolean coarse = mesh.runs != null && mesh.pageRuns != null;
        // Cook can retain exact object spans while omitting an entirely unowned
        // coarse run table. Those spans still carry native square/object decisions.
        if (!coarse && pageSpans == null) return;
        // VP run records are (owner, first vertex, vertex count); ownership is
        // squareIndex << 2 | kind, with north=1 and west=2. This is public mesh
        // metadata, not a copy of VP's room/portal selection implementation.
        for (int page = 0; page < mesh.first.length; page++) {
            int firstRun = coarse ? mesh.pageRuns[page] : 0;
            int lastRun = coarse ? mesh.pageRuns[page + 1] : 1;
            for (int run = firstRun; run < lastRun; run++) {
                int owner = coarse ? mesh.runs[run * 3] : -1;
                int first = coarse ? mesh.runs[run * 3 + 1] : mesh.first[page];
                int count = coarse ? mesh.runs[run * 3 + 2] : mesh.count[page];
                boolean keep = true;
                if (owner >= 0) {
                    int square = owner >>> 2;
                    if (square < 64) {
                        long bit = 1L << square;
                        keep = (visible.squares & bit) != 0;
                    }
                }
                if (!keep) {
                    changed = true;
                    continue;
                }
                int end = first + count;
                if (pageSpans != null) {
                    // VP's object spans retain exact object ownership even when its
                    // coarser runs merge neighbours or omit a cap's square owner.
                    for (int span = pageSpans[page]; span < pageSpans[page + 1]; span++) {
                        int cut = spans[span * 3 + 1], cutEnd = cut + spans[span * 3 + 2];
                        if (cutEnd <= first || cut >= end) continue;
                        int object = spans[span * 3];
                        boolean hidden = object >= 0 && (object >>> 16) < 64
                                && ((visible.squares & (1L << (object >>> 16))) == 0
                                    || visible.hiddenObjects.contains(object));
                        if (!hidden) continue;
                        addRange(scene, start, page, first, Math.max(first, cut) - first);
                        first = Math.min(end, cutEnd);
                        changed = true;
                    }
                }
                addRange(scene, start, page, first, end - first);
            }
        }
        if (changed) {
            scene.meshPlan[index] = start;
            scene.meshPlanLength[index] = scene.planEntries - start;
        } else {
            scene.planEntries = start;
        }
    }

    private static void addRange(SceneData scene, int start, int page, int first, int count) {
        if (count <= 0) return;
        int previous = (scene.planEntries - 1) * 3;
        if (scene.planEntries > start && scene.plan[previous] == page
                && scene.plan[previous + 1] + scene.plan[previous + 2] == first)
            scene.plan[previous + 2] += count;
        else scene.addPlanEntry(page, first, count);
    }

    private static Object readSquare(Bindings api, Object cell, int x, int y, int level) {
        try { return (Object) api.squareHandle.invokeExact(cell, x, y, level); }
        catch (Throwable error) { throw new IllegalStateException("Native cutaway query failed: " + api.square.getName(), error); }
    }

    private static boolean readBuilding(Bindings api, Object cutaways, int player, Object square) {
        try { return (boolean) api.shouldRenderHandle.invokeExact(cutaways, player, square); }
        catch (Throwable error) { throw new IllegalStateException("Native cutaway query failed: " + api.shouldRender.getName(), error); }
    }

    private static boolean readForced(Bindings api, Object cutaways, int player, Object square) {
        try { return (boolean) api.forceSquareHandle.invokeExact(cutaways, player, square); }
        catch (Throwable error) { throw new IllegalStateException("Native cutaway query failed: " + api.forceSquare.getName(), error); }
    }

    private static int readLevel(Bindings api, Object square) {
        try { return (int) api.levelHandle.invokeExact(square); }
        catch (Throwable error) { throw new IllegalStateException("Native cutaway query failed: " + api.level.getName(), error); }
    }

    private record ChunkLevel(int x, int y, int level) {}
    private record Masks(long squares, Set<Integer> hiddenObjects) {}

    private static final class BindingsHolder {
        private static final Bindings API = new Bindings();
    }

    private static final class Bindings {
        final Method cutawaysInstance;
        final Method shouldRender;
        final Method chunk, cutawayData, shouldRenderSquare;
        final Method forceSquare, objects, objectSquare, spriteGrid, gridLevels, objectInfo, renderInfo;
        final Method square;
        final Method level;
        final MethodHandle objectInfoHandle, squareHandle, shouldRenderHandle, forceSquareHandle, levelHandle;

        Bindings() {
            try {
                Class<?> cutaways = Class.forName("zombie.iso.fboRenderChunk.FBORenderCutaways");
                Class<?> gridSquare = Class.forName("zombie.iso.IsoGridSquare");
                Class<?> cell = Class.forName("zombie.iso.IsoCell");
                cutawaysInstance = cutaways.getMethod("getInstance");
                shouldRender = cutaways.getMethod("shouldRenderBuildingSquare", int.class, gridSquare);
                forceSquare = cutaways.getMethod("isForceRenderSquare", int.class, gridSquare);
                objects = gridSquare.getMethod("getObjects");
                Class<?> object = Class.forName("zombie.iso.IsoObject");
                objectSquare = object.getMethod("getSquare");
                spriteGrid = object.getMethod("getSpriteGrid");
                gridLevels = Class.forName("zombie.iso.sprite.IsoSpriteGrid").getMethod("getLevels");
                renderInfo = object.getMethod("getRenderInfo", int.class);
                objectInfo = Class.forName("zombie.iso.fboRenderChunk.FBORenderCell")
                        .getDeclaredMethod("calculateObjectRenderInfo", int.class, gridSquare, object);
                objectInfo.setAccessible(true);
                objectInfoHandle = MethodHandles.lookup().unreflect(objectInfo).asType(
                        MethodType.methodType(void.class, Object.class, int.class, Object.class, Object.class));
                chunk = gridSquare.getMethod("getChunk");
                Class<?> chunkType = Class.forName("zombie.iso.IsoChunk");
                cutawayData = chunkType.getMethod("getCutawayDataForLevel", int.class);
                shouldRenderSquare = Class.forName("zombie.iso.fboRenderChunk.FBORenderCutaways$ChunkLevelData")
                        .getMethod("shouldRenderSquare", int.class, gridSquare);
                square = cell.getMethod("getGridSquare", int.class, int.class, int.class);
                level = gridSquare.getMethod("getZ");
                squareHandle = MethodHandles.lookup().unreflect(square).asType(
                        MethodType.methodType(Object.class, Object.class, int.class, int.class, int.class));
                shouldRenderHandle = MethodHandles.lookup().unreflect(shouldRender).asType(
                        MethodType.methodType(boolean.class, Object.class, int.class, Object.class));
                forceSquareHandle = MethodHandles.lookup().unreflect(forceSquare).asType(
                        MethodType.methodType(boolean.class, Object.class, int.class, Object.class));
                levelHandle = MethodHandles.lookup().unreflect(level).asType(
                        MethodType.methodType(int.class, Object.class));
                // Public reflected queries still need no repeated access checks.
                // Keep their original Method slots for ABI verification and fixtures.
                for (Method method : new Method[] {cutawaysInstance, shouldRender, chunk, cutawayData,
                        shouldRenderSquare, forceSquare, objects, objectSquare, spriteGrid,
                        gridLevels, objectInfo, renderInfo, square, level}) method.setAccessible(true);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("This game version has no compatible cutaway API", ex);
            }
        }
    }
}
