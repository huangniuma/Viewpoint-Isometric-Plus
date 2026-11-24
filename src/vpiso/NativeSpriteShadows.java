package vpiso;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

/** Suppresses native fixed-shadow draws and removes known ground-shadow pixels from private atlases. */
public final class NativeSpriteShadows {
    private static final String TEXTURE = "zombie.core.textures.Texture";
    private static final String ID = "zombie.core.textures.TextureID";
    private static final String CELL = "zombie.iso.fboRenderChunk.FBORenderCell";
    private static final ThreadLocal<Integer> SCOPE = ThreadLocal.withInitial(() -> 0);
    private static final Map<Object, Copy> COPIES = new ConcurrentHashMap<>();
    private static final Map<Object, Atlas> ATLASES = new ConcurrentHashMap<>();
    private static final Map<Object, Copy> PRIVATE_COPIES = new ConcurrentHashMap<>();
    private static final Map<Object, String> NAMES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> SUBMISSIONS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger READY = new AtomicInteger();
    // Exact partial-alpha black shadow coordinates from the shipped PNGs; top-down, row-major bits.
    private static final byte[] HYDRANT = Base64.getDecoder().decode(
            "AAAAADAAAAAAAADABwAAAAAAgB8AAAAAAAA/AAAfAAAAfAAAPgAAAPABAH4AAAAA+oD/AAAA4O0H/wEAAMD/B/4DAACA///DDwAAAP//AzwAAAD/fwDgAAAAPwAAgAcAgP8DAADu////HwAAwP///z8AAADG//8AAAAAgP9/AAAAAAD/AAAAAAAAPgAAAAAAADwAAAAA");
    private static final byte[] POLE = Base64.getDecoder().decode("AADgHwAAgD8A");
    private static volatile boolean selected;
    private static boolean appliedSelected;
    private static Object appliedCell;
    private static int applied;

    private static final class Copy {
        Object source;
        volatile Object texture;
    }
    private static final class Atlas {
        volatile Object id;
    }

    /** Game thread, before the native cell begins baking its chunk textures. */
    public static void prepare(boolean enabled) {
        selected = enabled;
        int next = enabled ? READY.get() : 0;
        Object renderer = GameAccess.field(CELL, "instance", null);
        Object cell = GameAccess.field(CELL, "cell", renderer);
        if (cell == null) return;
        // A mode transition must rebake even before the first private atlas is ready.
        // READY alone is zero on both initial enable and disable in that case.
        if (next == applied && enabled == appliedSelected && cell == appliedCell) return;
        Object state = GameAccess.field("zombie.iso.IsoCamera", "frameState", null);
        int player = ((Number) GameAccess.field("zombie.iso.IsoCamera$FrameState", "playerIndex", state)).intValue();
        boolean cache = (Boolean) GameAccess.field("zombie.iso.fboRenderChunk.FBORenderLevels", "clearCachedSquares", null);
        GameAccess.set("zombie.iso.fboRenderChunk.FBORenderLevels", "clearCachedSquares", null, true);
        try {
            GameAccess.call(CELL, "invalidateAll", renderer, new Class<?>[] { int.class }, player);
            applied = next;
            appliedSelected = enabled;
            appliedCell = cell;
        }
        finally { GameAccess.set("zombie.iso.fboRenderChunk.FBORenderLevels", "clearCachedSquares", null, cache); }
    }

    public static int enter() {
        int previous = SCOPE.get();
        SCOPE.set(previous + 1);
        return previous;
    }
    public static void leave(int previous) { if (previous == 0) SCOPE.remove(); else SCOPE.set(previous); }

    private static boolean suppress() {
        return selected && SCOPE.get()!=0 && !IsometricBridge.capturing() && !IsometricBridge.rendering();
    }

