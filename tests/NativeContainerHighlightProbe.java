import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.vm.*;
import viewpoint.core.Frame;
import viewpoint.platform.*;
import viewpoint.render.*;
import vpiso.*;
import zombie.core.textures.ColorInfo;
import zombie.inventory.ItemContainer;
import zombie.iso.IsoObject;

/** Actual native selection state, actual VP object mask and skinned target mask; isolated world/assets. */
public final class NativeContainerHighlightProbe {
    static final int W=160;
    static KahluaTable env, page, data;
    static KahluaThread thread;

    public static void main(String[] args)throws Exception {
        selection();
        if (!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(W,W,"native model container selection",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();Main.compatible=true;
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{java.io.File.class},(Object)null);
            int scale=Integer.getInteger("highlight096.scale",100);
            String pack=System.getProperty("highlight096.pack",scale==100?"library":"default");
            ShaderPacks.useBuiltIn(pack,pack.equals("somber")?"quality":"high",Map.of("upscaling",scale==100?"off":"fsr3","renderScale",Integer.toString(scale)));
            if(Boolean.getBoolean("framegen097")) {
                IsometricControls.tick();
                ((LiveSettings.Toggle)GameAccess.field("vpiso.IsometricControls","frameGeneration",null)).set(true);
                Graphics.FRAME_GENERATION.set(false);
                GameAccess.set("viewpoint.render.FrameGenPass","fixedScreen",null,new int[]{W,W});
            }
            for(boolean skin:new boolean[]{false,true}) for(float yaw:new float[]{0,137}) gpu(skin,yaw);
            check(GL11.glGetError()==0,"GL error");
        } finally {NativeContainerHighlight.endRender();IsometricBridge.rendering(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        System.out.println("Native container highlight PASS: original native highlight/outline flags and colours, furniture/corpse parent identity, collapse/switch/clear lifecycle, outside-scope passthrough; actual VP precise object spans and skinned targets at two yaw angles; selected-only GPU tint/outline; output alpha/depth preserved. Synthetic map/assets/UI tables; live-save acceptance remains.");
    }

    static void selection()throws Exception {
        GameAccess.call("zombie.core.random.RandStandard","init",GameAccess.field("zombie.core.random.RandStandard","INSTANCE",null),new Class<?>[0]);
        var platform=J2SEPlatform.getInstance();env=platform.newEnvironment();thread=new KahluaThread(platform,env);
        Class<?> managerType=Class.forName("se.krka.kahlua.converter.KahluaConverterManager");
        Object manager=managerType.getConstructor().newInstance();
        Class.forName("zombie.Lua.KahluaNumberConverter").getMethod("install",managerType).invoke(null,manager);
        Object caller=Class.forName("se.krka.kahlua.integration.LuaCaller").getConstructor(managerType).newInstance(manager);
        GameAccess.set("zombie.Lua.LuaManager","env",null,env);GameAccess.set("zombie.Lua.LuaManager","thread",null,thread);GameAccess.set("zombie.Lua.LuaManager","caller",null,caller);
        GameAccess.set("se.krka.kahlua.vm.KahluaThread","debugOwnerThread",thread,Thread.currentThread());
        data=platform.newTable();page=platform.newTable();data.rawset("lootInventory",page);
        env.rawset("getPlayerData",(JavaFunction)(f,n)->{f.push(data);return 1;});
        Object sq=NativeSurfaceReceiverProbe.allocate(GameAccess.type("zombie.iso.IsoGridSquare"));
        Frame frame=new Frame();NativeContainerHighlight selection=new NativeContainerHighlight();
        Object earlier=NativeSurfaceReceiverProbe.allocate(IsoObject.class);
        GameAccess.set("viewpoint.core.Frame","lootObject",frame,earlier);
        NativeContainerHighlight.snapshot(frame);
        check(GameAccess.field("viewpoint.core.Frame","lootObject",frame)==earlier,"Selection leaked outside capture");
        for(String type:List.of("zombie.iso.IsoObject","zombie.iso.objects.IsoDeadBody")) {
            IsoObject obj=(IsoObject)NativeSurfaceReceiverProbe.allocate(GameAccess.type(type));
            GameAccess.set("zombie.iso.IsoObject","square",obj,sq);
            GameAccess.set("zombie.iso.IsoObject","highlightColor",obj,new ColorInfo[4]);
            GameAccess.set("zombie.iso.IsoObject","outlineHighlightCol",obj,new int[4]);
            ItemContainer container=new ItemContainer();container.parent=obj;
            page.rawset("inventory",container);page.rawset("coloredInv",container);page.rawset("isCollapsed",false);
            obj.setHighlighted(0,true,false);obj.setHighlightColor(0,.83f,.71f,.16f,1);
            obj.setOutlineHighlight(0,true);obj.setOutlineHighlightCol(0,.97f,.84f,.04f,1);
            selection.begin();try {snapshot(frame);}finally{NativeContainerHighlight.end();}
            check(GameAccess.field("viewpoint.core.Frame","lootObject",frame)==obj,"Selected native parent lost: "+type);
            check((Boolean)get(selection,"fill")&&(Boolean)get(selection,"edge"),"Native highlight/outline flags lost");
            close((Float)get(selection,"r"),.83f);close((Float)get(selection,"g"),.71f);close((Float)get(selection,"b"),.16f);
            close((Float)get(selection,"outlineR"),.97f,1f/255);close((Float)get(selection,"outlineB"),.04f,1f/255);
            // A second queued frame must not share the first selection's copied colours.
            obj.setHighlightColor(0,.1f,.2f,.3f,1);
            NativeContainerHighlight second=new NativeContainerHighlight();second.begin();try{snapshot(new Frame());}finally{NativeContainerHighlight.end();}
            close((Float)get(selection,"r"),.83f);close((Float)get(second,"r"),.1f);
            obj.setOutlineHighlight(0,false);
            selection.begin();try{snapshot(frame);}finally{NativeContainerHighlight.end();}
            check((Boolean)get(selection,"fill")&&!(Boolean)get(selection,"edge"),"Native outline-off did not retain fill-only mode");
            for(String gate:List.of("collapsed","switched","closed","unhighlighted")) {
                page.rawset("isCollapsed",gate.equals("collapsed"));
                page.rawset("coloredInv",gate.equals("closed")?null:container);
                page.rawset("inventory",gate.equals("switched")?new ItemContainer():container);
                obj.setHighlighted(0,!gate.equals("unhighlighted"),false);
                selection.begin();try{snapshot(frame);}finally{NativeContainerHighlight.end();}
                check(!(Boolean)get(selection,"fill")&&!(Boolean)get(selection,"edge"),"Stale highlight after "+gate+" "+type);
            }
            System.out.println("Native selection state PASS: "+type);
        }
    }

    static void gpu(boolean skin,float yaw)throws Exception {
        int color=NativeGroundProbe.texture(W,W,null),output=GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        int depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH24_STENCIL8,W,W);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_STENCIL_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
        Frame frame=new Frame();frame.number=(skin?200:100)+(int)yaw;
        frame.scene.skyR=frame.scene.skyG=frame.scene.skyB=1;
        Object mesh=null;
        if(skin) {
            NativeSurfaceReceiverProbe.addModel(frame.scene,quad(-2,-.2f),true,false);
            frame.scene.models.target(0);
            NativeSurfaceReceiverProbe.addModel(frame.scene,quad(.2f,2),true,false);
        } else {
            float[][] points=new float[12][3];System.arraycopy(quad(-2,-.2f),0,points,0,6);System.arraycopy(quad(.2f,2),0,points,6,6);
            mesh=NativeSurfaceReceiverProbe.mesh(frame.scene,0,points);frame.scene.meshFlags[0]|=1;frame.scene.meshPlan[0]=-1;frame.scene.meshModelSquares[0]=-1L;
            ((ChunkMeshData)mesh).objects(new int[]{0,0,6,1,6,6},new int[]{0,2},null);
        }
        NativeContainerHighlight selection=new NativeContainerHighlight();
        set(selection,"mesh",mesh);set(selection,"objectId",0);set(selection,"tile",0);set(selection,"owner",0);
        set(selection,"fill",true);set(selection,"edge",true);set(selection,"r",1f);set(selection,"g",1f);set(selection,"b",0f);set(selection,"a",1f);
        set(selection,"outlineR",1f);set(selection,"outlineG",1f);set(selection,"outlineB",0f);set(selection,"outlineA",1f);set(selection,"thickness",1f);
        Matrix4f view=new Matrix4f();float[] eye=new float[3];
        IsoCameraMath.matrices(new IsoCameraMath.Fit(0,0,4),0,0,W,W,frame.scene.projection,view,eye,yaw);
        IsometricBridge.modelWorldRendering(frame,new IsometricEffects(false,0,0,64,1024,false,0,1,1,1),true);
        selection.beginRender();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,W);GL11.glClearColor(.6f,.6f,.6f,.43f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            check(WorldRenderer.begin(frame.scene,frame.number,1,view,eye[0],eye[1],eye[2]),"Renderer begin");
            int scale=Integer.getInteger("highlight096.scale",100);
            FrameContext context=(FrameContext)GameAccess.field("viewpoint.render.WorldRenderer","frame",null);
            check(context.width==Math.round(W*scale/100f),"FSR world did not use the requested render scale");
            WorldRenderer.drawOutlines();WorldRenderer.drawTranslucent(frame.scene);WorldRenderer.finish(frame.scene);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);
            // Known presentation colour isolates highlighting from scene-lighting fixtures.
            GL11.glColorMask(true,true,true,true);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(.6f,.6f,.6f,.43f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            byte[] before=read();float[] depthBefore=new float[W*W];GL11.glReadPixels(0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthBefore);
            Object oldMesh=GameAccess.field("viewpoint.render.SceneData","targetMesh",frame.scene);
            selection.draw(frame);
            byte[] after=read();float[] depthAfter=new float[W*W];GL11.glReadPixels(0,0,W,W,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthAfter);
            check(Arrays.equals(depthBefore,depthAfter),"Highlight changed output depth");
            check(GameAccess.field("viewpoint.render.SceneData","targetMesh",frame.scene)==oldMesh,"Temporary scene target leaked");
            Object target=GameAccess.field("viewpoint.render.WorldRenderer","target",null);
            int mask=(Integer)GameAccess.field("viewpoint.render.TargetOutline","maskTexture",target);
            ByteBuffer maskPixels=BufferUtils.createByteBuffer(W*W);GL45.glGetTextureImage(mask,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,maskPixels);
            check(maskAt(maskPixels,frame,view,-1.1f)>200,"Selected object centre missing from mask");
            check(maskAt(maskPixels,frame,view,1.1f)==0,"Neighbour object included in selected mask");
            int masked=0,changed=0,wrong=0;
            for(int i=0;i<W*W;i++) {
                check(before[i*4+3]==after[i*4+3],"Highlight changed output alpha");
                if((maskPixels.get(i)&255)>200){masked++;if((after[i*4+2]&255)<(before[i*4+2]&255))changed++;}
                if(!nearMask(maskPixels,i)&&!Arrays.equals(Arrays.copyOfRange(before,i*4,i*4+4),Arrays.copyOfRange(after,i*4,i*4+4)))wrong++;
            }
            check(masked>30&&changed>30,"Selected "+(skin?"skin":"baked object")+" mask/tint empty: "+masked+"/"+changed);
            check(wrong==0,"Unselected pixels tinted: "+wrong);
            // Native outline option can be disabled independently of the filled selection.
            set(selection,"edge",false);clearOutput(output);selection.draw(frame);
            byte[] fillOnly=read();int outsideChanges=0;
            for(int i=0;i<W*W;i++) if((maskPixels.get(i)&255)==0 && (fillOnly[i*4+2]&255)!=153)outsideChanges++;
            check(outsideChanges==0,"Fill-only selection drew an outline");
            set(selection,"fill",false);set(selection,"edge",true);clearOutput(output);selection.draw(frame);
            byte[] edgeOnly=read();int interiorChanges=0,edgeChanges=0;
            for(int i=0;i<W*W;i++) {
                if((maskPixels.get(i)&255)==255&&(edgeOnly[i*4+2]&255)!=153)interiorChanges++;
                if((maskPixels.get(i)&255)==0&&(edgeOnly[i*4+2]&255)<153)edgeChanges++;
            }
            check(interiorChanges==0&&edgeChanges>0,"Native outline-only mode did not preserve the interior");
            // Closing the next native selection must be a complete no-op even if the old mask still exists.
            selection.begin();NativeContainerHighlight.end();selection.draw(frame);check(Arrays.equals(edgeOnly,read()),"Stale mask after selection closed");
            System.out.println("Container highlight GPU PASS: "+(skin?"skinned body":"precise object spans")+" yaw="+yaw+" selectedPixels="+masked+" tinted="+changed+" wrong="+wrong);
            if(Boolean.getBoolean("framegen097")) frameGeneration(frame,view,eye,selection,output);
        }finally {NativeContainerHighlight.endRender();IsometricBridge.rendering(null);if(mesh!=null)NativeSurfaceReceiverProbe.retire(mesh);GL30.glDeleteFramebuffers(output);GL11.glDeleteTextures(color);GL30.glDeleteRenderbuffers(depth);}
    }

