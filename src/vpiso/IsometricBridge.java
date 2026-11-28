package vpiso;

import viewpoint.core.Frame;
import viewpoint.core.SplitScreen;
import viewpoint.core.View;
import viewpoint.environment.Atmosphere;
import viewpoint.light.Daylight;
import viewpoint.models.Characters;
import viewpoint.models.Corpses;
import viewpoint.models.Models;
import viewpoint.platform.BuildPin;
import viewpoint.platform.Graphics;
import viewpoint.platform.IrisPacks;
import viewpoint.platform.Settings;
import viewpoint.platform.ShaderPacks;

/** Optional VP world drawing, or effects over the native world, never both in one frame. */
public final class IsometricBridge {
    public static final float DISTANCE = IsoCameraMath.DISTANCE;
    public static final float NEAR = IsoCameraMath.NEAR;
    public static final float FAR = IsoCameraMath.FAR;
    private static final ThreadLocal<Boolean> CAPTURING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> NATIVE_SHADOW_CAPTURE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Frame> RENDERING = new ThreadLocal<>();
    private static final ThreadLocal<IsometricEffects> MODEL_EFFECTS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> NATIVE_WATER = new ThreadLocal<>();
    private static final ThreadLocal<Object> SELF = new ThreadLocal<>();
    private static final Frame[] FRAMES = { new Frame(), new Frame(), new Frame() };
    private static final NativeOverlayDrawer[] DRAWERS = {
        new NativeOverlayDrawer(FRAMES[0]), new NativeOverlayDrawer(FRAMES[1]), new NativeOverlayDrawer(FRAMES[2])
    };
    private static final IsoDrawer[] MODEL_DRAWERS = {
        new IsoDrawer(FRAMES[0]), new IsoDrawer(FRAMES[1]), new IsoDrawer(FRAMES[2])
    };
    private static volatile boolean enabled = true;
    private static volatile boolean modelsEnabled;
    private static volatile boolean failed;
    private static volatile boolean nativeDepthMissing;
    private static volatile String nativeShadowStatus = "Dynamic shadows: waiting for scene.";
    private static volatile boolean nativeShadowsReady;
    private static volatile String publishedStatus = "Viewpoint Isometric Plus is ready.";
    private static float captureRadius = 96f;
    private static boolean submitted;
    // UI readers must not observe the transient reset at the start of afterWorld.
    private static volatile boolean modelSceneReady;
    private static long lastCapturedFrame = -2;
    private static float lastHalfHeight;
    private static IsoCameraMath.Fit captureFit;
    private static float captureCamX, captureCamY;
    private static int captureWidth, captureHeight;
    private static float captureYaw, lastYaw;
    private static final ThreadLocal<Boolean> NATIVE_GROUND = ThreadLocal.withInitial(() -> false);
    private static boolean groundPrepared;
    private static boolean groundGrass;
    private static volatile boolean groundRequested;
    public static boolean shadowPreloadAllowed() {
        return Main.compatible && enabled && !failed && !View.enabled && IsometricControls.shadowPreload()
                && IsometricControls.effects().needsShadows() && BuildPin.supported()
                && ShaderPacks.active() != null && requestedBlockReason() == null
                && (modelsEnabled || zombie.core.PerformanceSettings.fboRenderChunk)
                && SplitScreen.count((Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null)) <= 1;
    }
    public static float shadowPreloadYaw() { return modelsEnabled ? RotationCamera.yawDegrees() : 0f; }
    public static boolean frameGenerationAllowed() { return Main.compatible && enabled && !failed && !View.enabled
            && BuildPin.supported() && requestedBlockReason()==null
            && SplitScreen.count((Object[])GameAccess.field("zombie.characters.IsoPlayer","players",null))<=1; }

