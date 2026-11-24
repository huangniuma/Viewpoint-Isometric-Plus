package vpiso;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.SceneData;

/** Viewport rejection of ordinary receiver triangles only; never shadow casters. */
public final class NativeReceiverCull {
    private record Bounds(float lx,float ly,float lz,float hx,float hy,float hz) {}
    private static final Map<ChunkMeshData,Bounds> BOUNDS=Collections.synchronizedMap(new WeakHashMap<>());
    // Meshes.collect and NativeSurfaces.draw run on the same render thread.
    private static NativeReceiverCull active;
    private SceneData scene;
    private boolean[] excluded=new boolean[0];
    private final float[] matrix=new float[16];

    /** Cook worker, once per mesh, before VP uploads and frees its packed vertices. */
    public static void cooked(ChunkMeshData mesh) {
        if(mesh==null)return;
        ByteBuffer data=(ByteBuffer)GameAccess.field("viewpoint.render.ChunkMeshData","verts",mesh);
        if(data==null || mesh.first.length!=mesh.count.length)return;
        int corners=0;
        for(int page=0;page<mesh.first.length;page++) {
            int first=mesh.first[page],count=mesh.count[page];
            if(first<0 || count<0 || first>Integer.MAX_VALUE-count)return;
            corners=Math.max(corners,first+count);
        }
        // Only the ordinary40-byte vertex prefix contains xyz. Plant/model
        // slots after it have another layout and remain entirely unfiltered.
        if(corners<=0 || corners>(data.limit()-data.position())/40)return;
        float lx=Float.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Float.NEGATIVE_INFINITY,hy=hx,hz=hx;
        for(int i=0;i<corners;i++) {
            int at=data.position()+i*40;
            float x=data.getFloat(at),y=data.getFloat(at+4),z=data.getFloat(at+8);
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))return;
            lx=Math.min(lx,x);ly=Math.min(ly,y);lz=Math.min(lz,z);
            hx=Math.max(hx,x);hy=Math.max(hy,y);hz=Math.max(hz,z);
        }
        BOUNDS.put(mesh,new Bounds(lx,ly,lz,hx,hy,hz));
    }

    public NativeReceiverCull enter(SceneData value,Matrix4f projection,int width,int height) {
        NativeReceiverCull previous=active;
        scene=value;
        if(excluded.length<value.meshCount)excluded=new boolean[value.meshCount];
        java.util.Arrays.fill(excluded,0,value.meshCount,false);
        // Native receiver projection is affine. Unknown terrain displacement
        // and non-affine projections keep all geometry rather than guess bounds.
        projection.get(matrix);
        boolean affine=matrix[3]==0 && matrix[7]==0 && matrix[11]==0 && matrix[15]==1;
        for(float f:matrix)if(!Float.isFinite(f))affine=false;
        if(value.relief==null && affine && width>0 && height>0) {
            double mx=1.0+4.0/width,my=1.0+4.0/height;
            synchronized(BOUNDS) {
                for(int i=0;i<value.meshCount;i++) {
                    Bounds b=BOUNDS.get(value.meshes[i]);
                    if(b==null)continue;
                    double x=((double)b.lx+b.hx)*.5+value.meshOffsets[i*3];
                    double y=((double)b.ly+b.hy)*.5+value.meshOffsets[i*3+1];
                    double z=((double)b.lz+b.hz)*.5+value.meshOffsets[i*3+2];
                    if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))continue;
                    double ex=((double)b.hx-b.lx)*.5,ey=((double)b.hy-b.ly)*.5,ez=((double)b.hz-b.lz)*.5;
                    double sx=matrix[0]*x+matrix[4]*y+matrix[8]*z+matrix[12];
                    double sy=matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13];
                    double rx=Math.abs(matrix[0])*ex+Math.abs(matrix[4])*ey+Math.abs(matrix[8])*ez;
                    double ry=Math.abs(matrix[1])*ex+Math.abs(matrix[5])*ey+Math.abs(matrix[9])*ez;
                    excluded[i]=sx-rx>mx || sx+rx< -mx || sy-ry>my || sy+ry< -my;
                }
            }
        }
        active=this;
        return previous;
    }
    public static void restore(NativeReceiverCull previous) { active=previous; }
    public static boolean skip(SceneData scene,int index) {
        NativeReceiverCull current=active;
        return current!=null && current.scene==scene && index>=0 && index<scene.meshCount && current.excluded[index];
    }
}
