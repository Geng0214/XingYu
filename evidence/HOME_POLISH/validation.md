# 家园画面、问题修复与新增主题

日期：2026-10-01。此记录对应家园按钮遮挡优化、相关界面检查和三套新主题。聊天增删改查的完整操作记录见[CHAT18](../CHAT18/validation.md)。

## 改动及原因

- [HomeGameScene](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameScene.kt)删除家具、田地、活动上覆盖的按钮、操作提示及头像下方的额外文字牌。人物与头像属于场景内容，继续显示。对比[改动前](before-living.png)与[客厅](living-landscape.png)。
- [HomeGameUi](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameUi.kt)统一房间操作区：宽屏画面在左、操作在右；手机画面和操作纵向滚动，活动完成控件保留固定位置。六块田地按画面顺序排列，正常字号三列两行，系统字号达到 130% 后单列。厨房制作、卧室赠礼换装及四个底部入口继续可用。
- [CompanionTheme](../../app/src/main/java/com/ai/assistance/operit/ui/main/CompanionTheme.kt)与[ChatAppearance](../../app/src/main/java/com/ai/assistance/operit/ui/main/ChatAppearance.kt)统一应用深浅模式；此前应用选夜间、系统选日间时，聊天仍使用浅色。内置主题的选中色取自自身配色，消除默认紫色混入；导入主题的自定义颜色继续保留。
- 家园图标接入[主题图标](../../app/src/main/java/com/ai/assistance/operit/ui/main/ThemedIcons.kt)，消息气泡接入主题圆角。首次验证包进入家园出现 `IllegalArgumentException: Unregistered themed icon: Icons.Filled.MenuBook`，定位为普通图标引用与自动镜像注册不一致，改用 `Icons.AutoMirrored.Filled.MenuBook` / `HelpOutline` 后重新构建；没有绕过严格图标检查。
- [PluginLibraryPage](../../app/src/main/java/com/ai/assistance/operit/ui/main/PluginLibraryPage.kt)在小屏与大字号下将自定义主题卡片改为纵向排版，预览高度随字号增长，主题应用按钮标注具体主题名。
- [首页查询](../../app/src/main/java/com/ai/assistance/operit/data/db/AppDatabase.kt)将单独保存在会话上的开场白纳入可见事件查询。检查时发现聊天已有开场白而列表显示“暂无消息”；修复后列表显示同一条实际开场白，不生成消息或请求模型。

## 三套新主题

| 主题 | 实际风格 | 设备截图 |
| --- | --- | --- |
| 林间小憩 | 枝叶图、清绿配色、系统字体、线框图标、紧凑圆角，默认日间 | [首页](theme-forest-inbox.png)、[聊天](theme-forest-chat.png) |
| 樱雨来信 | 樱枝图、花瓣粉、衬线字体、柔彩图标、大圆角，默认日间 | [首页](theme-sakura-inbox.png)、[聊天](theme-sakura-chat.png) |
| 观星手记 | 星轨图、蓝黑与黄铜色、等宽字体、线框图标、紧凑圆角，默认夜间 | [首页](theme-observatory-inbox.png)、[聊天](theme-observatory-chat.png) |

新增图案是 Android 原生矢量资源，未调用图像生成模型。三套主题的卡片预览和实际页眉读取同一资源与主题定义；已有六套主题继续可选。角色已保存的壁纸、气泡配色继续按角色使用。系统字体是否有对应字体字形取决于设备。

## MuMu 实际核对

通过 ADB 使用现有 MuMu `emulator-5554`，覆盖安装保留应用数据。宽屏 1600×900 / 240 dpi；手机模拟尺寸 600×1280 / 256 dpi，即 375dp 宽；另核对 200% 系统字号。仅在自建 `HOME-QA` 角色进行种植、制作及活动，不修改原角色游戏进度。

