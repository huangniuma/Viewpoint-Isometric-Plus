package vpiso;

import viewpoint.addons.AddonCard;
import viewpoint.addons.AddonSystem;
import viewpoint.addons.State;

/** The native manager owns activation; existing feature preferences remain independent. */
public final class ManagedAddon {
    public static final String ID = "viewpoint-isometric-plus";
    public static final String VERSION = "0.2.35";
    public static final String DESCRIPTION = "Controls the companion ZombieBuddy renderer. Isometric shadows, color, water, grass and 3D models; settings in Options.";
    private static boolean available;
    private static State state;

    public static boolean refresh() {
        AddonSystem system = AddonSystem.current();
        available = system != null;
        state = null;
        if (system == null) return true; // Keep existing standalone behavior before VP starts its addon system.
        for (AddonCard card : system.manager().entries()) {
            if (!ID.equals(card.pkg().id())) continue;
            state = card.state();
            return state == State.ACTIVE;
        }
        return false;
    }

    public static String status() {
        if (!available) return "Viewpoint addon system is starting. Standalone settings remain available.";
        if (state == State.ACTIVE) return "Viewpoint addon: running.";
        return "Viewpoint addon is not running. Enable Viewpoint Isometric Plus in Delete > Advanced > Addons.";
    }

    private ManagedAddon() {}
}
