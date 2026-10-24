---
name: 星语
description: Android Material 3 主题、雨窗家园与 Godot 院子的设计记录
colors:
  home-ground-clear: "#658754"
  home-water: "#7BAEB2"
  home-water-ripple: "#A4D0CB"
  home-shore: "#D0DA9C"
  home-soil: "#815A3C"
  home-pixel-shadow: "#654734"
  home-pixel-wood: "#C28B58"
  home-pixel-plank: "#D8AD70"
  home-ink: "#3E493C"
  home-cream: "#F7EAC7"
  home-picture-green: "#668A4A"
  home-picture-gold: "#E3B453"
  home-picture-wood: "#946044"
  home-picture-red: "#D97760"
  home-picture-blue: "#79A7AB"
  home-name-text: "#FDF3D7"
  home-rain-wall: "#202628"
  home-rain-window: "#93ADB4"
  home-rain-gold: "#E9B36A"
  home-rain-rose: "#C98E86"
  home-rain-cream: "#F3E7D6"
rounded:
  home-name-label: "4dp"
  home-picture-direction-stat: "12dp"
  home-map-progress-feedback: "14dp"
  home-panel-invitation: "18dp"
spacing:
  home-picture-stack: "2dp"
  home-inline-tight: "4dp"
  home-picture-padding: "6dp"
  home-control-gap: "8dp"
  home-overlay-inset: "12dp"
  home-camera-clearance: "20dp"
  planner-inline-tight: "4dp"
  planner-field-stack-gap: "10dp"
  planner-row-gap: "12dp"
  planner-phone-inset: "16dp"
  planner-wide-inset: "24dp"
  planner-column-gap: "32dp"
  planner-bottom-clearance: "88dp"
components:
  home-map-button:
    rounded: "{rounded.home-map-progress-feedback}"
    width: "48dp"
    height: "48dp"
  home-direction-button:
    rounded: "{rounded.home-picture-direction-stat}"
    width: "48dp"
    height: "48dp"
  home-picture-button:
    rounded: "{rounded.home-picture-direction-stat}"
    width: "72dp"
    padding: "{spacing.home-picture-padding}"
  home-picture-button-large-text:
    rounded: "{rounded.home-picture-direction-stat}"
    width: "144dp"
    padding: "{spacing.home-picture-padding}"
  home-map-panel:
    rounded: "{rounded.home-panel-invitation}"
  home-invitation:
    rounded: "{rounded.home-panel-invitation}"
    padding: "{spacing.home-control-gap}"
  home-activity-progress:
    rounded: "{rounded.home-map-progress-feedback}"
    padding: "{spacing.home-control-gap}"
  home-name-label:
    backgroundColor: "{colors.home-ink}"
    textColor: "{colors.home-name-text}"
    rounded: "{rounded.home-name-label}"
  planner-header:
    padding: "8dp"
  planner-calendar-day:
    height: "56dp"
    padding: "6dp 0dp"
  planner-time-gutter:
    width: "64dp"
  planner-month-pane-wide:
    width: "360dp"
  planner-timeline-row:
    padding: "8dp 4dp 12dp 12dp"
  planner-date-field:
    height: "64dp"
    padding: "12dp"
  planner-time-field:
    height: "64dp"
    padding: "12dp"
  planner-add-action:
    padding: "{spacing.planner-phone-inset}"
---

# Design System: 星语

## Overview

**Creative North Star: "雨窗与院子"**

E15 的 Godot 院子仍保留：一张可以走动、耕作和与居民相处的像素农场，Kotlin / Room 持有同一共享世界的经营、碰撞、人物行为与保存状态。E18 把默认家园主界面改为 Compose 绘制的《雨窗》房间，宠物与共同照顾在场景内完成；像素地图从主界面退到“院子”入口。Kenney CC0 原像素图集与项目原创物件只承担院子场景；雨窗场景由 Compose 图形绘制，原生菜单、输入和文字继续继承应用 Material 3 主题、字体与字号。家园的像素色与雨窗色都限定在家园。

应用继续保留 E14 星语品牌：桌面自适应图标与设置页使用猫咪抱星星画作，通知使用单色星形。原图、最终资产与生成提示词见 [品牌记录](docs/品牌/README.md)。该品牌画作与本轮来源图集分别记录，不把来源素材标为 AI 生成。

