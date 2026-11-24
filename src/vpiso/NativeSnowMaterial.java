package vpiso;

import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;

/** Own snow GBuffer; selected VP material light and tone, composited over native art. */
public final class NativeSnowMaterial {
    private static final String VERTEX="""
        #version 330
        out vec2 vUv;
        void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);vUv=p;gl_Position=vec4(p*2.0-1.0,0,1);}
        """;
    private static final String COMPOSE="""
        uniform sampler2D vpIsoSnowNativeColor,vpIsoSnowNativeWeight;
        layout(location=0) out vec4 vpIsoSnowResult;
        layout(location=6) out vec4 vpIsoSnowOwner;
        layout(location=7) out vec4 vpIsoSnowContribution;
        void main(){
          float d=texture(uDepth,vUv).r;if(d>=1.0)discard;
          vpIsoSelectedLight();
          vec4 base=texture(vpIsoSnowNativeColor,vUv),weight=texture(vpIsoSnowNativeWeight,vUv);
          vec3 snow=nativeGrade(max(vpIsoSnowLit.rgb,vec3(0)))*weight.a;
          vpIsoSnowResult=vec4(max(base.rgb-weight.rgb,vec3(0))+snow,base.a);
          vpIsoSnowContribution=vec4(snow,base.a);vpIsoSnowOwner=vec4(-1,0,1,1);
          gl_FragDepth=d;
        }
        """;
    private int fbo,albedo,normal,engine,weight,depth,vao,width,height;
    private GlProgram light;
    private Object pack;

