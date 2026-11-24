package vpiso;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Reprojects native water geometry and its world-anchored pattern, preserving the native shader. */
public final class RotationWater {
    private static final String WATER = "zombie.iso.IsoWater";
    private static final String DATA = "zombie.iso.IsoWater$RenderData";
    private static final String SQUARE = "zombie.iso.IsoGridSquare";
    private static final Class<?>[] NONE = new Class<?>[0];
    private static final Map<Object, List<Batch>> BATCHES = new IdentityHashMap<>();
    private static final ThreadLocal<Batch> DRAWING = new ThreadLocal<>();
    private static final Pattern ENTRY = Pattern.compile("mainImage\\(\\s*gl_FragColor\\s*,\\s*vec2\\(\\s*gl_FragCoord\\.x\\s*/\\s*WViewport\\.z\\s*,\\s*1\\.0\\s*-\\s*gl_FragCoord\\.y\\s*/\\s*WViewport\\.w\\s*\\)\\s*\\)\\s*;");
    private static final Pattern SURFACE_UV = Pattern.compile("vec2\\s+uv\\s*=\\s*vec2\\(\\s*fragCoord\\.x\\s*\\*\\s*WOffset\\.z\\s*\\*\\s*0\\.0008\\s*,\\s*fragCoord\\.y\\s*\\*\\s*WOffset\\.a\\s*\\*\\s*0\\.0008\\s*\\)\\s*\\+\\s*WOffset\\.xy\\s*\\*\\s*0\\.0008\\s*;");
    private static final String GLSL = """
        // VPISO_ROTATED_NATIVE_WATER
        uniform float vpIsoWaterRotation;
        uniform mat4 vpIsoWaterInverse;
        uniform vec4 vpIsoWaterNativeX, vpIsoWaterNativeY;
        uniform vec4 vpIsoWaterViewport;
        uniform float vpIsoWaterLevel;
        vec2 vpIsoWaterSurfaceUV(vec2 fragCoord) {
          if (vpIsoWaterRotation < 0.5)
            return vec2(fragCoord.x*WOffset.z*0.0008,fragCoord.y*WOffset.a*0.0008)+WOffset.xy*0.0008;
          vec2 uv=(gl_FragCoord.xy-vpIsoWaterViewport.xy)/vpIsoWaterViewport.zw;
          vec4 a=vpIsoWaterInverse*vec4(uv*2.0-1.0,-1.0,1.0);
          vec4 b=vpIsoWaterInverse*vec4(uv*2.0-1.0, 1.0,1.0);
          a/=a.w; b/=b.w;
          vec3 scene=mix(a.xyz,b.xyz,(vpIsoWaterLevel-a.y)/(b.y-a.y));
          vec4 delta=vec4(-scene.x,-scene.z,scene.y/2.4494896,1.0);
          // The native shader's final water coordinates are absolute isometric pixels.
          // Do not convert through another viewport/zoom and apply WOffset a second time.
          return vec2(dot(vpIsoWaterNativeX,delta),dot(vpIsoWaterNativeY,delta))*0.0008;
        }
        """;

    private record Batch(int start, int end, RotationCamera.Snapshot camera, float plane,
                         float[] nativeX, float[] nativeY) {}

    /** Native RenderData.clear marks buffer reuse even after the addon stops producing water. */
    public static void clear(Object data) {
        synchronized (BATCHES) { BATCHES.remove(data); }
    }

    /** Call for every native producer append, including unrotated frames (camera == null). */
    public static void reproject(Object data, List<?> squares, int start, int end, RotationCamera.Snapshot camera) {
        if (start == 0 || camera == null) clear(data);
        if (camera == null || end <= start) return;
        if (end - start != squares.size())
            throw new IllegalStateException("Native water append no longer matches its source squares");
        float[] vertices = (float[]) GameAccess.field(DATA, "data", data);
        float[] point = new float[3];
        for (int square = start; square < end; square++) {
            Object original = squares.get(square - start);
            double x = number(GameAccess.field(SQUARE, "x", original));
            double y = number(GameAccess.field(SQUARE, "y", original));
            double z = number(GameAccess.field(SQUARE, "z", original));
            Object geometry = GameAccess.call(SQUARE, "getWater", original, NONE);
            float[] geometryX = (float[]) GameAccess.field("zombie.iso.IsoWaterGeometry", "x", geometry);
            float[] geometryY = (float[]) GameAccess.field("zombie.iso.IsoWaterGeometry", "y", geometry);
            double bx = nativeScreen("XToScreen", (float)x, (float)y, (float)z);
            double by = nativeScreen("YToScreen", (float)x, (float)y, (float)z);
            double ex = nativeScreen("XToScreen", (float)x + 1, (float)y, (float)z) - bx;
            double ey = nativeScreen("YToScreen", (float)x + 1, (float)y, (float)z) - by;
            double sx = nativeScreen("XToScreen", (float)x, (float)y + 1, (float)z) - bx;
            double sy = nativeScreen("YToScreen", (float)x, (float)y + 1, (float)z) - by;
            double determinant = ex * sy - sx * ey;
            if (Math.abs(determinant) < 1e-7) throw new IllegalStateException("Native water projection is singular");
            for (int corner = 0; corner < 4; corner++) {
                // Follow the actual native geometry order and its small shoreline overlaps.
                double dx = geometryX[corner] - bx, dy = geometryY[corner] - by;
                double wx = x + (dx * sy - sx * dy) / determinant;
                double wy = y + (ex * dy - dx * ey) / determinant;
                if (!camera.project(wx, wy, z, point))
                    throw new IllegalStateException("Cannot project native water corner");
                int offset = square * 32 + corner * 8;
                // Keep raster coordinates local. Adding and cancelling native map offsets
                // loses subpixel precision, enough to reverse the coplanar shore depth test.
                vertices[offset + 4] = point[0] * camera.zoom();
                vertices[offset + 5] = point[1] * camera.zoom();
                // Original depth biases use another depth encoding. The rotated path uses VP depth.
                vertices[offset + 7] = point[2] + .000001f;
            }
        }
        float[] nx = new float[4], ny = new float[4];
        float baseX = nativeScreen("XToScreen", camera.camX(), camera.camY(), camera.camZ());
        float baseY = nativeScreen("YToScreen", camera.camX(), camera.camY(), camera.camZ());
        // These are the fixed offsets applied by the original WaterShader.updateWaterParams.
        nx[3] = baseX - 90000f; ny[3] = baseY - 640000f;
        for (int axis = 0; axis < 3; axis++) {
            float x = camera.camX() + (axis == 0 ? 1 : 0);
            float y = camera.camY() + (axis == 1 ? 1 : 0);
            float z = camera.camZ() + (axis == 2 ? 1 : 0);
            nx[axis] = nativeScreen("XToScreen", x, y, z) - baseX;
            ny[axis] = nativeScreen("YToScreen", x, y, z) - baseY;
        }
        float level = number(GameAccess.field(SQUARE, "z", squares.get(0)));
        Batch batch = new Batch(start, end, camera, (level - camera.camZ()) * 2.4494896f, nx, ny);
        synchronized (BATCHES) { BATCHES.computeIfAbsent(data, ignored -> new ArrayList<>()).add(batch); }
    }

