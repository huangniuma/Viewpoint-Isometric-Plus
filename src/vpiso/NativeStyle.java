package vpiso;

/** Optional color grading of the native LDR picture, independent of geometry and shadows.
 * Preserves native low light before Library's shoulder; native LDR input does not reconstruct HDR lighting.
 */
public final class NativeStyle {
    public static final String GLSL = """
        uniform float vpIsoStyleOn;
        uniform float vpIsoStyleStrength;
        uniform float vpIsoStyleExposure;
        uniform float vpIsoStyleSaturation;
        uniform float vpIsoStyleContrast;

        vec4 vpIsoStyle(vec4 nativeColor) {
          float amount = clamp(vpIsoStyleStrength, 0.0, 1.0);
          if (vpIsoStyleOn < 0.5 || amount == 0.0) return nativeColor;
          vec3 graded = max(nativeColor.rgb, vec3(0.0)) * clamp(vpIsoStyleExposure, 0.0, 8.0);
          float luma = dot(graded, vec3(0.2126, 0.7152, 0.0722));
          graded = max(mix(vec3(luma), graded, clamp(vpIsoStyleSaturation, 0.0, 2.0)), vec3(0.0));
          // Power contrast keeps a zero visibility pixel at zero, with no additive lift.
          graded = pow(graded, vec3(clamp(vpIsoStyleContrast, 0.25, 3.0)));
          // Native room and lamp shading is already baked into this SDR picture.
          // Shape the brighter midtones without crushing its low-light detail again.
          vec3 sdr = min(graded, vec3(1.0));
          graded += 0.5 * max(sdr * sdr * (3.0 - 2.0 * sdr) - sdr, vec3(0.0));
          const float knee = 0.8;
          vec3 excess = max(graded - knee, vec3(0.0));
          graded = min(graded, vec3(knee)) + (1.0 - knee) * (1.0 - exp(-excess / (1.0 - knee)));
          return vec4(mix(nativeColor.rgb, graded, amount), nativeColor.a);
        }
        """;

    private NativeStyle() {}
}
