import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.*;
import viewpoint.core.Frame;

/** Native source pixels deliberately extend beyond, and disagree with, VP proxy coverage. */
public final class NativeSource073Probe {
    static final NativeShadow068Probe P=NativePlants072Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    static int registry,cases,external;
    static boolean baseline;
    public static void main(String[] args)throws Exception {
        P.pack=args[0];baseline=Arrays.asList(args).contains("baseline072");
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"073 native sources",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();N.fit(0);P.nativeProgram=NativeGroundProbe.link(N.VERT,NativeReceivers071Probe.FLAT);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            registry=NativeWallSources071Probe.fbo();NativeGround.begin(true);
            if(!baseline)lifecycle();
            for(int kind:new int[]{3,4})for(float size:new float[]{.18f,.65f})run(kind,size);
            if(!baseline){packedOwner();coveredColumns();}
            if(!baseline)N.require(external>100,"Empty mutual-shadow controls: "+external);
            N.require(GL11.glGetError()==0,"GL error");
            System.out.println((baseline?"Pinned072 missing-source negative PASS":"Native sources073 GPU PASS")+" pack="+P.pack+" cases="+cases+" external="+external
                +"; wide native art, tree/bush/bin/column envelopes, no matching proxy, wrong proxy independence, own vs external shared maps, camera motion, native RGBA/depth/stencil and persistent source lifecycle. Controlled geometry, live acceptance pending.");
        }finally{NativeGround.finish();NativeGround.release(registry);IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static int register(int kind,float half) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
        if(baseline) {
            // Same source record as073, injected into072's wall-only table.
            float[] data=(float[])GameAccess.field("vpiso.NativeGround","WALL_DATA",null);
            data[8]=3;data[9]=3;data[10]=0;data[11]=kind;return 2;
        }
        NativeGround.sourceBegin(new NativeSource(3,3,0,kind,new float[]{-half,0,-half,half,1.8f,half}));
        int id=((Number)GameAccess.field("vpiso.NativeGround","sourceOwner",null)).intValue();NativeGround.sourceEnd();return id;
    }
    static void run(int kind,float half)throws Exception {
        int id=register(kind,half);float[] owner={-2,id,-id*id,1};
        Frame f=P.frame();float[][] art=N.vertical(1.2f,2.6f);
        Object source=N.mesh(f.scene,0,box(half,1.8f));P.publish(source,true,0);
        // This opaque unrelated receiver is closer on screen, and exceeds the old
        // proximity limit. It casts nothing: changing it must not change reception.
        Object neighbour=N.mesh(f.scene,1,N.horizontal(.7f,2.5f));P.publish(neighbour,true,0);f.scene.meshFlags[1]=0;
        float[][] shade=N.horizontal(2.8f,.9f);for(float[] v:shade){v[0]-=1.2f;v[2]-=.45f;}
        Object roof=N.mesh(f.scene,2,shade);P.publish(roof,true,0);
        for(double shift:new double[]{0,.007,-.011}) {
            N.fit(shift);NativePlants072Probe.invalidate();P.draw(f,art,0,owner,0,true);byte[] a=P.actual.clone();
            int dark=NativeCanopy070Probe.dark(P.before,a);
            if(baseline)N.require(dark==0,"072 unexpectedly decodes native object source");
            else {
                N.require(dark>10,"Source missing external reception kind="+kind+" half="+half+" dark="+dark);external+=dark;
                Object[] meshes=(Object[])GameAccess.field("viewpoint.render.SceneData","meshes",f.scene);
                Object previous=meshes[1];meshes[1]=source;
                NativePlants072Probe.invalidate();P.draw(f,art,0,owner,0,true);
                N.require(P.different(a,P.actual,1)==0,"A foreign receiver changed the native source image");meshes[1]=previous;
            }
            cases++;
        }
        if(!baseline) {
            // Regenerate maps with only an outside source. Foliage's own card or
            // model parts must not create stripes, but the external roof remains.
            if(kind==4) {
                NativePlants072Probe.invalidate();P.draw(f,art,0,owner,0,true);byte[] a=P.actual.clone();
                f.scene.meshFlags[0]=0;NativePlants072Probe.invalidate();P.draw(f,art,0,owner,0,true);
                N.require(P.different(a,P.actual,3)==0,"Crown own caster introduced false shadow bands");f.scene.meshFlags[0]=1;cases++;
            }
            f.scene.meshFlags[2]=0;f.scene.meshFlags[0]=0;NativePlants072Probe.invalidate();P.draw(f,art,0,owner,0,true);
            P.identity("removed external casters");cases++;
            // Mixed IDs and unknown/cleared IDs must not borrow either object.
            for(float[] mixed:new float[][]{{-2,1,-1,1},{-1.5f,id,-id*id,1},{-2,id,-id*id-1,1}}) {
                P.draw(f,art,0,mixed,0,true);P.identity("mixed or unknown native source");cases++;
            }
        }
        N.retire(roof);N.retire(neighbour);N.retire(source);N.fit(0);
    }
    static float[][] box(float h,float top) {
        ArrayList<float[]> out=new ArrayList<>();
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++) {
            int u=(axis+1)%3,v=(axis+2)%3;int[] corners={0,1,3,0,3,2};
            for(int corner:corners){float[] p=new float[3];p[axis]=axis==1?(side==0?0:top):(side==0?-h:h);
                p[u]=u==1?((corner&1)==0?0:top):((corner&1)==0?-h:h);
                p[v]=v==1?((corner&2)==0?0:top):((corner&2)==0?-h:h);out.add(p);}
        }return out.toArray(float[][]::new);
    }
    static void lifecycle()throws Exception {
        float[] b={-.3f,0,-.3f,.3f,1.8f,.3f};NativeSource s=new NativeSource(3,3,0,3,b);b[4]=99;
        NativeGround.sourceBegin(s);int id=sourceOwner();N.require(id>=2,"No source allocation");
        int owners=NativeMetadata074.sourceOwners();FloatBuffer data=BufferUtils.createFloatBuffer(NativeGround.WALL_OWNERS*4+owners*12);NativeGround.ownerData(data);
        N.require(data.get((NativeGround.WALL_OWNERS+owners*2+id)*4+1)==1.8f,"Queued source retained mutable bounds");
        NativeGround.sourceBegin(new NativeSource(4,3,0,4,new float[]{-.6f,0,-.6f,.6f,3,.6f}));
        N.require(sourceOwner()!=id,"Neighbour borrowed source");NativeGround.sourceEnd();N.require(sourceOwner()==id,"Nested source restore");
        NativeGround.sourceEnd();N.require(sourceOwner()==0,"Source leaked after scope");
        NativeGround.finish();NativeGround.begin(true);NativeGround.finish();NativeGround.begin(true);data.clear();NativeGround.ownerData(data);
        N.require(data.get((NativeGround.WALL_OWNERS+id)*4+3)==0 && data.get((NativeGround.WALL_OWNERS+owners*2+id)*4+1)==0,"Cached source data not cleared");
        cases++;
    }
    static int sourceOwner(){return ((Number)GameAccess.field("vpiso.NativeGround","sourceOwner",null)).intValue();}
    static void coveredColumns()throws Exception {
        // A vertical side under a roomless roof must retain projected shadows;
        // the horizontal canopy fixture alone cannot catch this regression.
        NativeRelightMask regions=new NativeRelightMask();regions.x=regions.y=-32;regions.z=0;
        byte[] exterior=(byte[])NativeRelightMask.class.getField("exterior").get(regions);
        Arrays.fill(exterior,(byte)255);N.overlay.relight(0,0,regions);
        float half=.45f,top=2.0f;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
        NativeGround.sourceBegin(new NativeSource(3,3,0,3,new float[]{-half,0,-half,half,top,half}));
        int id=sourceOwner();NativeGround.sourceEnd();float[] owner={-2,id,-id*id,1};
        try {
            for(int axis:new int[]{0,2}) {
                Frame f=P.frame();f.scene.sunX=f.scene.sunZ=-.5f;f.scene.sunY=.70710677f;
                float[][] side=new float[6][3];int[] corners={0,1,3,0,3,2};
                for(int i=0;i<6;i++) {
                    side[i][axis]=-half;side[i][1]=(corners[i]&1)==0?0:top;
                    side[i][2-axis]=(corners[i]&2)==0?-half:half;
                }
                Object page=N.mesh(f.scene,0,N.horizontal(-5,.05f));P.publish(page,false,60);
                Arrays.fill(f.scene.exposure,(byte)0);
                float[][] roof=N.horizontal(2.4f,.9f);for(float[] v:roof){v[0]-=1;v[2]-=1;}
                Object canopy=N.mesh(f.scene,1,roof);P.publish(canopy,false,60);
                NativePlants072Probe.invalidate();P.draw(f,side,0,owner,0,true);
                int dark=NativeCanopy070Probe.dark(P.before,P.actual),lit=0;
                for(int i=0;i<N.W*N.H;i++)if((P.before[i*4]&255)>160 && Math.abs((P.before[i*4]&255)-(P.actual[i*4]&255))<=3)lit++;
                N.require(dark>10 && lit>10,"Covered column lost split lit/shadow regions axis="+axis+" dark="+dark+" lit="+lit);
                external+=dark;cases++;
                Arrays.fill(exterior,(byte)0);P.draw(f,side,0,owner,0,true);P.identity("enclosed column keeps native light");
                Arrays.fill(exterior,(byte)255);regions.z=1;P.draw(f,side,0,owner,0,true);P.identity("other-floor column room gate");regions.z=0;
                f.scene.meshFlags[1]=0;NativePlants072Probe.invalidate();P.draw(f,side,0,owner,0,true);P.identity("covered column external roof removed");
                N.retire(canopy);N.retire(page);cases+=3;
            }
            System.out.println("Covered vertical columns073 PASS: both side axes retain lit and projected-shadow regions under zero open-sky alpha; true rooms, owner floors and blocker removal preserve native light.");
        }finally{N.overlay.relight(0,0,null);}
    }
    static void packedOwner()throws Exception {
        NativePackedPlants072Probe.baseline=false;
        for(int i=0;i<NativePackedPlants072Probe.FILES.length;i++) {
            var file=NativePackedPlants072Probe.ROOT.resolve(NativePackedPlants072Probe.FILES[i]).toFile();
            viewpoint.render.PackModel model=viewpoint.render.PackModels.of(file);
            Object data=GameAccess.call("viewpoint.packs.PackLoads","load",null,new Class<?>[]{java.io.File.class,java.nio.file.Path.class},file,NativePackedPlants072Probe.ROOT);
            viewpoint.render.PackModels.onItsWay();model.read((viewpoint.render.PackData)data);NativePackedPlants072Probe.MODELS[i]=model;
        }
        viewpoint.render.PackModels.publish();
        Frame f=P.frame();Object mesh=NativePackedPlants072Probe.mesh(f,false,true,0,0,0);
        Object roof=N.mesh(f.scene,1,N.horizontal(2.6f,.7f));P.publish(roof,true,0);
        float[][] art=N.vertical(1.2f,2.3f);P.draw(f,art,0,new float[]{1,0,0,1},0,true);byte[] original=P.actual.clone();
        NativeSurfaces surfaces=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
        float[] solar=NativeReceivers071Probe.texture(surfaces.solar());
        float[] key=NativeSource.ownerKey(7,3,0,0);int found=0;
        for(int i=0;i<N.W*N.H;i++)if(solar[i*4+3]>0) {
            N.require(solar[i*4+2]==key[0] && solar[i*4+3]==key[1],"Cook/GPU exact owner key mismatch: "+Arrays.toString(Arrays.copyOfRange(solar,i*4,i*4+4))+" expected="+Arrays.toString(key));found++;
        }
        N.require(found>100,"No exact packed owner payload");
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,registry);
        NativeGround.sourceBegin(new NativeSource(7.5f,3.5f,0,4,new float[]{-.5f,0,-.5f,.5f,1.3f,.5f},0));int id=sourceOwner();NativeGround.sourceEnd();
        P.draw(f,art,0,new float[]{-2,id,-id*id,1},0,true);
        // Only compare pixels with actual owning geometry: art outside it uses
        // the newly reconstructed source shell by design.
        int changed=0;for(int i=0;i<N.W*N.H;i++)if(solar[i*4+3]>0)for(int c=0;c<3;c++)if(Math.abs((original[i*4+c]&255)-(P.actual[i*4+c]&255))>1){changed++;break;}
        N.require(changed==0,"Exact owning proxy lost physical position/normal: "+changed);cases++;
        N.retire(roof);N.retire(mesh);
    }
}
