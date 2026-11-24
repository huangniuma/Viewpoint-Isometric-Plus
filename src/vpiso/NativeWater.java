package vpiso;

import java.util.ArrayList;
import java.util.List;

/** Queues the original water producers against the current native SpriteRenderer state. */
public final class NativeWater {
    private static final String WATER = "zombie.iso.IsoWater";
    private static final String DATA = "zombie.iso.IsoWater$RenderData";
    private static final String SQUARE = "zombie.iso.IsoGridSquare";
    private static final String CHUNK = "zombie.iso.IsoChunk";
    private static final String LEVELS = "zombie.iso.fboRenderChunk.FBORenderLevels";
    private static final Class<?>[] NONE = new Class<?>[0], INT = { int.class };
    private static final Class<?>[] PLAYER_SQUARE = { int.class, GameAccess.type(SQUARE) };
    private static boolean announced;

    public static boolean available() {
        Object water = GameAccess.staticCall(WATER, "getInstance");
        return (Boolean) GameAccess.call(WATER, "getShaderEnable", water, NONE)
                && GameAccess.field(WATER, "effect", water) != null;
    }

    /** Call after the VP drawer and before native visibility and trees, only on the main thread. */
    public static void queue(Object renderer, int player) {
        queue(renderer, player, null);
    }

    public static void queue(Object renderer, int player, RotationCamera.Snapshot rotation) {
        RotationCamera.Snapshot previousRotation = RotationBounds.begin(rotation);
        try { queueNative(renderer, player, rotation); }
        finally { RotationBounds.end(previousRotation); }
    }

    private static void queueNative(Object renderer, int player, RotationCamera.Snapshot rotation) {
        Object frame = GameAccess.field("zombie.iso.IsoCamera", "frameState", null);
        if (((Number) GameAccess.field("zombie.iso.IsoCamera$FrameState", "camCharacterZ", frame)).floatValue() < 0f) return;
        if (!available()) return;
        Object water = GameAccess.staticCall(WATER, "getInstance");
        Object debug = GameAccess.field("zombie.debug.DebugOptions", "instance", null);
        Object weather = GameAccess.field("zombie.debug.DebugOptions", "weather", debug);
        Object terrain = GameAccess.field("zombie.debug.DebugOptions", "terrain", debug);
        Object tiles = GameAccess.field(terrain.getClass().getName(), "renderTiles", terrain);
        if (!option(weather, "waterPuddles") || !option(tiles, "water")) return;
        boolean bodyEnabled = option(tiles, "waterBody"), shoreEnabled = option(tiles, "waterShore");
        if (!bodyEnabled && !shoreEnabled) return;

        Object sprites = GameAccess.field("zombie.core.SpriteRenderer", "instance", null);
        int index = ((Number) GameAccess.call("zombie.core.SpriteRenderer", "getMainStateIndex", sprites, NONE)).intValue();
        Object state = ((Object[]) GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell", "perPlayerData", renderer))[player];
        List<?> chunks = (List<?>) GameAccess.field(state.getClass().getName(), "onScreenChunks", state);
        int maxLevel = 0;
        for (Object chunk : chunks) maxLevel = Math.max(maxLevel, integer(CHUNK, "maxLevel", chunk));
        ArrayList<Object> body = new ArrayList<>(), shore = new ArrayList<>();
        GameAccess.call(WATER, "clearThreadData", water, NONE);
        int bodyCount = 0, shoreCount = 0;
        for (int z = 0; z <= maxLevel; z++) {
            body.clear(); shore.clear();
            // renderOneLevel normally fills the water caches; that drawing is replaced in model mode.
            for (Object chunk : chunks) {
                if (z < integer(CHUNK, "minLevel", chunk) || z > integer(CHUNK, "maxLevel", chunk)) continue;
                if (((boolean[]) GameAccess.field(CHUNK, "lightingNeverDone", chunk))[player]) continue;
                Object levels = GameAccess.call(CHUNK, "getRenderLevels", chunk, INT, player);
                if (!(Boolean) GameAccess.call(LEVELS, "isOnScreen", levels, INT, z)) continue;
                int layer = ((Number) GameAccess.call(CHUNK, "squaresIndexOfLevel", chunk, INT, z)).intValue();
                Object[] squares = ((Object[][]) GameAccess.field(CHUNK, "squares", chunk))[layer];
                Object cutaway = GameAccess.call(CHUNK, "getCutawayDataForLevel", chunk, INT, z);
                String cutawayType = cutaway.getClass().getName();
                for (Object square : squares) {
                    if (square == null) continue;
                    Object geometry = GameAccess.call(SQUARE, "getWater", square, NONE);
                    if (geometry == null || !(Boolean) GameAccess.call("zombie.iso.IsoWaterGeometry", "isValid", geometry, NONE)) continue;
                    if (integer(SQUARE, "z", square) != z
                            || !(Boolean) GameAccess.call(cutawayType, "shouldRenderSquare", cutaway, PLAYER_SQUARE, player, square)
                            || !(Boolean) GameAccess.call(SQUARE, "IsOnScreen", square, NONE)) continue;
                    boolean isShore = (Boolean) GameAccess.call("zombie.iso.IsoWaterGeometry", "isbShore", geometry, NONE);
                    if (isShore) {
                        if (shoreEnabled) shore.add(square);
                    } else if (bodyEnabled) {
                        Object floor = GameAccess.call(SQUARE, "getFloor", square, NONE);
                        if (floor != null) {
                            Object info = GameAccess.call("zombie.iso.IsoObject", "getRenderInfo", floor, INT, player);
                            Object floorLayer = GameAccess.field(info.getClass().getName(), "layer", info);
                            if ("TranslucentFloor".equals(String.valueOf(floorLayer))) continue;
                        }
                        body.add(square);
                    }
                }
            }
            bodyCount += produce(water, index, player, body, z, false, rotation);
            shoreCount += produce(water, index, player, shore, z, true, rotation);
        }
        if (!announced && bodyCount + shoreCount > 0) {
            announced = true;
            System.out.println("[VPISO] native IsoWater producers active; raw on-screen squares; body="
                    + bodyCount + ", shore=" + shoreCount);
        }
    }

    private static int produce(Object water, int index, int player, ArrayList<Object> squares, int z, boolean shore,
                               RotationCamera.Snapshot rotation) {
        if (squares.isEmpty()) return 0;
        Object data = ((Object[][]) GameAccess.field(WATER, shore ? "renderDataShore" : "renderData", water))[index][player];
        int start = integer(DATA, "numSquares", data);
        GameAccess.call(WATER, shore ? "renderShore" : "render", water,
                new Class<?>[] { ArrayList.class, int.class }, squares, z);
        int end = integer(DATA, "numSquares", data);
        RotationWater.reproject(data, squares, start, end, rotation);
        float[] vertices = (float[]) GameAccess.field(DATA, "data", data);
        // Native water uses square depth + .001 and GL_LESS. Body geometry replaces VP water.
        // Shore overlaps VP's unbiased land: move only its appended vertices in front of that land.
        if (shore) for (int i = start * 32 + 7; i < end * 32; i += 8) vertices[i] -= rotation == null ? .002f : .000002f;
        return end - start;
    }

    private static boolean option(Object owner, String field) {
        Object option = GameAccess.field(owner.getClass().getName(), field, owner);
        return (Boolean) GameAccess.call("zombie.debug.BooleanDebugOption", "getValue", option, NONE);
    }
    private static int integer(String owner, String field, Object value) {
        return ((Number) GameAccess.field(owner, field, value)).intValue();
    }
    private NativeWater() {}
}
