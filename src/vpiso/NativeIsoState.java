package vpiso;

import java.util.ArrayList;

/** Keeps native visibility and interaction state alive when only the world drawing is replaced. */
public final class NativeIsoState {
    private static final String CELL = "zombie.iso.fboRenderChunk.FBORenderCell";
    private static final String CUTS = "zombie.iso.fboRenderChunk.FBORenderCutaways";
    private static final String LEVELS = "zombie.iso.fboRenderChunk.FBORenderLevels";
    private static final Class<?>[] INT = { int.class };
    private static boolean announced;
    private static boolean nativeNeedsRebuild;
    public static void newSession() { announced = nativeNeedsRebuild = false; }

    public static Object refresh(Object cell, Object player, int playerIndex) {
        RotationCamera.Snapshot previousRotation = RotationBounds.begin(RotationCamera.snapshot());
        try { return refreshNative(cell, player, playerIndex); }
        finally { RotationBounds.end(previousRotation); }
    }

    private static Object refreshNative(Object cell, Object player, int playerIndex) {
        nativeNeedsRebuild = true;
        Object renderer = GameAccess.field(CELL, "instance", null);
        Object cutaways = GameAccess.staticCall(CUTS, "getInstance");
        GameAccess.set(CELL, "cell", renderer, cell);
        GameAccess.set(CUTS, "cell", cutaways, cell);
        GameAccess.set(CELL, "currentTimeMillis", renderer, System.currentTimeMillis());
        refreshBounds(cell, player, playerIndex);
        refreshPeeking(cell, player, playerIndex);
        NativeTreeCutaway.refresh(cell, player, playerIndex);
        GameAccess.call(CELL, "callBeforeWorldRender", renderer, INT, playerIndex);
        Object[] data = (Object[]) GameAccess.field(CELL, "perPlayerData", renderer);
        Object state = data[playerIndex];
        int level = (int) Math.floor(GameAccess.coordinate(player, "getZ"));
        int previous = ((Number) GameAccess.field(state.getClass().getName(), "lastZ", state)).intValue();
        if (previous != level) {
            GameAccess.set("zombie.characters.IsoPlayer", "dirtyRecalcGridStack", player, true);
            GameAccess.set(state.getClass().getName(), "lastZ", state, level);
            GameAccess.call(CELL, "checkSeenRooms", renderer,
                    new Class<?>[] { GameAccess.type("zombie.characters.IsoPlayer"), int.class }, player, level);
        }
        boolean oldCache = (Boolean) GameAccess.field("zombie.iso.fboRenderChunk.FBORenderLevels", "clearCachedSquares", null);
        GameAccess.set("zombie.iso.fboRenderChunk.FBORenderLevels", "clearCachedSquares", null, true);
        try {
            float timer = ((Number) GameAccess.field("zombie.characters.IsoPlayer", "dirtyRecalcGridStackTime", player)).floatValue();
            Object time = GameAccess.staticCall("zombie.GameTime", "getInstance");
            float multiplier = ((Number) GameAccess.call("zombie.GameTime", "getMultiplier", time, new Class<?>[0])).floatValue();
            GameAccess.set("zombie.characters.IsoPlayer", "dirtyRecalcGridStackTime", player, timer - multiplier / 4f);
            boolean force = (Boolean) GameAccess.call(CELL, "checkNewlyOnScreenChunks", renderer, INT, playerIndex);
            force |= (Boolean) GameAccess.call(CELL, "recalculateGridStacks", renderer,
                    new Class<?>[] { GameAccess.type("zombie.characters.IsoPlayer"), int.class }, player, playerIndex);
            force |= (Boolean) GameAccess.call(CUTS, "checkPlayerRoom", cutaways, INT, playerIndex);
            force |= (Boolean) GameAccess.call("zombie.iso.IsoCell", "SetCutawayRoomsForPlayer", cell, new Class<?>[0]);
            ArrayList<?> chunks = (ArrayList<?>) GameAccess.field(state.getClass().getName(), "onScreenChunks", state);
            force |= (Boolean) GameAccess.call(CUTS, "checkExteriorWalls", cutaways, new Class<?>[] { ArrayList.class }, chunks);
            force |= (Boolean) GameAccess.call(CUTS, "checkSlopedSurfaces", cutaways, new Class<?>[] { ArrayList.class }, chunks);
            if (force) GameAccess.call(CUTS, "squareChanged", cutaways,
                    new Class<?>[] { GameAccess.type("zombie.iso.IsoGridSquare") }, (Object) null);
            force |= (Boolean) GameAccess.call(CUTS, "checkOccludedRooms", cutaways,
                    new Class<?>[] { int.class, ArrayList.class }, playerIndex, chunks);
            GameAccess.call(CELL, "prepareChunksForUpdating", renderer, INT, playerIndex);
            if (force) GameAccess.call(CUTS, "doCutawayVisitSquares", cutaways,
                    new Class<?>[] { int.class, ArrayList.class }, playerIndex, chunks);
            GameAccess.call(CELL, "updateChunkLighting", renderer, INT, playerIndex);
            GameAccess.call(CELL, "checkBlackedOutBuildings", renderer, INT, playerIndex);
            GameAccess.call(CELL, "checkBlackedOutRooms", renderer, INT, playerIndex);
            ((boolean[]) GameAccess.field("zombie.iso.IsoCell", "playerCutawaysDirty", cell))[playerIndex] = false;
            if (!announced) {
                announced = true;
                System.out.println("[VPISO] native cutaway/visibility prepass active; on-screen chunks=" + chunks.size());
            }
            return renderer;
        } finally {
            GameAccess.set("zombie.iso.fboRenderChunk.FBORenderLevels", "clearCachedSquares", null, oldCache);
        }
    }

