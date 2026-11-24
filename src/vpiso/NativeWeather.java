package vpiso;

import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import viewpoint.platform.GlProgram;
import viewpoint.render.FrameContext;
import viewpoint.render.Targets;

/** Frame-owned native shelter for rain over a cutaway roof; uploaded only when rain is drawn. */
public final class NativeWeather {
    private static final int TILES = 148, DEPTH = 149;
    private static final ThreadLocal<NativeWeather> ACTIVE = new ThreadLocal<>();
    public static final String GLSL = """
        uniform float vpIsoRainOn;
        uniform sampler2DArray vpIsoRainTiles;
        uniform sampler2D vpIsoRainDepth;
        uniform vec3 vpIsoRainOrigin, vpIsoRainSize, vpIsoRainCam;
        uniform mat4 vpIsoRainInverse, vpIsoRainToDepth;
        bool vpIsoRainSheltered(vec2 uv) {
          if (vpIsoRainOn < 0.5) return false;
          // Rain uses the plain projection; opaque depth includes temporal jitter.
          vec4 dc = vpIsoRainToDepth * vec4(uv * 2.0 - 1.0, 0.0, 1.0);
          vec2 duv = dc.xy / dc.w * 0.5 + 0.5;
          if (any(lessThan(duv, vec2(0.0))) || any(greaterThanEqual(duv, vec2(1.0)))) return false;
          float depth = texture(vpIsoRainDepth, duv).r;
          if (depth >= 1.0) return false;
          vec4 point = vpIsoRainInverse * vec4(duv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
          vec3 pos = point.xyz / point.w;
          float level = vpIsoRainCam.z + pos.y / 2.4494896;
          // The 6000-unit isometric eye loses several depth ulps when unprojecting
          // a floor. Snap only its centimetre-scale reconstruction error to that storey;
          // otherwise a covered ground floor can be mistaken for a missing basement.
          float storey = floor(level + 0.5);
          if (abs(level - storey) < 0.01) level = storey;
          vec3 world = vec3(vpIsoRainCam.x - pos.x, vpIsoRainCam.y - pos.z, level);
          ivec3 tile = ivec3(floor(world - vpIsoRainOrigin));
          if (any(lessThan(tile, ivec3(0))) || any(greaterThanEqual(tile, ivec3(vpIsoRainSize)))) return false;
          // Missing square = 0, exposed = 1, roofed/inside = 2. Missing data stays unchanged.
          return texelFetch(vpIsoRainTiles, tile, 0).r > 1.5 / 255.0;
        }
        """;

    private ByteBuffer pixels;
    private int x, y, z, width, height, layers, texture;
    private int uploadedWidth, uploadedHeight, uploadedLayers;
    private float camX, camY, camZ;
    private boolean ready, dirty, bound;
    private final int[] previousTextures = new int[2], previousSamplers = new int[2];
    private final Matrix4f toDepth = new Matrix4f();

    void capture(boolean precipitation, int x, int y, int z, int width, int height, int layers, float camX, float camY, float camZ) {
        ready=precipitation;
        if (!ready) return;
        this.x=x; this.y=y; this.z=z; this.width=width; this.height=height; this.layers=layers;
        this.camX=camX; this.camY=camY; this.camZ=camZ;
        int count=width*height*layers;
        if (pixels==null || pixels.capacity()<count) pixels=ByteBuffer.allocateDirect(count);
        pixels.clear().limit(count);
        for (int i=0;i<count;i++) pixels.put(i,(byte)0);
        dirty=true;
    }

    void square(int index, boolean sheltered) { pixels.put(index,(byte)(sheltered?2:1)); }

    void begin() {
        if (ACTIVE.get()!=null) throw new IllegalStateException("Native weather snapshots cannot nest");
        ACTIVE.set(this);
    }

    void end() {
        ACTIVE.remove();
        if (!bound) return;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            for (int i=0;i<2;i++) {
                int unit=i==0?TILES:DEPTH;
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
                GL11.glBindTexture(i==0?GL30.GL_TEXTURE_2D_ARRAY:GL11.GL_TEXTURE_2D,previousTextures[i]);
                GL33.glBindSampler(unit,previousSamplers[i]);
            }
        } finally { GL13.glActiveTexture(active); bound=false; }
    }

    public static void onUse(GlProgram program) {
        if (!program.has("vpIsoRainOn")) return;
        // Assign distinct sampler types even when this addon is inactive.
        program.setInt("vpIsoRainTiles",TILES); program.setInt("vpIsoRainDepth",DEPTH);
        NativeWeather data=ACTIVE.get();
        boolean enabled=data!=null && data.ready && IsometricBridge.modelRendering();
        program.set("vpIsoRainOn",enabled?1f:0f);
        if (!enabled) return;
        FrameContext context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
        Targets targets=(Targets)GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
        data.bind(targets.depth);
        program.set("vpIsoRainOrigin",data.x,data.y,data.z);
        program.set("vpIsoRainSize",data.width,data.height,data.layers);
        program.set("vpIsoRainCam",data.camX,data.camY,data.camZ);
        program.set("vpIsoRainInverse",context.invViewProjection);
        Matrix4f plainInverse=(Matrix4f)GameAccess.field("viewpoint.render.FrameContext","invPlainViewProjection",context);
        program.set("vpIsoRainToDepth",data.toDepth.set(context.viewProjection).mul(plainInverse));
    }

    private void bind(int depth) {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            if (!bound) {
                for (int i=0;i<2;i++) {
                    int unit=i==0?TILES:DEPTH;
                    GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
                    previousTextures[i]=GL11.glGetInteger(i==0?GL30.GL_TEXTURE_BINDING_2D_ARRAY:GL11.GL_TEXTURE_BINDING_2D);
                    previousSamplers[i]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit);
                }
                bound=true;
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0+TILES);
            if (texture==0) texture=GL11.glGenTextures();
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,texture); GL33.glBindSampler(TILES,0);
            if (dirty) upload();
            GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth); GL33.glBindSampler(DEPTH,0);
        } finally { GL13.glActiveTexture(active); }
    }

    private void upload() {
        int[] names={GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL12.GL_UNPACK_IMAGE_HEIGHT,
                GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS,GL12.GL_UNPACK_SKIP_IMAGES};
        int[] values=new int[names.length];
        for(int i=0;i<names.length;i++) values[i]=GL11.glGetInteger(names[i]);
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            for(int name:names) GL11.glPixelStorei(name,name==GL11.GL_UNPACK_ALIGNMENT?1:0);
            if (uploadedWidth!=width || uploadedHeight!=height || uploadedLayers!=layers) {
                GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL30.GL_R8,width,height,layers,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,pixels);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL12.GL_TEXTURE_MAX_LEVEL,0);
                uploadedWidth=width; uploadedHeight=height; uploadedLayers=layers;
            } else GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,0,0,0,width,height,layers,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,pixels);
            dirty=false;
        } finally {
            for(int i=0;i<names.length;i++) GL11.glPixelStorei(names[i],values[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
        }
    }
}
