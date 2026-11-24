import org.joml.Matrix4f;
import org.joml.Vector4f;
import vpiso.GameAccess;
import vpiso.RotationCamera;
import vpiso.RotationBounds;
import vpiso.RotationLayers;
import vpiso.RotationTrees;

/** Uses actual JOML and original native matrix stacks, without starting a game or a save. */
public final class RotationLayersProbe {
    private static final float STOREY = 2.4494896f;

    public static void main(String[] args) throws Exception {
        int cases = 0;
        for (int q = 0; q < 4; q++) for (float zoom : new float[] {.5f, 1f, 2f}) {
            RotationCamera.Snapshot camera = camera(q, zoom);
            float[] p = new float[3];
            if (!camera.project(14.5, 9.5, 2.25, p)) throw new AssertionError("project");
            if (RotationLayers.projectAxis(14.5f, 9.5f, 2.25f, 0) != null)
                throw new AssertionError("Native camera changed outside queued scope");
            RotationCamera.Snapshot previous = RotationLayers.begin(camera);
            try {
                near("offscreen X", RotationLayers.projectAxis(14.5f, 9.5f, 2.25f, 0), p[0] * zoom, .001f);
                near("offscreen Y", RotationLayers.projectAxis(14.5f, 9.5f, 2.25f, 1), p[1] * zoom, .001f);
                Object results = Class.forName("zombie.iso.IsoDepthHelper$Results").getDeclaredConstructor().newInstance();
                GameAccess.set("zombie.iso.IsoDepthHelper$Results", "depthStart", results, .123f);
                RotationLayers.depth(results, 14.5f, 9.5f, 2.25f);
                near("actual camera depth", ((Number) GameAccess.field("zombie.iso.IsoDepthHelper$Results",
                        "depthStart", results)).floatValue(), p[2], 1e-6f);
                RotationCamera.Snapshot nested = RotationLayers.begin(null);
                if (RotationLayers.current() != null) throw new AssertionError("Native nested scope");
                RotationLayers.end(nested);
                if (RotationLayers.current() != camera) throw new AssertionError("Previous queued camera lost");
            } finally { RotationLayers.end(previous); }
            if (RotationLayers.current() != null) throw new AssertionError("Camera scope leaked");
            treeMatrix(camera);
            cases++;
        }
        bounds();
        queuedVision();
        System.out.println("Rotation native layers PASS: " + cases
                + " angle/zoom cases; queued/nested scope isolation, native offscreen pixel scale and actual depth;"
                + " original MatrixStack projection/modelview, world-root/vertical alignment and camera-facing tree widths."
                + " CPU controlled inputs; no map or actual tree-texture/stencil GPU acceptance.");
    }

