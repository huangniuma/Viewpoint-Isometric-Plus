package vpiso;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import viewpoint.render.FarRelief;
import viewpoint.platform.Gl;
import viewpoint.platform.GlDebug;
import viewpoint.platform.GlProgram;
import viewpoint.render.SceneData;
import viewpoint.render.WorldRenderer;

/** Generates VP's actual sun/moon maps without drawing its world color or postprocessing. */
public final class NativeShadowMaps {
    private static final Class<?>[] NONE = new Class<?>[0];
    private static final Class<?>[] SCENE = { SceneData.class };
    private static Object shadows;
    private static int count;
    private static Object basisOwner;
    private static FarRelief.Field basisGrid;
    private static float basisLevels;
    private static float[] basisBands;
    private static boolean basisLightweight;

    /** Settled VP casters are reusable only while their terrain height basis matches. */
    public static void prepareBasis(Object pass, SceneData scene) {
        FarRelief.Lift lift = scene.relief;
        FarRelief.Field grid = lift == null ? null : lift.grid();
        float levels = lift == null ? 0f : lift.levels();
        float[] bands = lift == null ? null : lift.bands();
        boolean lightweight = LightweightShadows.active(scene);
        // Camera cutaway plans are used only by planned colour draws. Physical
        // sun/lamp/spot passes draw unplanned; their caster lifetime is tracked
        // by VP itself. A camera-only cut must not release every shadow cache.
        if (basisOwner == pass && basisGrid == grid && basisLevels == levels
                && java.util.Arrays.equals(basisBands, bands) && basisLightweight == lightweight) return;
        Object layers = GameAccess.field("viewpoint.render.ShadowPass", "layers", pass);
        GameAccess.call("viewpoint.render.CascadeLayers", "release", layers, new Class<?>[0]);
        java.util.Arrays.fill((boolean[]) GameAccess.field("viewpoint.render.ShadowPass", "valid", pass), false);
        basisOwner = pass; basisGrid = grid; basisLevels = levels;
        basisLightweight = lightweight;
        basisBands = bands == null ? null : bands.clone();
        NativeLampShadows.invalidate(pass);
        NativeSpotShadows.invalidate(pass);
    }

    /** Render-thread only. The caller owns GL-state restoration and scene retirement. */
    public static boolean draw(SceneData scene, int resolution, int cascades) {
        return draw(scene,resolution,cascades,null);
    }
    public static boolean draw(SceneData scene, int resolution, int cascades, NativeSurfaces surfaces) {
        return draw(scene,resolution,cascades,surfaces,IsometricEffects.DEFAULT);
    }
    public static boolean draw(SceneData scene, int resolution, int cascades, NativeSurfaces surfaces, IsometricEffects effects) {
        return draw(scene,resolution,cascades,surfaces,effects,null);
    }
    public static boolean draw(SceneData scene, int resolution, int cascades, NativeSurfaces surfaces, IsometricEffects effects, Matrix4f handView) {
        count = 0;
        currentScene=null;
        NativeLampShadows.reset();
        NativeSpotShadows.reset();
        if (scene.sunStrength <= .02f && scene.lampShadowCount == 0 && !NativeSpotShadows.present(scene)) return false;
        WorldRenderer.prepare(Long.MAX_VALUE);
        if (!(Boolean) GameAccess.field("viewpoint.render.WorldRenderer", "ready", null)) return false;
        GameAccess.staticCall("viewpoint.render.PackLinks", "follow");
        if (!IsometricShaders.supports(GlProgram.pack().pack.id)) return false;
        shadows = GameAccess.field("viewpoint.render.WorldRenderer", "shadows", null);
        Object models = GameAccess.field("viewpoint.render.WorldRenderer", "models", null);
        boolean listening = (Boolean) GameAccess.field("viewpoint.platform.GlDebug", "listening", null);
        GlDebug.listen(true);
        GlDebug.push("VPISO native shadow maps");
        try {
            Gl.resetState();
            GL11.glClearDepth(1.0);
            GL11.glDepthMask(true);
            GameAccess.staticCall("viewpoint.render.FrameStream", "frameStarted");
            GameAccess.call("viewpoint.render.FarRelief", "frame", null, SCENE, scene);
            Object floors = GameAccess.field("viewpoint.render.WorldRenderer", "floors", null);
            GameAccess.call("viewpoint.render.FloorBakes", "update", floors,
                    new Class<?>[] { SceneData.class, boolean.class }, scene, false);
            GameAccess.call("viewpoint.render.PackModels", "upload", null,
                    new Class<?>[] { long.class }, System.nanoTime());
            GameAccess.call("viewpoint.render.Meshes", "prepare", null, SCENE, scene);
            viewpoint.core.Frame frame = IsometricBridge.renderFrame();
            GameAccess.call("viewpoint.render.ModelPass", "prepare", models,
                    new Class<?>[] { SceneData.class, long.class, float.class, float.class, float.class, float.class },
                    scene, frame == null ? 0L : frame.number, 1f, 0f, 0f, 0f);
            Object far = GameAccess.field("viewpoint.render.ShadowPass", "farMaps", shadows);
            GameAccess.call("viewpoint.render.FarShadow", "off", far, NONE);
            int side = Math.max(256, Math.min(16384, resolution));
            int layers = Math.max(1, Math.min(3, cascades));
            GameAccess.call("viewpoint.render.ShadowPass", "resize", shadows,
                    new Class<?>[] { int.class }, side);
            boolean lamps,spots;
            SceneData previousCasters = LightweightShadows.enter(scene,effects);
            try {
                prepareBasis(shadows, scene);
                if(scene.sunStrength > .02f) {
                    GameAccess.call("viewpoint.render.ShadowPass", "drawCascades", shadows,
                            new Class<?>[] { SceneData.class, int.class }, scene, layers);
                    count = layers;
                }
                lamps=NativeLampShadows.draw(shadows,scene);
                spots=NativeSpotShadows.draw(shadows,scene,handView);
            } finally { LightweightShadows.restore(previousCasters); }
            if(surfaces!=null) surfaces.draw(scene,models);
            currentScene=scene;
            return count>0 || lamps || spots;
        } finally {
            try {
                GameAccess.call("viewpoint.render.ModelPass", "endFrame", models, NONE);
            } finally {
                GlDebug.pop();
                GlDebug.listen(listening);
            }
        }
    }

    public static int count() { return count; }
    private static SceneData currentScene;
    public static SceneData scene() { return currentScene; }
    public static int texture() { return ((Number) GameAccess.field("viewpoint.render.ShadowPass", "cascades", shadows)).intValue(); }
    public static Matrix4f matrix(int layer) { return ((Matrix4f[]) GameAccess.field("viewpoint.render.ShadowPass", "matrix", shadows))[layer]; }
    public static float reach(int layer) { return ((float[]) GameAccess.field("viewpoint.render.ShadowPass", "reach", shadows))[layer]; }
    public static int size() { return ((Number) GameAccess.field("viewpoint.render.ShadowPass", "size", shadows)).intValue(); }
    public static float depthRange() { return 2f * ((Number) GameAccess.field("viewpoint.render.ShadowPass", "sunDistance", shadows)).floatValue() - 1f; }

    private NativeShadowMaps() {}
}
