import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import vpiso.Main;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Actual FrameStream and production advice, GL call ordering and compute/API ring reuse. */
public final class FrameStream113Probe {
    static final String STREAM="viewpoint.render.FrameStream";
    static final List<String> trace=new ArrayList<>();
    static int barriers,updates,wraps;
    public static void main(String[] args)throws Exception{
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        long window=GLFW.glfwCreateWindow(32,32,"FrameStream113",0,0);if(window==0)throw new AssertionError("GL window");
        int program=0;
        try{
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            System.out.println("GPU="+GL11.glGetString(GL11.GL_VENDOR)+" / "+GL11.glGetString(GL11.GL_RENDERER));
            Class<?> stream=weave();
            program=compute();
            run(stream,program,false,40);
            run(stream,program,true,200);
            require(GL11.glGetError()==0,"OpenGL error");
            System.out.println("FrameStream113 PASS: exact production reserve advice, unsupported passthrough, barrier-before-update trace, 240 compute/API same-range reuse cases; baseline failure is not required.");
        }finally{
            Main.compatible=false;if(program!=0)GL20.glDeleteProgram(program);
            GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
    static void run(Class<?> stream,int program,boolean compatible,int cycles)throws Exception{
        Main.compatible=compatible;int beforeBarriers=barriers,beforeUpdates=updates,beforeWraps=wraps;
        Method room=method(stream,"room",long.class,int.class),put=method(stream,"put",FloatBuffer.class,int.class),write=method(stream,"write",long.class,FloatBuffer.class),started=method(stream,"frameStarted");
        Method putInts=method(stream,"put",IntBuffer.class,int.class),putBytes=method(stream,"put",ByteBuffer.class,int.class);
        int length=256,bytes=length*4;
        FloatBuffer input=BufferUtils.createFloatBuffer(length),output=BufferUtils.createFloatBuffer(length);
        long elapsed=System.nanoTime();
        for(int n=0;n<cycles;n++){
            started.invoke(null);trace.clear();
            // Isolated fixture places the real ring cursor at its end to exercise wrap each cycle.
            if(field(stream,"buffer").getInt(null)!=0)field(stream,"head").setLong(null,field(stream,"size").getLong(null));
            long oldHead=field(stream,"head").getLong(null);
            long position=(Long)room.invoke(null,(long)bytes,256);
            if(position<oldHead)wraps++;
            require(trace.equals(compatible?List.of("B512"):List.of()),"room advice count/order "+trace);
            int id=field(stream,"buffer").getInt(null);
            GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,0,id,position,bytes);
            GL20.glUseProgram(program);GL20.glUniform1f(GL20.glGetUniformLocation(program,"seed"),n+.25f);
            GL43.glDispatchCompute(4,1,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            // Reuse exactly the shader-written range through the actual CPU upload path.
            field(stream,"head").setLong(null,field(stream,"size").getLong(null));
            input.clear();for(int i=0;i<length;i++)input.put(n*10f+i*.25f);input.flip();trace.clear();
            long reused=(Long)put.invoke(null,input,256);wraps++;
            require(reused==position,"Did not reuse the actual compute range");
            require(trace.equals(compatible?List.of("B512","UPDATE"):List.of("UPDATE")),"put order "+trace);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,id);output.clear();GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,reused,output);
            for(int i=0;i<length;i++)require(output.get(i)==n*10f+i*.25f,"GPU/API reuse mismatch cycle="+n+" field="+i);
            // write has no allocation: no additional reserve advice is expected after room.
            input.clear();for(int i=0;i<length;i++)input.put(-n-i*.125f);input.flip();trace.clear();write.invoke(null,reused,input);
            require(trace.equals(List.of("UPDATE")),"write unexpectedly allocates "+trace);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,id);output.clear();GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,reused,output);
            for(int i=0;i<length;i++)require(output.get(i)==-n-i*.125f,"write correctness");
            if(n==0){
                trace.clear();putInts.invoke(null,BufferUtils.createIntBuffer(4).put(new int[]{1,2,3,4}).flip(),16);
                require(trace.equals(compatible?List.of("B512","UPDATE"):List.of("UPDATE")),"int path order");
                trace.clear();putBytes.invoke(null,BufferUtils.createByteBuffer(4).putInt(7).flip(),16);
                require(trace.equals(compatible?List.of("B512","UPDATE"):List.of("UPDATE")),"byte path order");
            }
        }
        int expectedBarriers=compatible?cycles*2+2:0;
        require(barriers-beforeBarriers==expectedBarriers,"Barrier scope count");
        require(updates-beforeUpdates==cycles*2+2,"Upload scope count");
        System.out.println("compatible="+compatible+" cycles="+cycles+" barriers="+(barriers-beforeBarriers)+" updates="+(updates-beforeUpdates)+" wraps="+(wraps-beforeWraps)+" elapsed_ms="+(System.nanoTime()-elapsed)/1_000_000.0);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,0);GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,0);
    }
    public static void glMemoryBarrier(int bits){trace.add("B"+bits);barriers++;GL42.glMemoryBarrier(bits);}
    public static void glBufferSubData(int target,long offset,FloatBuffer values){trace.add("UPDATE");updates++;GL15.glBufferSubData(target,offset,values);}
    public static void glBufferSubData(int target,long offset,IntBuffer values){trace.add("UPDATE");updates++;GL15.glBufferSubData(target,offset,values);}
    public static void glBufferSubData(int target,long offset,ByteBuffer values){trace.add("UPDATE");updates++;GL15.glBufferSubData(target,offset,values);}
    static Class<?> weave()throws Exception{
        String patch="vpiso.Patch_FrameStreamUpdates";
        byte[] source=resource(patch);Method convert=PatchWeavingProbe.class.getDeclaredMethod("convert",byte[].class);convert.setAccessible(true);
        byte[] converted=(byte[])convert.invoke(null,(Object)source);
        ClassLoader adviceLoader=new BytesLoader(Map.of(patch,converted));
        ClassFileLocator locator=new ClassFileLocator.Compound(new ClassFileLocator.Simple(Map.of(patch,converted)),ClassFileLocator.ForClassLoader.of(FrameStream113Probe.class.getClassLoader()));
        byte[] woven=new ByteBuddy().redefine(Class.forName(STREAM)).visit(Advice.to(adviceLoader.loadClass(patch),locator).on(named("reserve").and(takesArguments(long.class,int.class)))).make().getBytes();
        // Trace the actual woven GL invocations while preserving every real GL operation.
        ClassReader reader=new ClassReader(woven);ClassWriter writer=new ClassWriter(reader,0);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,desc,sig,ex)){
                @Override public void visitMethodInsn(int op,String owner,String called,String descriptor,boolean itf){
                    if((owner.equals("org/lwjgl/opengl/GL42")&&called.equals("glMemoryBarrier"))||(owner.equals("org/lwjgl/opengl/GL15")&&called.equals("glBufferSubData")))owner="FrameStream113Probe";
                    super.visitMethodInsn(op,owner,called,descriptor,itf);
                }
            };}
        },0);
        return new BytesLoader(Map.of(STREAM,writer.toByteArray())).loadClass(STREAM);
    }
    static int compute(){
        int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);GL20.glShaderSource(shader,"#version 430\nlayout(local_size_x=64)in;layout(std430,binding=0)buffer Out{float values[];};uniform float seed;void main(){uint i=gl_GlobalInvocationID.x;values[i]=seed+float(i);}");GL20.glCompileShader(shader);
        require(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(shader));
        int program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);GL20.glDeleteShader(shader);require(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(program));return program;
    }
    static byte[] resource(String name)throws Exception{try(InputStream in=FrameStream113Probe.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){if(in==null)throw new IllegalStateException(name);return in.readAllBytes();}}
    static Method method(Class<?> type,String name,Class<?>... args)throws Exception{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static final class BytesLoader extends ClassLoader{
        final Map<String,byte[]> bytes;BytesLoader(Map<String,byte[]> bytes){super(FrameStream113Probe.class.getClassLoader());this.bytes=bytes;}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{Class<?> c=findLoadedClass(name);if(c==null){byte[] b=bytes.get(name);c=b==null?super.loadClass(name,false):defineClass(name,b,0,b.length);}if(resolve)resolveClass(c);return c;}
    }
}
