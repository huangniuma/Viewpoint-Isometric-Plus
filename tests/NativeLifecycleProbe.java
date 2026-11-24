import java.lang.reflect.Method;
import java.lang.reflect.Field;

/** Exercises the real game's two-level dirty buckets without world drawing. */
public final class NativeLifecycleProbe {
    public static void main(String[] args) throws Exception {
        Class<?> levels=Class.forName("zombie.iso.fboRenderChunk.FBORenderLevels");
        Class<?> chunk=Class.forName("zombie.iso.IsoChunk",false,ClassLoader.getSystemClassLoader());
        Object data=levels.getConstructor(int.class,chunk).newInstance(0,null);
        Field cache=levels.getField("clearCachedSquares"); boolean old=cache.getBoolean(null);
        cache.setBoolean(null,false);
        try {
            Method invalidate=levels.getMethod("invalidateLevel",int.class,long.class);
            Method dirty=levels.getMethod("isDirty",int.class,long.class,float.class);
            Method any=levels.getMethod("isDirty",int.class,float.class);
            Method clear=levels.getMethod("clearDirty",int.class,float.class);
            invalidate.invoke(data,0,64L);
            if (!(Boolean)dirty.invoke(data,0,192L,1f) || !(Boolean)dirty.invoke(data,1,192L,1f))
                throw new AssertionError("Real group topology invalidation missing");
            clear.invoke(data,0,1f);
            for (int i=0;i<20;i++) {
                if ((Boolean)dirty.invoke(data,0,192L,1f) || (Boolean)any.invoke(data,1,1f))
                    throw new AssertionError("Topology remains dirty after completed frame");
            }
            invalidate.invoke(data,1,128L);
            if (!(Boolean)dirty.invoke(data,0,192L,1f)) throw new AssertionError("New topology mutation lost");
            clear.invoke(data,1,1f);
            invalidate.invoke(data,0,2048L);
            if (!(Boolean)any.invoke(data,0,1f) || (Boolean)dirty.invoke(data,0,192L,1f))
                throw new AssertionError("Native redraw must remain dirty without retriggering topology");
            modelFallback(data, levels, chunk, clear, dirty);
            System.out.println("Native lifecycle PASS: actual FBORenderLevels dirty consumption stops idle rebuilds; new topology changes and fallback redraw remain detectable. No world/GL drawing.");
        } finally { cache.setBoolean(null,old); }
    }

    private static void modelFallback(Object data, Class<?> levels, Class<?> chunk, Method clear, Method dirty) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        Object cell = unsafe.allocateInstance(Class.forName("zombie.iso.IsoCell"));
        Object map = unsafe.allocateInstance(Class.forName("zombie.iso.IsoChunkMap"));
        Object tile = unsafe.allocateInstance(chunk);
        var maps = java.lang.reflect.Array.newInstance(map.getClass(), 4);
        java.lang.reflect.Array.set(maps, 0, map);
        int width = (Integer) vpiso.GameAccess.field("zombie.iso.IsoChunkMap", "chunkGridWidth", null);
        var chunks = java.lang.reflect.Array.newInstance(chunk, width * width);
        java.lang.reflect.Array.set(chunks, 0, tile);
        vpiso.GameAccess.set("zombie.iso.IsoCell", "chunkMap", cell, maps);
        vpiso.GameAccess.set("zombie.iso.IsoChunkMap", "chunksSwapB", map, chunks);
        vpiso.GameAccess.set("zombie.iso.IsoChunk", "lightingNeverDone", tile, new boolean[4]);
        var buckets = java.lang.reflect.Array.newInstance(levels, 4);
        java.lang.reflect.Array.set(buckets, 0, data);
        vpiso.GameAccess.set("zombie.iso.IsoChunk", "renderLevels", tile, buckets);
        Object renderer = vpiso.GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell", "instance", null);
        Object previous = vpiso.GameAccess.field("zombie.iso.fboRenderChunk.FBORenderCell", "cell", renderer);
        try {
            vpiso.GameAccess.set("zombie.iso.fboRenderChunk.FBORenderCell", "cell", renderer, cell);
            vpiso.Main.compatible = true;
            viewpoint.core.View.enabled = false;
            vpiso.IsometricBridge.setEnabled(true);
            vpiso.IsometricBridge.setModelsEnabled(true);
            vpiso.GameAccess.set("vpiso.IsometricBridge", "submitted", null, true);
            if (!vpiso.IsometricBridge.active()) throw new AssertionError("Submitted model frame lost native interaction scope");
            vpiso.GameAccess.set("vpiso.NativeIsoState", "nativeNeedsRebuild", null, true);
            vpiso.GameAccess.set("vpiso.IsometricBridge", "lastCapturedFrame", null, 55L);
            clear.invoke(data, 0, 1f);
            vpiso.IsometricBridge.setModelsEnabled(false);
            if (vpiso.IsometricBridge.active() || vpiso.IsometricBridge.suppressWeather()
                    || !vpiso.GameAccess.field("vpiso.IsometricBridge", "lastCapturedFrame", null).equals(-2L)) {
                throw new AssertionError("Model switch retained native input/weather or camera history");
            }
            if (vpiso.IsometricBridge.afterWorld(cell, false)
                    || !(Boolean) dirty.invoke(data, 0, 2048L, 1f)
                    || (Boolean) vpiso.GameAccess.field("vpiso.NativeIsoState", "nativeNeedsRebuild", null)) {
                throw new AssertionError("Switching 3D off did not rebuild real native chunk buckets");
            }
            clear.invoke(data, 0, 1f);
            vpiso.IsometricBridge.afterWorld(cell, false);
            if ((Boolean) dirty.invoke(data, 0, 2048L, 1f)) throw new AssertionError("Native fallback rebuilt caches every frame");
            vpiso.IsometricBridge.setModelsEnabled(true);
            vpiso.GameAccess.set("zombie.iso.IsoCamera$FrameState", "playerIndex",
                    vpiso.GameAccess.field("zombie.iso.IsoCamera", "frameState", null), 1);
            if (vpiso.IsometricBridge.afterWorld(cell, false)) throw new AssertionError("Unsupported player captured 3D world");
            if (!vpiso.IsometricBridge.afterWorld(cell, true)) throw new AssertionError("VP camera handling was overridden");
            System.out.println("Model switching PASS: production afterWorld fallback, real native chunk redraw exactly once, camera history reset, input/weather scopes cleared, unsupported-player fallback and VP camera passthrough; no world drawing.");
        } finally {
            vpiso.GameAccess.set("zombie.iso.fboRenderChunk.FBORenderCell", "cell", renderer, previous);
            vpiso.GameAccess.set("zombie.iso.IsoCamera$FrameState", "playerIndex",
                    vpiso.GameAccess.field("zombie.iso.IsoCamera", "frameState", null), 0);
            vpiso.IsometricBridge.setModelsEnabled(false);
        }
    }
}
