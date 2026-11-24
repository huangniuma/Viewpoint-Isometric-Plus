import java.lang.reflect.*;import java.nio.*;import java.security.*;import java.util.*;
public final class NativeView087Probe {
 public static void main(String[] args)throws Exception{
  Method capture=NativeViewProbe.class.getDeclaredMethod("nativeSnapshot");capture.setAccessible(true);Object data=capture.invoke(null);
  Method columns=NativeViewProbe.class.getDeclaredMethod("nativeColumns");columns.setAccessible(true);columns.invoke(null);
  Field pixels=data.getClass().getDeclaredField("pixels");pixels.setAccessible(true);ByteBuffer bytes=((ByteBuffer)pixels.get(data)).duplicate();byte[] contents=new byte[bytes.limit()];bytes.position(0);bytes.get(contents);
  System.out.println("VIEW087 "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents))+" bytes="+contents.length+"; native getters, disconnected chunks, levels, negative origin, blackout and endpoint rules.");
 }
}
