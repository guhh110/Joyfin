#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'HELP'
Joyfin 本地打包（优化 Preview，使用现有 debug 签名）

用法：./build.sh [tv|mobile] [--offline] [--dry-run]
  tv          默认；Android 9+，ARM32 / ARM64 / x86 / x86_64 通用包
  mobile      Android 12+，ARM64 手机包
  --offline   使用本地 Gradle 依赖缓存
  --dry-run   只显示配置和命令，不打包、不写入产物

可用 JAVA_HOME 指定 JDK 21，GRADLE_BIN 指定 Gradle 可执行文件。
Android SDK 由 local.properties 或 ANDROID_HOME 配置。
HELP
}
fail() { printf '错误：%s\n' "$*" >&2; exit 1; }
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
target=tv
target_set=false
offline=false
dry_run=false
for arg in "$@"; do
    case "$arg" in
        tv|mobile)
            if "$target_set"; then fail '只能选择一个目标：tv 或 mobile'; fi
            target="$arg"
            target_set=true
            ;;
        --offline) offline=true ;;
        --dry-run) dry_run=true ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; fail "未知参数：$arg" ;;
    esac
done

# 优先使用用户配置，再寻找 macOS JDK 21 和当前开发环境的本地工具。
if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -x /usr/libexec/java_home ]]; then
        if detected_java="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
            detected_version="$("$detected_java/bin/java" -version 2>&1 || true)"
            if [[ "$detected_version" =~ version\ \"21[.\"] ]]; then
                export JAVA_HOME="$detected_java"
            fi
        fi
    fi
    if [[ -z "${JAVA_HOME:-}" ]]; then
        for candidate in /tmp/tvfin-jdk/jdk-21*/Contents/Home; do
            if [[ -x "$candidate/bin/java" ]]; then export JAVA_HOME="$candidate"; break; fi
        done
    fi
fi
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
java_version="$("$java_bin" -version 2>&1)" || fail '未找到 Java，请安装 JDK 21 并设置 JAVA_HOME'
if [[ ! "$java_version" =~ version\ \"21[.\"] ]]; then
    fail '请将 JAVA_HOME 指向 JDK 21，然后重试'
fi
if [[ -n "${GRADLE_BIN:-}" ]]; then
    gradle_bin="$GRADLE_BIN"
elif [[ -x /tmp/tvfin-gradle/gradle-9.6.0/bin/gradle ]]; then
    gradle_bin=/tmp/tvfin-gradle/gradle-9.6.0/bin/gradle
else
    gradle_bin="$root/gradlew"
fi
[[ -x "$gradle_bin" ]] || fail "Gradle 不可执行：$gradle_bin"
if command -v shasum >/dev/null 2>&1; then
    checksum=(shasum -a 256)
elif command -v sha256sum >/dev/null 2>&1; then
    checksum=(sha256sum)
else
    fail '需要 shasum 或 sha256sum 来生成校验值'
fi
property=-Pldplayer=true
if [[ "$target" == mobile ]]; then property=-Pmobile=true; fi
command_args=("$gradle_bin" :app:assemblePreview "$property" --console=plain)
if "$offline"; then command_args+=(--offline); fi
printf '目标：%s\nJDK：%s\n命令：' "$target" "${JAVA_HOME:-PATH 中的 Java}"
printf '%q ' "${command_args[@]}"
printf '\n'
if "$dry_run"; then exit 0; fi

cd -- "$root"
mkdir -p "$root/dist"
output_dir="$(mktemp -d "$root/dist/joyfin-${target}-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
printf '构建记录：%s/build.log\n' "$output_dir"
if ! "${command_args[@]}" 2>&1 | tee "$output_dir/build.log"; then
    fail "打包失败，日志保留在 $output_dir/build.log"
fi
source_apk="$root/app/build/outputs/apk/preview/app-preview.apk"
[[ -s "$source_apk" ]] || fail 'Gradle 已结束，但未找到 Preview APK'
apk_name="Joyfin-${target}.apk"
cp -- "$source_apk" "$output_dir/$apk_name"
cp -- "$root/app/build/outputs/apk/preview/output-metadata.json" "$output_dir/output-metadata.json"
(
    cd -- "$output_dir"
    "${checksum[@]}" "$apk_name" > SHA256SUMS.txt
)
printf '\n打包完成：%s/%s\n校验文件：%s/SHA256SUMS.txt\n' "$output_dir" "$apk_name" "$output_dir"
