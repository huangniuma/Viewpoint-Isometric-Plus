# 0.2.45–0.2.47 更新说明 / Changes

## 中文

- 修正3D模型刷新及隐藏过程中，墙体、家具、僵尸等物件透明度异常叠加造成的变淡和闪烁，保留平滑渐隐。
- 减轻3D等距模式下雨天过重的灰色雨幕，改善大缩放下雨线过细、光源之外难以看清的问题，保留正常雾天、雪花、灯光照雨及室内和屋檐遮雨。
- 去除车库门开启、暗室材质逐渐显现时的颗粒过渡，让模型交接更平滑。
- 恢复树根显示；树冠沿用原版逐渐隐藏、恢复的规则，并处理渐隐末尾的白色残留。
- 增加 ProjectViewpointNearVegetation 0.3.17 兼容，保留扩展树木的风动、积雪和落叶效果。
- 0.2.47修正开启3D模型时，PVNV扩展树与原版树同时显示、树冠和树干重叠的问题。

已有设置继续保留，无需重新配置。当前版本已完成离线构建和相关回归检查；新修正的实际游戏画面仍待确认。

## English

- Fix opacity pulses and flickering during 3D model refreshes and visibility transitions for walls, furniture, zombies and other objects, while retaining smooth fades.
- Reduce excessive grey rain haze in isometric 3D and improve thin, hard-to-see rain at broad zoom, including areas away from lights. Preserve normal fog, snow, rain lighting and shelter under roofs and eaves.
- Remove the grainy model transition when opening a garage door reveals a dark room.
- Restore tree roots. Use native gradual crown hiding and recovery, with handling for the white residue at the end of the fade.
- Add compatibility with ProjectViewpointNearVegetation 0.3.17, retaining wind, snow and falling leaves.
- Version0.2.47 fixes native trees rendering together with PVNV replacements in isometric 3D, which caused overlapping crowns and trunks.

Existing settings are retained. Offline builds and focused regressions have passed; in-game visual confirmation of the new fixes remains pending.
