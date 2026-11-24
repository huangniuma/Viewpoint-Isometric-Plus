package vpiso;

import java.util.List;

/** Client-only identity checks for streamed objects and reconnects; no network messages. */
public final class ClientSession {
    private static Object cell, player;
    private static final Class<?>[] NONE = new Class<?>[0], XYZ = { int.class, int.class, int.class };
    private static final String[] LISTS = { "getObjects", "getWorldObjects", "getMovingObjects", "getStaticMovingObjects" };

    public static boolean observe(Object nextCell, Object nextPlayer) {
        if (cell == nextCell && player == nextPlayer) return false;
        cell = nextCell; player = nextPlayer;
        return true;
    }

    public static boolean currentObject(Object object) {
        if (object == null) return false;
        Object square = GameAccess.call("zombie.iso.IsoObject", "getSquare", object, NONE);
        if (square == null) return false;
        Object world = GameAccess.field("zombie.iso.IsoWorld", "instance", null);
        Object current = world == null ? null : GameAccess.field("zombie.iso.IsoWorld", "currentCell", world);
        if (current == null) return false;
        Object loaded = GameAccess.call("zombie.iso.IsoCell", "getGridSquare", current,
                XYZ,
                GameAccess.field("zombie.iso.IsoGridSquare", "x", square),
                GameAccess.field("zombie.iso.IsoGridSquare", "y", square),
                GameAccess.field("zombie.iso.IsoGridSquare", "z", square));
        if (loaded != square) return false;
        for (String list : LISTS) {
            List<?> objects = (List<?>) GameAccess.call("zombie.iso.IsoGridSquare", list, square, NONE);
            if (objects != null) for (Object source : objects) if (source == object) return true;
        }
        return false;
    }

    private ClientSession() {}
}
