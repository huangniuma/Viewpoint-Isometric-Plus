# Development tools / 开发工具

`tests/` retains the historical regression and probe source files. The portable entry point is `tests/Smoke.ps1`; see [BUILDING.md](../BUILDING.md).

Other historical scripts require their original checkpoint artifacts, extracted runtime resources, diagnostic agents, local capture files or GPU tools. They are retained as reference source, not presented as a standalone test suite. Review dependencies and version assumptions before running an old script. Temporary runtime experiments are not enabled by installing the mod.

The complete local development archive remains beside the original `src/`, including `research/`, `build/`, the original `dev-tools/` inventory and historical packages. Those files were not removed or moved when this separate GitHub export was created. They are excluded from publication because the archive also contains installed third-party dependencies, extracted resources, machine-specific captures and generated history. Production JARs and player release ZIPs contain no diagnostic agents or automatic collection entry points.

`tests/` 保留历次回归与检查工具源码；可直接按构建文档运行的入口为 `tests/Smoke.ps1`。其他历史脚本依赖原开发目录中的对应版本、提取资源、临时代理或 GPU 工具，运行前需核对依赖和版本。完整本地工具、抓取数据、索引和历史构建仍在原始源码旁保留，本次独立导出没有删除或移动它们。安装模组不会自动启动诊断采样。

## 0.2.35 developer checks

`tests/Verify113.ps1` builds the real FrameStream/production-advice GPU ordering and buffer-reuse probe. It requires the pinned game/Viewpoint/ZombieBuddy JARs and compiler; existing historic regression helpers use local fixtures as documented above. `WallItems113Probe.java` covers wall attachments and native alpha stepping. Clock/temporal-jump negative probes are retained to document a rejected explanation, not shipped fixes. All live-observer sources and captures remain in the local development workspace per the preservation policy; none are loaded by the player package.


## 0.2.39–0.2.41 developer checks

The new retained probes cover auxiliary texture reuse and safe retirement117,
rotated cutaway size118, and furniture wall-face eligibility119. The complete
local archive includes their hidden GPU contexts, frozen baseline artifacts,
weaving fixtures, scripts, failed attempts and outputs. This export includes the
probe source under tests; numbered runners may still depend on that archive.
The portable entry point remains tests/Smoke.ps1.

Local validation passed same-input texture pixel parity, real-fence retirement,
50/100/200% cutaway boundaries, furniture118 negative controls, native furniture
fades, wall decorations, click behavior and four installed shader packs. The
user confirmed the furniture fix in0.2.41. The withdrawn0.2.38 camera experiment
is not part of the current implementation. Offline checks do not measure live
traversal FPS or establish compatibility with untested drivers/shader packs.


## 0.2.42–0.2.44 developer checks

PoolPit compatibility120/121 covers optional integration, shader-hook order,
swimming-height visibility and chunk restoration. The final policy enables
PoolPit integration only in isometric 3D; original appearance uses native water.
The experimental native PoolPit water composite is not part of the current source.

Smooth object fading122 reuses Viewpoint's existing weighted transparency while
preserving native fade timing, picking and cutaways. Local checks passed all four
installed shader packs, 57 focused GPU cases/72,729 checks, and 470 routing CPU
cases/5,907 checks with 14 negative controls. Baked floor LOD retains its original
dither path. These are independent CPU/hidden-GL results; live visual acceptance
of 0.2.44 remains pending and no FPS gain is claimed.

The probe sources are retained under tests. Full numbered runners, including
Verify122.ps1, also require local research fixtures, old checkpoint JARs, woven
classes and optional integrations not distributed here. The portable check is
still tests/Smoke.ps1. The complete original research and failed experiments stay
beside the original source and are not loaded by the player package.


## 0.2.45–0.2.47 developer checks

These updates preserve complementary old/new geometry coverage separately from
native visibility, reduce isometric rain haze, improve precipitation visibility,
and retain smooth garage-room model handoffs and native crown fading/roots.
PVNV0.3.17 integration preserves provider flags, wind/snow shader hooks and
resident-model ownership, with native fallback when no replacement is ready.

Source128Probe.java covers the actual woven Recipe.model -> Cooker.models ->
ChunkMeshData source pipeline and native record exclusion. It reproduces127's
missing source identity: PVNV calls Recipe.model directly and bypasses the normal
PackGather.place assignment. The repair copies only valid native source identity
for recognized PVNV models. Earlier127 consumer tests injected modelObjects;
they did not establish correct provider production or visual success.

NativeFade127Probe.java and the PVNV/crown probes are retained as regression
references. They require optional PVNV classes or local woven/research fixtures;
they are not part of the portable smoke entry point. Full source, dependencies,
failed fixtures, negative controls and outputs remain in the original local
development archive. No live observer or third-party JAR is included here.
Four installed shader packs passed combined provider links; offline results do
not establish live visual acceptance, other-driver compatibility or FPS gains.
