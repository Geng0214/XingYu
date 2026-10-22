# M0-T06 路线与放行决策

日期：2026-09-18；2026-09-19 依据构建现场证据修订；同日再依据 `THIRD-PARTY-NOTICES.md` 成文与条款正文取证修订 B06/B08/B09 的状态栏（B06 的"声明文件未成文"一项已解除，其余仍在）

## 决策

**当前不放行 M1；路线为 Fork 主路线。** 原"必须先完成一次有范围的 M0 补救"中的构建部分已完成：不跳门禁的完整 APK 已产出，基线已可物化复现，签名材料整改已闭合到构建门禁内。仍不放行，因为 B03–B05 依赖真机验证、静态结论不能替代，B06 与 B07–B09 是已实测出缺口但尚未整改的授权与义务问题。

理由：代码结构、Android 原生技术栈、角色/记忆/资料/模型模块与 V2.0 目标有复用价值；工具/后台边界与落盘保护的 S0 风险仍未验证。

## 阻塞项

| ID | 严重性 | 阻塞内容 | 解除条件 | 当前状态（2026-09-19） |
|---|---|---|---|---|
| M0-B01 | S1 | 基线未冻结 | 选定 tag/完整 commit，锁定子模块和资源哈希 | **大部分解除**。基线定义为 主仓库 `31646978` + `:terminal` `04586898` + 两份补丁 + 三个归档的 SHA-256，写进 `build-manifest.json` 的 `baselineDefinition`，并由 `ci/script/materialize_m0_clean.sh` 逐项断言。遗留：候选 tag v1.12.2 领先本基线 19 个文件，重挂与否是 M1 前的独立决策，不改变"基线已可复现"这一事实 |
| M0-B02 | S0 | 仓库曾以固定口令从 assets 加载并跟踪签名密钥库 | 构建扫描确认 APK/assets 不含签名材料；本地生成密钥可用；旧密钥失效处置 | **构建侧解除，运行期未验证**。`verifyNoEmbeddedSigningMaterial` 现枚举全部模块 assets 根并展开一层归档，两次探针实测均能命中；APK 5,550 条目侧扫描干净。未做：真机确认每安装随机导出密钥可生成可用、旧 `filesDir` 残留被清 |
| M0-B03 | S1 | 两份干净目录构建和真机安装未执行 | Debug + clone 两次复现并记录安装包 SHA-256 | **构建侧解除，运行期未验证**。`C:/M0/A` 与 `C:/M0/B` 由同一基线物化，串行各跑一次 `assembleDebugClone`：15m28s / 15m57s 均 BUILD SUCCESSFUL，235 个任务全为本次执行，五个门禁无 UP-TO-DATE/FROM-CACHE、命令行无 `-x`。两份产物条目集合完全一致（5,550 对 5,550）、包名/版本/ABI/签名证书 SHA-256 一致，APK SHA-256 分别记录为不等（计划书原文即"不要求 APK 字节完全一致，分别记录哈希"，故不构成缺陷）；16 个 `.so` 的 1,752,396 个差异字节已逐字节定位到 4 个节（`.rodata` 1,565,344 / `.text` 180,250 / `.rela.dyn` 6,485 / build-id 317）并收敛为单一根因：绝对构建路径进 `.rodata` 后改变了串池排布，带动取址立即数与重定位 addend 位移；`.text` 的 93,739 个差异字解码后无一为分支目标，控制流与指令语义一致，无未解释差异，见 `native-build-path-embedding.txt`。真机安装仍未执行（无设备） |
| M0-B04 | S0 | 工具、插件、MCP、Workflow、HTTP/后台入口尚未在执行层关闭 | 从初始化、注册、回调、Intent、广播和后台路径逐项验证拒绝 | **未执行**。新增面：`:terminal` 设备侧 FTP 开关与 Ubuntu rootfs DocumentsProvider、`subpack` 的 APK/EXE 产出与签名，均须纳入禁用与验证清单。本轮补入的具体链路见 `execution-entrypoints.md` §3.9：随包 APK 解包后经无障碍代点系统安装确认（`ShizukuInstaller.kt` + `UIHierarchyManager.kt`）、"导出为独立应用"在设备内改写并重签名 APK（`ExportDialogs.kt` + `ApkEditor.kt`），对应真机验证行 E15–E17 |
| M0-B05 | S0 | Room/ObjectBox/向量/附件/缓存/日志/备份全路径保护未证明 | 合成标记扫描无明文 Key，正文和可复原派生数据纳入已验证保护 | **未执行**。公共 `Download/Operit` 仍写入插件/会话/工作区/导出；`allowBackup` 仍为 `true` |
| M0-B06 | S1 | 外部 ZIP/AAR/原生库和子模块许可/来源未闭环 | 完整依赖许可清单由负责人复核签字 | **来源侧收敛、义务侧未闭合**。内嵌分发物逐项实测后：Ubuntu rootfs 与 shizuku.apk 已证实与官方 release 资产逐字节一致；sherpa 7 项的许可其实是 Apache-2.0（随包的 `stt-model-assets.properties` 写成"未声明"，属错误声明文本）；`liboperit_ripgrep.so` 的源码就在仓库内且**已完成源码级重建**——逐字节不等的原因实测为工具链版本不同（rustc 1.88/clang 18 对 rustc 1.95/clang 21），功能面等价已证，同时查出该二进制内嵌 `C:\Users\12809\…` 共 106 处，即由身份不可核查的个人机器手建后经无校验 Drive 归档进入分发链（见 `ripgrep-source-rebuild.txt`）。仍未解除：`accessibility.apk` 上游整棵树无授权文本（该仓库只有 `main` 一个分支、无 tag、无 release，唯一的项目级许可信号是 README 一枚 shields.io 徽章；取证已完成，处置随 M1 载荷移除，不再以"向作者索文"为前置）、`desktop.apk` 全仓零引用且其 `version-control-info.textproto` 为 `NO_SUPPORTED_VCS_FOUND`（"无来源"由推测升为实测：打包时不在任何 git 工作树内）、`subpack/android.apk` 是 debug 签名且只来自无校验 Drive 归档、ffmpeg-kit AAR 的 GPL/LGPL 归属未定且无版本钉、`axml` 的 Apache-2.0 只存在于晚于被分发 2.0.0 tag 的 master 上（需作者追认或整链移除）。`THIRD-PARTY-NOTICES.md` **第五轮已成文**（由 `scripts/third_party_notices.py` 从四层实测 JSON 生成，17 份许可正文逐字附出并留 URL/字节/SHA-256 指纹于 `licence-texts/sources.json`，其第 2 节列出 15 项待处置），因此本条的"文本未成文"已解除，剩下的全是个别载荷的处置决定（依赖树许可的结论另立 M0-B08，无许可授予的构件另立 M0-B09）。成文过程新闭合/新查明两条：UnRAR 条款正文已取得（其第 2 条要求在本 fork 的文档与源码注释里写明"不得用于开发 RAR 兼容压缩器"，此句至今未写，属本仓库可自证项）；ObjectBox Binary Licence 的正文**取不到且通道本身不通**（POM 的 url 只到站点根，站点公开的 PDF 是其《Terms Of Use》而非产品条款，且该文件自身禁止再制内容）。上游取证轮已结掉一条：`libsu:6.0.0` 的 Apache-2.0 在钉住的 tag 上可证。另有一类新形态值得单记：**分发物与可核验来源之间存在未记录的本地改动**——`accessibility.apk` 的 textproto 记录 revision = `main` 头 `1221cf08…`，而该提交自带 `versionName "1.4"/versionCode 3`，随包却是 `1.5/4`，说明那次构建的工作树被改过且没提交（AGP 只记 commit、不证树干净）。明细见 `license-inventory.md` §第二轮复核、§第三轮复核、§第四轮复核末的补做段与 `upstream-license-retrieval.md` §8 |
| M0-B07 | S1 | 分发 APK 的原生库内嵌绝对构建路径，含构建者用户名 | 给全部 externalNativeBuild 模块加 `-fmacro-prefix-map`/`-fdebug-prefix-map`、Rust 侧加 `--remap-path-prefix`，复核 ggml 后端注册表路径常量，整改后全条目复扫归零 | **新发现，未整改；且当前分发物已经在带别人的身份**。两层：(1) 我们自己编的原生库——13 个随包 `.so` 共 669 处绝对检出根，来源是 `__FILE__` 断言/日志串、Clang `(lambda at …)` 诊断串与 ggml 路径常量；从默认长根出包即把负责人的 Windows 用户名与目录结构带进发给 5–10 位受邀测试者的 APK，短根干净构建同样 669 处（内容是 `C:/M0/A`），证明是结构性机制而非本机偶发。(2) 外部预编译产物——`liboperit_ripgrep.so` 内嵌 106 处 `C:\Users\12809\…`，这是**一个我们无法核查的人的用户名**，随包分发即替他外泄身份，同时反向证明该二进制是个人机器手建而非上游 CI 产物（见 `ripgrep-source-rebuild.txt` §4）。明细与复扫判据见 `native-build-path-embedding.txt` |
| M0-B08 | S1 | 依赖树里的 copyleft 载荷与随包"开源许可"界面的错误声明 | 负责人对 `itextg`/`jlatexmath`/`junrar`/`libsu` 逐项作出去除或替换的决策；`OpenSourceLicenses.kt` 的 8 处错误声明改正或以审核过的 `THIRD-PARTY-NOTICES` 替换 | **新发现，未整改**。依赖树层实测后（`gradle-dependency-licenses.md`、`native-dependency-licenses.md`）：396 个选中坐标里 copyleft 系 5 个，但**无可选弱许可的是 4 个**——`checker-compat-qual` 实为 GPL2-with-CE **OR** MIT 双许可（原判只读了 POM 的第一条 `<license>`）。最重的是 `com.itextpdf:itextg:5.5.10` —— POM 原文只有 `GNU Affero General Public License v3` 一条，直接声明于 `app/build.gradle.kts:744`，dex 锚定检索证实 `Lcom/itextpdf/text/` 1,808 个描述符确在分发物里；AGPL 第 13 条针对"通过网络访问该程序的用户"，而首版形态正是发给 5–10 位受邀测试者的安装包。其次是 `jlatexmath-android` 三件套（GPL-2.0，88 个描述符在包内）与 `junrar`（`UnRar License`，非开源许可，235 个描述符在包内）。原生侧反而干净：11 个 FetchContent 依赖的顶层许可全为 MIT/Apache-2.0/zlib；Node 层 92 包与 Rust 层 48 包 copyleft 均为 0。同时查出 `ui/features/about/screens/OpenSourceLicenses.kt`（硬编码 68 条）有 **8 条**与实测不符，其中把 AGPL-3.0 写成 `MPL/LGPL`、把 `UnRar License` 写成 `The Unlicense`、给 GPL-2.0 凭空加上不存在的 classpath exception、把 `RenderX` 写成 `MIT`（详见 M0-B09）——这是向测试者分发中的错误授权表述，与计划书 §10.2 禁止"用措辞掩盖未实现的保护"同源。该界面还整层缺条目（ripgrep 与 32 个 crate、terser 及其内嵌 source-map/acorn、92 个 npm 包、MathJax 与随包的字体条目——第五轮实测 APK 里字体条目共 83 个，见 `THIRD-PARTY-NOTICES.md` §8.1），故 `THIRD-PARTY-NOTICES` 只能由四层实测数据生成。**第五轮已按该口径成文**：`THIRD-PARTY-NOTICES.md` 由 `scripts/third_party_notices.py` 整体生成，明确不以 `OpenSourceLicenses.kt` 为原料，其 §1 记了"不引用它"的理由与 8 条错误的对账出处；因此本条解除条件的两个分支现在都有了对应物——要么改正那 8 条，要么把该界面换成渲染这份审核过的文件（两者的差别只在是否保留一个会漂移的硬编码副本）。严重性记 S1 而非 S0：缺口是授权文本与义务，不落在用户数据保护面上，但同样在 M1 前关闭 |
| M0-B09 | S1 | 分发物里有一个**完全没有许可授予**的构件，且界面把它写成 MIT | 移除 `com.github.tech-pw:RenderX:1.0.0`（`app/build.gradle.kts:755` + `gradle/libs.versions.toml:167` 各 1 行，零源码改动），或取得作者对该构件对应提交的书面再分发授权 | **上游取证轮新发现，未整改**。jitpack 构建日志把缓存里的 `RenderX-1.0.0.aar`（SHA-1 `d1d4c451eaf5a743…`，1,746,805 字节）定位到提交 `de7d1e33da264f99…`（`git describe` = `1.0.0-0-gde7d1e3`，恰在唯一 tag 上）；该提交根目录 14 个条目里无任何许可文件，13 种常见拼写（含 `renderX/LICENSE`、`app/LICENSE`）全部 404，`build.gradle.kts`/`jitpack.yaml`/`gradle.properties` 无 `license` 行，`readme.md` 里 MIT/Apache/GPL/AGPL/BSD/Unlicense 六词 0 命中。上游全仓 `LICENSE` 只有 1 次提交：`88423982e36cce63…`，2024-02-26，比该 tag 晚 3 天，内容是 **GPL-3.0**（35,149 字节）。即按"无授予即保留所有权利"我们没有再分发它的权利，按"repo 级 GPL 覆盖全史"它是 GPL-3.0——**两种解读下 `OpenSourceLicenses.kt:113` 的 "MIT" 都是错误声明**（上游从未出现任何 MIT 文本）。在包内的证据：`Llive/pw/render` 72 个描述符（classes15、classes41）、150 个 MathJax 资产 1,434,800 字节、AAR 的 12 个 `res/font` 条目全部进包（第五轮复核：其中 11 个是字体本体，`poppins_bold.xml` 只是 font-family 声明；同 AAR 的 `assets/MathJax/fonts/` 另带 22 个 TeX 派生字体，故该构件实际带进 33 个字体条目）且自身无授权文本。Kotlin/Java 侧引用实测为 0，唯一提及是该库自带清单里一个 exported MAIN/LAUNCHER activity `live.pw.renderX.LatexView`，由 `app/src/main/AndroidManifest.xml:112-114` 用 `tools:node="remove"` 删掉，而 dex 里 `Llive/pw/renderX/LatexView;` 命中 0（该类根本不存在）。因此这是全部许可类阻塞里代价最低的一条：删两行即同时消掉无许可构件、GPL 义务、150 个资产与 33 个字体条目，并消除一处合并清单卫生问题 |

