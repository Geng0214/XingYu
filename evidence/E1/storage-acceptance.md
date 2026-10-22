# E1 第二段：加密存储、授权与迁移

日期：2026-09-20。状态：实现及宿主验证完成，Android 运行期与 E1 端到端未完成。开发版本 0.5.0/code 5；未替换现有 0.4.0 安装包。

## 数据与行为

- Room schema 4→5 显式事务迁移只新增 mcp_extensions、extension_tools、role_tool_grants、request_tool_permissions、tool_invocations。旧角色、会话、消息、模型配置、图片、收藏及置顶原样保留。现有 SQLCipher/Keystore 打开路径不变。
- 迁移前检查旧会话、消息、背景、头像与空间归属；不完整时直接报错，不清库、不切明文。schema 1/2/3 继续沿原迁移链到 5。
- 扩展配置只含凭据引用；保存非空引用时必须能以该完整 HTTPS 地址解密凭据。服务默认停用，工具列表刷新后需要重新授权；修改服务配置会停用、清除旧工具授权，并拒绝旧请求继续调用。
- 模型请求创建事务内冻结工具权限、角色/空间/会话/请求/助手消息身份、服务版本、授权版本和工具定义；上限 32 个工具、定义合计 65536 字符。此处只准备快照，尚未改变文本网络协议。
- 调用开始、状态更新与授权验证在同一 Room 事务；唯一请求/调用 ID 拒绝重放，每个请求最多 8 次。读取调用记录也同时核对角色、空间、会话和请求归属。
- 撤权、停用、更新列表或删除服务使未结束调用成为 FAILED/REVOKED；旧快照无法在重新授权后复活。已经发给外部服务的副作用不能由本机撤销，后续 UI 会先取消当前任务再变更授权。
- 启动时在同一事务内将未结束的调用和模型请求标为 INTERRUPTED，保留部分正文，不重放外部调用。删除会话/角色时随助手消息级联删除调用与授权快照；角色复制不携带授权。单独删除服务保留会话内的加密调用审计。

## 已执行验证

- 48 项 JVM 单元测试通过；原聊天、模型协议、模板、取消和扩展调度回归无失败或跳过。
- verify_extension_sql.py 在内存 SQLite 上执行源文件中的实际 DAO SQL 和迁移 DDL：schema 5 的列、索引与外键匹配 Room 导出；schema 4 所有原数据行保留，包括原凭据引用与图片字节。
- A/B 各 10 条旧合成标记，50 次交替执行授权查询、调用读取及状态写入；错误角色、空间、会话、请求和助手 ID 均拒绝。20 轮撤权/再授权/中断后，迟到完成写入为零。
- 停用与删除服务拒绝旧授权，删除服务保留调用记录，删除聊天消息级联清理其记录且保留另一角色数据，外键检查通过；异常旧头像归属在新增表之前被检测。
- 新增 4 项 ExtensionStorageAndroidTest 已编译：真实 SQLCipher 存储下的 50 次隔离、20 轮取消/撤权/中断、持久化次数上限及 20 个并发开始请求争用同一预算。扩展旧迁移测试覆盖 1/2/3/4→5，并检查原图片、置顶、收藏和凭据读取。

命令：ci/build.ps1 -Tasks ':app:testDebugUnitTest',':app:compileDebugAndroidTestKotlin','--offline'；.venv/Scripts/python.exe ci/script/verify_extension_sql.py。

结果见 [storage-tests.json](storage-tests.json) 和 [storage-sql.json](storage-sql.json)。宿主 SQL 检查没有运行 Kotlin Repository，也不是 SQLCipher 加密落盘或真机验收；Android 测试“编译成功”不能计为运行通过。

## 待完成

- adb devices 当前为空。Android 测试、覆盖安装、冷启动恢复、加密落盘扫描：NOT_RUN；M1/M2 未验项目继续保留。
- 扩展管理、按角色工具选择、调用确认/结果 UI、模型工具回合：下一项开发任务。保存配置或刷表接口本身不等于 App 已经能用插件。
- ToolPkg 运行时、包导入/权限/兼容案例仍属于本次 E1，继续实现；不能以 MCP 完成代替它。
- 发布前还需把当前仍针对 CHAT04 的 verify_apk.py 与 package_delivery.py 更新为 E1 实际权限、版本、载荷与签名检查，构建新包并交付源码。此开发检查点没有发布 APK。
