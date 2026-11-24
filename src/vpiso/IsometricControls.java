package vpiso;

import imgui.ImGui;
import java.util.ArrayList;
import java.util.List;
import viewpoint.platform.LiveSettings;
import viewpoint.platform.ShaderPack;
import viewpoint.platform.SettingsWindow;

/** All addon controls live in VP's settings; nothing is registered with Overlays. */
public final class IsometricControls {
    public static final String PAGE = "Viewpoint Isometric Plus";
    public static final String PLACE = PAGE + "/General";
    public static final String CAMERA = PAGE + "/Models and camera";
    public static final String SHADOWS = PAGE + "/Shadows";
    public static final String COLOR = PAGE + "/Color";
    public static final String ENVIRONMENT = PAGE + "/Environment";
    private static SettingsWindow.Button prepareButton, resetDirectionButton, openSettingsButton;
    private static LiveSettings.Toggle enabledSetting, models, rotation, water, grass, snow, shadows, lightweightShadows, style, frameGeneration;
    private static LiveSettings.Number shadowStrength, softness, distance, styleStrength, exposure, saturation, contrast;
    private static LiveSettings.Choice quality, frameGenerationRate;
    private static volatile IsometricEffects effects = IsometricEffects.DEFAULT;

    @SuppressWarnings("unchecked")
    public static void exposeLibrary() {
        List<ShaderPack> current = (List<ShaderPack>) GameAccess.field("viewpoint.platform.Graphics", "modern", null);
        if (current == null || current.stream().anyMatch(pack -> pack.id.equals("library"))) return;
        List<ShaderPack> expanded = new ArrayList<>(current);
        expanded.add((ShaderPack) GameAccess.call("viewpoint.platform.ShaderPacks", "find", null,
                new Class<?>[] { String.class }, "library"));
        GameAccess.set("viewpoint.platform.Graphics", "modern", null, List.copyOf(expanded));
    }

    public static IsometricEffects effects() { return effects; }
    public static boolean frameGeneration() { return frameGeneration != null && frameGeneration.get(); }
    public static int frameGenerationRate() {
        if(frameGenerationRate==null)return 30;
        return switch(frameGenerationRate.get()){case 1->30;case 2->40;case 3->50;case 4->60;default->0;};
    }

