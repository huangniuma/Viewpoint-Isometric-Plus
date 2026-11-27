import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import viewpoint.core.View;
import viewpoint.platform.LiveSettings;
import vpiso.GameAccess;
import vpiso.IsometricBridge;
import vpiso.IsometricControls;
import vpiso.IsometricText;
import vpiso.RotationCamera;
import vpiso.RotationDrag;

/** Uses VP's actual file IO in a workspace fixture, including fresh JVM reloads. */
public final class SettingsProbe {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(directory);
        Object fs = GameAccess.field("zombie.ZomboidFileSystem", "instance", null);
        GameAccess.set("zombie.ZomboidFileSystem", "cacheDir", fs, directory.toFile());
        Path file = directory.resolve("viewpoint-live.properties");
        if (args[1].equals("write-off")) {
            Properties initial = new Properties();
            initial.setProperty("fixture.unrelated", "preserved");
            try (var output = Files.newOutputStream(file)) { initial.store(output, "VPISO test fixture"); }
        }
        GameAccess.call("viewpoint.platform.LiveSettings", "load", null, new Class<?>[0]);
        IsometricControls.tick(); // Addon declares after VP has already loaded its file.
        LiveSettings.Toggle toggle = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "enabledSetting", null);
        boolean expected = !args[1].equals("read-off");
        if (toggle.get() != expected) throw new AssertionError("VP did not restore the persisted switch in a fresh JVM");
        LiveSettings.Toggle shadows = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "shadows", null);
        LiveSettings.Toggle style = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "style", null);
        LiveSettings.Toggle models = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "models", null);
        LiveSettings.Toggle water = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "water", null);
        LiveSettings.Toggle lightweight = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "lightweightShadows", null);
        if (lightweight.get() != args[1].equals("read-off")) throw new AssertionError("Lightweight default or fresh-JVM restore failed");
        lightweight.set(args[1].equals("write-off"));
        LiveSettings.Toggle preload = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "shadowPreload", null);
        if (preload.get() != !args[1].equals("read-off")) throw new AssertionError("Preload default or fresh-JVM restore failed");
        preload.set(!args[1].equals("write-off"));
        LiveSettings.Toggle grass = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "grass", null);
        if(grass.get()!=args[1].equals("read-off"))throw new AssertionError("Grass default/persistence changed");
        if(!GameAccess.field("viewpoint.platform.LiveSettings$Setting","label",grass).equals(IsometricText.localize("VP grass in isometric view")))throw new AssertionError("Grass label not localized");
        LiveSettings.Toggle snow = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "snow", null);
        LiveSettings.Toggle frameGeneration = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "frameGeneration", null);
        if(frameGeneration.get()!=args[1].equals("read-off"))throw new AssertionError("Frame generation default/persistence changed");
        if(!GameAccess.field("viewpoint.platform.LiveSettings$Setting","label",frameGeneration).equals(IsometricText.localize("Frame generation")))throw new AssertionError("Unlocalized independent frame generation");
        frameGeneration.set(args[1].equals("write-off"));
        LiveSettings.Choice rate = (LiveSettings.Choice) GameAccess.field("vpiso.IsometricControls", "frameGenerationRate", null);
        int expectedRate = args[1].equals("read-off") ? 4 : args[1].equals("read-on") ? 0 : 1;
        if(rate.get()!=expectedRate)throw new AssertionError("Frame generation base rate default/persistence changed");
        if(!GameAccess.field("viewpoint.platform.LiveSettings$Setting","label",rate).equals(IsometricText.localize("Frame generation base rate")))throw new AssertionError("Unlocalized frame generation rate");
        if(args[1].equals("write-off"))rate.set(4);
        else if(args[1].equals("read-off"))rate.set(0);
        if(snow.get()!=args[1].equals("read-off"))throw new AssertionError("Snow default/persistence changed");
        LiveSettings.Toggle rotation = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "rotation", null);
        LiveSettings.Number exposure = (LiveSettings.Number) GameAccess.field("vpiso.IsometricControls", "exposure", null);
        if (models.get() != args[1].equals("read-off")) throw new AssertionError("3D model default or persisted setting changed");
        if (water.get() != args[1].equals("read-on")) throw new AssertionError("Independent water default or persisted setting changed");
        float expectedYaw = args[1].equals("write-off") ? 0f : args[1].equals("read-off") ? 273.25f : 92.5f;
        if (rotation.get() != !args[1].equals("write-off") || Math.abs(RotationDrag.yawDegrees() - expectedYaw) > .001f)
            throw new AssertionError("Mouse rotation default or saved angle was not restored");
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", rotation)
                .equals(IsometricText.localize("Enable camera rotation"))) throw new AssertionError("Unlocalized rotation control");
        if (args[1].equals("write-off")) {
            toggle.set(false); models.set(true); water.set(false); shadows.set(false); style.set(true); exposure.set(1.75f);
            grass.set(true); snow.set(true);
            rotation.set(true); setYaw(273.25f);
        } else if (args[1].equals("read-off")) {
            if (shadows.get() || !style.get() || Math.abs(exposure.get()-1.75f)>.001f) throw new AssertionError("Independent settings were not restored");
            toggle.set(true); models.set(false); water.set(true); shadows.set(true); style.set(false); exposure.set(.65f);
            grass.set(false); snow.set(false);
            rotation.set(true); setYaw(92.5f);
        } else if (!shadows.get() || style.get() || Math.abs(exposure.get()-.65f)>.001f) throw new AssertionError("Shadow-only setting not restored");
        IsometricControls.tick();
        if (vpiso.IsometricControls.effects().lightweightShadows() != lightweight.get()
                || vpiso.IsometricControls.effects().shadows() != shadows.get()
                || vpiso.IsometricControls.effects().style() != style.get()
                || vpiso.IsometricControls.effects().water() != water.get()
                || vpiso.IsometricControls.effects().snow() != snow.get()
                || vpiso.IsometricControls.effects().withWater(!water.get()).snow() != snow.get()
                || vpiso.IsometricControls.effects().grass() != grass.get()) throw new AssertionError("Frame snapshot ignored effect settings");
        if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "enabled", null) != toggle.get()) {
            throw new AssertionError("Saved setting was not applied to rendering");
        }
        if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "modelsEnabled", null) != models.get()) {
            throw new AssertionError("Saved 3D model setting was not applied to rendering");
        }
        if (RotationCamera.requested() != rotation.get() || RotationCamera.quarterTurns() != 0) {
            throw new AssertionError("Rotation setting was not applied or bypassed the master/model prerequisite");
        }
        if (!args[1].equals("read-on")) {
            GameAccess.call("viewpoint.platform.LiveSettings", "save", null, new Class<?>[0]);
            Properties saved = new Properties();
            try (var input = Files.newInputStream(file)) { saved.load(input); }
            if (!saved.getProperty("vpiso.enabled").equals(Boolean.toString(toggle.get()))
                    || !saved.getProperty("vpiso.models").equals(Boolean.toString(models.get()))
                    || !saved.getProperty("vpiso.water").equals(Boolean.toString(water.get()))
                    || !saved.getProperty("vpiso.lightweightShadows").equals(Boolean.toString(lightweight.get()))
                    || !saved.getProperty("vpiso.shadowPreload").equals(Boolean.toString(preload.get()))
                    || !saved.getProperty("vpiso.snow").equals(Boolean.toString(snow.get()))
                    || !saved.getProperty("vpiso.frameGeneration").equals(Boolean.toString(frameGeneration.get()))
                    || !saved.getProperty("vpiso.frameGenerationRate").equals(args[1].equals("write-off")?"60":"game")
                    || !saved.getProperty("vpiso.grass").equals(Boolean.toString(grass.get()))
                    || !saved.getProperty("vpiso.rotation").equals(Boolean.toString(rotation.get()))
                    || Math.abs(Float.parseFloat(saved.getProperty("vpiso.yaw")) - RotationDrag.yawDegrees()) > .001f
                    || !saved.getProperty("fixture.unrelated").equals("preserved")) {
                throw new AssertionError("VP persistence changed an unrelated key or omitted the addon setting");
            }
        }
        // Reset changes only direction, including when rotation is waiting for its prerequisites.
        boolean enabledBeforeReset = toggle.get(), modelsBeforeReset = models.get(), rotationBeforeReset = rotation.get();
        Object resetDirection = GameAccess.field("vpiso.IsometricControls", "resetDirectionButton", null);
        GameAccess.call("viewpoint.platform.SettingsWindow$Button", "press", resetDirection, new Class<?>[0]);
        IsometricControls.tick();
        if (RotationDrag.yawDegrees() != 0 || toggle.get() != enabledBeforeReset || models.get() != modelsBeforeReset
                || rotation.get() != rotationBeforeReset) throw new AssertionError("Restore direction changed an unrelated switch");
        // UI updates also reach the switch while VP handles the world itself.
        vpiso.Main.compatible = true;
        View.enabled = true;
        toggle.set(false);
        IsometricControls.tick();
        if (!IsometricBridge.afterWorld(null, true) || !IsometricBridge.status().endsWith("off")) {
            throw new AssertionError("VP view bypassed settings or lost its world handling");
        }
        View.enabled = false;
        String stable = IsometricBridge.status();
        for (int i=0;i<1000;i++) {
            IsometricBridge.afterWorld(null,false);
            IsometricBridge.rendering(new viewpoint.core.Frame());
            if (!IsometricBridge.status().equals(stable)) throw new AssertionError("Status depends on frame submission timing");
            IsometricBridge.rendering(null);
        }
        if (IsometricBridge.afterWorld(null, false)) throw new AssertionError("Disabled addon captured a world");
        // The render thread consumes the options of its queued drawer, even after a UI edit.
        var frame = new viewpoint.core.Frame();
        var snapshot = IsometricControls.effects();
        IsometricBridge.modelRendering(frame, snapshot);
        shadows.set(!shadows.get());
        water.set(!water.get());
        IsometricControls.tick();
        if (!IsometricBridge.modelRendering() || IsometricBridge.renderEffects() != snapshot
                || IsometricBridge.renderFrame() != frame) throw new AssertionError("Queued 3D effects snapshot was replaced by a UI edit");
        IsometricBridge.rendering(frame);
        if (IsometricBridge.modelRendering() || IsometricBridge.renderEffects() != null || !IsometricBridge.rendering()) {
            throw new AssertionError("Native overlay inherited 3D effect settings");
        }
        IsometricBridge.rendering(null);
        if (IsometricBridge.modelRendering() || IsometricBridge.rendering()) throw new AssertionError("Render scope leaked");
        GameAccess.set("vpiso.IsometricBridge", "failed", null, true);
        toggle.set(true);
        IsometricControls.tick();
        if (!IsometricBridge.status().contains("rendering error") || IsometricBridge.afterWorld(null, false)) {
            throw new AssertionError("Checkbox incorrectly cleared the rendering failure latch");
        }
        System.out.println("Settings persistence PASS: " + args[1]
                + "; actual VP load/save, 3D model switch/default, independent water/shadows/style, middle-mouse rotation/precise-angle persistence and reset, master/model prerequisites, unrelated key preserved, VP-view update, original fallback, queued snapshot isolation and failure latch; fixture only.");
    }
    private static void setYaw(float degrees) {
        RotationDrag.reset();
        RotationDrag.move(Math.toRadians(degrees) / .0022, .1, 1f, 1f);
        GameAccess.call("vpiso.RotationDrag", "store", null, new Class<?>[0]);
    }
}
