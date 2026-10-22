# E11 共享家园经营游戏验收

2026-10-02。用户确认旧家园未发布，并授权编译、测试、Release 构建与 MuMu 验收。家园已换成一个共享像素世界；没有转换开发中的旧分角色家园，聊天及其他个人数据继续保留。

## 构建、存储与交付

- 通过 `ci/build.ps1` 完成 Debug、Android 测试 APK 和 Release 构建；界面修正后再次构建 Debug/Release，结果 `BUILD SUCCESSFUL`。Room 实际导出 schema34，个人备份格式 v32。
- 最新源码 555 项单元测试通过，失败、错误、跳过均为 0。结果由 `build/app/test-results/testDebugUnitTest/TEST-*.xml` 汇总，见 [unit-tests.json](unit-tests.json)。
- MuMu Android 15 上运行 `HomeStorageAndroidTest`、`HomeRewardAndroidTest`、`MigrationAndroidTest`、`BackupAndroidTest`、`PlayStorageAndroidTest`，共 11 项通过，75.284 秒。见 [最终设备测试输出](android-tests-final.txt)。这些测试在最终 UI 修正前执行；后续只修改地图布局、名牌和按钮间距，存储实现没有再变动。
- 设备测试覆盖空库首次用户与默认伙伴、增加/删除居民、共享世界保留、位置与物品重新读取、CAS 与重复纪念物领取、完整世界备份导入、只追加居民、schema1～33 到34的迁移及损坏归属引用拒绝。备份用合成数据库和测试口令，没有向外部服务发送数据。
- [主机迁移检查](home-migration.json)通过，DDL 与 Room schema34 一致。此项只运行主机 SQLite，设备 SQLCipher 验证以上述 Android 测试为准。
- [APK 检查](apk-audit.json)、[v2 签名](signature.txt)、[16KB zipalign](zipalign.txt)通过；三个 ARM64 原生库的 ELF `PT_LOAD` 对齐均为16KB。
- Release 覆盖安装成功；原有角色“你好”和最近消息仍可见。家园首次进入出现“我”和该角色两个人物；Release 实际播种、浇水成功，种植状态在一次冷启动后保留。
- `git diff --check`、新增文档的 Markdown 相对链接检查与设计 JSON 解析通过；交付文件哈希与 APK 检查记录一致。

交付：[local-companion-0.39.0.apk](../../output/local-companion-0.39.0.apk)，包名 `cn.localfirst.companion`，版本0.39.0/code40，17,072,719字节。SHA-256：`2BA6907C10641099DDF423C1106370B951AB155FBD6E30344CE2A1A82FCD634C`。历史源码包与 `delivery.json` 未重新生成。

## 实际经营与角色操作

Debug 使用默认“初见”及临时合成角色 Mori（人设为 `Synthetic test resident`）；Mori 已在验收后删除。截图中的游戏库存和奖励均由真实界面操作产生，未修改设备时钟、数据库或奖励。

| 操作 | 观察结果与截图 |
| --- | --- |
| 播种、浇水、等待成熟、收获 | 胡萝卜与棉花自然成熟，收获3份作物并归还种子。[收获](harvest.png)、[Release种植](release-planting.png) |
| 新增居民 | 保存 Mori 后直接在原农场生成第三个人物，已有作物保留。[新增居民](new-resident.png) |
| 采集、钓鱼、出售 | 获得木材和石料、消耗精力；钓得鲜鱼并出售，家园币由132增至150。[采石](gather-stone.png)、[钓鱼](fishing.png)、[出售](selling.png) |
| 制作与赠礼 | 用收获的胡萝卜制作汤，赠送 Mori 后个人库存增加1份、好感增加2；另用棉花制作棉布。[赠礼](gift.png) |
| 共同活动 | 与 Mori 喝茶20秒，领取12币和8经验；床上休息30秒，精力由3恢复到9。[活动中](together.png)、[领取](together-complete.png) |
| 家具摆放 | 支付65币、3木材与1石料，蜂蜜沙发替换鼠尾草沙发，进入室内可见黄色新沙发。[制作摆放](decorate.png)、[最终室内](../../.impeccable/review/interior.png) |
| 冷启动与删除居民 | 冷启动后币、精力与经营进度保留。通过角色管理删除临时 Mori，农场、两块棉花、蜂蜜沙发和背包继续保留。[重启](restart.png)、[删除后世界](role-deleted-world-kept.png) |

部分操作截图拍摄于 UI 修正前，用于证明当时的经营结果；最终布局以以下复核截图为准。

## 原生界面复核

最终 Debug 截图均来自 Android 15 MuMu，采用应用夜间主题。无 HTML 检测器。手机375×800dp、紧凑横屏800×375dp、宽屏约1067×600dp；系统字号100%与200%。Release 另检查原有日间主题及线框图标。

- [手机](../../.impeccable/review/phone.png)、[宽屏](../../.impeccable/review/tablet.png)、[紧凑横屏互动](../../.impeccable/review/phone-landscape.png)。
- [200%字号](../../.impeccable/review/phone-font200.png)、[200%字号互动](../../.impeccable/review/phone-font200-interaction.png)、[夜间](../../.impeccable/review/phone-dark.png)。
- [全图](../../.impeccable/review/overview.png)、[室内](../../.impeccable/review/interior.png)、[修正后互动](resident-interaction.png)。
- [Release日间首屏](release-phone.png)、[Release日间全图](release-overview.png)。
- [恢复原显示设置后的 Release 全图](release-original-overview.png)、[恢复后近景](release-original-display.png)。最终 Release 进程日志未发现 `AndroidRuntime` 或 `FATAL` 异常。

独立复核首次结论 `fix`：互动面板遮挡人物与 HUD、名牌压住人物、触控间距不足。修正后操作面板独立放在地图下方或宽屏右侧；地图使用剩余视口，名牌先测量再避开人物轮廓，方向键和动作换行间距8dp。复查结论为 **`ship`**，三项问题均解决，全部八张复核截图有效。该结论覆盖这批修正，非所有应用页面的认证。见 [复查记录](../../.impeccable/review/verdict.md)与[设计记录](../../DESIGN.md)。

已修复的执行问题：新地图图标没有注册到主题图标集合，导致首次打开异常；画布没有裁剪；旧测试仍传入已删除的价格字段、备份未来版本号与当前格式撞号；旧剧情迁移测试漏注册后续迁移，且无索引的历史 schema 夹具读取失败。迁移复测还发现旧家园清理带走了仍有用的聊天归属校验，现已在共享迁移保留该检查。最终11项设备测试通过；先前失败输出保留作排查记录，不能当作最终结果。

MuMu 的 `uiautomator` 进程曾在输出有效XML后自身退出异常；这与已修复的应用图标异常分别处理，没有给应用增加替代路径。分辨率、密度、系统字号、旋转与系统夜间设置已恢复原值。

未验证：真实手机触控与性能、TalkBack、系统“移除动画”、所有服装及全部活动的逐项手工操作、在线模型和付费服务。家园对白为本机游戏内容，不调用模型。