    private static void bounds() throws Exception {
        int accepted = 0, rejected = 0, newlyVisible = 0;
        RotationCamera.Snapshot nativeCamera = camera(0, 1f);
        float[] point = new float[3];
        for (int q = 0; q < 4; q++) {
            RotationCamera.Snapshot camera = camera(q, 1f);
            for (int x = -80; x <= 80; x += 4) for (int y = -80; y <= 80; y += 4) {
                boolean visible = RotationBounds.visible(camera, x, y, 1, x + 8, y + 8, 2);
                if (visible) accepted++; else rejected++;
                camera.project(x + 4, y + 4, 1, point);
                boolean centre = point[0] >= 0 && point[0] <= camera.width()
                        && point[1] >= 0 && point[1] <= camera.height() && point[2] >= 0 && point[2] <= 1;
                if (centre && !visible) throw new AssertionError("Rotated native prepass culled visible chunk");
                nativeCamera.project(x + 4, y + 4, 1, point);
                boolean nativeCentre = point[0] >= 0 && point[0] <= camera.width()
                        && point[1] >= 0 && point[1] <= camera.height();
                if (centre && !nativeCentre) newlyVisible++;
            }
        }
        if (rejected <= accepted * 3 || newlyVisible == 0)
            throw new AssertionError("Bounds failed to reject remote chunks or add rotated-only coverage: "
                    + accepted + "/" + rejected + "/" + newlyVisible);
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        var singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Object chunk = unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, Class.forName("zombie.iso.IsoChunk"));
        GameAccess.set("zombie.iso.IsoChunk", "wx", chunk, 1);
        GameAccess.set("zombie.iso.IsoChunk", "wy", chunk, 1);
        GameAccess.set("zombie.iso.IsoChunk", "minLevel", chunk, 1);
        GameAccess.set("zombie.iso.IsoChunk", "maxLevel", chunk, 1);
        if (RotationBounds.chunk(chunk, false)) throw new AssertionError("Native bounds changed outside scope");
        RotationCamera.Snapshot previous = RotationBounds.begin(camera(1, 1));
        try {
            if (!RotationBounds.chunk(chunk, false)) throw new AssertionError("Rotated centre chunk excluded");
            GameAccess.set("zombie.iso.IsoChunk", "wx", chunk, 300);
            GameAccess.set("zombie.iso.IsoChunk", "wy", chunk, 300);
            if (RotationBounds.chunk(chunk, false)) throw new AssertionError("Remote chunk included");
            if (!RotationBounds.chunk(chunk, true)) throw new AssertionError("Original visibility was lost");
            RotationCamera.Snapshot nested = RotationBounds.begin(null);
            if (RotationBounds.chunk(chunk, false)) throw new AssertionError("Bounds scope leaked into native path");
            RotationBounds.end(nested);
        } finally { RotationBounds.end(previous); }
        if (RotationBounds.chunk(chunk, false)) throw new AssertionError("Bounds scope leaked after render");
        System.out.println("Rotation bounds PASS: actual chunk fields and scoped union; " + newlyVisible
                + " rotated-only sample centres retained, " + rejected + " distant chunk boxes rejected, "
                + accepted + " conservative boxes kept; all visible sample centres covered.");
    }

    @SuppressWarnings("unchecked")
    private static void queuedVision() {
        Object vision=GameAccess.staticCall("zombie.vispoly.VisibilityPolygon2","getInstance");
        Object sprites=GameAccess.field("zombie.core.SpriteRenderer","instance",null);
        int index=((Number)GameAccess.call("zombie.core.SpriteRenderer","getMainStateIndex",sprites,new Class<?>[0])).intValue();
        Object drawer=((Object[][])GameAccess.field("zombie.vispoly.VisibilityPolygon2","drawers",vision))[0][index];
        var queuing=(ThreadLocal<RotationCamera.Snapshot>)GameAccess.field("vpiso.RotationLayers","QUEUING",null);
        var camera=camera(1,1);
        queuing.set(camera);
        try {
            vpiso.Patch_RotationVisionQueue.enter(0);
            var previous=RotationLayers.enterVision(drawer);
            if(RotationLayers.current()!=camera) throw new AssertionError("Queued vision lost its camera");
            RotationLayers.end(previous);
            vpiso.Patch_RotationVisionQueue.enter(0); // A frame may be queued but dropped before rendering.
            queuing.remove();
            vpiso.Patch_RotationVisionQueue.enter(0); // Native renderer reuses the same drawer.
            previous=RotationLayers.enterVision(drawer);
            if(RotationLayers.current()!=null) throw new AssertionError("Old vision camera leaked into native reuse");
            RotationLayers.end(previous);
        } finally { queuing.remove(); }
        System.out.println("Rotation vision queue PASS: actual native drawer identity, queued camera and dropped-frame/native reuse clearing.");
    }

    private static RotationCamera.Snapshot camera(int q, float zoom) {
        float yaw = (float) Math.toRadians(-135 + 90 * q), pitch = (float) Math.toRadians(-30);
        float dx = -(float) (Math.cos(yaw) * Math.cos(pitch));
        float dy = (float) Math.sin(pitch);
        float dz = -(float) (Math.sin(yaw) * Math.cos(pitch));
        Matrix4f view = new Matrix4f().setLookAt(-dx * 6000, -dy * 6000, -dz * 6000,
                0, 0, 0, 0, 1, 0);
        Matrix4f projection = new Matrix4f().setPerspective(2f * (float) Math.atan(12 * zoom / 6000),
                16f / 9f, 5744, 6256);
        return new RotationCamera.Snapshot(projection, view, 10.25f, 12.5f, 1f, 1920, 1080, zoom, q);
    }

    private static void treeMatrix(RotationCamera.Snapshot camera) {
        Object core = GameAccess.staticCall("zombie.core.Core", "getInstance");
        Object projectionStack = GameAccess.field("zombie.core.Core", "projectionMatrixStack", core);
        Object viewStack = GameAccess.field("zombie.core.Core", "modelViewMatrixStack", core);
        RotationCamera.Snapshot previous = RotationTrees.begin(camera);
        boolean projectionPushed = false, viewPushed = false;
        try {
            projectionPushed = RotationTrees.projection();
            viewPushed = RotationTrees.modelView(13f, 15f, 1f);
            if (!projectionPushed || !viewPushed) throw new AssertionError("Native tree matrices missing");
            Matrix4f projection = (Matrix4f) GameAccess.call("zombie.core.opengl.MatrixStack", "peek",
                    projectionStack, new Class<?>[0]);
            Matrix4f view = (Matrix4f) GameAccess.call("zombie.core.opengl.MatrixStack", "peek",
                    viewStack, new Class<?>[0]);
            Matrix4f combined = new Matrix4f(projection).mul(view);
            float[] p = project(combined, 0, 2f, 0, camera.width(), camera.height());
            float[] world = new float[3];
            camera.project(13, 15, 1 + (2f - .71999997f) / STOREY, world);
            near("native tree root X", p[0], world[0], .15f);
            near("native tree height", p[1], world[1], .15f);
            near("native tree actual depth", p[2], world[2], .00001f);
            float[] right = project(combined, 1, 2f, 0, camera.width(), camera.height());
            if (right[0] <= p[0]) throw new AssertionError("Tree billboard mirrored or edge-on");
            near("horizontal tree edge stays level", right[1], p[1], .15f);
            near("horizontal tree edge actual depth", right[2], p[2], .00001f);
        } finally {
            if (viewPushed) GameAccess.call("zombie.core.opengl.MatrixStack", "pop", viewStack, new Class<?>[0]);
            if (projectionPushed) GameAccess.call("zombie.core.opengl.MatrixStack", "pop", projectionStack, new Class<?>[0]);
            RotationTrees.end(previous);
        }
        if (RotationTrees.active()) throw new AssertionError("Native tree camera leaked");
    }

    private static float[] project(Matrix4f matrix, float x, float y, float z, int width, int height) {
        Vector4f p = matrix.transform(new Vector4f(x, y, z, 1));
        return new float[] {(p.x / p.w + 1) * width * .5f, (1 - p.y / p.w) * height * .5f,
                (p.z / p.w + 1) * .5f};
    }

    private static void near(String label, float value, float expected, float tolerance) {
        if (!Float.isFinite(value) || Math.abs(value - expected) > tolerance)
            throw new AssertionError(label + ": " + value + " != " + expected);
    }
}
