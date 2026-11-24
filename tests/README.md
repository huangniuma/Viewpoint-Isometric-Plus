# Tests / 测试

Run the portable CPU verification entry point [Smoke.ps1](Smoke.ps1) using the dependency arguments in [BUILDING.md](../BUILDING.md). It rebuilds the addon, exercises camera/culling calculations against the real game libraries and verifies addon class linkage without launching the game or opening a GPU context.

The remaining numbered scripts and probes preserve the development history. Many refer to local checkpoint JARs, research fixtures, shader expansions, installed mods or GPU/native tools absent from this export. They are useful source references, but are not a portable automated suite. The current smoke check does not reproduce historical visual regressions, verify an NVIDIA driver, run a multiplayer session or measure gameplay performance.

独立 CPU 检查入口为 `Smoke.ps1`。其余历史脚本与检查源码保留供复用，部分依赖未上传的本地版本、场景数据和 GPU 工具；不应将全部历史脚本视为可直接运行的测试套件。CPU 检查不代替画面验证、NVIDIA 驱动实测、联机测试或实际跑图性能测量。
