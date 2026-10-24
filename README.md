# Joyfin

Joyfin 是一款面向 Android TV、Google TV 和 Android 手机的 Jellyfin 客户端，为个人媒体库提供简洁、沉浸的观影体验。

以电影与故事为中心，Joyfin 将海报、剧照和内容介绍融入深色影院界面，让浏览片库、发现作品和挑选剧集自然衔接。无论使用电视遥控器还是手机触摸，都能舒适地探索自己的收藏。

连接你的 Jellyfin 服务器，在大屏与手机上欣赏电影和剧集，延续观看进度，继续未完的故事。

## 代码导航

页面按功能组织在 `app/src/main/java/dev/joyfin/ui/features/`，TV／手机布局分别放在各功能的 `tv/` 与 `mobile/` 目录；基础组件在 `ui/components/`，数据、会话和播放逻辑分别位于 `data/`、`session/` 和 `playback/`。

网络接口使用 Retrofit，底层连接由 OkHttp 管理；API 和本地配置统一使用 Kotlin Serialization。接口声明、传输模型与业务转换分别位于 `data/JellyfinService.kt`、`data/JellyfinDtos.kt` 和 `data/JellyfinApi.kt`。

播放器使用 Media3 与 libass，音频优先使用系统解码／直通，并由 [Jellyfin FFmpeg 音频扩展](https://github.com/jellyfin/jellyfin-androidx-media)补充 DTS／DTS-HD、TrueHD 等解码能力；视频能力按 Media3 的格式检查与容器解封装组合上报；内嵌 PGS 等字幕由 Media3 渲染。默认原画不设 80 Mbps 限制，已保存的限速偏好继续生效；具体支持以运行时检测和服务端协商为准。该扩展发布包使用 GPL-3.0 许可证，源码与许可证见其上游仓库。播放协商日志使用 `JoyfinPlayback` 标签，包含播放方式、兼容模式、上报音频格式和服务器返回的转码原因，不记录播放 URL 或令牌。

公开仓库仅包含应用源码、资源、测试及构建工具。设计文档、截图、设备记录、备份和构建产物保留在本地，不纳入版本控制。

## 本地打包

安装 JDK 21、Python 3 与项目所需的 Android SDK（compileSdk 37），在 `local.properties` 中设置 `sdk.dir`，或配置 `ANDROID_HOME`。脚本优先使用 `JAVA_HOME` 与 `GRADLE_BIN`，也会识别当前机器 `/tmp/tvfin-jdk/`、`/tmp/tvfin-gradle/` 下的开发工具；其他环境默认使用项目 Gradle Wrapper，首次运行需要联网下载 Gradle 和依赖。

在项目目录执行：

```bash
./build.sh                   # 手机 / TV 通用包
./build.sh --offline         # 缓存完整时离线打包
./build.sh --dry-run         # 只查看配置，不执行打包
```

脚本生成启用 R8 与资源优化的 Preview APK，沿用当前机器的 debug 签名。同一个 APK 支持 Android 9+ 手机和 TV，包含 ARM32、ARM64、x86、x86_64；运行时自动选择布局、触摸／遥控器操作与浏览页方向。覆盖安装需要与原安装使用同一签名密钥。

每次产物写入独立的 `dist/joyfin-时间-随机后缀/`，包含 `Joyfin-版本号.apk`（当前为 `Joyfin-0.2.3.apk`）、构建日志、Gradle 输出元数据与 `SHA256SUMS.txt`，不会覆盖历史安装包。文件名中的版本由 Python 3 从本次构建元数据读取；脚本不安装 APK、不修改版本号、不发布 Release。源码修改不会自动执行此脚本。
