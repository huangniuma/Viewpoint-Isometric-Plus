# Viewpoint Isometric Plus / Viewpoint 等距增强

Version / 版本：0.2.35  
Authors / 作者：Hesin, GPT6  
AI-assisted writing / AI 辅助撰稿：ChatGPT

在《僵尸毁灭工程》原版等距视角中使用 Viewpoint 的阴影、色彩、水面、草地以及积雪与足迹，并可选择开启 3D 模型与视角旋转。各项效果可独立设置，保留已有配置。

Use Viewpoint shadows, color, water, grass, accumulated snow and tracks in Project Zomboid's original isometric view, with optional 3D models and camera rotation. Effects are configured independently; existing preferences are retained.

## 启用与设置 / Activation and settings

1. 启用 Viewpoint、ZombieBuddy 和本模组，然后完全重启游戏。 / Enable Viewpoint, ZombieBuddy and this mod, then restart the game completely.
2. 按 Delete → 高级 → 附加模组，找到 **Viewpoint 等距增强**。首次使用时在「未启用」中启用；若 Viewpoint 要求确认，请检查其信息后按提示操作。 / Press Delete → Advanced → Addons and find **Viewpoint Isometric Plus**. Enable it from Inactive the first time; review and respond to Viewpoint's confirmation if shown.
3. 启用后在「运行中」显示；可关闭，也可查看模组信息。关闭不会清除各项功能的设置。 / The entry appears in Running when active. Disable it or inspect its information from the same list. Disabling retains feature preferences.
4. 在 **Delete → 选项 → Viewpoint 等距增强** 调整全部设置，附加模组卡片的设置入口也可跳转至此。 / Configure all features in **Delete → Options → Viewpoint Isometric Plus**; the addon's Settings shortcut opens this page too.

独立页面分为常规、模型与视角、阴影、色彩、环境和关于。设置会自动保存；总开关控制等距增强功能，附加模组列表中的关闭操作也会停止增强渲染。

The dedicated page has General, Models and camera, Shadows, Color, Environment and About tabs. Settings save automatically. The master switch controls isometric enhancements; disabling the native addon entry also stops enhanced rendering.

## 功能 / Features

- 阴影：太阳、月亮及固定灯光的动态投影；强度、柔和度、距离与质量。 / Shadows: dynamic sun, moon and fixed-lamp shadows, with strength, softness, distance and quality controls.
- 轻量化动态阴影：默认关闭。开启后仅角色、僵尸、动物、汽车、树木、房屋，以及通过“放置”摆出的背包物品投射动态阴影；家具、灌木及小杂物等仍能接收其他物体的阴影。 / Lightweight dynamic shadows: off by default. Limits casters to characters, zombies, animals, vehicles, trees, buildings and placed inventory items. Furniture, bushes and other surfaces still receive shadows.
- 色彩：画风强度、曝光、饱和度与对比度。 / Color: style strength, exposure, saturation and contrast.
- 环境：VP 水面和草地，可独立于 3D 模型使用。草地质量跟随 VP 的世界设置。 / Environment: VP water and grass, independent of 3D models. Grass quality follows VP's World settings.
- 模型与视角：可选 3D 模型；开启模型及旋转后，按住鼠标中键移动以转向，松开保持角度；可恢复原版方向。 / Models and camera: optional 3D models. With models and rotation enabled, hold the middle mouse button and move to rotate; release to keep the angle, or restore the original direction.
- 沿用现有原版交互、隐藏规则与兼容修复。 / Retains the existing native interaction, visibility and compatibility fixes.

## 兼容 / Compatibility

已验证环境：Project Zomboid 42.22.2、Viewpoint 0.1.7、ZombieBuddy 2.3.0。支持 Default / Modern、Vivid、Somber、Library 和 True Weathers。可使用「启用兼容图像配置」调整受支持的图像配置。

Verified environment: Project Zomboid 42.22.2, Viewpoint 0.1.7 and ZombieBuddy 2.3.0. Supported packs: Default / Modern, Vivid, Somber, Library and True Weathers. Use Enable compatible graphics to apply a supported configuration.

普通联机仅需按 Viewpoint 和 ZombieBuddy 的安装要求在客户端启用。本模组的 Java 文件位于客户端目录，专用服务器会跳过加载；不增加网络包、不替代原版权限或动作校验。本地分屏仍沿用原有支持边界。断线重进或切换存档会清理上一会话的临时状态，并保留设置。

For online play, follow Viewpoint and ZombieBuddy's client installation requirements. The Java addon is client-only and is skipped by dedicated servers. Native action and permission checks remain in control, and no custom network packets are added. Existing local split-screen limits remain. Reconnecting or changing saves clears transient session state while retaining preferences.

