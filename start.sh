#!/bin/bash
# 语音聊天系统一键启动脚本

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

cleanup() {
    echo ""
    echo "正在关闭服务..."
    kill $BACKEND_PID 2>/dev/null
    kill $FRONTEND_PID 2>/dev/null
    lsof -ti:5001 | xargs kill -9 2>/dev/null
    lsof -ti:3000 | xargs kill -9 2>/dev/null
    echo "已全部关闭。"
    exit 0
}

trap cleanup SIGINT SIGTERM

echo "════════════════════════════════"
echo "  语音聊天系统启动中..."
echo "════════════════════════════════"

# 启动后端
cd "$SCRIPT_DIR/backend"
python app.py &
BACKEND_PID=$!

# 启动前端
cd "$SCRIPT_DIR/frontend"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "后端: http://localhost:5001"
echo "前端: http://localhost:3000"
echo "按 Ctrl+C 关闭所有服务"
echo ""

# 等前端启动后自动打开浏览器
sleep 2
open http://localhost:3000

wait
