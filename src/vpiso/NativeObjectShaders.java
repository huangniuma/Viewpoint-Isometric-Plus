package vpiso;

import java.util.Set;

/** Colour-only source alpha and a small, occlusion-aware interaction query; no changes to shadow shaders. */
public final class NativeObjectShaders {
    private static final String VERTEX = """
        uniform int vpIsoObjectsOn;
        uniform usamplerBuffer vpIsoObjectTable;
        uniform int vpIsoObjectStart0, vpIsoObjectCount0;
        uniform int vpIsoObjectStart1, vpIsoObjectCount1;
        uniform int vpIsoObjectStart2, vpIsoObjectCount2;
        flat out vec3 vpIsoObjectInfo;
        float vpIsoWallCode;
        vec3 vpIsoSource(int kind, int vertex) {
          vpIsoWallCode = 0.0;
          if (vpIsoObjectsOn == 0) return vec3(1.0, 0.0, 0.0);
          int lo = kind == 0 ? vpIsoObjectStart0 : kind == 1 ? vpIsoObjectStart1 : vpIsoObjectStart2;
          int hi = lo + (kind == 0 ? vpIsoObjectCount0 : kind == 1 ? vpIsoObjectCount1 : vpIsoObjectCount2);
          while (lo < hi) {
            int mid = (lo + hi) / 2;
            uvec4 range = texelFetch(vpIsoObjectTable, mid);
            if (vertex < int(range.x)) hi = mid;
            else if (vertex >= int(range.y)) lo = mid + 1;
            else {
              vpIsoWallCode = float((range.w >> 16) & 511u);
              return vec3(uintBitsToFloat(range.z), float(range.w & 65535u), float(range.w >> 30));
            }
          }
          return vec3(1.0, 0.0, 0.0);
        }
        """;
    private static final String FRAGMENT = """
        uniform int vpIsoObjectsOn, vpIsoPickOn, vpIsoPickBits;
        uniform vec2 vpIsoPickPixel, vpIsoPickScale;
        flat in vec3 vpIsoObjectInfo;
        layout(std430, binding = 6) buffer VpIsoPickDepths { uint vpIsoPickDepths[]; };
        void vpIsoObjectFade() {
          if (vpIsoObjectsOn == 0) return;
          float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
          if (vpIsoObjectInfo.x <= noise) discard;
        }
        ivec2 vpIsoPickOffset() {
          return ivec2(floor((gl_FragCoord.xy - vpIsoPickPixel) / max(vpIsoPickScale, vec2(0.01)) + 0.5));
        }
        bool vpIsoPickStable() {
          if (vpIsoObjectsOn == 0 || vpIsoPickOn == 0 || vpIsoObjectInfo.z < 0.5 || vpIsoObjectInfo.x <= 0.0) return false;
          ivec2 off = vpIsoPickOffset();
          return all(lessThanEqual(abs(off), ivec2(48)));
        }
        void vpIsoRecordPick() {
          if (vpIsoObjectsOn == 0 || vpIsoPickOn == 0 || vpIsoObjectInfo.x <= 0.0) return;
          ivec2 off = vpIsoPickOffset();
          if (any(greaterThan(abs(off), ivec2(48))) || gl_FragCoord.z >= 1.0) return;
          int pixel = (off.y + 48) * 97 + off.x + 48;
          uint depthMax = 0xffffffffu >> uint(vpIsoPickBits);
          uint depth = uint(clamp(gl_FragCoord.z, 0.0, 1.0) * float(depthMax));
          uint entry = (depth << uint(vpIsoPickBits)) | uint(vpIsoObjectInfo.y);
          atomicMin(vpIsoPickDepths[pixel], entry);
        }
        """;

    public static String rewrite(String path, String source) {
        if (path.equals("mesh.vert")) {
            source = once(source, "vDistance = gl_Position.w;", "vDistance = gl_Position.w;\n"
                    + "  vpIsoObjectInfo = uModels != 0 ? vpIsoSource(1, aRun.y + gl_InstanceID)"
                    + " : uPlants != 0 || uCards != 0 ? vec3(1.0, 0.0, 0.0) : vpIsoSource(0, gl_VertexID);\n"
                    + "  vpIsoWallParent = vec4(0.0);\n"
                    + "  if (uModels != 0 && vpIsoWallCode > 0.5) {\n"
                    + "    int parent = int(vpIsoWallCode) - 1, tile = parent & 63;\n"
                    + "    vpIsoWallParent = vec4(v.mesh.xyz + vec3(-float(tile & 7), v.mesh.w, -float(tile >> 3)), float(parent >> 6));\n"
                    + "  }", path);
            return header(source, VERTEX + "flat out vec4 vpIsoWallParent;\n");
        }
        if (path.equals("model.vert")) {
            source = once(source, "vTexture = aDraw >> MODEL_SLOT_SHIFT;", "vTexture = aDraw >> MODEL_SLOT_SHIFT;\n"
                    + "  vpIsoObjectInfo = vpIsoSource(2, aDraw & ((1 << MODEL_SLOT_SHIFT) - 1));", path);
            return header(source, VERTEX);
        }
        if (Set.of("gbuffer.frag", "forward.frag", "model_gbuffer.frag", "model_glass.frag").contains(path)) {
            source = source.replace("#version 330", "#version 430");
            source = once(source, "fadeDither();", "bool vpIsoDeferredFade = vpIsoPickStable();\n  if (!vpIsoDeferredFade) { fadeDither(); vpIsoObjectFade(); }", path);
            int end = source.lastIndexOf('}');
            source = source.substring(0, end) + "  vpIsoRecordPick();\n  if (vpIsoDeferredFade) { fadeDither(); vpIsoObjectFade(); }\n" + source.substring(end);
            return header(source, FRAGMENT);
        }
        return source;
    }

    private static String once(String source, String expected, String replacement, String path) {
        int at = source.indexOf(expected);
        if (at < 0 || source.indexOf(expected, at + expected.length()) >= 0)
            throw new IllegalStateException("Unsupported object ownership shader: " + path);
        return source.substring(0, at) + replacement + source.substring(at + expected.length());
    }
    private static String header(String source, String header) {
        int line = source.startsWith("#version") ? source.indexOf('\n') + 1 : 0;
        return source.substring(0, line) + header + source.substring(line);
    }
    private NativeObjectShaders() {}
}
