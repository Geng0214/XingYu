# E15 Godot 共享种地家园验收

2026-10-03，按用户选定的开源种地路线实现家园。用户此前确认旧方案未发布，并授权继续构建、测试、Release 与 MuMu 操作。本轮继续使用当前共享世界；数据库 schema34、个人备份 v32、家园内部格式 3 未改。

## 实现

- 内嵌固定 Godot 4.7.2 Android 引擎，地图、作物和人物由 Godot 绘制，原生 Compose 承担地图内图像控件、名牌及无障碍语义。旧 Canvas 世界与人物绘制已删除，可复用的物品、动作和家具预览保留。
- 作物资源代码改编自 MIT `gadget-hq/2d-farming-game`，提交与依赖哈希见 [source-lock](../../game/home/source-lock.json)。未移植独立库存或示例地图；上游 Sprout Lands 非商业美术没有纳入。新图集来自 Kenney CC0 Tiny Town / Tiny Farm / Tiny Dungeon，结合项目原创像素物件，见 [来源](../../game/home/art/sources.json)和[场景说明](../../game/home/README.md)。
- Kotlin / Room 继续保存地图几何、碰撞、角色入住、位置、库存、成长时钟和经营账本。渲染投影不包含人设正文、消息或凭据。人物朝向、步态起伏、水波、场所闪光和操作粒子均参与画面；当前人物图集没有四方向序列帧。
- 单一原生渲染视图固定在 Activity 内容容器，离开家园时隐藏并暂停，重新进入复用；Activity 销毁才结束引擎。背包和菜单不再清除未回应邀约。系统动画偏好直接读取设置并传入场景，关闭装饰运动而保留游戏动作和经营计时。

## 已运行检查

1. `ci/build.ps1` 执行 `:app:testDebugUnitTest` 与 `:app:assembleRelease`，570 项 JVM 用例通过，失败／错误／跳过均为 0，见 [测试汇总](unit-tests.json)。包含 6 项 HomeRenderFrame 投影用例。最后 Android 动画设置读取调整之后单独再次执行 Release 构建（3 分 33 秒），未重复执行 JVM 用例；该调整由下面的真实设备设置切换验证。
2. Godot headless 检查通过：作物成长阶段、132 个图格区域、场景加载和 JSON 浮数池塘格归一化。使用 JVM 生成的明确合成夹具，不导入用户数据库。PCK 导出脚本对错误输出及缺失文件明确失败。
3. [APK 静态核验](apk-audit.json)、SDK `apksigner verify` v2 签名、`zipalign -c -P 16 -v 4` 和全部 5 个 ARM64 原生库的 16KB ELF 对齐通过。包内 PCK、第三方声明、正式应用身份及星语名称／图标核对通过；178 个运行依赖许可见 [runtime-licenses](runtime-licenses.json)。
4. 本轮 3 份 Godot 图集与 2 份原生人物／种子图集溯源扫描为 5 个文件、0 missing。全 drawable-nodpi 扫描另发现 5 个本轮未改变的品牌／其他功能资产缺嵌入元数据；这些为预存漂移，不把本轮结果扩大为整个 APK 的溯源完成。

## MuMu 原生操作

Android 15 / API35，MuMu ARM64 转译与 OpenGL ES 3.2 Compatibility。截图均为真实 `adb screencap`，像素未编辑；截图与实际层级对应关系见 [capture-matrix](capture-matrix.json)。MuMu 的 `uiautomator` 工具偶尔在已写出 XML 后返回异常退出，明确核对输出成功标记并读取该次实际 XML；此工具行为没有当作应用通过或应用崩溃。