附加模组入口控制配套的 ZombieBuddy 渲染组件；Viewpoint 信息页的托管运行开销只涵盖入口本身。保留内部模组 ID `VPIsometricLighting` 和已有 `vpiso.*` 设置键，无需重新配置。封面使用 Hesin 提供的 Viewpoint Isometric Plus 图片。

The native addon entry controls the companion ZombieBuddy renderer. Managed runtime costs in Viewpoint's information page cover the entry itself. The internal mod ID `VPIsometricLighting` and saved `vpiso.*` keys are retained. Cover artwork uses the Viewpoint Isometric Plus image supplied by Hesin.

## 修改与再发布 / Modification and redistribution

允许所有人修改、二次创作和再发布本模组，无需另行申请许可。授权适用于本模组的原创内容；第三方内容仍遵循各自的许可。详见 [LICENSE](LICENSE) 与 [NOTICE.md](NOTICE.md)。

Everyone may modify, create derivative works from, and redistribute this mod, including publishing modified versions, without requesting further permission. This covers the mod's original contributions; third-party content retains its own terms. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).

## 0.2.35

- 修复开启 3D 模型后进入 VP 视角时，僵尸偶发一帧拉伸、刺出巨大面片的问题。补全 GPU 骨骼计算与后续缓冲区更新的同步，保留现有动画及模型加速。 / Fixes intermittent single-frame zombie mesh spikes after using 3D isometric models and entering Viewpoint. Orders GPU skeleton writes before later shared-buffer updates while retaining animation and model acceleration.
- 墙上挂画等普通附墙装饰现在跟随对应墙面的透视渐隐与恢复，保留门窗、灯开关、容器的既有规则。 / Ordinary wall-mounted decorations now fade out and back in with the corresponding wall cutaway, preserving existing door, window, switch and container rules.
- 修复工具与失败对照继续保留在源码旁，不随玩家安装包加载。更新后请完全重启游戏。 / Investigation tools and failed comparisons remain alongside the source and are excluded from the player package. Restart the game completely after updating.

## 0.2.34

- 优化多物件区块的隐藏绘制计划：只对实际重叠的几何区段查询隐藏身份，减少重复查找与临时装箱。 / Checks hidden ownership only for intersecting geometry spans when building native visibility draw plans, reducing repeated lookups and temporary boxing in dense chunks.
- 优化物件阴影范围计算及 3D 接影／点击来源数据整理，减少重复反射调用与临时参数分配；继续读取当前模型、位置与透明度。 / Reduces reflective dispatch and temporary arguments in native object shadow bounds and 3D receiver/pick source preparation, while preserving current model, placement and alpha reads.
- 保留现有功能、画质、阴影距离、刷新和相互作用，以及 0.2.33 的树木光照兼容修正。原版与 PZO 结果对照及 GPU 回归通过；局部离线基准不代表实际跑图 FPS 或 1% low 的提升幅度。 / Preserves features, quality, shadow distance, refresh, interactions and the tree shader repair from 0.2.33. Native/PZO parity and GPU regressions pass; isolated benchmarks do not establish live traversal FPS or 1% low gains.

## 0.2.33

- 修复 Library 光影包及包装主光照入口的模组组合中，原版树木光照着色器因变量声明顺序错误而编译失败、导致增强效果停止的问题。 / Fixes native tree-light shader compilation failures caused by declarations following helper functions in Library and wrapped light-entry configurations.
- 保留树木接影、原版透明度、雨雪与画风处理，以及 0.2.32 的 NVIDIA 数组索引修正。 / Preserves tree shadow reception, native transparency, weather and selected-pack styling, including the NVIDIA array-index fix from 0.2.32.
- 已增加严格 GLSL 编译和前置辅助函数对照；各扩展模组的完整组合及报错用户的 NVIDIA 驱动仍需游戏内确认。更新后请完全重启游戏。 / Adds strict GLSL and helper-wrapper regression checks. Full addon combinations and the reporting NVIDIA driver still need in-game confirmation. Fully restart the game after updating.

## 0.2.32

- 针对NVIDIA显卡首次进入世界时原版外观特效着色器链接失败（C1068），修正手电筒／车灯无阴影参考分支的数组索引写法。 / Addresses NVIDIA C1068 native-effects shader link failures during initial world load by keeping unshadowed spotlight indices valid during compiler optimization.
- 补全物件、地板和桌面接影变量初始化，减少不同驱动的编译差异。 / Explicitly initializes native owner, floor and table receiver values for consistent driver compilation.
- 保留动态阴影、手电筒和车灯功能；严格常量索引编译检查和本机GPU新旧输出对照通过。RTX2060实际驱动仍需报错用户确认，未将本机AMD测试描述为NVIDIA实测。 / Preserves shadows, flashlights and vehicle lights. Strict constant-index checks and local GPU output comparisons pass; RTX2060 driver confirmation remains pending.
- 更新后请完全重启游戏。首次编译失败会让旧会话停止增强绘制，仅切换总开关无法重新加载JAR。 / Fully restart the game after updating; toggling the master switch does not reload the JAR or recover the old failed session.

