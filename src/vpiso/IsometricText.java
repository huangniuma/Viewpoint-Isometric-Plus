package vpiso;

import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Localized addon text and a small CJK atlas built before VP starts its ImGui frame. */
public final class IsometricText {
    private static final Map<String, String> CHINESE = Map.ofEntries(
        Map.entry("Viewpoint Isometric Plus", "Viewpoint 等距增强"),
        Map.entry("Open isometric settings", "打开等距增强设置"),
        Map.entry("Opens Options > Viewpoint Isometric Plus.", "打开选项中的 Viewpoint 等距增强页面。"),
        Map.entry("Viewpoint addon system is starting. Standalone settings remain available.", "Viewpoint 附加模组系统正在启动，独立设置仍可使用。"),
        Map.entry("Viewpoint addon: running.", "附加模组：运行中。"),
        Map.entry("Viewpoint addon is not running. Enable Viewpoint Isometric Plus in Delete > Advanced > Addons.",
                "附加模组未运行。请在 Delete→高级→附加模组中启用 Viewpoint 等距增强。"),
        Map.entry("Controls the companion ZombieBuddy renderer. Isometric shadows, color, water, grass and 3D models; settings in Options.",
                "等距视角的阴影、色彩、水面、草地和可选 3D 模型。控制配套的 ZombieBuddy 渲染组件。设置：Delete→选项→Viewpoint 等距增强。"),
        Map.entry("Options", "选项"),
        Map.entry("General", "常规"),
        Map.entry("Models and camera", "模型与视角"),
        Map.entry("Shadows", "阴影"),
        Map.entry("Color", "色彩"),
        Map.entry("Environment", "环境"),
        Map.entry("About", "关于"),
        Map.entry("Restore", "还原"),
        Map.entry("Minimize", "最小化"),
        Map.entry("Close", "关闭"),
        Map.entry("Authors: Hesin, GPT6", "作者：Hesin、GPT6"),
        Map.entry("Original isometric view with independent effects and optional Viewpoint 3D models.",
                "在原版等距视角下提供可独立开关的效果，并可选用 Viewpoint 3D 模型。"),
        Map.entry("Requires Project Zomboid 42.22.2, Viewpoint 0.1.7 and ZombieBuddy 2.3.0.",
                "需要《僵尸毁灭工程》42.22.2、Viewpoint 0.1.7 和 ZombieBuddy 2.3.0。"),
        Map.entry("Changes are saved automatically. The master switch turns all isometric enhancements on or off.",
                "设置自动保存；总开关可启用或关闭全部等距增强功能。"),
        Map.entry("Dynamic shadows: off.", "动态阴影：已关闭。"),
        Map.entry("Dynamic shadows: unsupported Viewpoint version.", "动态阴影：不支持当前 Viewpoint 版本。"),
        Map.entry("Dynamic shadows: active.", "动态阴影：正在运行。"),
        Map.entry("Dynamic shadows: waiting for scene.", "动态阴影：等待场景。"),
        Map.entry("Dynamic shadows: waiting for visible floor data.", "动态阴影：等待可见地面数据。"),
        Map.entry("Dynamic shadows: waiting for shadow maps.", "动态阴影：等待阴影贴图。"),
        Map.entry("Select a supported pack in VP Graphics. If shadows are unavailable, check the reason above or use Enable compatible graphics below.",
                "在 VP 图形设置中选择支持的着色器包。阴影不可用时，请查看上方原因，或点击下方的“启用兼容图形配置”。"),
        Map.entry("Enable isometric enhancements", "启用等距增强"),
        Map.entry("Enable 3D models", "启用 3D 模型"),
        Map.entry("Enable camera rotation", "启用视角旋转"),
        Map.entry("Requires 3D models. Hold the middle mouse button and move the mouse to rotate, like OldPoint. Release to keep the angle and restore the cursor. Direction is saved automatically.",
                "需要开启 3D 模型。和 OldPoint 一样，按住鼠标中键并移动鼠标旋转；松开保留角度，鼠标返回按下前的位置。方向自动保存。"),
        Map.entry("VP 3D models: hold middle mouse and move to rotate.", "状态：VP 3D 模型，按住鼠标中键并移动鼠标旋转。"),
        Map.entry("Camera direction", "视角方向"),
        Map.entry("Original direction", "原版方向"),
        Map.entry("Restore original direction", "恢复原版方向"),
        Map.entry("Requires isometric lighting and 3D models. Choose a direction below. During Y Walk To, building, dismantling and placement, the camera temporarily returns to the original direction, then restores your selected direction. Saved automatically.",
                "需要同时启用等距增强和 3D 模型，再在下方选择方向。使用 Y 移动至、建造、拆除和放置游标时，镜头临时回到原版方向，结束后恢复所选方向。设置自动保存。"),
        Map.entry("Rotation uses 90-degree steps. Turning off rotation, 3D models or isometric lighting restores the original direction; your selected direction is retained.",
                "每次旋转 90 度。关闭旋转、3D 模型或等距增强时恢复原版方向，并保留所选方向。"),
        Map.entry("Resets the saved camera direction. Keeps the rotation, model and effect switches as selected.",
                "将保存的视角方向重置为原版方向，保留旋转、模型和各项效果开关的选择。"),
        Map.entry("Camera rotation requires 3D models. Y Walk To, building, dismantling and placement keep the selected camera angle and use the mouse position in that view.",
                "视角旋转需要开启 3D 模型。Y 移动至、建造、拆除和放置时保留当前视角，并按该视角下的鼠标位置选点。"),
        Map.entry("VP water effects", "VP 水面效果"),
        Map.entry("VP grass in isometric view", "原版视角显示 VP 草地"),
        Map.entry("Viewpoint snow and tracks", "Viewpoint 积雪与足迹"),
        Map.entry("Adds Viewpoint's accumulated ground snow and footprints to the original world appearance. Uses Viewpoint World / Snow quality; Snow must not be Off. 3D mode keeps its existing snow settings. Saved automatically.",
                "在原版外观中显示 Viewpoint 的地面积雪堆积和踩踏足迹。质量使用 Viewpoint“世界 / 积雪”设置，积雪不能设为关闭。3D 模式沿用现有积雪设置。设置自动保存。"),
        Map.entry("Shows VP's grass in either model mode, including wind and trampling. Uses VP World / Grass quality and style; Grass must not be Off. Saved automatically.",
                "在两种模型模式下显示 VP 草地，沿用风摆和踩踏效果。质量与外观使用 VP“世界 / 草地”设置，草地不能设为关闭。设置自动保存。"),
        Map.entry("3D models off keeps the original world. Grass, accumulated snow and tracks, water effects, shadows and color style have independent switches. Changes are saved automatically.",
                "关闭 3D 模型保留原版世界；草地、积雪与足迹、水面效果、阴影和色彩画风可分别开关，设置自动保存。"),
        Map.entry("Off: original water. On: VP waves and water shading. Works independently of 3D models. Saved automatically.",
                "关闭：使用原版水面；开启：使用 VP 波浪和水面光照。无需开启 3D 模型，设置自动保存。"),
        Map.entry("3D models off keeps the original world. Water effects, shadows and color style have independent switches. Changes are saved automatically.",
                "关闭 3D 模型保留原版世界；水面效果、阴影和色彩画风可分别开关，设置自动保存。"),
        Map.entry("VP 3D models are active in isometric view.", "状态：正在等距视角下使用 VP 3D 模型。"),
        Map.entry("Camera rotation is paused for the native action cursor.", "状态：正在使用原版操作光标，视角暂时恢复原方向；结束操作后自动恢复。"),
        Map.entry("VP 3D models: camera direction 90°.", "状态：VP 3D 模型，视角方向 90°。"),
        Map.entry("VP 3D models: camera direction 180°.", "状态：VP 3D 模型，视角方向 180°。"),
        Map.entry("VP 3D models: camera direction 270°.", "状态：VP 3D 模型，视角方向 270°。"),
        Map.entry("On: VP world models in isometric view. Off: original world appearance with optional shadows and color style. Saved automatically.",
                "开启：在等距视角下使用 VP 世界模型；关闭：保留原版外观，可单独叠加阴影和画风。设置自动保存。"),
        Map.entry("Controls VP sun, moon, lamp, flashlight and headlight shadows in both model modes, including lightweight mode. Light quality follows the selected VP shader pack.",
                "在两种模型模式下控制 VP 太阳、月亮、灯具、手电筒和车灯阴影，轻量化模式同样支持。灯光质量沿用所选 VP 光影包。"),
        Map.entry("Native mode preserves indoor lamps and existing window light. Dynamic shadows replace separate native shadow blobs and known embedded sprite shadows while available.",
                "原版外观模式保留室内灯光和原有窗边采光。动态阴影可用时，替换原版独立固定阴影及已识别的贴图内嵌阴影。"),
        Map.entry("Adjusts highlight curve and colors in the selected model mode. VP 3D lighting still runs when 3D models are enabled.",
                "调节当前模型模式的高光曲线和色彩；启用 3D 模型时仍使用 VP 三维灯光。"),
        Map.entry("3D models off keeps the original world. 3D models on uses VP world rendering in isometric view. Shadows and color style have independent switches. Changes are saved automatically.",
                "关闭 3D 模型保留原版世界；开启后在等距视角下使用 VP 世界渲染。阴影和画风可分别开关，设置自动保存。"),
        Map.entry("Shadows and 3D models support Default, Vivid, Somber, Library and True Weathers. FSR 3, XeSS and DLSS use Viewpoint's resolution settings and hardware requirements. Frame generation has its own switch here. SSR must be off. Original world appearance stays at native resolution.",
                "阴影和 3D 模型支持 Default、Vivid、Somber、Library 和 True Weathers。FSR 3、XeSS、DLSS 沿用 Viewpoint 的分辨率设置与硬件要求。帧生成使用本页独立开关；需要关闭屏幕空间反射（SSR）。原版外观保持原生分辨率。"),
        Map.entry("Frame generation","帧生成"),
        Map.entry("Frame generation base rate","帧生成基础帧率"),
        Map.entry("Follow game limit","跟随游戏上限"),
        Map.entry("Limits real frames only while isometric frame generation is running. 30 targets about 60 displayed frames with interpolation. Follow game limit keeps your existing cap; lower limits are respected. This does not increase simulation speed or rendering resolution. Saved automatically.",
                "仅在等距帧生成实际运行时限制真实帧率。选择 30 时，配合插帧目标约为 60 显示帧；“跟随游戏上限”保留原设置，更低的游戏上限仍会保留。不提高游戏速度或渲染分辨率。自动保存。"),
        Map.entry("Generates intermediate display frames in both original and 3D model modes. Requires Viewpoint's frame generation library and compatible hardware. Does not change simulation speed or Viewpoint's other-view setting. Saved automatically.",
                "为原版外观和 3D 模型生成中间显示帧。需要 Viewpoint 的帧生成库与兼容硬件；不改变游戏模拟速度，也不改变 Viewpoint 其他视角的帧生成设置。自动保存。"),
        Map.entry("Sun, moon and lamp shadows", "太阳/月亮/灯具阴影"),
        Map.entry("Lightweight dynamic shadows", "轻量化动态阴影"),
        Map.entry("Shadow preload optimization", "阴影预载优化"),
        Map.entry("Prioritizes Viewpoint's existing preload around the isometric screen edges and spreads distant map arrivals across frames. Nearby chunks and overdue multiplayer chunks keep priority. Works in both model modes; requires dynamic shadows. Saved automatically.",
                "优先为等距画面边缘准备 Viewpoint 的预载数据，并将远处地图的接入分散到多帧。邻近区域和联机中等候过久的区域仍优先加载。支持两种模型模式，需开启动态阴影。设置自动保存。"),
        Map.entry("Only characters, zombies, animals, vehicles, trees, buildings and placed inventory items cast dynamic shadows. Other surfaces still receive shadows. Works in both model modes; requires dynamic shadows. Saved automatically.",
                "仅角色、僵尸、动物、汽车、树木、房屋，以及通过“放置”摆出的背包物品投射动态阴影。其他材质仍能接收阴影。支持两种模型模式，需开启动态阴影。设置自动保存。"),
        Map.entry("Shadow strength", "阴影强度"),
        Map.entry("Shadow softness", "阴影柔和度"),
        Map.entry("Shadow distance (tiles)", "阴影距离（格）"),
        Map.entry("Shadow quality", "阴影质量"),
        Map.entry("Low", "低"), Map.entry("Medium", "中"), Map.entry("High", "高"),
        Map.entry("VP color style", "VP 色彩画风"),
        Map.entry("Enhances contrast and colors without changing the original models. With 3D models on, adjusts VP lighting colors.",
                "增强原版画面的明暗对比和色彩，不替换模型；开启 3D 模型时调节 VP 灯光画面色彩。"),
        Map.entry("Style strength", "画风强度"),
        Map.entry("Exposure", "曝光"), Map.entry("Saturation", "饱和度"), Map.entry("Contrast", "对比度"),
        Map.entry("Graphics profile applied.", "已应用图形配置。"),
        Map.entry("Native world effects are active.", "状态：正在原版画面上叠加效果。"),
        Map.entry("Shadows unavailable: the native depth buffer is missing. Color style can still run.",
                "状态：未找到原版深度缓冲，暂不可叠加阴影；色彩画风仍可使用。"),
        Map.entry("All effects are off. Original rendering.", "状态：水面效果、阴影和画风均已关闭，使用原版画面。"),
        Map.entry("Keeps the original world appearance and visibility rules. Changes are saved automatically. Both effects off preserves the original picture.",
                "保留原版外观、遮挡和视野规则。设置自动保存；阴影和画风都关闭时使用原版画面。"),
        Map.entry("Shadow rendering needs Library. Color style alone works without VP world rendering.",
                "阴影需要 Library；单独使用色彩画风无需 VP 世界渲染。"),
        Map.entry("Uses VP shadow maps over the original picture. Original lamps and flashlight remain in use.",
                "在原版画面上叠加 VP 阴影贴图；灯光和手电继续使用原版效果。"),
        Map.entry("Applies VP's highlight curve and color adjustments to the original picture; does not replace world models or lighting.",
                "在原版画面上应用 VP 高光曲线和色彩调节，保留原版模型和灯光。"),
        Map.entry("Takes effect immediately in the original isometric view. Saved with Viewpoint settings.",
                "开关在原版等距视角下立即生效，并随 VP 设置自动保存；关闭后恢复原版渲染。"),
        Map.entry("Enable compatible graphics", "启用兼容图形配置"),
        Map.entry("Dismiss", "关闭提示"),
        Map.entry("Viewpoint Isometric Plus is ready.", "状态：等待原版场景。"),
        Map.entry("Keeps Default, Vivid, Somber, Library or True Weathers and the selected upscaler; otherwise selects Library. Disables SSR. Keeps both frame generation settings. Applies to Viewpoint graphics too.",
                "保留当前 Default、Vivid、Somber、Library 或 True Weathers 及所选升频；其他包切换到 Library。关闭屏幕空间反射（SSR），保留两种视角的帧生成设置；也会改变 Viewpoint 其他视角的图形配置。"),
        Map.entry("Switch VP to Library with frame generation and upscaling off to enable isometric lighting.",
                "使用 Library 并关闭补帧、升频以启用等距增强。这也会改变 VP 其他视角的图形配置。"),
        Map.entry("Applying compatible graphics: Library, frame generation off, upscaling off.",
                "正在应用兼容配置：Library，补帧关闭，升频关闭。"),
        Map.entry("Cannot apply graphics settings. See the [VPISO] error in console.txt.",
                "无法应用图形配置，请查看 console.txt 中的 [VPISO] 错误。"),
        Map.entry("Viewpoint Isometric Plus: enhanced isometric lighting enabled. Configure it in Delete > Options > Viewpoint Isometric Plus.",
                "等距增强已启用。按 Delete，在选项→Viewpoint 等距增强中设置。"),
        Map.entry("Viewpoint Isometric Plus: enhanced isometric rendering on", "增强等距增强：开启"),
        Map.entry("Viewpoint Isometric Plus: enhanced isometric rendering off", "状态：已关闭，使用原版画面。"),
        Map.entry("Viewpoint Isometric Plus stopped after a rendering error. Restart the game after checking console.txt.",
                "等距增强因渲染错误已停用。请检查 console.txt，然后重启游戏。"),
        Map.entry("Viewpoint Isometric Plus is armed. Return to isometric view with VP's O key.",
                "等距增强已待命，按 VP 的 O 键返回原版等距视角。"),
        Map.entry("Viewpoint Isometric Plus is off. Return to isometric view with VP's O key.",
                "等距增强已关闭，按 VP 的 O 键返回原版等距视角。")
    );
    private static final String EXTRA_GLYPHS = "等距增强正在等待：正在使用 Minecraft/Iris 着色器包为（需要 Library）；升频已开启；屏幕空间反射（SSR）已开启；补帧已开启；阴影需要启用原版区块渲染器；等距增强在捕获场景绘制场景时失败，已停用。请查看 console.txt 中的 [VPISO] 错误。";
    private static final String fontFile = findFont();
    private static ImFont chineseFont;
    private static long fontContext;