    /** Acknowledge only CPU work captured successfully into this owned frame. */
    public static void complete(Object renderer, int playerIndex) {
        Object state = ((Object[]) GameAccess.field(CELL, "perPlayerData", renderer))[playerIndex];
        ArrayList<?> chunks = (ArrayList<?>) GameAccess.field(state.getClass().getName(), "onScreenChunks", state);
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        float zoom = ((Number) GameAccess.call("zombie.core.Core", "getZoom", core, INT, playerIndex)).floatValue();
        for (Object chunk : chunks) {
            Object levels = GameAccess.call("zombie.iso.IsoChunk", "getRenderLevels", chunk, INT, playerIndex);
            int lo = ((Number) GameAccess.field("zombie.iso.IsoChunk", "minLevel", chunk)).intValue();
            int hi = ((Number) GameAccess.field("zombie.iso.IsoChunk", "maxLevel", chunk)).intValue();
            for (int z = lo; z <= hi; z++) {
                if (z != ((Number) GameAccess.call(LEVELS, "getMinLevel", levels, INT, z)).intValue()
                        || !(Boolean) GameAccess.call(LEVELS, "isOnScreen", levels, INT, z)) continue;
                GameAccess.call(LEVELS, "clearDirty", levels, new Class<?>[] { int.class, float.class }, z, zoom);
            }
            GameAccess.set(LEVELS, "prevMinZ", levels, lo);
            GameAccess.set(LEVELS, "prevMaxZ", levels, hi);
        }
    }

    /** Native textures were not drawn while enhanced; rebuild them before returning to vanilla. */
    public static boolean original() {
        if (nativeNeedsRebuild) {
            boolean oldCache = (Boolean) GameAccess.field(LEVELS, "clearCachedSquares", null);
            GameAccess.set(LEVELS, "clearCachedSquares", null, true);
            try {
                GameAccess.call(CELL, "invalidateAll", GameAccess.field(CELL, "instance", null), INT, 0);
                nativeNeedsRebuild = false;
            } finally { GameAccess.set(LEVELS, "clearCachedSquares", null, oldCache); }
        }
        return false;
    }

    private static void refreshBounds(Object cell, Object player, int index) {
        float width = ((Number) GameAccess.call("zombie.iso.IsoCamera", "getOffscreenWidth", null, INT, index)).floatValue();
        float height = ((Number) GameAccess.call("zombie.iso.IsoCamera", "getOffscreenHeight", null, INT, index)).floatValue();
        int minX = iso("XToIso", 0f, 0f, 0f), minY = iso("YToIso", width, 0f, 0f);
        int maxX = iso("XToIso", width, height, 6f), maxY = iso("YToIso", 0f, height, 6f);
        RotationCamera.Snapshot rotation = RotationCamera.snapshot();
        if (rotation != null) {
            double[] point = new double[2];
            for (int corner = 0; corner < 4; corner++) for (int layer : new int[] {-1, 6}) {
                if (!rotation.ground((corner & 1) * rotation.width(), (corner >> 1) * rotation.height(),
                        rotation.camZ() + layer, point)) continue;
                minX = Math.min(minX, (int) Math.floor(point[0]));
                minY = Math.min(minY, (int) Math.floor(point[1]));
                maxX = Math.max(maxX, (int) Math.ceil(point[0]));
                maxY = Math.max(maxY, (int) Math.ceil(point[1]));
            }
        }
        Object renderer = GameAccess.field(CELL, "instance", null);
        Object state = ((Object[]) GameAccess.field(CELL, "perPlayerData", renderer))[index];
        String owner = state.getClass().getName();
        GameAccess.set(owner, "occludedGridX1", state, minX);
        GameAccess.set(owner, "occludedGridY1", state, minY);
        GameAccess.set(owner, "occludedGridX2", state, maxX);
        GameAccess.set(owner, "occludedGridY2", state, maxY);
        minX -= 2; minY -= 2;
        minX -= minX % 8; minY -= minY % 8;
        maxX += 8 - maxX % 8; maxY += 8 - maxY % 8;
        GameAccess.set("zombie.iso.IsoCell", "minX", cell, minX);
        GameAccess.set("zombie.iso.IsoCell", "minY", cell, minY);
        GameAccess.set("zombie.iso.IsoCell", "maxX", cell, maxX);
        GameAccess.set("zombie.iso.IsoCell", "maxY", cell, maxY);
        if (minX != ((Number) GameAccess.field("zombie.iso.IsoCell", "lastMinX", cell)).intValue()
                || minY != ((Number) GameAccess.field("zombie.iso.IsoCell", "lastMinY", cell)).intValue()) {
            GameAccess.set("zombie.iso.IsoCell", "lightUpdateCount", cell, 10);
        }
        int level = (int) Math.floor(GameAccess.coordinate(player, "getZ"));
        int maxZ = level < 0 ? (int) Math.ceil(GameAccess.coordinate(player, "getZ")) + 1
                : ((Number) GameAccess.field("zombie.iso.IsoCell", "maxHeight", null)).intValue();
        GameAccess.set("zombie.iso.IsoCell", "maxZ", cell, maxZ);
    }

