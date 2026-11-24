package zombie.core.textures;

// Compilation ABI only, checked against javap output. Never included in the mod JAR.
public final class TextureDraw {
    public abstract static class GenericDrawer {
        public abstract void render();
        public void render(TextureDraw draw) { render(); }
        public void postRender() {}
    }
}
