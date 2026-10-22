# 新增主题与设计 Skill 验收

日期：2026-09-29

## 内容

- 七个 GitHub 设计 Skill 下载到 `../../.agents/skills/`，来源见[下载清单](../../.agents/skills/README.md)。这些文件仅供项目开发，不进入 Android APK。
- 应用内新增“纸间暖信”“海盐晴窗”“月色电台”三套主题。每套有独立配色、字体或图标样式、圆角、默认聊天气泡色，以及图像生成模型制作的无文字聊天页眉图。原有三套主题保留。
- 主题卡片使用真实组件绘制；页眉图与聊天首页调用相同资源和绘制组件，标题居中，右侧保留搜索、角色管理和新建聊天三个图标。

## 已执行检查

- `./ci/build.ps1 -Tasks ':app:assembleRelease'`：成功；最终一次构建为 `BUILD SUCCESSFUL in 2m 52s`。本轮未运行单元测试。
- `ci/script/verify_apk.py`：PASS；APK 版本 0.39.0、versionCode 40，ARM64 原生库加载段为 16 KB 对齐；检查结果见[APK 审计](apk-audit.json)。
- `apksigner verify --verbose`：v2 签名有效，签名者 1 个。`aapt2 dump resources` 确认三张装饰图已打包。
- MuMu `emulator-5554`：覆盖安装成功。在横屏下进入“设置 → 插件与主题”，分别应用三套新主题，核对聊天首页的页眉图、页面配色及导航图标。月色电台在强制停止并重新启动应用后仍保留。检查结束后，恢复模拟器原先的“团子日常”主题。

## 截图

- [预览页眉布局](plugins-final.png)
- [纸间暖信实际页面](letter-applied.png)
- [海盐晴窗实际页面](sea-applied.png)
- [月色电台预览](theme-scrolled-2-final.png)
- [月色电台实际页面](moon-applied-final.png)
- [月色电台重启后](moon-restart-final.png)
- [恢复原主题](original-theme-restored.png)

## 交付与限制

- 当前单版 APK：[local-companion-0.39.0.apk](../../output/local-companion-0.39.0.apk)，SHA-256：`1e51930c0334f2293a46bf7d5b5f82df4ed8c8bc861b0643c039f8f50cf8703d`。
- 已验证 MuMu 横屏预览、应用与重启。真机竖屏、大字号、内存占用、角色原有壁纸和聊天气泡设置保持情况未在本轮验证。
