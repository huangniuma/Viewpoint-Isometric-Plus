import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.core.Frame;
import viewpoint.platform.GlProgram;
import vpiso.*;
import zombie.iso.*;
import zombie.iso.sprite.*;

/** Actual attached child -> parent source table -> GPU wall mask, including protruding model faces. */
public final class WallAttachment114Probe {
    static final int SIDE=128;
    static boolean baseline;
    static int checks;
    static Method sample;
    public static void main(String[] args)throws Exception {
        baseline=args.length>1 && args[1].equals("baseline");
        zombie.core.random.RandStandard.INSTANCE.init();
        sample=NativeObjectState.class.getDeclaredMethod("sample",Object.class,float.class);sample.setAccessible(true);
        for(int x:new int[]{4,-4,10780})for(int y:new int[]{7,-1,9767})for(int face:new int[]{1,2,3}) {
            NativeObjectState state=new NativeObjectState();Object result=sample.invoke(state,wall(x,y,face,true),.65f);
            int id=(Integer)GameAccess.field("vpiso.NativeObjectState$Sample","id",result);
            require((id&65535)==0,"No-pick wall acquired a mouse target");
            if(!baseline)require((id>>>16)==((face<<6|((y&7)*8+(x&7)))+1),"Wrong parent tile or face");
            require((Float)GameAccess.field("vpiso.NativeObjectState$Sample","alpha",result)==.65f,"Parent alpha changed");checks++;
        }
        Object ordinary=sample.invoke(new NativeObjectState(),wall(4,7,2,false),1f);
        require((Integer)GameAccess.field("vpiso.NativeObjectState$Sample","id",ordinary)==0,"Unattached wall tagged");
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(SIDE,SIDE,"114 attached wall",0,0);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,GL30.glGenFramebuffers());
            int color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,SIDE,SIDE,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"FBO");
            GL11.glViewport(0,0,SIDE,SIDE);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(0,0,0,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+142);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,GL11.glGenTextures());
            ByteBuffer rule=BufferUtils.createByteBuffer(4).put((byte)255).put((byte)(700/4)).put((byte)(512/4)).put((byte)224).flip();
            GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA8,1,1,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rule);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+NativeWallCutaway.UNIT);
            Method load=NativeWallCutaway.class.getDeclaredMethod("loadMask",Path.class);load.setAccessible(true);load.invoke(null,Path.of(args[0],"media/wallcutaways.png"));
            String vertex="#version 430\n"+GameAccess.field("vpiso.NativeObjectShaders","VERTEX",null)+"\nflat out vec4 parent;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);vpIsoObjectInfo=vpIsoSource(2,0);"
                +(baseline?"parent=vec4(0);":"int code=int(vpIsoWallCode)-1,tile=code&63;parent=vec4(-float(tile&7),0,-float(tile>>3),float(code>>6));")+"}";
            String fragment="#version 430\n"+NativeViewData.GLSL+NativeWallCutaway.GLSL+"\nflat in vec4 parent;uniform int face;uniform float away;out vec4 color;void main(){float t=gl_FragCoord.x/128.0,y=gl_FragCoord.y/128.0*2.4494896;vec3 p=vec3(-4,0,-7)+(face==1?vec3(-t,y,-away):vec3(-away,y,-t));vec3 n=face==1?vec3(0,0,1):vec3(1,0,0);vpIsoCutWall(p,vec3(0,y,0),n,0"+(baseline?"":",parent")+");color=vec4(1);}";
            GlProgram program=GlProgram.fromSources("114 attached wall",new GlProgram.Sources(vertex,null,null,null,fragment),Map.of(),Map.of("color",0),List.of());
            for(int face:new int[]{1,2}) {
                NativeObjectState state=new NativeObjectState();Frame frame=new Frame();state.beginCapture(frame);NativeObjectState.endCapture();
                Object owner=sample.invoke(state,wall(4,7,face,true),1f);
                ((Map<Integer,Object>)GameAccess.field("vpiso.NativeObjectState","models",state)).put(0,owner);
                IsometricBridge.modelRendering(frame,IsometricEffects.DEFAULT);state.beginRender();NativeWallCutaway.begin();
                try {
                    NativeObjectState.prepared(frame.scene);program.use();NativeObjectState.onUse(program);NativeWallCutaway.onUse(program);
                    program.setInt("vpIsoNativeTiles",142);program.set("vpIsoNativeOrigin",4f,7f,0f);program.set("vpIsoNativeSize",1f,1f,1f);program.set("vpIsoNativeCam",0f,0f,0f);program.set("vpIsoNativeOn",1f);program.setInt("face",face);
                    double planar=coverage(program,0);require(planar>.03 && planar<.15,"Unexpected native mask "+planar);
                    for(float away:new float[]{.03f,.1f,.24f,.49f}) {
                        double covered=coverage(program,away);
                        if(baseline && away>.08f)require(covered>.99,"Negative control no longer reproduces protruding art");
                        if(!baseline)require(Math.abs(covered-planar)<.001,"Attachment escaped its parent mask at "+away+": "+covered+" vs "+planar);checks++;
                    }
                    program.set("vpIsoNativeOn",0f);require(coverage(program,.24f)>.99,"Ordinary VP guard");
                } finally {NativeWallCutaway.end();state.endRender();IsometricBridge.rendering(null);}
            }
            require(GL11.glGetError()==0,"OpenGL error");program.delete();
            System.out.println("WALLATTACH114 "+(baseline?"NEGATIVE CONTROL":"PASS")+" checks="+checks+"; actual parent IsoObject + WallOverlay-only child, unchanged alpha/no-picking, signed world coordinates, production upload/source decode, native N/W masks at 0..0.49 displacement, VP-disabled guard; no live visual claim.");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static IsoObject wall(int x,int y,int faces,boolean child)throws Exception {
        IsoGridSquare square=(IsoGridSquare)ModelCutawayProbe.allocate(IsoGridSquare.class);square.x=x;square.y=y;square.z=0;
        IsoSprite sprite=new IsoSprite();sprite.cutN=(faces&1)!=0;sprite.cutW=(faces&2)!=0;
        IsoObject wall=new IsoObject(null,square,sprite);wall.noPicking=true;
        if(child){IsoSprite drawing=new IsoSprite();drawing.name="location_community_school_02_32";drawing.getProperties().set(zombie.iso.SpriteDetails.IsoFlagType.WallOverlay);IsoSpriteInstance instance=new IsoSpriteInstance();instance.parentSprite=drawing;GameAccess.set("zombie.iso.IsoObject","attachedAnimSprite",wall,new ArrayList<>(List.of(instance)));}
        return wall;
    }
    static double coverage(GlProgram program,float away) {program.set("away",away);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);ByteBuffer p=BufferUtils.createByteBuffer(SIDE*SIDE*4);GL11.glReadPixels(0,0,SIDE,SIDE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);int n=0;for(int i=0;i<SIDE*SIDE;i++)if((p.get(i*4)&255)>127)n++;return n/(double)(SIDE*SIDE);}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
