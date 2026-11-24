package vpiso;

import java.util.List;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Both native wall-cap predicates, sharing their otherwise repeated object scan. */
public final class NativeWallCaps {
    private NativeWallCaps() {}

    public static int caps(Object value) {
        IsoGridSquare square = (IsoGridSquare) value;
        if (square == null || square.has(IsoFlagType.WallSE)) return 0;
        int caps = (square.has(IsoFlagType.WindowN) ? 4 : 0)
                | (square.has(IsoFlagType.WindowW) ? 8 : 0);
        if (caps == 12) return caps;
        List<IsoObject> objects = square.getObjects();
        if (objects.isEmpty()) return caps;
        boolean northwest = square.has(IsoFlagType.WallNW);
        boolean north = northwest || square.has(IsoFlagType.WallN) || square.has(IsoFlagType.DoorWallN);
        boolean west = northwest || square.has(IsoFlagType.WallW) || square.has(IsoFlagType.DoorWallW);
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object == null ? null : object.sprite;
            if (sprite != null) {
                int add = (north && sprite.cutN ? 4 : 0) | (west && sprite.cutW ? 8 : 0);
                if (add != 0 && !object.isWallSE()) {
                    caps |= add;
                    if (caps == 12) return caps;
                }
            }
            // Ordinary sprites cannot be garage doors. Avoid their property hash
            // lookup while preserving Door/Thumpable's exact native predicate.
            if ((object instanceof IsoDoor || object instanceof IsoThumpable) && IsoDoor.getGarageDoorIndex(object) != -1) {
                boolean direction = object instanceof IsoDoor door ? door.isNorth() : ((IsoThumpable) object).isNorth();
                caps |= direction ? 4 : 8;
                if (caps == 12) return caps;
            }
        }
        return caps;
    }
}