    public static boolean nativeGroundActive() { return groundRequested; }
    public static void beforeNativeWorld() {
        if (Main.compatible) checkSession();
        IsometricEffects effects=IsometricControls.effects();
        boolean active=Main.compatible && enabled && !modelsEnabled && !failed && !View.enabled
                && (effects.needsShadows() || effects.grass() || effects.snow())
                && BuildPin.supported() && requestedBlockReason()==null
                && SplitScreen.count((Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null)) <= 1
                && ((Number)GameAccess.field("zombie.iso.IsoCamera$FrameState", "playerIndex",
                    GameAccess.field("zombie.iso.IsoCamera", "frameState", null))).intValue()==0
                && GameAccess.staticCall("zombie.iso.IsoCamera", "getCameraCharacter")!=null
                && (Boolean)GameAccess.field("zombie.core.PerformanceSettings","fboRenderChunk",null);
        if (!active) nativeShadowsReady=false;
        NativeSpriteShadows.prepare(active && effects.needsShadows() && nativeShadowsReady);
        NATIVE_GROUND.set(active);
        groundRequested=active;
        if(!active) { groundPrepared=false;return; }
        if(groundGrass!=(effects.grass() || effects.snow())) { groundPrepared=false; groundGrass=effects.grass() || effects.snow(); }
        if(!groundPrepared) {
            Object renderer=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell","instance",null);
            if(GameAccess.field(renderer.getClass().getName(),"cell",renderer)!=null) {
                boolean previous=(Boolean)GameAccess.field("zombie.iso.fboRenderChunk.FBORenderLevels","clearCachedSquares",null);
                GameAccess.set("zombie.iso.fboRenderChunk.FBORenderLevels","clearCachedSquares",null,true);
                try {
                    GameAccess.call(renderer.getClass().getName(),"invalidateAll",renderer,new Class<?>[]{int.class},0);
                    groundPrepared=true;
                } finally { GameAccess.set("zombie.iso.fboRenderChunk.FBORenderLevels","clearCachedSquares",null,previous); }
            }
        }
        NativeGround.queueBegin(true);
    }
    public static void finishNativeWorld() {
        if(NATIVE_GROUND.get()) NativeGround.queueEnd();
        NATIVE_GROUND.remove();
    }

    public static boolean inView(float x, float y, float z) {
        if (SELF.get() != null && !NATIVE_SHADOW_CAPTURE.get()) return captureFit == null || IsoCameraMath.visible(captureFit,
                captureCamX, captureCamY, captureWidth, captureHeight, x, y, z, 12f, captureYaw);
        return x * x + z * z <= captureRadius * captureRadius;
    }
    public static boolean capturing() { return CAPTURING.get(); }
    public static boolean nativeShadowCapture() { return Main.compatible && NATIVE_SHADOW_CAPTURE.get(); }
    public static boolean rendering() { return RENDERING.get() != null; }
    public static Frame renderFrame() { return RENDERING.get(); }
    public static void rendering(Frame frame) {
        MODEL_EFFECTS.remove();
        NATIVE_WATER.remove();
        if (frame == null) RENDERING.remove(); else RENDERING.set(frame);
    }
    public static void modelRendering(Frame frame, IsometricEffects effects) {
        RENDERING.set(frame);
        MODEL_EFFECTS.set(effects);
        NATIVE_WATER.remove();
    }
    public static void modelWorldRendering(Frame frame, IsometricEffects effects) {
        modelWorldRendering(frame, effects, true);
    }
    public static void modelWorldRendering(Frame frame, IsometricEffects effects, boolean nativeWater) {
        modelRendering(frame, effects.withWater(false));
        NATIVE_WATER.set(PoolPitCompatibility.nativeWater(nativeWater));
    }
    public static boolean usesNativeWater() { return Boolean.TRUE.equals(NATIVE_WATER.get()); }
    public static boolean modelRendering() { return MODEL_EFFECTS.get() != null; }
    public static IsometricEffects renderEffects() { return MODEL_EFFECTS.get(); }
    public static boolean waterRendering() {
        IsometricEffects effects = MODEL_EFFECTS.get();
        return effects != null && effects.water();
    }
    public static boolean suppressWeather() { return active(); }
    public static boolean active() { return modelsEnabled && submitted && !View.enabled && enabled && !failed; }
    public static boolean rotationAllowed() { return !View.enabled && rotationConfigured(); }
    /** VP sets View.enabled before its transition draws the isometric endpoint. */
    public static boolean rotationConfigured() { return modelsEnabled && enabled && !failed && Main.compatible
            && BuildPin.supported() && requestedBlockReason() == null
            && SplitScreen.count((Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null)) <= 1; }
    public static boolean characterHidden(Object moving) {
        Object observer = SELF.get();
        // Native rendering already refreshes this in shadow-only mode. Model mode
        // replaces IsoPlayer.render, so retain its client-player target decision once.
        if (observer != null && moving != observer && !NATIVE_SHADOW_CAPTURE.get()
                && GameAccess.type("zombie.characters.IsoPlayer").isInstance(moving)
                && !GameAccess.type("zombie.characters.animals.IsoAnimal").isInstance(moving)
                && (Boolean) GameAccess.field("zombie.network.GameClient", "client", null)) {
            boolean seen = (Boolean) GameAccess.call("zombie.characters.IsoPlayer", "checkCanSeeClient", observer,
                    new Class<?>[] { GameAccess.type("zombie.characters.IsoPlayer") }, moving);
            GameAccess.call("zombie.iso.IsoObject", "setTargetAlpha", moving,
                    new Class<?>[] { int.class, float.class }, 0, seen ? 1f : 0f);
            if (!seen) ((java.util.Map<?, ?>) GameAccess.field("zombie.characters.IsoPlayer", "spottedPlayerTimer", observer))
                    .remove(GameAccess.call("zombie.characters.IsoPlayer", "getRemoteID", moving, new Class<?>[0]));
        }
        return characterAlpha(moving) <= 0f;
    }
    /** The simulation owns visibility targets and alpha stepping, including animals. */
    public static float characterAlpha(Object moving) {
        if (SELF.get() == null || moving == SELF.get() || !GameAccess.type("zombie.characters.IsoGameCharacter").isInstance(moving)) return 1f;
        Object square = GameAccess.call("zombie.iso.IsoMovingObject", "getCurrentSquare", moving, new Class<?>[0]);
        if (square == null) return 0f;
        float alpha = ((Number) GameAccess.call("zombie.iso.IsoObject", "getAlpha", moving, new Class<?>[] { int.class }, 0)).floatValue();
        return Float.isFinite(alpha) && alpha > .001f ? Math.min(1f, alpha) : 0f;
    }
    /** Preserve the character's look/texture record while fading every captured body part. */
    public static void characterFade(Frame frame, Object moving, int first) {
        float alpha = characterAlpha(moving);
        if (alpha >= 1f) return;
        if (!NATIVE_SHADOW_CAPTURE.get()) NativeObjectState.characterFade(frame,moving,first,alpha);
        float[] values = (float[]) GameAccess.field("viewpoint.render.ModelDraws", "values", frame.scene.models);
        for (int i = first; i < frame.scene.models.count(); i++) {
            int at = i * 68 + 56;
            float lo = Math.max(0f, Math.min(1f, values[at]));
            float hi = Math.max(lo, Math.min(1f, values[at + 1]));
            values[at + 1] = lo + (hi - lo) * alpha;
        }
    }

