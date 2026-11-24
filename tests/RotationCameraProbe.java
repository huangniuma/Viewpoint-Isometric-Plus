import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import vpiso.IsoCameraMath;
import vpiso.RotationCamera;

/** Projection/picking round trips, panning and independent clip-space culling across continuous yaw. */
public final class RotationCameraProbe {
    public static void main(String[] args) {
        Random random = new Random(15542);
        int cases = 0;
        double worst = 0;
        for (float yaw : new float[] {0f, 17.25f, 44.9f, 45f, 89.5f, 90f, 123.75f, 180f,
                225f, 270f, 315.25f, 359.75f, -342.75f, 765.25f})
            for (float zoom : new float[] {.5f, 1f, 2f})
            for (int[] size : new int[][] {{1280,720},{1920,1080},{3440,1440}}) {
                float camX=-8153.25f, camY=11710.75f, camZ=2.25f;
                var fit=IsoCameraMath.fit(camX,camY,size[0]*.5+120,size[1]*.5-40,
                        64/zoom,32/zoom,-64/zoom,32/zoom,size[0],size[1]);
                fit=RotationCamera.rotateFit(fit,camX,camY,yaw);
                Matrix4f projection=new Matrix4f(),view=new Matrix4f();
                IsoCameraMath.matrices(fit,camX,camY,size[0],size[1],projection,view,new float[3],yaw);
                var snapshot=new RotationCamera.Snapshot(projection,view,camX,camY,camZ,
                        size[0],size[1],zoom,yaw);
                if (snapshot.yawDegrees() != RotationCamera.normalizeYaw(yaw))
                    throw new AssertionError("Snapshot lost the precise yaw");
                float[] screen=new float[3]; double[] point=new double[2];
                if (!snapshot.project(camX,camY,camZ,screen)) throw new AssertionError("player not projected");
                if (Math.abs(screen[0]-(size[0]*.5+120))>2 || Math.abs(screen[1]-(size[1]*.5-40))>2)
                    throw new AssertionError("Camera pan changed across rotation: "+yaw);
                float anchorY=screen[1];
                snapshot.project(camX,camY,camZ+1,screen);
                double expectedHeight=192.0/zoom;
                if(Math.abs(anchorY-screen[1]-expectedHeight)>.15)
                    throw new AssertionError("Rotation changed pitch or scale: "+yaw+", height="+(anchorY-screen[1]));
                Matrix4f combined=new Matrix4f(projection).mul(view);
                for(int i=0;i<300;i++) {
                    double x=camX+(random.nextDouble()-.5)*60,y=camY+(random.nextDouble()-.5)*60;
                    double z=camZ+(random.nextDouble()-.5)*6;
                    if(!snapshot.project(x,y,z,screen)||!snapshot.ground(screen[0],screen[1],z,point))
                        throw new AssertionError("Projection failed");
                    double error=Math.hypot(x-point[0],y-point[1]); worst=Math.max(worst,error);
                    if(error>.025) throw new AssertionError("Picking drift yaw="+yaw+", error="+error);
                    float sx=(float)(camX-x),sy=(float)((z-camZ)*2.4494896),sz=(float)(camY-y);
                    Vector4f clip=combined.transform(new Vector4f(sx,sy,sz,1));
                    boolean interior=Math.abs(clip.x)<clip.w*.99&&Math.abs(clip.y)<clip.w*.99
                            &&Math.abs(clip.z)<clip.w*.99;
                    if(interior&&!IsoCameraMath.visible(fit,camX,camY,size[0],size[1],sx,sy,sz,0,yaw))
                        throw new AssertionError("Rotated visible tile culled");
                    cases++;
                }
                // Neither subsequent capture nor callers changing returned matrices may alter queued frames.
                snapshot.project(camX,camY,camZ,screen); float x=screen[0],y=screen[1];
                projection.identity();view.identity();snapshot.view().identity();snapshot.projection().identity();
                snapshot.project(camX,camY,camZ,screen);
                if(screen[0]!=x||screen[1]!=y) throw new AssertionError("Queued camera snapshot mutated");
            }
        System.out.printf("Rotation camera PASS: %d world/screen/pick cases; continuous yaw including wrap/diagonal boundaries, fixed pitch/scale, zoom/pan, negative coordinates, multiple heights, frustum and queued isolation; worst %.5f tiles.%n",cases,worst);
    }
}
