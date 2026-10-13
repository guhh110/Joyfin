# Joyfin

Joyfin 是一款面向 Android TV、Google TV 和 Android 手机的 Jellyfin 客户端，为个人媒体库提供简洁、沉浸的观影体验。

以电影与故事为中心，Joyfin 将海报、剧照和内容介绍融入深色影院界面，让浏览片库、发现作品和挑选剧集自然衔接。无论使用电视遥控器还是手机触摸，都能舒适地探索自己的收藏。

连接你的 Jellyfin 服务器，在大屏与手机上欣赏电影和剧集，延续观看进度，继续未完的故事。

## 代码导航

页面按功能组织在 `app/src/main/java/dev/joyfin/ui/features/`，TV／手机布局分别放在各功能的 `tv/` 与 `mobile/` 目录；基础组件在 `ui/components/`，数据、会话和播放逻辑分别位于 `data/`、`session/` 和 `playback/`。

网络接口使用 Retrofit，底层连接由 OkHttp 管理；API 和本地配置统一使用 Kotlin Serialization。接口声明、传输模型与业务转换分别位于 `data/JellyfinService.kt`、`data/JellyfinDtos.kt` 和 `data/JellyfinApi.kt`。

公开仓库仅包含应用源码、资源、测试及构建工具。设计文档、截图、设备记录、备份和构建产物保留在本地，不纳入版本控制。

## 本地打包

安装 JDK 21 与项目所需的 Android SDK（compileSdk 37），在 `local.properties` 中设置 `sdk.dir`，或配置 `ANDROID_HOME`。脚本优先使用 `JAVA_HOME` 与 `GRADLE_BIN`，也会识别当前机器 `/tmp/tvfin-jdk/`、`/tmp/tvfin-gradle/` 下的开发工具；其他环境默认使用项目 Gradle Wrapper，首次运行需要联网下载 Gradle 和依赖。

在项目目录执行：

```bash
./build.sh tv                 # TV 通用包，默认目标
./build.sh mobile             # ARM64 手机包
./build.sh tv --offline       # 缓存完整时离线打包
./build.sh tv --dry-run       # 只查看配置，不执行打包
```

脚本生成启用 R8 与资源优化的 Preview APK，沿用当前机器的 debug 签名。TV 支持 Android 9+，包含 ARM32、ARM64、x86、x86_64；手机支持 Android 12+，仅 ARM64。覆盖安装需要与原安装使用同一签名密钥。

每次产物写入独立的 `dist/joyfin-目标-时间-随机后缀/`，包含 APK、构建日志、Gradle 输出元数据与 `SHA256SUMS.txt`，不会覆盖历史安装包。脚本不安装 APK、不修改版本号、不发布 Release。源码修改不会自动执行此脚本。
