package vpiso;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayDeque;
import org.lwjgl.opengl.*;
import zombie.core.textures.TextureDraw;

/** Native floor RGB and linear floor-height moments, independent of foreground artwork. */
public final class NativeGround {
    // PZO SSR owns unit15. The pinned native renderer uses low units; take the
    // highest available combined unit and restore it before handing back the frame.
    private static final int SLOT = 7;
    private static final int OCCLUSION = 6;
    private static final int RELIGHT=5, VALIDITY=4, FLOOR_SLOT=3;
    private static int unit;
    private static final Map<Integer, Surface> SURFACES = new HashMap<>();
    private static final Map<Integer, Surface> CHUNKS = new HashMap<>();
    private static final Map<Integer, int[]> TAGS = new HashMap<>();
    private static final java.nio.ByteBuffer COLOR_MASK=org.lwjgl.BufferUtils.createByteBuffer(4);
    private static boolean world, floor, occluder, grassMask;
    private static float floorLevel;
    // A two-dimensional owner code keeps its squared norm exact in RGBA32F.
    // Cached chunks retain references until their metadata is actually cleared.
    public static final int WALL_OWNERS=1024;
    public static final int SOURCE_OWNERS=65536;
    public static final int METADATA_FORMAT=2;
    private static final Map<String,Integer> WALL_KEYS=new HashMap<>();
    private static final String[] WALL_NAMES=new String[WALL_OWNERS];
    private static final int[] WALL_REFS=new int[WALL_OWNERS];
    private static final float[] WALL_DATA=new float[WALL_OWNERS*4];
    private static final float[] SOURCE_DATA=new float[SOURCE_OWNERS*12];
    private static final Map<String,Integer> SOURCE_KEYS=new HashMap<>();
    private static final String[] SOURCE_NAMES=new String[SOURCE_OWNERS];
    private static final int[] SOURCE_REFS=new int[SOURCE_OWNERS];
    private static final ArrayDeque<Integer> SOURCE_FREE=new ArrayDeque<>();
    private static int nextSource=2;
    private static boolean sourcesPending;
    private static final ArrayDeque<Integer> SOURCE_STACK=new ArrayDeque<>();
    private static int sourceOwner;
    private static final ArrayDeque<Integer> WALL_FREE=new ArrayDeque<>();
    private static int nextWall=2,wallOwner;
    private static long wallRevision;
    private static final long[] OWNER_REVISIONS=new long[193];

    /** Native room/roof lighting is already present; walls use their visible owner-square side. */
    public static boolean receivesSun(zombie.iso.IsoObject object) {
        Object square=GameAccess.call("zombie.iso.IsoObject","getSquare",object,new Class<?>[0]);
        return square!=null && (Boolean)GameAccess.call("zombie.iso.IsoGridSquare","isOutside",square,new Class<?>[0]);
    }
    private static Surface output;
    private static Surface samplingSource;
    private static int samplingProgram, samplingUnit;
    private static int outputFramebuffer, savedTexture, savedSampler, savedMaskTexture, savedMaskSampler, savedFloorTexture, savedFloorSampler;
    private static long frame;
    private static final int[][] RELIGHT_STATE=new int[3][8];
    private static final class Surface {
        int texture, occlusion, floorMoments, relight, validity, width, height, color;
        int minFilter=GL11.GL_NEAREST, magFilter=GL11.GL_NEAREST;
        boolean relightAttached, relightWriting, relightUsable, floorAttached, floorWriting, captureWriting;
        long frame;
        final Set<Integer> walls=new HashSet<>();
        final Set<Integer> sources=new HashSet<>();
    }
    private static final class Command extends TextureDraw.GenericDrawer {
        final int operation;
        final float level;
        final int wallX,wallY,wallAxis;
        final NativeSource source;
        Command(int operation) { this(operation,0); }
        Command(int operation,float level) { this(operation,level,0,0,0); }
        Command(int operation,float level,int x,int y,int axis) {
            this.operation=operation;this.level=level;wallX=x;wallY=y;wallAxis=axis;source=null;
        }
        Command(NativeSource value) {operation=8;level=0;wallX=wallY=wallAxis=0;source=value;}
        @Override public void render() {
            if (operation==0 || operation==5) begin(operation==5);
            else if (operation==3) finish();
            else if(world) {
                if(operation==8)sourceBegin(source);
                else if(operation==9)sourceEnd();
                else if(operation==4) setWall(true);
                else if(operation==7) {
                    setWall(true);wallOwner=retainWall(wallX,wallY,(int)level,wallAxis);
                    program(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                }
                else { floorLevel=level;setFloor(operation==1 || operation==6); }
            }
        }
    }
    private static final Command BEGIN=new Command(0), FLOOR=new Command(1), OBJECT=new Command(2), END=new Command(3);
    private static final Command WALL=new Command(4);
    private static final Command BEGIN_GRASS=new Command(5), SOURCE_END=new Command(9);
    private static final Command[] FLOORS=floors();
    private static Command[] floors() {
        Command[] values=new Command[64];
        for(int i=0;i<values.length;i++)values[i]=new Command(6,i-32);
        return values;
    }
    private static Command floorCommand(float level) {
        int index=(int)level+32;
        return index>=0 && index<FLOORS.length
                && Float.floatToRawIntBits(level)==Float.floatToRawIntBits((float)(index-32))
                ? FLOORS[index]:new Command(6,level);
    }

