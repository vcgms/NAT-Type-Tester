#!/bin/bash
set -e

# 环境变量
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "$0")/NatTypeTester-Android" && pwd)"
GRADLE_CACHE="$HOME/.gradle/wrapper/dists/gradle-8.13-bin"

# 查找本地缓存的 Gradle
GRADLE_HOME=$(find "$GRADLE_CACHE" -maxdepth 2 -name "gradle-8.13" -type d 2>/dev/null | head -1)
GRADLE_CMD=""

if [ -n "$GRADLE_HOME" ] && [ -f "$GRADLE_HOME/bin/gradle" ]; then
    GRADLE_CMD="$GRADLE_HOME/bin/gradle"
    echo "使用本地缓存的 Gradle: $GRADLE_HOME"
else
    echo "未找到本地 Gradle 缓存，尝试使用 gradlew..."
    GRADLE_CMD="$PROJECT_DIR/gradlew"
fi

cd "$PROJECT_DIR"

echo ""
echo "=== 环境信息 ==="
echo "JAVA_HOME: $JAVA_HOME"
echo "ANDROID_HOME: $ANDROID_HOME"
echo "项目路径: $PROJECT_DIR"
echo "Gradle: $GRADLE_CMD"
echo ""

echo "=== 开始编译 ==="
"$GRADLE_CMD" assembleRelease \
    -Dorg.gradle.jvmargs="-Xmx2048m" \
    --no-daemon \
    --console=plain \
    2>&1

echo ""
echo "=== 编译完成 ==="

# 工作区根目录（PROJECT_DIR 的上级目录）
WORKSPACE_DIR="$(dirname "$PROJECT_DIR")"

# 从 build.gradle.kts 读取版本号
APP_NAME="NatTypeTester"
VERSION_NAME=$(grep 'versionName' "$PROJECT_DIR/app/build.gradle.kts" | head -1 | sed 's/.*"\(.*\)".*/\1/')

# Release 未签名 APK 路径
APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"

# 查找 apksigner
APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner -type f 2>/dev/null | sort -r | head -1)
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"

if [ -f "$APK" ]; then
    OUTPUT_NAME="${APP_NAME}-${VERSION_NAME}.apk"
    OUTPUT_PATH="$PROJECT_DIR/app/build/outputs/apk/release/$OUTPUT_NAME"
    rm -f "$OUTPUT_PATH"

    # 对未签名 APK 从零签 v1+v2（Debug 构建只签 v2，某些安装方式需要 v1）
    if [ -n "$APKSIGNER" ] && [ -f "$DEBUG_KEYSTORE" ]; then
        cp "$APK" "$OUTPUT_PATH"
        "$APKSIGNER" sign \
            --ks "$DEBUG_KEYSTORE" \
            --ks-pass pass:android \
            --ks-key-alias androiddebugkey \
            --key-pass pass:android \
            --v1-signing-enabled true \
            --v2-signing-enabled true \
            "$OUTPUT_PATH" 2>&1
        echo "✅ APK 已 v1+v2 签名: $OUTPUT_PATH"
    else
        cp "$APK" "$OUTPUT_PATH"
        echo "⚠️ 未找到 apksigner，APK 未签名: $OUTPUT_PATH"
    fi

    # 只拷贝 APK 到工作区根目录，不拷贝 .idsig 签名文件
    rm -f "$WORKSPACE_DIR/$OUTPUT_NAME"
    cp "$OUTPUT_PATH" "$WORKSPACE_DIR/$OUTPUT_NAME"
    echo "📦 已输出: $WORKSPACE_DIR/$OUTPUT_NAME"
else
    echo "❌ 未找到 APK，请检查上方编译错误"
fi
