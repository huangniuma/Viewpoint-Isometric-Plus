package viewpoint.addons;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import viewpoint.addons.graph.ClassSource;
import viewpoint.addons.lua.LuaBindings;
import viewpoint.addons.scan.AllowList;
import viewpoint.addons.scan.ScanResult;
import viewpoint.platform.AddonsView;
import viewpoint.platform.LiveSettings;
import vpiso.GameAccess;
import vpiso.IsometricControls;
import vpiso.ManagedAddon;

/** Exercises VP's shipped discovery, scanner, consent, loader, cards and renderer gate. */
public final class AddonProbe {
    private static final String ID = ManagedAddon.ID;

    public static void main(String[] args) throws Exception {
        AddonSystem system = installFixture(Path.of(args[0]));
        AddonsView view = AddonsView.current();
        frame(system);
        require(!card(view).active(), "new addon should start inactive");
        IsometricControls.tick();
        require(!hostEnabled(), "inactive native entry did not disable renderer");
        var master = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "enabledSetting", null);
        var models = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "models", null);
        models.set(true);
        require(master.get(), "inactive lifecycle changed saved master switch");
        AddonsView.Info info = view.info(ID);
        require(info != null && !info.overview().isEmpty(), "native information missing");
        require(card(view).version().equals(ManagedAddon.VERSION) && card(view).author().equals("Hesin, GPT6"), "card metadata");
        require(card(view).tab() == AddonsView.Tab.ADDONS && !card(view).legacy(), "not a native addon card");
        view.switchOn(ID);
        frame(system);
        State state = system.manager().entries().get(0).state();
        if (state == State.AWAITING_CONSENT) {
            AddonsView.Consent consent = view.consent(ID);
            require(consent != null, "native consent information missing");
            view.accept(ID, consent.shown());
            frame(system);
        }
        require(card(view).active(), "native activation failed: " + system.manager().entries());
        require(hostEnabled() && master.get() && models.get(), "activation lost existing preferences");
        Object loader = GameAccess.field("viewpoint.addons.AddonSystem", "loader", system);
        Map<?, ?> running = (Map<?, ?>) GameAccess.field("viewpoint.addons.AddonLoader", "running", loader);
        require(running.containsKey(ID), "native loader did not instantiate SDK entry");
        master.set(false);
        IsometricControls.tick();
        require(!hostEnabled() && card(view).active(), "feature master should remain independent");
        master.set(true);
        IsometricControls.tick();
        view.switchOff(ID);
        frame(system);
        require(!card(view).active() && !hostEnabled() && running.isEmpty(), "native disable failed");
        require(master.get() && models.get(), "native disable erased feature preferences");
        view.switchOn(ID);
        frame(system);
        require(card(view).active() && hostEnabled() && running.containsKey(ID), "native re-enable failed");
        view.switchOff(ID);
        frame(system);
        GameAccess.set("viewpoint.addons.AddonSystem", "current", null, null);
        AddonsView.install(null);
        require(ManagedAddon.refresh(), "startup fallback changed");
        System.out.println("Native addon078 PASS: discovery, descriptor, original allowlist scanner, consent, real loader, card/info, inactive/active/disable/re-enable, companion renderer gate and preferences preserved; fixture settings only.");
    }

    public static AddonSystem installFixture(Path project) throws Exception {
        Path root = project.resolve("build/addon078-fixture/42");
        Path packageDir = root.resolve("media/viewpoint/addons/" + ID);
        Files.createDirectories(packageDir);
        Path product = project.resolve("Contents/mods/VPIsometricLighting/42");
        Files.copy(product.resolve("media/viewpoint/addons/" + ID + "/addon.properties"),
                packageDir.resolve("addon.properties"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(project.resolve("build/ViewpointIsometricPlusAddon.jar"),
                packageDir.resolve("ViewpointIsometricPlusAddon.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(product.resolve("mod.info"), root.resolve("mod.info"), StandardCopyOption.REPLACE_EXISTING);
        Path host = root.resolve("media/java/client/VPIsometricLighting.jar");
        Files.createDirectories(host.getParent());
        Files.copy(project.resolve("build/VPIsometricLighting.jar"), host, StandardCopyOption.REPLACE_EXISTING);
        ModRoot mod = new ModRoot("VPIsometricLighting", "Viewpoint Isometric Plus", ManagedAddon.VERSION, "42.22", "42.22",
                List.of("Viewpoint", "ZombieBuddy"), root, null);
        Found found = Discovery.survey(List.of(mod));
        require(found.packages().size() == 1, "native discovery: " + found.packages());
        Installed pkg = found.accepted(ID);
        require(pkg != null && pkg.accepted(), "native descriptor refused: " + found.packages());
        require(pkg.descriptor().unknown().isEmpty(), "unknown descriptor keys");
        require(pkg.kind() == Kind.ADDON, "wrong tab kind");
        ClassLoader parent = AddonProbe.class.getClassLoader();
        AllowList list = AllowListFile.read(parent);
        require(list != null, "VP's pinned game/JDK allowlist unavailable: " + AllowListFile.header());
        ScanResult scan = new viewpoint.addons.scan.Scanner(list, ClassSource.of(parent))
                .scan(Files.readAllBytes(packageDir.resolve("ViewpointIsometricPlusAddon.jar")));
        require(scan.accepted(), "native scanner refused: " + scan.refusals());
        require(scan.classes().size() == 1, "renderer accidentally loaded in managed classloader");

        Map<String, String> values = new HashMap<>();
        Choices.Store store = new Choices.Store() {
            public String get(String key) { return values.get(key); }
            public void put(String key, String value) { values.put(key, value); }
        };
        // Fixture signature policy only; real native scanning and consent remain in place.
        ZbTrust trust = new ZbTrust(ZbTrust.Policy.read("prompt", true),
                (jar, signature, hash) -> new ZbTrust.Result(ZbTrust.Signature.UNSIGNED, "", "fixture unsigned", hash));
        Release release = new Release(Version.parse("42.22"), Version.parse("0.1.7"), Version.parse("1.0.0"));
        AddonSystem system = new AddonSystem(store, release, trust, Runnable::run, () -> List.of(mod),
                Set::of, () -> found, parent, list, LuaBindings.none());
        GameAccess.set("viewpoint.addons.AddonSystem", "current", null, system);
        AddonsViewPublisher view = new AddonsViewPublisher(system.manager(), system.gate(), system);
        AddonsView.install(view);
        GameAccess.set("viewpoint.addons.AddonSystem", "view", system, view);
        return system;
    }

    private static void frame(AddonSystem system) {
        for (int i = 0; i < 5; i++) { system.frame(); IsometricControls.tick(); }
    }
    private static AddonsView.Card card(AddonsView view) {
        return view.cards().stream().filter(c -> c.id().equals(ID)).findFirst().orElseThrow();
    }
    private static boolean hostEnabled() {
        return (Boolean) GameAccess.field("vpiso.IsometricBridge", "enabled", null);
    }
    private static void require(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
}
