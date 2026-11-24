package vpiso;

/** Reconstruct only the source named by the visible native draw, never a screen neighbour. */
public final class NativeSourceSurface {
    public static final String GLSL="""
        bool vpIsoSourceReceiver(int id,vec3 pixel,vec3 ray,out vec3 at,out vec3 n,out vec3 solar,out float base,out vec2 key) {
          if(id<2 || id>=65536)return false;
          vec4 source=vpIsoSourceRecord(id,0);
          key=vec2(vpIsoSourceRecord(id,1).w,vpIsoSourceRecord(id,2).w);
          if(source.w!=3.0 && source.w!=4.0 && source.w!=5.0)return false;
          base=source.z*2.4494896-uSceneOrigin.y;
          vec3 root=vec3(-source.x-uSceneOrigin.x,base,-source.y-uSceneOrigin.z);
          vec3 lo=root+vpIsoSourceRecord(id,1).xyz;
          vec3 hi=root+vpIsoSourceRecord(id,2).xyz;
          if(any(lessThanEqual(hi-lo,vec3(.0001))))return false;
          // The source volume is independent of the VP proxy's screen coverage.
          // A wider original illustration still belongs to this source: project
          // its ray to the closest own skin instead of falling back to the floor.
          vec3 centre=(lo+hi)*.5,extent=(hi-lo)*.5;
          vec3 safeRay=mix(vec3(1.0e-8),ray,greaterThan(abs(ray),vec3(1.0e-8)));
          vec3 a=(lo-pixel)/safeRay,b=(hi-pixel)/safeRay;
          vec3 nearFace=min(a,b),farFace=max(a,b);
          float enter=max(nearFace.x,max(nearFace.y,nearFace.z));
          float leave=min(farFace.x,min(farFace.y,farFace.z));
          if(enter<=leave)at=clamp(pixel+ray*enter,lo,hi);
          else at=clamp(pixel+ray*dot(centre-pixel,ray),lo,hi);
          vec3 face=abs((at-centre)/extent);
          int axis=face.x>=face.y && face.x>=face.z?0:face.y>=face.z?1:2;
          n=vec3(0);n[axis]=at[axis]>=centre[axis]?1.0:-1.0;
          solar=at;
          if(source.w==4.0) {
            // Native foliage cards represent a continuous crown. Keep its own
            // sunward skin distinct from physical lamp/grid coordinates.
            float radius=length(extent.xz);
            vec3 from=at-centre;
            vec3 sun=normalize(uSunDir);
            float sa=dot(sun.xz,sun.xz),sb=dot(from.xz,sun.xz);
            float sc=dot(from.xz,from.xz)-radius*radius;
            float side=sa>1.0e-10?(-sb+sqrt(max(0.0,sb*sb-sa*sc)))/sa:1.0e20;
            float cap=abs(sun.y)>1.0e-6?((sun.y>0.0?hi.y:lo.y)-at.y)/sun.y:1.0e20;
            solar=at+sun*max(min(side,cap),0.0);
            n=vec3(0,1,0);
          }
          return !any(isnan(at)) && !any(isinf(at)) && !any(isnan(solar)) && !any(isinf(solar));
        }
        """;
    private NativeSourceSurface() {}
}
