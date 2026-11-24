package vpiso;

/** Keep one native build confirmation until the owning cursor's next model frame. */
public final class NativeCursorInput {
    private static Object cursor, sampledComponent;
    private static boolean previousReleased, pending;
    private static final ThreadLocal<Boolean> CONFIRM = new ThreadLocal<>();

    public static void sample(Object component) {
        Object owner = GameAccess.call("zombie.characters.ecs.ECSComponent", "getECSOwnerEntity", component, new Class<?>[0]);
        Object[] players = (Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null);
        if (owner == null || players == null || players.length == 0 || owner != players[0]) return;
        if (!IsometricBridge.rotationAllowed()
                || (Boolean) GameAccess.staticCall("zombie.GameTime", "isGamePaused")
                || viewpoint.platform.SettingsWindow.shown()
                || (Boolean) GameAccess.staticCall("zombie.ui.UIManager", "isModalVisible")
                || (Boolean) GameAccess.field("viewpoint.platform.ImGuiFrame", "wantsMouse", null)
                || ((boolean[]) GameAccess.field("zombie.input.Mouse", "uiCaptured", null))[0]) {
            reset();
            return;
        }
        Object cell = GameAccess.field("zombie.iso.IsoWorld", "currentCell",
                GameAccess.field("zombie.iso.IsoWorld", "instance", null));
        Object drag = cell == null ? null : GameAccess.call("zombie.iso.IsoCell", "getDrag", cell,
                new Class<?>[] { int.class }, 0);
        boolean released = (Boolean) GameAccess.call("zombie.characters.component.CharacterInputComponent",
                "isBuildButtonReleased", component, new Class<?>[0]);
        observe(drag, released);
        sampledComponent = component;
    }

    public static void observe(Object next, boolean released) {
        if (next == null || next != cursor) {
            cursor = next;
            pending = false;
            previousReleased = released;
            return;
        }
        pending |= released && !previousReleased;
        previousReleased = released;
    }

    public static boolean begin(Object next) {
        boolean confirm = next != null && next == cursor && pending;
        if (next == cursor) pending = false;
        CONFIRM.set(confirm);
        return confirm;
    }

    public static boolean released(Object component, boolean original) {
        Boolean confirm = CONFIRM.get();
        return confirm != null && component == sampledComponent ? confirm : original;
    }

    public static void end() { CONFIRM.remove(); }

    public static void reset() {
        cursor = sampledComponent = null;
        previousReleased = pending = false;
    }

    private NativeCursorInput() {}
}
