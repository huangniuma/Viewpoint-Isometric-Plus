package vpiso;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.Lua.LuaEventManager", methodName="triggerEvent", strictMatch=true)
public final class Patch_NativeObjectPress {
    @Patch.OnEnter
    public static void enter(String name, Object object, Object x, Object y) { NativeObjectClick.event(name, object, x, y, false); }
    @Patch.OnExit
    public static void exit(String name, Object object, Object x, Object y) { NativeObjectClick.event(name, object, x, y, true); }
}
