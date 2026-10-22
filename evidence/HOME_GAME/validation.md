# 共同生活家园：实施与验证

## 范围与实现

将家园从六栏目物品列表改为可操作的共同生活小游戏：客厅、厨房、卧室、院子四个场景，显示用户与当前角色；喝茶、共读、吃饭、收拾、散步、休息六种活动形成精力、家园币、经验、家具与每日心愿的循环。种植、浇水、收获、烹饪、制衣、赠礼、穿搭和回忆继续使用现有角色家园数据。

- [共同生活状态](../../app/src/main/java/com/ai/assistance/operit/data/model/HomeLife.kt)与[家园状态](../../app/src/main/java/com/ai/assistance/operit/data/model/HomeState.kt)：活动、领奖、购买、摆放、心愿进入同一存档和事务记录；吃饭同时扣除一份成品食物。
- [界面](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameUi.kt)与[场景](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameScene.kt)：原生绘制室内、家具、人物与作物，原生控件提供互动；横屏场景与活动并列，手机竖屏滚动，活动完成按钮固定显示。大字号田地使用编号和状态图标，详细状态由原生详情页提供。
- [内部存档](../../app/src/main/java/com/ai/assistance/operit/data/model/HomeCodec.kt)格式 1→2，旧格式明确转换为初始共居进度，原田地、库存、穿搭及记录保留；数据库仍为 schema 32，个人备份仍为 v30。新存档与指令沿用现有备份表及完整记录重放校验。
- 玩法对白是本机游戏内容，活动不会调用聊天模型或联网。离线不扣除精力、作物或进度；每日心愿按北京时间更新。

## 实际设备操作

设备为 MuMu `emulator-5554`。本轮覆盖安装保留原应用数据，使用现有“你好2”“测试”角色；另创建 `Home-QA` 验证首次入住。手机尺寸为 600×1280 / 256 dpi（375dp 宽），横屏为 1600×900 / 240 dpi。字体分别检查 100%、150%、200%，系统动画时长设为 0 核对减少动画的操作。

| 检查 | 实际结果与证据 |
| --- | --- |
| 首次入住 | `Home-QA` 先显示[入住引导](welcome-final.png)，创建后为 120 家园币、8/12 精力、Lv.1；背包中胡萝卜、番茄、棉花种子各两枚。见[新家园](created-final.png)。 |
| 活动与精力 | 喝茶消耗 1 精力，完成后 120→132 家园币；休息后精力 7→12。共读、吃饭、收拾、散步也分别实际开始并完成。见[喝茶领取](activity-complete.png)、[休息倒计时](rest-running.png)、[手机活动操作](home-large-ready.png)。 |
| 重启保留 | 活动中结束进程并重新启动，已保存活动仍可领取。最终构建重开散步时直接进入院子，见[重开活动](walk-resumed-final.png)。 |
| 家具 | 购买蜂蜜沙发扣除 65 币；切换到已拥有的鼠尾草沙发再切回蜂蜜沙发，105 币保持不变。预览与实际场景使用同一家具绘制函数。见[家具布置](decoration-final.png)。 |
| 心愿与升级 | 喝茶、共读、收拾的三个心愿分别领取；已领取按钮禁用。随后完成散步到 100 经验，显示 Lv.2 与 213 币；莓果沙发由等级锁定变为可购买。见[升级](level-two.png)、[解锁家具](furniture-unlocked.png)。 |
| 种植与吃饭 | 第一块田实际播种、浇水、等待成熟并收获三份胡萝卜及一枚种子；用两份胡萝卜制作一份汤，分享时扣除汤与 2 精力，完成后 105→131 币。见[成熟田地](garden-phone-final.png)、[厨房](kitchen-phone-final.png)、[记录](history-final.png)。 |
| 完整记录 | “读取完整记录”成功展示 16 条操作；该入口调用 `HomeStorageValidation.validate`，逐条重放并核对存档与描述，证明这份运行样本的活动、物品、家具与奖励账本一致。后续升级操作亦保存在家园中。 |
| 角色隔离 | 切换到“测试”显示独立的 120 币、8 精力、初始沙发与未领取心愿，与“你好2”的进度不同，见[第二个角色](second-role-final.png)。 |
| 日夜与布局 | 实际查看[日间](home-day.png)、[夜间横屏](home-landscape-final.png)、[手机竖屏](home-phone-final.png)。房间切换、底部工具与活动面板可操作；场景高度同时受横向和纵向空间限制。 |
| 200% 字号 | 最终 APK 中六块田的编号与状态图标没有重叠，点击第六块田打开完整详情。见[田地修复后](font-200-garden-fixed.png)、[田地详情](font-200-detail.png)。 |
| 返回 | 游戏弹层可由系统返回关闭；根家园返回到聊天；从角色管理打开家园保留标题栏返回。 |

