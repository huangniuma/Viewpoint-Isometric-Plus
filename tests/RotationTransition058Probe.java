import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import viewpoint.camera.IsoLens;
import viewpoint.camera.MovePose;
import viewpoint.core.View;
import viewpoint.platform.BuildPin;
import viewpoint.platform.Graphics;
import viewpoint.platform.ShaderPacks;
import vpiso.GameAccess;
import vpiso.IsoCameraMath;
import vpiso.IsometricBridge;
import vpiso.Main;
import vpiso.RotationCamera;

/** Executes the actual production-woven MovePose; contains no replacement advice or game launch. */
public final class RotationTransition058Probe {
    private static final float[] FEET = {.31f, .22f, -.49f};
    private static final float[] EYE = {.45f, 1.6f, -2.2f};
    private static final float[] DIR = {-.3f, -.2f, -.9327379f};
    private static final float[] UP = {0, 1, 0};
    private static final float[] SHARES = {0, .1f, .25f, .5f, .75f, .9f, 1};
    private static Class<?> woven;
    private static Method at;
    private static Object instance;

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Pass the actual woven viewpoint/camera/MovePose.class path");
        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        require(constants.contains("vpiso/RotationCamera") && constants.contains("transitionStart"),
                "Supplied class has no production transition patch");
        try (var stream = MovePose.class.getResourceAsStream("MovePose.class")) {
            require(stream != null && !new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1)
                    .contains("vpiso/RotationCamera"), "Baseline must be the unmodified VP JAR class");
        }
        ClassLoader loader = new ClassLoader(RotationTransition058Probe.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (!name.equals("viewpoint.camera.MovePose")) return super.loadClass(name, resolve);
                Class<?> result = findLoadedClass(name);
                if (result == null) result = defineClass(name, bytes, 0, bytes.length);
                if (resolve) resolveClass(result);
                return result;
            }
        };
        woven = loader.loadClass("viewpoint.camera.MovePose");
        instance = woven.getConstructor().newInstance();
        at = woven.getMethod("at", float.class, IsoLens.class, float[].class, float[].class,
                float[].class, float[].class, float.class, float.class, float.class, float.class, float.class);

        int transitions = 0, guards = 0;
        try (SavedState saved = new SavedState()) {
            require(BuildPin.supported(), "Installed game is not the VP-pinned build");
            Object player = allocate(GameAccess.type("zombie.characters.IsoPlayer"));
            Arrays.fill(saved.players, null);
            saved.players[0] = player;
            for (boolean vp : new boolean[] {false, true}) {
                for (float zoom : new float[] {.75f, 1, 1.5f}) {
                    for (float pan : new float[] {-170, 0, 137}) {
                        IsoLens lens = lens(zoom, pan);
                        for (float yaw : new float[] {0, 17.25f, 90, 179, 180, 270, 359}) {
                            configure(vp, yaw);
                            require(IsometricBridge.rotationConfigured(), "Eligible transition blocked");
                            require(IsometricBridge.rotationAllowed() == !vp,
                                    "Ordinary rotation must stay disabled in VP view");
                            Pose start = sample(lens, 0);
                            float[] cameraEye = new float[3];
                            IsoCameraMath.matrices(new IsoCameraMath.Fit(0, 0, 1), 0, 0, 1280, 720,
                                    new Matrix4f(), new Matrix4f(), cameraEye, yaw);
                            for (int i = 0; i < 3; i++) {
                                near(start.dir[i], -cameraEye[i] / IsoCameraMath.DISTANCE,
                                        "Transition must start in the actual rotated camera direction");
                                near(start.eye[i], FEET[i] - start.dir[i] * lens.distance,
                                        "Rotated starting eye");
                            }
                            // Same native instance is reused through entry and the reverse exit path.
                            for (int direction : new int[] {1, -1}) {
                                for (int n = 0; n < SHARES.length; n++) {
                                    float share = SHARES[direction == 1 ? n : SHARES.length - 1 - n];
                                    Pose got = sample(lens, share), plain = baseline(lens, share);
                                    finite(got);
                                    require(got.projection.equals(plain.projection, 1e-6f),
                                            "Patch changed native lens, panning, FOV or clipping");
                                    equal(got.up, plain.up, "Native up interpolation");
                                    if (yaw == 0 || share == 1) equal(got, plain);
                                    transitions++;
                                }
                            }
                            equal(sample(lens, 0), start);
                            require(RotationCamera.requested() && RotationCamera.savedYawDegrees() == yaw,
                                    "Transition changed the saved rotation preference");
                        }
                    }
                }
            }
            for (boolean vp : new boolean[] {false, true}) {
                for (String guard : new String[] {"master", "models", "requested", "compatible",
                        "failed", "splitscreen", "build", "pack", "reflections"}) {
                    configure(vp, 90);
                    saved.players[1] = null;
                    switch (guard) {
                        case "master" -> set("vpiso.IsometricBridge", "enabled", false);
                        case "models" -> set("vpiso.IsometricBridge", "modelsEnabled", false);
                        case "requested" -> set("vpiso.RotationCamera", "requested", false);
                        case "compatible" -> Main.compatible = false;
                        case "failed" -> set("vpiso.IsometricBridge", "failed", true);
                        case "splitscreen" -> saved.players[1] = player;
                        case "build" -> set("viewpoint.platform.BuildPin", "supported", Boolean.FALSE);
                        case "pack" -> ShaderPacks.useBuiltIn("normal", "high", Map.of());
                        case "reflections" -> ShaderPacks.useBuiltIn("default", "high",
                                Map.of("upscaling", "off", "reflections", "high"));
                    }
                    if (!guard.equals("requested")) require(!IsometricBridge.rotationConfigured(),
                            "Eligibility guard not established: " + guard);
                    IsoLens lens = lens(1, 137);
                    for (float share : new float[] {0, .5f, 1}) {
                        equal(sample(lens, share), baseline(lens, share));
                        guards++;
                    }
                    require(RotationCamera.savedYawDegrees() == 90,
                            "Blocked transition erased saved rotation: " + guard);
                }
            }
        }
        System.out.println("ROTATION TRANSITION 058 PASS: " + transitions + " actual woven native at/turn entry/exit cases; "
                + guards + " guards; both View.enabled states; seven yaws, three zooms/pans; current rotated "
                + "camera start, native projection/up/target preserved, no accumulated yaw, all touched state restored. "
                + "No game launch, GPU frame or live animation observed.");
    }

    private static void configure(boolean vp, float yaw) {
        Main.compatible = true;
        View.enabled = vp;
        set("vpiso.IsometricBridge", "enabled", true);
        set("vpiso.IsometricBridge", "modelsEnabled", true);
        set("vpiso.IsometricBridge", "failed", false);
        set("vpiso.RotationCamera", "requested", true);
        set("vpiso.RotationCamera", "savedYaw", yaw);
        set("vpiso.RotationCamera", "yaw", vp ? 0f : yaw);
        set("viewpoint.platform.BuildPin", "supported", Boolean.TRUE);
        Graphics.FRAME_GENERATION.set(false);
        set("viewpoint.platform.IrisPacks", "active", null);
        ShaderPacks.useBuiltIn("library", "high", Map.of("upscaling", "off", "reflections", "off"));
    }

    private static IsoLens lens(float zoom, float pan) {
        return new IsoLens().place(64 * (100.25f - 200.75f) - (640 + pan) * zoom,
                32 * (100.25f + 200.75f) - 192 * 2 - (360 - pan * .3f) * zoom,
                1280 * zoom, 720 * zoom, 2, 100.25, 200.75, 2, (float) Math.toRadians(15));
    }

    private static Pose sample(IsoLens lens, float share) throws Exception {
        float[][] before = {FEET.clone(), EYE.clone(), DIR.clone(), UP.clone()};
        Object result = at.invoke(instance, share, lens, FEET, EYE, DIR, UP, 1.1f, 2.5f, 1280f / 720, .05f, 400);
        for (int i = 0; i < before.length; i++)
            require(Arrays.equals(before[i], new float[][] {FEET, EYE, DIR, UP}[i]), "Input array changed");
        return new Pose(((float[]) woven.getField("eye").get(result)).clone(),
                ((float[]) woven.getField("dir").get(result)).clone(),
                ((float[]) woven.getField("up").get(result)).clone(),
                new Matrix4f((Matrix4f) woven.getField("projection").get(result)));
    }

    private static Pose baseline(IsoLens lens, float share) {
        MovePose plain = new MovePose().at(share, lens, FEET, EYE, DIR, UP, 1.1f, 2.5f, 1280f / 720, .05f, 400);
        return new Pose(plain.eye.clone(), plain.dir.clone(), plain.up.clone(), new Matrix4f(plain.projection));
    }
    private record Pose(float[] eye, float[] dir, float[] up, Matrix4f projection) {}
    private static void finite(Pose pose) {
        for (float[] values : new float[][] {pose.eye, pose.dir, pose.up, pose.projection.get(new float[16])})
            for (float value : values) require(Float.isFinite(value), "Nonfinite transition output");
        near((float) Math.sqrt(pose.dir[0] * pose.dir[0] + pose.dir[1] * pose.dir[1] + pose.dir[2] * pose.dir[2]),
                1, "Direction normalization");
    }
    private static void equal(Pose got, Pose expected) {
        equal(got.eye, expected.eye, "Native eye passthrough");
        equal(got.dir, expected.dir, "Native direction passthrough");
        equal(got.up, expected.up, "Native up passthrough");
        require(got.projection.equals(expected.projection, 1e-6f), "Native projection passthrough");
    }
    private static void equal(float[] got, float[] expected, String why) {
        for (int i = 0; i < got.length; i++) near(got[i], expected[i], why);
    }
    private static void near(float got, float expected, String why) {
        require(Float.isFinite(got) && Math.abs(got - expected) < 2e-4f, why + ": " + got + " expected " + expected);
    }
    private static void require(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
    private static void set(String owner, String name, Object value) { GameAccess.set(owner, name, null, value); }
    private static Object allocate(Class<?> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

    private static final class SavedState implements AutoCloseable {
        final Object[] players = (Object[]) GameAccess.field("zombie.characters.IsoPlayer", "players", null);
        final Object[] oldPlayers = players.clone();
        final List<Object[]> fields = new ArrayList<>();
        final boolean framegen = Graphics.FRAME_GENERATION.get();
        SavedState() {
            save("viewpoint.core.View", "enabled"); save("vpiso.Main", "compatible");
            for (String name : new String[] {"enabled", "modelsEnabled", "failed"}) save("vpiso.IsometricBridge", name);
            for (String name : new String[] {"requested", "savedYaw", "yaw", "latest"}) save("vpiso.RotationCamera", name);
            save("viewpoint.platform.BuildPin", "supported");
            save("viewpoint.platform.ShaderPacks", "active"); save("viewpoint.platform.ShaderPacks", "live");
            save("viewpoint.platform.IrisPacks", "active");
        }
        private void save(String owner, String name) {
            fields.add(new Object[] {owner, name, GameAccess.field(owner, name, null)});
        }
        @Override public void close() {
            for (int i = fields.size() - 1; i >= 0; i--) {
                Object[] field = fields.get(i);
                set((String) field[0], (String) field[1], field[2]);
            }
            Graphics.FRAME_GENERATION.set(framegen);
            System.arraycopy(oldPlayers, 0, players, 0, oldPlayers.length);
        }
    }
}