    private static int iso(String axis, float x, float y, float z) {
        return ((Number) GameAccess.call("zombie.iso.IsoUtils", axis, null,
                new Class<?>[] { float.class, float.class, float.class }, x, y, z)).intValue();
    }

    private static void refreshPeeking(Object cell, Object player, int index) {
        long[] rooms = (long[]) GameAccess.field("zombie.iso.IsoCell", "playerWindowPeekingRoomId", cell);
        long before = rooms[index];
        rooms[index] = -1;
        Object building = GameAccess.call("zombie.iso.IsoMovingObject", "getCurrentBuilding", player, new Class<?>[0]);
        if (building == null) {
            Object direction = GameAccess.call("zombie.characters.IsoGameCharacter", "getForwardDirection", player, new Class<?>[0]);
            Object facing = GameAccess.call("zombie.iso.IsoDirections", "fromAngle", null,
                    new Class<?>[] { GameAccess.type("zombie.iso.Vector2") }, direction);
            Object square = GameAccess.call("zombie.iso.IsoMovingObject", "getCurrentSquare", player, new Class<?>[0]);
            building = GameAccess.call("zombie.iso.IsoCell", "GetPeekedInBuilding", cell,
                    new Class<?>[] { GameAccess.type("zombie.iso.IsoGridSquare"), GameAccess.type("zombie.iso.IsoDirections") }, square, facing);
            if (building != null) rooms[index] = ((Number) GameAccess.field("zombie.iso.IsoCell", "playerPeekedRoomId", cell)).longValue();
        }
        if (before != rooms[index]) GameAccess.set("zombie.characters.IsoPlayer", "dirtyRecalcGridStack", player, true);
    }

    public static void renderInteraction(Object cell, int playerIndex) {
        // Preserve the native render tail: speech/notes, entity components and tree indicators.
        // VP's alternative WorldText snapshot is skipped for this camera to avoid duplicate text.
        float recalc = ((Number) GameAccess.staticCall("zombie.iso.IsoGridSquare", "getRecalcLightTime")).floatValue();
        if (recalc < 0f) GameAccess.call("zombie.iso.IsoGridSquare", "setRecalcLightTime", null,
                new Class<?>[] { float.class }, 60f);
        int lightcache = ((Number) GameAccess.staticCall("zombie.iso.IsoGridSquare", "getLightcache")).intValue();
        if (lightcache <= 0) GameAccess.call("zombie.iso.IsoGridSquare", "setLightcache", null, INT, 90);
        Iterable<?> moving = (Iterable<?>) GameAccess.call("zombie.iso.IsoCell", "getObjectList", cell, new Class<?>[0]);
        for (Object object : moving) GameAccess.call("zombie.entity.GameEntity", "renderlast", object, new Class<?>[0]);
        ArrayList<?> updaters = (ArrayList<?>) GameAccess.call("zombie.iso.IsoCell", "getStaticUpdaterObjectList", cell, new Class<?>[0]);
        for (int i = 0; i < updaters.size(); i++) {
            GameAccess.call("zombie.entity.GameEntity", "renderlast", updaters.get(i), new Class<?>[0]);
        }
        GameAccess.call("zombie.iso.objects.IsoTree", "checkChopTreeIndicators", null, INT, playerIndex);
        GameAccess.staticCall("zombie.iso.objects.IsoTree", "renderChopTreeIndicators");
        GameAccess.set("zombie.iso.IsoCell", "lastMinX", cell, GameAccess.field("zombie.iso.IsoCell", "minX", cell));
        GameAccess.set("zombie.iso.IsoCell", "lastMinY", cell, GameAccess.field("zombie.iso.IsoCell", "minY", cell));
        // Cursor validation, clicks and VP preview geometry were captured before publishing
        // this model frame. Repeating DoBuilding here could confirm an action twice.
        Object cursor = GameAccess.call("zombie.iso.IsoCell", "getDrag", cell, INT, playerIndex);
        if (cursor == null || (Boolean) GameAccess.staticCall("zombie.GameTime", "isGamePaused")) {
            NativePlacement.renderOpaque(cell, playerIndex);
        }
    }

    private NativeIsoState() {}
}
