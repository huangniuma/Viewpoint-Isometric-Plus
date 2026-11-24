package vpiso;

import java.nio.IntBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.FrameContext;
import viewpoint.render.MousePick;
import viewpoint.render.SceneData;

/** Frame-owned source alpha and exact mouse ownership for the model camera's colour passes. */
public final class NativeObjectState {
    private static final int UNIT = 147, STORAGE = 6;
    private static final int ASSIST_RADIUS = 6, PICK_RADIUS = 48;
    private static final int PICK_WIDTH = PICK_RADIUS * 2 + 1, PICK_PIXELS = PICK_WIDTH * PICK_WIDTH;
    // Render-thread only: collect the previous frame instead of waiting for its drawer's
    // turn in the game's three-buffer ring.
    private static final Set<NativeObjectState> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<NativeObjectState> CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<NativeObjectState> RENDER = new ThreadLocal<>();
    private static volatile Pick latest;
    private static volatile long pickGeneration;
    private final IdentityHashMap<Object, Sample> sources = new IdentityHashMap<>();
    private final IsometricVisibility.CaptureQueries visibility = new IsometricVisibility.CaptureQueries();
    private final Map<Integer, Map<Integer, Sample>> meshes = new HashMap<>();
    private final Map<Integer, Sample> models = new HashMap<>();
    private final List<Object> ids = new ArrayList<>();
    private long[] rangeKeys=new long[16];
    private int[] rangePayload=new int[48];
    private int rangeCount;
    private final BitSet assisted = new BitSet();
    private final int[] starts = new int[3], counts = new int[3];
    private Frame frame;
    private MousePick.Ask ask, pendingAsk;
    private List<Object> pendingIds;
    private BitSet pendingAssisted;
    private int pickBits, pendingBits;
    private float queryScaleX=1f, queryScaleY=1f, queryPixelX, queryPixelY;
    private float pendingScaleX, pendingScaleY, pendingPixelX, pendingPixelY;
    private int[] sparsePixels, pixelColumns, pixelRows;
    private float yaw, pendingYaw;
    private float pickX, pickY;
    private long fence;
    private long generation, pendingGeneration, capturedAt, pendingCapturedAt;
    private int tableBuffer, tableTexture, pickBuffer, readBuffer;
    private IntBuffer tableWords, clearWords, readWords;
    private final int[] viewport = new int[4];
    private int previousTexture, previousSampler, previousStorage;
    private long previousStart, previousSize;
    private boolean ready, sampling;

    public void beginCapture(Frame frame) {
        this.frame = frame;
        sources.clear(); meshes.clear(); models.clear(); ids.clear(); ids.add(null); assisted.clear();
        ask = MousePick.ask; yaw = RotationCamera.yawDegrees();
        generation = pickGeneration; capturedAt = System.nanoTime();
        if (ask != null) {
            Class<?>[] args = { int.class, float.class, float.class, float.class };
            pickX = ((Number) GameAccess.call("zombie.Lua.LuaManager$GlobalObject", "screenToIsoX", null,
                    args, 0, (float) ask.x(), (float) ask.y(), (float) Math.floor(frame.camZ))).floatValue();
            pickY = ((Number) GameAccess.call("zombie.Lua.LuaManager$GlobalObject", "screenToIsoY", null,
                    args, 0, (float) ask.x(), (float) ask.y(), (float) Math.floor(frame.camZ))).floatValue();
        }
        CAPTURE.set(this);
        IsometricVisibility.beginCapture(visibility);
    }

    public static void endCapture() { IsometricVisibility.endCapture(); CAPTURE.remove(); }

    /** Reuse the game's actual alpha step, once per source and captured frame. */
    public static float alpha(Object object, int player, float target) {
        NativeObjectState state = CAPTURE.get();
        if (state == null || object == null) return target;
        // NativeTrees owns the original bark/leaf transition and its animation step.
        if (object instanceof zombie.iso.objects.IsoTree) return target;
        Sample prior = state.sources.get(object);
        if (prior != null) return prior.alpha;
        zombie.iso.IsoObject nativeObject = (zombie.iso.IsoObject) object;
        nativeObject.setTargetAlpha(player, target);
        AlphaUpdate.step(nativeObject,player);
        float current = nativeObject.getAlpha(player);
        current = Float.isFinite(current) ? Math.max(0f, Math.min(1f, current)) : target;
        state.sample(object, current);
        return current;
    }

