package vpiso;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import viewpoint.platform.GlProgram;

/** Small runtime adaptations of VP's own loaded shaders; contains no VP shader sources. */
public final class IsometricShaders {
    private static final String HEADER = """
        #ifndef VP_ISOMETRIC_SHADER_HELPERS
        #define VP_ISOMETRIC_SHADER_HELPERS
        uniform float vpIsoEnabled;
        uniform vec3 vpIsoEye;
        uniform float vpIsoModels;
        uniform float vpIsoWater;
        uniform float vpIsoNativeWater;
        uniform float vpIsoShadowStrength;
        uniform float vpIsoShadowDistance;
        uniform float vpIsoShadowSoftness;
        uniform int vpIsoShadowLayers;
        vec3 vpIsoOrigin(vec3 ordinaryEye) {
          return vpIsoEnabled > 0.5 ? vpIsoEye : ordinaryEye;
        }
        float vpIsoDistance(float ordinaryDistance, vec3 pos) {
          return vpIsoEnabled > 0.5 ? length(pos - vpIsoEye) : ordinaryDistance;
        }
        float vpIsoPullDistance(float ordinaryDistance, vec3 pos) {
          return vpIsoEnabled > 0.5 ? min(length(pos - vpIsoEye), 64.0) : ordinaryDistance;
        }
        float vpIsoShadowAmount(vec3 pos) {
          float fade = 1.0 - smoothstep(vpIsoShadowDistance * 0.8, vpIsoShadowDistance, length(pos.xz));
          return clamp(vpIsoShadowStrength * fade, 0.0, 1.0);
        }
        #endif
        """;

    private static final Set<String> DISTANCE_CLIENTS = Set.of(
        "light.frag", "lib/reflective.glsl");
    private static final Set<String> ATMOSPHERE = Set.of(
        "sky.frag", "cloud.frag", "lib/cloud_depth.glsl");
    private static final Pattern EYE_DISTANCE = Pattern.compile(
        "distance\\(\\s*(pos|vPos|p)\\s*,\\s*uEye\\s*\\)");
    private static final Pattern EYE_LENGTH = Pattern.compile(
        "length\\(\\s*(pos|vPos|p)\\s*-\\s*uEye\\s*\\)");
    private static final Pattern SHARE_BODY = Pattern.compile(
        "(float\\s+distanceShare\\([^\\r\\n]*\\)\\s*\\{)");
    private static final Pattern FOG_BODY = Pattern.compile(
        "(vec3\\s+distanceFog\\([^\\r\\n]*\\)\\s*\\{)");
    private static final Pattern MAIN_BODY = Pattern.compile("(void\\s+main\\(\\)\\s*\\{)");

    private IsometricShaders() { }

    /** Verified VP 0.1.7 packs, including True Weathers' Default inheritance. */
    public static boolean supports(String pack) {
        return Set.of("library", "default", "vivid", "somber", "trueweathers").contains(pack);
    }

