import java.util.List;
import java.util.Map;
import viewpoint.platform.GlProgram;
import viewpoint.platform.Graphics;
import viewpoint.platform.LiveSettings;
import viewpoint.platform.ShaderPack;
import viewpoint.platform.ShaderPacks;
import vpiso.GameAccess;
import vpiso.IsometricBridge;
import vpiso.IsometricControls;

/** Uses actual VP settings types without starting the game or saving any settings file. */
public final class ProfileProbe {
    public static void main(String[] args) throws Exception {
        GameAccess.call("viewpoint.platform.ShaderPacks", "discover", null,
                new Class<?>[] { java.io.File.class }, (Object) null);
        ShaderPack registeredLibrary = (ShaderPack) GameAccess.call("viewpoint.platform.ShaderPacks", "find", null,
                new Class<?>[] { String.class }, "library");
        ShaderPacks.useBuiltIn("default", "high", Map.of("upscaling","fsr3","reflections","high"));
        ShaderPack selectedDefault = ShaderPacks.active().pack;
        Graphics.FRAME_GENERATION.set(true);
        String blocked = IsometricBridge.requestedBlockReason();
        if (blocked == null || blocked.contains("shader pack") || blocked.contains("upscaling")
                || !blocked.contains("SSR") || blocked.contains("frame generation")) {
            throw new AssertionError("Missing real-session blocker: " + blocked);
        }
        GameAccess.set("viewpoint.platform.Graphics","modern",null,List.of(selectedDefault));
        GameAccess.set("viewpoint.platform.Graphics","iris",null,List.of());
        IsometricControls.exposeLibrary();
        IsometricControls.exposeLibrary();
        var modern = (List<?>) GameAccess.field("viewpoint.platform.Graphics","modern",null);
        if (modern.size()!=2 || !((ShaderPack)modern.get(1)).id.equals("library")) {
            throw new AssertionError("Library not exposed once");
        }
        if (modern.get(1) != registeredLibrary) {
            throw new AssertionError("Library must reuse VP's discovered pack; its option visibility uses object identity");
        }
        LiveSettings.Choice mode = LiveSettings.choice("graphics.mode","Mode","test",new String[]{"Vanilla","Modern"},1);
        GameAccess.set("viewpoint.platform.Graphics","mode",null,mode);
        GameAccess.call("viewpoint.platform.Graphics","declarePack",null,new Class<?>[0]);
        var choice = (LiveSettings.Choice) GameAccess.field("viewpoint.platform.Graphics","pack",null);
        choice.set(1);
        if (((ShaderPack) GameAccess.call("viewpoint.platform.Graphics","inUse",null,new Class<?>[0])).id.equals("library") == false) {
            throw new AssertionError("VP cannot select Library from the expanded Choice");
        }
        choice.set(0);
        LiveSettings.Choice upscale = LiveSettings.choice("pack.library.upscaling","Upscaling","test",new String[]{"off","fsr3"},1);
        IsometricControls.tick();
        vpiso.Main.compatible = true;
        String shadowStatus = IsometricBridge.shadowStatus();
        if (!shadowStatus.startsWith("Dynamic shadows unavailable: ") || !shadowStatus.contains("SSR"))
            throw new AssertionError("Visible shadow state hides the selected unsupported pack: " + shadowStatus);
        if (!Graphics.FRAME_GENERATION.get() || choice.get()!=0 || upscale.get()!=1) {
            throw new AssertionError("Settings changed without the user action");
        }
        LiveSettings.Choice defaultUpscale = LiveSettings.choice("pack.default.upscaling", "Upscaling", "test", new String[]{"off","fsr3"},1);
        LiveSettings.Choice defaultSSR = LiveSettings.choice("pack.default.reflections", "Reflections", "test", new String[]{"off","high"},1);
        Object button = GameAccess.field("vpiso.IsometricControls", "prepareButton", null);
        GameAccess.call("viewpoint.platform.SettingsWindow$Button", "press", button, new Class<?>[0]);
        IsometricControls.tick();
        if (mode.get()!=1 || choice.get()!=0 || !Graphics.FRAME_GENERATION.get() || upscale.get()!=1) {
            throw new AssertionError("Compatible-profile action did not apply its settings");
        }
        if (defaultUpscale.get()!=1 || defaultSSR.get()!=0) throw new AssertionError("Profile changed upscaling or retained SSR");
        Graphics.FRAME_GENERATION.set(false);
        for (String pack : List.of("default", "vivid", "somber", "library")) {
            ShaderPacks.useBuiltIn(pack, pack.equals("somber") ? "quality" : "high", Map.of("upscaling","off","reflections","off"));
            if (IsometricBridge.requestedBlockReason()!=null) throw new AssertionError("Built-in blocked: " + pack);
        }
        for (String pack : List.of("normal", "third-party")) {
            if (IsometricBridge.blockedReason(pack,false,0,false,false)==null) throw new AssertionError("Unsupported pack accepted");
        }
        for (String pack : List.of("default", "vivid", "somber", "library")) {
            for (String upscaleMode : List.of("off", "fsr3", "xess", "dlss")) {
                ShaderPacks.useBuiltIn(pack, pack.equals("somber") ? "quality" : "high",
                        Map.of("upscaling", upscaleMode, "reflections", "off"));
                if (IsometricBridge.requestedBlockReason()!=null) throw new AssertionError("Upscaler blocked: " + pack + "/" + upscaleMode);
            }
        }
        if (IsometricBridge.blockedReason("default",false,4,false,false)==null
                || IsometricBridge.blockedReason("default",false,-1,false,false)==null)
            throw new AssertionError("Unknown vendor accepted");
        ShaderPacks.useBuiltIn("default", "high", Map.of("upscaling","off","reflections","off"));
        // Reproduce the stale-linked-pack gate: render thread has not relinked yet.
        GameAccess.set("viewpoint.platform.GlProgram","pack",null,ShaderPacks.active());
        ShaderPacks.useBuiltIn("library","high",Map.of("upscaling","off"));
        if (!GlProgram.pack().pack.id.equals("default") || !ShaderPacks.active().pack.id.equals("library")) {
            throw new AssertionError("Stale-pack fixture not established");
        }
        if (IsometricBridge.requestedBlockReason()!=null) {
            throw new AssertionError("New Library request is blocked by the old linked Default pack");
        }
        GameAccess.set("zombie.core.PerformanceSettings", "fboRenderChunk", null, true);
        IsometricBridge.nativeShadowStatus(true, true, true);
        if (!IsometricBridge.shadowStatus().equals("Dynamic shadows: active.")) throw new AssertionError("Ready shadow state not shown");
        ShaderPacks.useBuiltIn("vivid", "high", Map.of("upscaling", "off"));
        if (!IsometricBridge.shadowStatus().equals("Dynamic shadows: active.")) throw new AssertionError("Supported pack switch blocked visible shadow state");
        ShaderPacks.useBuiltIn("library", "high", Map.of("upscaling", "off"));
        IsometricBridge.nativeShadowStatus(true, false, true);
        if (!IsometricBridge.shadowStatus().contains("visible floor")) throw new AssertionError("Missing floor data is hidden");
        IsometricBridge.nativeShadowStatus(true, true, false);
        if (!IsometricBridge.shadowStatus().contains("shadow maps")) throw new AssertionError("Missing shadow maps are hidden");
        System.out.println("Profile regression PASS: FSR3/XeSS/DLSS and frame generation accepted; SSR remains diagnosed; explicit profile preserves both frame generation settings and selected upscaler; unknown vendor rejected; Library selectable once; stale linked pack does not block the requested pack.");
    }
}