    public static boolean chinese() {
        try {
            // Main runs during ZB startup; avoid getLanguage(), which can initialize Core.
            Object language = GameAccess.field("zombie.core.Translator", "language", null);
            if (language == null) return Locale.getDefault().getLanguage().equals("zh");
            String name = (String) GameAccess.call("zombie.core.Language", "name", language, new Class<?>[0]);
            return name.equalsIgnoreCase("CN") || name.equalsIgnoreCase("CH") || name.toLowerCase(Locale.ROOT).startsWith("zh");
        } catch (Throwable unavailable) {
            return Locale.getDefault().getLanguage().equals("zh");
        }
    }

    public static String localize(String text) {
        if (!chinese() || fontFile == null) return text;
        String translated = CHINESE.get(text);
        if (translated != null) return translated;
        if (text.startsWith("Dynamic shadows unavailable: ")) {
            return "动态阴影不可用：" + localize("Viewpoint Isometric Plus is waiting: " + text.substring("Dynamic shadows unavailable: ".length()))
                    .substring("等距增强正在等待：".length());
        }
        if (text.startsWith("Viewpoint Isometric Plus is waiting: ")) {
            return "等距增强正在等待：" + text.substring("Viewpoint Isometric Plus is waiting: ".length())
                    .replace("Minecraft/Iris pack is active", "正在使用 Minecraft/Iris 着色器包")
                    .replace("shader pack is ", "着色器包为 ")
                    .replace("(use Default, Vivid, Somber, Library or True Weathers)", "（请选择 Default、Vivid、Somber、Library 或 True Weathers）")
                    .replace("unsupported upscaling mode", "不支持的升频模式")
                    .replace("SSR is on", "屏幕空间反射（SSR）已开启")
                    .replace("frame generation is on", "补帧已开启")
                    .replace("native chunk renderer is required", "阴影需要启用原版区块渲染器")
                    .replace("; ", "；");
        }
        if (text.startsWith("Viewpoint Isometric Plus stopped while ")) {
            String stage = text.substring("Viewpoint Isometric Plus stopped while ".length()).split("\\.", 2)[0];
            stage = stage.replace("capturing", "捕获场景").replace("rendering", "绘制场景");
            return "等距增强在" + stage + "时失败，已停用。请查看 console.txt 中的 [VPISO] 错误。";
        }
        return text;
    }

