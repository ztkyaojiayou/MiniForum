#!/usr/bin/env bash
# MiniForum 启动脚本（Linux / macOS）
# 用法：
#   ./scripts/start.sh          # 直接运行（前台）
#   ./scripts/start.sh --build  # 先 mvn package 再运行
set -e
cd "$(dirname "$0")/.."

JAR="target/mini-forum-1.0.0.jar"

if [ ! -f "$JAR" ]; then
    echo "[MiniForum] 未找到 $JAR，先执行构建..."
    mvn package -DskipTests
fi

if [ "$1" == "--build" ]; then
    echo "[MiniForum] 重新构建..."
    mvn package -DskipTests
fi

echo "[MiniForum] 启动中... 访问 http://localhost:8090"
exec java -jar "$JAR"
