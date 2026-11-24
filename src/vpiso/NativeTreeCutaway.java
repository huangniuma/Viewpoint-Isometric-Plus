package vpiso;

import java.lang.reflect.Constructor;
import java.util.List;
import zombie.core.textures.TextureDraw;

/** Native stencil candidate records and VP main-view tree suppression. */
public final class NativeTreeCutaway extends TextureDraw.GenericDrawer {
    private static final NativeTreeCutaway BEFORE_MASK = new NativeTreeCutaway();
    private static final String[] PATHS={"media/mask_transparency_player.png","media/mask_transparency_cursor.png"};
    private static Constructor<?> areaConstructor;

    /** The sprite mask needs the native program even when a preceding raw pass changed it. */
    public static void drawStencilMask(Object cell, Object sprites) {
        GameAccess.call("zombie.core.SpriteRenderer", "drawGeneric", sprites,
                new Class<?>[] { TextureDraw.GenericDrawer.class }, BEFORE_MASK);
        GameAccess.call("zombie.iso.IsoCell", "drawStencilMask", cell, new Class<?>[0]);
    }

    @Override public void render() {
        GameAccess.staticCall("zombie.core.ShaderHelper", "forgetCurrentlyBound");
        GameAccess.call("zombie.core.ShaderHelper", "glUseProgramObjectARB", null,
                new Class<?>[] { int.class }, 0);
    }

    public static final String GLSL = """
        void vpIsoCutTree(vec3 rootScene, int plants, int flags) {
          if (vpIsoNativeOn < 0.5) return;
          if (plants == 2) { if ((flags & 268435456) != 0) discard; return; }
          if (plants == 0 || (flags & (4096 | 512)) != 0
              || ((flags >> 19) & 7) != 3) return;
          vec3 world = vec3(vpIsoNativeCam.x-rootScene.x, vpIsoNativeCam.y-rootScene.z,
              vpIsoNativeCam.z+rootScene.y/2.4494896);
          int encoded = int(round(vpIsoNativeRule(world + vec3(0.0,0.0,0.0001)).a*255.0)) & 31;
          // Original sprites, crown replacement and alpha blending are drawn by FBORenderTrees.
          if (encoded != 0) discard;
        }
        """;

    /** Rebuilds only drawStencilMask's CPU records; no native stencil or world draw is issued. */
    @SuppressWarnings("unchecked")
    public static void refresh(Object cell, Object player, int index) {
        List<Object> areas = (List<Object>) GameAccess.call("zombie.iso.IsoCell", "getStencilAreas", cell, new Class<?>[0]);
        areas.clear();
        int width = number("zombie.iso.IsoCamera", "getOffscreenWidth", index);
        int height = number("zombie.iso.IsoCamera", "getOffscreenHeight", index);
        int offX = (int) Math.floor(((Number) GameAccess.staticCall("zombie.iso.IsoCamera", "getRightClickOffX")).doubleValue());
        int offY = (int) Math.floor(((Number) GameAccess.staticCall("zombie.iso.IsoCamera", "getRightClickOffY")).doubleValue());
        addArea(cell, areas, 0, width/2, height/2, offX, offY);
        if ((Boolean) GameAccess.call("zombie.characters.IsoPlayer", "isAnyAimKeyDown", player, new Class<?>[0])) {
            addArea(cell, areas, 1, number("zombie.input.AimingReticle", "getX", index),
                    number("zombie.input.AimingReticle", "getY", index), 0, 0);
        }
    }

    private static int number(String owner, String method, int index) {
        return ((Number) GameAccess.call(owner,method,null,new Class<?>[] {int.class},index)).intValue();
    }

    private static void addArea(Object cell, List<Object> areas, int image, int x, int y, int offX, int offY) {
        Object texture = GameAccess.call("zombie.iso.IsoCell", "getStencilTexture", cell,
                new Class<?>[] {String.class}, PATHS[image]);
        if (texture == null) return;
        int width = ((Number) GameAccess.call("zombie.core.textures.Texture", "getWidth", texture, new Class<?>[0])).intValue();
        int height = ((Number) GameAccess.call("zombie.core.textures.Texture", "getHeight", texture, new Class<?>[0])).intValue();
        int x1 = x-width-offX, y1 = y-height-offY;
        try {
            if (areaConstructor == null) areaConstructor = GameAccess.type("zombie.iso.IsoCell$StencilArea").getConstructor(
                    int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class);
            areas.add(areaConstructor.newInstance(x1,y1,x1+2*width,y1+2*height,x,y,width,height,offX,offY));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native stencil area ABI changed",error); }
    }

    private NativeTreeCutaway() {}
}