## 0.2.31

- 依据原版外观和3D在250%缩放下的实际跑图计时，优化隐藏规则的热路径调用和原版绘制提交。 / Uses live250% traversal stage measurements to target native submission and3D visibility hot paths.
- 3D每次采集继续执行相同的原版查询、分类和渐变，减少反射、坐标装箱和重复字段查找；墙体遮挡检查省去不影响结果的工作。 / Retains native visibility, classification and fade queries while reducing reflection, coordinate boxing, repeated field lookups and irrelevant wall-cap checks.
- 原版外观在当前world pass内省去未变的附加uniform写入；原版blend、framebuffer和drawbuffer仍实时读取。 / Native appearance avoids unchanged addon uniform submissions within the current world pass, while native blend/framebuffer/drawbuffer state remains live.
- 本次不降低阴影质量、距离或刷新规则。地图构建、原版渲染与等待仍会造成尖峰；尚未验证新版本能将现场抖动恢复到4ms。 / Preserves shadow quality, distance and refresh rules. Map building, native rendering and waits still cause spikes; this release has not been shown to restore live jitter to4ms.

## 0.2.30

- 专门优化关闭3D模型时的原版外观阴影合成，避免计算随后会被更精确表面或完整地板光照覆盖的结果。 / Optimizes original-appearance shadow composition by avoiding lighting calculations that are replaced by more precise surfaces or complete floor lighting.
- 复用光照上下文与状态缓冲，减少逐帧临时分配；每次仍重新读取场景与绘制状态。 / Reuses lighting context and state buffers to reduce per-frame allocations while still reading current scene and rendering state on every call.
- 保留阴影相互作用、树冠稳定接影、桌沿分层接影、人物和车辆接影，以及原有画质、距离和更新规则。 / Preserves shadow interactions, stable canopy reception, layered table-edge reception, actor/vehicle reception, quality, distance and update rules.
- 同输入GPU对照画面一致；局部合成和分配基准不代表现场跑图FPS或1%low提升幅度。 / Identical-input GPU comparisons preserve output; isolated composition/allocation benchmarks do not establish live traversal FPS or1% low gains.

## 0.2.29

- 来源回收只在确有待处理来源时运行，避免跑图后逐帧遍历、重新装箱已经空闲的来源编号。保留延迟回收、缓存重绘和编号复用规则。 / Runs source retirement only when sources await collection, avoiding repeated free-ID scans and boxing while preserving deferred reuse and repaint semantics.
- 原版渲染复用常见楼层和来源结束的不可变指令，减少地图刷新时的临时对象。 / Reuses immutable common-floor and source-end commands in native rendering.
- 3D接影与点击来源表使用可复用数值数组，保留原有稳定排序、透明度、精确身份和GPU数据。 / Uses reusable primitive source-range arrays with identical stable ordering, alpha, exact identities and GPU table data.
- 独立对照验证CPU／分配改善；现场跑图的偶发停顿与1%low效果仍需游戏内测量。不降低阴影距离、画质或功能。 / Isolated checks verify CPU/allocation improvements; live traversal stalls and1% lows require gameplay measurement. Preserves quality, shadow distance and features.

## 0.2.28

- 3D模式的物件隐藏分类与透明度更新绑定原版调用，减少逐物件的反射、参数数组和装箱开销。原版函数、调用顺序和渐变步进继续保留。 / Binds the same native object-classification and alpha-update calls once, reducing per-object reflection, parameter arrays and boxing while retaining native behavior and update order.
- 原版和PZO来源数据、点击身份、四玩家透明度与内部回调均通过精确对照。 / Exact native/PZO ownership, pick identity, four-player alpha and internal callbacks are verified.
- 本次不降低画质或功能；离线局部基准反映CPU调用与分配改善，不代表现场FPS或1% low提升幅度。 / Preserves quality and features; isolated CPU/allocation results do not establish live FPS or1% low gains.

## 0.2.27

- 原版外观仅剔除完全处于画面外的普通接影表面，保留屏外物体投进画面内的阴影；树冠、模型、角色和车辆绘制及接影继续保留。 / Native appearance rejects only fully offscreen ordinary receiver surfaces, retaining offscreen shadow casters and all foliage/model/actor/vehicle paths.
- 3D模式的相机透视、切墙和去顶变化继续使用有效的物理阴影缓存，减少整套太阳及局部光源缓存的重复重建；地图几何与光源变化仍按原机制刷新。 / Camera cutaway changes reuse valid physical-shadow caches; geometry and light changes retain their existing refresh rules.
- 不降低阴影距离、分辨率或刷新频率；新增独立几何提交量与完整接影输出对照。 / Preserves shadow distance, resolution and update policy, with isolated submission and receiver-output parity checks.
- 离线验证不代表现场跑图FPS提升幅度；实际收益仍取决于地图、模组和硬件。 / Offline validation does not measure live traversal FPS gains; results depend on scene, other mods and hardware.

