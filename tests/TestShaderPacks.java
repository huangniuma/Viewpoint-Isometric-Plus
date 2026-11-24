import java.io.File;
import java.util.Map;
import java.util.function.Function;
import viewpoint.platform.PackOption;
import viewpoint.platform.ShaderPack;
import viewpoint.platform.ShaderPacks;
import vpiso.GameAccess;

/** Test-only external pack selection through VP's actual read-only discovery path. */
public final class TestShaderPacks {
    public static final File FOLDER = new File("C:/Users/Win10/Zomboid/viewpoint-shaderpacks");
    public static void select(String id,String preset,Map<String,String> values) {
        if(!id.equals("trueweathers")) {ShaderPacks.useBuiltIn(id,preset,values);return;}
        ShaderPack pack=(ShaderPack)GameAccess.call("viewpoint.platform.ShaderPacks","find",null,new Class<?>[]{String.class},id);
        if(pack==null) {
            GameAccess.call("viewpoint.platform.ShaderPacks","discover",null,new Class<?>[]{File.class},FOLDER);
            pack=(ShaderPack)GameAccess.call("viewpoint.platform.ShaderPacks","find",null,new Class<?>[]{String.class},id);
        }
        if(pack==null || pack.builtIn)throw new AssertionError("Actual external True Weathers pack not discovered");
        var chosen=pack.preset(preset);
        Function<PackOption,String> value=option->values.getOrDefault(option.id,chosen.valueFor(option));
        GameAccess.call("viewpoint.platform.ShaderPacks","use",null,new Class<?>[]{ShaderPack.class,Function.class},pack,value);
    }
    public static void selectLive(String id,String preset,Map<String,String> values) {
        ShaderPack pack=(ShaderPack)GameAccess.call("viewpoint.platform.ShaderPacks","find",null,new Class<?>[]{String.class},id);
        if(pack==null) {
            select(id,preset,values);
            pack=ShaderPacks.active().pack;
        }
        var chosen=pack.preset(preset);
        Function<PackOption,String> value=option->values.getOrDefault(option.id,chosen.valueFor(option));
        GameAccess.call("viewpoint.platform.ShaderPacks","use",null,
                new Class<?>[]{ShaderPack.class,Function.class,boolean.class},pack,value,true);
    }
    private TestShaderPacks() {}
}
