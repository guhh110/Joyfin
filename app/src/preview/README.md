# 手动 UI 场景

仅 `preview` 构建提供 `dev.joyfin.scenario.UiScenarioActivity`，release 不包含此入口。
复用正式页面、Retrofit 服务与 JSON 转换器，以应用拦截器生成正常响应、成功空列表或 HTTP 503。
不新增或运行自动化测试。

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es scenario normal --es page home
```

- `scenario`：`normal`、`empty`、`error`。页面顶部也可切换，切换会刷新当前页面。
- `page`：`home`、`tracking`（追剧）、`detail`（剧集）、`movie`（电影）、`library`、`library-grid`（直接进入作品网格）、`search`、`person`、`settings`、`audit`（片库体检）、`player`、`player-sidebar`。
- `--el delayMs 1200`：每次响应延迟，范围 0–5000ms，用来人工查看按钮内加载和静态骨架。
- `--ez controls false`：隐藏顶部模拟场景切换栏，用完整屏幕检查正式页面布局。
- `-S` 停止当前应用进程后进入独立场景，保留真实数据；每次冷启动可检查不含旧内容的结果状态。
- 从正常切到错误／空数据，可检查刷新保留已有内容及成功清空；从错误切回正常，可检查恢复。

独立进程使用带前缀的模拟偏好和独立文件目录。只接受虚构主机 `ui-scenario.invalid`，所有请求在本地返回，不发送到真实服务器；作品、用户和令牌均为演示数据。
图片默认复用应用内港湾素材，部分剧集／人物故意无图。长作品名、长单集标题、两季剧集用于人工检查排布、边缘绘制和焦点恢复。
播放出口进入本地模拟播放器，复用正式播放器 UI，以港湾素材作为静态画面。可操作播放／暂停、进度、选集、音轨、字幕、画质上限、倍速、画面缩放和控制栏；所有选择只保留在本次场景内。收藏等 HTTP 写入只在模拟响应中完成。模拟播放器不执行在线字幕请求；在线字幕需要在正式播放器连接字幕站验收。

## 图片与列表加载性能复核

`--ez stress true` 提供 20 个媒体库封面，以及 `library-grid` 内 160 部作品、每页 40 项的分页数据。
用不同 URL 的图片复核解码、缓存和滚动，避免共用一张港湾素材掩盖图片加载开销。

主机安装 Python 3 与 Pillow 后：

```sh
python app/src/preview/tools/artwork_fixture.py
# 在另一个终端执行；包名按安装的 Preview 包调整
adb reverse tcp:18766 tcp:18766
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page library-grid --ez stress true --es artworkBase http://127.0.0.1:18766/cold-1 --el delayMs 450
adb shell dumpsys gfxinfo dev.joyfin:ui_scenario framestats
adb shell dumpsys meminfo dev.joyfin:ui_scenario
```

图片服务器只返回生成的 JPEG，支持 320 / 640 / 1920px 图片和 `/stats` 请求数量、字节数统计。
`artworkBase` 仅接受本机及模拟器主机地址；测试图片请求绕过系统代理，普通模拟入口及正式应用沿用原配置。
更换 `cold-1` 路径可复核冷缓存，复用相同路径并重启场景可复核磁盘缓存。`page library` 可复核横向媒体库封面，`page library-grid` 可复核海报和加载更多。
比较性能时使用默认开启 R8 的 Preview 构建，并在相同设备、数据及操作下记录多轮结果；模拟器帧耗时不直接代表真实 TV 表现。

正式图片请求统一为两档：整屏背景上限 `1920×1080`；海报、列表宽封面、剧照、人物、章节及 Logo 的宽度上限为 `320px`，保持原图比例。图片服务保留 `640px` 仅用于复核先前的性能基线。

### 骨架屏与同进程加载对照

`--es skeleton Detailed` 使用正常骨架，`Plain` 使用等高纯色占位，`Drawn` 使用同外观的单绘制节点候选实现。默认仍为 `Detailed`；这些参数只供 Preview 对照，正式界面没有此设置。

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page library-grid --ez stress true --es skeleton Detailed --es profileRun detailed-1 --ei profileReloads 2 --es artworkBase http://127.0.0.1:18766/detailed-1 --el delayMs 1500
adb logcat -d -v raw -s JoyfinUiProfile:I
adb shell dumpsys gfxinfo dev.joyfin:ui_scenario framestats
```

`profileReloads` 在同一 Activity / 进程内自动重新加载作品网格，最多 5 次；每次数据到达后等待 1800ms 再开始下一轮。`profileRun` 启用阶段标记，记录轮次、`startup/loading/content` 和单调时钟纳秒时间，便于与帧记录对齐。更换 run 路径可比较图片冷缓存；同轮重新加载则保留图片内存缓存。每种骨架至少采集三轮，并将冷启动、首次数据到达和同进程后续加载分别比较。

### 列表字段与图片并发复核

