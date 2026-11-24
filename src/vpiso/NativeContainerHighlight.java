package vpiso;

import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.*;
import se.krka.kahlua.vm.KahluaTable;
import viewpoint.core.Frame;
import viewpoint.platform.Gl;
import viewpoint.platform.GlProgram;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** Copy the native inventory selection into its owning model frame, never into shared meshes. */
public final class NativeContainerHighlight {
    private static final ThreadLocal<NativeContainerHighlight> CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<NativeContainerHighlight> RENDER = new ThreadLocal<>();
    private static final String OBJECT = "zombie.iso.IsoObject";
    private static final Class<?>[] PLAYER = { int.class };
    private static final String OUTLINE = "viewpoint.render.TargetOutline";
    private static final Class<?>[] CONTEXT = { FrameContext.class };
    private Object object, square;
    private ChunkMeshData mesh;
    private int owner, objectId, tile;
    private float r, g, b, a, thickness;
    private boolean fill, edge;
    private static GlProgram composite;

    public void begin() { object = square = null; mesh = null; fill = edge = false; CAPTURE.set(this); }
    public static void end() { CAPTURE.remove(); }
    public void beginRender() { RENDER.set(this); }
    public static void endRender() { RENDER.remove(); }
    public static boolean ownsMask() {
        NativeContainerHighlight active = RENDER.get();
        return active != null && (active.fill || active.edge);
    }

    /** Called after VP's loot snapshot, before its rigid and corpse model capture. */
    public static void snapshot(Frame frame) {
        NativeContainerHighlight active = CAPTURE.get();
        if (active == null) return;
        active.select(frame);
    }

    private void select(Frame frame) {
        Object data = GameAccess.call("viewpoint.building.BuildDriver", "call", null,
                new Class<?>[] { String.class, Object[].class }, "getPlayerData", new Object[] { 0.0 });
        for (String key : List.of("lootInventory", "playerInventory")) {
            Object page = field(data, key);
            Object container = field(page, "coloredInv");
            if (container == null || !container.equals(field(page, "inventory"))
                    || Boolean.TRUE.equals(field(page, "isCollapsed"))) continue;
            Object parent = GameAccess.call("zombie.inventory.ItemContainer", "getParent", container, new Class<?>[0]);
            if (parent == null) {
                Object item = GameAccess.call("zombie.inventory.ItemContainer", "getContainingItem", container, new Class<?>[0]);
                if (item != null) parent = GameAccess.call("zombie.inventory.InventoryItem", "getWorldItem", item, new Class<?>[0]);
            }
            if (parent == null || GameAccess.type("zombie.characters.IsoPlayer").isInstance(parent)) continue;
            boolean highlighted = (Boolean) GameAccess.call(OBJECT, "isHighlighted", parent, PLAYER, 0);
            boolean outlined = (Boolean) GameAccess.call(OBJECT, "isOutlineHighlight", parent, PLAYER, 0);
            if (!highlighted && !outlined) continue;
            Object sq = GameAccess.call(OBJECT, "getSquare", parent, new Class<?>[0]);
            if (sq == null) continue;
            object = parent; square = sq; fill = highlighted; edge = outlined;
            Object color = GameAccess.call(OBJECT, "getHighlightColor", parent, PLAYER, 0);
            r = color(color, "r"); g = color(color, "g"); b = color(color, "b"); a = color(color, "a");
            if (outlined) {
                int packed = ((Number) GameAccess.call(OBJECT, "getOutlineHighlightCol", parent, PLAYER, 0)).intValue();
                // Native Colour ABGR, copied before the render thread can observe another selection.
                outlineR = (packed & 255) / 255f; outlineG = (packed >>> 8 & 255) / 255f;
                outlineB = (packed >>> 16 & 255) / 255f; outlineA = (packed >>> 24) / 255f;
            }
            thickness = ((Number) GameAccess.call(OBJECT, "getOutlineThickness", parent, new Class<?>[0])).floatValue();
            GameAccess.set("viewpoint.core.Frame", "lootObject", frame, parent);
            GameAccess.set("viewpoint.core.Frame", "lootSquare", frame, sq);
            GameAccess.set("viewpoint.core.Frame", "lootVehicle", frame,
                    GameAccess.type("zombie.vehicles.BaseVehicle").isInstance(parent) ? parent : null);
            GameAccess.set("viewpoint.core.Frame", "lootCharacter", frame, null);
            frame.lootItems.clear();
            return;
        }
    }

    private float outlineR, outlineG, outlineB, outlineA;

    public void resolve(Frame frame) {
        if (object == null) return;
        mesh = (ChunkMeshData) GameAccess.call("viewpoint.world.ChunkCache", "meshOn", null,
                new Class<?>[] { GameAccess.type("zombie.iso.IsoGridSquare") }, square);
        int x = ((Number) GameAccess.call("zombie.iso.IsoGridSquare", "getX", square, new Class<?>[0])).intValue();
        int y = ((Number) GameAccess.call("zombie.iso.IsoGridSquare", "getY", square, new Class<?>[0])).intValue();
        tile = Math.floorMod(y, 8) * 8 + Math.floorMod(x, 8);
        List<?> objects = (List<?>) GameAccess.call("zombie.iso.IsoGridSquare", "getObjects", square, new Class<?>[0]);
        int index = objects.indexOf(object);
        objectId = index < 0 ? -1 : tile << 16 | index;
        Object sprite = GameAccess.call(OBJECT, "getSprite", object, new Class<?>[0]);
        int kind = sprite == null ? 0 : ((Number) GameAccess.call("viewpoint.visibility.Edges", "ownerKind", null,
                new Class<?>[] { GameAccess.type(OBJECT), GameAccess.type("zombie.iso.sprite.IsoSprite") }, object, sprite)).intValue();
        owner = tile << 2 | Math.max(0, kind);
        // Moving bodies, vehicles and world items use their exact model target, never every object on the tile.
        if (index < 0) mesh = null;
        object = square = null;
    }