    /** Theme.fonts has just rebuilt the atlas; Theme.fit uploads it immediately afterwards. */
    public static void afterThemeFonts() {
        chineseFont = null;
        fontContext = 0;
        if (fontFile == null) return;
        var atlas = ImGui.getIO().getFonts();
        var ranges = new ImFontGlyphRangesBuilder();
        ranges.addRanges(atlas.getGlyphRangesDefault());
        ranges.addText(EXTRA_GLYPHS);
        CHINESE.values().forEach(ranges::addText);
        ImFontConfig config = new ImFontConfig();
        try {
            config.setOversampleH(2);
            config.setOversampleV(1);
            float scale = Math.max(1f, ImGui.getIO().getDisplaySizeY() / 1080f);
            chineseFont = atlas.addFontFromFileTTF(fontFile, Math.round(15f * scale), config, ranges.buildRanges());
            fontContext = ImGui.getCurrentContext().ptr;
            if (chinese()) ImGui.getIO().setFontDefault(chineseFont);
        } finally {
            config.destroy();
        }
    }

    public static boolean pushFont() {
        if (!chinese() || chineseFont == null || fontContext != ImGui.getCurrentContext().ptr) return false;
        ImGui.pushFont(chineseFont);
        return true;
    }

    private static String findFont() {
        String windows = System.getenv("WINDIR");
        if (windows == null) windows = "C:/Windows";
        for (String file : new String[] { "msyh.ttc", "simsun.ttc" }) {
            Path candidate = Path.of(windows, "Fonts", file);
            if (Files.isRegularFile(candidate)) return candidate.toString();
        }
        return null;
    }

    private IsometricText() {}
}
