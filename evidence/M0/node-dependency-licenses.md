M0-A05 依赖许可第四层：随包分发的 Node/JS（`assets/bridge/*`，1,378,177 字节）与其 npm 闭包

测量日期：2026-09-19
配合 `gradle-dependency-licenses.md`（JVM 层）、`native-dependency-licenses.md`（CMake FetchContent 与 `assets/js` 壳层）、`rust-dependency-licenses.md`（Rust 层）阅读。Node 层在前三份文件里一直被列为"未覆盖"，本轮补齐。机器可读全表：`node-dependency-licenses.json`（92 个包的声明许可、上游 LICENSE 文件名/字节数/SHA-256 前 16 位/文本类别/版权行、registry 完整性与锁文件比对结果、仓库与两个 bundle 的度量）。

## 1. 这一层分发的是什么

`app/src/main/assets/bridge/` 两个文件，都是 ncc 打包产物，都实测进了 APK：

| 文件 | 字节 | SHA-256 前 16 位 | APK 内条目 | 与仓库副本 |
|---|---|---|---|---|
| `assets/bridge/index.js` | 57,268 | `6425a33ac581307a` | `C:/M0/A`、`C:/M0/B` 两份干净构建各有 1 条 | 逐字节相同 |
| `assets/bridge/spawn-helper.js` | 1,320,909 | `03f0b790b7c6f99d` | 同上 | 逐字节相同 |

运行期由 `app/src/main/java/com/ai/assistance/operit/data/mcp/plugins/MCPBridge.kt:213/220` 从 assets 打开、`:215/222` 写到 `OperitPaths.bridgeDir()`、`:264` 一并列进复制清单，最终 `:361` 以 `cd ~/bridge && node index.js <port>` 在 Termux 里用 Node 执行。所以这不是"前端静态资源"，是随包分发并在设备上执行的 JS 载荷 —— 与 M1 要关闭的 MCP 能力同一条链，许可面和执行面要一起处置。

产生方式：`tools/mcp_bridge/package.json` 的 `build` = `tsc && ncc build dist/index.js -o bundle && ncc build dist/spawn-helper.js -o bundle-helper` 再 `copy`。关键事实是 `copy` 只复制 `bundle/index.js` 与 `bundle-helper/index.js` 两个文件，而 `tools/mcp_bridge/.gitignore` 内容为 `dist`、`bundle`、`bundle-helper` 三行 —— 唯一进仓库、进 APK 的就是这两个压缩产物，打包器写在 `bundle/` 里的任何东西（含 ncc 的许可 sidecar、含 source map）既不留存也不分发。

## 2. 方法与可复现命令

- **版本闭包**：解析仓库根 `pnpm-lock.yaml`（`lockfileVersion: '9.0'`，`packages:` 190 条、`snapshots:` 190 条），从 `importers['tools/mcp_bridge'].dependencies` 的 `@modelcontextprotocol/sdk@1.30.0(zod@3.25.76)` 出发，沿 `snapshots` 的 `dependencies`/`optionalDependencies` 做 BFS，得到 **92 个包、0 个未解析**。同一条目的 devDependencies 钉住 `@types/node 18.19.130`、`@vercel/ncc 0.38.4`、`ts-node 10.9.2`、`typescript 5.9.3`（构建期，见第 5 节的例外）。
- **为什么必须联网**：本机没有 `node_modules`，也没有 pnpm store（`~/Library/pnpm/store`、`~/pnpm-store`、`~/.pnpm-store`、`%LOCALAPPDATA%/pnpm/cache` 四个候选路径全部不存在），许可文本无法从机器上取到。这一点与 JVM/Rust 层"读缓存 POM / 读注册表解包目录"的离线口径不同，本轮明确改走网络取证。
- **联网取证**：经本机代理 `http://127.0.0.1:7897` 请求 `https://registry.npmjs.org/<name>/<version>` 取元数据，再取 `dist.tarball`。每包先比对 **registry 的 `dist.integrity` 与锁文件钉住的 integrity**（同一 `sha512-…` 编码、同样截到 16 字符）：**92/92 相同**，即读到的许可文本对应的正是锁文件钉住的那份内容。解包只接受 `package/` 前缀、拒绝含 `..` 的成员、`filter='data'`。
- **许可文本判定**：取 tarball 根目录文件名以 `LICENSE|LICENCE|COPYING|COPYRIGHT|UNLICENSE|NOTICE` 开头的文件（本轮 92 包全部恰好 1 份），按全文判类别并记 SHA-256 前 16 位。分类器第一版把 `fast-uri` 判成 BSD-2 系，原因是它按规范 BSD-3 的 "Neither the name of the copyright holder…" 句式匹配，而 fast-uri 的第三款写作 "The names of any contributors may not be used to endorse or promote…"；补上"endorse 子句"这一路后重判为 BSD-3-Clause，与它的声明字段一致。**这类误判是分类器的缺陷，不是上游声明与文本冲突**，记录在此是为了说明"文本类别"这一列的可信度需要连同判据一起读。

