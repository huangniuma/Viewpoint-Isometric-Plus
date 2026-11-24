package vpiso;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.SceneData;

/** Geometry used only to locate shadow receivers; the displayed art remains native. */
public final class NativeSurfaces {
    private static final int DEPTH_UNIT=140, EXPOSURE_UNIT=141;
    private static final Pattern INCLUDE=Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final String OUTPUT="""
        in vec3 vpIsoSurfacePos;
        in vec3 vpIsoSurfaceNormal;
        flat in float vpIsoSurfaceBase;
        flat in float vpIsoSurfaceKind;
        flat in vec4 vpIsoPlantRoot;
        flat in vec3 vpIsoPlantBounds;
        flat in vec2 vpIsoSourceKey;
        layout(location=0) out vec4 vpIsoSurfaceOutput;
        layout(location=1) out vec4 vpIsoNormalOutput;
        layout(location=2) out vec4 vpIsoSolarOutput;
        uniform vec3 vpIsoEye;
        uniform sampler2D vpIsoOriginalDepth, vpIsoExposure;
        uniform mat4 vpIsoInverse, uViewProjection;
        uniform vec3 vpIsoOrigin;
        uniform vec2 vpIsoSize, vpIsoExposureOrigin;
        uniform float vpIsoFloor;
        uniform float vpIsoOwnerOriginY;
        uniform vec3 vpIsoSun;
        // A crossed card is a caster, not a continuous canopy receiver. Find
        // the camera-facing point of the enclosing crown, then its sunward
        // skin. Keep this offset separate from lamp, room and light-grid data.
        vec2 vpIsoCrownSolarTravel() {
          if(vpIsoPlantRoot.w<.5) return vec2(0);
          vec3 ray=normalize(vec3(-vpIsoInverse[2].x,vpIsoInverse[2].z*2.4494896,-vpIsoInverse[2].y));
          vec3 ro=vpIsoSurfacePos-vpIsoPlantRoot.xyz;
          float radius=vpIsoPlantBounds.x;
          float a=dot(ray.xz,ray.xz),b=dot(ro.xz,ray.xz),c=dot(ro.xz,ro.xz)-radius*radius;
          float enter=-1.0e20,leave=1.0e20;
          if(a>1.0e-10) {
            float disc=b*b-a*c;
            if(disc<0.0) return vec2(0);
            float root=sqrt(disc);enter=(-b-root)/a;leave=(-b+root)/a;
          } else if(c>0.0) return vec2(0);
          if(abs(ray.y)>1.0e-6) {
            vec2 cap=(vpIsoPlantBounds.yz-ro.y)/ray.y;
            enter=max(enter,min(cap.x,cap.y));leave=min(leave,max(cap.x,cap.y));
          } else if(ro.y<vpIsoPlantBounds.y || ro.y>vpIsoPlantBounds.z) return vec2(0);
          if(enter>leave) return vec2(0);
          vec3 front=ro+ray*enter;
          float sunLength=length(vpIsoSun);
          if(sunLength<1.0e-6) return vec2(0);
          vec3 sun=vpIsoSun/sunLength;
          float sa=dot(sun.xz,sun.xz),sb=dot(front.xz,sun.xz),sc=dot(front.xz,front.xz)-radius*radius;
          float side=sa>1.0e-10?(-sb+sqrt(max(0.0,sb*sb-sa*sc)))/sa:1.0e20;
          float cap=abs(sun.y)>1.0e-6?((sun.y>0.0?vpIsoPlantBounds.z:vpIsoPlantBounds.y)-front.y)/sun.y:1.0e20;
          vec2 travel=vec2(enter,max(min(side,cap),0.0));
          if(any(isnan(travel)) || any(isinf(travel))) return vec2(0);
          return travel;
        }
        void main() {
          vpIsoSurfaceOriginal();
          gl_FragDepth=gl_FragCoord.z;
          vec2 uv=gl_FragCoord.xy/vpIsoSize;
          float d=texture(vpIsoOriginalDepth,uv).r;
          if(d<=0.0 || d>=1.0) discard;
          vec3 delta=(vpIsoInverse*vec4(vec3(uv,d)-vpIsoOrigin,0.0)).xyz;
          vec3 nativePos=vec3(-delta.x,delta.z*2.4494896,-delta.y);
          // Packed furniture can have floor depth even at a tabletop pixel.
          // Rejecting its top here exposes legs/undersides as receivers. Exact
          // packed ownership is validated by the compositor; unrelated meshes
          // and foliage retain the original proximity guard.
          bool ownedSolid=vpIsoSourceKey.y>0.0 && vpIsoPlantRoot.w<.5
              && dot(vpIsoSurfaceNormal,vpIsoSurfaceNormal)>.6;
          if(!ownedSolid && distance(nativePos,vpIsoSurfacePos)>1.25) discard;
          vec3 face=cross(dFdx(vpIsoSurfacePos),dFdy(vpIsoSurfacePos));
          // Match VP Surface.resolve: foliage uses its upward sentinel normal,
          // independently of which crossed card wins the depth test.
          bool foliage=dot(vpIsoSurfaceNormal,vpIsoSurfaceNormal)<.6;
          vec3 n=dot(vpIsoSurfaceNormal,vpIsoSurfaceNormal)>1.0e-12?normalize(vpIsoSurfaceNormal):vec3(0,1,0);
          if(!foliage && dot(face,n)*dot(face,vpIsoSurfacePos-vpIsoEye)>0.0) n=-n;
          vec2 square=floor(vpIsoExposureOrigin-vpIsoSurfacePos.xz-n.xz*.025);
          float outside=0.0;
          if(all(greaterThanEqual(square,vec2(0))) && all(lessThan(square,vec2(64)))
              && abs(vpIsoSurfaceBase-vpIsoFloor)<.1)
            // VP exposure: 0 indoors, 100 outside under a roof, 255 open sky.
            // Covered outdoor surfaces still receive the roof's physical projection.
            outside=step(.5/255.0,texelFetch(vpIsoExposure,ivec2(square),0).r);
          // Sign keeps indoor receivers available to lamps while excluding sunlight.
          // Roof artwork remains a foreground contribution in the native renderer.
          // Keep it separate from ground so it can receive other objects' shadows.
          bool packedCrown=vpIsoPlantRoot.w>1.5;
          bool crown=foliage && vpIsoPlantRoot.w>.5 && !packedCrown;
          // Crossed leaves keep their alpha coverage, but the enclosing crown
          // owns reception and depth. Adjacent cards/trees must not alternate
          // the receiving owner as the native camera moves by subpixels.
          vec3 receive=vpIsoSurfacePos;
          vec2 travel=crown?vpIsoCrownSolarTravel():vec2(0);
          if(crown) {
            vec3 ray=normalize(vec3(-vpIsoInverse[2].x,vpIsoInverse[2].z*2.4494896,-vpIsoInverse[2].y));
            receive+=ray*travel.x;
            vec4 clip=uViewProjection*vec4(receive,1);
            gl_FragDepth=clip.z/clip.w*.5+.5;
          }
          vpIsoSurfaceOutput=vec4(receive,(packedCrown?6.0:crown?5.0:foliage?4.0:vpIsoSurfaceKind)*(outside>.5?1.0:-1.0));
          // The owning floor, rather than a tall vertex's height, selects its light grid.
          // Integer levels remain exact in the half-float normal target, including
          // negative floors and a camera standing between storeys.
          // Kind5 has an implicit upward foliage normal. Reuse its RGB payload
          // for a relative solar offset; no additional full-screen attachment.
          vpIsoNormalOutput=vec4(crown?vpIsoSun/max(length(vpIsoSun),1.0e-6)*travel.y:n,round((vpIsoSurfaceBase+vpIsoOwnerOriginY)/2.4494896));
          // Packed vegetation has real side normals. Keep them for lamps; only
          // its solar coordinate uses the continuous envelope of all own parts.
          vpIsoSolarOutput=vec4(packedCrown?vpIsoCrownSolarTravel():vec2(0),vpIsoSourceKey);
        }
        """;
    private GlProgram mesh, model, vehicle;
    private final boolean modelOnly;
    private final NativeReceiverCull receiverCull=new NativeReceiverCull();
    private Object programPack;
    private int fbo, positions, normals, solar, depth, exposure, width, height, originalDepth;
    private NativeReceiver receiver;
    private boolean ready;
    private final java.nio.ByteBuffer exposurePixels=BufferUtils.createByteBuffer(4096);