    public static void tick() {
        if (prepareButton == null) {
            String place = PLACE;
            enabledSetting = toggle("enabled", "Enable isometric enhancements", place, true);
            frameGeneration = toggle("frameGeneration", "Frame generation", place, false);
            frameGeneration.describe(IsometricText.localize("Generates intermediate display frames in both original and 3D model modes. Requires Viewpoint's frame generation library and compatible hardware. Does not change simulation speed or Viewpoint's other-view setting. Saved automatically."));
            frameGenerationRate = LiveSettings.choice("vpiso.frameGenerationRate", IsometricText.localize("Frame generation base rate"), place,
                    new String[]{"game","30","40","50","60"},1);
            frameGenerationRate.shownAs(new String[]{IsometricText.localize("Follow game limit"),"30","40","50","60"});
            frameGenerationRate.describe(IsometricText.localize("Limits real frames only while isometric frame generation is running. 30 targets about 60 displayed frames with interpolation. Follow game limit keeps your existing cap; lower limits are respected. This does not increase simulation speed or rendering resolution. Saved automatically."));
            models = toggle("models", "Enable 3D models", CAMERA, false);
            models.describe(IsometricText.localize("On: VP world models in isometric view. Off: original world appearance with optional shadows and color style. Saved automatically."));
            rotation = toggle("rotation", "Enable camera rotation", CAMERA, false);
            rotation.describe(IsometricText.localize("Requires 3D models. Hold the middle mouse button and move the mouse to rotate, like OldPoint. Release to keep the angle and restore the cursor. Direction is saved automatically."));
            RotationDrag.initialize();
            water = toggle("water", "VP water effects", ENVIRONMENT, false);
            water.describe(IsometricText.localize("Off: original water. On: VP waves and water shading. Works independently of 3D models. Saved automatically."));
            grass = toggle("grass", "VP grass in isometric view", ENVIRONMENT, false);
            grass.describe(IsometricText.localize("Shows VP's grass in either model mode, including wind and trampling. Uses VP World / Grass quality and style; Grass must not be Off. Saved automatically."));
            snow = toggle("snow", "Viewpoint snow and tracks", ENVIRONMENT, false);
            snow.describe(IsometricText.localize("Adds Viewpoint's accumulated ground snow and footprints to the original world appearance. Uses Viewpoint World / Snow quality; Snow must not be Off. 3D mode keeps its existing snow settings. Saved automatically."));
            shadows = toggle("shadows", "Sun, moon and lamp shadows", SHADOWS, true);
            shadows.describe(IsometricText.localize("Controls VP sun, moon, lamp, flashlight and headlight shadows in both model modes, including lightweight mode. Light quality follows the selected VP shader pack."));
            lightweightShadows = toggle("lightweightShadows", "Lightweight dynamic shadows", SHADOWS, false);
            lightweightShadows.describe(IsometricText.localize("Only characters, zombies, animals, vehicles, trees, buildings and placed inventory items cast dynamic shadows. Other surfaces still receive shadows. Works in both model modes; requires dynamic shadows. Saved automatically."));
            shadowStrength = number("shadowStrength", "Shadow strength", SHADOWS, 0f, 1f, .05f, .55f);
            softness = number("softness", "Shadow softness", SHADOWS, 0f, 3f, .25f, 1f);
            distance = number("distance", "Shadow distance (tiles)", SHADOWS, 16f, 128f, 8f, 64f);
            quality = LiveSettings.choice("vpiso.quality", IsometricText.localize("Shadow quality"), SHADOWS,
                    new String[] { "low", "medium", "high" }, 1);
            quality.shownAs(new String[] { IsometricText.localize("Low"), IsometricText.localize("Medium"), IsometricText.localize("High") });
            style = toggle("style", "VP color style", COLOR, true);
            style.describe(IsometricText.localize("Enhances contrast and colors without changing the original models. With 3D models on, adjusts VP lighting colors."));
            styleStrength = number("styleStrength", "Style strength", COLOR, 0f, 1f, .05f, 1f);
            exposure = number("exposure", "Exposure", COLOR, .25f, 3f, .05f, 1f);
            saturation = number("saturation", "Saturation", COLOR, 0f, 2f, .05f, 1f);
            contrast = number("contrast", "Contrast", COLOR, .5f, 2f, .05f, 1f);
            prepareButton = SettingsWindow.button(place, IsometricText.localize("Enable compatible graphics"),
                    IsometricText.localize("Keeps Default, Vivid, Somber, Library or True Weathers and the selected upscaler; otherwise selects Library. Disables SSR. Keeps both frame generation settings. Applies to Viewpoint graphics too."));
            prepareButton.beside("vpiso.enabled");
            resetDirectionButton = SettingsWindow.button(CAMERA, IsometricText.localize("Restore original direction"),
                    IsometricText.localize("Resets the saved camera direction. Keeps the rotation, model and effect switches as selected."));
            SettingsWindow.panel(place, IsometricControls::drawSettings);
            SettingsWindow.panel(CAMERA, () -> ImGui.textWrapped(IsometricText.localize("Camera rotation requires 3D models. Y Walk To, building, dismantling and placement keep the selected camera angle and use the mouse position in that view.")));
            SettingsWindow.panel(SHADOWS, () -> ImGui.textWrapped(IsometricText.localize("Native mode preserves indoor lamps and existing window light. Dynamic shadows replace separate native shadow blobs and known embedded sprite shadows while available.")));
            openSettingsButton = SettingsWindow.button("Addons/" + ManagedAddon.ID,
                    IsometricText.localize("Open isometric settings"),
                    IsometricText.localize("Opens Options > Viewpoint Isometric Plus."));
        }
        boolean running = ManagedAddon.refresh();
        if (openSettingsButton.take()) IsometricSettingsPage.open();
        if (resetDirectionButton.take()) {
            RotationDrag.reset();
        }
        IsometricBridge.setEnabled(enabledSetting.get() && running);
        IsometricBridge.setModelsEnabled(models.get());
        RotationCamera.configureYaw(rotation.get(), RotationDrag.yawDegrees());
        effects = new IsometricEffects(shadows.get(), shadowStrength.get(), softness.get(), distance.get(),
                1024 << quality.get(), style.get(), styleStrength.get(), exposure.get(), saturation.get(), contrast.get(), water.get(), grass.get(), lightweightShadows.get(), snow.get());
        if (prepareButton.take()) {
            try {
                String selected = ((ShaderPack) GameAccess.call("viewpoint.platform.Graphics", "inUse", null,
                        new Class<?>[0])).id;
                if (!IsometricShaders.supports(selected)) selected = "library";
                put("graphics.mode", "Modern");
                put("graphics.pack", selected);
                put("pack." + selected + ".reflections", "off");
                enabledSetting.set(true);
                IsometricBridge.setEnabled(running);
                SettingsWindow.edited();
                prepareButton.status(IsometricText.localize("Graphics profile applied."));
            } catch (Throwable error) {
                prepareButton.status(IsometricText.localize("Cannot apply graphics settings. See the [VPISO] error in console.txt."));
                error.printStackTrace(System.out);
            }
        }
    }

    private static LiveSettings.Toggle toggle(String key, String label, String place, boolean initial) {
        return LiveSettings.toggle("vpiso." + key, IsometricText.localize(label), place, initial);
    }

    private static LiveSettings.Number number(String key, String label, String place, float min, float max, float step, float initial) {
        return LiveSettings.number("vpiso." + key, IsometricText.localize(label), place, min, max, step, initial);
    }

    private static void put(String key, String value) {
        GameAccess.call("viewpoint.platform.LiveSettings", "put", null,
                new Class<?>[] { String.class, String.class }, key, value);
    }

    private static void drawSettings() {
        boolean pushed = IsometricText.pushFont();
        try {
            ImGui.separator();
            ImGui.textWrapped(IsometricText.localize("3D models off keeps the original world. Grass, accumulated snow and tracks, water effects, shadows and color style have independent switches. Changes are saved automatically."));
            ImGui.textWrapped(IsometricText.localize("Shadows and 3D models support Default, Vivid, Somber, Library and True Weathers. FSR 3, XeSS and DLSS use Viewpoint's resolution settings and hardware requirements. Frame generation has its own switch here. SSR must be off. Original world appearance stays at native resolution."));
        } finally {
            if (pushed) ImGui.popFont();
        }
    }

    private IsometricControls() {}
}