## 已解除的伪阻塞

此前记为"外部依赖不可得"的四条结论（Drive 502、无 Rust 工具链、`libs.zip` 缺失、必须跳门禁）全部为误判，实测与根因记录在 `runtime-audit.md` 的修订表。**不得再以"依赖不可获取"作为 M0 停滞理由。**

许可侧同样有被实测推翻的记录：Ubuntu rootfs"来源不可验证"（实为 proot-distro v4.18.0 官方资产，逐字节一致）、sherpa 模型"许可未声明"（模型卡写明 Apache-2.0，错的只是仓库属性文件里的注解文本）、`liboperit_ripgrep.so`"许可随 ripgrep"（是仓库自带的 `operit_ripgrep` crate，依赖已钉版本）、`libsu:6.0.0`"许可无法确定"（jitpack 生成的 POM 不带 `<licenses>`，但上游 tag 上的 `LICENSE` 是 Apache-2.0，且自 2018-01-25 未改动）。这四条的共同点是原判定停在"看名字/看注释/看 POM 缺字段"，没有回到上游资产本身。

但**反向也发生了**，不能把上面那段读成"联网取证总是把缺口填平"：`RenderX:1.0.0` 原来记的是"本机无出处可证"，联网之后变成"该构件在对应提交上无任何许可授予"（M0-B09）。它们的解除不改变 B06 仍阻塞的结论——未闭合的已换成授权文本与义务，不是来源。

## 暂不进入的工作

- 不做深度 UI 换肤、语音、硬件、QQ/微信、NAS/AstrBot、云同步、插件市场和复杂 Agent。
- 不在 M0 未通过前开发 M2 及之后的隔离/记忆/性能功能，以免在错误基线继续堆功能。

## M1 准入条件

所有 M0-B01 至 M0-B09 关闭；M0-A01 至 M0-A06 有证据；无 S0/S1 未关闭问题；负责人签署 Fork/参考/暂停路线和支持设备范围，并签署 `docs/scope-freeze-v3.md`。

判据核对一处（防止后续会话自行收紧验收条件）：计划书 V2.0 第 7 页 M0-A01 的合格标准原文是"两次均成功；完整源码/依赖版本可追溯。**不要求 APK 字节完全一致，分别记录哈希**"，证据要求为"构建日志、版本清单、安装录像"。因此本轮观察到的两份 SHA-256 不等**不构成阻塞**，也不存在"判据不可达"的问题；我们额外做的逐字节归因是超出计划要求的补强，作用是回答"差异是否可能来自污染"，答案是已命名机制且无未解释部分。M0-A01 相对计划书的真实缺口只剩"真机安装 + 安装录像"一项。