依据为 [PRODUCT.md](PRODUCT.md)、[方向契约](.impeccable/direction.md)、[家园入口](app/src/main/java/com/ai/assistance/operit/ui/main/HomePages.kt)、[游戏控件](app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameUi.kt)、[场景与命中](app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameScene.kt)、[图片与肖像](app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameImages.kt)、[原生宿主](app/src/main/java/com/ai/assistance/operit/ui/main/HomeGodotHost.kt)、[渲染投影](app/src/main/java/com/ai/assistance/operit/ui/main/HomeRenderFrame.kt)及 [Godot 场景](game/home/home.gd)。这是 code-led 迭代，没有 approved comp；原生 Android 本轮未运行 HTML / CSS detector。

独立 [首轮复核](evidence/E15_GODOT_HOME/finish-review.md)提出五项修复，后续 [verdict](evidence/E15_GODOT_HOME/verdict.md)将 F1–F5 全部评分为 resolved，专项 disposition 为 `ship`。此结论只覆盖这五项修复，不代表整面重新审计或全设备认证。实现与实际操作范围见 [运行验收](evidence/E15_GODOT_HOME/validation.md)。

E16 日程是现有原生系统的局部扩展，继承应用主题、字体与形状。提取依据为 [PlannerPages](app/src/main/java/com/ai/assistance/operit/ui/main/PlannerPages.kt)、[PlannerCalendarUi](app/src/main/java/com/ai/assistance/operit/ui/main/PlannerCalendarUi.kt)和 [PlannerCalendar](app/src/main/java/com/ai/assistance/operit/data/model/PlannerCalendar.kt)；页面策略留在 [日程方向契约](.impeccable/surfaces/assistance-operit-ui-main-plannerpages-kt-ebc64f29.md)。[官方参考](evidence/E16_PLANNER_UI/references.md)只提供交互结构，没有复制素材或 approved comp。本轮原生截图完整复核的 disposition 为 `ship`，范围见 [E16 收尾评审](evidence/E16_PLANNER_UI/finish-review.md)；E15 历史结论没有重跑或扩大。

E18 将家园主界面切换为 Compose 绘制的《雨窗》房间：黄昏、窗、雨、猫和角色同处一屏，领养卡、照顾面板与共同手账浮在场景上；旧 Godot 像素地图保留为“院子”，不再是默认家园。宠物并入共享家园 JSON，家园格式 3→4，旧格式 3 继续可读。依据为 [CompanionHomeScene](app/src/main/java/com/ai/assistance/operit/ui/main/CompanionHomeScene.kt)、[CompanionHomeUi](app/src/main/java/com/ai/assistance/operit/ui/main/CompanionHomeUi.kt)、[HomePet](app/src/main/java/com/ai/assistance/operit/data/model/HomePet.kt)、[HomeCodec](app/src/main/java/com/ai/assistance/operit/data/model/HomeCodec.kt)、[HomePages](app/src/main/java/com/ai/assistance/operit/ui/main/HomePages.kt)和 [HomeGameUi](app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameUi.kt)。这一节描述已实现并通过 JVM、构建与模拟器设备复验的现状，原生截图见 [E18 验收记录](evidence/E18_RAIN_HOME/validation.md)。

**Key Characteristics:**

- 同一共享世界决定经营、人物占位与成长，Godot 接收只读画面投影。
- 雨窗主界面以宠物、照顾面板和共同手账操作；院子保留地图内物件、物品图像与动作图格。
- 原生容器、输入和文字继承应用 Material 3 主题、字体与系统字号。
- 人物图像、脚底、名牌与命中共享镜头；居民使用本机人设关键词规则。
- 未回应邀约在普通背包和菜单期间隐藏，收起后恢复；系统移除动画停止场景装饰运动。
- 日程以真实记录投影日期标记和时间线，使用主题角色、原生选择器及随字号变化的布局。

## Colors

图集保留自身原像素；前言提取的是当前源码显式定义的复用绘图色，不是从图集采样出的全应用色板。

### Primary

已选择的应用 `ColorScheme` 是原生控件颜色来源，见 [CompanionTheme](app/src/main/java/com/ai/assistance/operit/ui/main/CompanionTheme.kt)与 [主题接入](app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt)。选中图格使用 `primaryContainer`，进度指示器使用 Material 默认角色。内置、自定义、日间和夜间主题继续存在，不固定任一可选色板为唯一品牌色。

### Neutral

地图浮层、状态与控制使用 `surfaceContainer`，普通图格使用 `surfaceContainerHigh`，正文沿用 `onSurface`。角色由 [withCompanionSurfaceTokens](app/src/main/java/com/ai/assistance/operit/ui/main/CompanionThemeFormat.kt)生成。图片色不替换这些主题角色。