| 范围 | 实际结果与证据 |
| --- | --- |
| 手机 375dp | 900×1600、density384、fontScale1；主地图、背包、作物、收获、全图、室内与活动均可操作。见 phone 系列截图及 XML。 |
| 200% 字号 | 同一手机 fontScale2；地图和物品保留图片入口，背包为可滚动两列。见 [地图](phone-large-text.png)与[背包](phone-large-bag.png)。未把静态截图当作全部物品滚动验收。 |
| 横屏与平板 | 1600×900 / density240 与 1920×1200 / density240。见 [横屏](landscape.png)、[平板](tablet.png)与[平板背包](tablet-bag.png)。矩阵中的 19 组及额外 final-home 的原生控件检查均在地图内；该检查不代替 TalkBack 或触控目标尺寸验收。 |
| 重入与后台恢复 | 最终包同一进程 9590，聊天↔家园连续 3 次、桌面后台再返回均成功，期间没有 force-stop；见 [lifecycle](lifecycle.json)与[重入画面](tablet-reentry.png)。另外调整设备类别时主动冷启动，人物、库存、田地与活动保留。 |
| 自主邀约与面板 | 你好主动接近并提出 CLEAN 邀约；背包与菜单分别隐藏邀请，收起后恢复同一角色与活动。见 [邀请核对](invitation-retained.json)、before/bag/after/menu/after-menu 原生截图与 XML。实际回应开始 CLEAN，完成后手动领取奖励。 |
| 系统移除动画 | 同一背包与池塘 ROI 原生两帧间隔 1.1 秒，scale0.5 时 188 像素变化，实时改为 0 后变化为 0；见 [正常动画](motion-normal.json)和[移除动画](motion-reduced.json)。设置 0 时实际向左移动仍可操作，见 [游戏动作](reduced-motion-gameplay.png)。该对比仅测水面，不扩大为整个画面的动画指标或性能结果。 |
| 种植循环与室内 | 原有成熟胡萝卜收获；实际播种胡萝卜、浇水，经过自然时间后再次收获 +3；番茄播种和浇水；最终在第 0 块地播种棉花并浇水。见 [收获](phone-harvest.png)与[棉花](phone-crop.png)。通过地图进入小屋显示家具，重启仍保留位置，见 [室内](phone-interior.png)。 |

测试使用正常游戏操作，未注入或清空用户数据库。旧“你好”角色、最近消息、家园名称与居民继续可见；不宣称完整数据库或备份比对。原 120 币／7 精力经旧休息奖励、两次 CLEAN 活动与领取后变为 160 币／8 精力；胡萝卜库存为 6，棉花种子 2→1。变化来自上表实际操作，不能写成数值未变。

设备屏幕尺寸、密度、字体、自动旋转及动画时长已恢复原设置；最终包仍只有 `cn.localfirst.companion` 一个已安装应用。设备安装文件 SHA-256 与交付文件逐字节哈希一致，见 [安装与恢复记录](release-device.json)。恢复后的家园见 [final-home](final-home.png)，额外控件边界见 [final-home-controls](final-home-controls.json)。

## 独立复核

首次 [finish-review](finish-review.md) 提出五项：页面离开终止引擎、朝左人物偏位、池塘内部网格岸边、背包／菜单清除邀请、未遵守系统移除动画。已处理并把同组 12 张原生画面覆盖重截送回同一 reviewer 评分，五项全部 resolved，专项 [verdict](verdict.md) 为 `ship`；结论仅覆盖这五项修复，不扩大为整面重新审计或全设备认证。原生本轮未运行 HTML／CSS detector，没有 approved comp。实际设计令牌与平台约定由独立 documenter 更新至 [DESIGN](../../DESIGN.md)与[原生扩展](../../.impeccable/design.json)。

## 交付与限制

交付 [APK](../../output/local-companion-0.39.0.apk)：星语 0.39.0 / code40，89,987,965 字节，SHA-256 `D553FEE0B827C93F2A27F7660FE2275134BBD1CCCFDC7CF148EF5DBE5CB03011`。最终 Release 已通过单应用入口覆盖安装 MuMu，包名、签名及共享存档身份一致。体积增加主要来自内嵌 Godot 引擎。

本轮没有 Android 仪器测试，E11 的 11 项 SQLCipher 用例仅为历史证据。未验收真机、TalkBack、暗色主题、预测返回、性能／电量、通知发送、隐私锁认证或在线模型；未执行付费服务。运行中改 density 后整个应用导航类别需冷启动重新核对，因此尺寸矩阵使用冷启动，不宣称任意 density 热切换通过。历史源码包与 delivery.json 未重新生成。
