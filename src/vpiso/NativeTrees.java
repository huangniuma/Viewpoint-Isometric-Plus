package vpiso;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import viewpoint.core.Frame;
import viewpoint.render.FrameContext;
import viewpoint.render.WorldRenderer;
import zombie.core.textures.TextureDraw;

/** Original tree drawing and cutaways; VP retains the complete tree shadow geometry. */
public final class NativeTrees extends TextureDraw.GenericDrawer {
    private static final String BATCH="zombie.iso.fboRenderChunk.FBORenderTrees";
    private static final String CELL="zombie.iso.fboRenderChunk.FBORenderCell";
    private static final Class<?>[] NONE=new Class<?>[0];
    private final IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();
    private final List<Object> trees=new ArrayList<>();
    private final NativeTreeSort sort=new NativeTreeSort();
    private final java.util.Set<Object> highlighted=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final java.util.Set<NearVegetationCompat.Owner> modeled=new java.util.HashSet<>();
    private final java.util.Set<Object> modelRecords=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private Frame frame;
    private Object batch;
    private int hidden,records;
    public RotationCamera.Snapshot rotation;

    public void reset() { release(); seen.clear(); trees.clear(); modeled.clear(); frame=null; hidden=records=0; }

    public void prepare(Frame value) { frame=value;NearVegetationCompat.owners(value,modeled); }

    public void add(Object square) {
        List<?> objects=(List<?>)GameAccess.call("zombie.iso.IsoGridSquare","getObjects",square,NONE);
        Class<?> treeType=GameAccess.type("zombie.iso.objects.IsoTree");
        for (Object object:objects) if (treeType.isInstance(object) && seen.put(object,true)==null) trees.add(object);
    }

    public void capture(Object renderer,int player) {
        RotationCamera.Snapshot previousRotation = RotationBounds.begin(rotation);
        try { captureNative(renderer, player); }
        finally { RotationBounds.end(previousRotation); }
    }

    private void captureNative(Object renderer,int player) {
        if (trees.isEmpty()) return;
        if (!(Boolean)GameAccess.field("zombie.core.PerformanceSettings","fboRenderChunk",null))
            throw new IllegalStateException("Native trees require the FBO renderer");
        Object oldCurrent=GameAccess.field(BATCH,"current",null);
        boolean oldTranslucent=(Boolean)GameAccess.field(CELL,"renderTranslucentOnly",renderer);
        GameAccess.staticCall(BATCH,"startFrame");
        batch=GameAccess.staticCall(BATCH,"alloc");
        GameAccess.call(BATCH,"init",batch,NONE);
        @SuppressWarnings("unchecked")
        List<Object> captured=(List<Object>)GameAccess.field(BATCH,"trees",batch);
        // Native transparent crowns still write depth. Submit far roots first so a
        // faded foreground crown cannot reject a rear crown before it is blended.
        // IsoDepthHelper's fixed-view depth is affine in -(x+y+2*z).
        sort.sort(trees,rotation);
        try {
            GameAccess.set(BATCH,"current",null,batch);
            GameAccess.set(CELL,"renderTranslucentOnly",renderer,true);
            for (Object tree:trees) {
                Object square=GameAccess.call("zombie.iso.IsoObject","getSquare",tree,NONE);
                if (!(Boolean)GameAccess.call(CELL,"shouldRenderSquare",renderer,
                        new Class<?>[]{GameAccess.type("zombie.iso.IsoGridSquare")},square)) continue;
                boolean translucent=(Boolean)GameAccess.call(CELL,"isTranslucentTree",renderer,
                        new Class<?>[]{GameAccess.type("zombie.iso.IsoObject")},tree);
                if (rotation != null) translucent = RotationTrees.obstructs(rotation, tree);
                GameAccess.set("zombie.iso.objects.IsoTree","renderFlag",tree,translucent);
                if (translucent) hidden++;
                float target=((Number)GameAccess.call(CELL,"calculateObjectTargetAlpha_NotDoorOrWall",renderer,
                        new Class<?>[]{GameAccess.type("zombie.iso.IsoObject")},tree)).floatValue();
                GameAccess.call("zombie.iso.IsoObject","setTargetAlpha",tree,
                        new Class<?>[]{int.class,float.class},player,target);
                Object color=GameAccess.call(CELL,"sanitizeLightInfo",renderer,
                        new Class<?>[]{int.class,GameAccess.type("zombie.iso.IsoGridSquare")},player,square);
                // The public tree entry otherwise returns before producing a base sprite when highlighted.
                byte highlight=((Number)GameAccess.field("zombie.iso.IsoObject","ppfHighlighted",tree)).byteValue();
                if ((highlight & (1<<player))!=0) {
                    color=GameAccess.call("zombie.iso.IsoObject","getHighlightColor",tree,new Class<?>[]{int.class},player);
                    GameAccess.set("zombie.iso.objects.IsoTree","chopTreeHighlighted",null,tree);
                }
                try {
                    int first=captured.size();
                    GameAccess.set("zombie.iso.IsoObject","ppfHighlighted",tree,(byte)0);
                    GameAccess.call("zombie.iso.objects.IsoTree","render",tree,
                            new Class<?>[]{float.class,float.class,float.class,GameAccess.type("zombie.core.textures.ColorInfo"),
                                    boolean.class,boolean.class,GameAccess.type("zombie.core.opengl.Shader")},
                            squareCoordinate(square,"x"),squareCoordinate(square,"y"),squareCoordinate(square,"z"),color,true,false,null);
                    if(!modeled.isEmpty() && modeled.contains(NearVegetationCompat.owner(tree,square))) {
                        float crown=1f;
                        for(int i=first;i<captured.size();i++) {
                            Object record=captured.get(i);
                            if((Boolean)GameAccess.field(BATCH+"$Tree","transparent",record))
                                crown=Math.min(crown,((Number)GameAccess.field(BATCH+"$Tree","cutawayAlpha",record)).floatValue());
                            modelRecords.add(record);
                        }
                        NativeObjectState.treeCrown(frame,tree,crown);
                    }
                    if((highlight & (1<<player))!=0)for(int i=first;i<captured.size();i++)highlighted.add(captured.get(i));
                } finally { GameAccess.set("zombie.iso.IsoObject","ppfHighlighted",tree,highlight); }
            }
            if(rotation==null) {
                // DepthStagger is assigned by the native producer, after root sorting.
                // Same-row neighbours can change its offset without changing alpha.
                // Sort the completed records by the depth the native draw will use.
                int sx=(int)Math.floor(((Number)GameAccess.field(BATCH,"playerX",batch)).floatValue());
                int sy=(int)Math.floor(((Number)GameAccess.field(BATCH,"playerY",batch)).floatValue());
                var depths=new IdentityHashMap<Object,Float>();
                String type=BATCH+"$Tree";
                for(Object record:captured) {
                    float x=((Number)GameAccess.field(type,"x",record)).floatValue()-.5f;
                    float y=((Number)GameAccess.field(type,"y",record)).floatValue()-.5f;
                    float z=((Number)GameAccess.field(type,"z",record)).floatValue();
                    float offset=((Number)GameAccess.field(type,"depthOffset",record)).floatValue();
                    depths.put(record,NativeVision.sample(sx,sy,x,y,z)+offset*.0002f);
                }
                captured.sort(Comparator.comparingDouble((Object record)->-depths.get(record)));
            }
            records=captured.size();
        } catch (Throwable error) { release(); throw error; }
        finally {
            GameAccess.set(BATCH,"current",null,oldCurrent);
            GameAccess.set(CELL,"renderTranslucentOnly",renderer,oldTranslucent);
        }
    }

