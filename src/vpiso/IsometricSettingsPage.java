package vpiso;

import imgui.ImGui;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A native Options entry; managed addon/resource cards retain their own lifecycle. */
public final class IsometricSettingsPage {
    private static final String PAGES = "viewpoint.platform.SettingsPages";
    private static final String WIDGETS = "viewpoint.platform.Widgets";
    private static Object page;
    private static String pageName;
    private static int selectedTab;
    private static final String[] TABS = { "General", "Models and camera", "Shadows", "Color", "Environment", "About" };
    private static final String[] PLACES = { IsometricControls.PLACE, IsometricControls.CAMERA,
            IsometricControls.SHADOWS, IsometricControls.COLOR, IsometricControls.ENVIRONMENT };

    public static void open() {
        GameAccess.set(PAGES, "shown", null, IsometricText.localize(IsometricControls.PAGE));
        GameAccess.set(PAGES, "minimized", null, false);
    }

    public static boolean ours(Object candidate) {
        return candidate != null && ((List<?>) GameAccess.call(PAGES + "$Page", "tabs", candidate,
                new Class<?>[0])).contains(IsometricControls.PAGE);
    }

    public static void localizePage(List<Object> pages) {
        for (int i = 0; i < pages.size(); i++) {
            if (!ours(pages.get(i))) continue;
            String name = IsometricText.localize(IsometricControls.PAGE);
            if (page == null || !name.equals(pageName)) {
                try {
                    Class<?> icon = GameAccess.type("viewpoint.platform.Icons$Icon");
                    var constructor = GameAccess.type(PAGES + "$Page").getDeclaredConstructor(String.class,
                            String.class, icon, List.class);
                    constructor.setAccessible(true);
                    page = constructor.newInstance(name, "Viewpoint", GameAccess.field(icon.getName(), "MONITOR", null),
                            List.of(IsometricControls.PAGE));
                    pageName = name;
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Viewpoint isometric settings page", error);
                }
            }
            pages.set(i, page);
        }
    }

    /** Move only our entry into Options; native discovery/find still owns navigation. */
    public static List<?> section(String name, List<?> pages) {
        if (page == null) return pages;
        if (name.equals("Options")) {
            var options = new ArrayList<Object>(pages);
            if (options.stream().noneMatch(IsometricSettingsPage::ours)) options.add(page);
            return options;
        }
        if (name.equals("Advanced")) return pages.stream().filter(entry -> !ours(entry)).toList();
        return pages;
    }

    public static void draw(Map<?, ?> settings, float width) {
        boolean pushed = IsometricText.pushFont();
        try {
            String[] labels = java.util.Arrays.stream(TABS).map(IsometricText::localize).toArray(String[]::new);
            selectedTab = (Integer) GameAccess.call(WIDGETS, "pages", null,
                    new Class<?>[] { String.class, String[].class, int.class }, "vpiso-settings-tabs", labels, selectedTab);
            ImGui.spacing();
            String place = selectedTab < PLACES.length ? PLACES[selectedTab] : IsometricControls.PAGE + "/About";
            GameAccess.call(WIDGETS, "beginCard", null, new Class<?>[] { String.class, String.class, float.class },
                    place, "", width);
            try {
                if (selectedTab == 0) {
                    ImGui.textUnformatted(IsometricText.localize(IsometricControls.PAGE));
                    ImGui.spacing();
                    ImGui.textWrapped(IsometricText.localize(ManagedAddon.status()));
                    ImGui.textWrapped(IsometricText.localize(IsometricBridge.status()));
                    ImGui.textWrapped(IsometricText.localize(IsometricBridge.shadowStatus()));
                    ImGui.textWrapped(IsometricText.localize("Select a supported pack in VP Graphics. If shadows are unavailable, check the reason above or use Enable compatible graphics below."));
                    ImGui.separator();
                }
                if (selectedTab == 2)
                    ImGui.textWrapped(IsometricText.localize(IsometricBridge.shadowStatus()));
                if (selectedTab < PLACES.length) {
                    float inner = (Float) GameAccess.staticCall(WIDGETS, "inner");
                    List<?> controls = settings.get(place) instanceof List<?> values ? values : List.of();
                    GameAccess.call("viewpoint.platform.SettingsWindow", "body", null,
                            new Class<?>[] { String.class, List.class, float.class }, place, controls, inner);
                } else {
                    ImGui.textUnformatted(IsometricText.localize(IsometricControls.PAGE));
                    ImGui.textUnformatted(ManagedAddon.VERSION);
                    ImGui.spacing();
                    ImGui.textUnformatted(IsometricText.localize("Authors: Hesin, GPT6"));
                    ImGui.textWrapped(IsometricText.localize("Original isometric view with independent effects and optional Viewpoint 3D models."));
                    ImGui.textWrapped(IsometricText.localize("Requires Project Zomboid 42.22.2, Viewpoint 0.1.7 and ZombieBuddy 2.3.0."));
                    ImGui.textWrapped(IsometricText.localize("Changes are saved automatically. The master switch turns all isometric enhancements on or off."));
                }
            } finally {
                GameAccess.staticCall(WIDGETS, "endCard");
            }
        } finally {
            if (pushed) ImGui.popFont();
        }
    }

