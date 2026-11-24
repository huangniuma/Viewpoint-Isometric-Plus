package vpiso;

import java.util.Arrays;
import viewpoint.core.Frame;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;

/** Native sight/fade snapshot, owned by the same sprite buffer as its receiver. */
public final class NativeRelightMask {
    public static final int SIZE=64;
    public final byte[] pixels=new byte[SIZE*SIZE];
    public final byte[] exterior=new byte[SIZE*SIZE];
    private final boolean[] squares=new boolean[(SIZE+2)*(SIZE+2)];
    public int x,y,z;

    public void capture(Frame frame,Object cell,int player) {
        x=(int)Math.floor(frame.camX)-SIZE/2;y=(int)Math.floor(frame.camY)-SIZE/2;z=(int)Math.floor(frame.camZ);
        Arrays.fill(squares,false);Arrays.fill(pixels,(byte)0);Arrays.fill(exterior,(byte)0);
        IsoCell world=(IsoCell)cell;
        for(int row=0;row<SIZE+2;row++)for(int col=0;col<SIZE+2;col++) {
            IsoGridSquare square=world.getGridSquare(x+col-1,y+row-1,z);
            // isOutside/light alpha also exclude roofed yards. A rain canopy
            // without a room still receives physical sun/moon projections.
            if(square!=null && row>0 && row<=SIZE && col>0 && col<=SIZE)
                exterior[(row-1)*SIZE+col-1]=square.getRoomID()==-1L && !square.isInARoom()?(byte)255:0;
            if(square!=null && square.lighting[player]!=null)
                squares[row*(SIZE+2)+col]=square.lighting[player].bCanSee()
                        && square.lighting[player].darkMulti()>=1f;
        }
        // PZO/native corner interpolation may sample any neighbour. Retain the
        // native lighting for all sight edges, remembered areas and fading rooms.
        for(int row=0;row<SIZE;row++)for(int col=0;col<SIZE;col++) {
            boolean visible=true;
            for(int dy=0;dy<3 && visible;dy++)for(int dx=0;dx<3;dx++)
                if(!squares[(row+dy)*(SIZE+2)+col+dx]){visible=false;break;}
            if(visible)pixels[row*SIZE+col]=(byte)255;
        }
    }
}
