import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import viewpoint.packs.PackBind;
import vpiso.*;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/** Real installed packed bush/hedge geometry, alpha and maps; synthetic native artwork. */
public final class NativePackedPlants072Probe {
    static final NativeShadow068Probe P=NativePlants072Probe.P;
    static final NativeSurfaceReceiverProbe N=P.N;
    static final Path ROOT=Path.of("D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3810302175/mods/PZVoxelStudioViewpoint/common/media/voxel-studio");
    static final String[] FILES={"5364bc76-5677-4d05-adfc-20a0db5d399d/h-9d0069d3ce/bare-big-0.obj",
        "5364bc76-5677-4d05-adfc-20a0db5d399d/h-9d0069d3ce/bush-big-8-lime.obj",
        "3264ddc4-3c80-4945-a248-88b68cb4b14f/h-9c55ddc73d/keep-keep-i0-model.obj"};
    static final PackModel[] MODELS=new PackModel[3];
    static boolean baseline;
    static Path output;
    static String type;
    static int cases,oldDark,externalPixels,lampPixels;
    public static void main(String[] args)throws Exception {
        P.pack=args[0];output=Path.of(args[1]);Files.createDirectories(output);baseline=Arrays.asList(args).contains("baseline071");
        N.require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(N.W,N.H,"072 actual packed vegetation",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            TestShaderPacks.selectLive(P.pack,P.pack.equals("somber")?"quality":"high",Map.of("upscaling","off"));
            N.fixture();P.nativeProgram=NativeGroundProbe.link(NativePlants072Probe.VERTEX,NativePlants072Probe.NATIVE);
            P.metadata=P.texture(GL30.GL_RGBA32F,N.W,N.H);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,N.fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT6,GL11.GL_TEXTURE_2D,P.metadata,0);N.overlay.metadata(P.metadata);
            for(int i=0;i<FILES.length;i++) {
                var file=ROOT.resolve(FILES[i]).toFile();MODELS[i]=PackModels.of(file);
                PackData data=(PackData)GameAccess.call("viewpoint.packs.PackLoads","load",null,new Class<?>[]{java.io.File.class,Path.class},file,ROOT);
                PackModels.onItsWay();MODELS[i].read(data);
            }PackModels.publish();
            if(!baseline){sourceScope();boundsOwnership();}
            for(boolean hedge:new boolean[]{false,true}) {
                type=hedge?"hedge":"bush";
                NativePlants072Probe.half=hedge?.64f:.67f;NativePlants072Probe.height=hedge?1.04f:1.32f;
                for(int state=0;state<3;state++) {
                    NativePlants072Probe.fit(false,state);Frame f=NativePlants072Probe.frame(state);
                    f.scene.sunX=state==0?-.888565f:f.scene.sunX;f.scene.sunY=state==0?.4372087f:f.scene.sunY;f.scene.sunZ=state==0?.13892712f:f.scene.sunZ;
                    Object vegetation=mesh(f,hedge,true,0,0,0);float[][] art=NativePlants072Probe.billboard();draw(f,art);
                    int dark=NativePlants072Probe.dark(P.actual),seams=NativePlants072Probe.seams(P.actual);
                    physicalReference(state);System.out.println("Packed "+type+" state="+state+" baseline="+baseline+" selfDark="+dark+" seams="+seams);
                    if(baseline)oldDark+=dark;else {
                        if(dark>0)selfDebug(f);
                        N.require(dark==0&&seams==0,"Packed own-part reception discontinuity "+type+" state="+state+" dark="+dark+" seams="+seams);
                    }
                    cases++;if(!baseline&&state==0)external(f,art);N.retire(vegetation);
                }
                lamp(hedge);if(!baseline)ordinary(hedge);
            }
            if(baseline)N.require(oldDark>10,"Actual packed geometry failed to reproduce prior receiver defect");
            else N.require(externalPixels>10&&lampPixels>5,"External or lamp control empty");
            N.require(GL11.glGetError()==0,"GL error end");
            System.out.println((baseline?"Pinned071 packed vegetation negative PASS":"Actual packed vegetation072 GPU PASS")+" pack="+P.pack+" cases="+cases+" oldSelfDark="+oldDark+" externalPixels="+externalPixels+" lampPixels="+lampPixels
                +"; actual OBJ/MTL/alpha and VP cook/pack/upload/receiver/maps, same physical positions/normals, frozen071 lamp bytes, external-only regenerated maps, native alpha/depth/stencil, untagged model exclusion; synthetic native art, live visual acceptance pending.");
        } finally {IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static Object fresh(String name,int... args)throws Exception {
        Class<?>[] types=new Class<?>[args.length];Arrays.fill(types,int.class);var c=GameAccess.type(name).getDeclaredConstructor(types);c.setAccessible(true);
        Object[] boxed=new Object[args.length];for(int i=0;i<args.length;i++)boxed[i]=args[i];return c.newInstance(boxed);
    }
    static Object mesh(Frame f,boolean hedge,boolean tagged,float x,float y,float z)throws Exception {
        Object recipe=fresh("viewpoint.world.Recipe",0);List<Object> ops=new ArrayList<>();
        for(int index:hedge?new int[]{2}:new int[]{0,1}) {
            PackBind bind=new PackBind(MODELS[index],0,0,0,0,1);
            Object op=GameAccess.call("viewpoint.world.Recipe","model",null,
                new Class<?>[]{recipe.getClass(),PackBind.class,boolean.class,float.class,float.class,float.class,int.class,int.class,int.class,int.class},
                recipe,bind,false,-x,-z,y,0,tagged?1<<27:0,0,0);
            GameAccess.set("viewpoint.world.Recipe$Op","object",op,4<<16);ops.add(op);
        }
        Object cooker=fresh("viewpoint.world.Cook$Cooker");int[] runs=(int[])GameAccess.call(cooker.getClass().getName(),"models",cooker,new Class<?>[]{List.class},ops);
        if(!baseline && !Boolean.getBoolean("plants072.wovenCook"))PlantModelBounds.cooked(cooker,ops);
        int length=((Number)GameAccess.field(cooker.getClass().getName(),"modelFloats",cooker)).intValue();
        float[] records=(float[])GameAccess.field(cooker.getClass().getName(),"instances",cooker);
        FloatBuffer data=org.lwjgl.system.MemoryUtil.memAllocFloat(length);data.put(records,0,length).flip();
        Object pages=java.lang.reflect.Array.newInstance(GameAccess.type("zombie.core.textures.TextureID"),0);
        Object mesh=GameAccess.type("viewpoint.render.ChunkMeshData").getConstructor(pages.getClass(),int[].class,int[].class,FloatBuffer.class,int.class,int[].class,int[].class)
                .newInstance(pages,new int[0],new int[0],data,0,null,null);
        GameAccess.call(mesh.getClass().getName(),"models",mesh,new Class<?>[]{int.class,int[].class},0,runs);
        GameAccess.call(mesh.getClass().getName(),"objects",mesh,new Class<?>[]{int[].class,int[].class,int[].class},null,null,GameAccess.field(cooker.getClass().getName(),"modelObjects",cooker));
        ((Object[])GameAccess.field("viewpoint.render.SceneData","meshes",f.scene))[0]=mesh;f.scene.meshCount=1;f.scene.meshFlags[0]=2|8|16;
        f.scene.meshFade[0]=0;f.scene.meshFade[1]=2;P.publish(mesh,true,0);return mesh;
    }
    static void draw(Frame f,float[][] art)throws Exception {
        GL20.glUseProgram(P.nativeProgram);GL20.glUniform1f(GL20.glGetUniformLocation(P.nativeProgram,"cardHeight"),NativePlants072Probe.height);
        GL20.glUniform1f(GL20.glGetUniformLocation(P.nativeProgram,"halfWidth"),NativePlants072Probe.half);
        GL20.glUniform1f(GL20.glGetUniformLocation(P.nativeProgram,"flatDepth"),1);
        GL20.glUniform2f(GL20.glGetUniformLocation(P.nativeProgram,"across"),(float)Math.cos(NativePlants072Probe.angle+Math.PI*.75),(float)Math.sin(NativePlants072Probe.angle+Math.PI*.75));
        NativePlants072Probe.invalidate();P.draw(f,art,0,new float[]{1,0,0,1},0,true);
        NativePlants072Probe.surfaces=(NativeSurfaces)GameAccess.field("vpiso.NativeOverlay","surfaces",N.overlay);
    }
    static void physicalReference(int state)throws Exception {
        NativeSurfaces s=NativePlants072Probe.surfaces;float[] positions=NativeReceivers071Probe.texture(s.texture()),normals=NativeReceivers071Probe.texture(s.normals());
        ByteBuffer data=ByteBuffer.allocate(positions.length*8).order(ByteOrder.LITTLE_ENDIAN);int received=0;
        for(int i=0;i<positions.length/4;i++) {
            if(Math.abs(positions[i*4+3])>.5){received++;N.require(Math.abs(positions[i*4+3]-(baseline?1:6))<.01,"Packed foliage source classification");}
            for(int c=0;c<3;c++)data.putFloat(positions[i*4+c]);for(int c=0;c<4;c++)data.putFloat(normals[i*4+c]);
        }N.require(received>50,"Actual model surface empty "+type+" count="+received);
        Path path=output.resolve("physical071-"+P.pack+"-"+type+"-"+state+".bin");
        if(baseline)Files.write(path,Arrays.copyOf(data.array(),data.position()));
        else N.require(Arrays.equals(Files.readAllBytes(path),Arrays.copyOf(data.array(),data.position())),"Packed physical pos/n or owner changed");
    }
    static void external(Frame f,float[][] art)throws Exception {
        float height=NativePlants072Probe.height,half=NativePlants072Probe.half,dy=height*.5f+.5f;
        float span=half+Math.max(Math.abs(f.scene.sunX),Math.abs(f.scene.sunZ))/f.scene.sunY*height*.5f;
        float[][] points=N.horizontal(height+.5f,span*.65f);for(float[] p:points){p[0]+=f.scene.sunX/f.scene.sunY*dy;p[2]+=f.scene.sunZ/f.scene.sunY*dy;}
        Object blocker=N.mesh(f.scene,1,points);draw(f,art);byte[] full=P.actual.clone();int dark=NativePlants072Probe.dark(full);N.require(dark>5,"Actual packed external reception empty "+type);externalPixels+=dark;
        byte flags=f.scene.meshFlags[0];f.scene.meshFlags[0]=0;NativePlants072Probe.invalidate();IsometricBridge.rendering(f);
        try{N.require(NativeShadowMaps.draw(f.scene,1024,2,null),"External-only map");}finally{f.scene.meshFlags[0]=flags;IsometricBridge.rendering(null);}
        NativePlants072Probe.recompose(f);byte[] expected=P.copy(N.read());N.require(P.different(full,expected,3)==0,"Own packed parts changed external reception "+type);
        N.retire(blocker);f.scene.meshCount=1;cases++;
    }
    static void lamp(boolean hedge)throws Exception {
        NativePlants072Probe.fit(false,0);Frame f=NativePlants072Probe.frame(0);Object plant=mesh(f,hedge,true,0,0,0);
        float[][] art=NativePlants072Probe.billboard();f.scene.sunStrength=0;P.lamp(f.scene,0,3,true,4);f.scene.lightCount=f.scene.lampShadowCount=1;
        float[][] points=N.horizontal(2,.6f);for(float[] p:points)p[0]+=.6f;Object blocker=N.mesh(f.scene,1,points);
        for(int i=0;i<12;i++)draw(f,art);
        int dark=NativePlants072Probe.dark(P.actual);lampPixels+=dark;
        Path pinned=output.resolve("packed-lamp071-"+P.pack+"-"+type+".rgba8");if(baseline)Files.write(pinned,P.actual);
        else N.require(Arrays.equals(Files.readAllBytes(pinned),P.actual),"Packed solar fix changed lamp-only RGBA "+type);
        System.out.println("Packed "+type+" lamp-only dark="+dark+" "+(baseline?"071 reference":"exact071 PASS"));
        N.retire(blocker);N.retire(plant);cases++;
    }
    static void ordinary(boolean hedge)throws Exception {
        NativePlants072Probe.fit(false,0);Frame f=NativePlants072Probe.frame(0);Object model=mesh(f,hedge,false,0,0,0);draw(f,NativePlants072Probe.billboard());
        float[] pos=NativeReceivers071Probe.texture(NativePlants072Probe.surfaces.texture());int received=0;
        for(int i=0;i<N.W*N.H;i++)if(Math.abs(pos[i*4+3])>.5){received++;N.require(Math.abs(pos[i*4+3]-1)<.01,"Untagged model borrowed plant correction");}
        N.require(received>50,"Untagged model control empty");N.retire(model);cases++;
    }
    static void sourceScope()throws Exception {
        for(String name:new String[]{"f_bushes_1_8","vegetation_ornamental_01_5","furniture_seating_0","fixtures_windows_0"}) {
            IsoObject object=(IsoObject)N.allocate(IsoObject.class);IsoSprite sprite=new IsoSprite();sprite.setName(name);GameAccess.set(IsoObject.class.getName(),"sprite",object,sprite);
            int prev=TreeModelScope.enter(object);try{N.require(TreeModelScope.flags(0)==(name.startsWith("f_")||name.startsWith("vegetation_")?1<<27:0),"Native source tag "+name);}
            finally{TreeModelScope.restore(prev);}N.require(TreeModelScope.flags(0)==0,"Source tag leaked");
        }
    }
    static void boundsOwnership()throws Exception {
        Object recipe=fresh("viewpoint.world.Recipe",0);List<Object> ops=new ArrayList<>();
        PackModel missing=PackModels.of(ROOT.resolve("unused072-no-read.obj").toFile());
        for(int i=0;i<7;i++) {
            PackModel model=i==6?missing:MODELS[i==0?0:1];
            PackBind bind=new PackBind(model,i%2==0?37:0,0,0,0,i==3?-.8f:1);
            int owner=i<2?123:i<4?124:i<6?-1:125;
            float x=i<2?i*.12f:i<4?20:i==4?40:i==5?60:80;
            Object op=GameAccess.call("viewpoint.world.Recipe","model",null,
                new Class<?>[]{recipe.getClass(),PackBind.class,boolean.class,float.class,float.class,float.class,int.class,int.class,int.class,int.class},
                recipe,bind,true,-x,0f,i==1?.15f:0f,0,i==3?0:1<<27,0,0);
            GameAccess.set("viewpoint.world.Recipe$Op","object",op,owner);ops.add(op);
        }
        // Frozen VP producer first: the candidate must only populate its four
        // reserved floats, regardless of sorting or un-published render indices.
        Object cooker=fresh("viewpoint.world.Cook$Cooker");Main.compatible=false;
        try{GameAccess.call(cooker.getClass().getName(),"models",cooker,new Class<?>[]{List.class},ops);}finally{Main.compatible=true;}
        int length=((Number)GameAccess.field(cooker.getClass().getName(),"modelFloats",cooker)).intValue();
        float[] data=(float[])GameAccess.field(cooker.getClass().getName(),"instances",cooker);float[] before=Arrays.copyOf(data,length);
        PlantModelBounds.cooked(cooker,ops);
        for(int t=0;t<length;t+=14) {
            for(int c=0;c<10;c++)N.require(Float.floatToRawIntBits(before[t+c])==Float.floatToRawIntBits(data[t+c]),"Physical packed field changed");
            int model=Float.floatToRawIntBits(data[t+8]);boolean plant=(Float.floatToRawIntBits(data[t+6])&(1<<27))!=0;
            N.require((int)data[t+13]==(plant&&model!=missing.index?1:0),"Unknown or untagged source borrowed crown bounds");
            N.require(data[t+10]<3,"Different native owners/ownerless roots merged");
        }
        if(Boolean.getBoolean("plants072.wovenCook")) {
            Object automatic=fresh("viewpoint.world.Cook$Cooker");GameAccess.call(automatic.getClass().getName(),"models",automatic,new Class<?>[]{List.class},ops);
            N.require(Arrays.equals(Arrays.copyOf(data,length),Arrays.copyOf((float[])GameAccess.field(automatic.getClass().getName(),"instances",automatic),length)),"Actual Cook hook differs from bounded metadata producer");
            GameAccess.call(automatic.getClass().getName(),"models",automatic,new Class<?>[]{List.class},(Object)null);
            N.require(((Number)GameAccess.field(automatic.getClass().getName(),"modelFloats",automatic)).intValue()==0,"Empty cook retained crowns");
        }
        System.out.println("Packed source ownership PASS: sorted multi-part owner, shifted/scaled/rotated binds, far neighbour, two ownerless roots, ordinary model, missing bounds, empty cook, unchanged first10 raw fields and actual woven hook equality.");
    }
    static void selfDebug(Frame f)throws Exception {
        NativeSurfaces s=NativePlants072Probe.surfaces;float[] pos=NativeReceivers071Probe.texture(s.texture()),norm=NativeReceivers071Probe.texture(s.normals()),solar=NativeReceivers071Probe.texture(s.solar());
        int printed=0;for(int i=0;i<N.W*N.H;i++)if((P.before[i*4]&255)>170&&(P.actual[i*4]&255)+3<(P.before[i*4]&255)&&printed++<5)
            System.out.println("DARK "+i%N.W+","+i/N.W+" pos="+Arrays.toString(Arrays.copyOfRange(pos,i*4,i*4+4))+" n="+Arrays.toString(Arrays.copyOfRange(norm,i*4,i*4+4))+" solar="+Arrays.toString(Arrays.copyOfRange(solar,i*4,i*4+4)));
        Object dummy=N.mesh(f.scene,1,N.horizontal(-5,.03f));
        f.scene.meshFlags[0]=0;NativePlants072Probe.invalidate();IsometricBridge.rendering(f);
        try{NativeShadowMaps.draw(f.scene,1024,2,null);}finally{f.scene.meshFlags[0]=2|8|16;IsometricBridge.rendering(null);}
        NativePlants072Probe.recompose(f);System.out.println("Own caster removed dark="+NativePlants072Probe.dark(P.copy(N.read())));
        GL44.glClearTexImage(NativeShadowMaps.texture(),0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,new float[]{1});
        NativePlants072Probe.recompose(f);System.out.println("Actual depth map cleared dark="+NativePlants072Probe.dark(P.copy(N.read())));
        N.retire(dummy);f.scene.meshCount=1;
    }
}