## 3. 92 个包的许可结论

| 项 | 实测 |
|---|---|
| 声明许可字段 | MIT 82、ISC 7、BSD-3-Clause 2、BSD-2-Clause 1 |
| 声明字段与随包文本一致 | **92/92**（含修正后的 `fast-uri`） |
| 随包许可文本 | 每包 1 份，合计 92 份，去重后 **72 份不同文本**（最大重复组 7 份共用同一份 1,071 字节的 MIT 文本） |
| copyleft | **0**（无 GPL/LGPL/AGPL/MPL/EPL/CDDL） |
| `deprecated` 标记 | 0 |
| 有 `repository` 字段 | 92/92 |
| registry 完整性 = 锁文件钉值 | 92/92 |

也就是说：这一层没有传染性许可，义务是"逐字保留版权声明与许可文本"那一类。要单独提醒成文的是 `fast-uri 3.1.4`（1,813 字节）—— 它的 LICENSE 顶部有**两条版权行**（`2011-2021, Gary Court until https://github.com/garycourt/uri-js/commit/a1acf730b4bba3f1…`、`2021-present The Fastify team`），且第三条款是改写过的非标准 BSD-3 表述；`THIRD-PARTY-NOTICES` 必须从取回的 `package/LICENSE` 原文复制，不能由 SPDX 缩写"BSD-3-Clause"代写。JSON 里那份版权行为符合本目录哈希截断约定，把 40 位提交号截到了 16 位，成文时不要从 JSON 复制。

## 4. "哪些包真的在 bundle 里"：正向证据比 Rust 层弱

ncc 输出是压缩产物，许可归属的常规线索全被抹掉了。实测两个 bundle：`Copyright` 出现 **0** 次、`/*!` 横幅 **0** 次、单词 `MIT` **0** 次、`node_modules/` 路径 token 0 次（`spawn-helper.js` 里 2 次，且都是 cross-spawn 自己的 `isCmdShimRegExp` 正则源码，不是构建路径）。也就是说 Rust 层用的"内嵌路径即正证据"这条通道在这里基本不存在。可用的正向证据只剩字符串字面量（压缩会改名，但不会改字符串），本轮对 26 个包各取 1–3 个特征串检索：