`home-ground-clear` 是 Godot 运行时清屏色，不是 Tiny Town 草地图格的像素色。`home-water`、`home-water-ripple`、`home-shore` 与 `home-soil` 对应连续水面、波纹、外缘岸线和耕地。`home-pixel-shadow`、`home-pixel-wood`、`home-pixel-plank` 为树干、地垄和原创家具的复用深木色、木色及木板色，来源为 [home.gd](game/home/home.gd)。

`home-ink`、`home-cream` 及 `home-picture-*` 来自 [HomePictures](app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameImages.kt)，用于原生物品、种子袋与动作图片；墨色也用于名牌底色。名牌文字使用 `home-name-text`。已布置家具的主要颜色继续读取 `HomeFurniture.color`，不将用户家具选择固化为原始色板。

材料行保持图片完整颜色，并显示“持有/需要”数字：足够时使用 `onSurface`，不足时使用 `error`。不可用图格除选中状态外透明度为 50%；选中图格保持全色。

**The Native Color Rule.** 像素素材与绘图色用于家园内容，原生容器和文字继续绑定当前应用主题。

日程选中日期使用 `primary/onPrimary`，未选中的今天用 `primary` 细描边；邻月日期、说明及分享状态使用 `onSurfaceVariant`。普通日期与分类栏使用 `surface`，记录行使用 `surfaceContainerLow`，空态图标容器使用 `surfaceContainerHigh`，分隔线使用 `outlineVariant`，逾期组标题、输入错误和删除菜单使用 `error`。这些都是当前 `ColorScheme` 的运行时绑定，未增加固定灰绿十六进制色或合成色阶；日间／夜间及用户自定义主题继续生效。

## Typography

家园没有独立像素字体或固定数字字号。字体由应用选择的 `FontFamily.Default`、`FontFamily.Serif` 或 `FontFamily.Monospace` 通过 `withFontFamily` 应用到 Material 类型角色；有效字号由系统 `fontScale` 与所选应用倍率共同决定。源码没有另定义这些角色的固定 sp 值，前言不新增一套静态字号。

浮层标题与物品增量使用 `titleMedium`；气泡、错误与确认使用 `bodySmall`；状态、进度和材料数字使用 `labelLarge`；图格数量使用 `labelMedium`；图片短标签和人物名牌使用 `labelSmall`。标题与名牌为一行，气泡最多两行；图片短标签通常最多两行，紧凑入口为一行，超出使用省略号。

非紧凑图格在有效 `fontScale >= 1.5` 时切换大字号宽度，本轮 200% 手机背包形成两列。名牌由 `Layout` 测量后放置：居民宽 32～120dp、玩家宽 32～64dp，避让间隙为 6dp。显示当前角色、当前选中居民及玩家名牌；越界名牌不放置。名牌避让使用人物常规轮廓向上 48、横向 ±18、脚点向下 6 个世界单位，居民停留活动符号向上计 70。

日程页面与当天标题使用 `titleLarge`，月份、日期数字、记录标题及选择器标题使用 `titleMedium`，分组标题使用 `titleSmall`；备注与说明使用 `bodyMedium`，日期／时间字段值使用 `bodyLarge`，角色名、时区与状态使用 `bodySmall`。时间列使用 `labelLarge`、结束时间与星期使用 `labelMedium`，年度标记使用 `labelSmall`。日程没有独立固定 sp 尺度；标题加粗与完成删除线在同一 Material 类型角色上表达状态，记录备注最多两行并省略。

**The Planner Type Rule.** 日程文字继续使用当前 Material 类型角色；大字号将时间移入记录内容，并让表单与选择器正文滚动，操作按钮保留独立区域。

## Layout

地图填满顶部应用栏下的可玩区域。币、精力和等级位于左上，全图和菜单位于右上；无操作浮层时，方向键位于左下、背包及布置入口位于右下。浮层显示时隐藏这些下方入口与方向键。进屋、出门和更名均由地图内物件或菜单进入。

浮层对齐地图右下，内容可滚动。实测规则来自 [HomeGameUi](app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameUi.kt)：

| 条件 | 当前布局 |
| --- | --- |
| 地图宽至少 600dp 且宽大于高 | 常规浮层宽为 336dp 与地图宽 46% 的较小值；最大高度为地图高减 88dp。 |
| 其他尺寸 | 常规浮层宽为 360dp 与地图宽减 24dp 的较小值；最大高度为地图高 58% 与地图高减 148dp 的较小值。 |
| 小屋或资源目标的精简浮层 | 宽为 184dp 与地图宽减 24dp 的较小值。 |
| 所有浮层 | 最大高度计算结果至少 112dp；水平内边距 12dp、垂直 4dp，滚动内容底部 8dp。 |
| 地图高小于 420dp | 使用紧凑入口和活动进度位置。 |

