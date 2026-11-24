import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import viewpoint.core.Frame;
import viewpoint.render.*;
import viewpoint.platform.GlProgram;
import vpiso.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Exercise VP's actual packed recipe, frame draw collector and command reuse without issuing GL. */
public final class PackedObjects063Probe {
    private static final String DRAW="viewpoint.render.PackDraws";
    public static boolean issued,failIssue;
    public static final class IssueSink {
        @Advice.OnMethodEnter(skipOn=Advice.OnNonDefaultValue.class)
        public static boolean enter() {
            PackedObjects063Probe.issued=true;
            if(PackedObjects063Probe.failIssue)throw new IllegalStateException("draw sink063");
            return true;
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("prepare")) {
            byte[] bytes;
            if(args[1].endsWith(".jar")) {
                try(java.util.jar.JarFile jar=new java.util.jar.JarFile(args[1])) {bytes=jar.getInputStream(jar.getJarEntry(DRAW.replace('.','/')+".class")).readAllBytes();}
            } else bytes=Files.readAllBytes(Path.of(args[1]));
            ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(Map.of(DRAW,bytes)),ClassFileLocator.ForClassLoader.ofSystemLoader());
            bytes=new ByteBuddy().redefine(TypePool.Default.of(locator).describe(DRAW).resolve(),locator)
                .visit(Advice.to(IssueSink.class).on(named("issue"))).make().getBytes();
            Path output=Path.of(args[2],DRAW.replace('.','/')+".class");Files.createDirectories(output.getParent());Files.write(output,bytes);
            return;
        }
        boolean baseline=args.length>0&&args[0].equals("baseline062");
        UpperObjects063Probe.main(baseline?new String[]{"baseline062"}:new String[0]);
        Object cutaways=GameAccess.staticCall("zombie.iso.fboRenderChunk.FBORenderCutaways","getInstance");
        Object per=((Object[])GameAccess.field(cutaways.getClass().getName(),"perPlayerData",cutaways))[0];
        Object btc=GameAccess.field(per.getClass().getName(),"buildingsToCollapse",per);
        @SuppressWarnings("unchecked") List<Object> collapsed=(List<Object>)GameAccess.field(btc.getClass().getName(),"buildingsToCollapse",btc);
        collapsed.add(ModelCutawayProbe.building);
        Object api=GameAccess.field("vpiso.IsometricVisibility$BindingsHolder","API",null);
        Object previous=GameAccess.field(api.getClass().getName(),"square",api);
        Visibility109Bindings.set(api,"square",UpperObjects063Probe.class.getMethod("squareAt",int.class,int.class,int.class));
        try {
            PackModel model=PackModels.of(new java.io.File("packed063-fixture.obj"),0);
            PackModels.publish();
            GameAccess.set("viewpoint.render.PackModel","state",model,3);
            GameAccess.set("viewpoint.render.PackModel","indices",model,6);
            // Use the real packed ownership producer, including multiple instances of one object.
            List<Object> recipe=new ArrayList<>();
            for(int owner:new int[]{0,1,1,2,-1}) {
                Object op=fresh("viewpoint.world.Recipe$Op");String name=op.getClass().getName();
                GameAccess.set(name,"model",op,model);GameAccess.set(name,"object",op,owner);
                GameAccess.set(name,"scale",op,1f);recipe.add(op);
            }
            Object cooker=fresh("viewpoint.world.Cook$Cooker");
            int[] runs=(int[])GameAccess.call(cooker.getClass().getName(),"models",cooker,new Class<?>[]{List.class},recipe);
            int[] owners=(int[])GameAccess.field(cooker.getClass().getName(),"modelObjects",cooker);
            require(owners.length==5&&runs.length==3,"Real packed producer failed to create mixed instances");
            ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[0],new int[0],new int[0],new float[0],1);
            mesh.models(0,runs);mesh.objects(null,null,owners);
            mesh.arenaFirst=200;
            int[] runsBefore=runs.clone(),ownersBefore=owners.clone();
            Frame frame=new Frame();SceneData scene=frame.scene;scene.meshCount=1;scene.meshes[0]=mesh;
            scene.meshFlags[0]=SceneData.VISIBLE|SceneData.NEAR|SceneData.CASTS_SHADOW;
            GlProgram program=(GlProgram)ModelCutawayProbe.allocate(GlProgram.class);
            Object uniforms=fresh("viewpoint.platform.UniformLookups");
            @SuppressWarnings("unchecked") Map<String,Integer> locations=(Map<String,Integer>)GameAccess.field(uniforms.getClass().getName(),"locations",uniforms);
            locations.put("uModels",0);GameAccess.set(GlProgram.class.getName(),"uniforms",program,uniforms);GameAccess.set(GlProgram.class.getName(),"id",program,1);
            GameAccess.set("viewpoint.render.PackModels","tableTexture",null,1);
            int cases=0;
            for(float floor:new float[]{0,1,0,1,0}) {
                frame.camZ=floor;ModelCutawayProbe.camera(floor);IsometricVisibility.apply(frame,new Object(),0);
                // Begin a new native pack frame, then exercise both collection and cached reuse.
                @SuppressWarnings("unchecked") List<Object> cache=(List<Object>)GameAccess.field(DRAW,"collected",null);cache.clear();
                for(int reuse=0;reuse<2;reuse++) {
                    draw(scene,program,SceneData.VISIBLE,true);
                    Set<Integer> slots=slots();
                    int first=((Number)GameAccess.call("viewpoint.render.ChunkMeshData","slot",mesh,new Class<?>[]{int.class},0)).intValue();
                    Set<Integer> expected=new HashSet<>();
                    for(int i=0;i<owners.length;i++)if(baseline||floor>=1||owners[i]!=1)expected.add(first+i);
                    require(slots.equals(expected),"Packed colour mismatch at floor="+floor+" reuse="+reuse+" slots="+slots+" expected="+expected);cases++;
                    draw(scene,program,SceneData.CASTS_SHADOW,false);
                    require(slots().size()==owners.length,"Physical packed shadow instances were clipped");cases++;
                }
                require(Arrays.equals(runs,runsBefore)&&Arrays.equals(owners,ownersBefore),"Shared packed metadata mutated");
            }
            // Verify the woven draw scope restores even on an issue exception.
            if(!baseline) {
                failIssue=true;
                try {draw(scene,program,SceneData.VISIBLE,true);throw new AssertionError("Exception sink not reached");}
                catch(IllegalStateException expected){require(expected.getMessage().equals("draw sink063"),"Wrong draw failure");}
                finally{failIssue=false;}
                boolean before=IsometricVisibility.enterPacked(false);require(!before,"Packed colour scope leaked after throw");IsometricVisibility.leavePacked(before);
                // A frame recaptured for a different floor must replace its per-object masks.
                frame.camZ=1;ModelCutawayProbe.camera(1);IsometricVisibility.apply(frame,new Object(),0);
                @SuppressWarnings("unchecked") List<Object> cache=(List<Object>)GameAccess.field(DRAW,"collected",null);cache.clear();
                draw(scene,program,SceneData.VISIBLE,true);require(slots().size()==5,"Reused frame kept an old object mask");cases+=2;
            }
            System.out.println((baseline?"Pinned062 packed upper negative control PASS: ":"Packed upper objects regression063 PASS: ")+cases+" cases; real Cook.models ownership and woven PackDraws.draw/collect, repeated packed instances, ownerless source, physical shadows, stairs, command reuse, per-frame refresh and exceptional scope cleanup; GL issue only intercepted, no live map.");
        } finally {Visibility109Bindings.set(api,"square",previous);collapsed.remove(ModelCutawayProbe.building);}
    }
    private static Object fresh(String name)throws Exception {Constructor<?> c=GameAccess.type(name).getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
    private static void draw(SceneData scene,GlProgram program,byte flag,boolean planned)throws Exception {
        issued=false;
        try {
            GameAccess.call(DRAW,"draw",null,new Class<?>[]{SceneData.class,GlProgram.class,byte.class,int.class,boolean.class},scene,program,flag,10,planned);
        } catch(IllegalStateException wrapped) {
            Throwable t=wrapped;while(t.getCause()!=null)t=t.getCause();if(t instanceof IllegalStateException state)throw state;throw wrapped;
        }
        require(issued,"Actual packed draw did not reach issue sink");
    }
    private static Set<Integer> slots() {
        Set<Integer> slots=new HashSet<>();
        int[] runs=(int[])GameAccess.field(DRAW,"runs",null);
        @SuppressWarnings("unchecked") List<Object> buckets=(List<Object>)GameAccess.field(DRAW,"buckets",null);
        int bucketCount=((Number)GameAccess.field(DRAW,"bucketCount",null)).intValue();
        for(int b=0;b<bucketCount;b++) {
            Object bucket=buckets.get(b);String type=bucket.getClass().getName();
            int count=((Number)GameAccess.field(type,"count",bucket)).intValue();int[] commands=(int[])GameAccess.field(type,"commands",bucket);
            for(int c=0;c<count;c++) {int run=commands[c*5+4],n=commands[c*5+1];for(int i=0;i<n;i++)slots.add(runs[run*4+1]+i);}
        }
        return slots;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