    public static void setModelsEnabled(boolean value) {
        if (modelsEnabled == value) return;
        modelsEnabled = value;
        resetMode();
        System.out.println("[VPISO] isometric 3D models " + (value ? "on" : "off"));
    }
    private static void resetMode() {
        NativeObjectState.resetPick();
        NativeCursorInput.reset();
        groundRequested=false;
        submitted = false;
        modelSceneReady = false;
        lastCapturedFrame = -2;
        publishedStatus = "Viewpoint Isometric Plus is ready.";
        nativeShadowStatus = "Dynamic shadows: waiting for scene.";
        nativeShadowsReady=false;
    }

    /** A reconnect/new save owns fresh transient state, while user preferences stay intact. */
    public static void newSession() {
        resetMode();
        IsometricFrameGeneration.newSession();
        failed = nativeDepthMissing = false;
        groundPrepared = false;
        lastHalfHeight = 0f; captureFit = null;
        RotationCamera.clearSnapshot();
        RotationInteraction.cameraChanged();
        NativeIsoState.newSession();
    }

    private static void checkSession() {
        Object world = GameAccess.field("zombie.iso.IsoWorld", "instance", null);
        Object cell = world == null ? null : GameAccess.field("zombie.iso.IsoWorld", "currentCell", world);
        Object[] players = (Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null);
        Object player = players == null || players.length == 0 ? null : players[0];
        if (ClientSession.observe(cell, player)) newSession();
    }

