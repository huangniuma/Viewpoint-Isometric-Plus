package vpiso;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import viewpoint.render.SceneData;

/** Optional True Weathers surface effects use the current VP normal/depth format. */
public final class TrueWeatherCompat {
    private static final String DRAW="vptw.AmbientDraw";
    private static final String[] PROGRAMS={"boltProg","glassProg","coverProg","waterProg"};
    private static Boolean present;
    private static final ThreadLocal<Boolean> EARLY=new ThreadLocal<>();
    private static final String HEADER="""
        uniform float vpIsoTwOn;
        uniform vec2 vpIsoTwClips;
        vec3 vpIsoTwNormal(vec4 t) {
          if(vpIsoTwOn<.5)return t.xyz;
          vec2 e=t.xy*2.0-1.0;
          vec3 n=vec3(e,1.0-abs(e.x)-abs(e.y));
          float k=max(-n.z,0.0);
          n.xy+=vec2(n.x>=0.0?-k:k,n.y>=0.0?-k:k);
          return normalize(n)*t.w;
        }
        """;

    public static String fragment(String source) {
        if(source==null || source.contains("uniform float vpIsoTwOn;"))return source;
        boolean normals=source.contains("vec3 n = texture(uNormal, vUv).xyz;");
        boolean depth=source.contains("2.0 * 0.05 * 400.0 / (400.0 + 0.05 - z * (400.0 - 0.05))");
        if(!normals && !depth)return source;
        source=source.replace("vec3 n = texture(uNormal, vUv).xyz;","vec3 n = vpIsoTwNormal(texture(uNormal, vUv));");
        source=source.replace("2.0 * 0.05 * 400.0 / (400.0 + 0.05 - z * (400.0 - 0.05))",
                "vpIsoTwOn>.5 ? 2.0*vpIsoTwClips.x*vpIsoTwClips.y/(vpIsoTwClips.y+vpIsoTwClips.x-z*(vpIsoTwClips.y-vpIsoTwClips.x)) : 2.0*0.05*400.0/(400.0+0.05-z*(400.0-0.05))");
        if(normals)source=source.replace("vec4 alb = texture(uAlbedo, vUv);",
                "vec4 alb = texture(uAlbedo, vUv);\n  if(vpIsoTwOn>.5 && abs(alb.a-208.0/255.0)<.002)discard;");
        int at=source.indexOf('\n')+1;
        return source.substring(0,at)+HEADER+source.substring(at);
    }

    public static void uniforms() {
        boolean models=IsometricBridge.modelRendering();
        int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try {
            for(String name:PROGRAMS) {
                int program=((Number)GameAccess.field(DRAW,name,null)).intValue();
                if(program==0)continue;
                int on=GL20.glGetUniformLocation(program,"vpIsoTwOn");
                if(on<0)continue;
                GL20.glUseProgram(program);GL20.glUniform1f(on,models?1f:0f);
                GL20.glUniform2f(GL20.glGetUniformLocation(program,"vpIsoTwClips"),IsometricBridge.NEAR,IsometricBridge.FAR);
            }
        } finally {GL20.glUseProgram(previous);}
    }

    /** Surface colour must be sampled before precipitation adds its light. */
    public static void beforeWeather(SceneData scene) {
        if(!IsometricBridge.modelRendering())return;
        if(present==null)try {Class.forName(DRAW,false,TrueWeatherCompat.class.getClassLoader());present=true;}
        catch(ClassNotFoundException absent){present=false;}
        if(present) {
            EARLY.set(true);
            try {GameAccess.call(DRAW,"draw",null,new Class<?>[]{Object.class,float.class,float.class,float.class},scene,0f,1.5f,0f);}
            finally {EARLY.remove();}
        }
    }
    public static boolean skipLate() {return IsometricBridge.modelRendering() && !Boolean.TRUE.equals(EARLY.get());}
    private TrueWeatherCompat() {}
}
