## verdict

本轮只评分 [首轮复核](finish-review.md) 的 F1–F5，没有开展新缺陷搜寻。已逐一重新打开原路径的 12 张截图，以及成功重入、5 张邀约转换、4 张运动对比和移除动画后的操作截图；均显示名称对应的原生界面，没有黑屏、空白或错误页面。补充 [capture-matrix.json](capture-matrix.json) 关联最终 APK SHA-256 `d553fee0b827c93f2a27f7660fe2275134bbd1cccfdc7cf148ef5dbe5cb03011` 与 19 组界面／控件证据，4 张运动对比图单独记录于 motion JSON。

| 项目 | 判定 | 本轮证据 |
| --- | --- | --- |
| F1：Godot 宿主导航生命周期 | resolved | [tablet-reentry.png](tablet-reentry.png) 现在显示完整家园、人物和地图内控制，不再是桌面。[lifecycle.json](lifecycle.json) 记录聊天↔家园 3 次及后台恢复均保持 PID 9590，未 force-stop。HomeGodotLayer 已固定于 MainActivity 的 Scaffold 容器，HomeGodotSurface 仅发布地图位置／数据，离开页面设置 GONE 与 pause；Activity 销毁才调用引擎 destroy。当前截图中的原生浮层、标题与导航未被永久渲染层遮住。 |
| F2：朝左人物锚点 | resolved | [phone-crop.png](phone-crop.png)、[phone-harvest.png](phone-harvest.png)、[tablet-bag.png](tablet-bag.png) 与 [reduced-motion-gameplay.png](reduced-motion-gameplay.png) 中人物、脚下阴影和名牌沿同一中心显示；原来向右偏移一整个 sprite 的现象已消失。`_actor()` 在负宽镜像前不再平移 position.x，穿搭、活动图片与阴影继续使用相同 foot。 |
| F3：池塘内部岸线 | resolved | [phone-overview.png](phone-overview.png)、[landscape.png](landscape.png)、[tablet.png](tablet.png)、[tablet-bag.png](tablet-bag.png) 均呈现连续水面，浅色岸线只沿外缘；内部逐格方框已消失，水波与木栈道保留。`_pond_cells()` 把 JSON 格坐标转为 Vector2i 整数键，邻格判定与该集合一致。 |
| F4：背包／菜单保留邀约 | resolved | [invitation-before.png](invitation-before.png) 显示“你好”的 CLEAN 邀约；[invitation-bag.png](invitation-bag.png) 与 [invitation-menu.png](invitation-menu.png) 中邀约隐藏，关闭后在 [invitation-after.png](invitation-after.png) 与 [invitation-after-menu.png](invitation-after-menu.png) 恢复相同肖像、文案和回应图片。[invitation-retained.json](invitation-retained.json) 记录两次恢复及真实原生转换，没有注入数据库。`open()` 只改变 panelName，不再清除 invitation。 |
| F5：系统移除动画 | resolved | 已打开 normal／reduced 的各两张原生截图。[motion-normal.json](motion-normal.json) 在动画倍率 0.5 的池塘 ROI 记录 188 个变化像素；[motion-reduced.json](motion-reduced.json) 在实时改为 0 后同 ROI 为 0。Settings 观察器直接读取 ANIMATOR_DURATION_SCALE 并传入 packet.reduceMotion；Godot 在该状态停止 clock、清除粒子，人物起伏归零，状态改变仍可重绘。[reduced-motion-gameplay.png](reduced-motion-gameplay.png) 显示该设置下向左移动后的实际家园，必要操作保留。像素结论只涵盖已记录水面 ROI，其他装饰运动由实现路径核对支持。 |

这批修复在上述重拍矩阵中未引入可见的层叠、地图边界或控制布局回归；没有新增 material 项目。

## remaining

clear — F1–F5 均 resolved。本轮 `ship` 仅覆盖这五项修复的评分，不扩大为整面重新审计或全设备质量认证。

暗色主题、TalkBack、真机性能、在线模型与隐私锁仍未验收；当前后续文档尚由 documenter 更新。570 项 JVM 测试、Godot 检查、Release 构建与 APK 审计为输入提供的验证结果；JVM 测试在最后 Android 偏好读取修正前执行，本 reviewer 没有运行或补记为最终包的新一轮测试。5 份本轮 home 图集 provenance scan 为 0 missing；其他未改变资产的预存元数据缺失不因本评分变成全 APK 溯源通过。

disposition: ship