    public static Object enter(int player, int start, boolean shore) {
        Batch previous = DRAWING.get(), next = null;
        Object sprites = GameAccess.field("zombie.core.SpriteRenderer", "instance", null);
        int state = ((Number) GameAccess.call("zombie.core.SpriteRenderer", "getRenderStateIndex", sprites, NONE)).intValue();
        Object water = GameAccess.staticCall(WATER, "getInstance");
        Object data = ((Object[][]) GameAccess.field(WATER, shore ? "renderDataShore" : "renderData", water))[state][player];
        synchronized (BATCHES) {
            List<Batch> batches = BATCHES.get(data);
            if (batches != null) for (Batch batch : batches) if (start >= batch.start && start < batch.end) {
                next = batch;
                break;
            }
        }
        if (next == null) DRAWING.remove(); else DRAWING.set(next);
        return previous;
    }

    public static void leave(Object previous) {
        if (previous == null) DRAWING.remove(); else DRAWING.set((Batch) previous);
    }

    /** Paired with local vertices, only inside the queued rotated native water draw. */
    public static void projection(Matrix4f matrix) {
        Batch batch = DRAWING.get();
        if (batch == null) return;
        matrix.setOrtho(0f, batch.camera.width() * batch.camera.zoom(),
                batch.camera.height() * batch.camera.zoom(), 0f, -1f, 1f);
    }

    /** The original renderer has bound its shader and uploaded all normal water uniforms. */
    public static void uniforms() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) return;
        int enabled = GL20.glGetUniformLocation(program, "vpIsoWaterRotation");
        if (enabled < 0) return;
        Batch batch = DRAWING.get();
        GL20.glUniform1f(enabled, batch == null ? 0f : 1f);
        if (batch == null) return;
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "vpIsoWaterInverse"), false,
                batch.camera.inverse().get(new float[16]));
        vector(program, "vpIsoWaterNativeX", batch.nativeX);
        vector(program, "vpIsoWaterNativeY", batch.nativeY);
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL20.glUniform4f(GL20.glGetUniformLocation(program, "vpIsoWaterViewport"),
                viewport[0], viewport[1], viewport[2], viewport[3]);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "vpIsoWaterLevel"), batch.plane);
    }

    public static String rewrite(String path, String source) {
        if (source == null || source.contains("VPISO_ROTATED_NATIVE_WATER")) return source;
        String file = path.replace('\\', '/');
        if (!file.endsWith("/water.frag") && !file.endsWith("/water_hq.frag")) return source;
        if (!ENTRY.matcher(source).find()) return source;
        var surface = SURFACE_UV.matcher(source);
        if (!surface.find()) return source;
        String changed = surface.replaceFirst("vec2 uv = vpIsoWaterSurfaceUV(fragCoord);");
        int main = changed.indexOf("void mainImage");
        if (main < 0) return source;
        return changed.substring(0, main) + GLSL + '\n' + changed.substring(main);
    }

    private static void vector(int program, String name, float[] value) {
        GL20.glUniform4f(GL20.glGetUniformLocation(program, name), value[0], value[1], value[2], value[3]);
    }
    private static float nativeScreen(String method, float x, float y, float z) {
        return number(GameAccess.call("zombie.iso.IsoUtils", method, null,
                new Class<?>[] { float.class, float.class, float.class, int.class }, x, y, z, 0));
    }
    private static float number(Object value) { return ((Number) value).floatValue(); }
    private RotationWater() {}
}
