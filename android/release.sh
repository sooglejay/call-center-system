#!/bin/bash

# Android App 自动打包并发布脚本
# 用法: ./release.sh [服务器地址]
# 示例: ./release.sh http://localhost:8081

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
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"

# 从 local.properties 读取默认服务器地址
parse_server_url_from_local_properties() {
    local local_props_file="local.properties"

    if [ ! -f "$local_props_file" ]; then
        echo -e "${YELLOW}警告: 找不到 $local_props_file${NC}"
        return 1
    fi

    # 提取 DEFAULT_SERVER_URL (去掉 /api/ 后缀)
    local full_url=$(grep -E 'DEFAULT_SERVER_URL\s*=\s*[^[:space:]]+' "$local_props_file" | cut -d'=' -f2 | tr -d ' ' | head -1)

    if [ -z "$full_url" ]; then
        return 1
    else
        # 去掉 /api/ 后缀，因为 API 路径会在请求时自动添加
        SERVER_URL=$(echo "$full_url" | sed 's|/api/$||; s|/api$||')
        return 0
    fi
}

# 显示帮助
show_help() {
    echo -e "${BLUE}Android App 自动打包发布脚本${NC}"
    echo ""
    echo "用法:"
    echo "  ./release.sh [服务器地址]"
    echo ""
    echo "参数:"
    echo "  服务器地址  可选，指定服务器地址（如 http://localhost:8081）"
    echo "              如果不指定，将从 local.properties 读取"
    echo ""
    echo "环境变量 (可选，用于覆盖默认配置):"
    echo "  SERVER_URL      服务器地址（优先级最高）"
    echo "  ADMIN_USERNAME  管理员账号 (默认: admin)"
    echo "  ADMIN_PASSWORD  管理员密码 (默认: admin123)"
    echo ""
    echo "示例:"
    echo "  ./release.sh                              # 从 local.properties 读取服务器地址"
    echo "  ./release.sh http://localhost:8081    # 指定服务器地址"
    echo "  SERVER_URL=http://example.com:8081 ./release.sh  # 使用环境变量"
}

# 解析 build.gradle.kts 获取版本号
parse_version_from_gradle() {
    local gradle_file="app/build.gradle.kts"

    if [ ! -f "$gradle_file" ]; then
        echo -e "${RED}错误: 找不到 $gradle_file${NC}"
        exit 1
    fi

    # 提取 versionCode
    VERSION_CODE=$(grep -E "versionCode\s*=\s*[0-9]+" "$gradle_file" | grep -oE "[0-9]+" | head -1)

    # 提取 versionName (支持双引号字符串)
    VERSION_NAME=$(grep -E "versionName\s*=\s*\"[^\"]+\"" "$gradle_file" | grep -oE '"[^"]+"' | tr -d '"' | head -1)

    if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
        echo -e "${RED}错误: 无法从 $gradle_file 解析版本号${NC}"
        echo "请确保文件中包含 versionCode = X 和 versionName = \"X.X.X\""
        exit 1
    fi
}

# 显示帮助
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    show_help
    exit 0
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    Android App 自动打包发布脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查必要文件
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo -e "${RED}错误: 签名密钥文件不存在: $KEYSTORE_FILE${NC}"
    exit 1
fi

# 从 build.gradle.kts 读取版本号
parse_version_from_gradle

# 确定服务器地址（优先级：环境变量 > 命令行参数 > local.properties）
if [ -n "$SERVER_URL" ]; then
    # 环境变量优先级最高
    echo -e "${YELLOW}使用环境变量指定的服务器地址:${NC} $SERVER_URL"
elif [ -n "$1" ]; then
    # 命令行参数
    SERVER_URL="$1"
    echo -e "${YELLOW}使用命令行指定的服务器地址:${NC} $SERVER_URL"
else
    # 从 local.properties 读取
    if parse_server_url_from_local_properties; then
        echo -e "${YELLOW}从 local.properties 读取服务器地址:${NC} $SERVER_URL"
    else
        echo -e "${RED}错误: 无法确定服务器地址${NC}"
        echo "请使用以下方式之一指定:"
        echo "  1. 设置 SERVER_URL 环境变量"
        echo "  2. 命令行参数: ./release.sh http://example.com:8081"
        echo "  3. 在 local.properties 中设置 DEFAULT_SERVER_URL"
        exit 1
    fi
