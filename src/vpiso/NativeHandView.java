package vpiso;

import org.joml.Matrix4f;

/** The moving body turns between logical headings; an aimed beam follows the aim immediately. */
public final class NativeHandView {
    public static void capture(Matrix4f view, Object character) {
        float angle = ((Number) GameAccess.call("zombie.characters.IsoGameCharacter",
                "getDirectionAngleRadians", character, new Class<?>[0])).floatValue();
        Object animation = GameAccess.call("zombie.characters.IsoGameCharacter",
                "getAnimationPlayer", character, new Class<?>[0]);
        boolean aiming = GameAccess.type("zombie.characters.IsoPlayer").isInstance(character)
                && (Boolean) GameAccess.call("zombie.characters.IsoPlayer", "isAiming", character, new Class<?>[0]);
        if (animation != null && !aiming) {
            float rendered = ((Number) GameAccess.call("zombie.core.skinnedmodel.animation.AnimationPlayer",
                    "getAngle", animation, new Class<?>[0])).floatValue();
            if (Float.isFinite(rendered)) angle = rendered;
        }
        view.setLookAt(0f, 1.5f, 0f, -(float) Math.cos(angle), 1.5f,
                -(float) Math.sin(angle), 0f, 1f, 0f);
    }

    private NativeHandView() {}
}