流程截图来自本轮多个验证构建；先前 `activity-start.png` 的场景尺寸问题已经修复，不能用作最终布局示意。最终交付构建的实际夜间界面见[家园](home-delivered.png)，200% 字号田地修复见上表。

验证结束后通过应用界面删除本轮创建的 `Home-QA` 角色，保留原“你好2”“测试”角色与记录；重新选中“你好2”并恢复“月色电台”主题。模拟器恢复物理分辨率 900×1600、密度 240、系统字号 1.0、自动旋转、原动画时长 0.5。

## 最终 APK

以下为家园阶段验证构建；当前交付 APK 已包含后续增删改查调整，最新校验见[增删改查验收](../CHAT18/validation.md)。

- 仅执行 `./ci/build.ps1 -Tasks ':app:assembleRelease'`，最终构建成功，耗时 2 分 39 秒；没有执行默认任务中的单元测试。
- `apksigner verify --verbose` 确认 v2 签名有效；`zipalign -c -P 16 4` 检查通过；`ci/script/verify_apk.py --stage HOME_GAME` 静态检查通过，结果见 [apk-audit.json](apk-audit.json)。
- 最终 APK 已覆盖安装到 MuMu，安装成功，应用可启动；该构建已核对 200% 字号田地修复及最终家园界面。最后一次读取的 `AndroidRuntime` 错误日志没有应用异常输出；本轮 MuMu 的 UI 自动化进程曾在导出控件树后自身退出，不记为应用崩溃。
- 交付文件：[local-companion-0.39.0.apk](../../output/local-companion-0.39.0.apk)，版本 0.39.0 / code 40，19,337,907 字节。SHA-256：`D3AC40B7420F84338621A46E120916B9308B26BC094497A2F674AAE06C727528`。
- 聊天首页加号菜单使用“角色管理”。本轮交付 APK 与安装说明，历史源码包及交付清单未重新生成。

## 背景素材

使用图像生成模型生成[春日庭院背景](../../app/src/main/res/drawable-nodpi/home_meadow.png)。背景只承载草地与树木，人物、房屋、家具、作物、文字、按钮由应用实时绘制，购买家具与穿搭会改变实际场景。

生成提示：

> Use case: stylized-concept. Create a production background illustration for a cozy Android co-living home game, landscape 3:2 aspect ratio. High quality hand-painted storybook game environment, soft gouache texture, careful bold readable silhouettes, sunny spring garden with gently curved trees, small daisies, climbing greenery, distant rolling hill, blue sky. Palette mint sage, pale sky blue, honey yellow, warm wood. Composition: Decorative lush foliage frames ONLY the top 20 percent and narrow left/right edges, broad nearly empty light sage meadow occupies the entire center and lower 75 percent as a calm stage where native game engine draws the house, furniture, characters, soil patches. Horizon high near top. Keep center uncluttered, subtle ground texture. Straight on elevated three-quarter perspective compatible with a 2D room dollhouse. No people, no animals, no buildings, no furniture, no paths or fences, no UI, no text, no letters, no borders, no device mockup. The end result should feel like a polished cozy farming life game environment rather than a flat gradient or marketing banner.

## 验证边界

- 本轮没有运行单元测试、Android 仪器测试或真机验证。现有 `HomeStateTest` 的严格格式检查期待值已随内部格式改为 2，但没有运行该测试。
- 未实际执行新家园数据的加密备份导出/恢复、TalkBack 朗读、跨日心愿更新或大批非空旧存档升级；相关实现已阅读，不记为设备验证通过。
- 本轮新流程验证了种植、做汤与共享食物；原制衣、赠礼、穿脱服装仍保留原事务与入口，尚未重新执行全部配方及每种穿搭的设备检查。
