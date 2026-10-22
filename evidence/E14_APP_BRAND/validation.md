# E14 星语名称与徽标验收

2026-10-03，用户根据所附猫咪抱星星品牌图，要求修改应用徽标与名称。未更换应用安装、签名或存储身份。

## 资产与源码

- 内置 imagegen 根据原图制作不带白边、文字或预设圆角的方形徽标，随后仅调整构图为系统裁切留出空间。最终 1254px PNG 母版、参考图与完整提示词保存在 [品牌资产](../../docs/品牌/README.md)；生成资源只做 512px WebP 缩放与编码。
- Android 桌面与圆形图标引用同一个自适应图标，设置页使用同一画作。旧聊天气泡徽标已删除，通知改用单色星形。
- 桌面、设置、关于、隐私锁、备份、诊断、通知与协议提示中的显示名称统一为“星语”；设置页标语为“和更懂你的 AI 聊天”。包名仍为 `cn.localfirst.companion`，0.39.0 / code40，schema34 与备份 v32 不变。

## 实际检查

1. 运行 `ci/build.ps1 -Tasks ':app:assembleRelease'`，最终 Release 构建成功（3 分 15 秒）。早先名称处理尚未完成时启动的构建已中止，不作为成功证据。
2. [APK 检查](apk-audit.json)通过：核对包内“星语”名称、打包图像与源资源逐字节相同、应用入口与权限、签名材料未进入包、ARM64 原生库 16KB ELF 对齐。SDK apksigner v2 签名验证和 zipalign 16KB 对齐通过。
3. 使用 [单应用安装入口](../../ci/install.ps1)覆盖安装 MuMu Android 15。已安装包查询只返回 `cn.localfirst.companion`。
4. 打开 [桌面](launcher.png)与 [设置](settings.png)，名称为“星语”，猫咪抱星星徽标已实际显示；设置页显示新标语。实际 UI 层级为 [桌面](launcher.xml)和 [设置](settings.xml)。
5. [聊天](chat.png)仍显示原“你好”角色与最近“你好”消息；[家园](home.png)仍显示“我们的家园”、120 币与 7 精力。[设备核对](device-check.json)同时记录交付哈希、单应用状态与当前进程 AndroidRuntime 无错误。可见状态检查不是全数据库内容比较。

## 交付与限制

当前 [安装包](../../output/local-companion-0.39.0.apk)为星语 0.39.0，17,142,744 字节，SHA-256：`4B02EE0E5C9BC1C46B8C3E6C894F6ED6AD908704C2F29A7FAD60A588FF18A3AE`。

本轮没有运行单元或 Android 仪器测试，没有执行真机、不同系统图标形状、通知实际发送、隐私锁认证或在线模型服务验收。此前家园行为的测试与尺寸检查见 [E12 历史记录](../E12_MAP_INTERACTIONS/validation.md)。历史源码包及 `delivery.json` 没有重新生成。
