package vpiso;

import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;

/** Supplies native depth to the original visibility polygon renderer, including installed overrides. */
public final class NativeVision {
    private static final int UNIT = 146;
    private static GlProgram program;
    private static int vao;
    private static final Map<Object, Boolean> MODEL_QUEUED = new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> MODEL_QUEUING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> MODEL_DRAWING = ThreadLocal.withInitial(() -> false);
    private boolean enabled;
    private final float[] depth = new float[4];
    public RotationCamera.Snapshot rotation;

    public static final String VERTEX = """
        #version 330
        out vec2 uv;
        void main() {
          uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
          gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
        }
        """;
    public static final String FRAGMENT = """
        #version 330
        in vec2 uv;
        uniform sampler2D sourceDepth;
        uniform mat4 inverseCamera;
        uniform vec4 nativeDepth;
        uniform float rotatedDepth;
        uniform float stabilizeDepth;
        uniform vec2 sourceJitter;
        float stableRotatedDepth() {
          ivec2 size=textureSize(sourceDepth,0);
          vec2 pixel=uv*vec2(size)-sourceJitter;
          ivec2 p=clamp(ivec2(floor(pixel)),ivec2(0),size-1);
          float d=texelFetch(sourceDepth,p,0).r;
          if (d>=1.0) return 1.0;
          // Depth is affine in screen space on each triangle. Evaluate that plane at the
          // unjittered pixel, rather than copying a different Halton sample each frame.
          // Pick the smaller one-sided slope so silhouettes do not blend across objects/sky.
          vec2 dx=vec2(d-texelFetch(sourceDepth,max(p-ivec2(1,0),ivec2(0)),0).r,
                       texelFetch(sourceDepth,min(p+ivec2(1,0),size-1),0).r-d);
          vec2 dy=vec2(d-texelFetch(sourceDepth,max(p-ivec2(0,1),ivec2(0)),0).r,
                       texelFetch(sourceDepth,min(p+ivec2(0,1),size-1),0).r-d);
          // An extremum may be a one-texel pole against sky on both sides. Keep its depth.
          vec2 slope=vec2(dx.x*dx.y<=0.0?0.0:(abs(dx.x)<abs(dx.y)?dx.x:dx.y),
                          dy.x*dy.y<=0.0?0.0:(abs(dy.x)<abs(dy.y)?dy.x:dy.y));
          return clamp(d+dot(slope,pixel-(vec2(p)+0.5)),0.0,1.0);
        }
        void main() {
          float d=stabilizeDepth>0.5?stableRotatedDepth():texture(sourceDepth,uv).r;
          if (rotatedDepth>0.5) { gl_FragDepth=d; return; }
          if (d>=1.0) { gl_FragDepth=1.0; return; }
          vec4 p=inverseCamera*vec4(uv*2.0-1.0,d*2.0-1.0,1.0);
          vec3 scene=p.xyz/p.w;
          vec3 delta=vec3(-scene.x,-scene.z,scene.y/2.4494896);
          gl_FragDepth=clamp(nativeDepth.x+dot(nativeDepth.yzw,delta),0.0,1.0);
        }
        """;

    public void capture(Frame frame) {
        Object debug=GameAccess.field("zombie.debug.DebugOptions","instance",null);
        Object fbo=GameAccess.field("zombie.debug.DebugOptions","fboRenderChunk",debug);
        Object option=GameAccess.field("zombie.iso.fboRenderChunk.FBORenderDebugOptions","renderVisionPolygon",fbo);
        enabled=(Boolean)GameAccess.call("zombie.debug.BooleanDebugOption","getValue",option,new Class<?>[0]);
        int sx=(int)Math.floor(frame.camX),sy=(int)Math.floor(frame.camY);
        depth[0]=sample(sx,sy,frame.camX,frame.camY,frame.camZ);
        depth[1]=sample(sx,sy,frame.camX+1,frame.camY,frame.camZ)-depth[0];
        depth[2]=sample(sx,sy,frame.camX,frame.camY+1,frame.camZ)-depth[0];
        depth[3]=sample(sx,sy,frame.camX,frame.camY,frame.camZ+1)-depth[0];
    }

