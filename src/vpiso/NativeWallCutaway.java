package vpiso;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import viewpoint.platform.GlProgram;

/** Samples the game's own wall mask for ordinary N/W faces; never changes shadow geometry. */
public final class NativeWallCutaway {
    public static final int UNIT = 143; // Pinned VP 0.1.6.2 uses units 0..141.
    public static final String GLSL = """
        uniform sampler2D vpIsoWallMask;
        uniform vec2 vpIsoWallMaskSize;
        uniform float vpIsoRotated;
        uniform mat4 vpIsoRotatedPV;
        uniform vec2 vpIsoRotatedScreen;
        void vpIsoCutWall(vec3 scenePos, vec3 localPos, vec3 normal, int flags) {
          if (vpIsoNativeOn < 0.5) return;
          vec3 world = vec3(vpIsoNativeCam.x - scenePos.x,
              vpIsoNativeCam.y - scenePos.z,
              vpIsoNativeCam.z + (scenePos.y - localPos.y) / 2.4494896);
          float t;
          bool north = abs(normal.z) > 0.9;
          bool west = abs(normal.x) > 0.9;
          if (!north && !west) return;
          if (north ? abs(world.y - floor(world.y + 0.5)) > 0.08
                    : abs(world.x - floor(world.x + 0.5)) > 0.08) return;
          if (north) { world.y = floor(world.y + 0.5); t = fract(world.x); }
          else { world.x = floor(world.x + 0.5); t = fract(world.y); }
          world.z = floor(world.z + 0.5);
          vec4 rule = vpIsoNativeRule(world);
          int capabilities = int(round(rule.a * 255.0));
          if (north ? (capabilities & 64) == 0 : (capabilities & 32) == 0) return;
          if (vpIsoRotated > 0.5) {
            // Keep native room/floor decisions. Cut only walls between the new camera
            // and the player's body; the original N/W atlas describes the fixed view.
            vec4 wallClip = vpIsoRotatedPV * vec4(scenePos, 1.0);
            vec4 bodyClip = vpIsoRotatedPV * vec4(0.0, 1.1, 0.0, 1.0);
            vec2 separation = (wallClip.xy / wallClip.w - bodyClip.xy / bodyClip.w)
                * vpIsoRotatedScreen * 0.5 / vec2(110.0, 155.0);
            if (wallClip.z / wallClip.w < bodyClip.z / bodyClip.w - 0.00001
                && dot(separation, separation) < 1.0) discard;
            return;
          }
          float h = localPos.y / 2.4494896;
          if (h < -0.02 || h > 1.02) return;
          float column = round((north ? rule.g : rule.b) * 255.0) * 4.0;
          vec2 pixel = vec2(north ? 64.0*t : 2.0 + 64.0*(1.0-t),
              194.0 + 32.0*t - 192.0*h);
          pixel = clamp(pixel, vec2(0.5), vec2(65.5,225.5));
          float row = (flags & 16384) != 0 ? 904.0 : (flags & 1024) != 0 ? 226.0 : 0.0;
          if (texture(vpIsoWallMask, (vec2(column,row) + pixel) / vpIsoWallMaskSize).r < 0.5) discard;
        }
        """;
    private static int texture, width, height;
    private static int oldActive, oldTexture, oldSampler;
    private static boolean bound;

    public static void begin() {
        if (UNIT >= GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)) {
            throw new IllegalStateException("No spare texture unit for native wall cutaways");
        }
        oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
        oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        oldSampler = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, UNIT);
        bound = true;
        try {
            if (texture == 0) loadMask();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL33.glBindSampler(UNIT, 0);
        } finally {
            GL13.glActiveTexture(oldActive);
        }
    }

    private static void loadMask() {
        Object fs = GameAccess.field("zombie.ZomboidFileSystem", "instance", null);
        String path = (String) GameAccess.call("zombie.ZomboidFileSystem", "getString", fs,
                new Class<?>[] { String.class }, "media/wallcutaways.png");
        loadMask(Path.of(path));
    }

    private static void loadMask(Path path) {
        try {
            var image = ImageIO.read(path.toFile());
            if (image == null) throw new IllegalStateException("Cannot decode native wall mask: " + path);
            width = image.getWidth(); height = image.getHeight();
            ByteBuffer red = BufferUtils.createByteBuffer(width * height);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                red.put((byte) (image.getRGB(x, y) >>> 16));
            }
            red.flip();
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            try {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, width, height, 0,
                        GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, red);
            } finally {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
                GL11.glPopClientAttrib();
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12_CLAMP_TO_EDGE);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Native wall cutaway texture is unavailable", e);
        }
    }
    private static final int GL12_CLAMP_TO_EDGE = 33071;

    public static void onUse(GlProgram program) {
        if (!program.has("vpIsoWallMask")) return;
        program.setInt("vpIsoWallMask", UNIT);
        RotationCamera.Snapshot rotation = RotationLayers.current();
        program.set("vpIsoRotated", rotation == null ? 0f : 1f);
        if (rotation != null) {
            program.set("vpIsoRotatedPV", rotation.projection().mul(rotation.view()));
            program.set("vpIsoRotatedScreen", rotation.width() * rotation.zoom(), rotation.height() * rotation.zoom());
        }
        if (bound) program.set("vpIsoWallMaskSize", (float) width, (float) height);
    }

    public static void end() {
        if (!bound) return;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
        GL33.glBindSampler(UNIT, oldSampler);
        GL13.glActiveTexture(active);
        bound = false;
    }

    private NativeWallCutaway() {}
}