## 0.2.26

- 跑图和地图刷新时，仅更新发生变化的接影来源表行；保留原有物件身份、完整精度、删除回收与阴影相互作用。 / Traversal uploads only changed shadow-source rows, preserving exact identities, precision, deletion/reuse and shadow interactions.
- 同一次原版绘制共用一份实时状态快照，减少重复查询；不会跨帧缓存原版绘制状态。 / Reuses one live state snapshot within each native draw, without caching native state across frames.
- 3D物件捕获合并重复来源表，减少临时分配，保留原版透明度、物件处理顺序与点击身份。 / Merges duplicate3D ownership tables to reduce allocations while retaining native alpha, processing order and pick identity.
- 改动通过独立性能基准及画面/状态回归；实际跑图帧率仍随场景、其他模组和硬件变化。 / Verified with isolated benchmarks and visual/state regressions; actual traversal frame times depend on scene, other mods and hardware.

## 0.2.25

- 重叠树冠使用连续的接影表面和深度，避免角色移动时在交叉树叶卡片之间反复切换。保留树木投影、外部接影、原版贴图与渐变，以及角色和车辆接影。 / Overlapping crowns use continuous receiver surfaces and depth instead of alternating between internal leaf cards. Tree casting, external reception, original artwork and fading, and character/vehicle reception remain enabled.
- 本次临时版本已确认原版外观与 3D 模式均不再出现现场树冠闪烁，角色和车辆接影正常。 / Live temporary verification confirmed stable crowns in both original artwork and 3D modes, with character and vehicle shadow reception retained.

## 0.2.24

- 原版外观帧生成新增人物／僵尸／动物骨骼及车辆运动数据与物体实际深度，修正运动物体误用背景相机运动产生的拖影。半透明贴图边缘采用邻近物体运动覆盖，保留墙体遮挡。 / Original-view frame generation now supplies posed-object and vehicle motion plus physical object depth. Native sprite edges use nearby object motion while retaining native occlusion.
- 单独开启帧生成时也捕获必要的移动模型，不要求开启动态阴影；3D 模型继续使用 Viewpoint 的原有运动输入。 / Frame generation alone captures moving models without requiring dynamic shadows; 3D rendering retains Viewpoint's existing motion inputs.
- 实际游戏临时版本反馈“好很多了”。离线包含 0.2.23 失败对照、实际原版帧生成输入、骨骼运动和遮挡检查；此修正减轻本次异常，不保证消除所有插帧遮挡伪影。 / The temporary live version was reported substantially improved. Retained checks cover the 0.2.23 failure, actual native inputs, bones and occlusion; residual interpolation artifacts can depend on the scene.

## 0.2.23

- 树木排序每次捕获只读取一次坐标，复用排序数值存储；保留原有两次稳定排序，避免移动、旋转时反复创建投影临时数组。树木绘制按原生程序所有者恢复绑定，每棵树复用相同的根部矩阵。 / Snapshots tree sort keys once per capture and reuses numeric storage while preserving both stable sorts. Tree fills restore their native run program and reuse the root matrix within each tree.
- 修复 Viewpoint 过渡截图、原版退出保存画面备份误读地面辅助图层产生黑色树木、建筑和人物轮廓；只在复制期间读取正常颜色图层，完整恢复原读取状态。 / View transitions and native save-picture backups copy visible color rather than the floor auxiliary attachment, restoring the original read selection afterwards.
- 受控树木排序基准和绘制回归通过；最低帧和移动稳定性的实际收益取决于场景，未以离线结果宣称游戏帧率涨幅。 / Controlled sort benchmarks and rendering regressions pass; actual low-FPS and movement gains remain scene dependent.

## 0.2.22

- 3D 模型下的原版树木使用所选 Viewpoint 光影包的真实材质光照，统一天空／月光、灯光、夜间色彩和雨雪环境，不再直接把原版缓存亮度作为成品颜色放入 HDR。 / Native tree artwork in model mode now uses the selected Viewpoint material light, including sky/moon, lamps, night color and weather, instead of inserting native cached brightness as finished HDR color.
- 恢复树干底部展开的树根，按同一屏幕射线与所属地面接合；保留前景遮挡、树冠／树干分层、原版渐变及同行树冠排序。 / Restores flared root artwork by joining its skirt to its own ground along the same screen ray. Foreground occlusion, crown/trunk separation, native fading and stable same-row ownership remain.

