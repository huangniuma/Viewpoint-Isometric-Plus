package vpiso;

import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;
import viewpoint.platform.GlProgram;

/** Samples only original color/depth and VP shadow maps; never VP world color. */
public final class NativeOverlay {
    public static final String VERTEX = NativeVision.VERTEX;
    public static final String FRAGMENT = """
        #version 330
        in vec2 uv;
        out vec4 outputColor;
        uniform sampler2D nativeColor;
        uniform sampler2D nativeDepth;
        uniform sampler2D nativeGround;
        uniform sampler2D nativeSurfaces;
        uniform sampler2D nativeNormals;
        uniform sampler2D nativeSolar;
        uniform sampler2D nativeMetadata;
        uniform sampler2D nativeFloor;
        uniform int floorOn;
        uniform sampler2D nativeRelight, nativeRelightValidity, nativeVisible;
        uniform sampler2D vpIsoNativeWalls;
        uniform int relightOn;
        uniform ivec2 nativeVisibleOrigin;
        uniform int nativeVisibleLevel;
        uniform int metadataOn;
        uniform float nativeWorldZ;
        uniform int surfacesOn;
        uniform mat4 inverseNative;
        uniform vec3 nativeOrigin;
        uniform float shadowDistance;
        uniform float shadowStrength;
        uniform int shadowsOn;
        """ + NativeStyle.GLSL + NativeOwnerMetadata.GLSL + NativeSourceSurface.GLSL + """
        vec3 vpIsoProxyRatio(vec4 surface,vec4 normal) {
          if(abs(abs(surface.a)-6.0)<.1) {
            vec2 travel=texture(nativeSolar,uv).rg;
            vec3 ray=normalize(vec3(-inverseNative[2].x,inverseNative[2].z*2.4494896,-inverseNative[2].y));
            vec3 solar=surface.xyz+ray*travel.x+normalize(uSunDir)*travel.y;
            return vpIsoLightingRatio(surface.xyz,normal.xyz,step(0.0,surface.a),normal.a*2.4494896-uSceneOrigin.y,solar);
          }
          if(abs(abs(surface.a)-5.0)<.1)
            return vpIsoLightingRatio(surface.xyz,vec3(0,1,0),step(0.0,surface.a),normal.a*2.4494896-uSceneOrigin.y,surface.xyz+normal.xyz);
          return dot(normal.xyz,normal.xyz)>.5?vpIsoLightingRatio(surface.xyz,normal.xyz,step(0.0,surface.a),normal.a*2.4494896-uSceneOrigin.y):vec3(1);
        }
        bool vpIsoTableReceiver(vec3 pixel,vec3 ray,vec2 key,vec3 lo,vec3 hi,float base,out vec3 at) {
          at=vec3(0.0);
          if(key.y<=0.0 || abs(ray.y)<1.0e-6)return false;
          vec2 size=vec2(textureSize(nativeSurfaces,0));
          vec4 current=texture(nativeSurfaces,uv),currentNormal=texture(nativeNormals,uv);
          if(abs(current.a)>.5 && abs(current.a)<1.5 && currentNormal.y>.99
              && abs(current.y-hi.y)<.002
              && abs(currentNormal.a*2.4494896-uSceneOrigin.y-base)<.002
              && all(equal(texture(nativeSolar,uv).zw,key))) {at=current.xyz;return true;}
          // Extend a table's own horizontal face over small differences between
          // the native illustration and its replacement geometry. Identity,
          // level and volume checks prevent borrowing another object's surface.
          float radius=clamp(.16*size.x/max(length(inverseNative[0].xy),1.0e-5),1.0,40.0);
          float best=1.0e20;bool found=false;
          for(int ring=0;ring<5;ring++)for(int direction=0;direction<8;direction++) {
            if(ring==0 && direction>0)continue;
            float angle=float(direction)*.785398163;
            vec2 offset=ring==0?vec2(0):vec2(cos(angle),sin(angle))*radius*float(ring)/4.0;
            vec2 sampleUv=uv+offset/size;
            if(any(lessThan(sampleUv,vec2(0))) || any(greaterThanEqual(sampleUv,vec2(1))))continue;
            vec4 point=texture(nativeSurfaces,sampleUv),normal=texture(nativeNormals,sampleUv);
            if(abs(point.a)<.5 || abs(point.a)>1.5 || normal.y<.99
                || abs(normal.a*2.4494896-uSceneOrigin.y-base)>.002
                || any(notEqual(texture(nativeSolar,sampleUv).zw,key)))continue;
            vec3 candidate=pixel+ray*((point.y-pixel.y)/ray.y);
            if(any(lessThan(candidate,lo-vec3(.20))) || any(greaterThan(candidate,hi+vec3(.20))))continue;
            float score=dot(offset,offset);
            // At an illustrated lip the closer model pixel may be the lower
            // bench. The nearest upward layer toward the orthographic eye is
            // the foreground surface; distance breaks ties within that plane.
            if(!found || candidate.y>at.y+.002 || abs(candidate.y-at.y)<=.002 && score<best)
              {at=candidate;best=score;found=true;}
          }
          return found;
        }
        void main() {
          vec4 color=texture(nativeColor,uv);
          bool anyShadow=shadowsOn!=0;
          anyShadow=anyShadow || vpIsoLampsOn!=0;
          anyShadow=anyShadow || vpIsoSpotsOn!=0;
          if (anyShadow) {
            float d=texture(nativeDepth,uv).r;
            vec3 delta=(inverseNative*vec4(vec3(uv,d)-nativeOrigin,0.0)).xyz;
            vec3 pos=vec3(-delta.x,delta.z*2.4494896,-delta.y);
            float fade=1.0-smoothstep(shadowDistance*.85,shadowDistance,length(pos.xz));
            // Reuse the existing depth gate rather than nesting/duplicating the
            // selected pack's complete lighting graph on large shader packs.
            if (d>0.0 && d<1.0 && fade>0.0) {
              vec3 ground=clamp(texture(nativeGround,uv).rgb,vec3(0.0),color.rgb);
              vec4 surface=surfacesOn!=0?texture(nativeSurfaces,uv):vec4(0.0);
              vec3 floorPos=pos;
              bool floorValid=false;
              vec4 nativeOwner=metadataOn!=0?texture(nativeMetadata,uv):vec4(0.0);
              ivec2 ownerCode=ivec2(1,0);float floorLevel=nativeWorldZ;
              bool decoded=metadataOn!=0 && vpIsoDecodeOwner(nativeOwner,ownerCode,floorLevel,floorValid);
              // Floor contribution survives any number of overlapping foreground
              // owners; it no longer depends on decoding their identities.
              if(floorOn!=0)floorValid=vpIsoDecodeFloor(texture(nativeFloor,uv),floorLevel);
              if(metadataOn==0)ownerCode=ivec2(1,0);
              bool bladePixel=decoded && ownerCode==ivec2(-1,0);
              bool wallPixel=decoded && ownerCode.x== -1 && ownerCode.y!=0;
              int sourceId=decoded?vpIsoSourceId(ownerCode):0;
              if(metadataOn!=0) {
                if(bladePixel)floorValid=true; // actual VP blade depth
                else if(floorValid) {
                  vec3 ray=(inverseNative*vec4(0,0,1,0)).xyz;
                  floorValid=abs(ray.z)>1e-6;
                  if(floorValid) {
                    vec3 floorDelta=delta+ray*((floorLevel-nativeWorldZ-delta.z)/ray.z);
                    floorPos=vec3(-floorDelta.x,floorDelta.z*2.4494896,-floorDelta.y);
                  }
                }
              } else if(abs(abs(surface.a)-2.0)<.1) { floorPos=surface.xyz;floorValid=true; }
              // Change each native contribution once, using the selected pack's
              // light ratio. A foreground model can never relocate a native floor.
              vec3 floorColor=ground;
              bool floorRelit=false;
              if(floorValid && relightOn!=0) {
                ivec2 square=ivec2(floor(-uSceneOrigin.xz-floorPos.xz))-nativeVisibleOrigin;
                bool inside=all(greaterThanEqual(square,ivec2(0))) && all(lessThan(square,ivec2(64)));
                vec4 owner=texture(nativeMetadata,uv);
                bool pureFloor=owner.b>=0.0 && owner.a>.999 && owner.r<.001 && abs((floorPos.y+uSceneOrigin.y)/2.4494896-float(nativeVisibleLevel))<.002;
                if(inside && pureFloor && texelFetch(nativeVisible,square,0).r>.5
                    && texture(nativeRelightValidity,uv).r>.999) {
                  floorColor=vpIsoRelightFloor(ground,texture(nativeRelight,uv).rgb,floorPos);
                  floorRelit=true;
                }
              }
              if(floorValid && !floorRelit)
                floorColor=ground*vpIsoLightingRatio(floorPos,vec3(0,1,0),metadataOn!=0?-1.0:step(0.0,surface.a));
              vec3 body=color.rgb-ground;
              vec3 bodyRatio=vec3(1);
              bool physical=abs(surface.a)>.5 && (abs(surface.a)<1.5 || abs(surface.a)>2.5);
              bool uncertainWall=metadataOn!=0 && !decoded && nativeOwner.r<0.0 && nativeOwner.r>=-1.002*nativeOwner.a;
              bool sourceResolved=false;
              if(sourceId>=2) {
                vec3 at,n,solar;vec2 key;
                float base;
                vec3 ray=normalize(vec3(-inverseNative[2].x,inverseNative[2].z*2.4494896,-inverseNative[2].y));
                if(vpIsoSourceReceiver(sourceId,pos,ray,at,n,solar,base,key)) {
                  vec2 proxyKey=texture(nativeSolar,uv).zw;
                  vec4 source=vpIsoSourceRecord(sourceId,0);
                  vec3 root=vec3(-source.x-uSceneOrigin.x,base,-source.y-uSceneOrigin.z);
                  vec3 lo=root+vpIsoSourceRecord(sourceId,1).xyz,hi=root+vpIsoSourceRecord(sourceId,2).xyz;
                  bool keyed=key.y>0.0 && all(equal(key,proxyKey));
                  // Procedural ordinary meshes have no packed key. Retain their
                  // real side normals and position only inside this source's bounds.
                  bool procedural=(source.w==3.0 || source.w==5.0) && proxyKey.y==0.0 && abs(surface.a)<1.5
                      && all(greaterThanEqual(surface.xyz,lo-vec3(.025))) && all(lessThanEqual(surface.xyz,hi+vec3(.025)));
                  // A multi-tile tabletop's illustration can cross its model-part
                  // boundary. A real upward face at this owner's exact top/level
                  // corroborates the same plane even when the adjacent part owns
                  // that face. Keep its physical point instead of snapping down
                  // to the current part's bounding-box side.
                  vec4 proxyNormal=texture(nativeNormals,uv);
                  bool continuousTop=(source.w==3.0 || source.w==5.0) && proxyKey.y>0.0 && abs(surface.a)<1.5
                      && proxyNormal.y>.99 && abs(surface.y-hi.y)<.002
                      && abs(proxyNormal.a*2.4494896-uSceneOrigin.y-base)<.002;
                  vec3 tableAt=vec3(0.0);
                  if(source.w==5.0 && vpIsoTableReceiver(pos,ray,key,lo,hi,base,tableAt))
                    bodyRatio=vpIsoLightingRatio(tableAt,vec3(0,1,0),-1.0,base);
                  else
                    bodyRatio=physical && (keyed || procedural || continuousTop)?vpIsoProxyRatio(surface,proxyNormal):
                        vpIsoLightingRatio(at,n,-1.0,base,solar);
                  sourceResolved=true;
                }
              } else if(wallPixel) {
                // Source wall IDs and two moments travel through the exact native
                // artwork draw/filter. A different foreground cannot borrow the
                // nearest plant proxy; mixed/fading/unknown walls stay native.
                int id=ownerCode.y;
                if(id>=2 && id<1024) {
                  vec4 wall=texelFetch(vpIsoNativeWalls,ivec2(int(id),0),0);
                  if(wall.w==1.0 || wall.w==2.0) {
                    int axis=wall.w==1.0?0:2;
                    vec3 ray=vec3(-inverseNative[2].x,inverseNative[2].z*2.4494896,-inverseNative[2].y);
                    float plane=-(axis==0?wall.x:wall.y)-uSceneOrigin[axis];
                    if(abs(ray[axis])>1.0e-6) {
                      vec3 at=pos+ray*((plane-pos[axis])/ray[axis]);
                      float height=at.y+uSceneOrigin.y-wall.z*2.4494896;
                      // The native wall mask supplies the finite visible extent.
                      if(height>=-.08 && height<=2.5294896) {
                        vec3 n=axis==0?vec3(-1,0,0):vec3(0,0,-1);
                        if(dot(n,vpIsoLightingEye-at)<0.0)n=-n;
                        bodyRatio=vpIsoLightingRatio(at,n,-1.0,wall.z*2.4494896-uSceneOrigin.y);
                      }
                    }
                  }
                }
              }
              if(!sourceResolved && physical && !wallPixel && !uncertainWall)
                bodyRatio=vpIsoProxyRatio(surface,texture(nativeNormals,uv));
              color.rgb=mix(ground,floorColor,fade)+body*mix(vec3(1),bodyRatio,fade);
            } else if(d>0.0 && d<1.0) {
              vec3 ground=clamp(texture(nativeGround,uv).rgb,vec3(0.0),color.rgb);
              color.rgb=ground+(color.rgb-ground);
            }
          }
          outputColor=vpIsoStyle(color);
        }
        """;

