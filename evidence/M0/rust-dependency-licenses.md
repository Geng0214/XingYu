M0-A05 依赖许可第三层：`liboperit_ripgrep.so` 的 Rust 依赖（Cargo.lock 48 包）

测量日期：2026-09-19
配合 `gradle-dependency-licenses.md`（JVM 层）与 `native-dependency-licenses.md`（CMake FetchContent 与 JS 资产层）阅读。这一层此前在两份文件里都被列为"未覆盖"，本轮补齐。机器可读全表：`rust-crate-licenses.json`（48 条，含每个构件的全部许可文件名、SHA-256 前 16 位、字节数与文本类别）。

## 1. 方法与可复现命令

```bash
export CARGO_HOME='C:/Users/15942/dev/rust/cargo'
export RUSTUP_HOME='C:/Users/15942/dev/rust/rustup'
cd C:/Users/15942/Desktop/Work/operit/tools/native_ripgrep
cargo tree --offline --locked --target aarch64-linux-android -e normal
cargo tree --offline --locked --target aarch64-linux-android -e normal,build
```

两条前提，都是本轮实测出来的，换机器时会踩：

- `CARGO_HOME` 必须显式指到 `C:/Users/15942/dev/rust/cargo`。默认位置 `C:/Users/15942/.cargo` 里只有一个空的 `registry/CACHEDIR.TAG`，用默认值跑 `--offline` 会报 `no matching package named grep-matcher found`，看起来像依赖缺失，实际是找错了注册表。
- rustup 没配默认工具链（`rustup could not choose a version of cargo to run … no default is configured`），所以要么 `rustup default`，要么直接调用 `C:/Users/15942/dev/rust/rustup/toolchains/1.88.0-x86_64-pc-windows-msvc/bin/cargo.exe`。本轮用后者，与 `ripgrep-source-rebuild.txt` 那次源码重建同一工具链（rustc 1.88.0）。

许可取值口径与 JVM 层一致：只读发布产物，不读上游仓库工作区。`declared` 取 `registry/src/index.crates.io-…/<crate>-<ver>/Cargo.toml` 的 `license` 字段（crates.io 发布时规范化的值）；许可文本取该构件目录内文件名以 `license|licence|copying|copyright|notice|unlicense` 开头的全部文件（跳过 `target/`、`.git`、`tests/`、`examples/`、`benches/`、`fixtures/`、`test_data/`），逐个按首 1200 字节判类别并记 SHA-256 前 16 位。

## 2. 计数与角色划分

`Cargo.lock` 有 48 个 `[[package]]`，但对 `aarch64-linux-android` 这个目标，真正参与运行期链接的只有一部分：

| 角色 | 数 | 判定依据 |
|---|---|---|
| Android 运行期库（`-e normal` 里出现且非 proc-macro） | **32** | 代码可能进入 `.so` |
| 构建期 proc-macro | 4 | `jni-sys-macros 0.4.1`、`proc-macro2 1.0.106`、`serde_derive 1.0.228`、`thiserror-impl 1.0.69` —— 在宿主上展开宏，不进产物 |
| 宿主构建专用 | 3 | `winapi-util 0.1.11`、`windows-link 0.2.1`、`windows-sys 0.61.2`：只在 `:normal,build` 集合里，来自 `jni` 的 build-dependency `walkdir`，为宿主 Windows 解析 |
| 锁定但本目标从未解析 | 9 | `windows-sys 0.45.0`、`windows-targets 0.42.2` 与 7 个 `windows_* 0.42.2` 架构分包 —— 在锁文件里占位，`cargo tree` 两个集合都不含，注册表里 6 个连源码都没下载 |
| 实测在随包 `.so` 里留下 `<crate>-<ver>` 源码路径的 | 13 | 见第 4 节 |
| copyleft | **0** | 48 个包的许可文本没有任何一份是 GPL/LGPL/AGPL/MPL/EPL |
| 本机查无许可文本 | 8 | `operit_ripgrep`（我们自己的 crate）、`jni-sys-macros`、6 个未下载的 `windows_*`；见第 3 节 |

按许可文本类别聚合（一个包可有多份文本）：`Apache-2.0 | MIT` 25、`DUAL-POINTER(Unlicense OR MIT) | MIT | Unlicense` 7、`MIT | Unlicense` 2、`MIT` 3、`Apache-2.0 | MIT | Unicode` 2、`Apache-2.0 | BSD-family` 1（`cesu8` 的 `COPYRIGHT-RUST.txt`，即 Rust 项目那份 Apache + BSD 双清单）、无文本 8。

## 3. 这一层真正要处置的两件事