    public void begin(int w,int h) {
        if(width!=w || height!=h || fbo==0) {
            if(fbo!=0) {GL30.glDeleteFramebuffers(fbo);for(int id:new int[]{albedo,normal,engine,weight,depth})GL11.glDeleteTextures(id);}
            width=w;height=h;fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            albedo=attachment(GL11.GL_RGBA8,GL30.GL_COLOR_ATTACHMENT0);
            normal=attachment(GL30.GL_RGBA16F,GL30.GL_COLOR_ATTACHMENT1);
            engine=attachment(GL30.GL_RGBA16F,GL30.GL_COLOR_ATTACHMENT2);
            weight=attachment(GL30.GL_RGBA16F,GL30.GL_COLOR_ATTACHMENT7);
            depth=attachment(GL30.GL_DEPTH_COMPONENT32F,GL30.GL_DEPTH_ATTACHMENT);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Native snow GBuffer incomplete");
        } else GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,GL30.GL_COLOR_ATTACHMENT1,GL30.GL_COLOR_ATTACHMENT2,0,0,0,0,GL30.GL_COLOR_ATTACHMENT7});
        GL11.glViewport(0,0,w,h);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
        for(int i:new int[]{0,1,2,7})GL30.glClearBufferfv(GL11.GL_COLOR,i,new float[4]);
        GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    public boolean finish(FrameContext context,NativeReceiver receiver,int output,int color,int[] viewport) {
        try(NativeLighting.Scope scope=NativeLighting.open(context.scene,receiver)) {
            if(!scope.ready())return false;
            if(light==null || pack!=GlProgram.pack()) {
                String source=source();
                GlProgram next=GlProgram.fromSources("VPISO native snow material",new GlProgram.Sources(VERTEX,null,null,null,source),
                        Map.of(),Map.of(),List.of("VPISO selected snow material"));
                if(light!=null)light.delete();light=next;pack=GlProgram.pack();
                if(vao==0)vao=GL30.glGenVertexArrays();
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,output);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0,0,0,0,0,0,GL30.GL_COLOR_ATTACHMENT6,GL30.GL_COLOR_ATTACHMENT7});
            GL11.glViewport(viewport[0],viewport[1],width,height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_ALWAYS);GL11.glDepthMask(true);
            light.use();scope.uniforms(light,0,0);
            GameAccess.call("viewpoint.render.FrameUniforms","apply",null,new Class<?>[]{GlProgram.class,FrameContext.class},light,context);
            GameAccess.call("viewpoint.render.FrameContext","camera",context,new Class<?>[]{GlProgram.class},light);
            // A distant eye gives the native parallel view its constant direction.
            // Keep its world-space pixel footprint independent of eye distance.
            float[] m=receiver.inverse.get(new float[16]);
            float x=m[8],y=-m[10]*2.4494896f,z=m[9],length=(float)Math.sqrt(x*x+y*y+z*z),distance=10000f;
            light.set("uEye",x/length*distance,y/length*distance,z/length*distance);
            float dz=-m[6]/m[10],dx=m[4]+m[8]*dz,dy=m[5]+m[9]*dz;
            float span=(float)Math.sqrt(dx*dx+dy*dy)*Math.abs(IsoCameraMath.DY);
            light.set("uCamera",IsoCameraMath.NEAR,IsoCameraMath.FAR,1f,span/2f);
            if(light.has("vpIsoEnabled"))light.set("vpIsoEnabled",0f);
            light.setInt("uDebugView",0);light.setInt("uContactShadows",0);light.setInt("uLampShadowCount",0);
            light.setInt("uSpotCount",0);light.setInt("uTorchCount",0);light.setInt("uBlobCount",0);
            light.setInt("uLampTilesAcross",0);
            GameAccess.call("viewpoint.render.SurfacePass","lamps",null,new Class<?>[]{GlProgram.class,SceneData.class},light,context.scene);
            light.setInt("uLampTilesAcross",0); // lampWalk's existing full-list fallback
            GameAccess.call("viewpoint.render.SnowPass","state",GameAccess.field("viewpoint.render.WorldRenderer","snow",null),new Class<?>[]{GlProgram.class},light);
            Object targets=GameAccess.field("viewpoint.render.WorldRenderer","targets",null);
            bind(84,(Integer)GameAccess.field("viewpoint.render.Targets","noise2d",targets));
            bind(80,(Integer)GameAccess.field("viewpoint.render.Targets","water",targets));
            GameAccess.call("viewpoint.render.RainTextures","bind",GameAccess.field("viewpoint.render.Targets","rainTextures",targets),new Class<?>[0]);
            GameAccess.call("viewpoint.render.ToneLut","bind",GameAccess.field("viewpoint.render.Targets","toneLut",targets),new Class<?>[0]);
            light.set("uExposure",((Number)GameAccess.call("viewpoint.render.EyeMeter","exposure",null,new Class<?>[]{FrameContext.class},context)).floatValue());
            light.setInt("uAlbedo",9);light.setInt("uNormal",10);light.setInt("uEngineLight",11);light.setInt("uDepth",12);
            light.setInt("vpIsoSnowNativeColor",16);light.setInt("vpIsoSnowNativeWeight",17);
            bind(9,albedo);bind(10,normal);bind(11,engine);bind(12,depth);bind(16,color);bind(17,weight);
            GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            return true;
        }
    }

    private static String source() {
        ShaderPacks.Active active=GlProgram.pack();
        // Resolve live options once, after adding the selected tone functions.
        // A previously resolved source already contains the PackOptions block.
        Object body=GameAccess.call("viewpoint.platform.Shaders","expand",null,new Class<?>[]{String.class,viewpoint.platform.ShaderPack.class},"light.frag",active.pack);
        String source=(String)GameAccess.field("viewpoint.platform.Shaders$Body","head",body);
        String tail=(String)GameAccess.field("viewpoint.platform.Shaders$Body","tail",body);
        if(tail!=null)source+=tail;
        source=source.replace("#define VIEWPOINT_LAMP_LIST","// Native snow evaluates the existing bounded lamp list directly.");
        source=source.replace("out vec4 fragColor;","vec4 vpIsoSnowLit;").replace("fragColor","vpIsoSnowLit").replace("void main()","void vpIsoSelectedLight()");
        // Native mode has no matching screen-space AO/GI frame. Keep these
        // ancillary terms neutral, retaining the selected material light itself.
        source=source.replace("occlusionAt(ivec2(gl_FragCoord.xy), n)","vec4(n,1.0)")
                .replace("characterVisibility(pos, n)","1.0")
                .replace("contactShadowAt(ivec2(gl_FragCoord.xy), length(pos - uEye))","1.0")
                .replace("bounceAt(pos, n)","vec3(0.0)").replace("flashBounceAt(pos, n)","vec3(0.0)");
        source=source.replace("if (reflects(vUv, pos, albedo, normal, engine, mirrorN, mirrorRough))", "if (false)")
                .replace("reflectedOver(skyGlint, n, v, rough, f0, vUv, depth)", "skyGlint");
        source=source.replace("return min(lampField(i, pos, n), lampShadow(i, pos, n));","return 1.0;");
        // Parallel view direction uses the remote eye; snow detail falloff uses
        // the logical player distance, and the footprint is one native pixel.
        source=source.replace("vpIsoDistance(length(pos - uEye), pos)","length(pos - vec3(0.0,1.5,0.0))")
                .replace("away = length(pos - uEye)","away = length(pos - vec3(0.0,1.5,0.0))")
                .replace("away * pixel /", "pixel /");
        String grade=NativeWaterShader.overlayPost(active);
        int version=source.indexOf('\n')+1;
        source=source.substring(0,version)+"#undef PIPELINE_WATER_FFT\n"+source.substring(version);
        return NativeWaterShader.overlayOptions(source+"\nuniform float uExposure;\n"+grade+COMPOSE,active);
    }
    private int attachment(int format,int slot) {
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,width,height,0,slot==GL30.GL_DEPTH_ATTACHMENT?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA,GL11.GL_FLOAT,0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,slot,GL11.GL_TEXTURE_2D,id,0);return id;
    }
    private static void bind(int unit,int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL33.glBindSampler(unit,0);}
}