    private static float squareCoordinate(Object square,String field) {
        return ((Number)GameAccess.field("zombie.iso.IsoGridSquare",field,square)).floatValue();
    }

    public void queue(Object sprites) {
        if (batch!=null) GameAccess.call("zombie.core.SpriteRenderer","drawGeneric",sprites,
                new Class<?>[]{GameAccess.type("zombie.core.textures.TextureDraw$GenericDrawer")},this);
    }

    /** Keep the native producer/cutaways, but let the selected VP pack finish the trees. */
    public void renderWorld(Frame frame) {
        if (batch == null || records == modelRecords.size()) return;
        FrameContext context = (FrameContext) GameAccess.field("viewpoint.render.WorldRenderer", "frame", null);
        int output = ((Number) GameAccess.field("viewpoint.render.FrameContext", "outputDrawFbo", context)).intValue();
        int[] viewport = (int[]) GameAccess.field("viewpoint.render.FrameContext", "viewport", context);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        // This draw-only camera also covers yaw zero. Capture must keep the original
        // null rotation, otherwise its native obstruction test would be replaced.
        RotationCamera.Snapshot previous = rotation;
        rotation = new RotationCamera.Snapshot(context.jitteredProjection, context.view,
                frame.camX, frame.camY, frame.camZ, context.width, context.height, 1f, 0f);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        try {
            WorldRenderer.bindTarget();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, output);
            // Only the player's native transparency stencil is copied. World depth
            // stays in VP coordinates, shared with the tree draw-only camera.
            GL30.glBlitFramebuffer(viewport[0], viewport[1], viewport[0] + viewport[2], viewport[1] + viewport[3],
                    0, 0, context.width, context.height, GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST);
            try {NativeTreeLighting.begin(context,highlighted);render();}
            finally {NativeTreeLighting.end();}
        } finally {
            rotation = previous;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindVertexArray(vao);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
            GameAccess.staticCall("zombie.core.ShaderHelper", "forgetCurrentlyBound");
            GameAccess.staticCall("zombie.core.opengl.GLStateRenderThread", "startFrame");
        }
    }

    @Override public void render() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        RotationCamera.Snapshot previousRotation = RotationTrees.begin(rotation);
        RotationTrees.Order order = null;
        try {
            @SuppressWarnings("unchecked")
            List<Object> records = (List<Object>) GameAccess.field(BATCH,"trees",batch);
            List<Object> all=null;
            if(!modelRecords.isEmpty()) {all=new ArrayList<>(records);records.removeAll(modelRecords);}
            try {
            order = RotationTrees.order(records);
            GL11.glColorMask(true,true,true,true);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST); GL11.glAlphaFunc(GL11.GL_GREATER,0f);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GameAccess.call(BATCH,"render",batch,NONE);
            } finally {
                if(order!=null){RotationTrees.endOrder(order);order=null;}
                if(all!=null){records.clear();records.addAll(all);}
            }
        } catch (Throwable error) { IsometricBridge.fail("native trees",error); }
        finally { if (order != null) RotationTrees.endOrder(order); RotationTrees.end(previousRotation); GL11.glPopAttrib(); GameAccess.staticCall("zombie.core.opengl.GLStateRenderThread","startFrame"); }
    }

    @Override public void postRender() { release(); }

    public void release() {
        if (batch!=null) { GameAccess.call(BATCH,"postRender",batch,NONE); batch=null; }
        highlighted.clear();
        modelRecords.clear();
    }

    public String statistics() { return "trees="+trees.size()+", stencil="+hidden+", native records="+records; }
}
