package zombie.iso;
public class IsoObject {
    public zombie.iso.sprite.IsoSprite sprite;
    public zombie.inventory.ItemContainer container;
    public boolean noPicking;
    public zombie.iso.sprite.IsoSprite getSprite() { return null; }
    public IsoGridSquare getSquare() { return null; }
    public zombie.iso.sprite.IsoSpriteGrid getSpriteGrid() { return null; }
    public zombie.iso.fboRenderChunk.ObjectRenderInfo getRenderInfo(int player) { return null; }
    public void setTargetAlpha(int player, float alpha) {}
    public float getAlpha(int player) { return 0f; }
    public boolean isWallSE() { return false; }
}
