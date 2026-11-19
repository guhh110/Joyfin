# 手动 UI 场景

仅 `preview` 构建提供 `dev.joyfin.scenario.UiScenarioActivity`，release 不包含此入口。
复用正式页面、Retrofit 服务与 JSON 转换器，以应用拦截器生成正常响应、成功空列表或 HTTP 503。
TV 首屏另有候选规则、发现请求与遥控器自动化测试；此入口用于人工检查正式页面和故障恢复。

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

## TV 首页首屏

TV 的 `page home` 正常场景展示全屏主视觉、主操作与详情、底部最多六个候选，顶部仅显示 Joyfin 图形 Logo 和全局导航，不显示品牌小字、页面标题或返回按钮；其他 TV 页头不变。默认三部续看与三部发现交错，缺额互补，同剧去重；发现只使用最近入库且尚未看完的电影与剧集，排除完整续看池。继续观看、最近加入和媒体库分页继续遵守首页栏目设置。手机沿用原有首页布局。

首屏片名优先显示服务器提供的透明 Logo，保持比例并靠左展示；没有 Logo、加载中或加载失败时使用文字片名，标题区保持等高，避免下方信息和按钮跳动。首屏候选、栏目展开卡片和普通海报的媒体标题同步缩小，栏目标题与“查看全部”字号保持不变。

TV 首屏采用电影舞台：横图铺满背景，右侧保留主体，左侧局部遮罩保证文字对比；右侧中部减少压暗与染色，底部渐隐推迟到片单附近。背景解码后在后台采样低饱和氛围色，不额外请求图片；无横图时使用更集中的低饱和色晕与右侧原比例海报，海报位于暗罩前方，保留原图亮度、细边与柔和投影，不覆盖底部候选。候选停稳 180ms 后才加载新背景，加载期间保留上一张，准备好后交叉淡入 320ms；失败回到中性背景，不影响文字与操作。默认演示剧集与电影提供横图标记，“周末去海边”保留无横图分支；默认仍共用横版港湾素材，真实竖版比例与亮度通过设备测试的竖图夹具验证。

文字片名仅在明确的冒号或带空格的长横线处分为主副标题；主副标题分别控制字号、行高与间隔，副标题更小、更淡且最多一行，普通片名保留最多两行。时间数字及无效分隔保持原文，标题区等高，Logo 优先级不变；大字体模式保留主副层级。简介宽度收窄至文字区的约八成，与操作之间留白。候选获得焦点时轻抬与放大，并显示低强度氛围柔光和白框；布局槽位不变，未聚焦的当前作品仍只显示短线，进度条保留。关闭系统动画时取消淡入与抬升动画，仍保留最终焦点状态。

首屏与候选卡不显示来源标签，底部不显示常驻按键提示；候选片名和进度条保留，来源信息仍用于无障碍描述。简介最多一行；元信息仅显示年份、电影／剧集与评分，缺失项省略。剧集季／集编号并入按钮，例如“继续 S5E140”；续看电影能计算剩余时长时显示“继续 · 剩余 N 分钟”，不再预留独立进度文字行。其他主按钮为“播放”“继续”或“剧集”，旁边保留“详情”；“剧集”使用列表图标，其他主操作使用播放图标。主按钮按全部候选文案及当前字号统一预留宽度，本次首页停留期间只增不减；详情按钮宽度固定，切换候选时两按钮的位置和尺寸保持稳定，保留焦点动效。候选仅在真正聚焦时显示白框，当前选中但未聚焦的候选改用图片上方短线，与底部观看进度条区分；短线区域始终预留。必要的加载、错误与重试提示保留。

内容就绪后聚焦主操作；左右切换主操作／详情，上键进入导航，下键进入当前候选。候选左右预览且边界停止，确认执行主操作，上键回主操作，下键进入下一栏目。返回键从候选回主操作，再回导航。详情或播放返回后恢复作品、栏目与焦点；刷新和分批到达通过稳定 ID 保持选择。

“查看全部”固定在各栏目标题栏最右侧，不随卡片横向滚动，空内容、加载和错误状态也不在列表末尾重复显示。卡片或重试入口上键进入“查看全部”，下键返回当前卡片或空状态的重试入口；按钮上键进入上一栏目，无下方内容时下键进入下一栏目，翻页键仍直接切换栏目。重试保留在列表末尾；两种入口使用紧凑的文字箭头样式，与媒体卡片共用焦点边框。焦点离开卡片后保留展开预览与背景，但移除白框，避免与入口同时显示焦点。

首屏以下的栏目减弱背景暗罩，右侧画面更明亮，底部渐隐延后；首屏舞台不变。仅当前选中的展开卡片在片名下增加一行弱化信息：剧集单集显示季集编号与剩余时长／总时长，电影显示年份、评分与剩余时长／总时长，整部剧集显示年份与评分。缺失信息直接省略，不展示简介或额外标签；折叠卡片、卡片尺寸、播放入口及焦点恢复保持不变。

栏目剧集卡片优先显示真实单集剧照；缺失或加载失败时，使用同剧海报低分辨率柔化后的低对比背景，海报也不可用则保留冷灰渐变。缺图提示只保留小号、低亮度的“暂无剧照”，不再重复季集编号。柔化使用小尺寸采样，Android 11 等旧版系统同样生效，不依赖系统模糊特效；正常剧照不做柔化。仅栏目媒体卡的焦点边框略微降低不透明度，其他页面、首屏与“查看全部”不变。

完整展示当前作品后，无操作十秒自动切换；首次遥控按键立即停止本次轮播，刷新不重置。离开首屏、后台和弹层期间暂停。背景上限 1080p，最多预加载下一张；无横图时显示渐变与右侧海报，图片失败仍保留文字和操作。续看、追剧、发现独立加载和重试，已有内容刷新失败时保留。`scenario empty/error` 与 `delayMs 5000` 可检查空状态、失败和慢加载。

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es page home --es scenario normal --el delayMs 80
```

可配合 `artworkBase` 本地图片服务记录 1920px 背景及候选缩略图请求数量。只需在当前 TV 模拟器验证，不要求切换显示尺寸。

## 播放器、设置与侧边栏

各页面采用单行标题栏。媒体库的标题、数量和筛选合并为一条工具栏；搜索返回、输入框和提交位于同一行，TV 在线字幕搜索条件与输入同行。人物页通过信息按钮展开完整介绍，播放器的片名与集名同行。TV 播放控制不显示独立黑色底板与边框，保留画面边缘渐变。可检查长标题和弹层关闭后的焦点恢复。

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
