package vpiso;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.SceneData;

/** One game-thread native-visibility snapshot for one owned render frame. */
public final class NativeViewData {
    private static final int UNIT = 142;
    private static final int MAX_SIDE = 192;
    private static final int MAX_LAYERS = 64;
    private static final int CAP_N = 4;
    private static final int CAP_W = 8;
    private static final ThreadLocal<NativeViewData> ACTIVE = new ThreadLocal<>();

    /** R=building blackout, G/B=atlas column/4, A=128|north-capable64|west-capable32. */
    public static final String GLSL = """
        uniform sampler2DArray vpIsoNativeTiles;
        uniform float vpIsoNativeOn;
        uniform vec3 vpIsoNativeOrigin;
        uniform vec3 vpIsoNativeSize;
        uniform vec3 vpIsoNativeCam;

        vec4 vpIsoNativeRule(vec3 worldXYZ) {
            if (vpIsoNativeOn < 0.5) return vec4(1.0, 207.0/255.0, 224.0/255.0, 1.0);
            ivec3 p = ivec3(floor(worldXYZ - vpIsoNativeOrigin));
            if (any(lessThan(p, ivec3(0))) || any(greaterThanEqual(p, ivec3(vpIsoNativeSize))))
                return vec4(1.0, 207.0/255.0, 224.0/255.0, 0.0);
            return texelFetch(vpIsoNativeTiles, p, 0);
        }

        float vpIsoNativeDark(vec3 scenePos) {
            if (vpIsoNativeOn < 0.5) return 1.0;
            vec3 worldXYZ = vec3(vpIsoNativeCam.x - scenePos.x,
                                 vpIsoNativeCam.y - scenePos.z,
                                 vpIsoNativeCam.z + scenePos.y / 2.4494896);
            // Inverse projection can put a rasterized floor a few ulps below its own
            // level. Use VP's storey tolerance before flooring, or blackout samples
            // alternate between this room and the floor below as the camera moves.
            worldXYZ.z += 0.0001;
            ivec3 p = ivec3(floor(worldXYZ - vpIsoNativeOrigin));
            if (any(lessThan(p.xy, ivec2(0))) || any(greaterThanEqual(p.xy, ivec2(vpIsoNativeSize.xy))))
                return 1.0;
            p.z = min(p.z, int(vpIsoNativeSize.z) - 1);
            // Only absent squares fall back. A blacked-out upper floor must stay black.
            for (int i = 0; i < 64 && p.z >= 0; ++i, --p.z) {
                vec4 rule = texelFetch(vpIsoNativeTiles, p, 0);
                if (rule.a > 0.5) return rule.r;
            }
            return 1.0;
        }
        """;

    private int minX, minY, minLevel, width, height, layers;
    private float camX, camY, camZ;
    private ByteBuffer pixels;
    private byte[] topology = new byte[0];
    private final BitSet requested = new BitSet();
    private final NativeWeather weather = new NativeWeather();
    private int sampledSquares;
    private boolean ready;
    private boolean dirty;
    private int texture;
    private int uploadedWidth, uploadedHeight, uploadedLayers;
    private boolean bound;
    private int previousActive, previousTexture, previousSampler;

    /** Called after the native cutaway/blackout prepass, before queueing this frame. */
    public void capture(Frame frame, Object cell, int playerIndex, Object nativeRenderer) {
        capture(frame,cell,playerIndex,nativeRenderer,null);
    }