| 核对项目 | 实际结果 |
| --- | --- |
| 四个房间 | 切换客厅、厨房、卧室、院子，画面没有操作按钮覆盖；原生操作移到右侧。[客厅](living-landscape.png)、[厨房](kitchen-landscape.png)、[卧室](bedroom-landscape.png)、[院子](garden-landscape.png)。 |
| 手机与大字号 | [375dp 院子](garden-phone.png)显示画面和独立田地操作；[200% 画面](garden-font-200.png)没有按钮覆盖，向下滚动可用[单列操作](garden-controls-font-200.png)。 |
| 种植与收获 | 1 号田播种胡萝卜、浇水，详情状态更新；等待自然成熟后收获胡萝卜 ×3、种子 ×1，田地恢复空地。[田地详情](plot-detail.png)。 |
| 活动与重启 | 开始喝茶，离开家园到设置后返回；完成并领取一次奖励，家园变为 132 币、精力 7/12、Lv.1 / 8 经验；强制停止后重启仍保留这些数值。[进行中](activity-running.png)、[领取后](activity-completed.png)。 |
| 制作与抽屉返回 | 收获后从厨房制作胡萝卜汤 ×1，背包剩余胡萝卜 ×1、汤 ×1。[背包](craft-bag.png)。赠礼换装、布置、心愿及回忆均能打开，Android 返回键关闭工作表并留在家园。 |
| 主题实际显示 | 三套主题逐一应用并查看首页与聊天。观星手记在系统 `Night mode: no` 时聊天仍显示应用夜色；强制停止重启后主题与本机开场白仍保留。 |
| 大字号主题页 | 自定义主题卡片标题、说明、编辑按钮分别排布，不再挤成碎片。[200% 主题页](themes-font-200.png)；[主题预览](theme-preview-font-200.png)的导航保持同一位置。 |
| 首页与角色删除 | 最终包列表显示 `Hello. Local theme check.` 开场白；[菜单](inbox-crud-menu.png)提供编辑角色、置顶、删除对话及删除角色。删除自建角色的确认框明确 1 段私聊、家园和个人记录范围，确认后仅临时角色消失，原“你好2”“测试”仍在。角色创建与完整增删改查另见 CHAT18。 |
| 原存档与环境恢复 | 原“你好2”家园仍显示 231 家园币、精力 3/12、Lv.2 / 12 经验，[截图](original-home-preserved.png)仅证明这些数值与可访问性，不代表全量历史逐条比对。临时角色、私聊与家园已通过应用删除；恢复原月色电台主题、物理尺寸 900×1600、密度 240、字号 1.0、动画时长 0.5、自动旋转及用户旋转值 0。 |

四房间、宽屏田地、200% 田地控制、制作背包、首页开场白与清理截图在最终包核对。部分种植、活动、前两套主题及预览截图来自中间验证包；相关 UI 在最终构建中保持一致。较早院子图曾使用两列控制，已用最终三列截图覆盖。

## 构建和交付

- 四次执行 `./ci/build.ps1 -Tasks ':app:assembleRelease'`，均成功：3 分 33 秒、2 分 54 秒、2 分 52 秒、3 分 25 秒。第 1 包的图标运行时异常已修复；第 3 包包含田地与大字号卡片调整；最后一次包含首页开场白查询修复。
- 最终 APK 已覆盖安装到 MuMu并成功冷启动。最后读取当前运行阶段的 1200 条 `AndroidRuntime:E` 日志没有异常输出；此前失败包的异常不记为最终通过。
- `apksigner verify --verbose` 确认 v2 签名有效，`zipalign -c -P 16 4` 退出 0。项目 `.venv` 中执行 `ci/script/verify_apk.py --stage HOME_POLISH` 为 PASS，范围见[包检查](apk-audit.json)。
- 最终单版 [local-companion-0.39.0.apk](../../output/local-companion-0.39.0.apk)：版本 0.39.0 / code 40，19,341,499 字节，SHA-256：`5F2431A940D812DEB0A11903E408F2F437820F8F5C2B12709C8FAF891C71515E`。
- 数据库 schema 32、备份 v30 和家园内部存档格式 2 不变；本次新增读取查询、主题定义及资源，不做数据格式迁移。安装说明同步；历史源码包和 `delivery.json` 未重新生成。

## 验证边界

本次没有运行单元测试、Android 仪器测试、Android 真机、TalkBack、外部聊天/生图/语音服务或新一轮备份恢复。没有进行全应用所有功能回归，以上设备结果仅覆盖列出的界面与操作，不宣称软件不存在其他问题。