    public static void queueBegin() { queue(BEGIN); }
    public static void queueBegin(boolean mask) { queue(mask?BEGIN_GRASS:BEGIN); }
    public static void queueEnd() { queue(END); }
    public static void queueFloor(boolean value) { queue(value ? FLOOR : OBJECT); }
    /** Native accumulated snow is a floor overlay, not an anonymous model. */
    public static void queueFloor(float level) { queue(floorCommand(level)); }
    /** Immutable metadata follows the exact native floor draw, including render-square overrides. */
    public static void queueFloor(zombie.iso.IsoObject object) {
        Object square=GameAccess.call("zombie.iso.IsoObject","getRenderSquare",object,new Class<?>[0]);
        if(square==null) { queue(OBJECT);return; }
        queue(floorCommand(((Number)GameAccess.field("zombie.iso.IsoGridSquare","z",square)).floatValue()));
    }
    public static void queueWall(boolean value) { queue(value ? WALL : OBJECT); }
    public static void queueSource(NativeSource source) {queue(new Command(source));}
    public static void queueSourceEnd() {queue(SOURCE_END);}
    public static void sourceBegin(NativeSource source) {
        SOURCE_STACK.push(sourceOwner);
        sourceOwner=source==null || floor || wallOwner!=0?0:retainSource(source);
        program(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
    public static void sourceEnd() {
        sourceOwner=SOURCE_STACK.isEmpty()?0:SOURCE_STACK.pop();
        program(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
    /** The cutaway renderer often passes null direction; its actual shaper selects the face. */
    public static void queueWall(zombie.iso.IsoObject object,zombie.iso.IsoDirections direction,Object shaper) {
        int axis=shaper instanceof zombie.iso.sprite.shapers.WallShaperW?1
                :shaper instanceof zombie.iso.sprite.shapers.WallShaperN?2
                :direction==zombie.iso.IsoDirections.W?1:direction==zombie.iso.IsoDirections.N?2:0;
        Object square=GameAccess.call("zombie.iso.IsoObject","getRenderSquare",object,new Class<?>[0]);
        if(axis==0 || square==null) { queue(WALL);return; }
        queue(new Command(7,((Number)GameAccess.field("zombie.iso.IsoGridSquare","z",square)).floatValue(),
                ((Number)GameAccess.field("zombie.iso.IsoGridSquare","x",square)).intValue(),
                ((Number)GameAccess.field("zombie.iso.IsoGridSquare","y",square)).intValue(),axis));
    }
    public static void setFloor(boolean value) { floor=value; occluder=!value;wallOwner=0; program(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); }
    public static void setWall(boolean value) { floor=false; occluder=true;wallOwner=1; program(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); }
    private static int retainWall(int x,int y,int z,int axis) {
        Surface surface=SURFACES.get(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        if(surface==null)return 1;
        // Adjacent wall tiles on the same physical plane share an ID, so native
        // zoom filtering cannot turn their common edge into an unshadowed seam.
        String key=(axis==1?x:y)+":"+z+":"+axis;
        Integer id=WALL_KEYS.get(key);
        if(id==null) {
            if(WALL_FREE.isEmpty() && nextWall>=WALL_OWNERS)return 1; // known wall, unknown plane
            id=WALL_FREE.isEmpty()?nextWall++:WALL_FREE.removeFirst();
            WALL_KEYS.put(key,id);WALL_NAMES[id]=key;
            int at=id*4;WALL_DATA[at]=axis==1?x:0;WALL_DATA[at+1]=axis==2?y:0;
            WALL_DATA[at+2]=z;WALL_DATA[at+3]=axis;OWNER_REVISIONS[0]=++wallRevision;
        }
        if(surface.walls.add(id))WALL_REFS[id]++;
        return id;
    }
    private static void releaseWalls(Surface surface) {
        for(int id:surface.walls)if(--WALL_REFS[id]==0) {
            WALL_KEYS.remove(WALL_NAMES[id]);WALL_NAMES[id]=null;
            java.util.Arrays.fill(WALL_DATA,id*4,id*4+4,0f);WALL_FREE.add(id);OWNER_REVISIONS[0]=++wallRevision;
        }
        surface.walls.clear();
        for(int id:surface.sources)if(--SOURCE_REFS[id]==0) {
            // Negative entries retire after the current world pass. Cached
            // native draws can still name this ID before its source is redrawn.
            SOURCE_FREE.add(-id);sourcesPending=true;
        }
        surface.sources.clear();
    }
    private static void collectSources() {
        // Positive entries are already reusable. Rotating them every frame
        // only allocates boxed IDs; preserve the legacy queue pass when a
        // cached destination actually retires a source.
        if(!sourcesPending)return;
        sourcesPending=false;
        int count=SOURCE_FREE.size();
        for(int i=0;i<count;i++) {
            int queued=SOURCE_FREE.removeFirst();
            if(queued>=0) { SOURCE_FREE.addLast(queued);continue; }
            int id=-queued;
            if(SOURCE_REFS[id]!=0 || SOURCE_NAMES[id]==null)continue;
            SOURCE_KEYS.remove(SOURCE_NAMES[id]);SOURCE_NAMES[id]=null;
            for(int row=0;row<3;row++)java.util.Arrays.fill(SOURCE_DATA,(row*SOURCE_OWNERS+id)*4,(row*SOURCE_OWNERS+id)*4+4,0f);
            SOURCE_FREE.addLast(id);sourceRevision(id);
        }
    }
    public static long wallRevision() { return wallRevision; }
    public static void wallData(java.nio.FloatBuffer target) { target.put(WALL_DATA).flip(); }
    public static void ownerData(java.nio.FloatBuffer target) {target.put(WALL_DATA).put(SOURCE_DATA).flip();}
    public static long ownerRevision(int row) {return OWNER_REVISIONS[row];}
    /** Contiguous texture rows; IDs and full precision retain their original layout. */
    public static void ownerRows(java.nio.FloatBuffer target,int first,int count) {
        if(first==0) {target.put(WALL_DATA);first++;count--;}
        if(count>0)target.put(SOURCE_DATA,(first-1)*WALL_OWNERS*4,count*WALL_OWNERS*4);
        target.flip();
    }
    private static void sourceRevision(int id) {
        long revision=++wallRevision;
        for(int plane=0;plane<3;plane++)OWNER_REVISIONS[1+(id>>10)+plane*64]=revision;
    }
    private static int retainSource(NativeSource source) {
        Surface surface=SURFACES.get(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        if(surface==null)return 0;
        Integer id=SOURCE_KEYS.get(source.key);
        if(id==null) {
            boolean reusable=!SOURCE_FREE.isEmpty() && SOURCE_FREE.peekFirst()>0;
            if(!reusable && nextSource>=SOURCE_OWNERS)return 0;
            id=reusable?SOURCE_FREE.removeFirst():nextSource++;
            SOURCE_KEYS.put(source.key,id);SOURCE_NAMES[id]=source.key;
            int at=id*4;SOURCE_DATA[at]=source.x;SOURCE_DATA[at+1]=source.y;SOURCE_DATA[at+2]=source.level;SOURCE_DATA[at+3]=source.kind;
            for(int axis=0;axis<3;axis++) {
                SOURCE_DATA[(SOURCE_OWNERS+id)*4+axis]=source.bounds[axis];
                SOURCE_DATA[(2*SOURCE_OWNERS+id)*4+axis]=source.bounds[axis+3];
            }
            SOURCE_DATA[(SOURCE_OWNERS+id)*4+3]=source.owner[0];SOURCE_DATA[(2*SOURCE_OWNERS+id)*4+3]=source.owner[1];
            sourceRevision(id);
        }
        if(surface.sources.add(id))SOURCE_REFS[id]++;
        return id;
    }
    private static void queue(Command drawer) {
        GameAccess.call("zombie.core.SpriteRenderer", "drawGeneric",
                GameAccess.field("zombie.core.SpriteRenderer", "instance", null),
                new Class<?>[]{GameAccess.type("zombie.core.textures.TextureDraw$GenericDrawer")}, drawer);
    }

    public static void begin() { begin(false); }
    public static void begin(boolean mask) {
        unit=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)-1;
        world=true; floor=false; floorLevel=0; wallOwner=0;sourceOwner=0;SOURCE_STACK.clear();occluder=true; grassMask=mask;
        samplingSource=null;
        frame++;
        TAGS.clear();
        for(int i=0;i<3;i++) {
            int slot=FLOOR_SLOT+i;int[] state=RELIGHT_STATE[i];
            state[0]=GL30.glIsEnabledi(GL11.GL_BLEND,slot)?1:0;
            state[1]=GL30.glGetIntegeri(GL14.GL_BLEND_SRC_RGB,slot);state[2]=GL30.glGetIntegeri(GL14.GL_BLEND_DST_RGB,slot);
            state[3]=GL30.glGetIntegeri(GL14.GL_BLEND_SRC_ALPHA,slot);state[4]=GL30.glGetIntegeri(GL14.GL_BLEND_DST_ALPHA,slot);
            state[5]=GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_RGB,slot);state[6]=GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_ALPHA,slot);
            GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,slot,COLOR_MASK);
            state[7]=0;for(int c=0;c<4;c++)if(COLOR_MASK.get(c)!=0)state[7]|=1<<c;
        }
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        savedTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);savedSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit);
        if(grassMask) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-1);
            savedMaskTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);savedMaskSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit-1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-2);
            savedFloorTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);savedFloorSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit-2);
        }
        GL13.glActiveTexture(active);
        outputFramebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        output=attach(true);
        program(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
    public static void finish() {
        if(!world) return;
        if (output!=null) {
            int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if(fbo==outputFramebuffer || output.relightAttached || output.floorAttached) {
                if(fbo!=outputFramebuffer)GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,outputFramebuffer);
                buffers(output,false);
                if(output.floorAttached) {
                    GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+FLOOR_SLOT,GL11.GL_TEXTURE_2D,0,0);
                    output.floorAttached=false;
                }
                if(output.relightAttached) {
                    GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+RELIGHT,GL11.GL_TEXTURE_2D,0,0);
                    GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+VALIDITY,GL11.GL_TEXTURE_2D,0,0);
                    output.relightAttached=false;
                }
                if(fbo!=outputFramebuffer)GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            }
        }
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,savedTexture);GL33.glBindSampler(unit,savedSampler);
        if(grassMask) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,savedMaskTexture);GL33.glBindSampler(unit-1,savedMaskSampler);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,savedFloorTexture);GL33.glBindSampler(unit-2,savedFloorSampler);
        }
        GL13.glActiveTexture(active);
        for(int i=0;i<3;i++) {
            int slot=FLOOR_SLOT+i;int[] state=RELIGHT_STATE[i];
            if(state[0]!=0)GL30.glEnablei(GL11.GL_BLEND,slot);else GL30.glDisablei(GL11.GL_BLEND,slot);
            GL40.glBlendFuncSeparatei(slot,state[1],state[2],state[3],state[4]);
            GL40.glBlendEquationSeparatei(slot,state[5],state[6]);
            GL30.glColorMaski(slot,(state[7]&1)!=0,(state[7]&2)!=0,(state[7]&4)!=0,(state[7]&8)!=0);
        }
        world=false; floor=false; grassMask=false; output=null;sourceOwner=0;SOURCE_STACK.clear();
        samplingSource=null;
        collectSources();
    }
    public static int texture() {
        return world && output!=null ? output.texture : 0;
    }
    public static int occlusionTexture() { return world && grassMask && output!=null ? output.occlusion : 0; }
    public static int floorTexture() {return world && grassMask && output!=null && output.floorAttached?output.floorMoments:0;}
    public static int relightTexture() { return world && output!=null && output.relightAttached && output.relightUsable?output.relight:0; }
    public static int relightValidity() { return world && output!=null && output.relightAttached && output.relightUsable?output.validity:0; }
    public static boolean active() { return world; }

    public static void chunkBegin(Object chunk, boolean clear) {
        if (!world) return;
        Surface surface=attach(clear);
        if (surface==null) return;
        surface.frame=frame;
        Object depth=GameAccess.field(chunk.getClass().getName(), "depth", chunk);
        int id=((Number)GameAccess.call("zombie.core.textures.Texture", "getID", depth, new Class<?>[0])).intValue();
        CHUNKS.put(id,surface);
    }
    public static void chunkEnd() {
        if (!world) return;
        Surface surface=SURFACES.get(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        if (surface!=null) {
            buffers(surface,false);
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface.texture);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            if(grassMask) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface.occlusion);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                if(surface.floorMoments!=0) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface.floorMoments);
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                }
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);GL13.glActiveTexture(active);
        }
    }
    public static void chunkShader(Object draw) {
        if (!world) return;
        int framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if(framebuffer!=outputFramebuffer) {
            Surface destination=SURFACES.get(framebuffer);
            if(destination==null || destination.frame!=frame) {
                destination=attach(true);
                if(destination!=null) {
                    destination.frame=frame;
                    int type=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                    if(type==GL11.GL_TEXTURE) {
                        int target=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                        CHUNKS.put(target,destination);
                    }
                }
            }
        }
        Object depth=GameAccess.field("zombie.core.textures.TextureDraw", "tex1", draw);
        int id=depth==null ? 0 : ((Number)GameAccess.call("zombie.core.textures.Texture", "getID", depth, new Class<?>[0])).intValue();
        Surface surface=CHUNKS.get(id);
        Surface destination=SURFACES.get(framebuffer);
        if(surface!=null && destination!=null && surface!=destination) {
            for(int owner:surface.walls)if(destination.walls.add(owner))WALL_REFS[owner]++;
            for(int owner:surface.sources)if(destination.sources.add(owner))SOURCE_REFS[owner]++;
        }
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int location=GL20.glGetUniformLocation(program,"vpIsoGroundTexture");
        if(location<0) return;
        samplingSource=surface; samplingProgram=program;
        int diffuse=GL20.glGetUniformLocation(program,"DIFFUSE");
        samplingUnit=diffuse<0?-1:GL20.glGetUniformi(program,diffuse);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GL33.glBindSampler(unit,0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface==null?0:surface.texture);
        GL20.glUniform1i(location,unit);
        if(grassMask) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-1);
            GL33.glBindSampler(unit-1,0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface==null?0:surface.occlusion);
            int mask=GL20.glGetUniformLocation(program,"vpIsoOcclusionTexture");
            if(mask>=0)GL20.glUniform1i(mask,unit-1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-2);GL33.glBindSampler(unit-2,0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface==null?0:surface.floorMoments);
            int floorMap=GL20.glGetUniformLocation(program,"vpIsoFloorTexture");
            if(floorMap>=0)GL20.glUniform1i(floorMap,unit-2);
        }
        GL13.glActiveTexture(active);
    }
    public static void program(int program) {
        if(!world)return;
        Surface surface=SURFACES.get(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        if(program==0) {
            if(surface!=null){surface.captureWriting=false;surface.floorWriting=false;surface.relightWriting=false;buffers(surface,true);}
            return;
        }
        int[] locations=TAGS.computeIfAbsent(program,p->new int[]{GL20.glGetUniformLocation(p,"vpIsoGroundTag"),
                GL20.glGetUniformLocation(p,"vpIsoGrassOccluder"),GL20.glGetUniformLocation(p,"vpIsoGrassMaskOn"),
                GL20.glGetUniformLocation(p,"vpIsoFloorLevel"),GL20.glGetUniformLocation(p,"vpIsoMetadataStraight"),
                GL20.glGetUniformLocation(p,"vpIsoRelightWrite"),GL20.glGetUniformLocation(p,"vpIsoWallOwner"),
                GL20.glGetUniformLocation(p,"vpIsoSourceOwner"),GL20.glGetUniformLocation(p,"vpIsoFloorWrite"),
                0,0,0,0,0,0,0,0,0,0});
        // These values belong only to our auxiliary outputs. Keep native blend,
        // framebuffer and draw-buffer reads live; cache no original draw state.
        int initialized=locations[18];
        for(int index=0;index<8;index++) {
            if(locations[index]<0)continue;
            float value=switch(index) {
                case 0 -> floor?1f:0f;
                case 1 -> occluder?1f:0f;
                case 2 -> grassMask?1f:0f;
                case 3 -> floorLevel;
                case 4 -> GL30.glIsEnabledi(GL11.GL_BLEND,0)
                        && GL30.glGetIntegeri(GL14.GL_BLEND_SRC_RGB,0)==GL11.GL_SRC_ALPHA?1f:0f;
                case 5 -> surface!=null && surface.relightAttached?1f:0f;
                case 6 -> wallOwner;
                default -> !floor && wallOwner==0?sourceOwner:0;
            };
            int bits=Float.floatToRawIntBits(value),bit=1<<index;
            if((initialized&bit)==0 || locations[9+index]!=bits) {
                GL20.glUniform1f(locations[index],value);locations[9+index]=bits;initialized|=bit;
            }
        }
        locations[18]=initialized;
        if(surface!=null)surface.relightWriting=locations[5]>=0;
        if(surface!=null) {
            // Legacy gl_FragColor broadcasts RGB into every enabled MRT. Only
            // capture programs that actually provide our auxiliary outputs.
            surface.captureWriting=locations[2]>=0;
            surface.floorWriting=grassMask && locations[8]>=0 && surface.floorAttached;
            if(locations[8]>=0) {
                float value=surface.floorWriting?1f:0f;int bits=Float.floatToRawIntBits(value);
                if((locations[18]&256)==0 || locations[17]!=bits) {
                    GL20.glUniform1f(locations[8],value);locations[17]=bits;locations[18]|=256;
                }
            }
            buffers(surface,true);
        }
    }
    public static void beforeDraw() {
        if(!world) return;
        int current=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        program(current);
        if(current==samplingProgram && samplingSource!=null && samplingUnit>=0) syncSampling();
        Surface surface=SURFACES.get(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        if(surface!=null) {
            // Generic source commands can run before a batched native draw
            // switches its framebuffer. The destination that stores the IDs
            // must retain them, independently of the command's initial target.
            if(surface.captureWriting) {
                if(!floor && wallOwner==0 && sourceOwner>=2 && surface.sources.add(sourceOwner))SOURCE_REFS[sourceOwner]++;
                if(wallOwner>=2 && surface.walls.add(wallOwner))WALL_REFS[wallOwner]++;
            }
            // An unknown foreground shader cannot erase captured floor validity.
            // Fall back for this frame instead of relighting through its artwork.
            if(surface.relightAttached && !surface.relightWriting)surface.relightUsable=false;
            // All auxiliary attachments mirror the same native draw. Snapshot its
            // state once per draw; never reuse it across a later native state change.
            boolean blend=GL30.glIsEnabledi(GL11.GL_BLEND,0);
            int srcRGB=GL30.glGetIntegeri(GL14.GL_BLEND_SRC_RGB,0),dstRGB=GL30.glGetIntegeri(GL14.GL_BLEND_DST_RGB,0);
            int srcAlpha=GL30.glGetIntegeri(GL14.GL_BLEND_SRC_ALPHA,0),dstAlpha=GL30.glGetIntegeri(GL14.GL_BLEND_DST_ALPHA,0);
            int equationRGB=GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_RGB,0),equationAlpha=GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_ALPHA,0);
            GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,0,COLOR_MASK);
            boolean red=COLOR_MASK.get(0)!=0,green=COLOR_MASK.get(1)!=0,blue=COLOR_MASK.get(2)!=0,alpha=COLOR_MASK.get(3)!=0;
            for(int slot=FLOOR_SLOT;slot<=SLOT;slot++) {
                if(slot==FLOOR_SLOT && !surface.floorAttached || (slot==VALIDITY || slot==RELIGHT) && !surface.relightAttached
                        || slot==OCCLUSION && !grassMask)continue;
                if(blend) GL30.glEnablei(GL11.GL_BLEND,slot); else GL30.glDisablei(GL11.GL_BLEND,slot);
                GL40.glBlendFuncSeparatei(slot,srcRGB,dstRGB,slot==OCCLUSION?GL11.GL_ONE:srcAlpha,
                        slot==OCCLUSION?GL11.GL_ONE_MINUS_SRC_ALPHA:dstAlpha);
                GL40.glBlendEquationSeparatei(slot,equationRGB,equationAlpha);
                GL30.glColorMaski(slot,red,green,blue,alpha);
            }
        }
    }
    /** Texture.bind() applies native zoom filters after chunkShader() has run. */
    private static void syncSampling() {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+samplingUnit);
            if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=samplingSource.color)return;
            int min=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER);
            int mag=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER);
            int sampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,samplingUnit);
            // Contribution RGB and linear owner moments must cover the same
            // texels and mip levels as DIFFUSE, including PZO's explicit LODs.
            for(int i=0;i<(grassMask?3:1);i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit-i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,i==0?samplingSource.texture:i==1?samplingSource.occlusion:samplingSource.floorMoments);
                GL33.glBindSampler(unit-i,sampler);
                if(samplingSource.minFilter!=min)GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,min);
                if(samplingSource.magFilter!=mag)GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,mag);
            }
            samplingSource.minFilter=min; samplingSource.magFilter=mag;
        } finally {GL13.glActiveTexture(active);}
    }
    public static void destroy(Object framebuffer) {
        int id=((Number)GameAccess.call("zombie.core.textures.TextureFBO","getBufferId",framebuffer,new Class<?>[0])).intValue();
        if(id==0) return;
        GameAccess.call("zombie.core.opengl.RenderThread","invokeOnRenderContext",null,new Class<?>[]{Runnable.class},
                (Runnable)()->release(id));
    }
    public static void release(int framebuffer) {
        Surface surface=SURFACES.remove(framebuffer);
        if(surface==null) return;
        if(samplingSource==surface)samplingSource=null;
        CHUNKS.values().removeIf(value->value==surface);
        releaseWalls(surface);
        if(!world)collectSources();
        GL11.glDeleteTextures(surface.texture);
        GL11.glDeleteTextures(surface.occlusion);GL11.glDeleteTextures(surface.floorMoments);
        GL11.glDeleteTextures(surface.relight);GL11.glDeleteTextures(surface.validity);
    }

    private static Surface attach(boolean clear) {
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if(fbo==0 || GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)<=SLOT) return null;
        int kind=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int color=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            int w,h;
            if(kind==GL11.GL_TEXTURE) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
                w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
                h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            } else return null;
            Surface surface=SURFACES.computeIfAbsent(fbo,k->new Surface());
            if(surface.texture==0 || surface.width!=w || surface.height!=h || surface.color!=color) {
                if(surface.texture!=0) GL11.glDeleteTextures(surface.texture);
                if(surface.occlusion!=0) GL11.glDeleteTextures(surface.occlusion);GL11.glDeleteTextures(surface.floorMoments);
                GL11.glDeleteTextures(surface.relight);GL11.glDeleteTextures(surface.validity);
                surface.relight=surface.validity=0;surface.relightAttached=false;
                surface.occlusion=surface.floorMoments=0;surface.floorAttached=false;
                surface.texture=GL11.glGenTextures();surface.width=w;surface.height=h;surface.color=color;
                surface.minFilter=surface.magFilter=GL11.GL_NEAREST;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface.texture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                clear=true;
            }
            if(grassMask && surface.occlusion==0) {
                surface.occlusion=GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,surface.occlusion);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,surface.minFilter);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,surface.magFilter);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                clear=true;
            }
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+SLOT,GL11.GL_TEXTURE_2D,surface.texture,0);
            if(grassMask)GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+OCCLUSION,GL11.GL_TEXTURE_2D,surface.occlusion,0);
            if(grassMask && !surface.floorAttached
                    && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+FLOOR_SLOT)==GL11.GL_NONE
                    && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+FLOOR_SLOT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_NONE) {
                if(surface.floorMoments==0) {
                    surface.floorMoments=relightTexture(GL30.GL_RGBA32F,GL11.GL_RGBA,w,h);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,surface.minFilter);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,surface.magFilter);
                    clear=true;
                }
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+FLOOR_SLOT,GL11.GL_TEXTURE_2D,surface.floorMoments,0);
                surface.floorAttached=true;
            }
            if(NativeGroundShaders.PZO_FINAL && fbo==outputFramebuffer && !surface.relightAttached
                    && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+RELIGHT)==GL11.GL_NONE
                    && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+VALIDITY)==GL11.GL_NONE
                    && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+RELIGHT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_NONE
                    && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+VALIDITY,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_NONE) {
                if(surface.relight==0) {
                    surface.relight=relightTexture(GL30.GL_RGBA16F,GL11.GL_RGBA,w,h);
                    surface.validity=relightTexture(GL30.GL_R16F,GL11.GL_RED,w,h);
                }
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+RELIGHT,GL11.GL_TEXTURE_2D,surface.relight,0);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+VALIDITY,GL11.GL_TEXTURE_2D,surface.validity,0);
                surface.relightAttached=true;
                surface.relightWriting=true;
                surface.relightUsable=true;
            }
            surface.captureWriting=true;surface.floorWriting=surface.floorAttached;
            buffers(surface,true);
            if(clear) {
                releaseWalls(surface);
                boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL30.glColorMaski(SLOT,true,true,true,true);
                GL30.glClearBufferfv(GL11.GL_COLOR,SLOT,new float[]{0,0,0,0});
                if(grassMask) {
                    GL30.glColorMaski(OCCLUSION,true,true,true,true);
                    GL30.glClearBufferfv(GL11.GL_COLOR,OCCLUSION,new float[]{0,0,0,0});
                }
                if(surface.floorAttached) {
                    GL30.glColorMaski(FLOOR_SLOT,true,true,true,true);
                    GL30.glClearBufferfv(GL11.GL_COLOR,FLOOR_SLOT,new float[]{0,0,0,0});
                }
                if(surface.relightAttached)for(int slot=VALIDITY;slot<=RELIGHT;slot++) {
                    GL30.glColorMaski(slot,true,true,true,true);
                    GL30.glClearBufferfv(GL11.GL_COLOR,slot,new float[]{0,0,0,0});
                }
                if(scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            return surface;
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);GL13.glActiveTexture(active); }
    }
    private static void buffers(Surface surface,boolean enabled) {
        int target=enabled && surface.captureWriting?GL30.GL_COLOR_ATTACHMENT0+SLOT:GL11.GL_NONE;
        int mask=grassMask?(enabled && surface.captureWriting?GL30.GL_COLOR_ATTACHMENT0+OCCLUSION:GL11.GL_NONE):GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+OCCLUSION);
        boolean relight=surface.relightAttached;
        int floor=enabled && surface.floorWriting?GL30.GL_COLOR_ATTACHMENT0+FLOOR_SLOT:GL11.GL_NONE;
        boolean writing=enabled && surface.relightWriting;
        if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+SLOT)==target && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+OCCLUSION)==mask
                && (!surface.floorAttached || GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+FLOOR_SLOT)==floor)
                && (!relight || GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+RELIGHT)==(writing?GL30.GL_COLOR_ATTACHMENT0+RELIGHT:GL11.GL_NONE)
                    && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+VALIDITY)==(writing?GL30.GL_COLOR_ATTACHMENT0+VALIDITY:GL11.GL_NONE))) return;
        int[] buffers=new int[SLOT+1];
        for(int i=0;i<SLOT;i++) buffers[i]=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i);
        buffers[SLOT]=target;
        buffers[OCCLUSION]=mask;
        if(surface.floorAttached)buffers[FLOOR_SLOT]=floor;
        if(relight)for(int slot=VALIDITY;slot<=RELIGHT;slot++)buffers[slot]=writing?GL30.GL_COLOR_ATTACHMENT0+slot:GL11.GL_NONE;
        GL20.glDrawBuffers(buffers);
    }
    private static int relightTexture(int internal,int format,int w,int h) {
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,GL11.GL_FLOAT,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }
    private NativeGround() {}
}

