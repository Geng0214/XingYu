# E1 第四段：QuickJS 本地脚本运行时基础

日期：2026-09-20。状态：源码/原生构建和宿主脚本兼容检查完成，Android JNI 运行、ToolPkg 安装与模型调度未完成。仍为内部 0.5.0/code 5/schema 5，公开交付保持 0.4.0。

## 当前实现

- 采用 [QuickJS 官方 2026-06-04 发布源码](https://bellard.org/quickjs/)；必要的 17 个源码、头文件和许可文件共 2614001 字节，保存在 third_party/quickjs。源归档 SHA-256 和逐文件哈希固定，Gradle 编译前检查；不拉取 master，不新增 Gradle 模块，不把引擎命令行程序带进 Android。
- 基线 QuickJsNativeRuntime 的独立实例模式适配到现有 app/CMake。原桥未设置内存上限且使用 JNI modified UTF-8，当前改为每次调用新 VM、32 MiB 内存、512 KiB 栈、最多 4096 个 Promise 作业、100～30000 毫秒时限、不可清除的取消标记和真实 UTF-8 byte[] 边界。限制使用 [官方 C API](https://bellard.org/quickjs/quickjs.html#Memory-handling)；实际 Android 表现待验。
- 运行时不链接 std/os、动态模块加载器或通用 Java 宿主桥，不公开网络/文件/设备宿主 API。禁止阻塞式 Atomics.wait。异常只返回固定类别，不读取私密异常正文或脚本堆栈。这是受限语言运行时，不是独立 Android 进程沙箱。
- 原生句柄由登记表和共享所有权管理，关闭/中断不释放仍在求值的 VM。Kotlin 使用单个工作线程和有界队列；取消时直接通知原生中断，不靠等到脚本结束后才检查协程取消。
- CommonJS 支持 exports/module.exports、包内相对 require、async 返回和 complete 回调；源代码与参数以 JSON 数据进入包装器。禁止越过包根、设备绝对路径和裸模块导入。不提供包外文件查找；文件来源将在下一步由已验证的安装快照传入。
- 原 examples/time.js 从接手基线恢复，未修改。它仅是脚本示例，仍不是已完成安装的 ToolPkg 包。代码没有修改 Room schema 或用户数据，也没有在启动时自动执行此脚本。

## 已执行验证

- 64 项 JVM 测试通过，无失败、错误或跳过。新增 5 项测试由实际 Kotlin 包装器生成脚本，并在官方同版本 Windows QuickJS CLI 中运行，覆盖中文/emoji/NUL、参数注入、CommonJS 相对引用、complete、非法模块路径、缺失函数/结果、重复完成和输入上限。
- time.js 的 get_time 与 format_time 在真实 QuickJS 引擎执行，时间戳与宿主时钟范围相符，返回格式通过检查；不是使用假结果代替插件执行。
- ARM64 debug/release 原生构建、Android 测试编译、R8、release lint 和签名打包通过。新增 8 项 Android 测试涵盖实际 JNI UTF-8、CommonJS、20 次新实例隔离、无 OS/Java 桥、循环/Promise 预算、20 次取消、内存/输出/阻塞等待上限及私密错误文本；当前仅编译，未运行。
- APK 静态扫描通过：三个预期原生库、无新增权限/后台组件、无签名材料/旧载荷、许可内容一致；libquickjsjni.so 为 1359424 字节，ELF PT_LOAD 为 16384 对齐，没有 Windows 机器路径字符串。zipalign -c -P 16 4 通过。
- 内部 APK 8979960 字节，SHA-256 为 0d31e2924964460842d36a22c8680301e3e59f987787b3c4d115816795336bbc；签名 SHA-256 保持 ac7283d4cc843d710a77f3c5b3f96ee3663793b78e8fb30c6a145b8e37f497fa。公开 0.4.0 包未替换。

详见 [quickjs-tests.json](quickjs-tests.json) 与 [quickjs-apk-audit.json](quickjs-apk-audit.json)。内部包在本段源码提交前构建，不作为最终源码归档与正式版本的对应关系证明；E1 交付时将基于已提交源码重新打包。

## 构建处理与复现

首次 C++ 编译发现 Windows 不区分大小写，QuickJS 的 VERSION 被当作 C++ 标准库的 version 头。采用基线原有的生成转接头方式，不再把 QuickJS 源码根目录加入 C++ 搜索路径；没有改动上游源文件。Gradle 哈希任务的 MessageDigest 导入也已修正。

Windows 宿主测试工具只放在 .gradle-m0/companion-tools，不加入仓库或 APK。首次运行 .venv/Scripts/python.exe ci/script/prepare_quickjs_test.py；脚本校验官方二进制 ZIP 的固定哈希，测试再次校验 qjs.exe 与 DLL。Android 构建使用固定 NDK 27.3.13750724、CMake 3.22.1。

验证命令：ci/build.ps1 -Tasks ':app:testDebugUnitTest',':app:compileDebugAndroidTestKotlin',':app:assembleRelease','--offline'；.venv/Scripts/python.exe ci/script/verify_apk.py --report quickjs-apk-audit.json。

## 未完成与下一步

- 当前设备列表为空，Android JNI 的时间/内存/取消/生命周期验证为 NOT_RUN。Windows CLI 测试没有执行这份 JNI，也没有模拟其安全边界；不能作为 Android 运行通过。
- ToolPkg 清单、METADATA 与版本校验，导入预览，加密包保存，启停卸载、角色/能力授权，以及本地插件→模型的实际回传链路尚未实现。下一步优先接上这些功能，保留完整 E1 目标。
- 操作系统宿主 API、旧 Compose DSL/Hook 插件和其他 Operit 包没有因此获得兼容性。完整包示例需要实际安装、调用、禁用、删除和迟到结果测试后才能声明支持。
- SQLCipher/升级/真实模型/MCP/跨设备/用户观察待验继续保留；E2～E8 按总计划继续，未标记完成。
