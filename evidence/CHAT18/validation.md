# 聊天首页与角色管理：增删改查

## 原因与改动

首页的角色操作菜单原先依赖已有私聊，未聊天的角色没有操作入口；编辑依赖点击角色，删除藏在长资料页底部，角色管理未提供搜索。

- [聊天首页](../../app/src/main/java/com/ai/assistance/operit/ui/main/MessengerPage.kt)：所有角色行提供右侧菜单和长按操作，编辑角色直达对应资料，删除角色与删除对话分开。首页搜索明确查询角色、群聊与最近消息，返回可关闭搜索。
- [角色管理](../../app/src/main/java/com/ai/assistance/operit/ui/main/ProfilePages.kt)：新增、名称与人设搜索、编辑按钮、删除菜单；所有角色删除入口共用范围说明与确认框。
- [角色编辑](../../app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt)：事务保存成功后返回列表；删除确认保留至事务成功，操作失败由既有错误提示说明。
- [群聊列表](../../app/src/main/java/com/ai/assistance/operit/ui/main/GroupPages.kt)与[视图模型](../../app/src/main/java/com/ai/assistance/operit/ui/main/CompanionViewModel.kt)：从当前数据库读取群定义后编辑或确认删除，沿用群版本检查及生成停止规则。
- 为 `PersonRemove` 注册实心和线框主题图标，避免严格图标查找在操作菜单中失败。数据格式、唯一私聊规则及角色存档不变。

## 实际验证

设备为 MuMu `emulator-5554`，覆盖安装保留原应用数据。手机尺寸设为 600×1280 / 256 dpi（375dp 宽）；另核对 200% 系统字号和动画时长 0。

| 操作 | 实际结果与证据 |
| --- | --- |
| 新增角色 | 从角色管理创建 `CRUD-QA`，填写本机测试人设、开场白及已保存模型配置；保存成功后返回列表。创建不请求模型。 |
| 未聊天角色操作 | 首页长按 `CRUD-QA` 显示编辑与删除角色，两项均可用，见[操作菜单](unstarted-role-menu.png)。 |
| 编辑 | 从首页菜单直达 `CRUD-QA` 资料，将名称改为 `CRUD-Edited`，保存后列表即时显示新名称，见[编辑后](role-edited.png)。 |
| 查询角色 | 在角色管理输入 `fixture`，按人设匹配，仅显示测试角色，见[搜索结果](role-search.png)。 |
| 私聊与删除对话 | 打开测试角色生成本机开场白，不发送模型请求；首页菜单显示编辑、置顶、删除对话、删除角色，见[私聊菜单](private-chat-menu.png)。确认删除后角色仍显示，已创建的家园仍能打开，见[删除说明](delete-chat-confirm.png)、[保留家园](home-after-chat-delete.png)。 |
| 新建、编辑群聊 | 创建 `CRUD-Group`，选择测试角色及原“测试”角色，保存后从首页菜单加载群定义并将名称改为 `CRUD-Group-Edited`，两位成员与模型选择保留。见[群聊菜单](group-menu.png)、[编辑后](group-edited.png)。本轮不发送群消息。 |
| 查询与返回 | 首页输入小写 `crud-group` 匹配新群名，见[首页搜索](inbox-search.png)；键盘收起后系统返回关闭搜索、恢复完整首页。 |
| 删除群聊 | 首页确认框明确保留角色、私聊和家园，见[群聊删除说明](delete-group-confirm.png)；确认后临时群从首页消失，角色和测试私聊仍在。 |
| 删除角色 | 角色管理菜单删除 `CRUD-Edited`，确认框显示 1 段私聊及家园进度的删除范围；200% 字号下说明可滚动、两个按钮可点击，见[删除说明](delete-role-confirm-font-200.png)。确认后临时角色从列表消失，原“你好2”“测试”仍在。 |
| 最终布局 | 最终 APK 在 375dp 宽、200% 字号下将标题与操作分行，标题完整显示、新增与导入按钮可用，见[大字号修复后](manager-font-200-fixed.png)。正常字号的[横屏界面](manager-landscape.png)也已查看。 |
| 原家园 | 删除临时角色后，原“你好2”家园仍可打开，显示 231 家园币、Lv.2，见[原角色家园](original-home-preserved.png)。此项验证可访问性，不是对其全部历史数据逐项比对。 |

本轮创建的临时角色、私聊、家园及群聊已通过应用界面清理。流程截图来自首个验证构建；大字号标题出现单字换行，已调整为独立标题行和操作行，并在最终 APK 核对修复。验证结束恢复原物理分辨率 900×1600、密度 240、系统字号 1.0、动画时长 0.5、自动旋转和用户旋转值 0。

## 构建与交付

- 本轮两次执行 `./ci/build.ps1 -Tasks ':app:assembleRelease'`，分别成功耗时 2 分 41 秒、3 分 8 秒；第二次包含大字号标题修复。
- 最终 APK 已覆盖安装到 MuMu，启动成功；`apksigner verify --verbose` 确认 v2 签名有效，`zipalign -c -P 16 4` 检查通过，静态检查见 [apk-audit.json](apk-audit.json)。最后一次 `AndroidRuntime` 错误日志读取没有应用异常输出。
- 单版 [local-companion-0.39.0.apk](../../output/local-companion-0.39.0.apk)，版本 0.39.0 / code 40，19,337,907 字节。SHA-256：`5C8E25DCDC68E87E2EAB135667D5ED60985CE4540262E232BD6821A2ABB3E464`。
- 安装说明同步更新；历史源码包及 `delivery.json` 未重新生成。

## 验证边界

本轮没有运行单元测试、Android 仪器测试、真机或 TalkBack 朗读。没有向聊天模型发送消息，也没有验证生成中删除、关联长期记忆处置及大量角色的列表表现；这些继续使用现有事务与错误提示，不记为本轮设备通过。
