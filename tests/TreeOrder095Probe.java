import java.util.*;
import org.joml.Matrix4f;
import vpiso.*;

/** Bounded dense-row depth, XL identity, exact scope/list restoration and foreground margin. */
public final class TreeOrder095Probe {
 static final String TREE="zombie.iso.fboRenderChunk.FBORenderTrees$Tree";
 public static void main(String[] args)throws Exception {
  int checks=0;
  for(float yaw:new float[]{0,.004f,.006f,.04f,.05f,90,180,270})for(int count:new int[]{1,3,33,129}) {
   Matrix4f projection=new Matrix4f(),view=new Matrix4f();float[] eye=new float[3];IsoCameraMath.matrices(new IsoCameraMath.Fit(0,0,8),0,0,256,256,projection,view,eye,yaw);
   RotationCamera.Snapshot camera=new RotationCamera.Snapshot(projection,view,0,0,0,256,256,1f,yaw);
   List<Object> records=new ArrayList<>();for(int i=0;i<count;i++){Object r=NativeSurfaceReceiverProbe.allocate(GameAccess.type(TREE));GameAccess.set(TREE,"x",r,(float)i);GameAccess.set(TREE,"y",r,(float)-i);GameAccess.set(TREE,"z",r,0f);records.add(r);}
   Object xl=NativeSurfaceReceiverProbe.allocate(GameAccess.type(TREE));GameAccess.set(TREE,"x",xl,0f);GameAccess.set(TREE,"y",xl,0f);GameAccess.set(TREE,"z",xl,0f);records.add(xl);
   Collections.reverse(records);List<Object> original=new ArrayList<>(records);
   var before=RotationTrees.begin(camera);RotationTrees.Order order=RotationTrees.order(records);
   try {
    Object root=original.get(original.size()-1);near(RotationTrees.treeDepth(root),RotationTrees.treeDepth(xl),"XL crown/trunk different root depth");
    for(Object r:records) {
     float x=((Number)GameAccess.field(TREE,"x",r)).floatValue(),y=((Number)GameAccess.field(TREE,"y",r)).floatValue();
     float[] screen=new float[3];camera.project(x,y,0,screen);
     double w=projection.m23()*(view.m02()*-x+view.m22()*-y+view.m32())+projection.m33();
     double total=(RotationTrees.treeDepth(r)+RotationTrees.leafDepth(r))/w;
     require(Math.abs(total)<=265*Math.ulp(.5f),"dense row offset unbounded "+count+" "+total);
     double vz=view.m02()*-x+view.m22()*-y+view.m32();
     for(double height:new double[]{0,8,32}) {
      double z=vz+view.m12()*height,depth=depth(projection,z),front=depth(projection,z+.1);
      require(depth-front>Math.abs(total)*8,"tree bias crosses foreground margin");checks++;
     }
    }
   }finally{RotationTrees.endOrder(order);RotationTrees.end(before);}
   require(records.equals(original),"record list order leaked");require(!RotationTrees.active(),"tree camera scope leaked");
   var again=RotationTrees.begin(camera);try{for(Object r:records)near(RotationTrees.treeDepth(r),0,"tree order scope leaked");}finally{RotationTrees.end(again);}
  }
  System.out.println("Tree095 depth bounds PASS: checks="+checks+"; 129 roots, capped offsets, XL same-root, foreground .1 units, list and scope restore.");
 }
 static double depth(Matrix4f p,double z){return ((p.m22()*z+p.m32())/(p.m23()*z+p.m33()))*.5+.5;}
 static void near(float a,float b,String label){require(Math.abs(a-b)<.000001f,label);}static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
