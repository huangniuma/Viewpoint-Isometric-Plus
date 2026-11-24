package vpiso;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import viewpoint.core.Frame;

/** Frame-owned native visibility for Project Reflections' independent glass overlays. */
public final class ReflectionVisibility {
    private static final ThreadLocal<ReflectionVisibility> ACTIVE = new ThreadLocal<>();
    private static final Map<Object, Object> SOURCES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ClassValue<Field> KEYS = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            try {
                Field key = type.getDeclaredField("key");
                key.setAccessible(true);
                return key;
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Reflection surface has no source key", error); }
        }
    };
    private final IdentityHashMap<Object, Float> alphas = new IdentityHashMap<>();
    private Frame frame;

    /** Sprite-keyed mirrors still have an individual native source object. */
    public static void remember(Object surface, Object source) {
        if (surface != null && source != null) SOURCES.put(surface, source);
    }

    public void capture(Frame frame, Object cell, int player) {
        this.frame = frame;
        alphas.clear();
        for (Field current : OptionalScans.CURRENT) {
            try {
                Object[] surfaces = (Object[]) current.get(null);
                if (surfaces == null) continue;
                for (Object surface : surfaces) {
                    Object key = KEYS.get(surface.getClass()).get(surface);
                    if (GameAccess.type("zombie.iso.IsoObject").isInstance(key)) remember(surface, key);
                }
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot capture reflection sources", error); }
        }
        synchronized (SOURCES) {
            // Include older surfaces held by Project Reflections' short linger cache.
            // Re-evaluate their sources every frame, independently of its scan interval.
            for (Object source : SOURCES.values()) {
                if (!GameAccess.type("zombie.iso.IsoObject").isInstance(source) || alphas.containsKey(source)) continue;
                Object square = GameAccess.call("zombie.iso.IsoObject", "getSquare", source, new Class<?>[0]);
                alphas.put(source, currentSource(cell, square, source)
                        ? IsometricVisibility.objectAlpha(frame, square, source, player) : 0f);
            }
        }
    }

    private static boolean currentSource(Object cell, Object square, Object source) {
        if (square == null) return false;
        int x = ((Number) GameAccess.field("zombie.iso.IsoGridSquare", "x", square)).intValue();
        int y = ((Number) GameAccess.field("zombie.iso.IsoGridSquare", "y", square)).intValue();
        int z = ((Number) GameAccess.field("zombie.iso.IsoGridSquare", "z", square)).intValue();
        Object loaded = GameAccess.call("zombie.iso.IsoCell", "getGridSquare", cell,
                new Class<?>[] { int.class, int.class, int.class }, x, y, z);
        if (loaded != square) return false;
        // removeFromSquare leaves getSquare non-null. Cached reflections must not
        // keep rendering a removed object or an object from an unloaded map square.
        Object objects = GameAccess.call("zombie.iso.IsoGridSquare", "getObjects", square, new Class<?>[0]);
        for (Object object : (java.util.List<?>) objects) if (object == source) return true;
        return false;
    }

    public ReflectionVisibility begin() {
        ReflectionVisibility previous = ACTIVE.get();
        ACTIVE.set(this);
        return previous;
    }

    public static void restore(ReflectionVisibility previous) {
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }

    public static float alpha(Object context, Object surface) {
        ReflectionVisibility current = ACTIVE.get();
        if (current == null || current.frame == null || context == null || surface == null
                || GameAccess.field("viewpoint.render.FrameContext", "scene", context) != current.frame.scene) return 1f;
        Object source = SOURCES.get(surface);
        if (source == null) {
            try { source = KEYS.get(surface.getClass()).get(surface); }
            catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read reflection source", error); }
        }
        return current.alphas.getOrDefault(source, 1f);
    }

    private static final class OptionalScans {
        private static final Field[] CURRENT = fields();
        private static Field[] fields() {
            java.util.List<Field> result = new java.util.ArrayList<>();
            for (String name : new String[] {"projectreflections.Scan", "prv2.Scan"}) {
                try {
                    Class<?> type = Class.forName(name, false, ReflectionVisibility.class.getClassLoader());
                    Field field = type.getDeclaredField("current");
                    field.setAccessible(true);
                    result.add(field);
                } catch (ClassNotFoundException absent) {
                    // This is an optional integration, not a required addon.
                } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported reflection scan API", error); }
            }
            return result.toArray(Field[]::new);
        }
    }
}