## 0.2.21

- 面向大视野跑图优化 3D 场景采集：将南北／东西墙体能力的重复物件扫描合并，减少原版隐藏与透明度访问的反射检查和临时分配；保留实际原版判定、更新顺序、可见范围、灯光刷新和阴影质量。 / Reduces repeated wall-cap object scans, reflective visibility/alpha queries and temporary allocations during wide-view 3D capture. Actual native decisions, update order, view range, lighting refresh and shadow quality remain intact.
- 在“通用”添加“帧生成基础帧率”：跟随游戏、30、40、50、60，默认 30。只在帧生成已成功运行时限制真实帧，关闭、失败、切换视角或离开游戏后恢复原上限；不改写游戏或 Viewpoint 保存的帧率设置。30 基础帧配合插帧的目标约为 60 显示帧，实际效果取决于基础帧是否稳定。 / Adds Frame generation base rate: follow game, 30, 40, 50 or 60, default 30. Applies only after successful generation capture; off, failure, view/session changes and menus retain original caps without rewriting saved settings. Thirty real frames targets roughly sixty displayed frames; stability depends on sustainable base performance.
- 不启用 3D 模型时仍保持原版贴图原生分辨率；3D 模式保留所选 Viewpoint 升频及渲染比例。基础限帧不改变分辨率，也不等同于 Viewpoint 独立模拟／重复绘制机制。 / Native artwork remains at native resolution; model mode retains the selected Viewpoint upscaler and render scale. The base cap changes neither resolution nor Viewpoint's independent simulation/replay architecture.

## 0.2.20

- 优化原版外观的动态阴影合成：跳过距离范围外的接影计算。保留色彩画风、阴影范围、质量和刷新时机。 / Optimizes native-artwork shadow composition by skipping shadow reception outside its existing range. Style, range, quality and update timing are preserved.
- 优化 3D 模式的场景采集：同一帧复用同一物件和地格的原版隐藏判定，下一帧重新读取；合并多楼层重复的可视地格请求。保留透视、渐变、点击、天气及物理投影。 / Reuses native object and square visibility decisions within one captured frame, refreshes them on the next capture, and unions repeated multi-level tile requests. Native cutaways, fading, picking, weather and physical shadows are retained.
- 保留独立 CPU／GPU 性能基准和新旧画面对照工具。局部基准耗时不能等同于完整存档的帧率提升；实际收益随场景和瓶颈变化。 / Retains standalone CPU/GPU benchmarks and old/new image comparisons. Local timings do not represent whole-save FPS improvements; gains depend on the scene and bottleneck.

## 0.2.19

- 在「选项 → Viewpoint 等距增强 → 通用」新增独立的 **帧生成** 开关，默认关闭并自动保存，支持原版外观和 3D 模型。与 Viewpoint 其他视角的开关独立；启用兼容图形配置时保留这两个开关及升频选择。 / Adds an independent, persistent **Frame generation** switch under Options → Viewpoint Isometric Plus → General, off by default, supporting original artwork and 3D models. Viewpoint's other-view preference remains independent; compatible graphics preserves both switches and the upscaler.
- 原版外观使用同一帧原版深度、实际相机映射及光流；3D 模型沿用 Viewpoint 的模型深度与运动向量。沿用其帧生成原生库、无界面画面采集、界面合成和额外显示时机，不增加游戏模拟次数或修改原版动作。 / Native artwork supplies native depth and actual camera motion with optical flow; model mode uses VP's model depth and motion vectors. Both retain Viewpoint native frame generation, HUD-free capture, UI composition and presentation, without advancing simulation or changing native actions.
- 平滑旋转保留历史，较大角度突变、缩放、尺寸及会话／模式切换重置历史；保留已有 FSR 分辨率缩放、阴影、高亮、天气及交互。需要 Viewpoint 帧生成库与兼容硬件；实际流畅度受基础帧率、显示器及场景影响。 / Retains history during smooth rotation and resets on large jumps, zoom, size or session/mode changes. Existing FSR scaling, shadows, highlights, weather and interaction remain. Requires VP frame-generation libraries and compatible hardware; smoothness depends on base frame rate, display and scene.

## 0.2.18

