package vpiso;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Carries the original renderer's ground RGB contribution alongside its unchanged image. */
public final class NativeGroundShaders {
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern OUTPUT = Pattern.compile("(?m)(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+(\\w+)\\s*;");
    public static final boolean PZO_FINAL = pzoFinal();

    private static boolean pzoFinal() {
        try {
            Class.forName("pzopt.EntityShadow", false, ClassLoader.getSystemClassLoader())
                    .getDeclaredMethod("devFinal", String.class, String.class);
            return true;
        } catch (ReflectiveOperationException ignored) { return false; }
    }

    // The global default program can compile before Main has checked Viewpoint.
    // Its extra outputs are dormant until NativeGround enables capture.
    public static boolean bootstrap(String path) {
        return (path!=null && path.replace('\\','/').endsWith("/default.frag")) || "default.frag".equals(path);
    }

    public static String rewrite(String path, String source) {
        if (path == null || source == null || !path.endsWith(".frag")
                || source.contains("VPISO_GROUND_CONTRIBUTION")) return source;
        source=source.replace("\uFEFF", "");
        Matcher main = MAIN.matcher(source);
        if (!main.find()) return source;
        String file = path.replace('\\', '/');
        file = file.substring(file.lastIndexOf('/') + 1);
        boolean chunk = file.equals("chunkShader.frag") || file.matches("pzopt_(?:swChunk|sfChunk|chunkBase|chunkStock)\\.frag");
        String value;
        String header = "\n// VPISO_GROUND_CONTRIBUTION\nuniform float vpIsoGroundTag;\n"
                + "uniform float vpIsoGrassMaskOn;\nuniform float vpIsoGrassOccluder;\nuniform float vpIsoFloorLevel;\nuniform float vpIsoMetadataStraight;\nuniform float vpIsoWallOwner;\nuniform float vpIsoSourceOwner;\n";
        String output;
        String maskOutput;
        String relightOutput, validOutput, floorOutput;
        String broadcast = "";
        if (source.contains("gl_FragColor")) {
            source = source.replace("gl_FragColor", "gl_FragData[0]");
            value = "gl_FragData[0]"; output = "gl_FragData[7]"; maskOutput="gl_FragData[6]";
            relightOutput="gl_FragData[5]"; validOutput="gl_FragData[4]"; floorOutput="gl_FragData[3]";
            // gl_FragColor broadcasts to every enabled native draw buffer. Replacing it
            // with only gl_FragData[0] silently drops the other original outputs.
            for(int i=1;i<7;i++) broadcast += "gl_FragData["+i+"] = gl_FragData[0]; ";
        } else if (source.contains("gl_FragData[0]")) {
            value = "gl_FragData[0]"; output = "gl_FragData[7]"; maskOutput="gl_FragData[6]";
            relightOutput="gl_FragData[5]"; validOutput="gl_FragData[4]"; floorOutput="gl_FragData[3]";
        } else {
            Matcher outputs = OUTPUT.matcher(source);
            value = null;
            while (outputs.find()) {
                if ("7".equals(outputs.group(1))) return source;
                if (value == null || "0".equals(outputs.group(1))) value = outputs.group(2);
            }
            if (value == null) return source;
            output = "vpIsoGroundOutput";
            maskOutput="vpIsoOcclusionOutput";
            relightOutput="vpIsoRelightOutput"; validOutput="vpIsoRelightValidity"; floorOutput="vpIsoFloorOutput";
            header += "layout(location=7) out vec4 vpIsoGroundOutput;\nlayout(location=6) out vec4 vpIsoOcclusionOutput;\n";
        }
        // Only the final framebuffer borrows these slots, and only when both are
        // unowned. Other native/PZO MRT outputs retain their original attachments.
        boolean relightSlots=!Pattern.compile("(?:location\\s*=\\s*[45]\\b|gl_FragData\\s*\\[\\s*[45]\\s*\\])")
                .matcher(source).find();
        if(relightSlots) {
            header+="uniform float vpIsoRelightWrite;\n";
            if(output.equals("vpIsoGroundOutput"))header+="layout(location=5) out vec4 vpIsoRelightOutput;\nlayout(location=4) out vec4 vpIsoRelightValidity;\n";
        }
        boolean floorSlot=!Pattern.compile("(?:location\\s*=\\s*3\\b|gl_FragData\\s*\\[\\s*3\\s*\\])")
                .matcher(source).find();
        if(floorSlot) {
            header+="uniform float vpIsoFloorWrite;\n";
            if(output.equals("vpIsoGroundOutput"))header+="layout(location=3) out vec4 vpIsoFloorOutput;\n";
        }
        String relight="vec3(0.0)", valid="0.0";
        String contribution = "vec3(0.0)";
        String alpha="mix("+value+".a,1.0,vpIsoMetadataStraight)";
        header+="vec2 vpIsoOwnerCode() { if(vpIsoWallOwner>.5)return vec2(-1.0,vpIsoWallOwner); "
                +"if(vpIsoSourceOwner>.5)return vec2(-2.0-mod(vpIsoSourceOwner,256.0),floor(vpIsoSourceOwner/256.0)); "
                +"return vec2(vpIsoGrassOccluder,vpIsoGroundTag*vpIsoFloorLevel); }\n";
        String occlusion=alpha+" * vpIsoOwnerCode().x";
        String first=alpha+" * vpIsoOwnerCode().y";
        String second=alpha+" * dot(vpIsoOwnerCode(),vpIsoOwnerCode())";
        String metadataAlpha=value+".a";
        String floorMoments=alpha+" * vpIsoGroundTag * vec3(vpIsoFloorLevel,vpIsoFloorLevel*vpIsoFloorLevel,1.0)";
        if (chunk) {
            // Same original chunk UV and native color/visibility modulation, including PZO lighting.
            if (!source.contains("DIFFUSE") || !source.contains("texCoord")) return source;
            header += "uniform sampler2D vpIsoGroundTexture;\nuniform sampler2D vpIsoOcclusionTexture;\nuniform sampler2D vpIsoFloorTexture;\n";
            // Select the native color assignment. An earlier alpha-only fetch may
            // live inside a disabled #ifdef, with an undefined optional LOD macro.
            Matcher fetch=Pattern.compile("\\bc\\s*=\\s*((\\w+)\\(\\s*DIFFUSE\\s*,\\s*[^;()]+\\))")
                    .matcher(source.substring(MAIN.matcher(source).results().findFirst().orElseThrow().start()));
            if(!fetch.find()) return source;
            String original=fetch.group(1), ground=original.replace("DIFFUSE","vpIsoGroundTexture");
            String metadataFetch=null;
            if(source.contains("vec4 pzsfFetch(")) {
                int start=source.indexOf("vec4 pzsfMagnify("), endStart=source.indexOf("vec4 pzsfFetch(");
                if(start<0) return source;
                int end=bodyEnd(source,source.indexOf('{',endStart));
                // Coverage and height moments are linear data. A color-only gamma
                // average destroys their variance/ownership even at identical UVs.
                String helpers=source.substring(start,end).replace("pzsf","vpIsoMetadata")
                        .replace("#if PZSF_LINEAR","#if 0");
                source=source.substring(0,end)+"\n"+helpers+source.substring(end);
                if(fetch.group(2).equals("pzsfFetch"))
                    metadataFetch=original.replace("DIFFUSE","vpIsoOcclusionTexture").replace("pzsfFetch(","vpIsoMetadataFetch(");
            }
            if(fetch.group(2).equals("pzSwFetch") && source.contains("return pzSwC0")) {
                // PZO's prefetch helper ignores its sampler argument when stationary.
                // Clone that helper with a sample from the requested texture instead of DIFFUSE's cache.
                int start=source.indexOf("vec4 pzSwFetch(");
                int open=source.indexOf('{',start), end=bodyEnd(source,open);
                Matcher cached=Pattern.compile("\\bpzSwC0\\s*=\\s*([^;]+);").matcher(source);
                if(start<0 || open<0 || !cached.find()) return source;
                String helper=source.substring(start,end).replace("pzSwFetch(","vpIsoGroundFetch(")
                        .replace("pzSwC0","("+cached.group(1).replace("DIFFUSE","s")+")");
                String metadataHelper=helper.replace("vpIsoGroundFetch(","vpIsoSwayMetadataFetch(")
                        .replace("pzsfFetch(","vpIsoMetadataFetch(");
                source=source.substring(0,end)+"\n"+helper+"\n"+metadataHelper+source.substring(end);
                ground=ground.replace("pzSwFetch(","vpIsoGroundFetch(");
                metadataFetch=original.replace("DIFFUSE","vpIsoOcclusionTexture").replace("pzSwFetch(","vpIsoSwayMetadataFetch(");
            }
            contribution = ground+".rgb * (" + value + ".rgb / max("+original+".rgb, vec3(0.00001)))";
            String metadata=metadataFetch!=null?metadataFetch:ground.replace("vpIsoGroundTexture","vpIsoOcclusionTexture");
            metadataAlpha=metadata+".a * ("+value+".a / max("+original+".a, 0.00001))";
            String modulation=" * ("+value+".a / max("+original+".a, 0.00001)) / max(mix(1.0,("+metadataAlpha+"),vpIsoMetadataStraight),0.00001)";
            occlusion=metadata+".r"+modulation;
            first=metadata+".g"+modulation;
            second=metadata+".b"+modulation;
            floorMoments=metadata.replace("vpIsoOcclusionTexture","vpIsoFloorTexture")+".rgb"+modulation;
            if(relightSlots && source.contains("vec3 pplLight(") && source.contains("pplConn")
                    && source.contains("pplVisible(") && source.contains("pplOrg")) {
                // PZO's white chunk bake still contains artwork tint and room
                // blackout. Preserve col.rgb (which is distinct from col.a).
                relight=ground+".rgb * col.rgb";
                // darkMulti is also part of PZO's final light, not just its bake.
                // Rebase only fully visible, single-floor pixels; fading/unseen
                // receivers retain their native contribution and the ratio path.
                header+="\nfloat vpIsoRelightEligible(vec4 owner,float depth) {\n"
                        +"float coverage=owner.a-owner.r; if(owner.r<0.0 || coverage<.999*owner.a || coverage<.003)return 0.0;\n"
                        +"float level=owner.g/coverage; if(abs(level-round(level))>.002 || (owner.b-owner.r)/coverage-level*level>.002)return 0.0;\n"
                        +"if(pplOn<.5 || (int(pplOpt2.w+.5)&16)!=0)return 0.0;\n"
                        +"vec3 p=pplPos(gl_FragCoord.xy,depth); ivec2 square=ivec2(floor(p.xy+.004));\n"
                        +"vec4 state=texelFetch(pplConn,ivec3((square+pplOrg.xy)&pplOrg.z,int(round(level))&pplOrg.w),0);\n"
                        +"return pplVisible(state.a) && (int(state.b*255.0+.5)&127)==127 ? 1.0 : 0.0; }\n";
                // This helper references original PZO declarations/functions, so
                // append it after their definitions rather than in the header.
                int helper=header.indexOf("\nfloat vpIsoRelightEligible");
                String definition=header.substring(helper);header=header.substring(0,helper);
                source+="\n"+definition;
                valid="vpIsoRelightEligible("+metadata+",gl_FragDepth)";
            }
        } else {
            contribution = value + ".rgb * vpIsoGroundTag";
        }
        source = MAIN.matcher(source).replaceFirst("void vpIsoOriginalMain()");
        // Header follows #version and extensions, so legacy/native GLSL syntax remains valid.
        int insertion = source.indexOf('\n') + 1;
        while (source.startsWith("#extension", insertion)) insertion = source.indexOf('\n', insertion) + 1;
        if (output.equals("vpIsoGroundOutput")) header = "\n#extension GL_ARB_explicit_attrib_location : enable\n" + header;
        source = source.substring(0, insertion) + header + source.substring(insertion);
        String extra=relightSlots?" if(vpIsoRelightWrite>.5) { "+relightOutput+"=vec4("+relight+","+value+".a); "
                +validOutput+"=vec4("+valid+" * "+alpha+",0,0,"+value+".a); }":"";
        if(floorSlot)extra+=" if(vpIsoFloorWrite>.5) "+floorOutput+"=vec4("+floorMoments+","+value+".a);";
        return source + "\nvoid main() { vpIsoOriginalMain(); " + broadcast + output + " = vec4(" + contribution + ", " + value + ".a); "
                + "if(vpIsoGrassMaskOn > 0.5) " + maskOutput + " = vec4("+occlusion+", "+first+", "+second+", "+metadataAlpha+");"+extra+" }\n";
    }
    private static int bodyEnd(String source,int open) {
        int depth=0;
        for(int i=open;i<source.length();i++) {
            if(source.charAt(i)=='{')depth++;
            else if(source.charAt(i)=='}' && --depth==0)return i+1;
        }
        throw new IllegalStateException("Unclosed native shader body");
    }
    private NativeGroundShaders() {}
}
