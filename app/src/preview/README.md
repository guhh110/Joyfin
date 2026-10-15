# 手动 UI 场景

仅 `preview` 构建提供 `dev.joyfin.scenario.UiScenarioActivity`，release 不包含此入口。
复用正式页面、Retrofit 服务与 JSON 转换器，以应用拦截器生成正常响应、成功空列表或 HTTP 503。
不新增或运行自动化测试。

```sh
adb shell am start -S -W -n dev.joyfin/.scenario.UiScenarioActivity --es scenario normal --es page home
```

- `scenario`：`normal`、`empty`、`error`。页面顶部也可切换，切换会刷新当前页面。
- `page`：`home`、`detail`（剧集）、`movie`（电影）、`library`、`search`、`subtitles`。
- `--el delayMs 1200`：每次响应延迟，范围 0–5000ms，用来人工查看按钮内加载和静态骨架。
- `-S` 停止当前应用进程后进入独立场景，保留真实数据；每次冷启动可检查不含旧内容的结果状态。
- 从正常切到错误／空数据，可检查刷新保留已有内容及成功清空；从错误切回正常，可检查恢复。

独立进程使用带前缀的模拟偏好和独立文件目录。只接受虚构主机 `ui-scenario.invalid`，所有请求在本地返回，不发送到真实服务器；作品、用户和令牌均为演示数据。
图片复用应用内港湾素材，部分剧集／人物故意无图。长作品名、长单集标题、两季剧集用于人工检查排布、边缘绘制和焦点恢复。
播放出口只显示模拟反馈；收藏等 HTTP 写入只在模拟响应中完成。字幕页复用隔离的本地目录，默认没有下载包；字幕站搜索、包下载和本地整理不属于 Jellyfin Retrofit 三状态模拟范围。

退出模拟界面后启动普通 `dev.joyfin/.MainActivity`，继续使用原有服务器和账号。
