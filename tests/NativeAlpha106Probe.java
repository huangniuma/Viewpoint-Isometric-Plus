import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import viewpoint.core.Frame;
import vpiso.*;
import zombie.iso.*;
import zombie.iso.sprite.IsoSprite;

/** Same protected native final alpha entry, including virtual internal overload and per-capture calls. */
public final class NativeAlpha106Probe {
    static final class Source extends IsoObject {
        int calls;
        final ArrayList<String> events=new ArrayList<>();
        Source(IsoGridSquare square) { super(null,square,new IsoSprite());neverDoneAlpha=false; }
        @Override protected void updateAlpha(int player,float a,float b) {
            calls++;events.add(player+":"+Integer.toHexString(Float.floatToRawIntBits(a))+":"+Integer.toHexString(Float.floatToRawIntBits(b)));
            super.updateAlpha(player,a,b);
        }
    }
    public static void main(String[] args)throws Exception {
        zombie.core.random.RandStandard.INSTANCE.init();UpperObjects064Probe.main(new String[]{"caps"});
        var factory=UpperObjects064Probe.class.getDeclaredMethod("square",int.class,int.class,int.class,boolean.class);factory.setAccessible(true);
        IsoGridSquare square=(IsoGridSquare)factory.invoke(null,4,4,0,false);
        NativeObjectState state=new NativeObjectState();Frame frame=new Frame();StringBuilder result=new StringBuilder();int cases=0;
        for(int player=0;player<4;player++)for(float alpha:new float[]{0,.125f,.8f,1})for(float target:new float[]{0,.2f,.7f,1,Float.NaN,Float.POSITIVE_INFINITY}) {
            Source source=new Source(square);source.setAlphaAndTarget(player,alpha);
            state.beginCapture(frame);
            try {
                float first=NativeObjectState.alpha(source,player,target);int calls=source.calls;
                float second=NativeObjectState.alpha(source,player,target);
                require(Float.floatToIntBits(first)==Float.floatToIntBits(second),"Same-capture alpha changed");
                require(source.calls==calls,"Same-capture extra native step");
                result.append(player).append('/').append(Float.floatToRawIntBits(first)).append('/').append(source.events).append(';');
            } finally { NativeObjectState.endCapture(); }
            state.beginCapture(frame);
            try { result.append(Float.floatToRawIntBits(NativeObjectState.alpha(source,player,target))).append('/').append(source.events).append(';'); }
            finally { NativeObjectState.endCapture(); }
            int before=source.calls;
            require(Float.floatToIntBits(NativeObjectState.alpha(source,player,target))==Float.floatToIntBits(target),"Unscoped alpha changed");
            require(source.calls==before,"Unscoped query added native step");cases++;
        }
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(result.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("ALPHA106 PASS cases="+cases+" checksum="+HexFormat.of().formatHex(hash)+"; real final native entry/internal virtual callback, 4players, target/current alpha, repeat/capture lifetime and nonfinite results.");
    }
    static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