    public void capture(Frame frame, Object cell, int playerIndex, Object nativeRenderer, NativeTrees trees) {
        ready = false;
        sampledSquares = 0;
        SceneData scene = frame.scene;
        int loX = Integer.MAX_VALUE, loY = Integer.MAX_VALUE;
        int hiX = Integer.MIN_VALUE, hiY = Integer.MIN_VALUE;
        int playerLevel = (int) Math.floor(frame.camZ);
        int loZ = playerLevel, hiZ = playerLevel;
        for (int i = 0; i < scene.meshCount; i++) {
            if ((scene.meshFlags[i] & (SceneData.VISIBLE | SceneData.NEAR)) == 0) continue;
            int x = Math.round(frame.camX - scene.meshOffsets[i * 3]);
            int y = Math.round(frame.camY - scene.meshOffsets[i * 3 + 2]);
            loX = Math.min(loX, x);
            loY = Math.min(loY, y);
            hiX = Math.max(hiX, x + 7);
            hiY = Math.max(hiY, y + 7);
            loZ = Math.min(loZ, scene.meshes[i].level);
            hiZ = Math.max(hiZ, scene.meshes[i].level);
        }
        if (loX == Integer.MAX_VALUE) return;

        // The one-tile halo supplies native N/S/E/W endpoint decisions.
        loX--; loY--; hiX++; hiY++;
        minX = boundedStart(loX, hiX, (int) Math.floor(frame.camX));
        minY = boundedStart(loY, hiY, (int) Math.floor(frame.camY));
        width = Math.min(MAX_SIDE, hiX - loX + 1);
        height = Math.min(MAX_SIDE, hiY - loY + 1);
        minLevel = Math.max(-32, loZ);
        int maxLevel = Math.min(31, hiZ);
        layers = Math.min(MAX_LAYERS, maxLevel - minLevel + 1);
        if (layers <= 0) return;
        camX = frame.camX; camY = frame.camY; camZ = frame.camZ;

        int tileCount = width * height * layers;
        int byteCount = tileCount * 4;
        boolean precipitation=scene.rain>0.001f || scene.snow>0.001f;
        weather.capture(precipitation,minX,minY,minLevel,width,height,layers,camX,camY,camZ);
        if (pixels == null || pixels.capacity() < byteCount) pixels = ByteBuffer.allocateDirect(byteCount);
        pixels.clear().limit(byteCount);
        if (topology.length < tileCount) topology = new byte[tileCount];
        else Arrays.fill(topology, 0, tileCount, (byte) 0);
        // Query only visible chunk columns and their halo. Keep intermediate floors:
        // an existing blacked-out upper floor must not fall back to the floor below.
        for (int offset = 0; offset < byteCount; offset += 4) pixels.putInt(offset, 0);
        requested.clear();
        for (int i = 0; i < scene.meshCount; i++) {
            if ((scene.meshFlags[i] & (SceneData.VISIBLE | SceneData.NEAR)) == 0) continue;
            int x = Math.round(frame.camX - scene.meshOffsets[i * 3]) - minX;
            int y = Math.round(frame.camY - scene.meshOffsets[i * 3 + 2]) - minY;
            int x1 = Math.max(0, x - 1), x2 = Math.min(width, x + 9);
            if (x1 >= x2) continue;
            for (int row = Math.max(0, y - 1); row < Math.min(height, y + 9); row++) {
                int start = row * width;
                requested.set(start + x1, start + x2);
            }
        }
        // Every visible mesh requests the same complete vertical column. Union the
        // footprints once, then expand that union, instead of repeating all levels
        // for every mesh on every floor. Intermediate floors and halos are unchanged.
        int plane = width * height;
        for (int start = requested.nextSetBit(0); start >= 0 && start < plane;) {
            int end = Math.min(plane, requested.nextClearBit(start));
            for (int layer = 1; layer < layers; layer++) requested.set(layer * plane + start, layer * plane + end);
            start = requested.nextSetBit(end);
        }
        Bindings api = BindingsHolder.API;
        long now = System.currentTimeMillis();
        try {
            for (int index = requested.nextSetBit(0); index >= 0; index = requested.nextSetBit(index + 1)) {
                        int x = index % width, y = index / width % height, z = index / (width * height);
                        sampledSquares++;
                        Object square = (Object) api.square.invokeExact(cell, minX + x, minY + y, minLevel + z);
                        int offset = index * 4;
                        if (square == null) {
                            pixels.putInt(offset, 0);
                            continue;
                        }
                        if (precipitation) weather.square(index,(boolean)api.roof.invokeExact(square) || !(boolean)api.outside.invokeExact(square));
                        int caps = NativeWallCaps.caps(square);
                        boolean capN = (caps & CAP_N) != 0;
                        boolean capW = (caps & CAP_W) != 0;
                        int flags = (int) api.flags.invokeExact(square, playerIndex, now);
                        topology[index] = (byte) ((flags & 3) | (capN ? CAP_N : 0) | (capW ? CAP_W : 0));
                        pixels.put(offset, (byte) readBlackout(api, square, playerIndex, nativeRenderer));
                        Object treeObject = (Object) api.tree.invokeExact(square);
                        int tree = treeObject == null ? 0 : 31;
                        if (treeObject != null && trees != null) trees.add(square);
                        pixels.put(offset + 3, (byte) (128 | (capN ? 64 : 0) | (capW ? 32 : 0) | tree));
            }
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot capture native sight and wall-cutaway state", error);
        }
        for (int index = requested.nextSetBit(0); index >= 0; index = requested.nextSetBit(index + 1)) {
                    int x = index % width, y = index / width % height;
                    if (pixels.get(index * 4 + 3) == 0) continue;
                    int c = topology[index];
                    int e = x + 1 < width ? topology[index + 1] : 0;
                    int w = x > 0 ? topology[index - 1] : 0;
                    int n = y > 0 ? topology[index - width] : 0;
                    int s = y + 1 < height ? topology[index + width] : 0;
                    pixels.put(index * 4 + 1, (byte) (northColumn(c, e, w, n) / 4));
                    pixels.put(index * 4 + 2, (byte) (westColumn(c, s, n, w) / 4));
        }
        dirty = true;
        ready = true;
    }

