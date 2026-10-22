# E6 像素冒险：0.38.0

本批把“把灯带回海边”规则核心接入原生活动页面、角色对白、生产存档和个人备份。版本 0.38.0/code 39，保留包名、签名和 Room schema 29，备份内容 v28 读取 v1～v27。开发接通、验收待补齐；完整 E6 与总计划仍在进行。原核心 502 项测试证据保留在 [E6_ADVENTURE_CORE](../E6_ADVENTURE_CORE/acceptance.md)。

## 行为与验证范围

三张地图包含地形阻挡、四方向移动、近邻交互、主线材料消耗与围巾支线。位置、背包、探索与奖励由合法动作重放得到，模型只能回复角色对白。地图提供近景/全图、完整文字位置和移动按钮。完成后的灯塔、工作台及机关仍显示状态，已拾取材料从地图移除。原始图集和最终生成提示词见本记录的[素材来源](#素材来源)。

### 素材来源

本轮使用内置 imagegen 工具生成原创 4×4 透明像素图集，直接用作原生 Canvas 图片资源；未使用命令行图像接口，也未引入第三方游戏素材或附加运行库。生成日期为 2026-09-21。

文件位于 `app/src/main/res/drawable-nodpi/adventure_atlas.png`，尺寸 1254×1254，RGBA PNG，1,323,436 字节；SHA-256：`946904d1fdb5b14cee3df3b59902d4ce62adcf000d17e31f0f187c8e79c35292`。图集按四行四列取整边界采样；地形铺满格子，其他物件保留透明边缘，不另存素材副本。行序从左至右、从上至下：沙地、水、树、石墙；用户、同行者、守塔人、灯塔；木板、绳索、工作台、拱门；灯罩、萤火种、紫水晶、围巾。图片只负责显示，物品数量、地图碰撞与奖励由本机规则决定。

最终生成提示词：

```text
Use case: stylized-concept. Asset type: one production game sprite atlas for a cozy original native Android pixel adventure, not a mockup. Create a square 4 by 4 atlas, exactly 16 equally sized cells, no gutters or grid lines. Read left to right, top to bottom. Row 1: sandy path terrain tile filling its cell; blue river water tile filling its cell; leafy green tree sprite; grey stone wall terrain tile filling its cell. Row 2: tiny full-body explorer with coral jacket and small backpack; a clearly different full-body companion with teal jacket and long dark hair; elderly lighthouse keeper; small white lighthouse with red roof. Row 3: two wooden planks; coiled rope; wooden crafting workbench; arched stone doorway. Row 4: glass lantern shade; glowing golden firefly seed; violet crystal; red scarf. Pixel art with visibly crisp square pixels, simple readable 16-bit handheld RPG sprites viewed from a high overhead three-quarter angle, consistent scale and light direction. Objects centered inside their own cell with 15 percent clear margin; terrain tiles alone fill their whole cell. All background outside individual objects must be genuinely transparent alpha, not white or a checkerboard. Every sprite stays completely inside its cell; no scene composition or extra objects. No text, numbers, logos, labels, borders, shadows outside cells, or watermark. This atlas will be sampled by exact cell coordinates in the game.
```

每次模型邀请先确认费用，冻结角色、空间、会话、活动和配置。新增 50 次 A/B 上下文交替、各 10 个标记；20 轮实际协调器延迟响应、取消、重试、配置更新及删除；20 次加密备份追加恢复。失败及迟到数据不改变已确认位置或库存，不伪造对白；可明确跳过等待继续探索。模型不接收未探索物件或其他角色私聊。存档容量为完整回复和外层状态编码预留空间。

未提交文字和新建表单存于 ViewModel 内存，页面仍可访问时跨配置重建保留；旧活动回调不能修改新活动草稿。隐私锁关闭访问及进程结束继续清除未提交文本，不写入明文 SavedState Bundle。已写入的记录保留在加密库。容量不足时禁用写入并给出具体原因，不删除旧记录。

Impeccable 独立源码复核的三项修正为：地标可见性、内存草稿重建、容量控件；审查结论为这三项在源码范围 resolved，disposition ship。未进行原生截图或运行审查，不能据此声称视觉、TalkBack、暗色或大字通过。文档复核保留既有 Material 3 体系。编译时曾发现冻结 activity-compose 没有 LocalActivity，已改用现有 LocalContext 解析宿主 Activity，不升级依赖。

## 签名交付

应用源码 `c25e1c7409118a5fe9d698c0173aa042f0883436` 完成 4 分 1 秒干净构建、510 项测试和 AndroidTest 编译；Release Lint 为 0 错误、62 警告、1 提示。之后仅修正文档历史表述和 APK 审计脚本对压缩资源名的解析，应用目录 Git tree 保持 `84fac12cb4387738f4177a83cc02339d47035ed1`。最终源码 `ce2abc2c34af041f247ce35c34e032cd36258844` 重新签名打包，APK 内版本信息与该提交一致。

APK 11,244,187 字节，SHA-256 `46129be9cbc5e9a064b7d922ce121f83e009195f61ff9285eb2de3700ac72013`。源码 ZIP 4,549,200 字节，SHA-256 `a9e785685d36113721bd88cab2e1c469f0c08ef3ddc073954f0ec7097bfdb6d7`；1051 个文件与同一提交的 Git archive 逐字节一致。签名证书连续，实际 Manifest、原生库、禁用载荷、图集资源表映射及 16 KiB 对齐通过。9 项网页音频回归和宿主迁移检查通过。

交付目录保留最新 APK、一个源码 ZIP、说明/清单及 0.37.0 升级基线。清理前核对绝对路径、两份旧 APK 哈希、相关进程及独占读取；移除 0.36.0 基线释放 9,914,663 字节，不创建旧版归档或工程副本。目录由 23,082,296 字节变为 25,775,521 字节，增长主要来自新增图集及源码包。本轮原始图集仅一份纳入工程；历史证据继续保留。

## 待验与后续

修正后 510 项 JVM 测试、70 个套件全部通过，AndroidTest 编译通过；其中新增原生加密冒险存储用例仅完成编译，未连接设备执行。最终源码、干净构建日志、APK/源码哈希、签名和清理记录以本目录 verification.json、delivery.json 及 build-final-clean.log 为准。宿主迁移检查继续验证 schema 29、旧数据保留、事务回滚、50 次查询隔离与 20 次迟到更新，不代表 Android SQLCipher 已实测。

- 待验：真实设备覆盖安装、旧数据/凭据读取、SQLCipher 数据库与 WAL 正文扫描、活动恢复和备份事务。
- 待验：小屏地图与按钮、深色/大字、TalkBack、旋转/锁定/进程结束、停止与快速重试。
- 待验：两个真实模型服务、跨设备备份及三人操作观察；现有正式象棋棋例缺口也保留。
- 继续开发：E6 完整阶段验收、E7 家园/背包/服装及高级美化、E8 真实服务和外设，不把当前构建视为完整计划完成。
