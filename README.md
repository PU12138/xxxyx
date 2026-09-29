# 仙途·传奇 (xxxyx)

一款竖屏修仙小游戏，灵感源自《传奇》的刷怪成长玩法：扮演修仙者在妖兽波次中搏杀，积攒经验突破境界，拾取法器、攒灵石，每 5 波挑战渡劫妖王。

## 安装
- 直接安装 [`release/xiantu-1.0.apk`](release/xiantu-1.0.apk)（Android 5.0+，需开启「未知来源应用」安装权限）。
- 竖屏沉浸式，自带背景音乐与音效（右上角 ♪ 可静音）。

## 操作
- 左下虚拟摇杆移动。
- 右下「剑诀」（万剑诀·范围剑气）、「护体」（金刚护体·回血护盾）施法，「丹」服用回血。
- 自动攻击最近妖兽；击杀获取经验与灵石，攒满经验突破境界。

## 境界
练气 → 筑基 → 金丹 → 元婴 → 化神 → 炼虚 → 合体 → 大乘 → 渡劫（每境 9 重，共 81 重）。

## 构建
```bash
./gradlew :app:assembleRelease
```
产物：`app/build/outputs/apk/release/app-release.apk`。
依赖 JDK 17、Android SDK Platform 34 / Build-Tools 34、Gradle 8.7（仓库已含 wrapper）。

## 目录结构
- `app/src/main/java/com/xxxyx/cultivation/` 游戏源码
  - `game/` 实体与世界观（Entity/Player/Monster/Projectile/LootItem/Particle/GameWorld/CultivationRealm）
  - `ui/` 摇杆与按钮（Joystick/ActionButton）
  - `audio/` 背景音乐与音效管理（GameAudio）
  - `GameView.java` 渲染线程 · `MainActivity.java` 入口
- `app/src/main/res/raw/` 程序化生成的背景音乐与音效（由 `tools/gen_audio.py` 生成）
- `release/` 可直接安装的 APK
