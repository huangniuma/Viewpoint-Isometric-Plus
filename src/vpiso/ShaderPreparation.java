package vpiso;

import java.util.List;
import viewpoint.platform.ShaderPacks;

/** Keep VP's current generic and literal programs; defer speculative graphics changes. */
public final class ShaderPreparation {
    public static List<?> current(String requested, List<?> settings) {
        if (!Main.compatible || !IsometricShaders.supports(requested) || settings.size() < 2) return settings;
        // VP 0.1.7 emits the requested options first, then their literal specialization,
        // followed by presets, individual option changes and other packs. Preserve the
        // exact original objects so cache keys and runtime shader sources do not change.
        for (int i = 0; i < 2; i++) {
            ShaderPacks.Active active = (ShaderPacks.Active) GameAccess.call(
                    "viewpoint.platform.AheadPlan$Setting", "pack", settings.get(i), new Class<?>[0]);
            if (!active.pack.id.equals(requested)) return settings;
        }
        System.out.println("[VPISO] shader preparation: " + requested + "; current configurations 2/"
                + settings.size() + "; other packs and option variants compile when selected; disk cache retained.");
        return List.copyOf(settings.subList(0, 2));
    }

    private ShaderPreparation() { }
}