- 兼容 Viewpoint 的分辨率缩放与 FSR 3（Viewpoint 中的 FSR 选项）、XeSS、DLSS 选项；兼容配置按钮保留所选升频。3D 模型沿用 Viewpoint 的输入比例、输出尺寸及硬件／原生库要求，原版外观保持原生分辨率，不对原版贴图额外降采样。 / Allows Viewpoint resolution scaling and FSR 3, XeSS and DLSS selections, preserving the selected upscaler when applying compatible graphics. 3D models use Viewpoint render/output sizes and native-library/hardware requirements; original sprites remain at native resolution.
- 修正原生升频使用的等距相机近远面，保留正确的时间抖动、运动向量及历史重置；低分辨率点击按实际像素覆盖与抖动对齐，容器高亮保持输出分辨率，原版视野／水面深度按输出坐标还原。 / Corrects vendor camera clips, retains temporal jitter/motion/reset, maps low-resolution picking footprints, keeps container highlights at output resolution and aligns native visibility/water depth.
- FSR 已在本机 AMD 显卡上运行独立原生 GPU 与实际渲染链夹具；XeSS／DLSS 验证范围是共用参数和接入，其原生运行仍取决于硬件与依赖。屏幕空间反射（SSR）、补帧和 Iris 限制保持。完整存档画面仍需游戏内验收。 / FSR is tested through native GPU dispatch and the actual renderer on the local AMD GPU. XeSS/DLSS checks cover shared inputs/integration, with native execution dependent on hardware and libraries. SSR, frame-generation and Iris restrictions remain. Live-save visual acceptance remains pending.

## 0.2.17

- 修正等距 3D 模式同行树冠交叠时深度与排序不一致引起的抢显示、闪烁；按实际相机与绘制深度统一排序，保留树干和树叶的独立层次。 / Fixes competing same-row crowns by aligning tree submission order and rendered depth in isometric 3D mode, retaining separate bark and leaf layers.
- 深度错位有明确上限，并按密集程度约束叶层前移；保持缩放、旋转、邻树增减及大型树冠／树干身份一致。 / Bounds total depth staggering and adapts leaf separation for dense rows across zoom, rotation, neighbour changes and large-tree parts.
- 保留原版透视渐变、深度遮挡、阴影几何及雨雪雾幕；不修改游戏或 Viewpoint 文件。 / Retains native cutaway fading, depth occlusion, shadow geometry and weather without editing game or Viewpoint files.

## 0.2.16

- 原版视角的角色投影读取原版当前透明度；僵尸／动物完全淡出后不再留下动态阴影，渐变过程中阴影同步减弱。 / Native-view character casters follow current native alpha, fade their shadows together, and stop casting when fully hidden.
- 3D 模式不再用格子可见布尔值立即剔除僵尸与动物，改为沿用原版淡入淡出；覆盖身体和附属部件，并保留贴图合成、既有裁剪及原版特殊视野规则。 / Uses native zombie/animal fades instead of immediate square-visibility culling, including body and attached parts while preserving composited appearance, clipping and native special visibility rules.
- 不额外推进透明度、不重跑视野更新；保留客户端玩家的原版可见目标判定及真正 Viewpoint 视角的原有路径。 / Adds no alpha step or LOS update, retaining native client-player visibility targets and the ordinary Viewpoint camera path.

## 0.2.15

- 修正等距 3D 模式下 TrueWeathers 世界细节与当前 Viewpoint 法线、相机深度格式不一致造成的紫色雪斑和建筑／窗户亮纹。 / Fixes purple snow patches and bright wall/window streaks caused by mismatched normals and camera depth in TrueWeathers world details under isometric 3D rendering.
- 世界细节每帧只绘制一次，在雨雪雾幕之前处理；保留天气遮罩，并避免对 Viewpoint 已有积雪再次涂色。 / Draws world details once before precipitation haze, retaining weather masking and avoiding a second coating over existing Viewpoint snow.
- 兼容处理仅在等距 3D 绘制期间启用；TrueWeathers 未安装时直接跳过。保留原版视角积雪与足迹、现有设置和原版操作。 / Adaptation is scoped to isometric 3D rendering and skipped when TrueWeathers is absent; native snow, tracks, existing settings and interaction remain supported.

## 0.2.14

- 修正原版视角积雪的水平截断及远处缺雪，按原版帧缓冲实际像素读取楼层遮挡。 / Fixes native snow cutoff and distant missing snow with exact framebuffer pixel addressing.
- 使用当前光影包的雪材质光照与色调处理，保留雪面起伏、踩踏足迹及天气积雪状态。 / Uses the selected pack's snow material lighting and tone, retaining surface relief, tracks and accumulation state.
- 将原版冬季雪贴图正确记为地面；角色脚部按实际深度埋雪，原版物件与植物按自身贴图位置和所属楼层覆盖，修正底边与半透明边缘残留。 / Classifies native winter snow as floor, buries character soles by actual depth, and covers native sprite bases and alpha edges using their own position and owning storey.
- 原版与 3D 物件外形仍各自保留；接入相同雪材质不代表两种画面逐像素一致。 / Native sprites and 3D shapes retain their respective appearance; shared snow material does not imply identical pixels.

## 0.2.13

