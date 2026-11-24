package vpiso;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;

/** Reuses the installed VP pack's water, without replacing Library's world lighting. */
public final class NativeWaterShader {
    private static final String BASE = "/viewpoint/shaders/";
    private static final String DEFAULT = "/viewpoint/shaderpacks/default/shaders/";
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");

    public static String rewrite(String path, String source) {
        if (!path.equals("light.frag") || source.contains("VP_ISOMETRIC_WATER")) return source;
        // Default and Somber already supply their own water implementations.
        if (source.contains("lib/water_surface.glsl") || source.contains("lib/water_waves.glsl")) return source;
        String changed = once(source, "uniform vec3 uSkyLevel;",
                "#ifndef VIEWPOINT_SKY_LEVEL\n#define VIEWPOINT_SKY_LEVEL\nuniform vec3 uSkyLevel;\n#endif");
        changed = once(changed, "vec3 lightShare(", WaterLibrary.SOURCE + "\nvec3 lightShare(");
        changed = once(changed, "vec3 pos = scenePos(vUv, depth);",
                "vec3 pos = scenePos(vUv, depth);\n  vec4 vpIsoWaterSpan = waterSpan(pos);");
        return once(changed, "  vec3 bounce = bounceAt(pos, n) + flashBounceAt(pos, n);",
                "  if (vpIsoWater > 0.5 && isWater(albedo.a)) {\n"
                + "    fragColor = vec4(water(pos, vpIsoWaterSpan, depth, light, engine.a, lit, vec4(n, seen)), 1.0);\n"
                + "    return;\n  }\n"
                + "  vec3 bounce = bounceAt(pos, n) + flashBounceAt(pos, n);");
    }

