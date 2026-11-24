package vpiso;
import me.zed_0xff.zombie_buddy.Patch;

/** This batch already runs on the render thread: never enqueue a delayed owner. */
@Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees",methodName="renderTree",strictMatch=true)
public final class Patch_NativeSourceTree {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Object tree,@Patch.Local("vpIsoTreeSource") boolean active,
                             @Patch.Local("vpIsoLitTree") Object previous) {
        previous=NativeTreeLighting.tree(tree);
        active=NativeGround.active();
        if(active)NativeGround.sourceBegin(NativeTreeSources.source(tree));
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Local("vpIsoTreeSource") boolean active,@Patch.Local("vpIsoLitTree") Object previous) {
        if(active)NativeGround.sourceEnd();NativeTreeLighting.tree(previous);
    }
}