- 在「选项 → Viewpoint 等距增强 → 环境」新增默认关闭的 **Viewpoint 积雪与足迹**。关闭 3D 模型时，也可显示 Viewpoint 的真实地面积雪厚度、踩踏足迹及其随天气的变化。 / Adds the independent **Viewpoint snow and tracks** switch under Options → Viewpoint Isometric Plus → Environment, off by default, bringing VP ground snow and contact tracks to the original world appearance.
- 积雪质量沿用当前光影包的 Snow 设置，不能设为 Off；需要地图上已有积雪。原版家具、人物、墙体、半透明边缘、室内与楼层遮挡保持，3D 模式沿用 Viewpoint 原有积雪设置。Library 本身没有积雪质量选项，使用提供 Snow 的光影包（Default、Vivid、Somber、True Weathers）开启此效果。 / Requires existing ground snow and a selected shader pack with Snow above Off; retains native foreground, alpha edges, indoor/storey visibility and VP's existing 3D snow behavior. Library has no Snow option; choose Default, Vivid, Somber or True Weathers for this feature.

## 0.2.12

- 修正 3D 模式缩放后室内重新出现落雨：处理远距等距相机还原地板深度时的楼层边界误差，避免误读为缺失的地下层，继续沿用原版屋顶和室内判定。室外、窗外和露天屋顶降水保留。 / Stabilizes storey selection at reconstructed floor boundaries after zoom, preventing the remote isometric camera from treating an indoor floor as a missing basement; native shelter decisions and outdoor precipitation remain in use.
- 保留树木雨幕、普通雾、透视渐变和已有阴影、交互、画质设置。 / Retains tree rain haze, normal fog, native fading, existing shadows, interaction and quality settings.

## 0.2.11

- 3D 模式根据同一帧可见表面的原版楼层及遮雨状态过滤室内、屋檐下的雨雪，保留窗外、露天与可见屋顶上的降水；未下雨雪时不上传遮雨纹理。 / Filters rain and snow over native sheltered receivers in 3D mode, retaining outdoor, window-view and exposed-roof precipitation; shelter textures upload only when precipitation is drawn.
- 原版树木在雨幕之前写入颜色和深度，让大雨、雨雪天气的压色同时作用于树木；保留普通雾、画风、树冠渐变和旋转。 / Draws native trees before rain haze so their color and depth receive the same weather veil, retaining normal fog, style, crown fading and rotation.

## 0.2.10

- 修正原版贴图下桌面、桌沿和野餐桌座板错误接收自身下部模型投影的问题，保留其他物体投射的阴影；多块家具沿用自己的来源身份。 / Fixes wrong tabletop, lip and picnic-seat receivers over native artwork while retaining external shadows and each furniture source identity.
- 调整绘制来源记录的回收时机，避免绘制缓存引用已失效的接影数据。现有功能、画质和设置保持一致。 / Retains pending receiver records until the world pass completes, preserving current features, quality and settings.

## 0.2.9

- 优化共用反射访问缓存，减少每帧锁竞争、字符串和临时缓冲分配；视野拓扑只处理实际请求的格子。现有画质、阴影距离、设置和功能保持一致。 / Optimizes shared reflection access, temporary buffers and requested visibility tiles while retaining existing quality, distances, settings and features.
- 联机／重连兼容：新世界或本地角色更换时重置临时渲染、输入和相机状态；卸载或移除的物件不再作为点击目标。普通网络联机保持客户端渲染，服务器仍由原版处理动作。 / Improves session/reconnect handling and rejects unloaded/removed click targets. Rendering stays client-side and native actions retain server authority.
- 保留原版操作、五个已支持光影包、PZO和Project Reflections兼容。新增联机验证为独立客户端夹具，尚未进行双客户端实际联网验收；性能数字为局部基准，非整场景FPS承诺。 / Retains native interaction and existing pack/mod compatibility. Multiplayer validation uses independent client fixtures, not a two-client network playtest; performance results are local benchmarks rather than whole-scene FPS claims.

## 0.2.8

- 修正移动鼠标后点击门／容器时目标被丢弃：每帧收取已完成的选取图，并查询当前鼠标像素；点击辅助仍为6屏幕像素。 / Fixes lost door/container clicks after pointer movement by collecting completed selection maps every frame and querying the current input pixel; the assist remains six screen pixels.
- 原版左键派发前刷新目标，轻微抖动时保持按下／松开为同一物件，拖动、界面接管与相机切换会取消；保留原版距离、阻挡和动作规则。 / Refreshes the target before native click dispatch and keeps press/release identity under small pointer jitter, with cancellation for drags, UI capture and camera changes; native reach, obstruction and action rules are retained.

## 0.2.7

