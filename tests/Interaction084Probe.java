import java.lang.reflect.*;
import org.joml.Matrix4f;
import vpiso.*;
import viewpoint.core.Frame;
import viewpoint.render.MousePick;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.iso.IsoObject;

/** Actual game animation angle and alpha stepping, with no world/GL or live-game attachment. */
public final class Interaction084Probe {
    static final sun.misc.Unsafe UNSAFE;
    static { try { Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); UNSAFE=(sun.misc.Unsafe)f.get(null); }
        catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    public static class Actor extends IsoPlayer {
        AnimationPlayer animation;
        boolean aim;
        float logical;
        public Actor() { super((zombie.iso.IsoCell)null); }
        @Override public AnimationPlayer getAnimationPlayer() { return animation; }
        @Override public float getDirectionAngleRadians() { return logical; }
        @Override public boolean isAiming() { return aim; }
    }
    public static void main(String[] args)throws Exception {
        Actor actor=(Actor)UNSAFE.allocateInstance(Actor.class);
        actor.animation=(AnimationPlayer)UNSAFE.allocateInstance(AnimationPlayer.class);
        Matrix4f actual=new Matrix4f(); int turns=0,steps=0;
        for(int i=0;i<=128;i++) {
            float angle=-3.2f+i*.05f;
            actor.animation.setAngle(angle);actor.logical=(float)(Math.round(angle/(Math.PI/4))*(Math.PI/4));
            actor.aim=false;NativeHandView.capture(actual,actor);
            same(actual,view(angle),"Moving beam snapped to the logical heading");turns++;
            actor.aim=true;actor.logical=angle+.13f;NativeHandView.capture(actual,actor);
            same(actual,view(actor.logical),"Aiming no longer updates immediately");turns++;
        }
        actor.animation=null;NativeHandView.capture(actual,actor);same(actual,view(actor.logical),"No-model fallback");
        MousePick.ask=null;
        zombie.core.random.RandStandard.INSTANCE.init();
        for(float start:new float[]{0,.25f,.66f,1})for(float target:new float[]{0,.25f,.66f,1}) {
            IsoObject original=new IsoObject(), model=new IsoObject();
            original.neverDoneAlpha=model.neverDoneAlpha=false;
            original.setAlphaAndTarget(0,start);model.setAlphaAndTarget(0,start);
            for(int i=0;i<8;i++) {
                original.setTargetAlpha(0,target);
                GameAccess.call("zombie.iso.IsoObject","updateAlpha",original,new Class<?>[]{int.class},0);
                NativeObjectState state=new NativeObjectState();state.beginCapture(new Frame());
                try {
                    float alpha=NativeObjectState.alpha(model,0,target);
                    require(Math.abs(alpha-original.getAlpha(0))<.00001f,"Model fade diverged from native step");
                    require(NativeObjectState.alpha(model,0,target)==alpha,"Duplicate source advanced twice in one frame");
                } finally {NativeObjectState.endCapture();}
                steps++;
            }
        }
        System.out.println("Interaction084 CPU PASS: continuous hand view="+turns+", native fade steps="+steps+"; aiming and missing-animation fallback; per-frame alpha deduplication.");
    }
    static Matrix4f view(float a) { return new Matrix4f().setLookAt(0,1.5f,0,-(float)Math.cos(a),1.5f,-(float)Math.sin(a),0,1,0); }
    static void same(Matrix4f a,Matrix4f b,String message) {float[] x=a.get(new float[16]),y=b.get(new float[16]);for(int i=0;i<16;i++)require(Math.abs(x[i]-y[i])<.00001f,message);}
    static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
