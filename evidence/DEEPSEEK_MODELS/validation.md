# DeepSeek 模型选择验收

日期：2026-10-01。版本：0.39.0 / code 40，schema 32，备份 v30。

## 原因与修改

- 原模型连接页只有手动名称输入，没有读取模型列表的入口；点击 DeepSeek 写入旧名称 `deepseek-chat`，再次点击还会清空 Key 草稿。
- `ConnectionModelPicker.kt` 提供 Flash / Pro 明确选择、手动 ID、服务端模型列表搜索与选择；页面进入时不改写已有配置。
- `ModelCatalogService.kt` 使用 GET 读取同一服务的 `/models`；`EndpointCompleter.modelsEndpoint` 保留 `/v1` 等 API 前缀。响应限制为 2 MiB / 5,000 个模型，校验 JSON、模型 ID 和可选名称；失败显示对应错误，不替换返回模型。
- `CompanionViewModel.availableModels` 仅在地址与已保存连接一致时读取原凭据；地址或 Key 修改后取消在途请求并清理旧列表。没有新增数据库或备份迁移。
- 官方当前模型 ID 与名称核对见[模型列表](https://api-docs.deepseek.com/zh-cn/api/list-models/)和[更新记录](https://api-docs.deepseek.com/updates/)。

## MuMu 实际检查

设备：MuMu / emulator-5554，Android 15。覆盖安装新版 Release 成功。

| 操作 | 实际结果 |
| --- | --- |
| 已保存连接打开 | 显示原 `deepseek-chat` 并提示选择新模型；服务地址保留，Key 不回显。见[修改前配置](picker-before.png)。 |
| Flash / Pro 芯片 | 分别填入 `deepseek-flash` 和 `deepseek-v4-pro`；再点当前 DeepSeek 芯片，选择和地址仍保留。 |
| 获取模型列表 | 使用应用内已保存 Key、原 `/v1/chat/completions` 地址，从服务端返回两个模型。显示 `DeepSeek-V4.1-Flash / deepseek-flash` 和 `DeepSeek-V4-Pro / deepseek-v4-pro`。见[实际列表](live-model-list.png)。 |
| 搜索与选择 | 搜索 `pro` 只显示 Pro，点击后模型 ID 为 `deepseek-v4-pro`。见[搜索结果](search-pro.png)。 |
| 保存与冷启动 | 模拟器原连接选择 Flash 并确认保存；冷启动后连接名与模型 ID 均为 `deepseek-flash`，Key 留空继续使用原凭据。 |
| 375dp 手机布局 | 750×1334 / 320dpi 下重新打开连接并读取真实模型列表；两条模型名称、ID 和关闭按钮均显示完整。见[手机列表](phone-model-list.png)。检查后恢复原尺寸、密度和自由旋转。 |

没有执行“测试已保存连接”或发送付费聊天请求，没有导出凭据。此次只确认模型发现、选择与配置保存，未验证 Flash / Pro 的聊天生成。应用操作正常；崩溃缓冲中有 MuMu 的 `uiautomator` 退出 SIGSEGV 记录，不属于应用进程，未发现本次操作导致的应用崩溃。

## 构建与交付

- `./ci/build.ps1 -Tasks ':app:assembleRelease'`：成功，5m37s；只有已有弃用 API 与恒真条件警告。
- APK v2 签名校验：通过；`zipalign -c -P 16 4`：通过。
- `.venv/Scripts/python.exe ci/script/verify_apk.py --stage DEEPSEEK_MODELS`：通过，见[包检查](apk-audit.json)。
- 交付：[单版 APK](../../output/local-companion-0.39.0.apk)，19,341,499 字节。
- SHA-256：`301538AD0EF29EBA080C48C0A4D1CB9B0A35123E74B4207D771DB8BB2BD1C363`。
- 更新 APK 和安装说明；历史源码包与 `delivery.json` 未重新生成。

本轮没有运行单元测试、Android 仪器测试或 Android 真机检查。鉴权、服务不存在、格式错误等分支仅做代码核对，未通过模拟服务逐项执行。
