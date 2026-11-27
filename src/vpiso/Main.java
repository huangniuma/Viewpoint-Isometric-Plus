package vpiso;

public final class Main {
    public static boolean compatible;
    public static void main(String[] args) {
        try {
            var resource = ClassLoader.getSystemResource("viewpoint/FP.class");
            if (resource == null || !(resource.openConnection() instanceof java.net.JarURLConnection connection)) {
                throw new IllegalStateException("Viewpoint.jar resource unavailable");
            }
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = connection.getJarFileURL().openStream()) {
                byte[] bytes = new byte[65536];
                int size;
                while ((size = input.read(bytes)) > 0) digest.update(bytes, 0, size);
            }
            String hash = java.util.HexFormat.of().formatHex(digest.digest());
            compatible = hash.equals("8d0d5a73227d3ba0724cab076f621435ebaed8ed2c73bfaf7d32a90b0a249e1f");
            System.out.println(compatible
                ? "[VPISO] Viewpoint Isometric Plus 0.2.37 ready for Viewpoint 0.1.7; activation: Advanced > Addons; settings: Options > Viewpoint Isometric Plus."
                : "[VPISO] inactive: unsupported Viewpoint.jar SHA-256 " + hash);
        } catch (Exception error) {
            System.out.println("[VPISO] inactive: " + error);
        }
    }
}
