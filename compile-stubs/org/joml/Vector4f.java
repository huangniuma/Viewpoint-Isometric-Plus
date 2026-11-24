package org.joml;
// Compilation ABI only. Runtime uses the game's JOML; never packaged.
public class Vector4f {
    public float x, y, z, w;
    public Vector4f(float x, float y, float z, float w) {}
    public Vector4f div(float value) { throw new UnsupportedOperationException(); }
}