    public NativeSurfaces() { this(false); }
    public NativeSurfaces(boolean modelOnly) { this.modelOnly=modelOnly; }

    public void prepare(NativeReceiver value,int source,int w,int h) {
        receiver=value; originalDepth=source; width=w; height=h; ready=false;
    }
    public void reset() { ready=false; }
    public int texture() { return ready?positions:0; }
    public int normals() { return ready?normals:0; }
    public int solar() { return ready?solar:0; }

    /** Runs while ModelPass is prepared, before it releases per-frame textures. */
    public void draw(SceneData scene,Object models) {
        draw(scene,models,true);
    }
    /** Physical posed models only; ordinary tile meshes cannot prove actor burial. */
    public void drawModels(SceneData scene,Object models) {
        draw(scene,models,false);
    }
    private void draw(SceneData scene,Object models,boolean worldMeshes) {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] units={DEPTH_UNIT,EXPOSURE_UNIT}, bindings=new int[2], samplers=new int[2];
        for(int i=0;i<2;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+units[i]);
            bindings[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            samplers[i]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,units[i]);
        }
        try {
            ensure();
            GL13.glActiveTexture(GL13.GL_TEXTURE0+DEPTH_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,originalDepth);GL33.glBindSampler(DEPTH_UNIT,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+EXPOSURE_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,exposure);GL33.glBindSampler(EXPOSURE_UNIT,0);
            exposurePixels.clear();exposurePixels.put(scene.exposure).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,64,64,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,exposurePixels);
            // Meshes.bindPage binds its page on the currently active unit.
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1,modelOnly?0:GL30.GL_COLOR_ATTACHMENT2});
            GL11.glViewport(0,0,width,height);
            GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            Matrix4f projection=projection(receiver);
            float[] inverse=receiver.inverse.get(new float[16]);
            Vector3f eye=new Vector3f(inverse[8],-inverse[10]*2.4494896f,inverse[9]).normalize().mul(10000f);
            float floor=(float)(Math.floor(scene.originY/2.4494896+1e-5)-scene.originY/2.4494896)*2.4494896f;
            setup(mesh,projection,scene,floor);mesh.set("uEye",eye.x,eye.y,eye.z);mesh.set("vpIsoEye",eye.x,eye.y,eye.z);
            if(worldMeshes) {
                NativeReceiverCull previous=receiverCull.enter(scene,projection,width,height);
                try { GameAccess.call("viewpoint.render.Meshes","draw",null,
                        new Class<?>[]{SceneData.class,GlProgram.class,byte.class},scene,mesh,(byte)2); }
                finally { NativeReceiverCull.restore(previous); }
            }
            setup(model,projection,scene,floor);
            model.set("vpIsoEye",eye.x,eye.y,eye.z);
            // Native colour/shadow poses share the prepared model uploads. Both rigid
            // and skinned native models receive; vehicle material sampling stays VP's.
            for(boolean rigid:new boolean[]{false,true})
                GameAccess.call("viewpoint.render.ModelPass","iris",models,
                        new Class<?>[]{GlProgram.class,Matrix4f.class,float.class,boolean.class},model,projection,0f,rigid);
            setup(vehicle,projection,scene,floor);
            vehicle.set("vpIsoEye",eye.x,eye.y,eye.z);
            GameAccess.call("viewpoint.render.ModelPass","irisVehicles",models,
                    new Class<?>[]{GlProgram.class,boolean.class},vehicle,false);
            ready=true;
        } finally {
            for(int i=0;i<2;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+units[i]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,bindings[i]);GL33.glBindSampler(units[i],samplers[i]);
            }
            GL13.glActiveTexture(active);
        }
    }

    /** Native normalized screen/depth from VP coordinates, then normalized clip space. */
    public static Matrix4f projection(NativeReceiver receiver) {
        Matrix4f axes=new Matrix4f().set(new float[]{-1,0,0,0,0,0,1f/2.4494896f,0,0,-1,0,0,0,0,0,1});
        Matrix4f nativeForward=new Matrix4f().translate(receiver.origin[0],receiver.origin[1],receiver.origin[2])
                .mul(new Matrix4f(receiver.inverse).invert()).mul(axes);
        return new Matrix4f().set(new float[]{2,0,0,0,0,2,0,0,0,0,2,0,-1,-1,-1,1}).mul(nativeForward);
    }
    private void setup(GlProgram program,Matrix4f projection,SceneData scene,float floor) {
        program.use();program.set("uViewProjection",projection);
        program.set("uFadeNoise",0f);program.set("vpIsoInverse",receiver.inverse);
        program.set("vpIsoOrigin",receiver.origin[0],receiver.origin[1],receiver.origin[2]);
        program.set("vpIsoSize",(float)width,(float)height);
        program.set("vpIsoExposureOrigin",scene.exposureAX,scene.exposureAY);program.set("vpIsoFloor",floor);
        program.set("vpIsoOwnerOriginY",(float)scene.originY);
        program.set("vpIsoSun",scene.sunX,scene.sunY,scene.sunZ);
        program.set("vpIsoSourceOrigin",(float)scene.originX,(float)scene.originY,(float)scene.originZ);
    }
    private void ensure() {
        if(mesh!=null && programPack!=GlProgram.pack()) {
            mesh.delete();model.delete();vehicle.delete();mesh=model=vehicle=null;
        }
        if(mesh==null) {
            Object shadows=GameAccess.field("viewpoint.render.WorldRenderer","shadows",null);
            Object models=GameAccess.field("viewpoint.render.WorldRenderer","models",null);
            Object cars=GameAccess.field("viewpoint.render.ModelPass","vehicles",models);
            mesh=program("shadow.vert","shadow.frag",GameAccess.field("viewpoint.render.ShadowPass","program",shadows),true);
            model=program("model.vert","model_shadow.frag",GameAccess.field("viewpoint.render.ModelPass","shadow",models),false);
            vehicle=program("model.vert","vehicle_shadow.frag",GameAccess.field("viewpoint.render.VehiclePass","shadow",cars),false);
            programPack=GlProgram.pack();
        }
        if(fbo!=0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,positions);
            if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==width
                    && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==height)return;
            GL11.glDeleteTextures(positions);GL11.glDeleteTextures(normals);GL11.glDeleteTextures(solar);GL11.glDeleteTextures(depth);GL30.glDeleteFramebuffers(fbo);
        }
        positions=texture(GL30.GL_RGBA32F,GL11.GL_RGBA,width,height);
        normals=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,width,height);
        solar=modelOnly?0:texture(GL30.GL_RGBA32F,GL11.GL_RGBA,width,height);
        depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT,width,height);
        if(exposure==0)exposure=texture(GL30.GL_R8,GL11.GL_RED,64,64);
        fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,positions,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT1,GL11.GL_TEXTURE_2D,normals,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT2,GL11.GL_TEXTURE_2D,solar,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Native surface receiver framebuffer incomplete");
    }
    @SuppressWarnings("unchecked")
    private static GlProgram program(String vertex,String fragment,Object original,boolean mesh) {
        String v=expand(vertex,new HashSet<>()),f=expand(fragment,new HashSet<>());
        String varyings="\nout vec3 vpIsoSurfacePos, vpIsoSurfaceNormal;\nflat out float vpIsoSurfaceBase, vpIsoSurfaceKind;\n"
                +"flat out vec4 vpIsoPlantRoot;\nflat out vec3 vpIsoPlantBounds;\nflat out vec2 vpIsoSourceKey;\nuniform vec3 vpIsoSourceOrigin;\n";
        int main=v.indexOf("void main()");
        v=v.substring(0,main)+varyings+v.substring(main);
        if(mesh) {
            v=once(v,"vec3 pos = aPos, offset = aMesh.xyz, foot = vec3(0.0);",
                    "vpIsoSourceKey=vec2(0); vpIsoPlantRoot=vec4(0); vpIsoPlantBounds=vec3(0); vpIsoSurfaceNormal=aNormal; vec3 pos = aPos, offset = aMesh.xyz, foot = vec3(0.0);");
            v=once(v,"pos = plant.pos;", """
                    vpIsoSurfaceNormal=vec3(0.0,0.5,0.0); pos = plant.pos;
                    int vpIsoSlot=(gl_VertexID/PLANT_VERTICES)*PLANT_TEXELS;
                    int vpIsoPlantObject=int(round(texelFetch(uPlantSlots,vpIsoSlot+6).y))-1;
                    if(vpIsoPlantObject>=0) {
                      int vpIsoTile=vpIsoPlantObject>>16,vpIsoIndex=vpIsoPlantObject&65535;
                      ivec2 vpIsoSquare=ivec2(round(-aMesh.xz-vpIsoSourceOrigin.xz))+ivec2(vpIsoTile&7,vpIsoTile>>3);
                      int vpIsoLevel=int(round((plant.foot.y+aMesh.y+vpIsoSourceOrigin.y)/2.4494896));
                      if(all(greaterThanEqual(vpIsoSquare,ivec2(0))) && all(lessThan(vpIsoSquare,ivec2(65536))) && vpIsoLevel>=-32 && vpIsoLevel<32 && vpIsoIndex<1024)
                        vpIsoSourceKey=vec2(vpIsoSquare.x|((vpIsoSquare.y&255)<<16),((vpIsoSquare.y>>8)|((vpIsoLevel+32)<<8)|(vpIsoIndex<<14))+1);
                    }
                    vec4 vpIsoCard=vec4(texelFetch(uPlantSlots,vpIsoSlot+4).xy,texelFetch(uPlantSlots,vpIsoSlot+5).xy);
                    float vpIsoWind=length(plantSway(plant.foot,vpIsoCard.w,vpIsoCard.w,(plant.flags & FLAG_GRASS)!=0));
                    float vpIsoRadius=max(abs(vpIsoCard.x),abs(vpIsoCard.y))+vpIsoWind;
                    // VP also tags native bushes/flowers GRASS for sway. They
                    // are crossed-card receivers too; only facing cards opt out.
                    if((plant.flags & FLAG_FACING)==0 && vpIsoRadius>1.0e-4 && vpIsoCard.w-vpIsoCard.z>1.0e-4) {
                      vpIsoPlantRoot=vec4(reliefLifted(plant.foot+aMesh.xyz),1);
                      vpIsoPlantBounds=vec3(vpIsoRadius,vpIsoCard.z,vpIsoCard.w);
                    }
                    """);
            v=once(v,"pos = card.pos;", "vpIsoSurfaceNormal=vec3(0.0,1.0,0.0); pos = card.pos;");
            v=once(v,"pos = model.pos;", """
                    vpIsoSurfaceNormal=model.normal; pos = model.pos;
                    int vpIsoModelSlot=(aRun.y+gl_InstanceID)*PLANT_TEXELS;
                    vec3 vpIsoModelBounds=vec3(modelSlot(vpIsoModelSlot,10),modelSlot(vpIsoModelSlot,11),modelSlot(vpIsoModelSlot,12));
                    float vpIsoOwnerMeta=modelSlot(vpIsoModelSlot,13);
                    int vpIsoObject=int(round(fract(vpIsoOwnerMeta)*8388608.0))-1;
                    if(vpIsoObject>=0) {
                      int vpIsoTile=vpIsoObject>>16,vpIsoIndex=vpIsoObject&65535;
                      ivec2 vpIsoSquare=ivec2(round(-model.mesh.xz-vpIsoSourceOrigin.xz))+ivec2(vpIsoTile&7,vpIsoTile>>3);
                      int vpIsoLevel=int(round((model.foot.y+model.mesh.y+vpIsoSourceOrigin.y)/2.4494896));
                      if(all(greaterThanEqual(vpIsoSquare,ivec2(0))) && all(lessThan(vpIsoSquare,ivec2(65536))) && vpIsoLevel>=-32 && vpIsoLevel<32 && vpIsoIndex<1024)
                        vpIsoSourceKey=vec2(vpIsoSquare.x|((vpIsoSquare.y&255)<<16),((vpIsoSquare.y>>8)|((vpIsoLevel+32)<<8)|(vpIsoIndex<<14))+1);
                    }
                    if(vpIsoOwnerMeta>=1.0 && (model.flags & 402653184)!=0) {
                      vpIsoPlantRoot=vec4(reliefLifted(model.foot+model.mesh.xyz),2);
                      vpIsoPlantBounds=vpIsoModelBounds;
                    }
                    """);
            v=once(v,"gl_Position = uViewProjection * vec4(lifted, 1.0);",
                    "vpIsoSurfacePos=lifted; vpIsoSurfaceBase=(standing?foot.y:aMesh.w)+offset.y;"
                    +" vpIsoSurfaceKind=(vFlags & FLAG_ROOF)!=0?3.0:(vFlags & (FLAG_SOLID_FLOOR | FLAG_WATER | FLAG_BAKED_FLOOR))!=0?2.0:1.0;"
                    +" gl_Position = uViewProjection * vec4(lifted, 1.0);");
        } else {
            v=once(v,"gl_Position = uViewProjection * world;",
                    "vpIsoSourceKey=vec2(0); vpIsoPlantRoot=vec4(0); vpIsoPlantBounds=vec3(0); vpIsoSurfacePos=world.xyz; vpIsoSurfaceNormal=vNormal; vpIsoSurfaceBase=stand.y; vpIsoSurfaceKind=1.0; gl_Position=uViewProjection*world;");
        }
        f=f.replace("void main()","void vpIsoSurfaceOriginal()")+"\n"+OUTPUT;
        GlProgram result=GlProgram.fromSources("VPISO receiver "+fragment,new GlProgram.Sources(v,null,null,null,f),
                Map.of(),Map.of(),List.of("VPISO surface receiver"));
        ((Map<String,Integer>)GameAccess.field("viewpoint.platform.GlProgram","samplers",original)).forEach(result::sampler);
        result.sampler("vpIsoOriginalDepth",DEPTH_UNIT);result.sampler("vpIsoExposure",EXPOSURE_UNIT);
        return result;
    }
    private static String expand(String path,Set<String> seen) {
        if(!seen.add(path))throw new IllegalStateException("Cyclic surface shader include: "+path);
        String source=(String)GameAccess.call("viewpoint.platform.Shaders","read",null,new Class<?>[]{String.class},path);
        StringBuilder result=new StringBuilder();
        for(String line:source.split("\n",-1)) {
            var include=INCLUDE.matcher(line);
            if(include.matches())result.append(expand(include.group(1),seen));else result.append(line).append('\n');
        }
        if(source.startsWith("#version")) {
            int newline=result.indexOf("\n")+1;
            result.insert(newline,"#extension GL_ARB_shader_storage_buffer_object : enable\n#extension GL_ARB_shader_image_load_store : enable\n"
                    +(String)GameAccess.field("viewpoint.platform.ShaderPacks$Active","defines",ShaderPacks.active()));
        }
        seen.remove(path);
        return result.toString();
    }
    private static int texture(int internal,int format,int w,int h) {
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,GL11.GL_FLOAT,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        return id;
    }
    private static String once(String source,String expected,String replacement) {
        int at=source.indexOf(expected);
        if(at<0 || source.indexOf(expected,at+expected.length())>=0)
            throw new IllegalStateException("Unsupported native surface shader: "+expected);
        return source.substring(0,at)+replacement+source.substring(at+expected.length());
    }
}
