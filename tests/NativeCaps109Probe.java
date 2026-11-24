import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import zombie.core.properties.*;
import zombie.iso.*;
import zombie.iso.objects.*;
import zombie.iso.sprite.*;
import zombie.iso.SpriteDetails.IsoFlagType;
import vpiso.GameAccess;

/** Compare one-pass capability queries with the actual game private producers. */
public final class NativeCaps109Probe {
    private static final IsoFlagType[] FLAGS = {IsoFlagType.WallSE, IsoFlagType.WindowN,
            IsoFlagType.WindowW, IsoFlagType.WallN, IsoFlagType.WallW,
            IsoFlagType.WallNW, IsoFlagType.DoorWallN, IsoFlagType.DoorWallW};
    public static void main(String[] args) throws Throwable {
        zombie.core.random.RandStandard.INSTANCE.init();
        var alias = new HashMap<String, ArrayList<String>>();
        alias.put("GarageDoor", new ArrayList<>(List.of("1", "2", "3", "4", "5", "6")));
        zombie.core.TilePropertyAliasMap.instance.Generate(alias);
        Method n = IsoGridSquare.class.getDeclaredMethod("hasCutawayCapableWallNorth", IsoGridSquare.class);
        Method w = IsoGridSquare.class.getDeclaredMethod("hasCutawayCapableWallWest", IsoGridSquare.class);
        n.setAccessible(true); w.setAccessible(true);
        MethodHandle north = MethodHandles.lookup().unreflect(n), west = MethodHandles.lookup().unreflect(w);
        IsoGridSquare square = (IsoGridSquare) ModelCutawayProbe.allocate(IsoGridSquare.class);
        GameAccess.set(IsoGridSquare.class.getName(), "objects", square, new ArrayList<IsoObject>());
        int cases = 0;
        for (int mask = 0; mask < 256; mask++) {
            PropertyContainer properties = new PropertyContainer();
            for (int i=0; i<FLAGS.length; i++) if ((mask & (1 << i)) != 0) properties.set(FLAGS[i]);
            GameAccess.set(IsoGridSquare.class.getName(), "properties", square, properties);
            for (int walls = 0; walls < 16; walls++) for (int garages = 0; garages < 16; garages++) {
                square.getObjects().clear();
                square.getObjects().add(null);
                square.getObjects().add(new IsoObject()); // no sprite
                for (int i=0; i<3; i++) square.getObjects().add(new IsoObject(null, square, new IsoSprite()));
                if ((walls & 1) != 0) square.getObjects().add(wall(square,true,false));
                if ((walls & 2) != 0) square.getObjects().add(wall(square,false,false));
                if ((walls & 4) != 0) square.getObjects().add(wall(square,true,true));
                if ((walls & 8) != 0) square.getObjects().add(wall(square,false,true));
                if ((garages & 1) != 0) square.getObjects().add(garage(square,true,false,false));
                if ((garages & 2) != 0) square.getObjects().add(garage(square,false,false,false));
                if ((garages & 4) != 0) square.getObjects().add(garage(square,true,true,true));
                if ((garages & 8) != 0) square.getObjects().add(garage(square,false,true,true));
                int expected = ((boolean)north.invokeExact(square) ? 4 : 0) | ((boolean)west.invokeExact(square) ? 8 : 0);
                int actual = vpiso.NativeWallCaps.caps(square);
                if (actual != expected) throw new AssertionError("caps mismatch flags="+mask+" walls="+walls+" garages="+garages+" expected="+expected+" actual="+actual);
                cases++;
            }
        }
        for (int mask = 0; mask < 256; mask++) {
            PropertyContainer properties = new PropertyContainer();
            for (int i=0; i<FLAGS.length; i++) if ((mask & (1 << i)) != 0) properties.set(FLAGS[i]);
            GameAccess.set(IsoGridSquare.class.getName(), "properties", square, properties);
            for (int shape = 0; shape < 4; shape++) {
                square.getObjects().clear();
                if (shape >= 1) square.getObjects().add(null);
                if (shape >= 2) square.getObjects().add(new IsoObject());
                if (shape >= 3) for(int floor=0;floor<8;floor++) square.getObjects().add(new IsoObject(null,square,new IsoSprite()));
                int expected = ((boolean)north.invokeExact(square) ? 4 : 0) | ((boolean)west.invokeExact(square) ? 8 : 0);
                int actual = vpiso.NativeWallCaps.caps(square);
                if (actual != expected) throw new AssertionError("empty/floor-only mismatch flags="+mask+" shape="+shape+" expected="+expected+" actual="+actual);
                cases++;
            }
        }
        if (vpiso.NativeWallCaps.caps(null) != 0) throw new AssertionError("null square");
        System.out.println("Caps109 original predicate parity PASS: " + cases + " squares; 256 flag states, 16 wall states, 16 door/thumpable garage states; sprite-less/null entries, diagonal walls and north/west orientations. CPU-only fixture.");
    }
    private static IsoObject wall(IsoGridSquare square, boolean north, boolean diagonal) {
        IsoSprite sprite = new IsoSprite();
        if (north) sprite.cutN=true; else sprite.cutW=true;
        if (diagonal) sprite.getProperties().set(IsoFlagType.WallSE);
        return new IsoObject(null,square,sprite);
    }
    private static IsoObject garage(IsoGridSquare square, boolean north, boolean thumpable, boolean open) throws Exception {
        IsoObject object = (IsoObject) ModelCutawayProbe.allocate(thumpable ? IsoThumpable.class : IsoDoor.class);
        IsoSprite sprite = new IsoSprite();
        sprite.getProperties().set("GarageDoor", "1");
        GameAccess.set(IsoObject.class.getName(), "sprite", object, sprite);
        GameAccess.set(IsoObject.class.getName(), "square", object, square);
        if (thumpable) {
            GameAccess.set(IsoThumpable.class.getName(), "north", object, north);
            GameAccess.set(IsoThumpable.class.getName(), "open", object, open);
        } else {
            ((IsoDoor)object).north = north;
            GameAccess.set(IsoDoor.class.getName(), "open", object, open);
        }
        return object;
    }
}

