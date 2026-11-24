package vpiso;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/** Game ABI accessed at runtime: compile-time game classes are intentionally not bundled. */
public final class GameAccess {
    private static final ConcurrentHashMap<String, Members> OWNERS = new ConcurrentHashMap<>();
    private static final Class<?>[] NONE = new Class<?>[0];

    private static final class Members {
        final Class<?> type;
        final ConcurrentHashMap<String, Field> fields = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, MethodEntry> methods = new ConcurrentHashMap<>();
        Members(String name) {
            try { type = Class.forName(name); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
        }
    }
    private record MethodEntry(Class<?>[] signature, Method method, MethodEntry next) {}

    private static Members members(String name) {
        Members cached = OWNERS.get(name);
        if (cached != null) return cached;
        // Class initialisers may call another accessor on the same owner. Resolve
        // outside ConcurrentHashMap's mapping lock, then publish one immutable owner.
        Members resolved = new Members(name);
        Members raced = OWNERS.putIfAbsent(name, resolved);
        return raced == null ? resolved : raced;
    }

    public static Class<?> type(String name) {
        return members(name).type;
    }

    private static Field resolvedField(String owner, String name) {
        Members members = members(owner);
        Field cached = members.fields.get(name);
        if (cached != null) return cached;
        return members.fields.computeIfAbsent(name, key -> {
            try {
                Field field = members.type.getDeclaredField(key);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(owner + "." + name, e); }
        });
    }

    public static Object field(String owner, String name, Object target) {
        try {
            return resolvedField(owner, name).get(target);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(owner + "." + name, e); }
    }

    public static void set(String owner, String name, Object target, Object value) {
        try { resolvedField(owner, name).set(target, value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(owner + "." + name, e); }
    }

    public static Object call(String owner, String name, Object target, Class<?>[] signature, Object... args) {
        try {
            Members members = members(owner);
            for (MethodEntry entry = members.methods.get(name); entry != null; entry = entry.next) {
                if (Arrays.equals(entry.signature, signature)) return entry.method.invoke(target, args);
            }
            // Only an unresolved overload locks; warmed calls read immutable entries.
            Method resolved = null;
            synchronized (members.methods) {
                MethodEntry head = members.methods.get(name);
                for (MethodEntry entry = head; entry != null; entry = entry.next) {
                    if (Arrays.equals(entry.signature, signature)) { resolved = entry.method; break; }
                }
                if (resolved == null) {
                    try { resolved = members.type.getDeclaredMethod(name, signature); }
                    catch (NoSuchMethodException inherited) { resolved = members.type.getMethod(name, signature); }
                    resolved.setAccessible(true);
                    members.methods.put(name, new MethodEntry(signature.clone(), resolved, head));
                }
            }
            return resolved.invoke(target, args);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(owner + "." + name + Arrays.toString(signature), e); }
    }

    public static Object staticCall(String owner, String name) {
        return call(owner, name, null, NONE);
    }

    public static float coordinate(Object character, String method) {
        return ((Number) call("zombie.iso.IsoMovingObject", method, character, new Class<?>[0])).floatValue();
    }

    private GameAccess() {}
}
