package zombie.iso;

/** Compile-only ABI for the Java 25 game's vector; never packaged in the addon. */
public class Vector2 {
    public float x, y;
    public Vector2() {}
    public Vector2 set(float x, float y) { this.x = x; this.y = y; return this; }
}