    private GlProgram program;
    private Object programPack;
    private final NativeSurfaces surfaces=new NativeSurfaces();
    private int vao, color, depth, width, height, output;
    private final int[] viewport = new int[4];
    private boolean copiedDepth;
    private boolean reportedReadSource;
    private int ground, groundCopy;
    private int metadata,metadataCopy,floor,floorCopy;
    private int relight,validity,relightCopy,validityCopy,visible;
    private int wallOwners;
    private long wallRevision=-1;
    private final long[] ownerRevisions=new long[193];
    private final java.nio.FloatBuffer wallPixels=org.lwjgl.BufferUtils.createFloatBuffer(NativeGround.WALL_OWNERS*4+NativeGround.SOURCE_OWNERS*12);
    private NativeRelightMask relightMask;
    private final java.nio.ByteBuffer visiblePixels=org.lwjgl.BufferUtils.createByteBuffer(64*64*2);
    public void relight(int base,int valid,NativeRelightMask mask) {relight=base;validity=valid;relightMask=mask;}
    public void ground(int texture) { ground=texture; }
    public void metadata(int texture) { metadata=texture; }
    public void floor(int texture) {floor=texture;}
    public NativeSurfaces surfaces(NativeReceiver receiver) {
        surfaces.prepare(receiver,depth,width,height);return surfaces;
    }