    /** Called after Shaders.read(String, Active, boolean[]) selects a resource. */
    public static String rewrite(String path, String source) {
        if (path == null || source == null || source.contains("VP_ISOMETRIC_SHADER_HELPERS")) {
            return source;
        }
        String changed = NativeObjectShaders.rewrite(path, NativeWaterShader.rewrite(path, source));
        if (path.equals("snow_layer.frag") || path.equals("snow_wake.frag")) {
            int end=changed.lastIndexOf('}');
            if(end<0)throw new IllegalStateException("Unsupported snow shader in "+path);
            String under=path.equals("snow_layer.frag")?"vPos-vec3(0.0,vThick,0.0)":"vPos";
            changed=changed.substring(0,end)+"\n  vpIsoSnowOutput(gNormalUnpack(gNormal).xyz, gLight, "+under+");\n"+changed.substring(end);
            int body=changed.indexOf("void main()");
            if(body<0)throw new IllegalStateException("Unsupported snow entry in "+path);
            changed=changed.substring(0,body)+NativeSnowOverlay.GLSL+changed.substring(body);
        } else if (path.equals("grass.frag")) {
            changed = replaceExactlyOnce(changed, "gLight = vLight;", "gLight = vLight;\n" + NativeGrassOverlay.OUTPUT, path);
            changed = addNativeHeader(changed, NativeGrassOverlay.GLSL);
        } else if (path.equals("grass.vert")) {
            changed = replaceEyeReads(changed);
        } else if (path.equals("contact_shadows.comp")) {
            changed = replaceExactlyOnce(changed, "thicknessScale[i] = abs(FAR_DEPTH - depths.x);",
                    "thicknessScale[i] = vpIsoContactShell(read, depths.x, textureSize(uDepth, 0));", path);
            changed = addNativeHeader(changed, """
                uniform float vpIsoContactOn;
                uniform mat4 vpIsoContactInverse;
                uniform vec2 vpIsoContactClips;
                float vpIsoContactShell(ivec2 pixel, float depth, ivec2 size) {
                  if (vpIsoContactOn < 0.5) return abs(1.0 - depth);
                  vec2 uv = (vec2(pixel) + 0.5) / vec2(size);
                  vec4 p = vpIsoContactInverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                  float away = max(length(p.xyz / p.w - vec3(0.0, 1.5, 0.0)), 1.0);
                  float nearClip = vpIsoContactClips.x, farClip = vpIsoContactClips.y;
                  float viewDepth = nearClip * farClip / (farClip - depth * (farClip - nearClip));
                  // d(GL depth)/d(metres), with the same relative shell thickness as
                  // VP's near camera. The remote isometric eye must not inflate it.
                  return (nearClip * farClip / (farClip - nearClip)) * away / (viewDepth * viewDepth);
                }
                """);
        } else if (path.equals("lib/mesh_vertex.glsl")) {
            changed = "vec3 vpIsoPlantRoot;\n" + changed;
            changed = replaceExactlyOnce(changed,"vec3 foot = vec3(0.0);",
                    "vec3 foot = vec3(0.0); vpIsoPlantRoot = vec3(0.0);",path);
            changed = replaceExactlyOnce(changed,"foot = plant.foot; standing = true;",
                    "foot = plant.foot; vpIsoPlantRoot = plant.foot + v.mesh.xyz; standing = true;",path);
            changed = replaceExactlyOnce(changed, "d * d * 6.0e-6",
                "vpIsoPullDistance(d, v.pos) * vpIsoPullDistance(d, v.pos) * 6.0e-6", path);
            // Native shore depth follows the physical plane. A distance-dependent base-floor
            // pull makes whole land triangles cut through it, even with temporal jitter off.
            changed = replaceExactlyOnce(changed, "0.5 * float(floorTile)",
                "(vpIsoEnabled > 0.5 && vpIsoModels > 0.5 && vpIsoNativeWater > 0.5 ? 0.0 : 0.5) * float(floorTile)", path);
        } else if (path.equals("mesh.vert")) {
            changed = insertBody(changed, MAIN_BODY,
                    "\n  // The native tree lookup follows the instance foot, including all attached leaves.\n",path);
            changed = replaceExactlyOnce(changed,"MeshVertex v = meshVertex(uEye);",
                    "MeshVertex v = meshVertex(uEye);\n  vpIsoTreeRoot = vpIsoPlantRoot; vpIsoPlants = uModels != 0 ? 2 : uPlants;",path);
            changed = addNativeHeader(changed,"flat out vec3 vpIsoTreeRoot;\nflat out int vpIsoPlants;\n");
        } else if (path.equals("lib/distance.glsl")) {
            changed = replaceEyeReads(changed);
            changed = insertBody(changed, SHARE_BODY,
                "\n  if (vpIsoEnabled > 0.5) { d = length(pos - vpIsoEye); fogDepth = d; }\n", path);
            changed = insertBody(changed, FOG_BODY,
                "\n  if (vpIsoEnabled > 0.5) { d = length(pos - vpIsoEye); fogDepth = d;"
                + " view = (pos - vpIsoEye) / max(d, 1.0e-4); }\n", path);
        } else if (ATMOSPHERE.contains(path)) {
            changed = replaceEyeReads(changed);
        } else if (path.equals("precip.vert")) {
            // Keep weather around the player. Its cards must still face the render camera.
            changed = replaceEyeReads(changed)
                .replace("normalize(vpIsoOrigin(uEye) - p)", "normalize(uEye - p)")
                .replace("normalize(vpIsoOrigin(uEye) - pos)", "normalize(uEye - pos)");
        } else if (path.equals("rain_over.frag")) {
            changed = replaceExactlyOnce(changed,"fragColor = texture(uRain, paniniFrame(vUv));",
                    "fragColor = texture(uRain, paniniFrame(vUv));\n"
                    + "  if (vpIsoRainSheltered(paniniFrame(vUv))) fragColor = vec4(0.0);",path);
            changed = addNativeHeader(changed,NativeWeather.GLSL);
        } else if (path.equals("lib/sun.glsl")) {
            changed = replaceExactlyOnce(changed, "bool sunMap(int c) {",
                "bool sunMap(int c) {\n  if (vpIsoModels > 0.5 && c >= vpIsoShadowLayers) return false;", path);
            changed = replaceExactlyOnce(changed,
                "float lit = uShadowsOff > 0.5 ? 1.0 : sunVisibility(pos, n, facing);",
                "float lit = uShadowsOff > 0.5 ? 1.0 : sunVisibility(pos, n, facing);\n"
                + "  if (vpIsoModels > 0.5) {\n"
                + "    lit = mix(1.0, lit, vpIsoShadowAmount(pos));\n"
                + "  }", path);
            changed = replaceExactlyOnce(changed, "float clouds = cloudShadow(p);",
                "float clouds = cloudShadow(p);\n"
                + "  if (vpIsoModels > 0.5 && vpIsoShadowAmount(p) <= 0.0) return clouds;", path);
            changed = replaceExactlyOnce(changed, "return clouds * (z >= 1.0 ? 1.0 : shadowTap(c, vec3(sc.xy, z)));",
                "float lit = z >= 1.0 ? 1.0 : shadowTap(c, vec3(sc.xy, z));\n"
                + "      return clouds * (vpIsoModels > 0.5 ? mix(1.0, lit, vpIsoShadowAmount(p)) : lit);", path);
        } else if (path.equals("lib/sun_filter.glsl")) {
            // Library uses a square PCF kernel; the other built-ins use a disc.
            // True Weathers retains that filter and adds a weather filter with its own
            // texel step. Scale every footprint, retaining both filters and their fallback.
            if (!changed.contains("uShadowMaps[c].z")) {
                throw new IllegalStateException("Unsupported VP shader layout in " + path);
            }
            changed = changed.replace("uShadowMaps[c].z",
                "(uShadowMaps[c].z * (vpIsoModels > 0.5 ? vpIsoShadowSoftness : 1.0))");
        }
        if (DISTANCE_CLIENTS.contains(path)) {
            changed = distanceCalls(changed, EYE_DISTANCE);
            changed = distanceCalls(changed, EYE_LENGTH);
        }
        // OIT distances locate surfaces along the physical render-camera ray. Replacing
        // them with player distances reconstructs nearby alpha surfaces behind that eye,
        // thousands of units from the player, and makes their fog fully opaque/blue.
        // Only fog evaluation uses the logical eye (also in lib/distance.glsl above).
        if (path.equals("post.frag")) {
            changed = changed.replace("fogAt(linearDepth(depth), uFogRange)",
                "fogAt(vpIsoDistance(linearDepth(depth), scenePos(vUv, depth)), uFogRange)");
        } else if (path.equals("forward.frag")) {
            changed = changed.replace("fogAt(vDistance, uFogRange)",
                "fogAt(vpIsoDistance(vDistance, vPos), uFogRange)");
        }
        if (path.equals("tw_grade.frag")) {
            // Keep True Weathers' own grade and options, respecting the addon's model
            // style switch. Ordinary VP still receives the unattenuated weather grade.
            changed = replaceExactlyOnce(changed, "fragColor = vec4(max(c, vec3(0.0)), 1.0);",
                    "float vpIsoGrade = vpIsoModels > 0.5 ? (vpIsoStyleOn > 0.5 ? vpIsoStyleStrength : 0.0) : 1.0;\n"
                    + "  fragColor = vec4(mix(texture(uColor, vUv).rgb, max(c, vec3(0.0)), clamp(vpIsoGrade, 0.0, 1.0)), 1.0);", path);
            changed = addNativeHeader(changed, "uniform float vpIsoStyleOn;\nuniform float vpIsoStyleStrength;\n");
        }
        if (path.equals("gbuffer.frag") || path.equals("forward.frag")) {
            changed = insertBody(changed, MAIN_BODY,
                "\n  if (vpIsoNativeWater > 0.5 && (vFlags & FLAG_WATER) != 0) discard;\n"
                + "  vpIsoCutTree(vpIsoTreeRoot, vpIsoPlants, vFlags);\n"
                + "  if ((vFlags & (8192 | 32768)) == 0) vpIsoCutWall(vPos, vLocal, vNormal, vFlags);\n", path);
            changed = addNativeHeader(changed, "flat in vec3 vpIsoTreeRoot;\nflat in int vpIsoPlants;\n"
                    + NativeViewData.GLSL + NativeWallCutaway.GLSL + NativeTreeCutaway.GLSL);
        } else if (path.equals("post.frag")) {
            if (changed.contains("color = grade(")) {
                // Keep the selected pack's tone curve, night colours and (Somber) ink.
                changed = replaceExactlyOnce(changed, "color = grade(",
                    "vec3 vpIsoBase = clamp(max(color, vec3(0.0)) * uExposure, 0.0, 1.0);\n"
                    + "  if (vpIsoModels > 0.5) color *= vpIsoStyleExposure;\n"
                    + "  color = grade(", path);
                changed = replaceExactlyOnce(changed,
                    "color += (ign(gl_FragCoord.xy + 17.0) - 0.5) / 255.0;",
                    "if (vpIsoModels > 0.5) {\n"
                    + "    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));\n"
                    + "    vec3 graded = max(mix(vec3(luma), color, vpIsoStyleSaturation), vec3(0.0));\n"
                    + "    graded = pow(graded, vec3(vpIsoStyleContrast));\n"
                    + "    color = vpIsoStyleOn > 0.5 ? mix(vpIsoBase, graded, vpIsoStyleStrength) : vpIsoBase;\n"
                    + "  }\n"
                    + "  if (depth < 1.0) color *= vpIsoNativeDark(pos);\n"
                    + "  color += (ign(gl_FragCoord.xy + 17.0) - 0.5) / 255.0;", path);
            } else {
                changed = replaceExactlyOnce(changed,
                    "color = shoulder(max(color, vec3(0.0)) * uExposure);",
                    "vec3 vpIsoLinear = max(color, vec3(0.0)) * uExposure;\n"
                    + "  color = shoulder(vpIsoLinear);\n"
                    + "  if (vpIsoModels > 0.5) {\n"
                    + "    vec3 base = clamp(vpIsoLinear, 0.0, 1.0);\n"
                    + "    vec3 graded = vpIsoLinear * vpIsoStyleExposure;\n"
                    + "    float luma = dot(graded, vec3(0.2126, 0.7152, 0.0722));\n"
                    + "    graded = max(mix(vec3(luma), graded, vpIsoStyleSaturation), vec3(0.0));\n"
                    + "    graded = pow(graded, vec3(vpIsoStyleContrast));\n"
                    + "    color = vpIsoStyleOn > 0.5 ? mix(base, shoulder(graded), vpIsoStyleStrength) : base;\n"
                    + "  }\n"
                    + "  if (depth < 1.0) color *= vpIsoNativeDark(pos);", path);
            }
            // Keep #version the first directive, as with the ordinary helper header.
            changed = addNativeHeader(changed, NativeViewData.GLSL + NativeStyle.GLSL);
        }
        return changed.equals(source) ? source : header(changed);
    }

