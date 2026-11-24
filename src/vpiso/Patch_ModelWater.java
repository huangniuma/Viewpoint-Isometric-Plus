package vpiso;

import java.lang.reflect.Constructor;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.Pipeline;
import viewpoint.render.FrameContext;

/** Enables VP's own wave producer only for the addon's Library model frame. */
@Patch(className="viewpoint.render.WaveTile", methodName="draw")
public final class Patch_ModelWater {
    private static final Map<Pipeline, Pipeline> COPIES = new IdentityHashMap<>();
    private static final Pattern KNOB = Pattern.compile("(?m)^#define PIPELINE_(\\w+) (\\d+)\\s*$");

    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) FrameContext context,
                             @Patch.Local("vpIsoWaterPipeline") Pipeline previous) {
        previous = begin(context);
    }

    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Argument(0) FrameContext context,
                            @Patch.Local("vpIsoWaterPipeline") Pipeline previous) {
        restore(context, previous);
    }

    public static Pipeline begin(FrameContext context) {
        if (!IsometricBridge.waterRendering() || context.pipeline.water) return null;
        Pipeline previous = context.pipeline;
        context.pipeline = waterPipeline(previous);
        return previous;
    }

    /** A private immutable copy; also used by the independent native water producer. */
    public static Pipeline waterPipeline(Pipeline original) {
        if (original.water) return original;
        synchronized (COPIES) { return COPIES.computeIfAbsent(original, Patch_ModelWater::withWater); }
    }

    public static void restore(FrameContext context, Pipeline previous) {
        if (previous != null) context.pipeline = previous;
    }

    private static Pipeline withWater(Pipeline original) {
        try {
            Object[] knobs = GameAccess.type("viewpoint.platform.Pipeline$Knob").getEnumConstants();
            Map<String, Integer> values = new java.util.HashMap<>();
            var matches = KNOB.matcher((String) GameAccess.field("viewpoint.platform.Pipeline", "defines", original));
            while (matches.find()) values.put(matches.group(1), Integer.parseInt(matches.group(2)));
            int[] copy = new int[knobs.length];
            for (int i = 0; i < knobs.length; i++) {
                String name = ((Enum<?>) knobs[i]).name();
                Integer value = values.get(name);
                if (value == null) throw new IllegalStateException("Unsupported VP pipeline knob " + name);
                copy[i] = name.equals("WATER") ? 1 : value;
            }
            Constructor<Pipeline> constructor = Pipeline.class.getDeclaredConstructor(int[].class);
            constructor.setAccessible(true);
            return constructor.newInstance((Object) copy);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot prepare VP model water pipeline", error);
        }
    }

    private Patch_ModelWater() {}
}