    /** Match the native title bar, including its minimize/close and unapplied-graphics handling. */
    public static void header(float x, float y, float width, boolean minimized) {
        boolean pushed = IsometricText.pushFont();
        try {
            float scale = (Float) GameAccess.staticCall("viewpoint.platform.Theme", "scale");
            float height = 50f * scale, pad = 18f * scale, button = 16f * scale, gap = .75f * pad;
            var draw = ImGui.getWindowDrawList();
            String prefix = minimized ? "Project Viewpoint  /  " : IsometricText.localize("Options") + "  /  ";
            float textY = y + (height - ImGui.getTextLineHeight()) / 2f;
            draw.addText(x + pad, textY, color("TEXT_DIM"), prefix);
            draw.addText(x + pad + ImGui.calcTextSize(prefix).x, textY, color("TEXT"), pageName);
            if (!minimized) draw.addRectFilled(x, y + height - 1f, x + width, y + height, color("BORDER"));
            float buttonsX = x + width - pad - 2f * button - gap;
            if (ImGui.isWindowHovered() && ImGui.getMousePosX() <= buttonsX
                    && ImGui.getMousePosY() < y + height && ImGui.isMouseDoubleClicked(0))
                GameAccess.set(PAGES, "minimized", null, !minimized);
            ImGui.setCursorScreenPos(buttonsX, y + (height - button) / 2f);
            if ((Boolean) GameAccess.call("viewpoint.platform.Icons", "windowButton", null,
                    new Class<?>[] { String.class, boolean.class, float.class, String.class },
                    "##minimize", minimized, button, IsometricText.localize(minimized ? "Restore" : "Minimize")))
                GameAccess.set(PAGES, "minimized", null, !minimized);
            ImGui.setCursorScreenPos(x + width - pad - button, y + (height - button) / 2f);
            Class<?> icon = GameAccess.type("viewpoint.platform.Icons$Icon");
            Object bind = GameAccess.field("viewpoint.platform.Keys", "SETTINGS", null);
            String key = (String) GameAccess.call("viewpoint.platform.KeyBind", "windowText", bind, new Class<?>[0]);
            if ((Boolean) GameAccess.call("viewpoint.platform.Icons", "button", null,
                    new Class<?>[] { String.class, icon, float.class, String.class }, "##close",
                    GameAccess.field(icon.getName(), "CLOSE", null), button, IsometricText.localize("Close") + " (" + key + ")"))
                GameAccess.call("viewpoint.platform.GraphicsPage", "leave", null,
                        new Class<?>[] { Runnable.class }, (Runnable) () -> GameAccess.staticCall("viewpoint.platform.SettingsWindow", "close"));
        } finally {
            if (pushed) ImGui.popFont();
        }
    }

    private static int color(String name) {
        return (Integer) GameAccess.field("viewpoint.platform.Theme", name, null);
    }

    private IsometricSettingsPage() {}
}
