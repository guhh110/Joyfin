# Joyfin

Joyfin 是一款面向 Android TV、Google TV 和 Android 手机的 Jellyfin 客户端。连接自己的 Jellyfin 服务器，即可浏览媒体库、播放电影与剧集，并延续观看进度。

应用采用深色界面，分别适配电视遥控器和手机触摸操作。一个 APK 同时支持 Android 9 及以上的手机与 TV，包含 ARM32、ARM64、x86 和 x86_64 架构。

## 功能

- 浏览媒体库、搜索作品、查看电影与剧集详情。
- 查看剧集、演职人员和相似作品，管理收藏与继续观看。
- 切换媒体版本、画质、音轨、字幕与播放速度。
- 搜索、下载和整理字幕，在播放器中选择本地字幕。

播放器基于 Media3 与 libass，使用系统音频解码／直通，并通过 [Jellyfin FFmpeg 音频扩展](https://github.com/jellyfin/jellyfin-androidx-media)补充 DTS、DTS-HD 和 TrueHD 等格式的解码能力。默认使用不限码率的原画设置，实际播放方式取决于设备能力、音视频格式、网络条件与 Jellyfin 服务端协商结果。

## 下载与安装

安装包见 [GitHub Releases](https://github.com/guhh110/Joyfin/releases)。手机与 TV 使用同一通用 APK。覆盖升级需要与已安装版本使用相同的签名证书。

## 从源码构建

构建环境：

- JDK 21
- Python 3
- Android SDK 37

通过 `JAVA_HOME` 指定 JDK，通过 `ANDROID_HOME` 或 `local.properties` 中的 `sdk.dir` 指定 Android SDK。项目包含 Gradle Wrapper，首次构建需要下载 Gradle 和依赖；也可通过 `GRADLE_BIN` 指定 Gradle 可执行文件。

在项目根目录执行：

```bash
./build.sh                   # 构建使用开发签名的 Preview APK
./build.sh --offline         # 使用已缓存的依赖构建
./build.sh --dry-run         # 查看构建命令
```

Preview 构建启用 R8 和资源优化，并包含 UI 场景预览入口。正式 Release 构建不包含该入口。设置以下签名环境变量后，可运行 `./build.sh --release` 构建正式 APK：

| 环境变量 | 说明 |
| --- | --- |
| `JOYFIN_KEYSTORE_PATH` | 签名密钥库路径 |
| `JOYFIN_STORE_PASSWORD` | 密钥库密码 |
| `JOYFIN_KEY_ALIAS` | 签名密钥别名 |
| `JOYFIN_KEY_PASSWORD` | 签名密钥密码 |

构建产物输出到 `dist/`，包括 `Joyfin-版本号.apk`、`SHA256SUMS.txt`、构建日志和 Gradle 输出元数据。版本号读取自本次构建元数据。

## 自动发布

GitHub Actions 发布工作流位于 `.github/workflows/release.yml`。推送与应用版本一致的 `v主版本.次版本.修订号` 标签时，工作流构建正式通用 APK，校验版本、签名证书和架构后发布 GitHub Release。签名凭据通过仓库的 Actions Secrets 配置。

## 代码结构

- `ui/features/`：按功能组织的页面，TV 与手机布局分别位于各功能的 `tv/`、`mobile/` 目录。
- `ui/components/`：共享界面组件。
- `data/`、`session/`：数据访问与会话管理。
- `playback/`：播放控制、能力检测与播放诊断。

以上路径均相对于 `app/src/main/java/dev/joyfin/`。网络请求使用 Retrofit 与 OkHttp，数据序列化使用 Kotlin Serialization。

## 第三方组件

Jellyfin FFmpeg 音频扩展发布包采用 GPL-3.0 许可证，其源码和许可证见[上游仓库](https://github.com/jellyfin/jellyfin-androidx-media)。
