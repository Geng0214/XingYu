# 星语

基于 Operit 裁剪的 Android 陪伴聊天应用。Kotlin、Jetpack Compose、Room + SQLCipher、OkHttp；家园内嵌 Godot 4.7.2，仍为单应用模块。应用包名 `cn.localfirst.companion`，Android 8.0 及以上、ARM64。

第一次用软件或看项目，先从本页开始。只需先认三个文件夹：`app/` 是应用，`docs/` 是说明，`ci/` 是开发脚本。其他目录用途见[项目导航](docs/README.md)。

版本功能与升级方法见[使用与升级](docs/使用与升级.md)；开发进度和待验收事项见[项目导航](docs/README.md)及[当前计划](docs/当前计划.md)。

## 使用

1. 安装 `output/local-companion-0.40.0.apk`。这是个人试用包；APK 与源码包不随本仓库分发，clone 后需按下方“构建”自行生成。
2. 打开“设置”，添加模型服务，填写 HTTPS 地址、模型和自己的 API Key。确认地址后再测试；测试可能产生少量费用。
3. 选择或新建一个角色，进入“聊天”开始对话。角色、会话和个人资料都在本机管理；角色管理页可导入 SillyTavern JSON 角色卡，导入前先预览。
4. 聊天输入栏的“+”可打开图片、资料、表情、伴读和生图等功能；录音预览可直接发送语音条，长按消息可加反应，角色资料页可安排固定或由角色生成的定时消息。使用细节见[使用与升级](docs/使用与升级.md)。
5. 打开“家园”进入共享像素农场，点击地面走动、菜地种植、人物互动；所有聊天角色入住同一世界。角色资料页可管理记忆、日记、关系、日程与主题。
6. “日程”可按周／月选择日期，查看当天时间线；待办分为逾期、今天、之后和已完成。点击“+”添加，顶部信息按钮查看分享与提醒说明。
7. “设置”里可管理扩展、隐私锁和加密备份。调用工具前需要授权，备份前可预览内容。

聊天数据保存在本机加密库中；发送给模型或第三方服务的内容取决于你选择的功能和确认。API Key 独立加密。卸载或清空应用会失去未备份的数据。完整的数据保存、发送和升级说明见[使用与升级](docs/使用与升级.md)。

升级时覆盖安装并保留原应用数据，不要卸载或清空。遇到升级问题时先保留数据并查看[升级说明](docs/使用与升级.md)。

用户设备统一安装上述正式包。连接设备后使用单应用安装入口，参数填写设备序列号和 Android SDK 目录：

```powershell
./ci/install.ps1 -Serial '<设备序列号>' -SdkRoot '<Android SDK目录>'
```

该入口核对交付包的包名和非调试标记，覆盖安装正式版后移除历史调试版及测试包，保留正式版数据和被移除调试包的私有数据。两个构建类型共用功能源码，不需要同时安装；调试版验收样本不追加到正式版。实际处理见[单应用安装验收](evidence/E13_SINGLE_APP/validation.md)。

## 构建

构建命令用于开发验证；当前版本的设备验收状态请看[项目导航](docs/README.md)及对应 `evidence/` 阶段记录。

冻结工具链：JDK 21、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Android SDK/Build Tools 36、NDK 27.3.13750724、CMake 3.22.1、Godot 4.7.2.stable.official.ed1daf0bf。QuickJS 2026-06-04 的必要源码已固定在 third_party/quickjs，构建逐文件检查哈希，不在线拉取浮动分支；仍为一个 app 模块。

下载官方同版本 Godot 控制台编辑器并指定其路径。Gradle 的 `exportHomeGame` 在 Android 资源合并前导出 `game/home/` 为 PCK；Android 引擎来自固定 Maven 依赖，不安装第二个游戏应用。场景源码、导出步骤和 MIT / CC0 来源见 [家园工程](game/home/README.md)。

Windows 宿主测试增加官方同版本 QuickJS 命令行程序。先用 Python 建立本项目 .venv，再执行一次下列命令准备已固定哈希的测试工具；保存在 `.local/.gradle-m0` 缓存中，不打包进 APK 或源码 ZIP。它用于脚本兼容测试，Android JNI 仍需设备测试。

```powershell
.venv/Scripts/python.exe ci/script/prepare_quickjs_test.py
```

在 `local.properties` 配置 `sdk.dir`。发布签名使用该文件的 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`；文件和密钥库不得提交。缺发布签名时请先构建 debug，或用 Android keytool 创建自己的测试密钥。没有内置测试口令或内置签名材料。

本机快捷入口：

```powershell
./ci/build.ps1 -GodotExecutable '<Godot 4.7.2 控制台程序路径>' -EvidenceStage E17_FEATURE_PHASE1
```

其他 Windows 环境设置自己的 `JAVA_HOME`、`ANDROID_HOME` 和 `GODOT_EXECUTABLE` 后使用 Gradle：

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
./gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

输出位于 `build/app/outputs/apk/`。本机脚本沿用 `.local/.gradle-m0` 缓存与代理。Gradle 下载依赖需要访问 Google Maven 和 Maven Central，网络限制环境请配置自己的代理。

更新依赖声明时，重新生成随包许可，并将检查结果记录在对应阶段的 `evidence/` 中：

```powershell
./ci/build.ps1 -Tasks ':app:writeRuntimeInventory' -GodotExecutable '<Godot 4.7.2 控制台程序路径>' -EvidenceStage E17_FEATURE_PHASE1
node ci/script/generate_notices.mjs E17_FEATURE_PHASE1
```

包检查在自己的 venv 中运行 `python ci/script/verify_apk.py`，读取 local.properties 的 sdk.dir 使用 Build Tools 36 aapt2；也可通过 --aapt2 指定工具路径。检查 APK 内实际 Manifest、合并 Manifest、签名材料、原生库路径和 16 KB ELF 对齐；签名还需使用 SDK 的 `apksigner verify`。

`ci/` 内的专用脚本按所涉及功能单独运行；实际运行结果与未完成的设备验收应记录在对应阶段的 `evidence/` 中。

家园游戏控件边界检查读取实际 Android UI 层级文件，确认游戏操作都在地图范围内；应用导航另行保留。先在设备打开待检查的家园面板，再导出 `uiautomator dump` 文件：

```powershell
.venv/Scripts/python.exe ci/script/verify_home_controls.py captured-home.xml --report evidence/E15_GODOT_HOME/controls.json
```

该检查只判断实际控件边界，不能代替截图、居民连续行为或真机触控验收。

## 真机验证

真实 API Key 直接在 App 内输入，不通过聊天记录或命令参数传递。Android 仪器测试使用 `.debug` 包和合成数据，应在独立验收设备运行；用户的 MuMu 只安装正式包：

```powershell
./ci/build.ps1 -Tasks ':app:connectedDebugAndroidTest'
```

真机测试需使用已连接并授权 USB 调试的 ARM64 设备。设备测试的覆盖范围会随当前开发阶段变化；以阶段验收记录中实际执行的项目为准。JVM 单元测试或 APK 扫描不能代替真实设备和服务验收。

上游 [Operit](https://github.com/AAswordman/Operit)，主代码 LGPL-3.0-only。分发 APK 时同时提供本版本对应源码、构建说明和 [第三方声明](THIRD-PARTY-NOTICES.md)。本仓库不包含发布私钥；接手前的上游历史不应用于制作分发源码快照。