1. **`operit_ripgrep 0.1.0` 自己没有任何授权标记。** `tools/native_ripgrep/Cargo.toml` 无 `license` 字段，目录内只有 `Cargo.lock`、`Cargo.toml`、两个构建脚本、`src/`，没有 LICENSE 文件。也就是说这个 crate 的授权只能从仓库根 `LICENSE`（LGPL-3.0-only）推定，而它恰恰是整个 APK 里"我们自己写的、且必须提供对应源码"的那部分。修法是一行 `license` 字段加一份与根 LICENSE 一致的说明，属低成本、应在 M1 出包前完成。
2. **`unicode-ident 1.0.24` 的声明是复合的**：`(MIT OR Apache-2.0) AND Unicode-3.0`。它的目录里除 `LICENSE-APACHE`、`LICENSE-MIT` 外还有 `LICENSE-UNICODE`（1,995 字节，实测判为 Unicode 文本，内含 MIT 段落）；`regex-syntax 0.4.14/0.8.11` 一线另有嵌套的 `src/unicode_tables/LICENSE-UNICODE`（2,847 字节）。若把 Unicode 数据表按"只有 MIT/Apache"来汇总就是错的，`THIRD-PARTY-NOTICES` 里要单独保留 Unicode 条款原文。

除此之外，本机无许可文本的另外 7 个包（`jni-sys-macros` 与 6 个未下载的 `windows_*`）对本产物无义务影响：前者只在宿主展开宏、不进 `.so`；后者在本目标下从未被解析，也就没有被编译进分发物。

## 4. "哪些 crate 真的在二进制里"的正向证据

这一层的旧缺口是"只有锁文件，没有链入证明"。本轮用两条独立证据交叉：

- `cargo tree --target aarch64-linux-android -e normal`：36 个包（含 4 个 proc-macro）在本目标下被解析为依赖，其余 12 个 Windows 系包不在其中。
- 在随包 `app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so`（3,599,272 字节）里搜 `<registry>/<hash>/<crate>-<ver>/` 形态的源码路径，命中 91 处，覆盖 13 个 crate：`regex-automata-0.4.14` 43、`aho-corasick-1.1.4` 15、`regex-syntax-0.8.11` 9、`ignore-0.4.27` 5、`grep-regex-0.1.14` 4、`globset-0.4.18` 3、`jni-0.21.1` 3、`memchr-2.8.2` 3、`bstr-1.12.3` 2、`cesu8-1.1.0` 1、`itoa-1.0.18` 1、`same-file-1.0.6` 1、`walkdir-2.5.0` 1。

**这两个方向的逻辑强度不同，不能混着说**：路径命中是"该 crate 确有代码进来"的正证据；未命中不是"没链入"的反证据（只有源文件里出现 `file!()`/panic 串才会留路径）。所以本轮的结论止步于"13 个 crate 已证实进码，其余 19 个运行期 crate 只有依赖图层面的证据"。

## 5. 与 M0-B07 的相互影响

上面那 91 处路径同时就是 `decision.md` M0-B07 记的构建者身份泄漏本体（`C:\Users\12809\.cargo\registry\src\…`）。加了 `--remap-path-prefix` 之后这条证据通道会一起消失——那不是问题，但要在记录里写明：**整改后不能再指望用二进制内嵌路径来证明链入面**，届时的链入证据只能是构建图（`cargo tree` + `cargo build --unit-graph`）或 `-Cdebuginfo=0` 下的符号命名统计。整改验收判据（`native-build-path-embedding.txt` §2 的"命中数归零"）与本节共用同一个扫描脚本，这一点是有意为之。

## 6. 本轮未覆盖

- 逐 crate 的 feature 组合与实际编入的源文件清单未证（本节只到依赖图 + 13 个正向路径命中）。
- `cargo build --unit-graph` 未跑；它是整改后替代路径证据的现成手段。
- ~~Node 层（`web-chat/`、根 `package-lock.json`、`pnpm-lock.yaml`）仍未展开~~ **已完成**，见 `node-dependency-licenses.md`：随包的是 `assets/bridge/*.js` 两支 ncc bundle，其 `pnpm-lock.yaml` 运行期闭包 92 包（MIT 82 / ISC 7 / BSD-3 2 / BSD-2 1，copyleft 0）。`:app:syncMainAssets` 那条疑问也已实测否掉——web-chat 的构建结果不进 APK（`app/build.gradle.kts` 对它 0 引用，两份干净构建与 APK 内 0 命中），只有手工脚本会产出它。
- 与本层同风格的一条待办：`operit_ripgrep` 自身缺 `license` 字段与 LICENSE 文件（§3 第 1 条）。另一条同类待办——`accessibility.apk`/`desktop.apk` 的上游取证——已完成，见 `upstream-license-retrieval.md` §8。