- 改进 3D 模式门、墙灯和容器的点击：保留透视淡化物件的稳定交互轮廓，材质透明空洞与实墙仍参与遮挡判断。 / Improves 3D door, wall-light and container selection with stable interaction silhouettes during view fading; material holes and solid occluders remain respected.
- 可交互目标增加 6 屏幕像素内的最近可见目标辅助，随渲染分辨率、缩放和视角旋转对齐；完全隐藏的物件不参与辅助。 / Adds a six-screen-pixel assist for the nearest visible interactive target, aligned across render scales, zoom and camera rotation; fully hidden objects are excluded.

## 0.2.6

- 3D 模式手电筒跟随角色实际转身角度，瞄准时保留即时朝向。 / In 3D mode the flashlight follows the animated body turn, retaining immediate aiming direction.
- 左右键使用鼠标所在可见模型的原版物件身份，改进透视墙灯具及同格容器选择；保留原有原版操作。 / Uses the visible model's native source for mouse selection, improving lights on cutaway walls and containers sharing a tile while retaining native actions.
- 家具透明度沿原版速率逐帧变化，分别作用于普通、资源包和独立模型；物理投影继续保留。 / Furniture fades at the native rate across ordinary, packed and independent models, retaining physical shadow casting.

## 0.2.5

- 原版外观模式补齐手电筒和车灯的投影采集、阴影贴图与接影，跟随角色持握位置和方向；没有太阳或固定灯具时也可工作。 / Adds flashlight and headlight capture, shadow maps and reception over the native appearance, following the player’s hand and aim even without sun or fixed lamps.
- 轻量化模式保留这两类按需光源，投影物仍沿用轻量化类别；其他物体继续接影。质量由所选 Viewpoint 光影包决定。 / Retains both on-demand lights in lightweight mode with the same caster categories and all receivers; quality follows the selected Viewpoint shader pack.
- 恢复聚光阴影使用的纹理与缓冲绑定，保留已有缩放频闪修复；开发检查工具继续单独保留。 / Restores spot texture/buffer bindings, retains the zoom flicker fix, and preserves development tools separately.

## 0.2.4

- 新增轻量化动态阴影开关，位于「选项 → Viewpoint 等距增强 → 阴影」，支持中英文与设置保存。 / Adds the bilingual, persistent Lightweight dynamic shadows switch in Options → Viewpoint Isometric Plus → Shadows.
- 筛选太阳、月亮和灯具的投影来源，保留接影、原版外观与 3D 模型；切换时刷新投影缓存。实际性能收益取决于场景与光影包。 / Filters sun, moon and lamp casters while retaining receivers in both model modes; invalidates shadow caches on mode changes. Performance gains depend on the scene and shader pack.

## 0.2.3

- 地面的接影覆盖与楼层独立记录；灯具、墙面等半透明材质重叠时，前景身份不明确也不会取消背景的阴影。不同楼层的混合仍按方差判定，避免错误接影。 / Records floor coverage and height independently, retaining background shadows through overlapping translucent artwork while rejecting mixed floors.
- 补全启动初期 default.frag 的接入，并防止未接入的着色器将颜色广播到接影数据中。 / Captures the early default shader and prevents unsupported programs from broadcasting color into receiver metadata.
- 接影数据沿原版缓存区块的过滤、采样器与 MIP 缩放；保留 0.2.2 的频闪修复。 / Matches native cached-chunk filtering, samplers and mip scaling; retains the 0.2.2 flicker fix.

## 0.2.2

- 增强绘制完成后，同时恢复原版 GPU 着色器与绑定缓存，使后续缩放矩阵正常更新，修复窗边等场景的缩放频闪。 / Restores the native GPU shader and binding cache together after effects rendering so subsequent zoom matrices update correctly, fixing zoom flicker in scenes such as windows.
- 阴影和色彩画风各自启用时均使用该修正。 / Applies to both independent shadows and color styling.

## 0.2.1

- 接影辅助纹理跟随原版画面的缩放过滤与采样器，修正边缘采样覆盖不一致。 / Shadow receiver textures follow native zoom filtering and samplers to correct mismatched edge coverage.
- 半透明材质从当前像素恢复物体与地面的接影贡献，减少窗户及淡出材质的阴影白边。 / Recovers object and floor contributions at translucent pixels to reduce shadow gaps around windows and fading artwork.

## 0.2.0

- 正式接入 Viewpoint 的附加模组发现与生命周期，支持未启用/运行中、启停、信息和设置入口。 / Adds native Viewpoint discovery and lifecycle, including Inactive/Running, activation, information and a Settings shortcut.
- 保留「选项」中的独立分类设置页和中英文显示。 / Retains the categorized Options page and English/Chinese UI.
- 对外统一使用语义版本号；保留之前已验收的渲染实现。 / Uses semantic release numbering and retains the previously accepted rendering implementation.