- `spawn-helper.js`：**10 个包正向命中** —— `@modelcontextprotocol/sdk 1.30.0`（`notifications/initialized`、`MethodNotFound`、`McpError`）、`ajv 8.20.0`（`https://raw.githubusercontent.com/ajv-validator/ajv/master/lib/refs/data.json`，2 处）、`ajv-formats 3.0.1`（`formatMinimum`/`formatMaximum`）、`zod 3.25.76`（`ZodError`、`invalid_type`，另含其本地化消息串如 `kokonaisluvun`）、`zod-to-json-schema 3.25.2`、`cross-spawn 7.0.6`、`shebang-command 2.0.0`、`shebang-regex 3.0.0`、`which 2.0.2`、`json-schema-typed 8.0.2`。
- `index.js`：**0 个命中**。这不是异常：`tools/mcp_bridge/index.ts` 的 import 只有 `net`、`child_process`、`crypto`、`path`、`os` 五个 Node 内建模块，SDK 只在 `spawn-helper.ts` 里被引入（`@modelcontextprotocol/sdk/client/index.js`、`client/stdio.js`）。92 包闭包全部来自 `spawn-helper.js` 这一支。
- 反向探针：`ws` 的特征串（`Sec-WebSocket-Accept`、`WebSocketServer`、`permessage-deflate`）在两个 bundle 里全部不存在 —— 与"`ws` 不在 SDK 1.30.0 的闭包里"一致，顺带否掉早先按子串粗数得到的"`ws` 命中 87 次"那种读法（子串噪声，不可用）。

**仍未证实的部分要说清**：另外 82 个包没有特征串命中，而"未命中"不等于"未链入"（只有以字符串字面量出现过的才可见）。这一层能给出的最强表述是：92 个包被锁文件钉住、其中 10 个已证实在分发物内、其余只到依赖图层面。另一条本可用来收敛的通道是 source map —— `spawn-helper.js` 里留着 **76 处** `//# sourceMappingURL=`（64 个不同目标，如 `ucs2length.js.map`、`ref_error.js.map`、`boolSchema.js.map`、`ajv-provider.js.map`、`zod-compat.js.map`），`index.js` 1 处；这些 basename 已经能把 ajv/ajv-formats/SDK/zod 的内部模块点名出来，但 **APK 里 `.map` 条目为 0**（`assets/web-chat` 同样为 0），map 里本该有的 `sources:[…/node_modules/<pkg>/…]` 全清单随 `bundle/` 一起被丢弃了。

## 5. 构建期工具也往产物里放了代码，且它自己不出现在依赖清单里

两个 bundle 都含 `__webpack_modules__`（各 2 次）与 `__webpack_module_cache__`（各 4 次），即 `@vercel/ncc 0.38.4` 注入的 webpack 运行时随包分发。ncc 在 registry 上声明 MIT，但它的包元数据 `dependencies` 是**空的** —— ncc 把依赖打进了自己的 `dist/`。因此"从 npm 元数据自动生成 `THIRD-PARTY-NOTICES`"这条看似省事的路子会漏掉注入到产物里的这段第三方代码。这一条例外要在成文时手工补，或改用能读 bundle 内 map 的生成方式。

## 6. 仓内自述许可互相矛盾

| 位置 | 自述 | 支撑文本 |
|---|---|---|
| 仓库根 `LICENSE` | LGPL-3.0-only（实测文本头为 "GNU LESSER GENERAL PUBLIC LICENSE Version 3"） | 有 |
| 根 `package.json` | `"license": "ISC"` | 无 |
| `tools/mcp_bridge/package.json` | `"license": "MIT"`，且 `"author": ""` | `tools/` 下无任何 LICENSE 文件 |
| `web-chat/package.json` | 不写 `license`（`private: true`） | `web-chat/` 下无 LICENSE 文件 |

三个 JS 清单里两个自称 ISC/MIT、一个不写，而仓库整体是 LGPL-3.0-only，且 `tools/`、`web-chat/` 下没有一份许可文本。结论：**这几个字段的授权归属无法从仓内确定**，它们既可能是上游模板残留，也可能是有意的再授权声明 —— 后者不成立（LGPL 作品的组成部分不能由下游单方面改声明成 MIT/ISC）。这与 Rust 层 `operit_ripgrep` 无 `license` 字段是同一族问题，一并进 M0-B08 的处置清单。

## 7. 远端加载的第三方代码（随包但当前无加载者）

