import java.nio.file.Path;
import java.util.zip.ZipFile;
import vpiso.*;

/** Real optional water ownership/scope policy plus release archive exclusion. */
public final class PoolPitPolicy121Probe {
    public static void main(String[] args)throws Exception {
        boolean present=Boolean.parseBoolean(args[0]);
        if(PoolPitCompatibility.loaded()!=present)throw new AssertionError("optional detection");
        for(boolean water:new boolean[]{false,true}) {
            IsometricEffects selected=IsometricEffects.DEFAULT.withWater(water);
            IsometricEffects actual=PoolPitCompatibility.effects(selected);
            IsometricEffects wanted=present?selected.withWater(false):selected;
            if(!actual.equals(wanted))throw new AssertionError("unrelated effect changed or extra water retained");
            if(!present&&actual!=selected)throw new AssertionError("absent addon added work");
        }
        try(ZipFile z=new ZipFile(Path.of(args[1]).toFile())) {
            if(z.stream().anyMatch(e->e.getName().contains("PoolPitWaterOverlay")||e.getName().contains("PoolPitTerrain")||e.getName().contains("PoolPitSnow")))throw new AssertionError("withdrawn native adapter shipped");
        }
        PoolPitMode120Probe.main(new String[]{args[0]});
        System.out.println("PoolPitPolicy121 PASS present="+present+"; native water retained, optional water overlays suppressed only with PoolPit, all other effect values retained, 3D ownership/scope restored, withdrawn native adapters absent.");
    }
}