    static float sample(int sx,int sy,float x,float y,float z) {
        Object result=GameAccess.call("zombie.iso.IsoDepthHelper","getSquareDepthData",null,
                new Class<?>[]{int.class,int.class,float.class,float.class,float.class},sx,sy,x,y,z);
        return ((Number)GameAccess.field("zombie.iso.IsoDepthHelper$Results","depthStart",result)).floatValue();
    }

    public void queue(int player) {
        if (!enabled) return;
        boolean previous = MODEL_QUEUING.get();
        MODEL_QUEUING.set(true);
        try { RotationLayers.queueVision(player, rotation); }
        finally { MODEL_QUEUING.set(previous); }
    }

    /** Capture ownership with the queued drawer, including the unrotated model view. */
    public static void beforeVision(Object drawer) {
        synchronized (MODEL_QUEUED) {
            if (MODEL_QUEUING.get()) MODEL_QUEUED.put(drawer, true);
            else MODEL_QUEUED.remove(drawer);
        }
    }

    public static boolean enterVision(Object drawer) {
        boolean previous = MODEL_DRAWING.get();
        synchronized (MODEL_QUEUED) { MODEL_DRAWING.set(MODEL_QUEUED.remove(drawer) != null); }
        return previous;
    }

    public static void leaveVision(boolean previous) { MODEL_DRAWING.set(previous); }

    /** Native alpha-only visibility retains VP model colors without PZO's memory recoloring. */
    public static boolean modelVision() { return MODEL_DRAWING.get(); }

    public void writeDepth() {
        FrameContext context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
        int source=((Number)GameAccess.field("viewpoint.render.Targets","depth",targets)).intValue();
        int output=((Number)GameAccess.field("viewpoint.render.FrameContext","outputDrawFbo",context)).intValue();
        int[] viewport=(int[])GameAccess.field("viewpoint.render.FrameContext","viewport",context);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+UNIT);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int sampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,UNIT);
        try {
            if (program==null) {
                program=GlProgram.fromSources("VPISO native visibility depth",new GlProgram.Sources(VERTEX,null,null,null,FRAGMENT),
                        Map.of(),Map.of(),List.of("VPISO native depth"));
                vao=GL30.glGenVertexArrays();
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,output);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_ALWAYS);
            GL11.glDepthMask(true); GL11.glColorMask(false,false,false,false);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,source); GL33.glBindSampler(UNIT,0);
            program.use(); program.setInt("sourceDepth",UNIT);
            boolean upscaled=(Boolean)GameAccess.field("viewpoint.render.FrameContext","upscaled",context);
            program.set("inverseCamera",upscaled
                    ? (org.joml.Matrix4f)GameAccess.field("viewpoint.render.FrameContext","invPlainViewProjection",context)
                    : context.invViewProjection);
            program.set("nativeDepth",depth[0],depth[1],depth[2],depth[3]);
            program.set("rotatedDepth",rotation == null ? 0f : 1f);
            int width=((Number)GameAccess.field("viewpoint.render.FrameContext","width",context)).intValue();
            int height=((Number)GameAccess.field("viewpoint.render.FrameContext","height",context)).intValue();
            program.set("stabilizeDepth",(rotation != null || upscaled) && width>0 && height>0 ? 1f : 0f);
            program.set("sourceJitter",
                    ((Number)GameAccess.field("viewpoint.render.FrameContext","jitterX",context)).floatValue(),
                    ((Number)GameAccess.field("viewpoint.render.FrameContext","jitterY",context)).floatValue());
            GL30.glBindVertexArray(vao); GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding); GL33.glBindSampler(UNIT,sampler);
            GL13.glActiveTexture(active);
        }
    }
}
