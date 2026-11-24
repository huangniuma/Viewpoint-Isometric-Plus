package vpiso;

import org.joml.Matrix4f;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** VP's flashlight/headlight maps, evaluated over native receivers. Render-thread only. */
public final class NativeSpotShadows {
    private static final String SPOTS="viewpoint.render.Spots", RENDERER="viewpoint.render.WorldRenderer";
    private static final FrameContext context=new FrameContext();
    private static Object spots;
    private static boolean ready;

    public static boolean present(SceneData scene) {
        return scene.spots.flashOn || scene.spots.lampCount>0;
    }
    public static void reset() { ready=false; }
    public static boolean ready() { return ready; }
    public static void invalidate(Object shadows) {
        Object pass=GameAccess.field("viewpoint.render.ShadowPass","spots",shadows);
        java.util.Arrays.fill((boolean[])GameAccess.field(SPOTS,"drawn",pass),false);
    }

    public static boolean draw(Object shadows,SceneData scene,Matrix4f handView) {
        reset();
        if(!present(scene))return false;
        spots=GameAccess.field("viewpoint.render.ShadowPass","spots",shadows);
        context.scene=scene;context.pipeline=GlProgram.pack().pipeline;
        context.eyeX=context.eyeZ=0;context.eyeY=1.5f;
        context.view=handView==null?new Matrix4f().setLookAt(0,1.5f,0,0,1.5f,-1,0,1,0):handView;
        GameAccess.call("viewpoint.render.FrameContext","carry",context,new Class<?>[]{long.class},System.nanoTime());
        Object carried=GameAccess.field("viewpoint.render.FrameContext","lights",context);
        GameAccess.call("viewpoint.render.CarriedLights","carry",carried,
                new Class<?>[]{SceneData.class,GameAccess.type("viewpoint.render.ModelPass")},scene,
                GameAccess.field(RENDERER,"models",null));
        boolean previous=(Boolean)GameAccess.field(RENDERER,"handFromPlayer",null);
        Matrix4f view=(Matrix4f)GameAccess.field(RENDERER,"handView",null);
        Matrix4f saved=new Matrix4f(view);
        try {
            GameAccess.set(RENDERER,"handFromPlayer",null,true);view.set(context.view);
            GameAccess.call(SPOTS,"draw",spots,new Class<?>[]{FrameContext.class},context);
            ready=((Number)GameAccess.field(SPOTS,"count",spots)).intValue()>0;
            return ready;
        } finally {
            view.set(saved);GameAccess.set(RENDERER,"handFromPlayer",null,previous);context.scene=null;
        }
    }

    public static void uniforms(GlProgram program) {
        GameAccess.call(SPOTS,"sampler",null,new Class<?>[]{GlProgram.class},program);
        program.setInt("vpIsoSpotsOn",ready?1:0);
        if(!ready) {program.setInt("uSpotCount",0);return;}
        GameAccess.call(SPOTS,"uniforms",spots,new Class<?>[]{GlProgram.class},program);
        GameAccess.call(SPOTS,"bind",spots,new Class<?>[]{boolean.class},true);
    }
    private NativeSpotShadows() {}
}
