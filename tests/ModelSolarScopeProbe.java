import java.lang.reflect.InvocationTargetException;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.SceneData;
import vpiso.*;

/** Execute actual woven Meshes.draw exceptional cleanup, including nested camera draws. */
public final class ModelSolarScopeProbe {
    public static void main(String[] args) throws Exception {
        Frame frame = new Frame();
        frame.scene.meshCount = 1;
        frame.scene.meshFlags[0] = SceneData.CASTS_SHADOW | SceneData.VISIBLE;
        IsometricBridge.modelRendering(frame, new IsometricEffects(true,1,0,64,256,false,1,1,1,1));
        var method = GameAccess.type("viewpoint.render.Meshes").getDeclaredMethod("draw",
                SceneData.class, GlProgram.class, byte.class, int.class, boolean.class, boolean.class);
        method.setAccessible(true);
        // A null mesh fails inside the real consumer after the advice has entered its scope.
        // It requires no world or GPU; the original exception must propagate and restore scope.
        for (byte mask : new byte[] { SceneData.CASTS_SHADOW, SceneData.VISIBLE }) {
            try {
                method.invoke(null, frame.scene, null, mask, 31, false, false);
                throw new AssertionError("Invalid mesh did not throw");
            } catch (InvocationTargetException expected) {
                if (!(expected.getCause() instanceof NullPointerException)) throw expected;
            }
            if (GameAccess.field("vpiso.ModelSolarGeometry", "ACTIVE", null) instanceof ThreadLocal<?> scope
                    && scope.get() != null) throw new AssertionError("Solar scope leaked after draw failure");
        }
        var previous = ModelSolarGeometry.enter(frame.scene, SceneData.CASTS_SHADOW);
        try {
            try { method.invoke(null, frame.scene, null, SceneData.VISIBLE, 31, false, false); }
            catch (InvocationTargetException expected) {
                if (!(expected.getCause() instanceof NullPointerException)) throw expected;
            }
            ThreadLocal<?> scope = (ThreadLocal<?>) GameAccess.field("vpiso.ModelSolarGeometry", "ACTIVE", null);
            if (scope.get() != frame.scene) throw new AssertionError("Nested camera draw lost outer solar scope");
            Throwable[] error = { null };
            Thread thread = new Thread(() -> {
                if (scope.get() != null) error[0] = new AssertionError("Solar scope crossed render threads");
            });
            thread.start(); thread.join();
            if (error[0] != null) throw new AssertionError(error[0]);
        } finally { ModelSolarGeometry.restore(previous); IsometricBridge.rendering(null); }
        System.out.println("Physical solar scope PASS: actual woven solar/camera draw failures restore normal and nested ownership; thread isolation; no GPU or game world.");
    }
}
