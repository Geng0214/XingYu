# 星语共享家园

Godot 4.7.2 内嵌在现有 Android 家园板块中。聊天与家园属于同一个 APK，家园继续使用共享 Room 存档，不另建游戏账号或角色存档。

## 实施边界

- [HomeWorld](../../app/src/main/java/com/ai/assistance/operit/data/model/HomeWorld.kt) 是地图坐标、碰撞、寻路与命中检测的事实来源。场景读取同一份几何。
- [HomeState](../../app/src/main/java/com/ai/assistance/operit/data/model/HomeState.kt) 和 [HomeLife](../../app/src/main/java/com/ai/assistance/operit/data/model/HomeLife.kt) 负责库存、种植、成长时间、制作、采集、家具和共同活动。所有经营动作仍通过原有事务与账本执行。
- [HomeGameScene](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameScene.kt) 保留人物占位、玩家行走、居民性格行为与位置保存。新增聊天角色通过现有共享世界入住逻辑成为居民。
- [HomeRenderFrame](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeRenderFrame.kt) 只传递场景需要的坐标、作物阶段、资源可用性、穿搭、活动与镜头参数，不发送人设正文、聊天消息、模型地址或凭据。
- [home.gd](home.gd) 使用 CC0 图集绘制地图、房屋、作物、人物、水波和操作粒子。当前人物步态由位移、朝向镜像和起伏组成，图集没有四方向序列帧。
- [HomeGameUi](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeGameUi.kt) 继续提供地图内的原生图像按钮、背包、制作、布置、居民互动与无障碍动作。操作面板和可玩区域共用镜头，操作保留系统字号与 Material 主题。

当前自主性格采用本机人设关键词匹配；不是在线模型自主决策。居民不自动消费材料或领取经营奖励。Godot 场景不写第二份存档。

## Android 宿主

[HomeGodotHost](../../app/src/main/java/com/ai/assistance/operit/ui/main/HomeGodotHost.kt) 将唯一渲染视图固定在 Activity 的 Scaffold 内容容器中；家园只发布实际地图窗口坐标、大小与场景数据。原生视图定位到该地图区域，Compose 图片控件与名牌绘制在其上方。家园离开前台时隐藏视图并暂停渲染，重新进入时复用；只有整个 Activity 销毁才结束引擎。不能把 Godot GL 视图随家园页面移除，因为 GL 线程退出会销毁引擎并结束宿主。聊天与资料内的家园入口使用同一宿主。

Android 配置变化由宿主接收并转发，引擎与点击使用实际视图像素尺寸。系统移除动画通过 `Settings.Global.ANIMATOR_DURATION_SCALE` 与设置观察器传入场景，直接读取当前偏好，关闭装饰起伏、水波、闪光和粒子；经营计时与动作结果继续使用共享存档时钟。

Godot JNI 按方法原名查找 JVM 接口，Release 的 [保留规则](../../app/proguard-rules.pro) 明确保留 Godot 包及本项目桥接类。不能仅依赖库的 consumer rules；首次 MuMu 实测已经发现压缩遗漏并据此修正。

## 构建与核验

编辑器使用官方 `4.7.2.stable.official.ed1daf0bf`，Android 依赖固定为 `org.godotengine:godot:4.7.2.stable`。运行 [构建入口](../../ci/build.ps1) 时可通过 `-GodotExecutable` 指定控制台编辑器；脚本检查版本并设置 `GODOT_EXECUTABLE`。

Gradle 的 `exportHomeGame` 任务先生成 `build/generated/home-assets/home.pck`，再打包进唯一 APK。导出日志包含脚本错误时拒绝交付。PCK 不含测试脚本、素材来源清单和开发说明。

```powershell
./ci/build.ps1 -Tasks @(':app:testDebugUnitTest', ':app:assembleRelease', ':app:writeRuntimeInventory') -GodotExecutable '<Godot控制台程序路径>' -EvidenceStage E15_GODOT_HOME
```

Godot 的 [独立检查](tests/verify.gd) 验证阶段与图集，并读取 JVM 测试生成的明确演示夹具 `build/e15-home-fixture.json`。这份合成数据仅供检查，不导入用户存档：

```text
& '<Godot控制台程序路径>' --headless --path game/home --script res://tests/verify.gd -- --preview '<build/e15-home-fixture.json的绝对路径>'
```

Release 包通过 [APK 核验](../../ci/script/verify_apk.py) 后，用 [单应用安装入口](../../ci/install.ps1) 覆盖安装。实际测试与设备范围见 [E15 证据](../../evidence/E15_GODOT_HOME/validation.md)。

## 第三方来源

采用 [2D Farming Game](https://github.com/gadget-hq/2d-farming-game) 的 MIT 作物阶段与图集资源实现思路；[crop_stage.gd](crop_stage.gd) 是适配共享世界时钟后的资源。上游提交与 Android 库版本／哈希见 [版本锁定](source-lock.json)，完整声明见 [MIT 正文](../../third_party/licenses/tall-tales-farming-mit.txt)。没有移植其独立库存、键盘操作或整张示例地图。

素材使用 [Kenney Tiny Town](https://kenney.nl/assets/tiny-town)、[Tiny Farm](https://kenney.nl/assets/tiny-farm)、[Tiny Dungeon](https://kenney.nl/assets/tiny-dungeon)，均为 CC0。来源、原文件哈希和嵌入来源后的文件哈希见 [美术清单](art/sources.json)。只增加来源元数据，图像像素未改。上游教程中的 Sprout Lands 素材没有纳入本项目。

Godot 及其组件许可正文在 [Godot 声明](../../third_party/licenses/godot-4.7.2.txt)，通过同版本官方引擎的许可接口提取，并由声明生成脚本纳入 APK 的第三方声明。