fi

echo ""
echo -e "${YELLOW}版本信息 (从 build.gradle.kts 读取):${NC}"
echo "  版本号 (versionCode): $VERSION_CODE"
echo "  版本名称 (versionName): $VERSION_NAME"
echo -e "${YELLOW}服务器地址:${NC} $SERVER_URL"
echo ""

# 确认发布
echo -e "${YELLOW}是否继续发布? (y/N):${NC}"
read -r confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo -e "${YELLOW}已取消发布${NC}"
    exit 0
fi
echo ""

# 步骤 1: 清理项目
echo -e "${BLUE}[1/6] 清理项目...${NC}"
./gradlew clean --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}清理失败${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 清理完成${NC}"
echo ""

# 步骤 2: 构建 Release APK
echo -e "${BLUE}[2/6] 构建 Release APK...${NC}"
./gradlew assembleRelease --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}构建失败${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 构建完成${NC}"
echo ""

# 查找生成的 APK
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    # 尝试查找未签名版本
    UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
    if [ -f "$UNSIGNED_APK" ]; then
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
fi

# 获取 APK 信息
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo -e "${GREEN}✓ APK 文件: $APK_PATH ($APK_SIZE)${NC}"
echo ""

# 步骤 3: 登录获取 Token
echo -e "${BLUE}[3/6] 登录服务器...${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$SERVER_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo -e "${RED}登录失败，请检查账号密码${NC}"
    echo "响应: $LOGIN_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ 登录成功${NC}"
echo ""

# 步骤 4: 上传 APK
echo -e "${BLUE}[4/6] 上传 APK...${NC}"
UPLOAD_RESPONSE=$(curl -s -X POST "$SERVER_URL/api/version/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "apk=@$APK_PATH" \
    -F "version_code=$VERSION_CODE")

if echo "$UPLOAD_RESPONSE" | grep -q "error"; then
    echo -e "${RED}上传失败${NC}"
    echo "响应: $UPLOAD_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ 上传成功${NC}"
echo ""

# 步骤 5: 创建版本
echo -e "${BLUE}[5/6] 创建版本...${NC}"

# 读取更新日志
UPDATE_LOG=""
if [ -f "CHANGELOG.md" ]; then
    UPDATE_LOG=$(cat CHANGELOG.md)
elif [ -f "release-notes.txt" ]; then
    UPDATE_LOG=$(cat release-notes.txt)
fi

# 如果没有更新日志，使用默认内容
if [ -z "$UPDATE_LOG" ]; then
    UPDATE_LOG="版本 $VERSION_NAME 更新"
fi

# 转义 JSON 特殊字符（换行符、引号等）
UPDATE_LOG_ESCAPED=$(echo "$UPDATE_LOG" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\t/\\t/g' | awk '{printf "%s\\n", $0}' | sed '$ s/\\n$//')

# 创建版本
CREATE_RESPONSE=$(curl -s -X POST "$SERVER_URL/api/version/create" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{
        \"version_code\": $VERSION_CODE,
        \"version_name\": \"$VERSION_NAME\",
        \"update_log\": \"$UPDATE_LOG_ESCAPED\",
        \"force_update\": false,
        \"min_version_code\": $VERSION_CODE
    }")

if echo "$CREATE_RESPONSE" | grep -q "error"; then
    echo -e "${RED}创建版本失败${NC}"
    echo "响应: $CREATE_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ 版本创建成功${NC}"
echo ""

# 步骤 6: 完成
echo -e "${BLUE}[6/6] 发布完成!${NC}"
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}    发布成功!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "版本信息:"
echo "  版本号: $VERSION_CODE"
echo "  版本名称: $VERSION_NAME"
echo "  APK大小: $APK_SIZE"
echo ""
echo "客户端将在下次启动时自动检测更新。"
echo ""

exit 0
