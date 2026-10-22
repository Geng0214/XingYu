disposition: fix

证据范围：MuMu 原生 Android；已逐一打开 12 张指定有效截图及辅助故障图。未提供暗色主题、系统移除动画的设备验收、TalkBack／仪器或真机性能证据；这些项目不记为通过。此次没有执行构建、测试或设备操作，没有运行 HTML／CSS detector。

## persistence

pass — [PRODUCT.md](../../PRODUCT.md)、[方向契约](../../.impeccable/direction.md)与[质量标准](../../.impeccable/quality-bar.md)均存在；FORM 记录原始 seed `5132e4a2`、`experience`、Android／Compose／Godot 与 code-led 路径。输入明确没有 approved comp，因此 comp-led state、spec 与像素 diff 不适用。

旧 DESIGN 为 E12，输入明确由本轮 documenter 在复核后更新，不能据此宣称 E15 文档已经完成。[场景说明](../../game/home/README.md)与[美术来源清单](../../game/home/art/sources.json)明确记录 MIT Crop 资源适配与 Kenney CC0 图集；未重新扫描元数据或验证清单哈希。

有效截图均无黑屏或空白渲染区，内容与名称一致：`phone.png`、`phone-activity.png`、`phone-bag.png`、`phone-crop.png`、`phone-harvest.png`、`phone-overview.png`、`phone-interior.png`、`phone-large-text.png`、`phone-large-bag.png` 为 900×1600；`landscape.png` 为 1600×900；`tablet.png`、`tablet-bag.png` 为 1920×1200。`tablet-reentry.png` 显示模拟器桌面，按照输入仅作为退出故障证据，不冒充家园重入截图，也不替代上述有效矩阵。

569 JVM 测试与 Godot 132 图格／阶段检查、播种浇水收获、CLEAN 邀约回应及进屋重启保存，均为输入提供的先前验证结果，本 reviewer 没有重复运行。

## fidelity

| 元素／承诺 | 判定 | 原生证据与含义 |
| --- | --- | --- |
| TYPE | adaptation | 标题、标签、按钮继承 Material 3 type roles 与系统字号，符合 [Android 平台约定](../../.agents/skills/impeccable/reference/android.md)与 FIRST VIEWPORT 的字号适应要求；fontScale 2 的标题和控制文字没有截断。原生控制不要求另造像素显示字体。 |
| MATERIAL | match | Kenney 像素图集用于房屋、作物、人物和环境，动作／物品图片使用一致的像素语言；不是用装饰容器替代经营内容。池塘局部材质缺陷单列。 |
| GROUND | 无色相权威 | OWN-WORLD 没有指定地面精确色值或温度，也没有 approved comp 可采样，不能发明暖／冷目标；截图的草地与土路能够区分行走区域、菜地和物件。 |
| 可玩地图与首屏记忆点 | match | 首屏直接显示我、居民、木屋、作物与方向控制，能描述为“同住并耕作的农场”。游戏入口、背包、布置、资源与经营面板均在地图边界内，地图外保留 Android 页面标题及应用导航。 |
| 图像操作与经营循环 | match | 背包、播种、浇水、收获和共同活动有具体图片；作物阶段、已浇水点、库存与奖励数量反映状态改变。制作、出售、赠礼、换装、布置入口在提供的实现中齐备，未把未截图的状态说成设备验收通过。 |
| 镜头让出操作区域 | adaptation | 手机上的操作面板在地图内下方，宽屏位于地图内右侧；镜头通过 reservedBottom／reservedRight 移开，保留人物与目标的可见空间，符合用户“所有游戏交互在地图内”的确认。 |
| 大字号背包 | adaptation | `phone-large-bag.png` 改为两列并保持完整种子名称。第二排底部部分露出属于 XML 中明确存在的 ScrollView 与代码 verticalScroll，不是不可达的固定截断；滚动操作本身未由本 reviewer 执行。 |
| Android 触控与导航 | match／contradicted | 所提供 controls JSON 的地图内操作无越界，IconButton 为 48dp，动作格大于 48dp，间距 8dp；窄屏 NavigationBar、宽屏 NavigationRail。离开家园却终止宿主 Activity 的行为与 Android 导航保证矛盾，见 F1。 |
| 人物脚底、阴影、名牌与命中空间 | contradicted | `phone-crop.png`、`phone-harvest.png` 朝左人物向右脱开，阴影／“我”／选中圈仍留在原坐标；`tablet-bag.png` 的居民同样偏离其阴影。`home.gd` 在负宽镜像前增加整个 sprite 宽度，是现象对应的绘制路径，见 F2。 |
| 连续水面 | contradicted | `phone-overview.png`、`landscape.png`、`tablet.png`、`tablet-bag.png` 的池塘内部每格都有浅色边，读成棋盘而非水体；岸线应只围绕水体外缘，见 F3。 |
| THESIS：逐步移动与邀请保留 | match／contradicted | HomeTraffic／HomeSceneController 处理占位与逐步移动，地图和命中共用镜头。邀请回应直接提交指定居民的活动，但 `HomeGameUi.open()` 无条件清除 invitation，使打开背包／菜单消除尚未回应的邀请，违反“保留到回应或关闭”，见 F4。 |
| OWN-WORLD：像素农场动态 | match／contradicted | 水波、成长、起伏、操作粒子实现了场景动态。Godot 的 clock／delta 动画没有接收系统 Remove animations，违反原生运动偏好约定，见 F5。 |
| STORY：共享世界与性格居民 | match | HomeState 持有同一世界的玩家、居民、库存与菜地；HomePersonality 读取本机性格／兴趣关键词，HomeResidentBrain 控制散步、活动与接近邀约。实现及说明没有宣称在线模型自主决策。新增／删除居民的完整设备操作不在本次截图内。 |
| FIRST VIEWPORT：少文字、图片与可操作空间 | match | 首屏没有说明文章；物品和动作采用图像、短标签与进度。普通、大字体、横屏、平板都有地图内控制。人物错位和连续水面两项仍损害空间理解。 |
| FORM：原生 Godot 路线 | match | Android 内嵌 Godot，Room 继续作为共享存档事实来源；桥接包仅含渲染字段，不包含聊天正文或凭据。上游采用范围已明确为 Crop／图集适配，而非整个示例游戏直接移植。 |

