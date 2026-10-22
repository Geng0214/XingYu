M0-A05 依赖许可第二层：原生 FetchContent、随包脚本资产

测量日期：2026-09-19
配合 `gradle-dependency-licenses.md`（JVM 依赖树）阅读。本节覆盖"我们自己从源码编出来的那部分第三方"和"以脚本/资源形态随包的第三方"。

## 1. CMake FetchContent 原生依赖（11 个，全部 commit 钉死）

来源目录为 `C:/M0/A` 那次干净构建的实际下载结果，目录名即 `<name>-<40 位 commit>-src`，因此每个依赖的版本是构建时实测、不是抄文档。许可取每个源码树的顶层许可文件原文，按首 800 字节指纹归类；机器可读结果（含深度 ≤3 的全部嵌套许可文件与其 SHA-256 前 16 位）见 `native-dependency-licenses.json`。

| 依赖 | 钉死 commit | 顶层许可文件 | 许可指纹 | 文本 SHA-256（前 16） | 字节 |
|---|---|---|---|---|---|
| `ncnn` | `70e7bfd6081b` | `LICENSE.txt` | zlib | `7c974bac98848df4` | 6,427 |
| `sherpa_ncnn` | `c61e50d61e9f` | `LICENSE` | Apache-2.0 | `cfc7749b96f63bd3` | 11,358 |
| `wamr` | `4211c313bfac` | `LICENSE` | Apache-2.0 | `0a274df3755e61b1` | 12,242 |
| `dragonbones_cpp` | `f696143045e0` | `LICENSE` | MIT | `e055124edff26897` | 1,111 |
| `ufbx` | `fcc5d6ba444c` | `LICENSE` | MIT | `0dd48ebadf52273c` | 2,631 |
| `bullet3` | `63c4d67e3370` | `LICENSE.txt` | zlib | `885cd516a8420acb` | 1,022 |
| `saba` | `29b8efa8b31c` | `LICENCE` | MIT | `11a52e2589953f2a` | 1,083 |
| `llama_cpp` | `720d7fa4097f` | `LICENSE` | MIT | `94f29bbed6a22c35` | 1,078 |
| `mnn` | `d407447ed56c` | `LICENSE.txt` | Apache-2.0 | `a830d59c4f98d110` | 11,346 |
| `quickjs` | `04be24600159` | `LICENSE` | MIT | `598fd7fc928e4350` | 1,130 |
| `kleidiai` | `fa0a8e861d58` | **无顶层许可文件** | 见下 | — | — |

结论：这一层没有任何 copyleft 义务——11 个顶层许可全是 MIT / Apache-2.0 / zlib 家族。`itextg` 那类问题不出在这里。

`kleidiai` 单独说明，因为它看着像缺文件、其实不是：它用 REUSE 规范授权。`README.md` 第 176–178 行写 "KleidiAI is distributed under the software licenses in LICENSES directory"，目录里是 `LICENSES/Apache-2.0.txt` 与 `LICENSES/BSD-3-Clause.txt`，全树 `SPDX-License-Identifier` 统计为 **550 个 Apache-2.0 + 1 个 BSD-3-Clause**，逐文件头带 `// SPDX-License-Identifier: Apache-2.0`（例：`kai/kai_common.h:4`）。第三方 zip 侧车（`third_party/googletest-v1.17.0.zip.license`）写 `NOASSERTION` 版权人 + `BSD-3-Clause`。所以它的义务是完整的，只是扫描脚本按"顶层 LICENSE 文件名"找不到。

## 2. 嵌套许可（构建时同树下载，但是否链入未逐条证明）

深度 ≤3 的嵌套许可文件数：`bullet3` 45、`saba` 16、`mnn` 7、`wamr` 4、`llama_cpp` 5、其余各 1。要点：

- `llama_cpp`：`vendor/cpp-httplib/LICENSE`（MIT）、`licenses/LICENSE-jsonhpp`（MIT，即 `.rodata` 里那条 `nlohmann/json.hpp` 路径的来路）、`gguf-py/LICENSE`（MIT）。
- `mnn`：`3rd_party/protobuf/LICENSE`（BSD 家族）、`3rd_party/flatbuffers/LICENSE.txt`（Apache-2.0）、`3rd_party/half/LICENSE.txt`（MIT）、`3rd_party/rapidjson/license.txt`（指纹未识别，需人工读）、以及 `apps/frameworks/sherpa-mnn/`（内含 Apache-2.0 + NOTICE，与 `sherpa_ncnn` 顶层文本同为 `cfc7749b96f63bd3`，说明是同一份上游文本的重复拷贝）。
- `bullet3` 的嵌套许可绝大多数在 `data/`（示例网格资产，含一份 CC 与若干 UNFINGERPRINTED）和 `examples/ThirdPartyLibs/`（imgui、glad、lua、tinyxml2、openvr、enet 等）。
- `saba` 在 `license/` 下自带 glm、gli、glslang、googletest、imgui、json、lua、sol2、spdlog、tinyobjloader、glfw、fcpp、bullet 的许可文本，等于替我们做了部分汇总工作。

