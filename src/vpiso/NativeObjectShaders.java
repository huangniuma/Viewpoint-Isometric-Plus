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
        float vpIsoWallEligible;
        vec3 vpIsoSource(int kind, int vertex) {
          vpIsoWallCode = 0.0;
          vpIsoWallEligible = 0.0;
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
              vpIsoWallEligible = float((range.w >> 25) & 1u);
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
        #ifndef FADE_GIVEN
        flat in vec2 vFade;
        #define FADE_GIVEN
        #endif
        layout(std430, binding = 6) buffer VpIsoPickDepths { uint vpIsoPickDepths[]; };
        float vpIsoObjectOpacity() {
          return vpIsoObjectsOn == 0 ? 1.0
              : clamp(vpIsoObjectInfo.x,0.0,1.0)
                  * max(0.0,clamp(vFade.y,0.0,1.0)-clamp(vFade.x,0.0,1.0));
        }
        bool vpIsoObjectPartial() {
          return vpIsoObjectsOn != 0 && vpIsoObjectOpacity() < 1.0;
        }
        void vpIsoObjectFade() {
          if (vpIsoObjectOpacity() <= 0.0) discard;
        }
        ivec2 vpIsoPickOffset() {
          return ivec2(floor((gl_FragCoord.xy - vpIsoPickPixel) / max(vpIsoPickScale, vec2(0.01)) + 0.5));
        }
        bool vpIsoPickStable() {
          if (vpIsoObjectsOn == 0 || vpIsoPickOn == 0 || vpIsoObjectInfo.z < 0.5 || vpIsoObjectOpacity() <= 0.0) return false;
          ivec2 off = vpIsoPickOffset();
          return all(lessThanEqual(abs(off), ivec2(48)));
        }
        void vpIsoRecordPick() {
          if (vpIsoObjectsOn == 0 || vpIsoPickOn == 0 || vpIsoObjectOpacity() <= 0.0) return;
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
        if (path.equals("lib/sample_sprite.glsl")) {
            return once(source,"uniform vec2 uAlphaRange = vec2(0.3, 2.0);",
                    "uniform vec2 uAlphaRange = vec2(0.3, 2.0);\n"
                    + "#ifdef VPISO_SMOOTH_FORWARD\n"
                    + "#define uAlphaRange (vpIsoObjectPartial() ? vec2(uAlphaRange.x,2.0) : uAlphaRange)\n"
                    + "#endif\n",path);
        }
        if (path.equals("mesh.vert")) {
            source = once(source, "vDistance = gl_Position.w;", "vDistance = gl_Position.w;\n"
                    + "  vpIsoWallEligible = 0.0;\n"
                    + "  vpIsoObjectInfo = uModels != 0 ? vpIsoSource(1, aRun.y + gl_InstanceID)"
                    + " : uPlants != 0 || uCards != 0 ? vec3(1.0, 0.0, 0.0) : vpIsoSource(0, gl_VertexID);\n"
                    + "  vpIsoWallParent = vec4(0.0, 0.0, 0.0, -vpIsoWallEligible);\n"
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
            boolean opaque=path.equals("gbuffer.frag") || path.equals("model_gbuffer.frag");
            source = once(source, "fadeDither();", "bool vpIsoDeferredFade = vpIsoPickStable();\n"
                    + "  if (vpIsoObjectsOn == 0"+(path.equals("gbuffer.frag")?" || (vFlags & FLAG_BAKED_FLOOR) != 0":"")+") fadeDither();\n"
                    + "  else if (!vpIsoDeferredFade) vpIsoObjectFade();", path);
            if(path.equals("forward.frag")) {
                source=once(source,"translucent(uDebugView == 1 ? s.albedo : s.light.rgb, c.a, fromEye);",
                        "translucent(uDebugView == 1 ? s.albedo : s.light.rgb, c.a * vpIsoObjectOpacity(), fromEye);",path);
                source=once(source,"translucent(color, alpha, fromEye);",
                        "translucent(color, alpha * vpIsoObjectOpacity(), fromEye);",path);
            } else if(path.equals("model_glass.frag")) {
                source=once(source,"#include \"lib/hsv.glsl\"","#include \"lib/hsv.glsl\"\n#include \"lib/look.glsl\"\nflat in int vLook;\nuniform int uModelFilter;",path);
                source=once(source,"vGlass == 1, 0);","vGlass == 1, vpIsoObjectPartial() ? uModelFilter : 0);",path);
                source=once(source,"if (vGlass == 0 || c.a < uAlphaRange.x || c.a >= uAlphaRange.y) discard;",
                        "if (vpIsoObjectPartial()) {\n"
                        + "    if (vLook >= 0) c = look(vLook,vUv,c);\n"
                        + "    if (c.a < 0.01) discard;\n"
                        + "  } else if (vGlass == 0 || c.a < uAlphaRange.x || c.a >= uAlphaRange.y) discard;",path);
                source=once(source,"translucent(colour, c.a, length(vPos - uEye));",
                        "translucent(colour, c.a * vpIsoObjectOpacity(), length(vPos - uEye));",path);
            }
            int end = source.lastIndexOf('}');
            source = source.substring(0, end) + "  vpIsoRecordPick();\n"
                    + "  if (vpIsoDeferredFade) vpIsoObjectFade();\n"
                    + (opaque?"  if (vpIsoObjectPartial()"+(path.equals("gbuffer.frag")?" && (vFlags & FLAG_BAKED_FLOOR) == 0":"")+") discard;\n":"")+source.substring(end);
            return header(source, (path.equals("forward.frag")?"#define VPISO_SMOOTH_FORWARD\n":"")+FRAGMENT);
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