`app/src/main/assets/web/echarts_template.html`（2,709 字节）是**整个 APK 里唯一**带远端 `<script src>` 的 HTML——这一条本轮直接对产物重测：把 `C:/M0/A` 产物 5,550 条目里 4 个 `.html`（另三个是 `assets/templates/flutter/web/index.html`、`assets/templates/web/index.html` 与 itextg 自带的 `com/itextpdf/text/pdf/fonts/mustRead.html`）逐个取出扫 `<script[^>]*src="http…"`，只有它命中。URL 是 `https://cdn.jsdelivr.net/npm/echarts@5.3.3/dist/echarts.min.js`，且**没有 `integrity`、没有 `crossorigin` 属性**（实测各 0 次）。

**上一版这里写过一句错话需要订正**：原文说"因为不进 APK，本节不产生第三方声明义务"。实测相反——这个模板**就在 APK 里**（条目 `assets/web/echarts_template.html`，2,709 字节，两次干净构建一致）。结论因此要分两半说：

- 分发义务：仍然没有。ECharts 5.3.3 本身（Apache-2.0）不在包里，包里的只是一个指向 CDN 的 URL 字符串；我们不再分发它的代码，所以 `THIRD-PARTY-NOTICES` 不需要 ECharts 条目。
- 风险面**升级而不是取消**：这是一条**随包分发、零完整性校验的远端代码加载路径**。本轮实测它的加载者为 0——全仓（除 `evidence/` 与本文件）对 `echarts` 大小写敏感检索只命中模板自身，Kotlin/Java 无任何 `assets/web/` 或该文件名的引用（`PerformanceChart*` 那几条是我第一次不区分大小写检索时的假阳性，"p**erChart**s" 里含 "echarts"，已排除）。所以今天它不出网；但任何后续改动只要加上一行 `WebView.loadUrl("file:///android_asset/web/echarts_template.html")`，就会把一段无 SRI、无 `crossorigin` 的第三方 JS 变成运行期执行路径，而依赖树里没有任何东西钉住那个 URL 的内容。

处置：列入 M0-A03 真机出网清单（断言当前不产生命中该 URL 的请求）与 M1 整改范围——要么把 ECharts 随包并补 Apache-2.0 声明，要么删掉这个孤儿模板；不保留"URL 固定版本号即视为可信"的状态。图形可视化本身已按 V3 降级到阶段 2。

## 8. 本轮未覆盖（不要把这些当成已完成）

- `web-chat/` 的依赖闭包未测（它有自己的 `web-chat/package-lock.json`）。本轮已确认它**当前不进分发**：`app/src/main/assets/web-chat/` 被 `.gitignore:74` 忽略，工作树与两份干净构建里都不存在该目录，`app/build.gradle.kts` 全文对 `web-chat` 0 引用，APK 5,550 条目里 `assets/web-chat*` 0 命中，只有手工脚本 `npm run build:webchat` 会产出它。**若 M1 之后要真正启用 Web Chat，这一层必须先按本节同一方法补测**（React/Vite 闭包比 92 大得多）。
- 92 包只看了发布 tarball 根目录的许可文本，`examples/`、`tests/`、`data/` 下的嵌套许可文本未逐条展开（Rust 层同样的边界）。
- 逐包"是否真被链入"未收敛（第 4 节只给出 10 个正例）。要真正做到逐包，需要在能装 `node_modules` 的环境里重跑 `pnpm build` 并保留 source map，或用 `--stats` 输出模块清单。
- `tools/mcp_bridge` 的构建期 devDeps（`typescript 5.9.3`、`ts-node 10.9.2`、`@types/node 18.19.130`）未展开闭包；除 ncc 注入运行时（第 5 节）外，它们的代码不进产物。
- `THIRD-PARTY-NOTICES` 文本仍未成文 —— 四层原料（JVM 396 坐标、原生 11 个 FetchContent、Rust 48 包、Node 92 包）现在都已实测导出，缺的是合并成文、随包位置与 UI 呈现。