    public void draw(Frame frame) {
        if (!fill && !edge) return;
        if (mesh == null && !(Boolean) GameAccess.field("viewpoint.render.ModelDraws", "targeted", frame.scene.models)) return;
        FrameContext context = (FrameContext) GameAccess.field("viewpoint.render.WorldRenderer", "frame", null);
        Object target = GameAccess.field("viewpoint.render.WorldRenderer", "target", null);
        Object models = GameAccess.field("viewpoint.render.WorldRenderer", "models", null);
        SceneData scene = frame.scene;
        Object previousMesh = GameAccess.field("viewpoint.render.SceneData", "targetMesh", scene);
        int previousOwner = integer(scene, "targetOwner"), previousObject = integer(scene, "targetObject"), previousTile = integer(scene, "targetSquare");
        try {
            GameAccess.set("viewpoint.render.SceneData", "targetMesh", scene, mesh);
            GameAccess.set("viewpoint.render.SceneData", "targetOwner", scene, owner);
            GameAccess.set("viewpoint.render.SceneData", "targetObject", scene, objectId);
            GameAccess.set("viewpoint.render.SceneData", "targetSquare", scene, tile);
            GameAccess.call(OUTLINE, "ensure", target, new Class<?>[] { int.class, int.class }, context.outputWidth, context.outputHeight);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ((Number) GameAccess.field(OUTLINE, "maskFbo", target)).intValue());
            GL11.glViewport(0, 0, context.outputWidth, context.outputHeight);
            Gl.resetState(); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glClearColor(0, 0, 0, 0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GameAccess.call(OUTLINE, "world", target, CONTEXT, context);
            GameAccess.call("viewpoint.render.ModelPass", "targets", models, CONTEXT, context);
            composite(context, ((Number) GameAccess.field(OUTLINE, "maskTexture", target)).intValue());
        } finally {
            GameAccess.set("viewpoint.render.SceneData", "targetMesh", scene, previousMesh);
            GameAccess.set("viewpoint.render.SceneData", "targetOwner", scene, previousOwner);
            GameAccess.set("viewpoint.render.SceneData", "targetObject", scene, previousObject);
            GameAccess.set("viewpoint.render.SceneData", "targetSquare", scene, previousTile);
        }
    }

    private void composite(FrameContext context, int texture) {
        if (composite == null) composite = GlProgram.fromSources("VPISO native container highlight",
                new GlProgram.Sources(VERTEX, null, null, null, FRAGMENT), Map.of(), Map.of(), List.of("VPISO native container highlight"));
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ((Number) GameAccess.field("viewpoint.render.FrameContext", "outputDrawFbo", context)).intValue());
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ((Number) GameAccess.field("viewpoint.render.FrameContext", "outputReadFbo", context)).intValue());
        int[] viewport = (int[]) GameAccess.field("viewpoint.render.FrameContext", "viewport", context);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        Gl.resetState(); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND); GL11.glColorMask(true, true, true, false);
        composite.use(); composite.setInt("uMask", 0);
        composite.set("uColour", r, g, b, a); composite.set("uOutline", outlineR, outlineG, outlineB, outlineA);
        composite.set("uPixel", 1f / context.outputWidth, 1f / context.outputHeight);
        composite.set("uThickness", Math.min(8f, Math.max(1f, thickness)));
        Gl.bind(0, texture); GL33.glBindSampler(0, 0);
        if (fill) {
            composite.setInt("uPass", 0); GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO); Gl.screenTriangle();
        }
        if (edge) {
            composite.setInt("uPass", 1); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); Gl.screenTriangle();
        }
        Gl.bind(0, 0); GL11.glColorMask(true, true, true, true); GL11.glDisable(GL11.GL_BLEND);
    }

    private static Object field(Object table, String key) { return table instanceof KahluaTable t ? t.rawget(key) : null; }
    private static float color(Object value, String key) { return ((Number) GameAccess.field("zombie.core.textures.ColorInfo", key, value)).floatValue(); }
    private static int integer(SceneData scene, String key) { return ((Number) GameAccess.field("viewpoint.render.SceneData", key, scene)).intValue(); }

    public static final String VERTEX = """
        #version 330 compatibility
        out vec2 uv;
        void main() { vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); uv=p; gl_Position=vec4(p*2.0-1.0,0,1); }
        """;
    public static final String FRAGMENT = """
        #version 330 compatibility
        in vec2 uv;
        uniform sampler2D uMask;
        uniform vec4 uColour, uOutline;
        uniform vec2 uPixel;
        uniform float uThickness;
        uniform int uPass;
        out vec4 colour;
        void main() {
            float mask=texture(uMask,uv).r;
            if(uPass==0) { colour=vec4(mix(vec3(1),uColour.rgb,mask*uColour.a),1); return; }
            float expanded=mask;
            for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++)
                expanded=max(expanded,texture(uMask,uv+vec2(x,y)*uPixel*uThickness).r);
            colour=vec4(uOutline.rgb,max(0.0,expanded-mask)*uOutline.a);
        }
        """;
}
