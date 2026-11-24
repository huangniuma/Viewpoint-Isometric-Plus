package vpiso;

/** Integer owner coordinates and their squared norm survive native alpha blending. */
public final class NativeOwnerMetadata {
    public static final String GLSL="""
        bool vpIsoDecodeFloor(vec4 moments,out float level) {
          level=0.0;
          if(!(moments.b>1.0e-6))return false;
          float z=moments.r/moments.b;
          level=round(z);
          return abs(z-level)<.002 && abs(moments.g/moments.b-z*z)<.002;
        }
        bool vpIsoOwnerPoint(vec4 raw,out ivec2 code) {
          if(raw.a<.003)return false;
          vec2 mean=raw.rg/raw.a,point=round(mean);
          code=ivec2(point);
          float variance=abs(raw.b/raw.a-dot(mean,mean));
          return all(lessThan(abs(mean-point),vec2(.002))) && variance<.25;
        }
        int vpIsoSourceId(ivec2 code) {
          if(code.x> -2 || code.x< -257 || code.y<0 || code.y>255)return 0;
          return -code.x-2+256*code.y;
        }
        vec4 vpIsoSourceRecord(int id,int plane) {
          return texelFetch(vpIsoNativeWalls,ivec2(id&1023,1+(id>>10)+plane*64),0);
        }
        bool vpIsoFloorMixture(vec4 raw,float z,out ivec2 code) {
          // Recover this pixel's two contributions from its own moments. A wide
          // fading window or filtered edge need not contain a pure neighbour.
          vec2 offset=raw.rg-raw.a*vec2(0,z);
          float square=raw.b-2.0*z*raw.g+raw.a*z*z;
          if(square<=0.0)return false;
          float front=dot(offset,offset)/square,back=raw.a-front;
          if(front<.003 || back<.003)return false;
          vec2 point=vec2(0,z)+offset/front;
          code=ivec2(round(point));
          if(any(greaterThan(abs(point-vec2(code)),vec2(.002))))return false;
          int id=vpIsoSourceId(code);
          vec4 source=id>=2?vpIsoSourceRecord(id,0):
              code.x== -1 && code.y>=2 && code.y<1024?
              texelFetch(vpIsoNativeWalls,ivec2(code.y,0),0):vec4(0);
          bool valid=id>=2?(source.w==3.0 || source.w==4.0 || source.w==5.0):(source.w==1.0 || source.w==2.0);
          if(!valid || abs(source.z-z)>.002)return false;
          vec2 first=front*vec2(code)+back*vec2(0,z);
          float second=front*dot(vec2(code),vec2(code))+back*z*z;
          return all(lessThanEqual(abs(raw.rg-first),vec2(.002)))
              && abs(raw.b-second)<=max(.003,abs(second)*6.0e-7);
        }
        bool vpIsoDecodeOwner(vec4 raw,out ivec2 code,out float level,out bool floorValid) {
          floorValid=false;level=0.0;code=ivec2(1,0);
          if(vpIsoOwnerPoint(raw,code)) {
            if(code.x==0) {level=float(code.y);floorValid=true;}
            return true;
          }
          if(raw.r>=0.0) {
            float coverage=raw.a-raw.r;
            if(coverage>.003) {
              float z=raw.g/coverage;
              if(abs(z-round(z))<.002 && abs((raw.b-raw.r)/coverage-z*z)<.002) {
                level=round(z);floorValid=true;
              }
            }
            code=ivec2(1,0);return false;
          }
          float nativeLevel=floor(nativeWorldZ);
          if(vpIsoFloorMixture(raw,nativeLevel,code)) {
            level=nativeLevel;floorValid=true;return true;
          }
          // Only recover a foreground/floor mixture when neighbouring metadata
          // proves one owner and one floor. No neighbouring colour or position
          // is used, and ambiguous overlapping artwork remains on physical data.
          ivec2 size=textureSize(nativeMetadata,0),at=ivec2(uv*vec2(size));
          ivec2 candidate=ivec2(0);int z=0;bool found=false,foundFloor=false;
          for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++) {
            ivec2 sampleAt=at+ivec2(x,y);
            if(any(lessThan(sampleAt,ivec2(0))) || any(greaterThanEqual(sampleAt,size)))continue;
            ivec2 other;
            if(!vpIsoOwnerPoint(texelFetch(nativeMetadata,sampleAt,0),other))continue;
            if(other.x<0 && !(other.x== -1 && other.y==0)) {
              if(found && any(notEqual(candidate,other)))return false;
              candidate=other;found=true;
            } else if(other.x==0) {
              if(foundFloor && z!=other.y)return false;
              z=other.y;foundFloor=true;
            } else if(other.x!=0)return false;
          }
          if(!found || !foundFloor)return false;
          float front=raw.r/float(candidate.x),back=raw.a-front;
          if(front<.003 || back<.003)return false;
          float first=front*float(candidate.y)+back*float(z);
          float second=front*dot(vec2(candidate),vec2(candidate))+back*float(z*z);
          if(abs(raw.g-first)>.002 || abs(raw.b-second)>max(.003,abs(second)*6.0e-7))return false;
          int id=vpIsoSourceId(candidate);
          float sourceLevel=id>=2?vpIsoSourceRecord(id,0).z:
              candidate.x== -1 && candidate.y>=2 && candidate.y<1024?
              texelFetch(vpIsoNativeWalls,ivec2(candidate.y,0),0).z:1.0e10;
          if(abs(sourceLevel-float(z))>.002)return false;
          code=candidate;level=float(z);floorValid=true;return true;
        }
        """;
    private NativeOwnerMetadata() {}
}
