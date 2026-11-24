package vpiso;

/** Immutable settings travel with each queued frame, rather than changing mid-draw. */
public record IsometricEffects(boolean shadows, float shadowStrength, float softness, float distance,
                               int resolution, boolean style, float styleStrength, float exposure,
                               float saturation, float contrast, boolean water, boolean grass, boolean lightweightShadows, boolean snow) {
    public static final IsometricEffects DEFAULT = new IsometricEffects(true, .55f, 1f, 64f, 2048, true, 1f, 1f, 1f, 1f);
    public IsometricEffects(boolean shadows, float shadowStrength, float softness, float distance,
                            int resolution, boolean style, float styleStrength, float exposure,
                            float saturation, float contrast, boolean water, boolean grass, boolean lightweightShadows) {
        this(shadows, shadowStrength, softness, distance, resolution, style, styleStrength, exposure,
                saturation, contrast, water, grass, lightweightShadows, false);
    }
    public IsometricEffects(boolean shadows, float shadowStrength, float softness, float distance,
                            int resolution, boolean style, float styleStrength, float exposure,
                            float saturation, float contrast, boolean water, boolean grass) {
        this(shadows, shadowStrength, softness, distance, resolution, style, styleStrength, exposure,
                saturation, contrast, water, grass, false);
    }
    public IsometricEffects(boolean shadows, float shadowStrength, float softness, float distance,
                            int resolution, boolean style, float styleStrength, float exposure,
                            float saturation, float contrast, boolean water) {
        this(shadows, shadowStrength, softness, distance, resolution, style, styleStrength, exposure,
                saturation, contrast, water, false);
    }
    public IsometricEffects(boolean shadows, float shadowStrength, float softness, float distance,
                            int resolution, boolean style, float styleStrength, float exposure,
                            float saturation, float contrast) {
        this(shadows, shadowStrength, softness, distance, resolution, style, styleStrength, exposure,
                saturation, contrast, false);
    }
    public boolean needsShadows() { return shadows && shadowStrength > 0f; }
    public boolean needsStyle() { return style && styleStrength > 0f; }
    public boolean any() { return needsShadows() || needsStyle() || water || grass || snow; }
    public IsometricEffects withWater(boolean value) {
        return value == water ? this : new IsometricEffects(shadows, shadowStrength, softness, distance,
                resolution, style, styleStrength, exposure, saturation, contrast, value, grass, lightweightShadows, snow);
    }
    public IsometricEffects waterOnly() {
        return new IsometricEffects(false, 0f, softness, distance, resolution, false, 0f,
                exposure, saturation, contrast, water);
    }
}
