package vpiso;

import viewpoint.core.Frame;

/** Convert game-owned cursor previews into the model frame before it is published. */
public final class NativeCursorPreview {
    private static final ThreadLocal<Boolean> CAPTURING = new ThreadLocal<>();

    public static boolean capturing() { return Boolean.TRUE.equals(CAPTURING.get()); }

    public static void capture(Frame frame, Object cell, int player) {
        Object cursor = GameAccess.call("zombie.iso.IsoCell", "getDrag", cell,
                new Class<?>[] { int.class }, player);
        if (cursor == null || (Boolean) GameAccess.staticCall("zombie.GameTime", "isGamePaused")) {
            NativeCursorInput.reset();
            return;
        }
        float z = (float) Math.floor(frame.camZ);
        float mx = ((Number) GameAccess.staticCall("zombie.input.Mouse", "getXA")).floatValue();
        float my = ((Number) GameAccess.staticCall("zombie.input.Mouse", "getYA")).floatValue();
        float x = world("screenToIsoX", player, mx, my, z);
        float y = world("screenToIsoY", player, mx, my, z);
        Object shown;
        CAPTURING.set(true);
        NativeCursorInput.begin(cursor);
        try {
            try {
                call("ViewpointBuild.place", cursor, (double) Math.floor(x), (double) Math.floor(y),
                        (double) z, (double) x, (double) y);
                NativePlacement.renderOpaque(cell, player);
                GameAccess.call("zombie.iso.IsoCell", "DoBuilding", cell,
                        new Class<?>[] { int.class, boolean.class }, player, true);
            } finally { shown = call("ViewpointBuild.done", cursor); }
        } finally {
            NativeCursorInput.end();
            CAPTURING.remove();
        }
        if (shown != null) {
            String table = "se.krka.kahlua.vm.KahluaTable";
            Class<?>[] key = { Object.class };
            Object sx = GameAccess.call(table, "rawget", shown, key, "x");
            Object sy = GameAccess.call(table, "rawget", shown, key, "y");
            if (!(sx instanceof Number ix) || !(sy instanceof Number iy)) return;
            boolean valid = Boolean.TRUE.equals(GameAccess.call(table, "rawget", shown, key, "valid"));
            Object edge = GameAccess.call(table, "rawget", shown, key, "edge");
            GameAccess.call("viewpoint.building.BuildDriver", "markSquare", null,
                    new Class<?>[] { Frame.class, int.class, int.class, int.class, boolean.class, int.class },
                    frame, ix.intValue(), iy.intValue(), (int) z, valid, edge instanceof Number n ? n.intValue() : 0);
            GameAccess.call("viewpoint.building.BuildGhosts", "draw", null,
                    new Class<?>[] { Frame.class, GameAccess.type(table), int.class, int.class },
                    frame, shown, ix.intValue(), iy.intValue());
        }
    }

    private static float world(String method, int player, float x, float y, float z) {
        return ((Number) GameAccess.call("zombie.Lua.LuaManager$GlobalObject", method, null,
                new Class<?>[] { int.class, float.class, float.class, float.class }, player, x, y, z)).floatValue();
    }

    private static Object call(String name, Object... arguments) {
        return GameAccess.call("viewpoint.building.BuildDriver", "call", null,
                new Class<?>[] { String.class, Object[].class }, name, arguments);
    }

    private NativeCursorPreview() {}
}
