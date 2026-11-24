import java.lang.reflect.Method;
import org.joml.Matrix4f;
import vpiso.IsoCameraMath;

/** Exercises the addon matrices against the game's actual JOML on Java 25. */
public final class CameraProbe {
    public static void main(String[] args) throws Exception {
        Class<?> vectorType = Class.forName("org.joml.Vector3f");
        Method project = Matrix4f.class.getMethod("transformProject", float.class, float.class, float.class, vectorType);
        Method multiply = Matrix4f.class.getMethod("mul", Class.forName("org.joml.Matrix4fc"));
        double worst = 0;
        int cases = 0;
        for (int[] size : new int[][] {{1280,720},{1920,1080},{2560,1440},{3440,1440}}) {
            for (double zoom : new double[] {.5,.75,1,1.5,2}) {
                for (double[] offset : new double[][] {{0,0},{175,-80},{-260,120}}) {
                    double scale = 1 / zoom, px = size[0]*.5 + offset[0], py = size[1]*.5 + offset[1];
                    var fit = IsoCameraMath.fit(10524.25,9854.75,px,py,64*scale,32*scale,-64*scale,32*scale,size[0],size[1]);
                    Matrix4f projection = new Matrix4f(), view = new Matrix4f();
                    float[] eye = new float[3];
                    IsoCameraMath.matrices(fit,10524.25f,9854.75f,size[0],size[1],projection,view,eye);
                    multiply.invoke(projection,view);
                    for (double[] p : new double[][] {{0,0},{1,0},{0,1},{5,4},{-5,-4}}) {
                        Object v = vectorType.getConstructor().newInstance();
                        project.invoke(projection,(float)-p[0],0f,(float)-p[1],v);
                        double sx = (vectorType.getField("x").getFloat(v)+1)*size[0]/2;
                        double sy = (1-vectorType.getField("y").getFloat(v))*size[1]/2;
                        double expectedX = px + (p[0]-p[1])*64*scale;
                        double expectedY = py + (p[0]+p[1])*32*scale;
                        double error = Math.hypot(sx-expectedX,sy-expectedY);
                        worst = Math.max(worst,error);
                        if (error > 6) throw new AssertionError("Camera alignment error " + error);
                        cases++;
                    }
                }
            }
        }
        boolean rejects = false;
        try { IsoCameraMath.fit(0,0,0,0,1,1,1,1,100,100); }
        catch (IllegalArgumentException expected) { rejects = true; }
        if (!rejects) throw new AssertionError("Singular projection accepted");
        var cullFit = IsoCameraMath.fit(0,0,960,540,64,32,-64,32,1920,1080);
        int accepted=0,rejected=0;
        for (int x=-100;x<=100;x++) for (int y=-100;y<=100;y++) {
            double sx=960+(x-y)*64, sy=540+(x+y)*32;
            boolean expected=sx>2 && sx<1918 && sy>2 && sy<1078;
            boolean actual=IsoCameraMath.visible(cullFit,0,0,1920,1080,-x,0,-y,0);
            if (expected && !actual) throw new AssertionError("Visible world tile culled: "+x+","+y);
            if (actual) accepted++; else rejected++;
        }
        if (rejected<accepted*10) throw new AssertionError("Cull still accepts distant loaded world");
        System.out.println("Isometric frustum: visible tile basis preserved, "+rejected+" off-screen tile centres rejected.");
        System.out.printf("Camera projection: %d cases, worst perspective approximation %.3f pixels; singular basis rejected.%n",cases,worst);
    }
}
