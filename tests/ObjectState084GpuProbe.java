import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import viewpoint.render.*;
import vpiso.*;
import zombie.iso.IsoObject;

/** Production source-range upload, GLSL helpers and asynchronous ownership readback on a real GPU. */
@SuppressWarnings("unchecked")
public final class ObjectState084GpuProbe {
    static int W=64,cases;
    static Class<?> sampleType;
    static Constructor<?> sampleConstructor;
    public static void main(String[] args)throws Exception {
        require(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"source ownership and fades084",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            int vbo=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            float[] quad={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};
            FloatBuffer vertices=BufferUtils.createFloatBuffer(24);vertices.put(quad).put(quad).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,8,0L);
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            int color=texture(GL30.GL_RGBA32F,GL11.GL_RGBA),depth=texture(GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glViewport(0,0,W,W);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");
            Field vertex=NativeObjectShaders.class.getDeclaredField("VERTEX"),fragment=NativeObjectShaders.class.getDeclaredField("FRAGMENT");
            vertex.setAccessible(true);fragment.setAccessible(true);
            String vs="#version 430\n"+vertex.get(null)+"\nlayout(location=0) in vec2 p;uniform int kind,key;uniform float z,scale;void main(){vpIsoObjectInfo=vpIsoSource(kind,kind==0?gl_VertexID:key);gl_Position=vec4(p*scale,z,1);}";
            String fs="#version 430\n"+fragment.get(null)+"\nuniform int key;out vec4 color;void main(){vpIsoObjectFade();color=vec4(key==0?vec3(1,0,0):vec3(0,1,0),1);vpIsoRecordPick();}";
            GlProgram program=GlProgram.fromSources("source084",new GlProgram.Sources(vs,null,null,null,fs),Map.of(),Map.of(),List.of("production source helpers084"));
            sampleType=Class.forName("vpiso.NativeObjectState$Sample");sampleConstructor=sampleType.getDeclaredConstructor(float.class,int.class);sampleConstructor.setAccessible(true);
            GameAccess.set("vpiso.IsometricBridge","modelsEnabled",null,true);GameAccess.set("vpiso.IsometricBridge","submitted",null,true);
            // Initialise the real UI picker before starting the 250 ms input-age fixture.
            GameAccess.call("viewpoint.interact.MouseTargets","contextPick",null,new Class<?>[]{int.class,int.class},0,0);
            for(int kind=0;kind<3;kind++)for(float alpha:new float[]{0,.25f,.66f,1}) {
                NativeObjectState state=state(alpha);
                Frame frame=(Frame)GameAccess.field("vpiso.NativeObjectState","frame",state);
                int oldTexture=GL11.glGenTextures(),oldStorage=GL15.glGenBuffers();
                GL13.glActiveTexture(GL13.GL_TEXTURE0+147);GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,oldTexture);
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,oldStorage);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128L,GL15.GL_DYNAMIC_DRAW);
                GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,6,oldStorage,0,128);GL13.glActiveTexture(GL13.GL_TEXTURE0);
                IsometricBridge.modelWorldRendering(frame,IsometricEffects.DEFAULT,true);state.beginRender();
                try {
                    NativeObjectState.prepared(frame.scene);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    program.use();NativeObjectState.onUse(program);program.setInt("kind",kind);program.setInt("key",0);program.set("z",0f);program.set("scale",1f);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
                    FloatBuffer pixels=BufferUtils.createFloatBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                    int covered=0;for(int i=0;i<W*W;i++)if(pixels.get(i*4)>.5f)covered++;
                    require(Math.abs((float)covered/(W*W)-alpha)<.025f,"Object fade not applied kind="+kind+" alpha="+alpha+" pixels="+covered);
                    // Same-pixel far container and small front switch. Source ownership,
                    // rather than a tile's last item, must win in either submission order.
                    for(boolean reverse:new boolean[]{false,true}) {
                        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LESS);GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                        for(int step=0;step<2;step++) {
                            boolean front=(step==0)==reverse;
                            program.setInt("key",front?0:1);program.set("z",front?-.4f:.4f);program.set("scale",front?.2f:1f);
                            GL11.glDrawArrays(GL11.GL_TRIANGLES,front?0:6,6);
                        }
                    }
                    // Above glReadPixels deliberately stalls for coverage checks. Start
                    // the input-age fixture after that unrelated readback/driver warm-up.
                    GameAccess.set("vpiso.NativeObjectState","capturedAt",state,System.nanoTime());
                    state.finishPick();GL11.glFinish();
                    Method collect=NativeObjectState.class.getDeclaredMethod("collect");collect.setAccessible(true);collect.invoke(state);
                    Object latest=GameAccess.field("vpiso.NativeObjectState","latest",null);
                    Object picked=GameAccess.field(latest.getClass().getName(),"object",latest);
                    List<Object> ids=(List<Object>)GameAccess.field("vpiso.NativeObjectState","ids",state);
                    // The central pixel's stable fade mask determines whether the front
                    // fragment exists; a fully hidden object must expose its background.
                    FloatBuffer centre=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(W/2,W-1-W/2,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,centre);
                    int expected=centre.get(0)>.5f?1:2;
                    require(picked==ids.get(expected),"Source selection diverged from actual frontmost fragment kind="+kind);
                    MousePick.ask=(MousePick.Ask)GameAccess.field("vpiso.NativeObjectState","ask",state);
                    Object fallback=new IsoObject();require(NativeObjectState.picked(fallback)==picked,"MouseTargets ownership adapter ignored selected source");
                    Object click=GameAccess.call("viewpoint.interact.MouseTargets","contextPick",null,new Class<?>[]{int.class,int.class},W/2,W/2);
                    require(click!=null&&GameAccess.field("zombie.iso.IsoObjectPicker$ClickObject","tile",click)==picked,"Woven context picker did not select the visible source");
                    MousePick.ask=new MousePick.Ask(-32,-32,W,W);require(NativeObjectState.picked(fallback)==fallback,"Mouse outside the captured region was accepted");
                    MousePick.ask=(MousePick.Ask)GameAccess.field("vpiso.NativeObjectState","ask",state);
                    state.finishPick();NativeObjectState.resetPick();GL11.glFinish();collect.invoke(state);
                    require(NativeObjectState.picked(fallback)==fallback,"Queued ownership survived a camera or mode reset");
                    if(Boolean.getBoolean("buffers087")) {
                        Object table=GameAccess.field("vpiso.NativeObjectState","tableWords",state),clear=GameAccess.field("vpiso.NativeObjectState","clearWords",state),read=GameAccess.field("vpiso.NativeObjectState","readWords",state);
                        for(int reuse=0;reuse<6;reuse++){
                            NativeObjectState.prepared(frame.scene);state.finishPick();GL11.glFinish();collect.invoke(state);
                            require(GameAccess.field("vpiso.NativeObjectState","tableWords",state)==table&&GameAccess.field("vpiso.NativeObjectState","clearWords",state)==clear&&GameAccess.field("vpiso.NativeObjectState","readWords",state)==read,"Per-drawer direct buffer was reallocated at stable capacity");
                        }
                    }
                    cases++;
                } finally {state.endRender();IsometricBridge.rendering(null);}
                require(GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,6)==oldStorage,"SSBO binding leaked");
                require(GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,6)==128,"SSBO range leaked");
                GL13.glActiveTexture(GL13.GL_TEXTURE0+147);require(GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER)==oldTexture,"Object table texture leaked");GL13.glActiveTexture(GL13.GL_TEXTURE0);
                require(GL11.glGetError()==0,"OpenGL error kind="+kind);
            }
            if(Boolean.getBoolean("buffers087"))System.out.println("Buffer reuse087 GPU PASS: 72 repeated uploads/readbacks reuse direct table, clear and read buffers; published pixel arrays remain separate.");
            System.out.println("ObjectState084 GPU PASS: cases="+cases+"; mesh, packed and independent source ranges; 0/25/66/100 percent fades; front switch/background container ownership; woven context picker; stale mouse and queued reset rejection; asynchronous readback and GL binding/range restoration.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static void currentWorld(zombie.iso.IsoGridSquare square, Object... objects)throws Exception {
        zombie.iso.IsoCell cell=(zombie.iso.IsoCell)Interaction084Probe.UNSAFE.allocateInstance(zombie.iso.IsoCell.class);
        zombie.iso.IsoChunk chunk=(zombie.iso.IsoChunk)Interaction084Probe.UNSAFE.allocateInstance(zombie.iso.IsoChunk.class);
        chunk.loaded=true;chunk.minLevel=chunk.maxLevel=square.z;chunk.wx=Math.floorDiv(square.x,8);chunk.wy=Math.floorDiv(square.y,8);
        chunk.squares=new zombie.iso.IsoGridSquare[square.z+1][64];chunk.squares[square.z][Math.floorMod(square.y,8)*8+Math.floorMod(square.x,8)]=square;
        zombie.iso.IsoChunkMap map=(zombie.iso.IsoChunkMap)Interaction084Probe.UNSAFE.allocateInstance(zombie.iso.IsoChunkMap.class);
        zombie.iso.IsoChunk[] chunks=new zombie.iso.IsoChunk[zombie.iso.IsoChunkMap.chunkGridWidth*zombie.iso.IsoChunkMap.chunkGridWidth];chunks[0]=chunk;
        GameAccess.set("zombie.iso.IsoChunkMap","chunksSwapA",map,chunks);GameAccess.set("zombie.iso.IsoChunkMap","chunksSwapB",map,chunks);
        GameAccess.set("zombie.iso.IsoCell","chunkMap",cell,new zombie.iso.IsoChunkMap[]{map,map,map,map});
        Object world=Interaction084Probe.UNSAFE.allocateInstance(zombie.iso.IsoWorld.class);
        GameAccess.set("zombie.iso.IsoWorld","currentCell",world,cell);GameAccess.set("zombie.iso.IsoWorld","instance",null,world);
        GameAccess.set("zombie.iso.IsoGridSquare","objects",square,new java.util.ArrayList<>(java.util.Arrays.asList(objects)));
    }
    static NativeObjectState state(float alpha)throws Exception {
        MousePick.ask=null;Frame frame=new Frame();NativeObjectState state=new NativeObjectState();state.beginCapture(frame);NativeObjectState.endCapture();
        GameAccess.set("vpiso.NativeObjectState","ask",state,new MousePick.Ask(W/2,W/2,W,W));
        IsoObject front=new IsoObject(),back=new IsoObject();
        Object square=Interaction084Probe.UNSAFE.allocateInstance(zombie.iso.IsoGridSquare.class);
        GameAccess.set("zombie.iso.IsoObject","square",front,square);GameAccess.set("zombie.iso.IsoObject","square",back,square);
        currentWorld((zombie.iso.IsoGridSquare)square,front,back);
        List<Object> ids=(List<Object>)GameAccess.field("vpiso.NativeObjectState","ids",state);ids.add(front);ids.add(back);
        Object a=sampleConstructor.newInstance(alpha,1),b=sampleConstructor.newInstance(1f,2);
        ChunkMeshData mesh=new ChunkMeshData(new zombie.core.textures.TextureID[1],new int[]{0},new int[]{12},new float[168],0);
        mesh.arenaFirst=0;mesh.objects(new int[]{0,0,6,1,6,6},new int[]{0,2},new int[]{0,1});
        GameAccess.set("viewpoint.render.ChunkMeshData","corners",mesh,0);GameAccess.set("viewpoint.render.ChunkMeshData","modelBase",mesh,0);
        frame.scene.meshCount=1;frame.scene.meshes[0]=mesh;
        ((Map<Integer,Map<Integer,Object>>)GameAccess.field("vpiso.NativeObjectState","meshes",state)).put(0,Map.of(0,a,1,b));
        Map<Integer,Object> models=(Map<Integer,Object>)GameAccess.field("vpiso.NativeObjectState","models",state);models.put(0,a);models.put(1,b);
        return state;
    }
    static int texture(int format,int channels) {int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,W,W,0,channels,GL11.GL_FLOAT,0L);return t;}
    static void require(boolean ok,String text) {if(!ok)throw new AssertionError(text);}
}