    public void capture(boolean needsDepth) {
        surfaces.reset();
        output = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int sourceReadBuffer=-1;
        try {
            ensure(viewport[2], viewport[3]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, output);
            sourceReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            // The final image is attachment0. Never inherit an auxiliary MRT source
            // for the displayed native color (foreground RGB is zero in attachment7).
            if(output!=0) {
                if(sourceReadBuffer!=GL30.GL_COLOR_ATTACHMENT0 && !reportedReadSource) {
                    reportedReadSource=true;
                    System.out.println("[VPISO] native color capture corrected read buffer="+sourceReadBuffer+" to attachment0; framebuffer="+output);
                }
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, viewport[0], viewport[1], width, height);
            copiedDepth = needsDepth && GL11.glGetInteger(GL11.GL_DEPTH_BITS) > 0;
            if (copiedDepth) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depth);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, viewport[0], viewport[1], width, height);
            }
            if (ground!=0) {
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+7);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,groundCopy);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
            }
            if(metadata!=0) {
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+6);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,metadataCopy);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,viewport[0],viewport[1],width,height);
            }
            if(floor!=0) {
                if(floorCopy==0)floorCopy=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,GL11.GL_FLOAT);
                GL43.glCopyImageSubData(floor,GL11.GL_TEXTURE_2D,0,viewport[0],viewport[1],0,
                        floorCopy,GL11.GL_TEXTURE_2D,0,0,0,0,width,height,1);
            }
            if(relight!=0 && validity!=0) {
                if(relightCopy==0) {
                    relightCopy=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT);
                    validityCopy=texture(GL30.GL_R16F,GL11.GL_RED,GL11.GL_FLOAT);
                }
                GL43.glCopyImageSubData(relight,GL11.GL_TEXTURE_2D,0,viewport[0],viewport[1],0,
                        relightCopy,GL11.GL_TEXTURE_2D,0,0,0,0,width,height,1);
                GL43.glCopyImageSubData(validity,GL11.GL_TEXTURE_2D,0,viewport[0],viewport[1],0,
                        validityCopy,GL11.GL_TEXTURE_2D,0,0,0,0,width,height,1);
            }
        } finally {
            if(sourceReadBuffer>=0) GL11.glReadBuffer(sourceReadBuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
            GL13.glActiveTexture(active);
        }
    }

    public boolean hasDepth() { return copiedDepth; }

    public void draw(NativeReceiver receiver, IsometricEffects effects, int map,
                     Matrix4f[] matrices, float[] texels, float range, int mapSize, int count, float daylight) {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] lampState=NativeLampShadows.save();
        int[] textures = new int[17], samplers = new int[17];
        for (int i = 0; i < 17; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(i == 2 ? GL30.GL_TEXTURE_BINDING_2D_ARRAY : GL11.GL_TEXTURE_BINDING_2D);
            samplers[i] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, i);
        }
        try (NativeLighting.Scope lighting=NativeLighting.open(effects.needsShadows()?NativeShadowMaps.scene():null,receiver)) {
            if (program == null || programPack!=GlProgram.pack()) {
                GlProgram next = GlProgram.fromSources("VPISO native effects", new GlProgram.Sources(VERTEX, null, null, null, NativeLighting.fragment(FRAGMENT)),
                        Map.of(), Map.of(), List.of("VPISO native effects"));
                if(program!=null)program.delete();
                program=next;programPack=GlProgram.pack();
                if(vao==0)vao = GL30.glGenVertexArrays();
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, output);
            GL11.glViewport(viewport[0], viewport[1], width, height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            for (int i = 0; i < 17; i++) {
                if(i==14 || i==15)continue; // VP lighting owns 14; PZO may own 15.
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i); GL33.glBindSampler(i, 0);
                if(i==5 || i==6) continue; // VP owns light atlas5 and sun cascade6.
                GL11.glBindTexture(i == 2 ? GL30.GL_TEXTURE_2D_ARRAY : GL11.GL_TEXTURE_2D, i == 0 ? color : i == 1 ? depth : i == 2 ? map : i==3 ? groundCopy : i==4 ? surfaces.texture() : i==8 ? metadataCopy : i==9?relightCopy:i==10?validityCopy:i==11?visible:i==12?wallOwners:i==13?surfaces.solar():i==16?floorCopy:surfaces.normals());
            }
            boolean rebase=relight!=0 && validity!=0 && relightMask!=null;
            if(relightMask!=null) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+11);
                int unpack=GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
                GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
                try {
                    org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER,0);
                    for(int state:new int[]{GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,
                            GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES})
                        GL11.glPixelStorei(state,0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
                    visiblePixels.clear();
                    for(int i=0;i<64*64;i++)visiblePixels.put(relightMask.pixels[i]).put(relightMask.exterior[i]);
                    visiblePixels.flip();
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,64,64,GL30.GL_RG,GL11.GL_UNSIGNED_BYTE,visiblePixels);
                } finally {
                    GL11.glPopClientAttrib();org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
                }
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
            if(wallOwners==0 || wallRevision!=NativeGround.wallRevision()) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+12);
                int unpack=GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
                GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
                try {
                    org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER,0);
                    for(int state:new int[]{GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,
                            GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES})
                        GL11.glPixelStorei(state,0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
                    boolean initial=wallOwners==0;
                    if(initial) {
                        wallOwners=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,wallOwners);
                        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,NativeGround.WALL_OWNERS,193,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                    }
                    // Moving into new chunks changes only a few source rows.
                    // Initialise every texel once, then upload all changed rows,
                    // including cleared/recycled identities, before this draw.
                    for(int row=0;row<ownerRevisions.length;) {
                        if(!initial && ownerRevisions[row]==NativeGround.ownerRevision(row)){row++;continue;}
                        int first=row++;
                        while(row<ownerRevisions.length && (initial || ownerRevisions[row]!=NativeGround.ownerRevision(row)))row++;
                        wallPixels.clear();NativeGround.ownerRows(wallPixels,first,row-first);
                        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,first,NativeGround.WALL_OWNERS,row-first,GL11.GL_RGBA,GL11.GL_FLOAT,wallPixels);
                        for(int changed=first;changed<row;changed++)ownerRevisions[changed]=NativeGround.ownerRevision(changed);
                    }
                    wallRevision=NativeGround.wallRevision();
                } finally {
                    GL11.glPopClientAttrib();org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
                }
            }
            program.use();
            lighting.uniforms(program,map,count);
            program.setInt("nativeColor", 0); program.setInt("nativeDepth", 1); program.setInt("shadowMap", 2);
            program.setInt("nativeGround",3);
            program.setInt("nativeSurfaces",4);program.setInt("surfacesOn",surfaces.texture()!=0?1:0);
            program.setInt("nativeNormals",7);program.setInt("nativeMetadata",8);
            program.setInt("nativeSolar",13);
            program.setInt("nativeFloor",16);program.setInt("floorOn",floor!=0?1:0);
            program.setInt("vpIsoNativeWalls",12);
            program.setInt("metadataOn",metadata!=0?1:0);program.set("nativeWorldZ",receiver.worldZ);
            program.setInt("nativeRelight",9);program.setInt("nativeRelightValidity",10);program.setInt("nativeVisible",11);
            program.setInt("relightOn",rebase && lighting.ready()?1:0);
            program.setInt("vpIsoNativeRegions",11);
            program.setInt("vpIsoNativeRegionsOn",relightMask!=null?1:0);
            if(relightMask!=null) {
                org.lwjgl.opengl.GL20.glUniform2i(org.lwjgl.opengl.GL20.glGetUniformLocation(program.id(),"vpIsoNativeRegionOrigin"),relightMask.x,relightMask.y);
                program.setInt("vpIsoNativeRegionLevel",relightMask.z);
            }
            if(rebase) {
                org.lwjgl.opengl.GL20.glUniform2i(org.lwjgl.opengl.GL20.glGetUniformLocation(program.id(),"nativeVisibleOrigin"),relightMask.x,relightMask.y);
                program.setInt("nativeVisibleLevel",relightMask.z);
            }
            boolean shadow = effects.needsShadows() && copiedDepth && ground!=0 && map != 0 && count > 0;
            if (effects.needsShadows()) IsometricBridge.nativeShadowStatus(copiedDepth, ground != 0, map != 0 && count > 0 || NativeLampShadows.ready() || NativeSpotShadows.ready());
            program.setInt("shadowsOn", shadow && lighting.ready() ? 1 : 0);
            program.set("inverseNative", receiver.inverse);
            program.set("nativeOrigin", receiver.origin[0], receiver.origin[1], receiver.origin[2]);
            program.set("shadowDistance", effects.distance());
            program.set("shadowStrength", effects.shadowStrength());
            program.set("vpIsoNativeShadowSoftness",effects.softness());
            NativeLampShadows.uniforms(program);
            program.setInt("vpIsoLampsOn",effects.needsShadows() && copiedDepth && lighting.ready() && NativeLampShadows.ready()?1:0);
            program.set("vpIsoLampStrength",effects.shadowStrength());
            program.setInt("vpIsoSpotsOn",effects.needsShadows() && copiedDepth && lighting.ready() && NativeSpotShadows.ready()?1:0);
            program.set("vpIsoStyleOn", effects.style() ? 1f : 0f);
            program.set("vpIsoStyleStrength", effects.styleStrength()); program.set("vpIsoStyleExposure", effects.exposure());
            program.set("vpIsoStyleSaturation", effects.saturation()); program.set("vpIsoStyleContrast", effects.contrast());
            GL30.glBindVertexArray(vao); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            NativeLampShadows.restore(lampState);
            for (int i = 0; i < 17; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glBindTexture(i == 2 ? GL30.GL_TEXTURE_2D_ARRAY : GL11.GL_TEXTURE_2D, textures[i]);
                GL33.glBindSampler(i, samplers[i]);
            }
            GL13.glActiveTexture(active);
        }
    }

    private void ensure(int w, int h) {
        if (width == w && height == h && color != 0) return;
        if (color != 0) { GL11.glDeleteTextures(color); GL11.glDeleteTextures(depth); GL11.glDeleteTextures(groundCopy); GL11.glDeleteTextures(metadataCopy); }
        GL11.glDeleteTextures(relightCopy);GL11.glDeleteTextures(validityCopy);
        GL11.glDeleteTextures(floorCopy);floorCopy=0;
        relightCopy=validityCopy=0;
        width = w; height = h;
        color = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        depth = texture(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        groundCopy = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        metadataCopy = texture(GL30.GL_RGBA32F,GL11.GL_RGBA,GL11.GL_FLOAT);
        if(visible==0) {
            visible=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,visible);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RG8,64,64,0,GL30.GL_RG,GL11.GL_UNSIGNED_BYTE,0L);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        }
    }
    private int texture(int internal, int format, int type) {
        int name = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, name);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, width, height, 0, format, type, 0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return name;
    }
}
