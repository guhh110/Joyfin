# 手动 UI 场景

仅 `preview` 构建提供 `dev.joyfin.scenario.UiScenarioActivity`，release 不包含此入口。
复用正式页面、Retrofit 服务与 JSON 转换器，以应用拦截器生成正常响应、成功空列表或 HTTP 503。
不新增或运行自动化测试。

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es scenario normal --es page home
```

- `scenario`：`normal`、`empty`、`error`。页面顶部也可切换，切换会刷新当前页面。
- `page`：`home`、`detail`（剧集）、`movie`（电影）、`library`、`library-grid`（直接进入作品网格）、`search`、`subtitles`、`person`、`settings`、`audit`（片库体检）、`player`、`player-sidebar`。
- `--el delayMs 1200`：每次响应延迟，范围 0–5000ms，用来人工查看按钮内加载和静态骨架。
- `-S` 停止当前应用进程后进入独立场景，保留真实数据；每次冷启动可检查不含旧内容的结果状态。
- 从正常切到错误／空数据，可检查刷新保留已有内容及成功清空；从错误切回正常，可检查恢复。

独立进程使用带前缀的模拟偏好和独立文件目录。只接受虚构主机 `ui-scenario.invalid`，所有请求在本地返回，不发送到真实服务器；作品、用户和令牌均为演示数据。
图片默认复用应用内港湾素材，部分剧集／人物故意无图。长作品名、长单集标题、两季剧集用于人工检查排布、边缘绘制和焦点恢复。
播放出口进入本地模拟播放器，复用正式播放器 UI，以港湾素材作为静态画面。可操作播放／暂停、进度、选集、音轨、字幕、画质上限、倍速、画面缩放和控制栏；所有选择只保留在本次场景内。收藏等 HTTP 写入只在模拟响应中完成。字幕页复用隔离的本地目录，默认没有下载包；字幕站搜索、包下载和本地整理不属于 Jellyfin Retrofit 三状态模拟范围。

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

## 播放器、设置与侧边栏

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