    public int sampledSquares() { return sampledSquares; }

    private static int boundedStart(int lo, int hi, int center) {
        if (hi - lo + 1 <= MAX_SIDE) return lo;
        return Math.max(lo, Math.min(hi - MAX_SIDE + 1, center - MAX_SIDE / 2));
    }

    private static int readBlackout(Bindings api, Object square, int player, Object renderer) throws Throwable {
        // Refresh JNI lighting for native sprite consumers, without using bSeen as a color gate.
        // B42's FBO floors use vertex light; current sight is drawn by NativeVision's native
        // VisibilityPolygon2 pass. A nearest-tile seen/darkMulti post mask adds black diamonds.
        boolean refreshed = (boolean) api.seen.invokeExact(square, player);
        float value = 1f;
        if (renderer != null && (boolean) api.blacked.invokeExact(renderer, square)) {
            float fade = (float) api.fade.invokeExact(renderer, square);
            value = Float.isFinite(fade) ? 1f - Math.max(0f, Math.min(1f, fade)) : 1f;
        }
        return Math.round(value * 255f);
    }

    /** Binds this snapshot on the render thread; every successful call needs end(). */
    public void begin() {
        if (bound || ACTIVE.get() != null) throw new IllegalStateException("Native view snapshots cannot nest");
        if (!ready) return;
        previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
        previousTexture = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
        previousSampler = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, UNIT);
        bound = true;
        try {
            if (texture == 0) texture = GL11.glGenTextures();
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, texture);
            GL33.glBindSampler(UNIT, 0);
            if (dirty) upload();
            ACTIVE.set(this);
            weather.begin();
            GL13.glActiveTexture(previousActive);
        } catch (Throwable error) {
            end();
            throw error;
        }
    }

    private void upload() {
        int[] names = { GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH,
                GL12.GL_UNPACK_IMAGE_HEIGHT, GL11.GL_UNPACK_SKIP_PIXELS,
                GL11.GL_UNPACK_SKIP_ROWS, GL12.GL_UNPACK_SKIP_IMAGES };
        int[] values = new int[names.length];
        for (int i = 0; i < names.length; i++) values[i] = GL11.glGetInteger(names[i]);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            for (int name : names) GL11.glPixelStorei(name, name == GL11.GL_UNPACK_ALIGNMENT ? 1 : 0);
            if (uploadedWidth != width || uploadedHeight != height || uploadedLayers != layers) {
                GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA8,
                        width, height, layers, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                uploadedWidth = width; uploadedHeight = height; uploadedLayers = layers;
            } else {
                GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, 0,
                        width, height, layers, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            }
            dirty = false;
        } finally {
            for (int i = 0; i < names.length; i++) GL11.glPixelStorei(names[i], values[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    public void end() {
        if (!bound) return;
        ACTIVE.remove();
        weather.end();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
            GL33.glBindSampler(UNIT, previousSampler);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, previousTexture);
        } finally {
            GL13.glActiveTexture(previousActive);
            bound = false;
        }
    }

    public static void onUse(GlProgram program) {
        if (!program.has("vpIsoNativeOn")) return;
        // Even disabled sampler arrays must not alias a sampler2D's default unit0.
        program.setInt("vpIsoNativeTiles", UNIT);
        NativeViewData data = ACTIVE.get();
        program.set("vpIsoNativeOn", data == null ? 0f : 1f);
        if (data != null) {
            program.set("vpIsoNativeOrigin", data.minX, data.minY, data.minLevel);
            program.set("vpIsoNativeSize", data.width, data.height, data.layers);
            program.set("vpIsoNativeCam", data.camX, data.camY, data.camZ);
        }
    }

    private static int northColumn(int c, int e, int w, int n) {
        int column = 700;
        if ((c & 1) != 0) {
            if ((e & 1) == 0 && (e & CAP_N) != 0) column = 444;
            if ((w & 1) == 0 && (w & CAP_N) != 0) column = 956;
            else if ((n & 2) == 0 && (n & CAP_W) != 0) column = 956;
        } else column = (e & 1) == 0 ? 828 : 956;
        return column;
    }

    private static int westColumn(int c, int s, int n, int w) {
        int column = 512;
        if ((s & 2) != 0) {
            if ((n & 2) == 0 && (n & CAP_W) != 0) column = 768;
            else if ((w & 1) == 0 && (w & CAP_N) != 0) column = 768;
            else if ((c & 1) == 0 && (c & CAP_N) != 0) column = 768;
        } else {
            if ((c & 2) == 0) column = 896;
            else if ((n & 2) == 0 && (n & CAP_W) != 0) column = 768;
            else if ((s & CAP_W) != 0) column = 256;
            else if ((w & 1) == 0 && (w & CAP_N) != 0) column = 768;
        }
        return column;
    }

    private static final class BindingsHolder {
        static final Bindings API = new Bindings();
    }

    private static final class Bindings {
        final MethodHandle square, seen, flags, capN, capW, blacked, fade;
        final MethodHandle tree;
        final MethodHandle roof, outside;

        Bindings() {
            try {
                Class<?> sq = Class.forName("zombie.iso.IsoGridSquare");
                Class<?> cell = Class.forName("zombie.iso.IsoCell");
                Class<?> renderer = Class.forName("zombie.iso.fboRenderChunk.FBORenderCell");
                Class<?> treeType = Class.forName("zombie.iso.objects.IsoTree");
                square = handle(cell, "getGridSquare", Object.class, new Class<?>[] {int.class, int.class, int.class},
                        Object.class, int.class, int.class, int.class);
                seen = handle(sq, "isSeen", boolean.class, new Class<?>[] {int.class}, Object.class, int.class);
                flags = handle(sq, "getPlayerCutawayFlag", int.class, new Class<?>[] {int.class, long.class},
                        Object.class, int.class, long.class);
                capN = handle(sq, "hasCutawayCapableWallNorth", boolean.class, new Class<?>[] {sq}, Object.class);
                capW = handle(sq, "hasCutawayCapableWallWest", boolean.class, new Class<?>[] {sq}, Object.class);
                blacked = handle(renderer, "isBlackedOutBuildingSquare", boolean.class, new Class<?>[] {sq}, Object.class, Object.class);
                fade = handle(renderer, "getBlackedOutRoomFadeRatio", float.class, new Class<?>[] {sq}, Object.class, Object.class);
                tree = handle(sq,"getTree",Object.class,new Class<?>[0],Object.class);
                outside = handle(sq,"isOutside",boolean.class,new Class<?>[0],Object.class);
                roof = MethodHandles.lookup().unreflectGetter(sq.getField("haveRoof"))
                        .asType(MethodType.methodType(boolean.class,Object.class));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("This game version has no compatible native sight API", error);
            }
        }

        private static MethodHandle handle(Class<?> owner, String name, Class<?> result,
                                            Class<?>[] arguments, Class<?>... adaptedArguments)
                throws ReflectiveOperationException {
            Method method = owner.getDeclaredMethod(name, arguments);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(result, adaptedArguments));
        }
    }
}
