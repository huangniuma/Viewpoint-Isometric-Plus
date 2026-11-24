# Build and CPU smoke verification

Use Windows PowerShell 5.1 or PowerShell 7 and a JDK with `java` and `jar` on `PATH` (tested with JDK 21). Install Project Zomboid 42.22, Viewpoint 0.1.7 and ZombieBuddy separately. The game runtime in `jre64/bin/java.exe` runs the smoke probes against the game's real dependency classes.

Set the three required dependency paths for your installation:

```powershell
$buildInputs = @{
    GameDirectory = 'D:\SteamLibrary\steamapps\common\ProjectZomboid'
    ViewpointJar = 'D:\SteamLibrary\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
    ZombieBuddyJar = 'D:\SteamLibrary\steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar'
}
.\build.ps1 @buildInputs
.\tests\Smoke.ps1 @buildInputs
```

The build uses Eclipse ECJ **3.44.0**, targeting Java 17 bytecode while reading the installed game's newer dependency classes. On the first build it downloads ECJ from [Maven Central](https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.44.0/ecj-3.44.0.jar) into `build/tools/` and verifies SHA-256:

```text
14C46DDC2E511C46C998DA728F6A80F13A689C920F3832402F7AD150F6A6188D
```

For an offline build, provide that exact compiler with `$buildInputs.CompilerJar = 'C:\Tools\ecj-3.44.0.jar'`. The checksum is checked for supplied compilers too. Dependencies and generated files stay outside version control; the compiler and game/mod JARs are not redistributed with this repository.

Outputs are staged as `build/VPIsometricLighting.jar`, `build/ViewpointIsometricPlusAddon.jar` and `build/manifest.json`. The manifest records compiler and input/output hashes. Build scripts never copy these outputs into `Contents/` or a game installation. Rebuilding removes only the generated `build/classes`, `build/compile-stubs` and `build/addon-classes` directories after checking their targets; linked output directories are rejected.

The renderer JAR packages only `vpiso/`; the managed addon JAR packages only `vip/`. `compile-stubs/` contains compilation ABI declarations and is excluded from production JARs and the smoke runtime classpath.

`Smoke.ps1` rebuilds the addon and compiles its two probes from source. `CameraProbe` checks camera projection, singular inputs and culling using the game's JOML. `LinkageProbe` runs JVM verification and reflects every renderer class, then checks the supported Viewpoint fingerprint and initial rendering scopes. Viewpoint 0.1.7 is pinned to SHA-256 `8d0d5a73227d3ba0724cab076f621435ebaed8ed2c73bfaf7d32a90b0a249e1f`; another build fails the compatibility check. These checks do not launch or attach to the game, create a GPU context, or measure game performance.

The other files in `tests/` preserve historical investigations and regressions. Many expect local `research/` captures, previous checkpoint JARs, prebuilt/woven fixtures, optional integrations, native libraries or a GPU. Those local dependencies are omitted from this export and the historical scripts still record their original paths. They are source references, not a portable complete test suite. The original development directory retains the full tools and evidence. Full rendering, driver and game-session verification remains a separate check.