    /** Same protected final native alpha step, bound once instead of boxed reflection per source. */
    private static final class AlphaUpdate {
        private static final java.lang.invoke.MethodHandle STEP;
        static {
            try {
                var method=zombie.iso.IsoObject.class.getDeclaredMethod("updateAlpha",int.class);
                method.setAccessible(true);
                STEP=java.lang.invoke.MethodHandles.lookup().unreflect(method);
            } catch(ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
        }
        static void step(zombie.iso.IsoObject object,int player) {
            try { STEP.invokeExact(object,player); }
            catch(Throwable error) { throw new IllegalStateException("Native alpha update failed",error); }
        }
    }

    private Sample sample(Object source, float alpha) {
        if (source == null) return new Sample(1f, 0);
        Sample old = sources.get(source);
        if (old != null) return old;
        zombie.iso.IsoObject nativeObject = (zombie.iso.IsoObject) source;
        zombie.iso.IsoGridSquare square = nativeObject.getSquare();
        boolean candidate = ask != null && square != null
                && !nativeObject.noPicking
                && Math.abs(square.x - pickX) < 8f
                && Math.abs(square.y - pickY) < 8f;
        Sample sample = new Sample(alpha, candidate ? ids.size() : 0);
        sources.put(source, sample);
        if (candidate) {
            ids.add(source);
            if (interactive(source)) assisted.set(sample.id);
        }
        return sample;
    }

    public static void model(Frame frame, Object source, int first, float alpha) {
        NativeObjectState state = CAPTURE.get();
        if (state == null || state.frame != frame || source == null) return;
        Sample sample = state.sample(source, alpha);
        for (int i = first; i < frame.scene.models.count(); i++) state.models.put(i, sample);
    }

    public static void mesh(Frame frame, Object cell, int index, int x, int y, int player) {
        NativeObjectState state = CAPTURE.get();
        if (state == null || state.frame != frame) return;
        ChunkMeshData mesh = frame.scene.meshes[index];
        Map<Integer, Sample> samples = new HashMap<>();
        int[] spans = (int[]) GameAccess.field("viewpoint.render.ChunkMeshData", "spans", mesh);
        int[] packed = (int[]) GameAccess.field("viewpoint.render.ChunkMeshData", "modelObjects", mesh);
        if (spans != null) for (int i = 0; i < spans.length; i += 3) samples.put(spans[i], null);
        if (packed != null) for (int owner : packed) samples.put(owner, null);
        for (Map.Entry<Integer, Sample> entry : samples.entrySet()) {
            int owner = entry.getKey();
            if (owner < 0 || owner >>> 16 >= 64) continue;
            int tile = owner >>> 16;
            zombie.iso.IsoGridSquare square = ((zombie.iso.IsoCell) cell).getGridSquare(x + tile % 8, y + tile / 8, mesh.level);
            if (square == null) continue;
            List<?> objects = square.getObjects();
            if ((owner & 65535) >= objects.size()) continue;
            Object object = objects.get(owner & 65535);
            float alpha = IsometricVisibility.objectAlpha(frame, square, object, player);
            entry.setValue(state.sample(object, alpha));
        }
        state.meshes.put(index, samples);
    }