    /** Called after GlProgram.use; only own uniforms are written here. */
    public static void onUse(GlProgram program) {
        NativeObjectState.onUse(program);
        NativeViewData.onUse(program);
        NativeWallCutaway.onUse(program);
        NativeGrassOverlay.onUse(program);
        NativeSnowOverlay.onUse(program);
        NativeWeather.onUse(program);
        boolean enabled = IsometricBridge.rendering();
        if (program.has("vpIsoEnabled")) {
            program.set("vpIsoEnabled", enabled ? 1.0f : 0.0f);
        }
        if (enabled && program.has("vpIsoEye")) {
            // All scene geometry is relative to Frame.camX/Y/Z, not the remote render eye.
            program.set("vpIsoEye", 0.0f, 1.5f, 0.0f);
        }
        boolean models = IsometricBridge.modelRendering();
        if (program.has("vpIsoContactOn")) {
            var context = (viewpoint.render.FrameContext) GameAccess.field("viewpoint.render.WorldRenderer", "frame", null);
            boolean contact = models && context.scene == IsometricBridge.renderFrame().scene;
            program.set("vpIsoContactOn", contact ? 1f : 0f);
            if (contact) {
                program.set("vpIsoContactInverse", context.invViewProjection);
                program.set("vpIsoContactClips", IsometricBridge.NEAR, IsometricBridge.FAR);
            }
        }
        if (program.has("vpIsoModels")) program.set("vpIsoModels", models ? 1f : 0f);
        set(program, "vpIsoWater", IsometricBridge.waterRendering() ? 1f : 0f);
        set(program, "vpIsoNativeWater", IsometricBridge.usesNativeWater() ? 1f : 0f);
        if (models) {
            IsometricEffects effects = IsometricBridge.renderEffects();
            set(program, "vpIsoShadowStrength", effects.needsShadows() ? effects.shadowStrength() : 0f);
            set(program, "vpIsoShadowDistance", effects.distance());
            set(program, "vpIsoShadowSoftness", effects.softness());
            if (program.has("vpIsoShadowLayers")) program.setInt("vpIsoShadowLayers",
                    effects.distance() <= 22f ? 1 : effects.distance() <= 64f ? 2 : 3);
            set(program, "vpIsoStyleOn", effects.needsStyle() ? 1f : 0f);
            set(program, "vpIsoStyleStrength", effects.styleStrength());
            set(program, "vpIsoStyleExposure", effects.exposure());
            set(program, "vpIsoStyleSaturation", effects.saturation());
            set(program, "vpIsoStyleContrast", effects.contrast());
        }
    }

