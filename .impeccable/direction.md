# 共享家园方向契约

## THESIS

进入家园就是进入一张可以走动、耕作和与居民相处的农场地图。所有游戏操作都发生在地图边界内，以物件、动作图片和短气泡发起，不在地图之外排列操作面板。人物占位参与寻路和逐步移动，自主邀约保留到回应或关闭，回应直接开始所邀活动。

## OWN-WORLD

Godot 二维像素农场，CC0 Kenney Tiny Town / Tiny Farm / Tiny Dungeon 图集提供草地、土路、木屋、植株和人物。地图几何、人物占位与经营规则继续读取共享世界。水波、作物成长、步态、操作粒子构成场景动态；原生图片菜单继承应用的 Material 3 主题。

## STORY

第一次打开时，“我”和聊天角色住在一起；新增聊天角色直接成为地图居民。播种、采集、钓鱼、制作、出售和家具布置形成经营循环；共同活动、对白、同行、赠礼和换装绑定用户选择的居民。居民依据已保存人设的性格与兴趣关键词自主散步、停留活动，并主动接近玩家发出邀约。全部角色共享一个世界，删除居民保留农场。

## FIRST VIEWPORT

标题下整块可玩地图，首屏看得到玩家与居民、农场环境和方向键。币、精力、等级、全图、菜单与背包入口都在地图内。点击物件后在地图内显示对应图片，镜头让出浮层位置；图片至少48dp可触控且间隔8dp。背包和配方是物品图像网格，换装使用实际人物预览，进度以条与数字表达。主画面不排列说明文章，角色气泡保持简短。窄屏、横屏、宽屏和大字号中保留实际操作能力。

## FORM

原生 Android / Jetpack Compose 内嵌 Godot 4.7.2，code-led。原始 concept-seed key 为 `5132e4a2`，mode 为 `experience`，catalog pool 为 `c3b204a1eed6`；E15 按用户选定的 Godot 开源种地项目路线替换场景，延续地图内操作与共享世界契约。没有 approved comp；质量标准见 [quality-bar.md](quality-bar.md)。

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## 已确认

- 旧家园未发布，清理旧方案。
- 用户授权继续执行编译、测试、Release 构建和 MuMu 验收。
- E15 延续共享家园，保留已有世界与聊天数据；地图外“小屋／进屋”和“更名”入口已移入地图。未回应邀约在普通背包／菜单期间隐藏，收起后恢复；系统移除动画关闭场景装饰运动。
- 参考游戏为心动小镇、星露谷、饥荒；具体玩法研究见 [当前计划](../docs/当前计划.md)。
