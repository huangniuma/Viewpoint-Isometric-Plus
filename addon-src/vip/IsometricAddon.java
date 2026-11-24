package vip;

import viewpoint.api.Addon;
import viewpoint.api.AddonContext;

/** Native lifecycle entry. The companion ZombieBuddy renderer reads its manager state. */
public final class IsometricAddon implements Addon {
    @Override
    public void enable(AddonContext context) {
        // The existing world renderer is loaded once by ZombieBuddy, rather than again here.
    }
}