    public void beginRender() {
        for (Iterator<NativeObjectState> it = PENDING.iterator(); it.hasNext();) {
            NativeObjectState pending = it.next();
            pending.collect();
            if (pending.fence == 0) it.remove();
        }
        collect();
        // A queued scene can be several input frames old. Rasterise the query around
        // the current mouse; candidate sources still cover a wide world neighbourhood.
        MousePick.Ask current = MousePick.ask;
        if (current != null) ask = current;
        capturedAt = System.nanoTime();
        RENDER.set(this); ready = sampling = false;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
        previousTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        previousSampler = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, UNIT);
        GL13.glActiveTexture(active);
        previousStorage = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, STORAGE);
        previousStart = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, STORAGE);
        previousSize = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, STORAGE);
    }

    /** Arena placement is final here. Cached mesh vertices and packed instances remain untouched. */
    public static void prepared(SceneData scene) {
        NativeObjectState state = RENDER.get();
        if (state != null && state.frame.scene == scene) state.upload();
    }

    private void upload() {
        for (int i = 0; i < frame.scene.models.count(); i++) {
            Object source = frame.scene.models.key(i);
            if (!models.containsKey(i) && GameAccess.type("zombie.iso.IsoObject").isInstance(source))
                models.put(i, sample(source, 1f));
        }
        rangeCount=0;
        for (Map.Entry<Integer, Map<Integer, Sample>> entry : meshes.entrySet()) {
            ChunkMeshData mesh = frame.scene.meshes[entry.getKey()];
            Map<Integer, Sample> objects = entry.getValue();
            int[] spans = (int[]) GameAccess.field("viewpoint.render.ChunkMeshData", "spans", mesh);
            int[] packed = (int[]) GameAccess.field("viewpoint.render.ChunkMeshData", "modelObjects", mesh);
            if (spans != null && mesh.arenaFirst >= 0) for (int i = 0; i < spans.length; i += 3) {
                Sample sample = objects.get(spans[i]);
                if (needed(sample) && spans[i + 2] > 0)
                    addRange(0, mesh.arenaFirst + spans[i + 1], mesh.arenaFirst + spans[i + 1] + spans[i + 2], sample);
            }
            if (packed != null) for (int i = 0; i < packed.length; i++) {
                Sample sample = objects.get(packed[i]);
                if (needed(sample)) {
                    int slot = ((Number) GameAccess.call("viewpoint.render.ChunkMeshData", "slot", mesh, new Class<?>[] { int.class }, ((Number) GameAccess.field("viewpoint.render.ChunkMeshData", "modelBase", mesh)).intValue() + i)).intValue();
                    addRange(1, slot, slot + 1, sample);
                }
            }
        }
        for (Map.Entry<Integer, Sample> entry : models.entrySet())
            if (needed(entry.getValue())) addRange(2, entry.getKey(), entry.getKey() + 1, entry.getValue());
        Arrays.sort(rangeKeys,0,rangeCount);
        int neededWords = Math.max(4, rangeCount * 4);
        if (tableWords == null || tableWords.capacity() < neededWords)
            tableWords = BufferUtils.createIntBuffer(Integer.highestOneBit(neededWords - 1) << 1);
        IntBuffer values = tableWords.clear();
        Arrays.fill(starts, 0); Arrays.fill(counts, 0);
        int position = 0;
        for (int kind = 0; kind < 3; kind++) {
            starts[kind] = position;
            while(position<rangeCount && (int)((rangeKeys[position]^Long.MIN_VALUE)>>>62)==kind) {
                long key=rangeKeys[position];int at=(int)(key&0x3fffffffL)*3;
                int first=(int)(key>>>30)^Integer.MIN_VALUE,id=rangePayload[at+2];
                values.put(first).put(rangePayload[at]).put(rangePayload[at+1]).put(id | (assisted.get(id) ? 0x40000000 : 0));
                counts[kind]++; position++;
            }
        }
        if (position == 0) values.put(new int[4]);
        values.flip();rangeCount=0;
        int previousBuffer = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        int previousSSBO = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            if (tableBuffer == 0) {
                tableBuffer = GL15.glGenBuffers(); tableTexture = GL11.glGenTextures();
                pickBuffer = GL15.glGenBuffers(); readBuffer = GL15.glGenBuffers();
            }
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, tableBuffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, values, GL15.GL_STREAM_DRAW);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, tableTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32UI, tableBuffer);
            GL33.glBindSampler(UNIT, 0);
            sampling = fence == 0 && ask != null && ask.screenWidth() > 0 && ask.screenHeight() > 0;
            pickBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(ids.size() - 1));
            if (pickBits > 16) throw new IllegalStateException("Too many mouse ownership sources");
            if (clearWords == null) {
                clearWords = BufferUtils.createIntBuffer(PICK_PIXELS);
                while (clearWords.hasRemaining()) clearWords.put(-1);
            }
            IntBuffer depths = clearWords.clear();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, pickBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, depths, GL15.GL_STREAM_COPY);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, STORAGE, pickBuffer);
            ready = true;
        } finally {
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previousBuffer);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousSSBO);
            GL13.glActiveTexture(active);
        }
    }

    public void finishPick() {
        if (!ready || !sampling) return;
        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
        int read = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        int write = GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        try {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, pickBuffer);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, readBuffer);
            GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, PICK_PIXELS * 4L, GL15.GL_STREAM_READ);
            GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, PICK_PIXELS * 4L);
            pendingIds = new ArrayList<>(ids); pendingAssisted = (BitSet) assisted.clone(); pendingAsk = ask; pendingYaw = yaw;
            pendingGeneration = generation; pendingCapturedAt = capturedAt; pendingBits = pickBits;
            pendingScaleX=queryScaleX; pendingScaleY=queryScaleY; pendingPixelX=queryPixelX; pendingPixelY=queryPixelY;
            fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            PENDING.add(this);
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, read);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, write);
        }
    }

    private void collect() {
        if (fence == 0) return;
        int status = GL32.glClientWaitSync(fence, 0, 0);
        if (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) return;
        GL32.glDeleteSync(fence); fence = 0;
        if (readWords == null) readWords = BufferUtils.createIntBuffer(PICK_PIXELS);
        IntBuffer depths = readWords.clear();
        int previous = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        try {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, readBuffer);
            GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, depths);
            int[] pixels = new int[PICK_PIXELS]; depths.get(pixels);
            resolvePixels(pixels);
            int chosen = choose(pixels, pendingBits, pendingAssisted, 0, 0);
            if (pendingGeneration == pickGeneration && (latest == null || pendingCapturedAt >= latest.time))
                latest = new Pick(pendingAsk, pendingIds.get(chosen), pendingYaw, pendingCapturedAt,
                        pendingGeneration, pixels, pendingIds, pendingAssisted, pendingBits);
        } finally { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, previous); pendingIds = null; }
    }

    /** Expand only the original raster footprint, before applying the six-pixel assist. */
    private void resolvePixels(int[] pixels) {
        if (pendingScaleX>=1f && pendingScaleY>=1f) return;
        if (sparsePixels==null) { sparsePixels=new int[PICK_PIXELS]; pixelColumns=new int[PICK_WIDTH]; pixelRows=new int[PICK_WIDTH]; }
        System.arraycopy(pixels,0,sparsePixels,0,PICK_PIXELS);
        // Read each output pixel from its actual raster texel footprint. This fills
        // sparse low-resolution queries without expanding a silhouette or assistance.
        for(int i=-PICK_RADIUS;i<=PICK_RADIUS;i++) {
            pixelColumns[i+PICK_RADIUS]=Math.round(((float)Math.floor(pendingPixelX+i*pendingScaleX)+.5f-pendingPixelX)/pendingScaleX);
            pixelRows[i+PICK_RADIUS]=Math.round(((float)Math.floor(pendingPixelY+i*pendingScaleY)+.5f-pendingPixelY)/pendingScaleY);
        }
        for(int y=0;y<PICK_WIDTH;y++) for(int x=0;x<PICK_WIDTH;x++) {
            int sx=pixelColumns[x], sy=pixelRows[y];
            pixels[y*PICK_WIDTH+x]=Math.abs(sx)>PICK_RADIUS || Math.abs(sy)>PICK_RADIUS
                    ? -1 : sparsePixels[(sy+PICK_RADIUS)*PICK_WIDTH+sx+PICK_RADIUS];
        }
    }

    private static int choose(int[] pixels, int bits, BitSet assisted, int offsetX, int offsetY) {
        int centre = 0, chosen = 0, bestDistance = Integer.MAX_VALUE, mask = (1 << bits) - 1;
        for (int dy = -ASSIST_RADIUS; dy <= ASSIST_RADIUS; dy++)
            for (int dx = -ASSIST_RADIUS; dx <= ASSIST_RADIUS; dx++) {
                int distance = dx * dx + dy * dy;
                if (distance > ASSIST_RADIUS * ASSIST_RADIUS) continue;
                int x = PICK_RADIUS + offsetX + dx, y = PICK_RADIUS + offsetY + dy;
                if (x < 0 || x >= PICK_WIDTH || y < 0 || y >= PICK_WIDTH) continue;
                int pixel = pixels[y * PICK_WIDTH + x];
                int id = pixel == -1 ? 0 : pixel & mask;
                if (distance == 0) centre = id;
                if (id != 0 && assisted.get(id) && distance < bestDistance) {
                    bestDistance = distance; chosen = id;
                }
            }
        return chosen != 0 ? chosen : centre;
    }

    /** Only ordinary actionable objects get the small screen-space selection assist. */
    private static boolean interactive(Object source) {
        if ((Boolean) GameAccess.field("zombie.iso.IsoObject", "noPicking", source)) return false;
        for (String name : new String[] { "IsoLightSwitch", "IsoDoor", "IsoWindow", "IsoWindowFrame", "IsoCurtain", "IsoThumpable", "IsoWorldInventoryObject" })
            if (GameAccess.type("zombie.iso.objects." + name).isInstance(source)) return true;
        return GameAccess.call("zombie.iso.IsoObject", "getContainer", source, new Class<?>[0]) != null;
    }

    public static Object picked(Object original) {
        if (IsometricBridge.active() && original != null
                && GameAccess.call("zombie.iso.IsoObject", "getSquare", original, new Class<?>[0]) != null
                && !ClientSession.currentObject(original)) original = null;
        Pick pick = latest;
        if (!IsometricBridge.active() || pick == null
                || pick.generation != pickGeneration || System.nanoTime() - pick.time > 250_000_000L
                || pick.yaw != RotationCamera.yawDegrees()) return original;
        MousePick.Ask current = MousePick.ask;
        if (current == null || Math.abs(current.x() - pick.ask.x()) > PICK_RADIUS - ASSIST_RADIUS
                || Math.abs(current.y() - pick.ask.y()) > PICK_RADIUS - ASSIST_RADIUS
                || current.screenWidth() != pick.ask.screenWidth() || current.screenHeight() != pick.ask.screenHeight()) return original;
        int id = choose(pick.pixels, pick.bits, pick.assisted, current.x() - pick.ask.x(), pick.ask.y() - current.y());
        Object selected = pick.ids.get(id);
        if (selected == null || !ClientSession.currentObject(selected)) return original;
        Object square = GameAccess.call("zombie.iso.IsoObject", "getSquare", selected, new Class<?>[0]);
        if (square == null) return original;
        GameAccess.set("viewpoint.interact.MouseTargets", "lastSquare", null, square);
        return selected;
    }

    public static void resetPick() { pickGeneration++; latest = null; NativeObjectClick.reset(); }

    public static void onUse(GlProgram program) {
        if (!program.has("vpIsoObjectsOn")) return;
        NativeObjectState state = RENDER.get();
        boolean enabled = state != null && state.ready && IsometricBridge.modelRendering();
        program.setInt("vpIsoObjectsOn", enabled ? 1 : 0);
        if (!enabled) return;
        if (program.has("vpIsoObjectTable")) {
            program.setInt("vpIsoObjectTable", UNIT);
            for (int kind = 0; kind < 3; kind++) {
                program.setInt("vpIsoObjectStart" + kind, state.starts[kind]);
                program.setInt("vpIsoObjectCount" + kind, state.counts[kind]);
            }
        }
        if (program.has("vpIsoPickOn")) {
            program.setInt("vpIsoPickOn", state.sampling ? 1 : 0);
            program.setInt("vpIsoPickBits", state.pickBits);
            if (state.sampling) {
                int[] viewport = state.viewport; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                float scaleX=(float)viewport[2]/state.ask.screenWidth(), scaleY=(float)viewport[3]/state.ask.screenHeight();
                FrameContext context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
                boolean current=context.scene==state.frame.scene;
                float jitterX=current?((Number)GameAccess.field("viewpoint.render.FrameContext","jitterX",context)).floatValue():0f;
                float jitterY=current?((Number)GameAccess.field("viewpoint.render.FrameContext","jitterY",context)).floatValue():0f;
                state.queryScaleX=scaleX; state.queryScaleY=scaleY;
                state.queryPixelX=viewport[0]+(state.ask.x()+.5f)*scaleX-jitterX;
                state.queryPixelY=viewport[1]+viewport[3]-(state.ask.y()+.5f)*scaleY-jitterY;
                program.set("vpIsoPickScale", scaleX, scaleY);
                program.set("vpIsoPickPixel", state.queryPixelX, state.queryPixelY);
            }
        }
    }

    public void endRender() {
        RENDER.remove(); ready = sampling = false;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousTexture);
        GL33.glBindSampler(UNIT, previousSampler); GL13.glActiveTexture(active);
        int previous = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        if (previousStorage != 0 && previousSize > 0)
            GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, STORAGE, previousStorage, previousStart, previousSize);
        else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, STORAGE, previousStorage);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous);
    }

    private record Sample(float alpha, int id) {}
    private static boolean needed(Sample sample) { return sample != null && (sample.alpha != 1f || sample.id != 0); }
    /** Primitive scratch: kind2bits, signed first32bits, original index30bits.
     * The final index is an exact tie-breaker for the original stable sort.
     * Java payload arrays limit capacity before that index can overflow. */
    private void addRange(int kind,int first,int end,Sample sample) {
        int index=rangeCount++;
        if(index==rangeKeys.length) {
            int capacity=Math.multiplyExact(rangeKeys.length,2);
            rangeKeys=Arrays.copyOf(rangeKeys,capacity);
            rangePayload=Arrays.copyOf(rangePayload,Math.multiplyExact(capacity,3));
        }
        rangeKeys[index]=(((long)kind<<62)|((Integer.toUnsignedLong(first^Integer.MIN_VALUE))<<30)|index)^Long.MIN_VALUE;
        int at=index*3;rangePayload[at]=end;rangePayload[at+1]=Float.floatToIntBits(sample.alpha);rangePayload[at+2]=sample.id;
    }
    private record Pick(MousePick.Ask ask, Object object, float yaw, long time, long generation,
                        int[] pixels, List<Object> ids, BitSet assisted, int bits) {}
}