    public static void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        resetMode();
        System.out.println("[VPISO] isometric lighting " + (value ? "on" : "off"));
    }
    public static String status() {
        if (!enabled) return "Viewpoint Isometric Plus: enhanced isometric rendering off";
        if (failed) return "Viewpoint Isometric Plus stopped after a rendering error. Restart the game after checking console.txt.";
        if (View.enabled) return "Viewpoint Isometric Plus is armed. Return to isometric view with VP's O key.";
        if (!modelsEnabled && nativeDepthMissing && IsometricControls.effects().needsShadows() && !publishedStatus.startsWith("Viewpoint Isometric Plus is waiting: "))
            return "Shadows unavailable: the native depth buffer is missing. Color style can still run.";
        if (modelSceneReady && modelsEnabled && RotationCamera.requested()) return "VP 3D models: hold middle mouse and move to rotate.";
        return publishedStatus;
    }
    public static String shadowStatus() {
        if (!enabled || !IsometricControls.effects().needsShadows()) return "Dynamic shadows: off.";
        if (!Main.compatible) return "Dynamic shadows: unsupported Viewpoint version.";
        if (failed) return "Viewpoint Isometric Plus stopped after a rendering error. Restart the game after checking console.txt.";
        if (View.enabled) return "Viewpoint Isometric Plus is armed. Return to isometric view with VP's O key.";
        String blocked = requestedBlockReason();
        if (blocked == null && !modelsEnabled && !(Boolean) GameAccess.field("zombie.core.PerformanceSettings", "fboRenderChunk", null))
            blocked = "native chunk renderer is required";
        if (blocked != null) return "Dynamic shadows unavailable: " + blocked;
        return modelsEnabled ? (modelSceneReady ? "Dynamic shadows: active." : "Dynamic shadows: waiting for scene.") : nativeShadowStatus;
    }
    public static void nativeShadowStatus(boolean depth, boolean ground, boolean maps) {
        nativeShadowsReady=depth && ground && maps;
        nativeShadowStatus = !depth ? "Shadows unavailable: the native depth buffer is missing. Color style can still run."
                : !ground ? "Dynamic shadows: waiting for visible floor data."
                : !maps ? "Dynamic shadows: waiting for shadow maps." : "Dynamic shadows: active.";
    }
    public static boolean afterWorld(Object cell, boolean handled) {
        submitted = false;
        if (!Main.compatible) { modelSceneReady = false; return handled; }
        checkSession();
        try {
            if (handled) { NativeIsoState.original(); return true; }
            if (!enabled || !modelsEnabled || failed || View.enabled || !BuildPin.supported()) return NativeIsoState.original();
            Object state = GameAccess.field("zombie.iso.IsoCamera", "frameState", null);
            int player = ((Number) GameAccess.field("zombie.iso.IsoCamera$FrameState", "playerIndex", state)).intValue();
            if (player != 0) return NativeIsoState.original();
            Object character = GameAccess.staticCall("zombie.iso.IsoCamera", "getCameraCharacter");
            if (character == null || SplitScreen.count((Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null)) > 1) return NativeIsoState.original();
            String blocked = requestedBlockReason();
            if (blocked != null) {
                publishedStatus = "Viewpoint Isometric Plus is waiting: " + blocked;
                return NativeIsoState.original();
            }
            Object sprites = GameAccess.field("zombie.core.SpriteRenderer", "instance", null);
            int index = ((Number) GameAccess.call("zombie.core.SpriteRenderer", "getMainStateIndex", sprites, new Class<?>[0])).intValue();
            if (index < 0 || index >= FRAMES.length) throw new IllegalStateException("Unexpected sprite buffer " + index);
            IsoDrawer drawer = MODEL_DRAWERS[index];
            drawer.effects = PoolPitCompatibility.effects(IsometricControls.effects());
            RotationCamera.refresh();
            RotationInteraction.prepareFrame();
            Object nativeRenderer;
            try {
                nativeRenderer = capture(FRAMES[index], drawer, cell, character, player);
                drawer.nativeWater = PoolPitCompatibility.nativeWater(NativeWater.available());
                if (drawer.effects.water()) {
                    NativeOverlayDrawer waterDrawer = DRAWERS[index];
                    waterDrawer.effects = drawer.effects.waterOnly();
                    waterDrawer.releaseScene = false;
                    waterDrawer.waterDepthBias = drawer.rotation == null ? -.001f : -.00001f;
                    waterDrawer.rotation = drawer.rotation;
                    waterDrawer.receiver.capture(FRAMES[index], player);
                    waterDrawer.water.capture(FRAMES[index], cell, player, drawer.rotation);
                }
            } catch (Throwable error) {
                drawer.nativeTrees.release();
                release(FRAMES[index]);
                throw error;
            }
            GameAccess.call("zombie.core.SpriteRenderer", "glDepthMask", sprites, new Class<?>[] { boolean.class }, true);
            GameAccess.call("zombie.core.SpriteRenderer", "glStencilMask", sprites, new Class<?>[] { int.class }, 255);
            NativeTreeCutaway.drawStencilMask(cell, sprites);
            GameAccess.call("zombie.core.SpriteRenderer", "drawGeneric", sprites,
                    new Class<?>[] { GameAccess.type("zombie.core.textures.TextureDraw$GenericDrawer") }, drawer);
            submitted = true;
            if (drawer.nativeWater) NativeWater.queue(nativeRenderer, player, drawer.rotation);
            if (drawer.effects.water()) GameAccess.call("zombie.core.SpriteRenderer", "drawGeneric", sprites,
                    new Class<?>[] { GameAccess.type("zombie.core.textures.TextureDraw$GenericDrawer") }, DRAWERS[index]);
            drawer.nativeVision.queue(player);
            GameAccess.call("viewpoint.interact.MouseTargets", "frame", null, new Class<?>[] { boolean.class }, true);
            RotationCamera.Snapshot previousRotation = RotationLayers.begin(drawer.rotation);
            try { NativeIsoState.renderInteraction(cell, player); }
            finally { RotationLayers.end(previousRotation); }
            NativeVehicleHints.render(cell, drawer.rotation);
            publishedStatus = "VP 3D models are active in isometric view.";
            return true;
        } catch (Throwable error) {
            fail("capturing", error);
            return NativeIsoState.original();
        } finally {
            modelSceneReady = submitted;
        }
    }
    public static void nativeDepthAvailable(boolean available) { nativeDepthMissing = !available; }

    public static void afterNativeWorld(Object cell) {
        if (Main.compatible) checkSession();
        if (!Main.compatible || !enabled || modelsEnabled || failed || View.enabled || !BuildPin.supported()) return;
        Frame captured = null;
        try {
            Object state = GameAccess.field("zombie.iso.IsoCamera", "frameState", null);
            int player = ((Number) GameAccess.field("zombie.iso.IsoCamera$FrameState", "playerIndex", state)).intValue();
            if (player != 0) return;
            Object character = GameAccess.staticCall("zombie.iso.IsoCamera", "getCameraCharacter");
            if (character == null || SplitScreen.count((Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null)) > 1) return;
            IsometricEffects selected = IsometricControls.effects();
            boolean generation=IsometricControls.frameGeneration() && frameGenerationAllowed();
            if (!selected.any() && !generation) { publishedStatus = "All effects are off. Original rendering."; return; }
            String blocked = selected.needsShadows() || selected.grass() || selected.snow() ? requestedBlockReason() : null;
            if (selected.needsShadows() && !(Boolean) GameAccess.field("zombie.core.PerformanceSettings", "fboRenderChunk", null)) {
                blocked = "native chunk renderer is required";
            }
            IsometricEffects effects = blocked == null ? selected : new IsometricEffects(false, selected.shadowStrength(),
                    selected.softness(), selected.distance(), selected.resolution(), selected.style(), selected.styleStrength(),
                    selected.exposure(), selected.saturation(), selected.contrast(), selected.water(), false);
            effects=PoolPitCompatibility.effects(effects);
            publishedStatus = blocked != null ? "Viewpoint Isometric Plus is waiting: " + blocked
                    : "Native world effects are active.";
            if (!effects.any() && !generation) return;
            Object sprites = GameAccess.field("zombie.core.SpriteRenderer", "instance", null);
            int index = ((Number) GameAccess.call("zombie.core.SpriteRenderer", "getMainStateIndex", sprites, new Class<?>[0])).intValue();
            if (index < 0 || index >= FRAMES.length) throw new IllegalStateException("Unexpected sprite buffer " + index);
            Frame frame = FRAMES[index];
            captured = frame;
            release(frame);
            frame.scene.reset();
            frame.modelObjects.clear(); frame.modelItems.clear(); frame.cameraShot.clear();
            frame.cameraShot.on = true; frame.onCamera = false;
            frame.camX = GameAccess.coordinate(character, "getX");
            frame.camY = GameAccess.coordinate(character, "getY");
            frame.camZ = GameAccess.coordinate(character, "getZ");
            frame.number = ((Number) GameAccess.field("viewpoint.FP", "frameNumber", null)).longValue();
            if(effects.snow()) frame.simNanos=((Number)GameAccess.staticCall("viewpoint.game.SimClock","simulated")).longValue();
            NativeOverlayDrawer drawer = DRAWERS[index];
            drawer.rotation = null;
            drawer.effects = effects;
            drawer.releaseScene = true;
            drawer.waterDepthBias = .001f;
            float heading=((Number)GameAccess.call("zombie.characters.IsoGameCharacter","getDirectionAngleRadians",character,new Class<?>[0])).floatValue();
            drawer.handView.setLookAt(0,1.5f,0,-(float)Math.cos(heading),1.5f,-(float)Math.sin(heading),0,1,0);
            if (effects.needsShadows() || effects.water() || effects.grass() || effects.snow()) drawer.receiver.capture(frame, player);
            if (effects.needsShadows() || effects.snow() || generation) drawer.shadowReceiver.captureWorld(frame, player);
            if (effects.needsShadows() || effects.grass() || effects.snow()) {
                float reach=effects.grass()?Math.max(effects.distance(),viewpoint.render.Grass.reach()):effects.distance();
                captureShadows(frame, cell, character, player,effects.snow()?Math.max(reach,96f):reach);
            } else if(generation) captureMotion(frame,cell,character);
            if(effects.needsShadows())drawer.relightMask.capture(frame,cell,player);
            if (effects.water()) {
                if (!effects.needsShadows() && !effects.grass() && !effects.snow()) {
                    prepareEnvironment(frame, cell);
                    // Water-only frames still need VP's normal asynchronous body map.
                    // Keep its optical data while retaining native flat geometry.
                    viewpoint.far.Relief.frame(frame);
                    frame.scene.relief(null);
                }
                drawer.water.capture(frame, cell, player);
            }
            GameAccess.call("zombie.core.SpriteRenderer", "drawGeneric", sprites,
                    new Class<?>[] { GameAccess.type("zombie.core.textures.TextureDraw$GenericDrawer") }, drawer);
            if(generation) IsometricFrameGeneration.queueNative(frame,drawer.shadowReceiver,sprites,index);
        } catch (Throwable error) {
            if (captured != null) release(captured);
            fail("capturing", error);
        }
    }

    private static Object capture(Frame frame, IsoDrawer drawer, Object cell, Object character, int player) {
        Characters.release(frame);
        Corpses.release(frame);
        Models.release(frame);
        frame.scene.reset();
        frame.modelObjects.clear();
        frame.modelItems.clear();
        frame.cameraShot.clear();
        frame.cameraShot.on = true; // Capture the player's complete body.
        frame.onCamera = false;
        frame.camX = GameAccess.coordinate(character, "getX");
        frame.camY = GameAccess.coordinate(character, "getY");
        frame.camZ = GameAccess.coordinate(character, "getZ");
        frame.viewYaw = IsoCameraMath.YAW;
        frame.viewPitch = IsoCameraMath.PITCH;
        frame.eyeX = 0f; frame.eyeY = 1.5f; frame.eyeZ = 0f; frame.eyeLean = 0f;
        frame.scene.originX = -frame.camX;
        frame.scene.originY = frame.camZ * 2.4494896f;
        frame.scene.originZ = -frame.camY;
        frame.scene.viewerX = 0f; frame.scene.viewerY = 1.5f; frame.scene.viewerZ = 0f;
        frame.scene.panini = 0f;
        frame.number = ((Number) GameAccess.field("viewpoint.FP", "frameNumber", null)).longValue();
        buildCamera(frame, drawer, player);
        try (PoolPitNativeView nativeView=PoolPitNativeView.begin(cell,character)) {
            Object nativeRenderer = NativeIsoState.refresh(cell, character, player);
            frame.viewYaw += (float) Math.toRadians(captureYaw);
            NativeHandView.capture(drawer.handView, character);
            boolean roomsBefore = Settings.rooms;
            CAPTURING.set(true);
            SELF.set(character);
            drawer.containerHighlight.begin();
            drawer.objects.beginCapture(frame);
            try {
                Settings.rooms = false; // Portal visibility is meant for a camera inside the room.
                GameAccess.call("viewpoint.environment.Weather", "snapshot", null,
                        new Class<?>[] { Frame.class, GameAccess.type("zombie.iso.IsoCell") }, frame, cell);
                Atmosphere.apply(frame.scene);
                Daylight.prepare(frame.scene);
                GameAccess.call("viewpoint.light.Flashlight", "snapshot", null,
                        new Class<?>[] { Frame.class, GameAccess.type("zombie.characters.IsoGameCharacter") }, frame, character);
                GameAccess.call("viewpoint.FrameFill", "captureWorld", null,
                        new Class<?>[] { Frame.class, GameAccess.type("zombie.iso.IsoCell"),
                                GameAccess.type("zombie.characters.IsoGameCharacter"), int.class }, frame, cell, character, player);
                frame.scene.shadowReach = Math.max(64f, drawer.effects.distance());
                frame.scene.farCount = 0;
                frame.scene.shellCount = 0;
                IsometricVisibility.apply(frame, cell, player);
                drawer.reflections.capture(frame, cell, player);
                drawer.nativeTrees.reset();
                drawer.nativeTrees.prepare(frame);
                drawer.nativeView.capture(frame, cell, player, nativeRenderer,drawer.nativeTrees);
                drawer.nativeTrees.rotation = drawer.rotation;
                drawer.nativeTrees.capture(nativeRenderer,player);
                drawer.nativeVision.capture(frame);
                drawer.nativeVision.rotation = drawer.rotation;
                NativeCursorPreview.capture(frame, cell, player);
                NativeIsoState.complete(nativeRenderer, player);
                drawer.containerHighlight.resolve(frame);
            } finally {
                NativeObjectState.endCapture();
                NativeContainerHighlight.end();
                Settings.rooms = roomsBefore;
                CAPTURING.remove();
                SELF.remove();
            }
            return nativeRenderer;
        }
    }

    private static void buildCamera(Frame frame, IsoDrawer drawer, int player) {
        int w = ((Number) GameAccess.call("zombie.iso.IsoCamera", "getScreenWidth", null, new Class<?>[] { int.class }, player)).intValue();
        int h = ((Number) GameAccess.call("zombie.iso.IsoCamera", "getScreenHeight", null, new Class<?>[] { int.class }, player)).intValue();
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        float zoom = ((Number) GameAccess.call("zombie.core.Core", "getZoom", core, new Class<?>[] { int.class }, player)).floatValue();
        double px = screen("XToScreenExact", frame.camX, frame.camY, frame.camZ, player) / zoom;
        double py = screen("YToScreenExact", frame.camX, frame.camY, frame.camZ, player) / zoom;
        double ex = screen("XToScreenExact", frame.camX + 1, frame.camY, frame.camZ, player) / zoom - px;
        double ey = screen("YToScreenExact", frame.camX + 1, frame.camY, frame.camZ, player) / zoom - py;
        double sx = screen("XToScreenExact", frame.camX, frame.camY + 1, frame.camZ, player) / zoom - px;
        double sy = screen("YToScreenExact", frame.camX, frame.camY + 1, frame.camZ, player) / zoom - py;
        float yaw = RotationCamera.yawDegrees();
        IsoCameraMath.Fit fit = RotationCamera.rotateFit(IsoCameraMath.fit(frame.camX, frame.camY,
                px, py, ex, ey, sx, sy, w, h), frame.camX, frame.camY, yaw);
        captureFit = fit; captureCamX = frame.camX; captureCamY = frame.camY;
        captureWidth = w; captureHeight = h;
        captureYaw = yaw;
        IsoCameraMath.matrices(fit, frame.camX, frame.camY, w, h, frame.scene.projection, drawer.view, drawer.eye, yaw);
        drawer.rotation = RotationCamera.capture(frame, drawer.view, w, h, zoom);
        frame.scene.cameraCut = frame.number != lastCapturedFrame + 1
                || IsometricFrameGeneration.rotationCut(lastYaw,yaw,IsometricControls.frameGeneration())
                || Math.abs(fit.halfHeight() - lastHalfHeight) > .005f * fit.halfHeight();
        lastYaw = yaw;
        lastCapturedFrame = frame.number;
        lastHalfHeight = fit.halfHeight();
    }

    private static double screen(String name, float x, float y, float z, int player) {
        return ((Number) GameAccess.call("zombie.iso.IsoUtils", name, null,
                new Class<?>[] { float.class, float.class, float.class, int.class }, x, y, z, player)).doubleValue();
    }

    private static void captureMotion(Frame frame,Object cell,Object character) {
        frame.scene.originX=-frame.camX;frame.scene.originY=frame.camZ*2.4494896f;frame.scene.originZ=-frame.camY;
        CAPTURING.set(true);NATIVE_SHADOW_CAPTURE.set(true);SELF.set(character);
        try {
            viewpoint.models.ModelMotion.begin(frame.scene.models);
            Characters.snapshot(frame,(zombie.iso.IsoCell)cell,(zombie.characters.IsoGameCharacter)character);
            Characters.vehicles(frame,(zombie.iso.IsoCell)cell,(zombie.characters.IsoGameCharacter)character);
            frame.scene.models.sealBaked();
            viewpoint.models.ModelMotion.end(frame.scene.models);
        } finally {CAPTURING.remove();NATIVE_SHADOW_CAPTURE.remove();SELF.remove();}
    }

    private static void captureShadows(Frame frame, Object cell, Object character, int player, float distance) {
        frame.scene.originX = -frame.camX;
        frame.scene.originY = frame.camZ * 2.4494896f;
        frame.scene.originZ = -frame.camY;
        frame.scene.viewerX = 0f; frame.scene.viewerY = 1.5f; frame.scene.viewerZ = 0f;
        frame.scene.shadowReach = Math.max(64f, distance);
        frame.eyeX = 0f; frame.eyeY = 1.5f; frame.eyeZ = 0f;
        frame.scene.projection.identity();
        boolean roomsBefore = Settings.rooms;
        captureRadius = distance + 32f;
        CAPTURING.set(true);
        NATIVE_SHADOW_CAPTURE.set(true);
        SELF.set(character);
        try {
            Settings.rooms = false;
            prepareEnvironment(frame, cell);
            GameAccess.call("viewpoint.light.Flashlight","snapshot",null,
                    new Class<?>[]{Frame.class,GameAccess.type("zombie.characters.IsoGameCharacter")},frame,character);
            GameAccess.call("viewpoint.FrameFill", "captureWorld", null,
                    new Class<?>[] { Frame.class, GameAccess.type("zombie.iso.IsoCell"),
                            GameAccess.type("zombie.characters.IsoGameCharacter"), int.class }, frame, cell, character, player);
            frame.scene.shadowReach = Math.max(64f, distance);
            frame.scene.farCount = 0;
            frame.scene.shellCount = 0;
        } finally {
            Settings.rooms = roomsBefore;
            CAPTURING.remove();
            NATIVE_SHADOW_CAPTURE.remove();
            SELF.remove();
        }
    }

    private static void prepareEnvironment(Frame frame, Object cell) {
        frame.scene.originX = -frame.camX;
        frame.scene.originY = frame.camZ * 2.4494896f;
        frame.scene.originZ = -frame.camY;
        frame.scene.viewerX = 0f; frame.scene.viewerY = 1.5f; frame.scene.viewerZ = 0f;
        frame.eyeX = 0f; frame.eyeY = 1.5f; frame.eyeZ = 0f;
        GameAccess.call("viewpoint.environment.Weather", "snapshot", null,
                new Class<?>[] { Frame.class, GameAccess.type("zombie.iso.IsoCell") }, frame, cell);
        Atmosphere.apply(frame.scene);
        Daylight.prepare(frame.scene);
        Daylight.snapshot(frame.scene);
    }

    public static void release(Frame frame) {
        Characters.release(frame); Corpses.release(frame); Models.release(frame);
    }
    public static String blockedReason(String pack, boolean iris, int upscaler, boolean reflections, boolean generation) {
        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        if (iris) blockers.add("Minecraft/Iris pack is active");
        if (!IsometricShaders.supports(pack)) blockers.add("shader pack is " + pack + " (use Default, Vivid, Somber, Library or True Weathers)");
        if (upscaler < 0 || upscaler > 3) blockers.add("unsupported upscaling mode");
        if (reflections) blockers.add("SSR is on");
        return blockers.isEmpty() ? null : String.join("; ", blockers);
    }
    public static String requestedBlockReason() {
        ShaderPacks.Active active = ShaderPacks.active();
        return blockedReason(active.pack.id, IrisPacks.active() != null, active.pipeline.upscaler,
                active.pipeline.reflections, Graphics.FRAME_GENERATION.get());
    }
    public static void fail(String stage, Throwable error) {
        if (failed) return;
        failed = true;
        nativeShadowsReady=false;
        submitted = false;
        modelSceneReady = false;
        System.out.println("[VPISO] effects stopped while " + stage + "; native world remains active:");
        error.printStackTrace(System.out);
    }
    private IsometricBridge() {}
}
