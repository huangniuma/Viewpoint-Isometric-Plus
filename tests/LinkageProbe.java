import java.nio.file.Files;
import java.nio.file.Path;

public final class LinkageProbe {
    public static void main(String[] args) throws Exception {
        int count = 0;
        try (var files = Files.walk(Path.of(args[0],"vpiso"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = Path.of(args[0]).relativize(path).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
                Class<?> type = Class.forName(name,false,ClassLoader.getSystemClassLoader());
                type.getDeclaredMethods();
                type.getDeclaredFields();
                count++;
            }
        }
        vpiso.Main.main(new String[0]);
        if (!vpiso.Main.compatible) throw new AssertionError("VP target fingerprint rejected");
        if (vpiso.IsometricBridge.capturing() || vpiso.IsometricBridge.rendering()) throw new AssertionError("Dirty initial scope");
        if (!vpiso.IsometricBridge.afterWorld(null,true)) throw new AssertionError("VP handling was overridden");
        System.out.println("JVM verification/linkage passed for " + count + " addon classes; target fingerprint and VP passthrough passed.");
    }
}
