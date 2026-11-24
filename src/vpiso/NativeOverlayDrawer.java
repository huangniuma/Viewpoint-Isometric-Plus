package vpiso;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import viewpoint.core.Frame;
import viewpoint.render.Retirement;
import zombie.core.textures.TextureDraw;

/** One receiver/scene/options snapshot per game sprite buffer. */
public final class NativeOverlayDrawer extends TextureDraw.GenericDrawer {
    private static final NativeOverlay overlay = new NativeOverlay();
    private static final NativeVPWaterOverlay waterOverlay = new NativeVPWaterOverlay();
    private static final NativeGrassOverlay grassOverlay = new NativeGrassOverlay();
    private static final NativeSnowOverlay snowOverlay = new NativeSnowOverlay();
    private final int[] samplers = new int[19];
    private final Matrix4f[] matrices = { new Matrix4f(), new Matrix4f(), new Matrix4f() };
    private final float[] texels = new float[3];
    private final Frame frame;
    public final NativeReceiver receiver = new NativeReceiver();
    public final NativeReceiver shadowReceiver = new NativeReceiver();
    public final Matrix4f handView = new Matrix4f();
    public final NativeVPWaterOverlay.Snapshot water = new NativeVPWaterOverlay.Snapshot();
    public final NativeRelightMask relightMask=new NativeRelightMask();
    public IsometricEffects effects = IsometricEffects.DEFAULT;
    public boolean releaseScene = true;
    public float waterDepthBias = .001f;
    public RotationCamera.Snapshot rotation;

    public NativeOverlayDrawer(Frame frame) { this.frame = frame; }

    @Override public void render() {
        int nativeGround=NativeGround.texture();
        int nativeOcclusion=NativeGround.occlusionTexture();
        int nativeFloor=NativeGround.floorTexture();
        overlay.ground(nativeGround);
        overlay.metadata(nativeOcclusion);
        overlay.floor(NativeGround.floorTexture());
        overlay.relight(NativeGround.relightTexture(),NativeGround.relightValidity(),relightMask);
        NativeGround.finish();
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < samplers.length; i++) samplers[i] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, i < 3 ? i : i + 61);
        boolean defaultShader = (Boolean) GameAccess.field("zombie.core.DefaultShader", "isActive", null);
        Object shaderBinding = GameAccess.field("zombie.core.ShaderHelper", "currentlyBound", null);
        Object textureId = GameAccess.field("zombie.core.textures.Texture", "lastTextureID", null);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        try {
            Retirement.collect();
            if (effects.water()) waterOverlay.draw(frame, receiver, water, true, waterDepthBias, rotation);
            if (effects.grass() && nativeOcclusion!=0) grassOverlay.draw(frame, receiver,nativeGround,nativeOcclusion);
            if (effects.snow() && nativeOcclusion!=0) snowOverlay.draw(frame,shadowReceiver,nativeGround,nativeOcclusion,nativeFloor);
            if (!effects.needsShadows() && !effects.needsStyle()) {
                return;
            }
            overlay.capture(effects.needsShadows());
            if (effects.needsShadows()) IsometricBridge.nativeDepthAvailable(overlay.hasDepth());
            int count = 0, map = 0, size = 0;
            float range = 1f;
            for (Matrix4f matrix : matrices) matrix.identity();
            java.util.Arrays.fill(texels, 0f);
            if (effects.needsShadows() && overlay.hasDepth()) {
                IsometricBridge.rendering(frame);
                int layers = effects.distance() <= 22f ? 1 : effects.distance() <= 64f ? 2 : 3;
                if (NativeShadowMaps.draw(frame.scene, effects.resolution(), layers,overlay.surfaces(shadowReceiver),effects,handView)) {
                    count = NativeShadowMaps.count(); map = NativeShadowMaps.texture(); size = NativeShadowMaps.size();
                    range = NativeShadowMaps.depthRange();
                    for (int i = 0; i < count; i++) {
                        matrices[i].set(NativeShadowMaps.matrix(i));
                        texels[i] = 2f * NativeShadowMaps.reach(i) / size;
                    }
                }
                IsometricBridge.rendering(null);
            }
            overlay.draw(shadowReceiver, effects, map, matrices, texels, range, size, count, frame.scene.sunStrength);
        } catch (Throwable error) {
            IsometricBridge.fail("rendering", error);
        } finally {
            IsometricBridge.rendering(null);
            Retirement.drawn(frame.number);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glBindVertexArray(vao);
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL20.glUseProgram(program);
            for (int i = 0; i < samplers.length; i++) GL33.glBindSampler(i < 3 ? i : i + 61, samplers[i]);
            // The GPU program and its native cache must agree. Clearing the cache
            // makes the next native setModelViewProjection skip its zoom update.
            GameAccess.set("zombie.core.ShaderHelper", "currentlyBound", null, shaderBinding);
            GameAccess.set("zombie.core.DefaultShader", "isActive", null, defaultShader);
            GameAccess.set("zombie.core.textures.Texture", "lastTextureID", null, textureId);
            GL13.glActiveTexture(active);
            Object ring = GameAccess.field("zombie.core.SpriteRenderer", "ringBuffer", null);
            GameAccess.set(ring.getClass().getName(), "restoreVbos", ring, true);
            GameAccess.set(ring.getClass().getName(), "restoreBoundTextures", ring, true);
            GameAccess.staticCall("zombie.core.opengl.GLStateRenderThread", "startFrame");
        }
    }

    @Override public void postRender() { if (releaseScene) IsometricBridge.release(frame); }
}