镜头让位使用 `onSizeChanged` 测得的浮层尺寸：普通尺寸保留底部“浮层高 + 20dp”，宽屏保留右侧“浮层宽 + 20dp”；镜头计算区域每边至少三个默认格宽。绘制边界仍是完整地图 `Box`，`clipToBounds()` 限制越界。

[HomeWorld](app/src/main/java/com/ai/assistance/operit/data/model/HomeWorld.kt)为 30 × 27 格。[HomeRenderFrame](app/src/main/java/com/ai/assistance/operit/ui/main/HomeRenderFrame.kt)与 Godot 使用 32 个世界单位为一格，默认显示格宽为 30dp，缩放范围 0.6～1.6。Godot 从 16 × 16 原图切片绘制并使用 nearest 过滤，世界单位、原像素尺寸和 Android dp 分别计算。

`HomeImageGrid` 横纵间隔均为 `home-control-gap`。一般图格宽度来自默认组件，大字号非紧凑图格来自大字号组件，高度至少 72dp；内部图片区域为 40 × 44dp，动作图片为 36dp、物品图片为 40dp。关闭、方向、数量和领奖的 `IconButton` 为 48 × 48dp，方向键和并列数量操作间隔为 8dp。

**The Map Bounds Rule.** 所有游戏操作保留在地图边界内，绘制、点击和名牌共享同一镜头，浮层只为镜头保留空间。

### 日程（E16）

日程保留紧凑角色标题、可横向滚动的四分类栏和单一添加 FAB。前言 `planner-*` 只记录当前源码中的局部尺寸；`planner-calendar-day.height` 与日期／时间字段的 `height` 是最小高度，月历栏宽只用于宽屏，记录行 `padding` 按上、右、下、左排列。

| 条件 | 当前布局 |
| --- | --- |
| 内容宽至少 840dp、有效字号低于 1.5，且为日历视图的日程／待办／纪念日 | 完整月历与当天安排并排；两栏独立滚动，总内容最大 1100dp，月历栏使用 `planner-month-pane-wide`；边距、栏间距与底部空间来自对应前言 token。 |
| 其他尺寸或大字号 | 单列可滚动，使用 `planner-phone-inset` 与 `planner-bottom-clearance`；周历可展开月历。全部记录与经期视图不显示日历。 |
| 有效字号至少 1.5 | 移除独立时间列，时间在记录标题前显示；时间输入的小时与分钟竖排。 |
| 编辑器字段可用宽小于 360dp，或有效字号至少 1.2 | 日期与时间入口竖排；普通横排时间入口宽 132dp。 |
| 编辑器与选择器正文 | 使用 `adaptiveDialogContentMaxHeight` 并滚动；为标题／操作预留 `96dp + 96dp × max(fontScale, 1)`，正文高度不超过窗口剩余空间。 |

周与月网格从周一开始，七列等宽；日期图格使用前言最小高度。在本轮 375dp 手机矩阵中图格宽约 49dp，图标操作采用 Material `IconButton` 的 48dp 触控目标。日期语义包含完整年月日、星期、今天、真实安排数及选中状态；小点最多三个，不能代替完整数量语义。上述尺寸观察只覆盖已提供矩阵。

**The Planner Calendar Rule.** 日历是既有记录按设备时区生成的投影；日期标记与当天时间线读取同一真实记录，全部入口保留其他日期的可发现性。

## Elevation & Depth

地图控制使用 2dp `Surface.shadowElevation`，操作浮层使用 3dp；图片图格、邀约、反馈和进度通过主题容器色区分。Godot 世界按纵坐标排序物件和人物，树木、房屋与人物使用场景内像素阴影；这些阴影属于场景内容，不构成应用阴影尺度。屋顶同时控制室内人物的绘制和命中可见性。

玩家、同行与共同活动参与者每格移动为 170ms 线性动画，其他居民依关键词规则使用 230、300 或 390ms。Godot `Engine.max_fps` 设为 30。人物步态由实际位移、水平朝向镜像和起伏组成，当前图集没有四方向序列帧；镜像沿用同一脚底中心。水波、场所闪光、成熟提示、营火、蝴蝶和活动标记使用同一个场景时钟，操作粒子寿命为 1.2 秒。

系统 `Settings.Global.ANIMATOR_DURATION_SCALE` 观察器直接读取当前设置；为 0 时投影包的 `reduceMotion` 使 Godot 停止时钟、清除粒子并保留状态改变重绘。水波、起伏和闪光等装饰运动停止，位移操作、作物成长与经营计时继续执行。离开家园隐藏并暂停引擎；Activity 销毁才销毁引擎。