    private static void set(GlProgram program, String uniform, float value) {
        if (program.has(uniform)) program.set(uniform, value);
    }

    private static String addNativeHeader(String source, String helpers) {
        if (!source.startsWith("#version")) return helpers + source;
        int line = source.indexOf('\n');
        return source.substring(0, line + 1) + helpers + source.substring(line + 1);
    }

    private static String distanceCalls(String source, Pattern expression) {
        Matcher matches = expression.matcher(source);
        StringBuffer out = new StringBuffer();
        while (matches.find()) {
            matches.appendReplacement(out, Matcher.quoteReplacement(
                "vpIsoDistance(" + matches.group() + ", " + matches.group(1) + ")"));
        }
        matches.appendTail(out);
        return out.toString();
    }

    private static String replaceEyeReads(String source) {
        // The declaration is retained; only expressions use the independent logical eye.
        String changed = source.replaceAll("\\buEye\\b", "vpIsoOrigin(uEye)");
        return changed.replaceAll("uniform\\s+vec3\\s+vpIsoOrigin\\(uEye\\)\\s*;", "uniform vec3 uEye;");
    }

    private static String insertBody(String source, Pattern signature, String insertion, String path) {
        Matcher body = signature.matcher(source);
        if (!body.find()) {
            throw new IllegalStateException("Unsupported VP shader function in " + path);
        }
        int at = body.end();
        if (body.find()) {
            throw new IllegalStateException("Ambiguous VP shader function in " + path);
        }
        return source.substring(0, at) + insertion + source.substring(at);
    }

    private static String replaceExactlyOnce(String source, String expected, String replacement, String path) {
        int at = source.indexOf(expected);
        if (at < 0 || source.indexOf(expected, at + expected.length()) >= 0) {
            throw new IllegalStateException("Unsupported VP shader layout in " + path);
        }
        return source.substring(0, at) + replacement + source.substring(at + expected.length());
    }

    private static String header(String source) {
        if (source.startsWith("#version")) {
            int line = source.indexOf('\n');
            return line < 0 ? source + "\n" + HEADER
                : source.substring(0, line + 1) + HEADER + source.substring(line + 1);
        }
        return HEADER + source;
    }
}