    /** Standalone optics use the installed VP wave/body functions, without a VP world G-buffer. */
    public static String overlayLibrary() {
        StringBuilder source = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String path : new String[] { "lib/camera.glsl", "lib/water_surface.glsl", "lib/water_body.glsl",
                "lib/specular.glsl", "lib/sky.glsl" }) source.append(expandOverlay(path, seen));
        return source.toString();
    }

    /** Somber has a separate water material and wave filter, inherited by no other pack. */
    public static String overlaySomber(ShaderPacks.Active active) {
        Set<String> seen = new HashSet<>();
        StringBuilder source = new StringBuilder();
        source.append(expandActive("lib/camera.glsl", seen, active));
        source.append(expandOverlay("lib/sky.glsl", seen));
        // The native overlay has no atmospheric SkyLight UBO. Normalize its standalone
        // sky reflection by the same native daylight used by its diffuse water light.
        source.append("float skyLevelGround() { return max(dot(uSkyLevel, vec3(0.2126,0.7152,0.0722)),0.025); }\n");
        for (String path : new String[] { "lib/water_waves.glsl", "lib/water_under.glsl",
                "lib/specular.glsl", "lib/occlusion.glsl", "lib/scotopic.glsl" }) {
            source.append(expandActive(path, seen, active));
        }
        String shade = readActive("lib/shade.glsl", active);
        source.append(shade.substring(shade.indexOf("vec3 skyPartOf("), shade.indexOf("const float THROUGH_BEND")));
        String result = source.toString();
        return once(result, function(result, "waterShore"),
                "float waterShore(vec2 xz) { return nativeShore(xz); }");
    }

    /** Library water is its original floor sprite through shade(), not Default optics. */
    public static String overlayMaterial() {
        String shade=readActive("lib/shade.glsl", ShaderPacks.active());
        String constants=shade.substring(0,shade.indexOf("vec3 lamps("))
                .replaceAll("(?m)^uniform [^;]+;", "").replaceAll("(?m)^#include[^\n]*", "");
        return expandActive("lib/sample_sprite.glsl", new HashSet<>(), ShaderPacks.active())+constants
                +(shade.contains("float faceShade(")?function(shade,"faceShade")+function(shade,"hueOf"):"")
                +function(shade, "shade");
    }

    public static String overlayPost(ShaderPacks.Active active) {
        String post = readActive("post.frag", active);
        if (!post.contains("vec3 grade("))
            return resource(BASE + "lib/common.glsl")
                    + "\nvec3 nativeGrade(vec3 color) { return shoulder(max(color,vec3(0.0))*uExposure); }\n";
        Set<String> seen = new HashSet<>();
        if (post.contains("vec3 grade(vec3 color, bool sky, float dark, float middle)")) {
            return expandActive("lib/purkinje.glsl", seen, active) + expandActive("lib/tone.glsl", seen, active)
                    + expandActive("lib/dark_vision.glsl", seen, active) + expandActive("lib/oklab.glsl", seen, active)
                    + post.substring(post.indexOf("const vec3 WARM"), post.indexOf("vec4 upsample("))
                    + post.substring(post.indexOf("const float SPOT_FROM"), post.indexOf("void main()"))
                    + "\nvec3 nativeGrade(vec3 color) { return grade(color, false, darkShare(color, uExposure * OPTION_EXPOSURE), 0.18); }\n";
        }
        return expandActive("lib/purkinje.glsl", seen, active) + expandActive("lib/tone.glsl", seen, active)
                + function(post, "grade") + "\nvec3 nativeGrade(vec3 color) { return grade(color); }\n";
    }

    /** Use VP's live-option declaration and literal handling for compile-time expressions. */
    public static String overlayOptions(String source, ShaderPacks.Active active) {
        try {
            Class<?> bodyType = GameAccess.type("viewpoint.platform.Shaders$Body");
            var constructor = bodyType.getDeclaredConstructor(String.class, int.class, List.class, boolean.class);
            constructor.setAccessible(true);
            Object body = constructor.newInstance(source, source.indexOf("#undef PIPELINE_WATER_FFT"),
                    List.of("VPISO installed VP water"), true);
            Object resolved = GameAccess.call("viewpoint.platform.Shaders", "given", null,
                    new Class<?>[]{bodyType, ShaderPacks.Active.class}, body, active);
            return (String) GameAccess.field("viewpoint.platform.Shaders$Source", "text", resolved);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot resolve installed VP water options", error);
        }
    }

    private static String readActive(String path, ShaderPacks.Active active) {
        return (String) GameAccess.call("viewpoint.platform.Shaders", "read", null,
                new Class<?>[] { String.class, viewpoint.platform.ShaderPack.class, boolean[].class },path,active.pack,new boolean[1]);
    }

    static String expandActive(String path, Set<String> seen, ShaderPacks.Active active) {
        if (!seen.add(path)) return "";
        String source = readActive(path, active);
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            var include = INCLUDE.matcher(line);
            if (include.matches()) result.append(expandActive(include.group(1), seen, active));
            else result.append(line).append('\n');
        }
        return result.toString();
    }

    private static String expandOverlay(String path, Set<String> seen) {
        if (!seen.add(path)) return "";
        String source;
        if (path.equals("constants.glsl")) {
            source = (String) GameAccess.call("viewpoint.platform.Shaders", "read", null,
                    new Class<?>[] { String.class }, path);
        } else {
            source = resource((GlProgram.class.getResource(BASE + path) != null ? BASE : DEFAULT) + path);
        }
        if (path.equals("lib/far_relief.glsl")) {
            // Keep VP's interpolation and nearest non-land kind selection unchanged.
            // Only the storage changes to the small frame-owned native water tile.
            source = once(source, "reliefBytes(uReliefFlow[at.y * int(uReliefFlowCells.x) + at.x])",
                    "texelFetch(nativeFlow, clamp(at-ivec2(nativeFlowGrid.xy),ivec2(0),textureSize(nativeFlow,0)-1), 0)");
        } else if (path.equals("lib/water_surface.glsl")) {
            source = once(source, "far * pixel / rise", "distance(uEye, pos) * pixel / rise");
        } else if (path.equals("lib/water_body.glsl")) {
            int start = source.indexOf("float shoreDistance(");
            int end = source.indexOf("\n}", start);
            if (start < 0 || end < start) throw new IllegalStateException("Unsupported VP shore-distance function");
            source = source.substring(0, start)
                    + "float shoreDistance(vec2 xz) { return nativeShore(xz); }"
                    + source.substring(end + 2);
        } else if (path.equals("lib/sky.glsl")) {
            String sky = resource(BASE + "sky.frag");
            int start = sky.indexOf("  float h = max(r.y, 0.0);");
            int end = sky.indexOf(';', sky.indexOf("  vec3 sky = ", start));
            if (start < 0 || end < start) throw new IllegalStateException("Unsupported VP sky gradient");
            source = once(source, function(source, "skyPlain"),
                    "vec3 skyPlain(vec3 r) {\n" + sky.substring(start, end + 1).replace("vec3 sky = ", "return ") + "\n}");
        }
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            var include = INCLUDE.matcher(line);
            if (include.matches()) result.append(expandOverlay(include.group(1), seen));
            else result.append(line).append('\n');
        }
        return result.toString();
    }

    private static final class WaterLibrary {
        static final String SOURCE = load();

        private static String load() {
            String light = resource(DEFAULT + "light.frag");
            StringBuilder result = new StringBuilder("\n#define VP_ISOMETRIC_WATER 1\n");
            Set<String> seen = new HashSet<>();
            for (String path : new String[] { "lib/land_light.glsl", "lib/specular.glsl", "lib/grids.glsl",
                    "lib/water_surface.glsl", "lib/water_body.glsl", "lib/reflected.glsl" }) {
                result.append(dependency(path, seen));
            }
            var foam = Pattern.compile("const float FOAM_ALBEDO\\s*=\\s*[^;]+;").matcher(light);
            if (!foam.find()) throw new IllegalStateException("Unsupported VP water foam constant");
            result.append(foam.group()).append('\n');
            for (String name : new String[] { "spotHighlight", "spotSpecular", "spotsSpecular", "water" }) {
                String function = function(light, name);
                if (name.equals("water")) {
                    function = once(function,
                            "shadeShared(vec3(1.0), light, skyShare, up, lit, occlusion, vec3(0.0))",
                            "shade(vec3(1.0), light, up, lit)");
                }
                result.append(function).append('\n');
            }
            return result.toString();
        }
    }

    /** Inline only Default-only dependencies; base resources keep VP's usual read/patch chain. */
    private static String dependency(String path, Set<String> seen) {
        if (!seen.add(path)) return "";
        if (path.equals("constants.glsl") || GlProgram.class.getResource(BASE + path) != null)
            return "#include \"" + path + "\"\n";
        String source = resource(DEFAULT + path);
        if (path.equals("lib/water_surface.glsl")) {
            // Narrow-FOV perspective emulates orthographic projection: actual distance * pixel
            // is the world footprint. Rain attenuation, passed as far, uses the logical player eye.
            source = once(source, "far * pixel / rise",
                    "(vpIsoModels > 0.5 ? distance(uEye, pos) : far) * pixel / rise");
        } else if (path.equals("lib/sky.glsl")) {
            // Library never generates AirLuts.skyView. Use its own captured sky gradient,
            // rather than Default's clear-sky atmosphere texture and night-only base.
            String sky = resource(BASE + "sky.frag");
            int first = sky.indexOf("  float h = max(r.y, 0.0);");
            int end = sky.indexOf(';', sky.indexOf("  vec3 sky = ", first));
            if (first < 0 || end < first) throw new IllegalStateException("Unsupported VP Library sky gradient");
            String gradient = sky.substring(first, end + 1).replace("vec3 sky = ", "return ");
            source = once(source, "#define DEFAULT_SKY",
                    "#define DEFAULT_SKY\nuniform vec3 uHorizon;\nuniform vec3 uZenith;");
            source = once(source, function(source, "skyPlain"), "vec3 skyPlain(vec3 r) {\n" + gradient + "\n}");
        } else if (path.equals("lib/land_light.glsl")) {
            // The matching Library sun source is uploaded by SurfacePass.sunUniforms.
            // Its pipeline also does not generate the Default sky irradiance texture.
            source = once(source, function(source, "landBody"),
                    "vec3 landBody() { return uSkyLevel * uSunColor * uSunStrength; }");
        }
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            var include = INCLUDE.matcher(line);
            if (include.matches()) result.append(dependency(include.group(1), seen));
            else result.append(line).append('\n');
        }
        return result.toString();
    }

    private static String resource(String path) {
        try (InputStream input = GlProgram.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing installed VP water resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException error) { throw new IllegalStateException("Cannot read VP water resource " + path, error); }
    }

    private static String function(String source, String name) {
        var match=Pattern.compile("(?:vec[234]|float)\\s+"+Pattern.quote(name)+"\\(").matcher(source);
        if(!match.find()) throw new IllegalStateException("Unsupported VP water function " + name);
        int start=match.start();
        if (match.find())
            throw new IllegalStateException("Unsupported VP water function " + name);
        int body = source.indexOf('{', start), level = 1;
        if (body < 0) throw new IllegalStateException("Missing VP water body " + name);
        for (int end = body + 1; end < source.length(); end++) {
            char c = source.charAt(end);
            if (c == '{') level++;
            else if (c == '}' && --level == 0) return source.substring(start, end + 1);
        }
        throw new IllegalStateException("Unclosed VP water function " + name);
    }

    private static String once(String source, String expected, String replacement) {
        int at = source.indexOf(expected);
        if (at < 0 || source.indexOf(expected, at + expected.length()) >= 0)
            throw new IllegalStateException("Unsupported VP water layout: " + expected);
        return source.substring(0, at) + replacement + source.substring(at + expected.length());
    }

    private NativeWaterShader() {}
}
