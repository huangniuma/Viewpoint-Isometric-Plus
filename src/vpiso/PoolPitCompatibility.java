package vpiso;

/** Optional PoolPit integration; its own mesh/shader hooks remain authoritative. */
public final class PoolPitCompatibility {
    private static final boolean PRESENT = present();

    private static boolean present() {
        try {
            Class.forName("poolpitvp.PoolPitVP", false, PoolPitCompatibility.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) { return false; }
    }

    public static boolean loaded() { return PRESENT; }
    public static boolean nativeWater(boolean available) { return available && !PRESENT; }
    public static IsometricEffects effects(IsometricEffects selected) {
        return PRESENT?selected.withWater(false):selected;
    }
    private PoolPitCompatibility() {}
}