## ceiling

未达到。像素素材与原生图像控制已经承担场景内容，尚未用稳固的角色锚点与连续水面完成空间表达；常规导航不能以退出到模拟器桌面结束。原生系统动画偏好也未进入 Godot 场景。

Material 3 色彩角色、字体与导航在所给明亮截图中可检查；暗色主题、TalkBack、预测返回手势、真实刷新率与性能没有对应证据，结论限于已提供的截图、控件树和实现。默认状态下没有发现 kicker、装饰性玻璃、渐变文字、Unicode 代替图标或与像素世界无关的硬阴影。

## material_fixes

1. **F1／P1 — Android 导航与 THESIS：** 修正唯一 Godot 渲染视图的宿主生命周期，使离开家园、返回聊天及再次进入只改变可见性／渲染生命周期，不能因 AndroidView detach 触发 `onGodotForceQuit → Activity.finish()`；用有效家园重入截图和实际离开／重入记录确认，`tablet-reentry.png` 当前桌面故障不能算通过。
2. **F2／P1 — 地图空间与命中一致性：** 修正 `home.gd::_actor()` 的朝左镜像，使两种朝向共享同一脚底中心；人物图像、穿搭、手持活动图片、阴影、名牌和目标命中必须沿用同一 actor 坐标，在 `phone-crop.png`／`phone-harvest.png` 及平板居民场景确认。
3. **F3／P2 — MATERIAL 与 OWN-WORLD：** 在 `_pond()` 使用统一的整数格坐标建立邻格集合，只给没有水体邻格的外边画岸线，保留水波与木栈道；`phone-overview.png`／`landscape.png`／`tablet.png` 必须呈现连续内部水面，不再逐格框线。
4. **F4／P2 — THESIS 邀请保留：** 去掉打开普通背包／菜单时清空 invitation 的行为；面板期间可隐藏邀约，关闭面板后恢复同一邀请，只在回应、明确关闭或该居民被删除时清除，并记录打开背包后收起仍能回应原邀约的实际操作结果。
5. **F5／P2 — Android 系统运动偏好：** 将系统 Remove animations 状态传入场景并响应变化；关闭水波、闪光、粒子和人物起伏等装饰运动，保留经营结果与必要的位移结果，补充该设置下的原生操作证据，不能用普通截图或 JVM 通过替代。

## keep

保留同一共享存档、地图内物件驱动交互、图像库存与动作、指定居民回应、性格驱动散步、镜头让位、大字号两列与 Material 3 原生导航；修复中不再引入地图外经营面板或长篇说明。
