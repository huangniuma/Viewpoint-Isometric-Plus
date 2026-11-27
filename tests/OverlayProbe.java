import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImFont;
import imgui.type.ImInt;
import java.util.Map;
import vpiso.GameAccess;
import vpiso.IsometricControls;
import vpiso.IsometricText;
import viewpoint.platform.LiveSettings;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

/** Real ImGui native draw lists and mouse events, without the game or a settings-file write. */
public final class OverlayProbe {
    private static Object backend;

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(1280, 1080, "VPISO localized UI verification", 0, 0);
        if (window == 0) throw new AssertionError("Hidden OpenGL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            checkLanguage("EN");
            checkLanguage("CN");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void checkLanguage(String language) throws Exception {
        var languageType = Class.forName("zombie.core.Language");
        var constructor = languageType.getDeclaredConstructor(String.class, String.class, String.class, boolean.class);
        constructor.setAccessible(true);
        GameAccess.set("zombie.core.Translator", "language", null,
                constructor.newInstance(language, language, "EN", false));
        var context = ImGui.createContext();
        try {
            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null);
            io.setDisplaySize(1280f, 720f);
            io.setDeltaTime(1f / 60f);
            // The game's modified renderer is Java 25 bytecode; javac 21 uses reflection here.
            backend = GameAccess.type("imgui.gl3.ImGuiImplGl3").getDeclaredConstructor().newInstance();
            GameAccess.call("imgui.gl3.ImGuiImplGl3", "init", backend, new Class<?>[] { String.class }, "#version 130");
            rebuildFonts(io, 1f);
            if (language.equals("CN") && !IsometricText.localize("Dismiss").equals("关闭提示")) {
                throw new AssertionError("Chinese game language did not localize the UI");
            }
            if (language.equals("EN") && !IsometricText.localize("Dismiss").equals("Dismiss")) {
                throw new AssertionError("English game language was not preserved");
            }
            int overlaysBefore = ((java.util.List<?>) GameAccess.field("viewpoint.platform.Overlays", "all", null)).size();
            // Each language uses a clean VP registry, as a fresh process would.
            ((java.util.List<?>) GameAccess.field("viewpoint.platform.LiveSettings", "ALL", null)).clear();
            ((Map<?, ?>) GameAccess.field("viewpoint.platform.SettingsWindow", "panels", null)).clear();
            ((java.util.List<?>) GameAccess.field("viewpoint.platform.SettingsWindow", "buttons", null)).clear();
            GameAccess.set("vpiso.IsometricControls", "prepareButton", null, null);
            GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null,
                    new Class<?>[] { java.io.File.class }, (Object) null);
            viewpoint.platform.ShaderPacks.useBuiltIn("library", "high", Map.of("upscaling", "off"));
            GameAccess.set("zombie.core.PerformanceSettings", "fboRenderChunk", null, true);
            vpiso.Main.compatible = true;
            IsometricControls.tick();
            Object settingsButton = GameAccess.field("vpiso.IsometricControls", "prepareButton", null);
            String settingsLabel = (String) GameAccess.field("viewpoint.platform.SettingsWindow$Button", "label", settingsButton);
            if (!settingsLabel.equals(IsometricText.localize("Enable compatible graphics"))) {
                throw new AssertionError("VP settings action label did not follow the game language");
            }
            checkSettings(io, settingsButton);
            checkOptionsPage(io, language);
            checkStableModelStatus(language);
            checkManagedAddon(io, language);
            int overlaysAfter = ((java.util.List<?>) GameAccess.field("viewpoint.platform.Overlays", "all", null)).size();
            if (overlaysAfter != overlaysBefore) throw new AssertionError("Addon registered an unwanted popup");
            // Settings must remain readable after VP replaces its font atlas.
            rebuildFonts(io, 1.5f);
            optionsFrame(io, -100f, -100f, false);
            System.out.println("Settings UI PASS (" + language
                    + "): native Options page, six category tabs, 20 settings including shadow preload, independent frame generation and base rate, lightweight shadows and grass, native checkbox/profile clicks, CJK glyphs, font resize; no popup or game launch.");
        } finally {
            if (backend != null) GameAccess.call("imgui.gl3.ImGuiImplGl3", "dispose", backend, new Class<?>[0]);
            ImGui.destroyContext(context);
        }
    }

    private static void checkSettings(ImGuiIO io, Object button) {
        String place = (String) GameAccess.field("viewpoint.platform.SettingsWindow$Button", "place", button);
        if (!place.equals(IsometricControls.PLACE)) {
            throw new AssertionError("Settings action is outside the addon page");
        }
        var places = (Map<?, ?>) GameAccess.call("viewpoint.platform.SettingsWindow", "byPlace", null, new Class<?>[0]);
        var tabs = GameAccess.call("viewpoint.platform.SettingsWindow", "byTab", null,
                new Class<?>[] { java.util.Collection.class }, places.keySet());
        if (!((Map<?, ?>) tabs).containsKey(IsometricControls.PAGE))
            throw new AssertionError("Dedicated Options page is missing");
        // Exercise the native controls independently before testing their categorized navigation.
        var all = new java.util.ArrayList<Object>((java.util.List<?>) places.get(place));
        places.forEach((key, value) -> {
            if (!key.equals(place) && key.toString().startsWith(IsometricControls.PAGE + "/"))
                all.addAll((java.util.List<?>) value);
        });
        places = Map.of(place, all);
        if (!"vpiso.enabled".equals(GameAccess.field("viewpoint.platform.SettingsWindow$Button", "beside", button)))
            throw new AssertionError("Compatible profile action is not beside the first control");
        LiveSettings.Toggle toggle = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "enabledSetting", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", toggle)
                .equals(IsometricText.localize("Enable isometric enhancements"))) throw new AssertionError("Unlocalized checkbox");
        toggle.set(true);
        settingsFrame(io, place, places, -100f, -100f, false);
        clickSettings(io, place, places, 40f, 45f);
        if (toggle.get()) throw new AssertionError("Real VP checkbox did not turn the addon off");
        if (!(Boolean) GameAccess.field("viewpoint.platform.SettingsWindow", "dirty", null)) {
            throw new AssertionError("Native checkbox did not request VP persistence");
        }
        IsometricControls.tick();
        if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "enabled", null)) throw new AssertionError("Checkbox was not consumed");
        clickSettings(io, place, places, 40f, 45f);
        IsometricControls.tick();
        if (!toggle.get()) throw new AssertionError("Real VP checkbox did not turn the addon on");
        if (((java.util.List<?>) places.get(place)).size() != 20) throw new AssertionError("Missing independent model/effect/rotation/grass/frame generation/preload controls");
        LiveSettings.Toggle preload = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "shadowPreload", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", preload).equals(IsometricText.localize("Shadow preload optimization")))
            throw new AssertionError("Unlocalized shadow preload checkbox");
        var preloadOnly = Map.of(place, java.util.List.of(preload));
        preload.set(true); settingsFrame(io, place, preloadOnly, -100f, -100f, false);
        clickSettings(io, place, preloadOnly, 40f, 45f);
        if (IsometricControls.shadowPreload()) throw new AssertionError("Shadow preload checkbox did not turn off");
        clickSettings(io, place, preloadOnly, 40f, 45f);
        if (!IsometricControls.shadowPreload()) throw new AssertionError("Shadow preload checkbox did not turn on");
        LiveSettings.Toggle lightweight = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "lightweightShadows", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", lightweight).equals(IsometricText.localize("Lightweight dynamic shadows")))
            throw new AssertionError("Unlocalized lightweight shadows checkbox");
        var lightweightOnly = Map.of(place, java.util.List.of(lightweight));
        lightweight.set(false);
        clickSettings(io, place, lightweightOnly, 40f, 45f); IsometricControls.tick();
        if (!IsometricControls.effects().lightweightShadows()) throw new AssertionError("Lightweight checkbox did not reach queued settings");
        clickSettings(io, place, lightweightOnly, 40f, 45f); IsometricControls.tick();
        if (IsometricControls.effects().lightweightShadows()) throw new AssertionError("Lightweight checkbox did not restore full shadows");
        LiveSettings.Toggle grass = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "grass", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", grass).equals(IsometricText.localize("VP grass in isometric view")))
            throw new AssertionError("Unlocalized grass checkbox");
        LiveSettings.Toggle snow = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "snow", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", snow).equals(IsometricText.localize("Viewpoint snow and tracks")))
            throw new AssertionError("Unlocalized snow checkbox");
        var snowOnly = Map.of(place, java.util.List.of(snow));
        snow.set(false); settingsFrame(io, place, snowOnly, -100f, -100f, false);
        clickSettings(io, place, snowOnly, 40f, 45f); IsometricControls.tick();
        if(!snow.get() || !IsometricControls.effects().snow())throw new AssertionError("Snow checkbox failed");
        clickSettings(io, place, snowOnly, 40f, 45f); IsometricControls.tick();
        if(snow.get() || IsometricControls.effects().snow())throw new AssertionError("Snow checkbox failed to turn off");
        var grassOnly = Map.of(place, java.util.List.of(grass));
        grass.set(false);
        settingsFrame(io, place, grassOnly, -100f, -100f, false);
        clickSettings(io, place, grassOnly, 40f, 45f);
        IsometricControls.tick();
        if (!grass.get() || !IsometricControls.effects().grass()) throw new AssertionError("VP grass checkbox did not enable the queued effect");
        clickSettings(io, place, grassOnly, 40f, 45f);
        IsometricControls.tick();
        if (grass.get() || IsometricControls.effects().grass()) throw new AssertionError("VP grass checkbox did not turn off");
        LiveSettings.Toggle models = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "models", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", models).equals(IsometricText.localize("Enable 3D models"))) {
            throw new AssertionError("Unlocalized 3D model checkbox");
        }
        // Draw and click the actual VP control with a fixed cursor, independently of wrapped descriptions.
        var modelOnly = Map.of(place, java.util.List.of(models));
        models.set(false);
        settingsFrame(io, place, modelOnly, -100f, -100f, false);
        clickSettings(io, place, modelOnly, 40f, 45f);
        IsometricControls.tick();
        if (!models.get() || !(Boolean) GameAccess.field("vpiso.IsometricBridge", "modelsEnabled", null)) {
            throw new AssertionError("Real VP 3D checkbox did not enable the model rendering path");
        }
        clickSettings(io, place, modelOnly, 40f, 45f);
        IsometricControls.tick();
        if (models.get() || (Boolean) GameAccess.field("vpiso.IsometricBridge", "modelsEnabled", null)) {
            throw new AssertionError("Real VP 3D checkbox did not restore the native rendering path");
        }
        LiveSettings.Toggle water = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "water", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", water).equals(IsometricText.localize("VP water effects"))) {
            throw new AssertionError("Unlocalized water checkbox");
        }
        var waterOnly = Map.of(place, java.util.List.of(water));
        water.set(false);
        settingsFrame(io, place, waterOnly, -100f, -100f, false);
        clickSettings(io, place, waterOnly, 40f, 45f);
        IsometricControls.tick();
        if (!IsometricControls.effects().water() || models.get()) throw new AssertionError("Water control depends on models");
        clickSettings(io, place, waterOnly, 40f, 45f);
        IsometricControls.tick();
        if (IsometricControls.effects().water()) throw new AssertionError("Water control did not disable");
        LiveSettings.Toggle rotation = (LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "rotation", null);
        if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", rotation).equals(IsometricText.localize("Enable camera rotation")))
            throw new AssertionError("Unlocalized camera rotation checkbox");
        var rotationOnly = Map.of(place, java.util.List.of(rotation));
        rotation.set(false);
        settingsFrame(io, place, rotationOnly, -100f, -100f, false);
        clickSettings(io, place, rotationOnly, 40f, 45f);
        IsometricControls.tick();
        if (!rotation.get() || models.get() || vpiso.RotationCamera.quarterTurns() != 0)
            throw new AssertionError("Rotation checkbox ignored prerequisites or changed models");
        clickSettings(io, place, rotationOnly, 40f, 45f);
        IsometricControls.tick();
        if (rotation.get()) throw new AssertionError("Rotation checkbox did not disable");
        for (String key : new String[] { "shadows", "style" }) {
            Object setting = GameAccess.field("vpiso.IsometricControls", key, null);
            if (!GameAccess.field("viewpoint.platform.LiveSettings$Setting", "label", setting).equals(IsometricText.localize(
                    key.equals("shadows") ? "Sun, moon and lamp shadows" : "VP color style"))) throw new AssertionError("Unlocalized effect label");
        }
        buttonFrame(io, button, -100f, -100f, false);
        buttonFrame(io, button, 50f, 45f, false);
        buttonFrame(io, button, 50f, 45f, true);
        buttonFrame(io, button, 50f, 45f, false);
        if (!(Boolean) GameAccess.call("viewpoint.platform.SettingsWindow$Button", "take", button, new Class<?>[0])) {
            throw new AssertionError("Real VP compatible-profile button did not queue its action");
        }
    }

    private static void checkOptionsPage(ImGuiIO io, String language) throws Exception {
        ((LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "models", null)).set(false);
        IsometricControls.tick();
        LiveSettings.toggle("fixture.world", "Fixture world", "World/Floors", true);
        viewpoint.platform.AddonsView.install((viewpoint.platform.AddonsView) java.lang.reflect.Proxy.newProxyInstance(
                OverlayProbe.class.getClassLoader(), new Class<?>[] { viewpoint.platform.AddonsView.class },
                (proxy, method, args) -> {
                    if (method.getReturnType() == java.util.List.class) return java.util.List.of();
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                }));
        var places = (Map<?, ?>) GameAccess.staticCall("viewpoint.platform.SettingsWindow", "byPlace");
        var tabs = GameAccess.call("viewpoint.platform.SettingsWindow", "byTab", null,
                new Class<?>[] { java.util.Collection.class }, places.keySet());
        var more = (java.util.List<?>) GameAccess.call("viewpoint.platform.SettingsPages", "more", null,
                new Class<?>[] { Map.class }, tabs);
        Object ours = more.stream().filter(vpiso.IsometricSettingsPage::ours).findFirst().orElseThrow();
        if (!GameAccess.call(ours.getClass().getName(), "name", ours, new Class<?>[0]).equals(IsometricText.localize(IsometricControls.PAGE)))
            throw new AssertionError("Native discovered page title is not localized");
        var options = (java.util.List<?>) GameAccess.field("viewpoint.platform.SettingsPages", "OPTION_PAGES", null);
        if (vpiso.IsometricSettingsPage.section("Options", options).stream().filter(vpiso.IsometricSettingsPage::ours).count() != 1
                || vpiso.IsometricSettingsPage.section("Advanced", more).stream().anyMatch(vpiso.IsometricSettingsPage::ours))
            throw new AssertionError("Options entry is missing, duplicated or still in Advanced");
        GameAccess.set("viewpoint.platform.SettingsPages", "shown", null, "World");
        GameAccess.set("vpiso.IsometricSettingsPage", "selectedTab", null, 0);
        for (int i = 0; i < 3; i++) optionsFrame(io, -100f, -100f, false);
        optionsFrame(io, 130f, 620f, false);
        optionsFrame(io, 130f, 620f, true);
        optionsFrame(io, 130f, 620f, false);
        if (!GameAccess.field("viewpoint.platform.SettingsPages", "shown", null).equals(IsometricText.localize(IsometricControls.PAGE)))
            throw new AssertionError("Actual Options sidebar click did not open the page");
        optionsFrame(io, -100f, -100f, false);
        screenshot("research/build078-options-" + language + "-0.png");
        float x = 268f;
        String[] labels = { "General", "Models and camera", "Shadows", "Color", "Environment", "About" };
        for (int index = 0; index < labels.length; index++) {
            float textWidth = ImGui.calcTextSize(IsometricText.localize(labels[index])).x;
            float clickX = x + textWidth / 2f;
            optionsFrame(io, clickX, 258f, false);
            optionsFrame(io, clickX, 258f, true);
            optionsFrame(io, clickX, 258f, false);
            if ((Integer) GameAccess.field("vpiso.IsometricSettingsPage", "selectedTab", null) != index)
                throw new AssertionError("Native category click failed: " + language + " / " + labels[index] + " x=" + clickX);
            optionsFrame(io, -100f, -100f, false);
            screenshot("research/build078-options-" + language + "-" + index + ".png");
            x += textWidth + 18f;
        }
        GameAccess.set("vpiso.IsometricSettingsPage", "selectedTab", null, 0);
        for (boolean down : new boolean[] { false, true, false }) optionsFrame(io, 854f, 205f, down);
        if (!(Boolean) GameAccess.field("viewpoint.platform.SettingsPages", "minimized", null))
            throw new AssertionError("Native minimize button did not work");
        for (boolean down : new boolean[] { false, true, false }) optionsFrame(io, 854f, 205f, down);
        if ((Boolean) GameAccess.field("viewpoint.platform.SettingsPages", "minimized", null))
            throw new AssertionError("Native restore button did not work");
        GameAccess.set("viewpoint.platform.SettingsWindow", "shown", null, true);
        for (boolean down : new boolean[] { false, true, false }) optionsFrame(io, 884f, 205f, down);
        if ((Boolean) GameAccess.field("viewpoint.platform.SettingsWindow", "shown", null))
            throw new AssertionError("Native close button did not work");
        viewpoint.platform.AddonsView.install(null);
        System.out.println("Options navigation078 PASS (" + language + "): actual sidebar and six tab clicks, localized title, minimize/restore/close, Options group and a separate managed addon shortcut.");
    }

    private static void checkManagedAddon(ImGuiIO io, String language) throws Exception {
        var system = viewpoint.addons.AddonProbe.installFixture(java.nio.file.Path.of("."));
        String id = vpiso.ManagedAddon.ID;
        var view = viewpoint.platform.AddonsView.current();
        ((LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "enabledSetting", null)).set(true);
        system.frame(); IsometricControls.tick();
        var card = view.cards().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        if (!card.name().equals(IsometricText.localize(IsometricControls.PAGE))
                || !card.description().equals(IsometricText.localize(vpiso.ManagedAddon.DESCRIPTION)))
            throw new AssertionError("Managed native card is not localized: " + card);
        String addons = (String) GameAccess.field("viewpoint.platform.SettingsPages", "ADDONS", null);
        GameAccess.set("viewpoint.platform.SettingsPages", "shown", null, addons);
        GameAccess.set("viewpoint.platform.AddonsPage", "opened", null, null);
        GameAccess.set("viewpoint.platform.AddonsPage", "settingsOf", null, null);
        for (int i = 0; i < 3; i++) optionsFrame(io, -100f, -100f, false);
        screenshot("research/build078-addon-" + language + "-inactive.png");
        view.switchOn(id);
        for (int i = 0; i < 5; i++) { system.frame(); IsometricControls.tick(); }
        var consent = view.consent(id);
        if (consent != null) {
            view.accept(id, consent.shown());
            for (int i = 0; i < 5; i++) { system.frame(); IsometricControls.tick(); }
        }
        if (!view.cards().stream().anyMatch(c -> c.id().equals(id) && c.active()))
            throw new AssertionError("Managed addon did not enable");
        for (int i = 0; i < 3; i++) optionsFrame(io, -100f, -100f, false);
        screenshot("research/build078-addon-" + language + "-active.png");
        GameAccess.set("viewpoint.platform.AddonsPage", "opened", null, id);
        for (int i = 0; i < 3; i++) optionsFrame(io, -100f, -100f, false);
        screenshot("research/build078-addon-" + language + "-info.png");
        GameAccess.set("viewpoint.platform.AddonsPage", "opened", null, null);
        GameAccess.set("viewpoint.platform.AddonsPage", "settingsOf", null, id);
        for (int i = 0; i < 3; i++) optionsFrame(io, -100f, -100f, false);
        screenshot("research/build078-addon-" + language + "-settings.png");
        Object shortcut = GameAccess.field("vpiso.IsometricControls", "openSettingsButton", null);
        if (!GameAccess.field("viewpoint.platform.SettingsWindow$Button", "place", shortcut).equals("Addons/" + id))
            throw new AssertionError("Managed settings shortcut not registered");
        buttonFrame(io, shortcut, -100f, -100f, false);
        buttonFrame(io, shortcut, 50f, 45f, false);
        buttonFrame(io, shortcut, 50f, 45f, true);
        buttonFrame(io, shortcut, 50f, 45f, false);
        IsometricControls.tick();
        if (!GameAccess.field("viewpoint.platform.SettingsPages", "shown", null).equals(IsometricText.localize(IsometricControls.PAGE)))
            throw new AssertionError("Managed settings button did not open Options page");
        GameAccess.set("viewpoint.platform.AddonsPage", "settingsOf", null, null);
        view.switchOff(id); system.frame();
        GameAccess.set("viewpoint.addons.AddonSystem", "current", null, null);
        viewpoint.platform.AddonsView.install(null);
        System.out.println("Managed addon UI078 PASS (" + language + "): native inactive/active cards, localized name/description, information, settings drawer and Options shortcut.");
    }

    private static void optionsFrame(ImGuiIO io, float x, float y, boolean down) {
        io.setMousePos(x, y); io.setMouseDown(0, down);
        ImGui.newFrame();
        GameAccess.staticCall("viewpoint.platform.SettingsPages", "draw");
        ImGui.render();
        GL11.glViewport(0, 0, 1280, 1080); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        var data = ImGui.getDrawData();
        GameAccess.call("imgui.gl3.ImGuiImplGl3", "renderDrawData", backend, new Class<?>[] { data.getClass() }, data);
        ImGui.freeDrawData(data);
    }

    private static void screenshot(String file) throws Exception {
        java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(1280 * 1080 * 4);
        GL11.glReadPixels(0, 0, 1280, 1080, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        var picture = new java.awt.image.BufferedImage(1280, 1080, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 1080; y++) for (int x = 0; x < 1280; x++) {
            int offset = ((1079 - y) * 1280 + x) * 4;
            picture.setRGB(x, y, ((pixels.get(offset) & 255) << 16) | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255));
        }
        javax.imageio.ImageIO.write(picture, "png", new java.io.File(file));
    }

    private static void clickSettings(ImGuiIO io, String place, Map<?, ?> places, float x, float y) {
        settingsFrame(io, place, places, x, y, false);
        settingsFrame(io, place, places, x, y, true);
        settingsFrame(io, place, places, x, y, false);
    }

    private static void checkStableModelStatus(String language) {
        vpiso.IsometricBridge.setEnabled(true);
        ((LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "models", null)).set(true);
        ((LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "shadows", null)).set(true);
        IsometricControls.tick();
        vpiso.RotationCamera.configure(true, 0);
        GameAccess.set("vpiso.IsometricBridge", "publishedStatus", null, "VP 3D models are active in isometric view.");
        GameAccess.set("vpiso.IsometricBridge", "modelSceneReady", null, true);
        String status = "VP 3D models: hold middle mouse and move to rotate.";
        for (int i = 0; i < 240; i++) {
            boolean submitted = (i & 1) == 0;
            GameAccess.set("vpiso.IsometricBridge", "submitted", null, submitted);
            if (!vpiso.IsometricBridge.status().equals(status)
                    || !vpiso.IsometricBridge.shadowStatus().equals("Dynamic shadows: active."))
                throw new AssertionError("UI status flickers with an in-progress frame: "
                        + vpiso.IsometricBridge.status() + " / " + vpiso.IsometricBridge.shadowStatus());
            if (vpiso.IsometricBridge.active() != submitted)
                throw new AssertionError("Persistent UI state changed native interaction scope");
        }
        if (language.equals("CN") && IsometricText.localize(status).equals(status))
            throw new AssertionError("Persistent model status is not localized");
        vpiso.IsometricBridge.setModelsEnabled(false);
        if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "modelSceneReady", null))
            throw new AssertionError("Turning models off retained UI readiness");
        vpiso.IsometricBridge.setModelsEnabled(true);
        if (!vpiso.IsometricBridge.shadowStatus().equals("Dynamic shadows: waiting for scene."))
            throw new AssertionError("Turning models on falsely claims a ready scene");
        GameAccess.set("vpiso.IsometricBridge", "modelSceneReady", null, true);
        vpiso.IsometricBridge.afterWorld(null, true);
        if ((Boolean) GameAccess.field("vpiso.IsometricBridge", "modelSceneReady", null))
            throw new AssertionError("VP camera passthrough retained model readiness");
        vpiso.RotationCamera.configure(false, 0);
        ((LiveSettings.Toggle) GameAccess.field("vpiso.IsometricControls", "models", null)).set(false);
        vpiso.IsometricBridge.setModelsEnabled(false);
        System.out.println("Stable settings status PASS (" + language + "): 240 frame transitions, localized rotation/shadow status, unchanged interaction scope, model reset and VP passthrough.");
    }

    private static void settingsFrame(ImGuiIO io, String place, Map<?, ?> places, float x, float y, boolean down) {
        io.setMousePos(x, y);
        io.setMouseDown(0, down);
        ImGui.newFrame();
        ImGui.setNextWindowPos(20f, 20f);
        ImGui.setNextWindowSize(650f, 500f);
        ImGui.begin("Advanced settings verification", 32 | 64 | 256);
        ImGui.setCursorScreenPos(40f, 40f);
        GameAccess.call("viewpoint.platform.SettingsWindow", "body", null,
                new Class<?>[] { String.class, java.util.List.class, float.class }, place, places.get(place), 580f);
        ImGui.end();
        ImGui.render();
        var data = ImGui.getDrawData();
        if (data.getTotalVtxCount() == 0) throw new AssertionError("Settings produced no geometry");
        GameAccess.call("imgui.gl3.ImGuiImplGl3", "renderDrawData", backend, new Class<?>[] { data.getClass() }, data);
        ImGui.freeDrawData(data);
    }

    @SuppressWarnings("unchecked")
    private static void rebuildFonts(ImGuiIO io, float scale) {
        io.setDisplaySize(1280f * scale, 1080f * scale);
        GameAccess.set("viewpoint.platform.Theme", "scale", null, scale);
        GameAccess.call("viewpoint.platform.Theme", "fonts", null, new Class<?>[0]);
        IsometricText.afterThemeFonts(); // The live patch invokes this immediately after Theme.fonts.
        io.getFonts().getTexDataAsRGBA32(new ImInt(), new ImInt());
        GameAccess.call("imgui.gl3.ImGuiImplGl3", "updateFontsTexture", backend, new Class<?>[0]);
        if (io.getFonts().getTexID() == 0) throw new AssertionError("Font atlas was not uploaded to OpenGL");
        ImFont font = (ImFont) GameAccess.field("vpiso.IsometricText", "chineseFont", null);
        if (font == null) throw new AssertionError("No CJK font loaded on this Windows test host");
        var translations = (Map<String, String>) GameAccess.field("vpiso.IsometricText", "CHINESE", null);
        var allText = new java.util.ArrayList<>(translations.values());
        allText.add((String) GameAccess.field("vpiso.IsometricText", "EXTRA_GLYPHS", null));
        for (String text : allText) {
            for (int codepoint : text.codePoints().filter(c -> c >= 0x80).toArray()) {
                if (font.findGlyphNoFallback(codepoint).ptr == 0) {
                    throw new AssertionError("Missing Chinese glyph U+" + Integer.toHexString(codepoint));
                }
            }
        }
    }

    private static void buttonFrame(ImGuiIO io, Object button, float x, float y, boolean down) {
        io.setMousePos(x, y); io.setMouseDown(0, down);
        ImGui.newFrame();
        ImGui.setNextWindowPos(20f, 20f); ImGui.setNextWindowSize(650f, 500f);
        ImGui.begin("Profile verification", 32 | 64 | 256);
        ImGui.setCursorScreenPos(40f, 40f);
        GameAccess.call("viewpoint.platform.SettingsWindow", "press", null,
                new Class<?>[] { button.getClass() }, button);
        ImGui.end(); ImGui.render();
        ImGui.freeDrawData(ImGui.getDrawData());
    }
}