    static void frameGeneration(Frame frame,Matrix4f view,float[] eye,NativeContainerHighlight selection,int output) {
        Object pass=GameAccess.field("viewpoint.render.FrameGenPass","made",null);
        check(pass!=null && GameAccess.field("viewpoint.render.FrameGenPass","generation",pass)!=null,"Actual world FG capture missing");
        GameAccess.call("viewpoint.render.FrameGenPass","copyHudless",pass,new Class<?>[]{int.class},output);
        check(!(Boolean)GameAccess.call("viewpoint.render.FrameGenPass","generate",pass,new Class<?>[]{int.class},output),"First actual-world FG must warm up");
        Object originalClock=GameAccess.field("viewpoint.render.WorldRenderer","clock",null);
        long[] time={1_000_000_000L};
        GameAccess.set("viewpoint.render.WorldRenderer","clock",null,(java.util.function.LongSupplier)()->time[0]);
        try {
            int generated=0;
            for(int i=0;i<4;i++) {
                frame.number++;frame.scene.cameraCut=i==0;time[0]+=16_000_000L;
                GameAccess.set("viewpoint.render.TemporalPass","lastFrameNanos",GameAccess.field("viewpoint.render.WorldRenderer","temporal",null),time[0]-16_000_000L);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,output);GL11.glViewport(0,0,W,W);
                check(WorldRenderer.begin(frame.scene,frame.number,1,view,eye[0],eye[1],eye[2]),"Actual FG world begin");
                WorldRenderer.drawTranslucent(frame.scene);WorldRenderer.finish(frame.scene);selection.draw(frame);
                pass=GameAccess.field("viewpoint.render.FrameGenPass","made",null);
                GameAccess.call("viewpoint.render.FrameGenPass","copyHudless",pass,new Class<?>[]{int.class},output);
                boolean inserted=(Boolean)GameAccess.call("viewpoint.render.FrameGenPass","generate",pass,new Class<?>[]{int.class},output);
                check(inserted==(i>0),"Actual FG history lifecycle");
                if(inserted)generated++;
            }
            check(generated==3,"Actual FG world did not generate");
            System.out.println("FrameGen097 full world PASS: skin="+((Boolean)GameAccess.field("viewpoint.render.ModelDraws","targeted",frame.scene.models))+" renderedScale="+Integer.getInteger("highlight096.scale",100)+" generated="+generated+" native highlight retained");
        } finally {GameAccess.set("viewpoint.render.WorldRenderer","clock",null,originalClock);}
    }
    static boolean nearMask(ByteBuffer mask,int i){int x=i%W,y=i/W;for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)if(x+dx>=0&&x+dx<W&&y+dy>=0&&y+dy<W&&(mask.get((y+dy)*W+x+dx)&255)>0)return true;return false;}
    static int maskAt(ByteBuffer mask,Frame f,Matrix4f view,float x){var p=new org.joml.Vector4f(x,0,0,1).mul(view).mul(f.scene.projection);int px=(int)((p.x/p.w*.5f+.5f)*W),py=(int)((p.y/p.w*.5f+.5f)*W);return mask.get(py*W+px)&255;}
    static float[][] quad(float lo,float hi){return new float[][]{{lo,0,-.8f},{hi,0,-.8f},{hi,0,.8f},{lo,0,-.8f},{hi,0,.8f},{lo,0,.8f}};}
    static byte[] read(){ByteBuffer b=BufferUtils.createByteBuffer(W*W*4);GL11.glReadPixels(0,0,W,W,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[] a=new byte[b.capacity()];b.get(a);return a;}
    static void clearOutput(int fbo){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glColorMask(true,true,true,true);GL11.glClearColor(.6f,.6f,.6f,.43f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);}
    static Object get(Object o,String key){return GameAccess.field("vpiso.NativeContainerHighlight",key,o);}
    static void snapshot(Frame frame){GameAccess.call("viewpoint.interact.LootMenu","snapshot",null,new Class<?>[]{Frame.class,GameAccess.type("zombie.characters.IsoGameCharacter")},frame,null);}
    static void set(Object o,String key,Object value){GameAccess.set("vpiso.NativeContainerHighlight",key,o,value);}
    static void close(float a,float b){close(a,b,.00001f);}
    static void close(float a,float b,float epsilon){check(Math.abs(a-b)<=epsilon,a+" != "+b);}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