    /** All native character/vehicle/corpse/mannequin blobs converge here; release unsubmitted records. */
    public static boolean suppressBlobs(Object renderer) {
        if(!suppress()) return false;
        try {
            var records=renderer.getClass().getDeclaredField("shadows");records.setAccessible(true);
            var sourcePool=renderer.getClass().getDeclaredField("shadowPool");sourcePool.setAccessible(true);
            List<?> shadows=(List<?>)records.get(renderer);
            Object pool=sourcePool.get(renderer);
            GameAccess.call("zombie.popman.ObjectPool","releaseAll",pool,new Class<?>[]{List.class},shadows);
            shadows.clear();
            return true;
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Native fixed shadow records unavailable",error); }
    }

    /** The atlas, sprite definition, picking mask and every other view retain their original texture. */
    public static Object texture(Object source) {
        if (source == null || !suppress() || PRIVATE_COPIES.containsKey(source)) return source;
        Copy known = COPIES.get(source);
        if (known != null) return known.texture == null ? source : known.texture;
        String name = name(source);
        if (!known(name)) return source;
        int width = integer(source, "getWidth"), height = integer(source, "getHeight");
        if (!matches(name,width,height)) return source;
        Copy copy = new Copy();
        copy.source = source;
        if (COPIES.putIfAbsent(source, copy) == null) {
            Object sourceId=GameAccess.call(TEXTURE,"getTextureId",source,new Class<?>[0]);
            Atlas fresh=new Atlas(),atlas=ATLASES.putIfAbsent(sourceId,fresh);
            if(atlas==null) {
                List<Object> siblings=siblings(source,sourceId);
                queue(() -> {
                    try {
                        fresh.id=create(source,siblings);
                        if(fresh.id!=null) {
                            for(Object sibling:siblings) COPIES.get(sibling).texture=wrapper(sibling,fresh.id);
                            READY.incrementAndGet();
                        }
                    } catch(Throwable error) {
                        System.out.println("[VPISO] native sprite shadow atlas unavailable for "+name+": "+error);
                    }
                });
            } else {
                // A late texture wrapper can share the ready page without a second GPU atlas allocation.
                Atlas shared=atlas;
                queue(() -> {
                    try {
                        if(shared.id!=null) { copy.texture=wrapper(source,shared.id);READY.incrementAndGet(); }
                    } catch(Throwable error) { System.out.println("[VPISO] native sprite shadow wrapper unavailable: "+error); }
                });
            }
        }
        return source;
    }

    /** Actual queue boundary: each folding-chair orientation/state is reported at most once. */
    public static Object submitted(Object source) {
        Object target = texture(source);
        if (source == null) return target;
        Copy copy = COPIES.get(source);
        if (copy == null) copy = PRIVATE_COPIES.get(source);
        int scope = SCOPE.get();
        if (copy == null && scope == 0) return target;
        Object original = copy == null || copy.source == null ? source : copy.source;
        String name = name(original);
        if (!name.equals("furniture_seating_indoor_01_60") && !name.equals("furniture_seating_indoor_01_61")
                && !name.equals("furniture_seating_indoor_01_62") && !name.equals("furniture_seating_indoor_01_63")) return target;
        boolean ready = copy != null && copy.texture != null, capture = IsometricBridge.capturing(), rendering = IsometricBridge.rendering();
        boolean replaced = target != original;
        String state = name + ":" + selected + ":" + (scope != 0) + ":" + ready + ":" + capture + ":" + rendering + ":" + replaced;
        if (SUBMISSIONS.add(state)) {
            System.out.println("[VPISO] native chair submit name=" + name + " selected=" + selected + " scope=" + scope
                    + " copyReady=" + ready + " capture=" + capture + " vp=" + rendering + " replaced=" + replaced
                    + " size=" + integer(original,"getWidth") + "x" + integer(original,"getHeight")
                    + " sourceGPU=" + integer(original,"getID") + " targetGPU=" + integer(target,"getID"));
        }
        return target;
    }

    private static String name(Object texture) {
        return NAMES.computeIfAbsent(texture, source -> {
            String value = (String) GameAccess.call(TEXTURE, "getName", source, new Class<?>[0]);
            return value == null ? "" : value;
        });
    }

    private static void queue(Runnable job) {
        GameAccess.call("zombie.core.opengl.RenderThread","queueInvokeOnRenderContext",null,new Class<?>[]{Runnable.class},job);
    }
    private static boolean known(String name) {
        return "street_decoration_01_12".equals(name)||"lighting_outdoor_01_16".equals(name)
                ||"lighting_outdoor_01_16_on".equals(name)||NativeShadowMasks.get(name)!=null;
    }
    private static boolean matches(String name,int width,int height) {
        NativeShadowMasks.Mask mask=NativeShadowMasks.get(name);
        return firstRow(name,width,height)>=0 || mask!=null && mask.width()==width && mask.height()==height;
    }
    private static List<Object> siblings(Object source,Object sourceId) {
        Map<?,?> textures=(Map<?,?>)GameAccess.field("zombie.core.textures.TexturePackPage","subTextureMap",null);
        List<Object> result=new ArrayList<>();result.add(source);
        for(String name:NativeShadowMasks.names()) {
            Object sibling=textures.get(name);
            if(sibling==null||sibling==source||GameAccess.call(TEXTURE,"getTextureId",sibling,new Class<?>[0])!=sourceId)continue;
            if(matches(name,integer(sibling,"getWidth"),integer(sibling,"getHeight"))) {
                result.add(sibling);COPIES.putIfAbsent(sibling,new Copy());
            }
        }
        // The manually checked small masks are deliberately below the general component-size cutoff.
        for(String name:List.of("street_decoration_01_12","lighting_outdoor_01_16","lighting_outdoor_01_16_on")) {
            Object sibling=textures.get(name);
            if(sibling!=null && !result.contains(sibling) && GameAccess.call(TEXTURE,"getTextureId",sibling,new Class<?>[0])==sourceId
                    && matches(name,integer(sibling,"getWidth"),integer(sibling,"getHeight"))) {
                result.add(sibling);COPIES.putIfAbsent(sibling,new Copy());
            }
        }
        return result;
    }

    private static int firstRow(String name, int width, int height) {
        if ("street_decoration_01_12".equals(name) && width == 57 && height == 74) return 53;
        if (("lighting_outdoor_01_16".equals(name) || "lighting_outdoor_01_16_on".equals(name))
                && width == 36 && height == 215) return 213;
        return -1;
    }

    /** Top-down sprite coordinates. Never apply this rule to an arbitrary sprite or body pixels. */
    public static int mask(String name, int width, int height, ByteBuffer rgba, int stride, int left, int top) {
        int row = firstRow(name, width, height), changed = 0;
        NativeShadowMasks.Mask generated=NativeShadowMasks.get(name);
        byte[] mask;
        if(row>=0)mask="street_decoration_01_12".equals(name)?HYDRANT:POLE;
        else {
            if(generated==null||generated.width()!=width||generated.height()!=height)return 0;
            row=0;mask=generated.bits();
        }
        for (int y = row; y < height; y++) for (int x = 0; x < width; x++) {
            int bit = (y - row) * width + x;
            if ((mask[bit / 8] & (1 << (bit % 8))) == 0) continue;
            int p = ((top + y) * stride + left + x) * 4;
            int a = rgba.get(p + 3) & 255;
            if (a > 0 && a < 255) {
                rgba.put(p + 3, (byte) 0);
                changed++;
            }
        }
        return changed;
    }

    /** Render thread only. Keeping the atlas dimensions also keeps all native depth UVs intact. */
    private static Object create(Object source, List<Object> siblings) throws Exception {
        int atlasWidth = integer(source, "getWidthHW"), atlasHeight = integer(source, "getHeightHW");
        int sourceId = integer(source, "getID");
        if (sourceId <= 0 || atlasWidth <= 0 || atlasHeight <= 0) return null;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int pack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING), unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        Object last = GameAccess.field(TEXTURE, "lastTextureID", null);
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0); GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1); GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0); GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceId);
            int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            int wrapS = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
            int wrapT = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
            ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(atlasWidth, atlasHeight), 4));
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int changed=0;
            for(Object sibling:siblings) {
                int left=Math.round(number(sibling,"xStart")*atlasWidth),top=Math.round(number(sibling,"yStart")*atlasHeight);
                int width=integer(sibling,"getWidth"),height=integer(sibling,"getHeight");
                if(left<0||top<0||left+width>atlasWidth||top+height>atlasHeight)continue;
                String name=(String)GameAccess.call(TEXTURE,"getName",sibling,new Class<?>[0]);
                changed+=mask(name,width,height,pixels,atlasWidth,left,top);
            }
            if(changed==0)return null;
            Object id = GameAccess.type(ID).getConstructor(int.class, int.class, ByteBuffer.class)
                    .newInstance(atlasWidth, atlasHeight, pixels);
            int clonedId = ((Number) GameAccess.call(ID, "getID", id, new Class<?>[0])).intValue();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, clonedId);
            // TextureID's buffer constructor otherwise follows the global lossy-compression option.
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, atlasWidth, atlasHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GameAccess.set(ID, "internalFormat", id, GL11.GL_RGBA8);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapT);
            if (min != GL11.GL_NEAREST && min != GL11.GL_LINEAR) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            Object sourceData = GameAccess.call(TEXTURE, "getTextureId", source, new Class<?>[0]);
            GameAccess.set(ID, "flags", id, GameAccess.field(ID, "flags", sourceData));
            GameAccess.set(ID, "glMinFilter", id, min); GameAccess.set(ID, "glMagFilter", id, mag);
            return id;
        } finally {
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pack); GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
            GL13.glActiveTexture(active);
            GameAccess.set(TEXTURE, "lastTextureID", null, last);
        }
    }

    private static Object wrapper(Object source,Object id) throws Exception {
        String name=(String)GameAccess.call(TEXTURE,"getName",source,new Class<?>[0]);
        Object result=GameAccess.type(TEXTURE).getConstructor(GameAccess.type(ID),String.class).newInstance(id,name);
        for(String field:new String[]{"name","width","height","widthOrig","heightOrig","realWidth","realHeight",
                "offsetX","offsetY","xStart","yStart","xEnd","yEnd","flip","bindAlways","solid","mask",
                "splitX","splitY","splitW","splitH","subTexture","assetParams"}) {
            GameAccess.set(TEXTURE,field,result,GameAccess.field(TEXTURE,field,source));
        }
        Copy copy = COPIES.get(source);
        copy.source = source;
        PRIVATE_COPIES.put(result, copy);
        return result;
    }

    private static int integer(Object texture, String method) {
        return ((Number) GameAccess.call(TEXTURE, method, texture, new Class<?>[0])).intValue();
    }
    private static float number(Object texture, String field) { return ((Number) GameAccess.field(TEXTURE, field, texture)).floatValue(); }
    private NativeSpriteShadows() {}
}
