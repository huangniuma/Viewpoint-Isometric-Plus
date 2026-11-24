import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import viewpoint.render.ChunkMeshData;
import viewpoint.render.FarRelief;
import viewpoint.render.SceneData;
import vpiso.GameAccess;
import vpiso.NativeReceiverCull;

/** CPU-only: actual culler versus separately projected vertex/corner clip planes. */
public final class ReceiverBounds105Probe {
    static final Object UNSAFE;
    static final Method ALLOCATE;
    static long frames, selections, rejected, visible, fallbackChecks;
    static volatile double blackhole;
    static { try {
        Field singleton=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        singleton.setAccessible(true);UNSAFE=singleton.get(null);
        ALLOCATE=UNSAFE.getClass().getMethod("allocateInstance",Class.class);
    } catch(Exception e) {throw new ExceptionInInitializerError(e);} }
    record Fixture(ChunkMeshData mesh,float[][] vertices,ByteBuffer bytes,byte[] original) {}

    public static void main(String[] args)throws Exception {
        if(args.length>0 && args[0].equals("bench")){benchmark();return;}
        randomized();edges();fallbacks();scopes();
        require(rejected>10000 && visible>10000,"Insufficient visibility/rejection coverage");
        System.out.printf(Locale.ROOT,"Receiver bounds105 CPU PASS frames=%d selections=%d rejected=%d visible=%d fallbacks=%d; independent vertex/corner planes, rotations/zoom25-250, offsets/negative floors, edges/subpixels, packed tail untouched, nested scope restore. No GPU or game-FPS measurement.%n",
                frames,selections,rejected,visible,fallbackChecks);
    }
    static void randomized()throws Exception {
        Random random=new Random(105);Fixture[] fixtures=new Fixture[96];
        for(int i=0;i<fixtures.length;i++) {
            float x=(random.nextFloat()-.5f)*150,y=(random.nextFloat()-.5f)*35,z=(random.nextFloat()-.5f)*150;
            float ex=.01f+random.nextFloat()*12,ey=.01f+random.nextFloat()*14,ez=.01f+random.nextFloat()*12;
            fixtures[i]=fixture(corners(x-ex,y-ey,z-ez,x+ex,y+ey,z+ez),i%3==0?4:0);
        }
        SceneData scene=scene(fixtures);
        NativeReceiverCull cull=new NativeReceiverCull();
        for(int yaw=0;yaw<360;yaw+=15)for(int zoom:new int[]{25,50,75,100,150,200,250})
            for(int floor:new int[]{-32,-3,0,3,31})for(int dimensions=0;dimensions<3;dimensions++) {
                int width=new int[]{320,1280,3840}[dimensions],height=new int[]{180,720,2160}[dimensions];
                float scale=5f/zoom;
                Matrix4f projection=new Matrix4f().scale(scale,scale*.75f,.01f)
                        .rotateX(.6154797f).rotateY((float)Math.toRadians(yaw));
                float[] m=projection.get(new float[16]);
                m[12]=((dimensions-1)*.125f)/width;m[13]=((yaw%4)-1.5f)*.125f/height;
                projection.set(m);
                for(int i=0;i<scene.meshCount;i++) {
                    scene.meshOffsets[i*3]=(i%5-2)*.0013f;
                    scene.meshOffsets[i*3+1]=floor*2.4494896f;
                    scene.meshOffsets[i*3+2]=(i%7-3)*.0017f;
                }
                NativeReceiverCull previous=cull.enter(scene,projection,width,height);
                try {
                    for(int i=0;i<fixtures.length;i++) {
                        boolean skip=NativeReceiverCull.skip(scene,i);
                        boolean outside=outside(fixtures[i].vertices,scene.meshOffsets,i,m,1+4.0/width,1+4.0/height);
                        require(skip==outside,"Corner oracle disagreement yaw="+yaw+" zoom="+zoom+" floor="+floor+" index="+i);
                        boolean actuallyVisible=anyInside(fixtures[i].vertices,scene.meshOffsets,i,m);
                        require(!skip || !actuallyVisible,"Visible vertex rejected");
                        if(skip)rejected++;else visible++;
                        selections++;
                    }
                } finally {NativeReceiverCull.restore(previous);}
                frames++;
            }
        for(Fixture fixture:fixtures)untouched(fixture);
    }
    static void edges()throws Exception {
        NativeReceiverCull cull=new NativeReceiverCull();
        for(int width:new int[]{1,32,1280,3840})for(int height:new int[]{1,32,720,2160})
            for(int axis=0;axis<2;axis++)for(int sign:new int[]{-1,1})for(int step=-8;step<=8;step++) {
                double edge=1+4.0/(axis==0?width:height);
                float position=(float)(sign*(edge+step*.125/(axis==0?width:height)));
                float[][] points=axis==0?new float[][]{{position,-.1f,0},{position,.1f,0},{position,0,.1f}}
                        :new float[][]{{-.1f,position,0},{.1f,position,0},{0,position,.1f}};
                Fixture f=fixture(points,2);SceneData scene=scene(f);
                Matrix4f p=new Matrix4f();float[] m=p.get(new float[16]);
                NativeReceiverCull previous=cull.enter(scene,p,width,height);
                try {
                    boolean skip=NativeReceiverCull.skip(scene,0);
                    require(skip==outside(points,scene.meshOffsets,0,m,1+4.0/width,1+4.0/height),"Exact edge differs");
                    require(!skip||!anyInside(points,scene.meshOffsets,0,m),"Edge-visible triangle rejected");
                    if(skip)rejected++;else visible++;selections++;frames++;
                } finally {NativeReceiverCull.restore(previous);}
                untouched(f);
            }
    }
    static void fallbacks()throws Exception {
        float[][] off=corners(10,10,10,11,11,11);
        Fixture normal=fixture(off,2);SceneData scene=scene(normal);NativeReceiverCull cull=new NativeReceiverCull();
        require(selection(cull,scene,new Matrix4f(),100,100),"Known offscreen negative control absent");
        Fixture missing=fixture(off,0);GameAccess.set("viewpoint.render.ChunkMeshData","verts",missing.mesh,null);
        // A fresh object with no cook registration must remain drawable.
        missing=new Fixture(rawMesh(new int[]{0},new int[]{8},null),off,null,null);
        retained(cull,scene(missing),new Matrix4f(),100,100);
        ByteBuffer valid=packed(off,0);
        for(int[][] ranges:new int[][][]{ {{0},{-1}}, {{-1},{8}}, {{Integer.MAX_VALUE},{1}}, {{0,0},{8}}, {{0},{0}}, {{0},{9000}} }) {
            ChunkMeshData mesh=rawMesh(ranges[0],ranges[1],valid);
            NativeReceiverCull.cooked(mesh);retained(cull,scene(new Fixture(mesh,off,null,null)),new Matrix4f(),100,100);
        }
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            float[][] points=corners(10,10,10,11,11,11);points[0][1]=bad;
            retained(cull,scene(fixture(points,0)),new Matrix4f(),100,100);
            Matrix4f badMatrix=new Matrix4f();float[] m=badMatrix.get(new float[16]);m[4]=bad;badMatrix.set(m);
            retained(cull,scene,badMatrix,100,100);
            scene.meshOffsets[0]=bad;retained(cull,scene,new Matrix4f(),100,100);scene.meshOffsets[0]=0;
        }
        scene.relief=(FarRelief.Lift)allocate(FarRelief.Lift.class);
        retained(cull,scene,new Matrix4f(),100,100);scene.relief=null;
        Matrix4f perspective=new Matrix4f();float[] m=perspective.get(new float[16]);m[3]=.001f;perspective.set(m);
        retained(cull,scene,perspective,100,100);
        retained(cull,scene,new Matrix4f(),0,100);retained(cull,scene,new Matrix4f(),100,-1);
        // Extra80-byte records contain NaNs and onscreen roots deliberately. They
        // must not contaminate the separate ordinary-prefix bound or be modified.
        require(selection(cull,scene(normal),new Matrix4f(),100,100),"Mixed packed tail corrupted ordinary bounds");
        untouched(normal);fallbackChecks++;
    }
    static void scopes()throws Exception {
        Fixture f=fixture(corners(10,10,10,11,11,11),0);
        SceneData a=scene(f),b=scene(f);NativeReceiverCull outer=new NativeReceiverCull(),inner=new NativeReceiverCull();
        NativeReceiverCull first=outer.enter(a,new Matrix4f(),100,100);
        require(first==null && NativeReceiverCull.skip(a,0),"Outer scope not installed");
        require(!NativeReceiverCull.skip(b,0)&&!NativeReceiverCull.skip(a,-1)&&!NativeReceiverCull.skip(a,1),"Foreign scene/index suppressed");
        NativeReceiverCull previous=inner.enter(b,new Matrix4f().translate(-10,-10,-10),100,100);
        require(previous==outer&&!NativeReceiverCull.skip(a,0)&&!NativeReceiverCull.skip(b,0),"Inner scope not isolated");
        NativeReceiverCull.restore(previous);require(NativeReceiverCull.skip(a,0),"Outer scope not restored");
        NativeReceiverCull.restore(first);require(!NativeReceiverCull.skip(a,0),"Scope leaked");
        fallbackChecks+=5;
    }
    static void benchmark()throws Exception {
        Field boundsField=NativeReceiverCull.class.getDeclaredField("BOUNDS");boundsField.setAccessible(true);
        Map<?,?> bounds=(Map<?,?>)boundsField.get(null);
        for(int count:new int[]{1000,10000,100000}) {
            ByteBuffer bytes=ByteBuffer.allocate(count*40).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<count;i++) {
                bytes.putFloat(i*40,(i%257)*.03125f);bytes.putFloat(i*40+4,(i%67)*.0625f);
                bytes.putFloat(i*40+8,(i%251)*.03125f);
            }
            ChunkMeshData mesh=rawMesh(new int[]{0},new int[]{count},bytes);
            int iterations=Math.max(20,1000000/count);
            for(int i=0;i<iterations;i++){bounds.clear();cookControl(mesh);bounds.clear();NativeReceiverCull.cooked(mesh);}
            long[] controls=new long[7],scans=new long[7];
            for(int round=0;round<7;round++) {
                long begin=System.nanoTime();for(int i=0;i<iterations;i++){bounds.clear();cookControl(mesh);}controls[round]=System.nanoTime()-begin;
                begin=System.nanoTime();for(int i=0;i<iterations;i++){bounds.clear();NativeReceiverCull.cooked(mesh);}scans[round]=System.nanoTime()-begin;
            }
            Arrays.sort(controls);Arrays.sort(scans);
            System.out.printf(Locale.ROOT,"COOK105 vertices=%d iterations_per_round=%d control_median_us=%.3f scan_median_us=%.3f scan_min_us=%.3f scan_max_us=%.3f%n",
                    count,iterations,controls[3]/(1000.0*iterations),scans[3]/(1000.0*iterations),scans[0]/(1000.0*iterations),scans[6]/(1000.0*iterations));
        }
        ByteBuffer bytes=packed(corners(-4,0,-4,4,6,4),4);
        for(int meshCount:new int[]{1000,4000}) {
            SceneData scene=(SceneData)allocate(SceneData.class);scene.meshCount=meshCount;
            scene.meshes=new ChunkMeshData[meshCount];scene.meshOffsets=new float[meshCount*3];
            for(int i=0;i<meshCount;i++) {
                scene.meshes[i]=rawMesh(new int[]{0},new int[]{8},bytes);NativeReceiverCull.cooked(scene.meshes[i]);
                scene.meshOffsets[i*3]=(i%100-50)*8;scene.meshOffsets[i*3+2]=(i/100-20)*8;
                scene.meshOffsets[i*3+1]=(i%9-4)*2.4494896f;
            }
            Matrix4f projection=new Matrix4f().scale(.02f,.015f,.001f).rotateX(.6154797f).rotateY(.7853982f);
            NativeReceiverCull cull=new NativeReceiverCull();int iterations=500;
            for(int i=0;i<iterations;i++)selectControl(scene);
            for(int i=0;i<iterations;i++)selectionRun(cull,scene,projection);
            long[] controls=new long[7],selection=new long[7];
            for(int round=0;round<7;round++) {
                long begin=System.nanoTime();for(int i=0;i<iterations;i++)selectControl(scene);controls[round]=System.nanoTime()-begin;
                begin=System.nanoTime();for(int i=0;i<iterations;i++)selectionRun(cull,scene,projection);selection[round]=System.nanoTime()-begin;
            }
            Arrays.sort(controls);Arrays.sort(selection);
            System.out.printf(Locale.ROOT,"SELECT105 meshes=%d frames_per_round=%d control_median_us=%.3f selection_median_us=%.3f selection_min_us=%.3f selection_max_us=%.3f%n",
                    meshCount,iterations,controls[3]/(1000.0*iterations),selection[3]/(1000.0*iterations),selection[0]/(1000.0*iterations),selection[6]/(1000.0*iterations));
        }
        System.out.println("CPU-only warmed microbench; controls inspect buffer/scene without scanning/projecting. Cook map is cleared in both loops so each scan inserts a fresh weak-map key entry while reusing immutable geometry bytes. Scan is once per newly cooked mesh and selection is per receiver draw; costs exclude real recipe generation, upload, GPU, map traversal and game FPS.");
    }
    static void cookControl(ChunkMeshData mesh) {
        ByteBuffer data=(ByteBuffer)GameAccess.field("viewpoint.render.ChunkMeshData","verts",mesh);
        blackhole=data.getFloat(data.position())+mesh.first.length+mesh.count.length;
    }
    static void selectControl(SceneData scene){blackhole=scene.meshCount+scene.meshOffsets.length+scene.meshes.length;}
    static void selectionRun(NativeReceiverCull cull,SceneData scene,Matrix4f projection) {
        NativeReceiverCull previous=cull.enter(scene,projection,3840,2160);
        try {blackhole=NativeReceiverCull.skip(scene,0)?1:0;}finally{NativeReceiverCull.restore(previous);}
    }
    static Fixture fixture(float[][] points,int extras)throws Exception {
        ByteBuffer bytes=packed(points,extras);byte[] original=new byte[bytes.capacity()];
        for(int i=0;i<original.length;i++)original[i]=bytes.get(i);
        ChunkMeshData mesh=rawMesh(new int[]{0,points.length/2},new int[]{points.length/2,points.length-points.length/2},bytes);
        NativeReceiverCull.cooked(mesh);Fixture f=new Fixture(mesh,points,bytes,original);untouched(f);return f;
    }
    static ByteBuffer packed(float[][] points,int extras) {
        int padding=16,ordinary=(points.length+(points.length&1))*40;
        ByteBuffer bytes=ByteBuffer.allocate(padding+ordinary+extras*80).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)(i*37+11));
        for(int i=0;i<points.length;i++)for(int axis=0;axis<3;axis++)bytes.putFloat(padding+i*40+axis*4,points[i][axis]);
        for(int i=0;i<extras;i++)for(int axis=0;axis<3;axis++)bytes.putFloat(padding+ordinary+i*80+axis*4,Float.NaN);
        bytes.position(padding);return bytes;
    }
    static ChunkMeshData rawMesh(int[] first,int[] count,ByteBuffer bytes)throws Exception {
        ChunkMeshData mesh=(ChunkMeshData)allocate(ChunkMeshData.class);
        GameAccess.set("viewpoint.render.ChunkMeshData","first",mesh,first);
        GameAccess.set("viewpoint.render.ChunkMeshData","count",mesh,count);
        GameAccess.set("viewpoint.render.ChunkMeshData","verts",mesh,bytes);return mesh;
    }
    static SceneData scene(Fixture... fixtures)throws Exception {
        SceneData scene=(SceneData)allocate(SceneData.class);scene.meshes=new ChunkMeshData[fixtures.length];
        scene.meshOffsets=new float[fixtures.length*3];scene.meshCount=fixtures.length;
        for(int i=0;i<fixtures.length;i++)scene.meshes[i]=fixtures[i].mesh;return scene;
    }
    static float[][] corners(float lx,float ly,float lz,float hx,float hy,float hz) {
        float[][] result=new float[8][3];for(int i=0;i<8;i++)result[i]=new float[]{(i&1)==0?lx:hx,(i&2)==0?ly:hy,(i&4)==0?lz:hz};return result;
    }
    // Independent oracle: project each corner/vertex, classify four clip planes.
    // No center/extent or absolute coefficient radius from the implementation.
    static boolean outside(float[][] points,float[] offsets,int index,float[] m,double mx,double my) {
        boolean left=true,right=true,bottom=true,top=true;
        for(float[] point:points) {
            double x=(double)point[0]+offsets[index*3],y=(double)point[1]+offsets[index*3+1],z=(double)point[2]+offsets[index*3+2];
            double sx=m[0]*x+m[4]*y+m[8]*z+m[12],sy=m[1]*x+m[5]*y+m[9]*z+m[13];
            left&=sx< -mx;right&=sx>mx;bottom&=sy< -my;top&=sy>my;
        }
        return left||right||bottom||top;
    }
    static boolean anyInside(float[][] points,float[] offsets,int index,float[] m) {
        for(float[] point:points) {
            // Float32 mirrors the independently submitted vertex path, including
            // source/offset addition before matrix multiplication.
            float x=point[0]+offsets[index*3],y=point[1]+offsets[index*3+1],z=point[2]+offsets[index*3+2];
            float sx=m[0]*x+m[4]*y+m[8]*z+m[12],sy=m[1]*x+m[5]*y+m[9]*z+m[13];
            if(sx>=-1&&sx<=1&&sy>=-1&&sy<=1)return true;
        }
        return false;
    }
    static boolean selection(NativeReceiverCull cull,SceneData scene,Matrix4f p,int w,int h) {
        NativeReceiverCull previous=cull.enter(scene,p,w,h);
        try{return NativeReceiverCull.skip(scene,0);}finally{NativeReceiverCull.restore(previous);}
    }
    static void retained(NativeReceiverCull cull,SceneData scene,Matrix4f p,int w,int h) {
        require(!selection(cull,scene,p,w,h),"Unknown/malformed geometry rejected");fallbackChecks++;
    }
    static void untouched(Fixture f) {
        if(f.bytes==null)return;
        require(f.bytes.position()==16 && f.bytes.limit()==f.bytes.capacity() && f.bytes.order()==ByteOrder.LITTLE_ENDIAN,"Packed buffer state mutated");
        for(int i=0;i<f.original.length;i++)require(f.bytes.get(i)==f.original[i],"Packed vertex/tail bytes mutated");
    }
    static Object allocate(Class<?> type)throws Exception{return ALLOCATE.invoke(UNSAFE,type);}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