`--ez payloadStress true` 在压力作品中加入长简介、24 位演员和视频版本信息，列表响应按实际请求的 `Fields` 投影；单个详情响应仍返回完整信息。`--ez payloadProfile true` 在首屏稳定后于后台执行预热及三轮 JSON 解析 / 模型转换采样，在 `JoyfinPayloadProfile` 输出样本字节数、中位数及 P95，不代表真实服务器、压缩传输或端到端加载耗时。

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page library-grid --ez stress true --ez payloadStress true --ez payloadProfile true --el delayMs 80
adb logcat -d -v raw -s JoyfinPayloadProfile:I
```

在本地图片场景中加 `--ei decodeParallelism 2` 或 `4` 可比较 BitmapFactory 解码并发，并启用 `JoyfinImageProfile` 中不含 URL 的解码和图片完成耗时记录。正式应用仍使用 Coil 默认并发；未传入该参数的普通图片预览不记录这些耗时。

## 追剧页

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page tracking --es scenario normal --el delayMs 80
```

默认收藏 7 部演示剧，覆盖半集续播、跨季续播、待看（含特别篇）、全部已看、跳过旧集但已追平、仅虚拟待入库剧集和长标题多季剧。可检查四种筛选、详情返回、取消追剧、刷新与首页最多 6 部轮播。取消收藏仅影响当前预览进程；用上面的命令重新启动可恢复。顶部的正常／空数据／错误按钮可检查刷新时保留内容、清空及错误恢复。

TV 卡片只保留海报、片名和一行进度；未看数显示在海报角标。点击卡片直接继续／开始观看，已追平或暂无可播放内容时进入详情；长按确认键或按遥控器菜单键展开查看详情、重试进度和取消追剧等操作，取消追剧仍需再次确认。

模拟播放器用于检查播放入口与界面，不产生真实视频解码、进度报告或跨设备同步。

## Cinerama 隧道首页

TV 的 `page home` 正常场景展示追剧、继续观看、最近加入和各媒体库，首页栏目设置继续决定后面三类的显示及顺序。舞台占满屏幕，不预留独立导航栏；以原生 Compose 实现中央巨幕、上下折面和切换动画，保留原型的大致空间关系。中央巨幕宽 90%、高 50%，四周保留留白，上下相邻栏目通过 `graphicsLayer` 的 58° 旋转、缩放和透明度形成折面。纵向切换用 650ms 位移及折叠动画，横向换片用 420ms 滑入及淡入淡出，退场面板沿各自边缘退出。播放、详情、全部作品紧随影片信息悬浮，顶部快捷入口无整块底座，底边仅保留操作提示和作品圆点。不足三个栏目时不重复填充副屏，系统关闭动画时立即切换。手机沿用原有首页布局。

TV 主屏获得焦点后，上下循环切换栏目、左右循环切换作品；OK 播放电影、续播追剧目标单集或打开剧集详情，长按 OK／菜单键打开详情，继续观看栏目长按打开原有快捷操作。返回键将焦点移到悬浮快捷入口，可继续进入追剧、媒体库、搜索和设置；顶部入口按下进入悬浮播放按钮，操作按钮按上／下回到选片舞台。每个栏目分别保存作品选择，从详情返回后恢复；列表刷新使用作品 ID 保持选择，作品移除后回退到第一部。仅 TV 首页使用新舞台，其它页面的路由、布局和全局配色恢复到主线版本（`41527aa`），手机首页沿用主线布局。

主屏背景沿用 1080p 图片请求，副屏使用缩略图，外层采用柔和的径向环境光以兼容 Android 9–11；仅保留当前、相邻及正在退场的面板。图片失败仍可通过文字操作作品。加载、空栏目、全部为空及请求失败都有独立展示；已有内容刷新失败时保留作品并显示重试入口。`scenario empty/error` 可检查空状态及重试。

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page home --es scenario normal --el delayMs 80
```

可配合 `artworkBase` 本地图片服务记录 1920px 主屏及 320px 副屏／环境图请求数量。

## 播放器、设置与侧边栏

各页面采用单行标题栏。媒体库的标题、数量和筛选合并为一条工具栏；搜索返回、输入框和提交位于同一行；字幕页移除重复入口，TV 搜索条件与输入同行。人物页通过信息按钮展开完整介绍，播放器的片名与集名同行。可重点检查长标题、大字体、窄屏工具横向滚动和弹层关闭后的焦点恢复。

```sh
# 设置页：演示账号、分组偏好与首页管理
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page settings
# 片库体检：选择开始扫描，检查筛选、分组结果和依据侧栏
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page audit --el delayMs 80
# 播放器：默认暂停，便于检查控制栏，手机自动横屏
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page player
# 直接展开播放设置侧边栏
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page player-sidebar
# 指定子面板：Audio、Subtitle、Episodes、Quality、Speed、Chapters、Versions、Skip、Advanced、PlaybackMode 等
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page player-sidebar --es panel Episodes
# 空音轨、字幕、剧集与章节，以及模拟播放错误
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page player-sidebar --es panel Audio --es scenario empty
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page player --es scenario error
```

模拟播放器不创建解码器、不发送播放请求或进度报告。画面缩放作用于静态港湾素材；诊断面板不会产生真实播放诊断，搜索字幕仅显示模拟提示，手动标记不在此模拟范围。

退出模拟界面后启动普通 `dev.joyfin/.MainActivity`，继续使用原有服务器和账号。
