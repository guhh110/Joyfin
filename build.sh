#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'HELP'
Joyfin 通用 APK 打包（默认优化 Preview，--release 使用正式签名）

用法：./build.sh [--release] [--offline] [--dry-run]
  Android 9+ 手机 / TV 通用包，包含 ARM32 / ARM64 / x86 / x86_64
  --offline   使用本地 Gradle 依赖缓存
  --dry-run   只显示配置和命令，不打包、不写入产物
  --release   构建正式 Release，不包含 UI 模拟入口

正式构建需要 JOYFIN_KEYSTORE_PATH、JOYFIN_STORE_PASSWORD、JOYFIN_KEY_ALIAS、JOYFIN_KEY_PASSWORD。
CI 可通过 JOYFIN_RELEASE_TAG 校验标签与本次 Gradle 产物版本一致。

可用 JAVA_HOME 指定 JDK 21，GRADLE_BIN 指定 Gradle 可执行文件。
Android SDK 由 local.properties 或 ANDROID_HOME 配置。
需要 Python 3 读取 Gradle 产物版本；APK 固定输出到项目根目录 dist/Joyfin-版本号.apk。
日志、元数据和校验文件同样写入 dist/，重复构建覆盖同名文件。
HELP
}
fail() { printf '错误：%s\n' "$*" >&2; exit 1; }
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
offline=false
dry_run=false
variant=preview
gradle_task=assemblePreview
for arg in "$@"; do
    case "$arg" in
        --offline) offline=true ;;
        --dry-run) dry_run=true ;;
        --release) variant=release; gradle_task=assembleRelease ;;
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
command -v python3 >/dev/null 2>&1 || fail '需要 Python 3 读取 Gradle 产物版本'
if command -v shasum >/dev/null 2>&1; then
    checksum=(shasum -a 256)
elif command -v sha256sum >/dev/null 2>&1; then
    checksum=(sha256sum)
else
    fail '需要 shasum 或 sha256sum 来生成校验值'
fi
command_args=("$gradle_bin" ":app:$gradle_task" --console=plain)
if "$offline"; then command_args+=(--offline); fi
printf '目标：Android 9+ 手机 / TV 通用包（ARM32 / ARM64 / x86 / x86_64）\nJDK：%s\n命令：' "${JAVA_HOME:-PATH 中的 Java}"
printf '%q ' "${command_args[@]}"
printf '\n'
if "$dry_run"; then exit 0; fi
if [[ "$variant" == release ]]; then
    for name in JOYFIN_KEYSTORE_PATH JOYFIN_STORE_PASSWORD JOYFIN_KEY_ALIAS JOYFIN_KEY_PASSWORD; do
        [[ -n "${!name:-}" ]] || fail "正式构建缺少环境变量：$name"
    done
    [[ -s "$JOYFIN_KEYSTORE_PATH" ]] || fail '正式签名密钥文件不存在或为空'
fi

cd -- "$root"
output_dir="$root/dist"
mkdir -p "$output_dir"
printf '构建记录：%s/build.log\n' "$output_dir"
if ! "${command_args[@]}" 2>&1 | tee "$output_dir/build.log"; then
    fail "打包失败，日志保留在 $output_dir/build.log"
fi
source_apk="$root/app/build/outputs/apk/$variant/app-$variant.apk"
[[ -s "$source_apk" ]] || fail "Gradle 已结束，但未找到 $variant APK"
metadata="$root/app/build/outputs/apk/$variant/output-metadata.json"
if ! version_name="$(python3 - "$metadata" <<'PY'
import json
import os
import re
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    elements = json.load(source)["elements"]
if len(elements) != 1:
    raise SystemExit("预期一个通用 APK，无法确定产物版本")
version = elements[0]["versionName"]
if not isinstance(version, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", version):
    raise SystemExit("产物版本为空或包含不支持的文件名字符")
tag = os.environ.get("JOYFIN_RELEASE_TAG")
if tag and tag != "v" + version:
    raise SystemExit("发布标签与本次 Gradle 产物版本不一致")
print(version)
PY
)"; then
    fail "无法读取构建版本，日志保留在 $output_dir/build.log"
fi
apk_name="Joyfin-${version_name}.apk"
cp -- "$source_apk" "$output_dir/$apk_name"
cp -- "$metadata" "$output_dir/output-metadata.json"
(
    cd -- "$output_dir"
    "${checksum[@]}" "$apk_name" > SHA256SUMS.txt
)
printf '\n打包完成：%s/%s\n校验文件：%s/SHA256SUMS.txt\n' "$output_dir" "$apk_name" "$output_dir"