**这里有一个不能含糊的边界**：本轮没有按 CMake target 的源文件列表逐条证明"哪些嵌套库真的被编进了 `.so`"。从路径看 `data/`、`examples/`、`tests/` 不参与 `:mmd`/`:mnn` 的库目标，但这是推断，不是实测。若将来生成 `THIRD-PARTY-NOTICES`，要么按 target 源列表收敛后再列，要么按"上游源码树完整声明"保守全列——两种口径都要在文件里写明用了哪一种。

## 3. 随包 JS 资产（`app/src/main/assets/js`，1.5 MB）

本节只管 `assets/js` 这一组（WebView 侧脚本与桥接壳）。另一组 `assets/bridge/*.js`（1,378,177 字节，MCP 的 Node 载荷）在首轮只被算进"随包 JS"的体积，其依赖闭包、许可文本与链入证明已于同轮单独展开，见 `node-dependency-licenses.md`。

| 文件 | 字节 | 性质 | 许可文本 |
|---|---|---|---|
| `terser.bundle.min.js` | 1,116,796 | 第三方打包产物 | `terser.LICENSE`（Mihai Bazon 2-clause BSD 风格） |
| `katex.min.js` | 270,802 | 第三方打包产物 | `katex.LICENSE.txt`（MIT，Khan Academy 2013–2020） |
| `AndroidUtils.js`、`UINode.js`、`OkHttp3.js`、`CryptoJS.js`、`Jimp.js`、`pako.js` | 34,384 / 29,774 / 10,424 / 4,288 / 2,248 / 1,476 | **Operit 自写的 native bridge 壳** | 不产生第三方义务 |

后六个文件的名字容易被误判成第三方库：它们的文件头自己写明是桥接壳（`CryptoJS.js`："Native Bridge for CryptoJS … delegates the actual cryptographic operations to … the app's JsEngine"；`pako.js`："This is a native-bridged implementation"；`Jimp.js`："A native-bridged implementation mimicking the Jimp API"）。登记在此是为了避免下一轮把它们当成缺失许可的第三方依赖重复追查。

**`terser.bundle.min.js` 有一个实际缺口**：这个 bundle 内含 `@jridgewell/source-map`（文件中 `jridgewell` 出现 5 次）与 `acorn`（3 次）的代码，而随包的 `terser.LICENSE` 只有 Bazon 一条版权声明，未覆盖这两个 MIT 模块的署名义务。修法是把 bundle 内所有模块的许可文本补进 sidecar，或换成带完整 `LICENSE` 列表的官方 terser 发布物。

## 4. 本轮未覆盖

- ~~`tools/native_ripgrep/Cargo.lock` 的 48 个 crate（Rust 侧许可聚合）~~ **已在同轮完成**，见 `rust-dependency-licenses.md`：48 包按 `--target aarch64-linux-android` 分为 32 运行期 / 4 构建期宏 / 3 宿主构建专用 / 9 从未解析，copyleft 计数 0。
- ~~`web-chat/` 与仓库根 `package-lock.json`、`pnpm-lock.yaml`、`package.json`（Node 层）。注意 `:app:syncMainAssets` 是否把 web-chat 构建结果打进 assets 未在本节确认。~~ **已在同轮完成**，见 `node-dependency-licenses.md`：`assets/bridge` 两支里只有 `spawn-helper.js` 走 `@modelcontextprotocol/sdk 1.30.0`，其 `pnpm-lock.yaml` 闭包 92 包（MIT 82 / ISC 7 / BSD-3 2 / BSD-2 1，copyleft 0，92/92 与锁文件完整性一致）；web-chat 那条疑问已实测否掉——`app/build.gradle.kts` 对 `web-chat` 0 引用、`app/src/main/assets/web-chat/` 被 `.gitignore:74` 忽略、两份干净构建与 APK（5,550 条目）内 0 命中，只有手工脚本 `npm run build:webchat` 会产出它。
- `subpack/android.apk` 内 `flutter_assets/NOTICES.Z`（143 段 BSD-3 / 278 段 Apache-2.0 / 83 段 MIT）的逐条归属——该载荷已按负责人裁决建议整体移除，移除后此层消失。
- ~~Gradle/Maven 依赖树里那 5 个"无出处"构件的上游 LICENSE 取证~~ **已完成**，见 `upstream-license-retrieval.md`：libsu 在钉住的 tag 上证实 Apache-2.0；axml 的 Apache-2.0 只存在于晚于 2.0.0 的 master；RenderX 分发的构件无任何许可授予（上游唯一许可是 3 天后加的 GPL-3.0）。`accessibility.apk`、`desktop.apk` 两项也已在该文件 §8 取证完毕（前者整棵树零授权文本、后者构建时不在 git 工作树内），两者均随 M1 载荷移除处置。
- 每构件"是否真的链入产物"的逐条证明（第 2 节末尾的边界同样适用于第 4 节的 JVM 层）。