日程记录行是默认零阴影的 `Surface`，使用主题容器色形成层次；FAB 和对话框沿用 Material 默认深度。周／月切换使用 Compose `animateContentSize()` 默认规格，未另定义日程动画时长、缓动或全应用运动 token；日程系统移除动画的设备体验本轮未验收。

## Shapes

前言记录家园实际复用的名牌、图格、控制、进度与浮层圆角。应用其余组件继续通过 [withCornerStyle](app/src/main/java/com/ai/assistance/operit/ui/main/CompanionTheme.kt)应用用户选择。场景素材保留像素边缘，原创物件使用矩形色块；原生交互仍由 Compose `Surface`、`IconButton`、`Text` 与输入组件承担。

日程日期图格绑定 `MaterialTheme.shapes.medium`，记录行绑定 `large`，日期／时间入口绑定 `small`；小点与空态容器采用 `CircleShape`。实际圆角跟随应用的默认／紧凑／柔和设置，因此前言不把截图圆角写成固定日程尺度。

## Components

- **地图与永久渲染门户：** `HomeGodotLayer` 是 Activity 的 Scaffold 内容中的唯一永久原生门户，位于 Compose 控件下方。`HomeGameScene` 通过 `HomeGodotSurface` 发布实际窗口 bounds 和渲染 packet。页面退出只隐藏并 pause，重新进入复用同一视图；Activity 销毁才 destroy。
- **物品与动作图格：** `HomePictureButton` 是带 enabled、selected 状态和物品名称／数量语义的可点选 `Surface`。背包、制作、种植、家具与居民选择复用图格。原生物品和动作保留原创 Canvas 图片，家具保留 `HomeFurniturePreview`。
- **人物和换装预览：** `HomePortrait` 从原生 Kenney 人物图集读取与场景相同的 `homeAvatarTile`，48 × 56 绘图单位中绘制 16 × 16 原切片；服装用原创矩形绘制。Godot 人物与原生预览共享身份选片和穿搭值，不把静态人物图集写成多方向动画。
- **地图内操作浮层：** 标题带 heading 语义，关闭为 48dp，内容可滚动；背包、配方、数量、丢弃／清理确认、名称输入、心愿和回忆均在地图内。更名使用 `OutlinedTextField` 与图片保存操作。
- **材料需求：** `HomeMaterialCost` 保留完整色彩的物品图片，持有／需要数字与朗读描述共同表达要求，行高至少 48dp。
- **回应与进度：** 邀约包含居民肖像、短问候、对应活动图片与关闭按钮，最大宽 320dp，无自动超时。普通背包和菜单期间隐藏，收起恢复同一邀约；回应、明确关闭、居民删除、活动开始或场景结束清除。回应直接使用邀请者 id 与所邀活动；MEAL 打开该居民的食物图片选择。活动条最大宽 240dp，包含图片、倒计时、进度和 48dp 手动领奖目标。
- **即时反馈：** 物品增量或聊天短回应最大宽 260dp，显示 3.5 秒；该时限不用于自主邀约。
- **人物行为与占位：** `HomeTraffic` 让全部人物共享占位，移动中保留起终格，逐步推进前检查新占位。`HomePersonality` 以本机已保存性格／兴趣关键词赋权目的地、步速与主动间隔；不请求在线模型，不自动消耗材料或领取奖励。自主决定循环限于 `RESUMED` 且可操作、无浮层时；移动由对应协程推进，渲染由原生宿主控制前后台。
- **辅助功能：** 地图提供场所、居民、田地与资源的 `CustomAccessibilityAction`，图像、数量、材料和领奖保留原生文本语义。TalkBack 设备体验本轮未验收。

**The Single World Rule.** Kotlin / Room 是世界与行为的事实来源，Godot 只读渲染投影；投影不包含人设正文、消息或凭据，也不另建库存或保存格式。

