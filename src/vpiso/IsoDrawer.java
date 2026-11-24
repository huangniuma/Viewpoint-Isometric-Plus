package vpiso;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import viewpoint.core.Frame;
import viewpoint.models.Characters;
import viewpoint.models.Corpses;
import viewpoint.models.Models;
import viewpoint.render.Retirement;
import viewpoint.render.CameraClear;
import viewpoint.render.WorldRenderer;
import zombie.core.textures.TextureDraw;

/** One drawer per SpriteRenderer state: scene data is never shared with a queued frame. */
public final class IsoDrawer extends TextureDraw.GenericDrawer {
    public final Matrix4f view = new Matrix4f();
    public final Matrix4f handView = new Matrix4f();
    public final float[] eye = new float[3];
    public final NativeViewData nativeView = new NativeViewData();
    public final NativeVision nativeVision = new NativeVision();
    public final NativeTrees nativeTrees = new NativeTrees();
    public final NativeContainerHighlight containerHighlight = new NativeContainerHighlight();
    public final ReflectionVisibility reflections = new ReflectionVisibility();
    public final NativeObjectState objects = new NativeObjectState();
    public IsometricEffects effects = IsometricEffects.DEFAULT;
    public boolean nativeWater;
    public RotationCamera.Snapshot rotation;
    private final Frame frame;

    IsoDrawer(Frame frame) { this.frame = frame; }

    @Override public void render() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean defaultShader = (Boolean) GameAccess.field("zombie.core.DefaultShader", "isActive", null);
        boolean hidden = WorldRenderer.hidden;
        Object textureId = GameAccess.field("zombie.core.textures.Texture", "lastTextureID", null);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        // Both model modes use native water, followed by the optional water-only overlay.
        IsometricBridge.modelWorldRendering(frame, effects, nativeWater);
        containerHighlight.beginRender();
        RotationCamera.Snapshot previousRotation = RotationLayers.begin(rotation);
        ReflectionVisibility previousReflections = reflections.begin();
        objects.beginRender();
        try {
            nativeView.begin();
            NativeWallCutaway.begin();
            Retirement.collect();
            WorldRenderer.cullFromPlayer = false;
            WorldRenderer.freeCamera = false;
            WorldRenderer.handFromPlayer = true;
            // VP can leave its loading frame routed to HiddenOutput. This drawer owns
            // a visible native-world frame even before the first manual VP view switch.
            WorldRenderer.hidden = false;
            WorldRenderer.handView.set(handView);
            CameraClear.frame(false, false, false, eye, 0f, 0f, 0f);
            if (!WorldRenderer.begin(frame.scene, frame.number, 1f, view, eye[0], eye[1], eye[2])) {
                throw new IllegalStateException("VP renderer could not start");
            }
            Models.release(frame);
            WorldRenderer.drawOutlines();
            WorldRenderer.drawTranslucent(frame.scene);
            objects.finishPick();
            // Rain haze reads the finished visible depth and blends directly into HDR.
            // Drawing native trees afterwards would erase that haze from their colour.
            nativeTrees.renderWorld(frame);
            TrueWeatherCompat.beforeWeather(frame.scene);
            // Weather emits around the player; the scene matrix still uses the remote eye.
            WorldRenderer.drawWeather(frame.scene, 0f, 1.5f, 0f);
            WorldRenderer.finish(frame.scene);
            containerHighlight.draw(frame);
            nativeVision.writeDepth();
        } catch (Throwable error) {
            IsometricBridge.fail("rendering", error);
            WorldRenderer.restoreOutput();
        } finally {
            objects.endRender();
            ReflectionVisibility.restore(previousReflections);
            NativeContainerHighlight.endRender();
            NativeWallCutaway.end();
            nativeView.end();
            RotationLayers.end(previousRotation);
            IsometricBridge.rendering(null);
            WorldRenderer.hidden = hidden;
            Retirement.drawn(frame.number);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glBindVertexArray(vao);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
            GameAccess.staticCall("zombie.core.ShaderHelper", "forgetCurrentlyBound");
            GameAccess.set("zombie.core.DefaultShader", "isActive", null, defaultShader);
            GameAccess.set("zombie.core.textures.Texture", "lastTextureID", null, textureId);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            Object ring = GameAccess.field("zombie.core.SpriteRenderer", "ringBuffer", null);
            GameAccess.set(ring.getClass().getName(), "restoreVbos", ring, true);
            GameAccess.set(ring.getClass().getName(), "restoreBoundTextures", ring, true);
            GameAccess.staticCall("zombie.core.opengl.GLStateRenderThread", "startFrame");
        }
    }

    @Override public void postRender() {
        nativeTrees.release();
        Characters.release(frame);
        Corpses.release(frame);
        Models.release(frame);
    }
}
