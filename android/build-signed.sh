#!/bin/bash

# Android App 签名包构建脚本
# 用法: ./build-signed.sh
# 仅构建签名 APK，不发布到服务器

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
KEYSTORE_FILE="release-key.jks"
KEYSTORE_PASSWORD="callcenter123"
KEY_ALIAS="callcenter"
KEY_PASSWORD="callcenter123"

# 从 local.properties 读取版本号
parse_version_from_local_properties() {
    local local_props_file="local.properties"

    if [ ! -f "$local_props_file" ]; then
        echo -e "${RED}错误: 找不到 $local_props_file${NC}"
        exit 1
    fi

    # 提取 VERSION_CODE
    VERSION_CODE=$(grep -E 'VERSION_CODE\s*=\s*[0-9]+' "$local_props_file" | cut -d'=' -f2 | tr -d ' ' | head -1)

    # 提取 VERSION_NAME
    VERSION_NAME=$(grep -E 'VERSION_NAME\s*=\s*[^[:space:]]+' "$local_props_file" | cut -d'=' -f2 | tr -d ' ' | head -1)

    if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
        echo -e "${RED}错误: 无法从 $local_props_file 解析版本号${NC}"
        echo "请确保文件中包含 VERSION_CODE 和 VERSION_NAME"
        exit 1
    fi
}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    Android App 签名包构建脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查必要文件
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo -e "${RED}错误: 签名密钥文件不存在: $KEYSTORE_FILE${NC}"
    exit 1
fi

# 从 local.properties 读取版本号
parse_version_from_local_properties

echo -e "${YELLOW}版本信息:${NC}"
echo "  版本号 (versionCode): $VERSION_CODE"
echo "  版本名称 (versionName): $VERSION_NAME"
echo ""

# 步骤 1: 清理项目
echo -e "${BLUE}[1/3] 清理项目...${NC}"
./gradlew clean --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}清理失败${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 清理完成${NC}"
echo ""

# 步骤 2: 构建 Release APK
echo -e "${BLUE}[2/3] 构建 Release APK...${NC}"
./gradlew assembleRelease --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}构建失败${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 构建完成${NC}"
echo ""

# 步骤 3: 检查并签名 APK
echo -e "${BLUE}[3/3] 检查 APK 签名...${NC}"

# 查找生成的 APK
APK_PATH="app/build/outputs/apk/release/app-release.apk"
UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"

if [ -f "$APK_PATH" ]; then
    echo -e "${GREEN}✓ 已找到签名 APK${NC}"
elif [ -f "$UNSIGNED_APK" ]; then
    echo -e "${YELLOW}发现未签名 APK，进行签名...${NC}"

    # 尝试查找 apksigner 和 zipalign
    APK_SIGNER=$(find $ANDROID_HOME/build-tools -name "apksigner" -type f 2>/dev/null | sort -V | tail -1)
    ZIP_ALIGN=$(find $ANDROID_HOME/build-tools -name "zipalign" -type f 2>/dev/null | sort -V | tail -1)

    if [ -n "$APK_SIGNER" ] && [ -n "$ZIP_ALIGN" ]; then
        echo -e "${GREEN}使用 apksigner 进行 v2 签名: $APK_SIGNER${NC}"

        # 第一步: 使用 jarsigner 进行 v1 签名
        echo -e "${BLUE}  -> v1 签名...${NC}"
        jarsigner -sigalg SHA256withRSA -digestalg SHA256 \
            -keystore "$KEYSTORE_FILE" \
            -storepass "$KEYSTORE_PASSWORD" \
            -keypass "$KEY_PASSWORD" \
            -signedjar "$APK_PATH" \
            "$UNSIGNED_APK" \
            "$KEY_ALIAS" 2>&1 | grep -E "(signed|warning|error)" || true

        # 第二步: 对齐 APK
        echo -e "${BLUE}  -> zipalign 对齐...${NC}"
        ALIGNED_APK="app/build/outputs/apk/release/app-release-aligned.apk"
        "$ZIP_ALIGN" -f -v 4 "$APK_PATH" "$ALIGNED_APK" 2>&1 | grep -E "(Verifying|Alignment)" || true
        mv "$ALIGNED_APK" "$APK_PATH"

        # 第三步: 使用 apksigner 进行 v2 签名
        echo -e "${BLUE}  -> v2 签名...${NC}"
        "$APK_SIGNER" sign --ks "$KEYSTORE_FILE" \
            --ks-pass pass:"$KEYSTORE_PASSWORD" \
            --key-pass pass:"$KEY_PASSWORD" \
            --ks-key-alias "$KEY_ALIAS" \
            --out "$APK_PATH" \
            "$APK_PATH" 2>&1 | grep -v "^  " || true
    else
        # 降级使用 jarsigner 进行 v1 签名
        echo -e "${YELLOW}未找到 apksigner/zipalign，使用 jarsigner 进行 v1 签名...${NC}"
        echo -e "${YELLOW}提示: 安装 Android SDK Build-Tools 以获得更好的兼容性${NC}"
        jarsigner -sigalg SHA256withRSA -digestalg SHA256 \
            -keystore "$KEYSTORE_FILE" \
            -storepass "$KEYSTORE_PASSWORD" \
            -keypass "$KEY_PASSWORD" \
            -signedjar "$APK_PATH" \
            "$UNSIGNED_APK" \
            "$KEY_ALIAS"
    fi

    if [ $? -ne 0 ]; then
        echo -e "${RED}签名失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ 签名完成${NC}"
else
    echo -e "${RED}错误: 找不到 APK 文件${NC}"
    exit 1
fi

# 获取 APK 信息
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}    构建成功!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "版本信息:"
echo "  版本号: $VERSION_CODE"
echo "  版本名称: $VERSION_NAME"
echo "  APK路径: $APK_PATH"
echo "  APK大小: $APK_SIZE"
echo ""

# 可选：复制到项目根目录的 release 目录
RELEASE_DIR="release"
mkdir -p "$RELEASE_DIR"
FINAL_APK="$RELEASE_DIR/app-v$VERSION_NAME.apk"
cp "$APK_PATH" "$FINAL_APK"
echo -e "${GREEN}✓ 已复制到: $FINAL_APK${NC}"
echo ""

exit 0