本轮图集来自 Kenney CC0 [Tiny Town](https://kenney.nl/assets/tiny-town)、[Tiny Farm](https://kenney.nl/assets/tiny-farm)与 [Tiny Dungeon](https://kenney.nl/assets/tiny-dungeon)。原像素、原文件哈希及嵌入来源后的哈希见 [美术来源清单](game/home/art/sources.json)。[crop_stage.gd](game/home/crop_stage.gd)适配 MIT 2D Farming Game 的作物阶段／图集资源代码，提交见 [source-lock](game/home/source-lock.json)；没有纳入 Sprout Lands 非商业素材、上游独立库存或示例地图。

验收证据来自最终 Release 的 MuMu Android 15 原生画面与控件树，本 documenter 没有重新运行构建、测试、扫描或设备动作：

| 已提供证据 | 结论边界 |
| --- | --- |
| [截图矩阵](evidence/E15_GODOT_HOME/capture-matrix.json) | 重拍 12 张：375dp 手机（900 × 1600、density384，fontScale1／2）、横屏（1600 × 900、density240）和平板（1920 × 1200、density240）；19 组控件边界 PASS，另 [final-home](evidence/E15_GODOT_HOME/final-home-controls.json)单独 PASS。边界检查不代替 TalkBack 或全部触控验收。 |
| [生命周期](evidence/E15_GODOT_HOME/lifecycle.json) | 同一 PID 9590 聊天与家园往返三次及后台恢复成功，没有 force-stop。 |
| [邀约保留](evidence/E15_GODOT_HOME/invitation-retained.json) | “你好”的真实 CLEAN 邀约在背包／菜单期间隐藏，关闭后恢复同一角色与活动，实际回应完成后手动领奖。 |
| [正常运动](evidence/E15_GODOT_HOME/motion-normal.json)／[移除动画](evidence/E15_GODOT_HOME/motion-reduced.json) | 相同池塘 ROI、间隔 1.1 秒，实时倍率 0.5 时 188 像素变化，改为 0 后 0 变化；0 下实际移动仍可操作。像素指标只覆盖水面，其余装饰运动由源码核对支持。 |
| [测试汇总](evidence/E15_GODOT_HOME/unit-tests.json) | 570 项 JVM 通过；最后 Android 动画设置读取小修前执行。小修后单独 Release 重建与实际设置切换验证，未重跑 JVM。Godot 阶段、132 图格、场景及 JSON 浮数池塘邻格检查通过。 |
| [APK 核验](evidence/E15_GODOT_HOME/apk-audit.json)／[覆盖安装](evidence/E15_GODOT_HOME/release-device.json) | 最终包 89,987,965 字节，SHA-256 `d553fee0b827c93f2a27f7660fe2275134bbd1cccfdc7cf148ef5dbe5cb03011`；星语 0.39.0 / code40，MuMu 仅安装 `cn.localfirst.companion` 一个正式应用。 |
| [运行验收](evidence/E15_GODOT_HOME/validation.md)中的保存与来源记录 | schema34、backup32、home format3 不变；聊天与家园可见状态保留，正常游戏动作后 160 币／8 精力，不是完整数据库比对。5 份新 home 图集 provenance scan 为 0 missing；5 份未改品牌／其他功能资产仍有预存元数据缺失，不能宣称全 APK 溯源完整。 |

本轮没有 Android 仪器测试；E11 的 11 项 SQLCipher 设备用例仅为历史记录。真机、TalkBack、暗色主题、预测返回、性能／电量、隐私锁与在线模型本轮未验收。侧车保留原生 Compose / Godot 规则与来源，不生成 HTML / CSS 替代控件或合成色阶。

### 日程原生组件与 E16 验证范围

- **日期与当天安排：** `PlannerCalendarPane` 在手机显示可展开周历，宽屏显示完整月历；`PlannerAgenda` 在日历与全部记录间切换。时间线读取设备时区的真实本地午夜边界，跨天显示“跨天／次日”；年度纪念日沿用记录原时区和非闰年 2 月 29 日按 2 月 28 日展示的规则。
- **记录行与待办：** 轻容器色、时间列、标题、备注和分享／提醒状态共同形成层级。完成使用原生 `Checkbox`、标题删除线和已完成分组；全部待办按已逾期、今天、之后、已完成分组。逐条编辑／删除位于 `DropdownMenu`，删除另有确认。
- **日期／时间编辑：** 日期是 Compose `DatePickerDialog/DatePicker`，年范围 1900～9999，选值毫秒按 UTC 日历日期解释，关闭自由文本模式切换。时间是 Material `AlertDialog` 内两个数字 `OutlinedTextField`，明确 24 小时制，小时 0～23、分钟 0～59，字段最多两位，合法时启用确定；没有使用平台时钟盘。编辑器保留结束时间、提醒 `FilterChip`、逐条分享 `Switch` 与保存／取消。
- **说明与空态：** “分享与提醒”入口展示可展开分享预览、设备时区及本机提醒限制。新记录默认不分享；经期只展示输入的记录和敏感分享说明，不预测周期。空态说明当前没有安排，提供实际添加操作，不写入演示记录。
- **主题图标：** 全部经由 [ThemedIcons](app/src/main/java/com/ai/assistance/operit/ui/main/ThemedIcons.kt) 绑定实心、线框和柔彩偏好；新增家族和镜像箭头分别登记，保留严格 `getValue` 查找。

**The Planner Flat Row Rule.** 日程记录依靠时间、标题与轻容器色分层；分享／提醒图标表达真实状态，完成状态同时给出复选框与文字样式。

下列仅复述已提供 E16 原生证据，本 documenter 没有运行构建、测试、扫描或设备操作；只核对源码、记录，并打开当天安排、200% 编辑器和夜间平板三张代表截图。

| 已提供证据 | 结论边界 |
| --- | --- |
| [E16 完整复核](evidence/E16_PLANNER_UI/finish-review.md) | 独立 reviewer 逐张查看 13 张必需最终截图及 2 张基线，disposition 为 `ship`，必需修复为空；只覆盖本轮日程与 MuMu 矩阵。code-led 局部扩展，没有 approved comp、decision comp、QUALITY BAR 卡或 HTML／CSS detector。 |
| [原生验收与截图](evidence/E16_PLANNER_UI/validation.md) | 375dp 手机 100%／200% 字号；横屏 1600 × 900、density240；平板 1920 × 1200、density160 日间／夜间；用户窗口 900 × 1600、density240／600dp。包含空态、真实临时安排、待办完成、编辑、日期与时间输入。 |
| [JVM 摘要](evidence/E16_PLANNER_UI/jvm-summary.json)与 [图标专项](evidence/E16_PLANNER_UI/icon-test.xml) | 全量 JVM 576 项（含 6 项日历）在图标登记修正前通过；修正后另有 1 项图标契约通过。最后选择器与字号修改后只重建 Release 并实际操作，未重跑全量 JVM。 |
| [APK 核验](evidence/E16_PLANNER_UI/apk-audit.json)与 [安装摘要](evidence/E16_PLANNER_UI/release-device.json) | 星语 0.39.0／code40，90,164,985 字节，SHA-256 `33d74af300927f390cbbd02f1a4b2f6ad9401d74b52abb648c99de7563cfc8fa`；v2 签名、16KB 对齐和包静态核验通过，安装包与交付包哈希一致，正式身份仅 `cn.localfirst.companion`。 |
| [记录清理与恢复](evidence/E16_PLANNER_UI/release-device.json) | 三条 UI 创建的 `E16_QA_*` 临时记录默认私密、提醒关闭，逐条删除；未保存经期。恢复物理尺寸／density，无覆盖，fontScale1、animator0.5、自动旋转1、日间主题。原角色与最近聊天可见，不是完整数据库比对。 |
| [最终运行查询](evidence/E16_PLANNER_UI/runtime-final.json) | 14:03 起被筛选的 AndroidRuntime 崩溃缓冲无新条目；不证明全部错误、ANR、性能或电量正常。 |

本轮角色空间、schema34、backup32、home format3 不变；没有复测 E15 家园。真机、TalkBack、预测返回、通知实际发送、隐私锁认证、在线模型、性能／电量及日程移除动画没有本轮验收；年度纪念日与经期编辑操作只由构建线程记录支持，没有逐态截图。E15 已记录的五份未改品牌／其他功能资产溯源元数据缺失仍是预存漂移，本轮不修复；E16 未新增 shipping raster，不把官方网页读取写成第三方截图验证。

## E18 雨窗家园

**Creative direction.** 家园从“可走动的像素农场”改为“一间会下雨的房间”。首屏只有一个动作：看它一眼；照顾从猫、碗、灯和角色留言进入。旧地图作为“院子”保留，入口在场景右上。

**Colors.** 场景固定使用 `home-rain-wall`、`home-rain-window`、`home-rain-gold`、`home-rain-rose` 与 `home-rain-cream`；家园外的原生控件继续绑定应用主题，雨窗面板内部使用固定的黄昏对比色。深色照顾面板与纸色领养卡只出现在家园，不推广为应用色板。

**The Rain Room Rule.** 雨窗场景、照顾面板、领养卡和手账属于家园内容，使用固定的黄昏色；它们不进入聊天、日程或设置，也不替换应用的可选主题。

**Layout.** 手机：全幅场景（裁剪在场景容器内，不覆盖应用栏与引导条）、顶部时间/天气/共处天数、右上“院子”和手账、底部“照顾”胶囊；领养时中央纸卡。宽屏（至少 600dp 且宽大于高）：照顾/手账面板宽 360dp 停靠右下，高度上限为可视高度减 96dp 以留出顶部 HUD，场景保持全幅铺满无黑边。200% 字号：面板内容滚动，四个照顾动作改为 2×2，需求标签列与手账时间列加宽，顶部“院子”改为图标按钮；横屏 200% 字号面板占满可视高度，向上滚动一次可见完整动作区。

**Components.** [RainWindowScene](app/src/main/java/com/ai/assistance/operit/ui/main/CompanionHomeScene.kt) 绘制房间、窗、雨、猫的呼吸、角色剪影和灯光；[CompanionHomeUi](app/src/main/java/com/ai/assistance/operit/ui/main/CompanionHomeUi.kt) 提供领养、照顾、手账和院子切换；照顾面板含四需求、默契、四项动作、交接和共同手账；手账按时间列出照顾与交接，领养日独立成卡。

**Boundary.** 需求衰减、成长阶段、交接和角色照顾全部在本地规则中完成，不调模型；聊天只注入一句只读摘要，且仅对已入住、非 raw 的角色。宠物状态只写共享家园 JSON（格式 4），不新增第二份存档。

**Verified on device.** 2026-10-24 在 MuMu Android 15（900×1600 / 240dpi）的一次性临时用户中复验：领养、喂食、交接与约 2 分钟后自动结算、手账、强停重启保留；E17 旧家园（格式 3）覆盖升级到格式 4 后雨窗可读且院子仍可玩；竖屏 100%/200% 字号与横屏 100%/200% 字号布局（布局截图取自上一版构建 `8a9be846…`，最终版 `386cec06…` 只多改了一行文案的角色名还原、未动布局，并在同一台模拟器复验了竖屏 100% 的完整流程）。设备上发现并已修掉四类缺陷：200% 需求标签与手账时间列截断、横屏场景溢出到应用栏且两侧留黑边、宽屏面板遮挡顶部 HUD 且动作区折叠在视野外、照顾面板"刚刚："一行透出账本原文的 `TA` 占位。原生截图与逐项记录见 [E18 验收记录](evidence/E18_RAIN_HOME/validation.md)。

**Not verified.** 横屏 200% 字号需要向上滚动一次才看到四个动作（面板已占满可视高度），本轮未再压缩；TalkBack、真机、性能／电量、暗色与日间主题切换、在线模型如何引用摘要、备份导出恢复格式 4 宠物、超过 7 天的长时离线衰减设备复验均未执行。610 项 JVM（失败、错误、跳过均为 0）与 Release 构建通过，静态核验 PASS。旧 Godot 地图和种植/布置未改，仍以 E15 证据为准。机器侧车 `.impeccable/design.json` 本轮未重新生成；雨窗令牌以本页和源码为准。

## Do's and Don'ts

### Do:

- **Do** 让家园操作留在地图边界内，并以实测浮层尺寸调整同一镜头。
- **Do** 让原生容器、输入和文字继承当前 Material 主题、字体与系统字号。
- **Do** 用物品图像与持有／需要数字表达材料，保留缺料错误色。
- **Do** 让全部人物共享占位、脚底锚点与镜头，让回应使用邀请者和具体活动。
- **Do** 保留普通背包／菜单关闭后的同一邀约，并响应实时系统运动偏好。
- **Do** 分别记录源码事实、模拟器观察、专项复核、来源与未验证范围。
- **Do** 让日程日期标记、时间线与全部记录入口使用同一真实记录投影。
- **Do** 让日程大字号布局、编辑正文与选择器正文滚动，保留独立确认操作。
- **Do** 让日程颜色、类型角色、形状与图标继续跟随当前应用主题。
- **Do** 让《雨窗》的黄昏场景色、照顾面板与领养卡只在家园内使用，家园外的原生控件继续跟随主题。
- **Do** 让宠物状态只写共享家园 JSON，聊天摘要只读、只对已入住且非 raw 的角色发送。
- **Do** 保留“院子”入口，让旧 Godot 地图的种植、布置和居民功能在 E18 仍可到达。

### Don't:

- **Don't** 将家园像素色或图格体系推广为整个应用的主题。
- **Don't** 让 Godot 写第二份共享存档，或向渲染投影发送人设、聊天与凭据。
- **Don't** 将关键词自主规则称为模型语义理解，或将当前人物图集称为四方向序列帧。
- **Don't** 将 F1–F5 resolved、控件边界 PASS 或水面 ROI 对比写成全界面／全设备验收。
- **Don't** 将来源图集标为 AI 生成，或将 5 份 home 图集扫描扩大为全 APK 溯源完成。
- **Don't** 将日程局部尺寸推广到家园，或把可选主题截图固化为全应用色板。
- **Don't** 将 E16 的 MuMu 矩阵、空崩溃缓冲或修改前的 JVM 结果扩大为全应用／真机认证。
- **Don't** 把“院子”像素地图说成默认家园，或把 E18 的代码接通写成已通过构建／设备验收。
- **Don't** 让聊天摘要携带需求数值、人设正文、凭据或未发生的宠物事件。
