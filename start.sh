#!/bin/bash
# ==========================================
#  闽路通 v2.0 — 一键启动脚本
# ==========================================

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

cleanup() {
    echo -e "\n${RED}正在停止服务...${NC}"
    kill $BACKEND_PID 2>/dev/null
    kill $FRONTEND_PID 2>/dev/null
    wait $BACKEND_PID 2>/dev/null
    wait $FRONTEND_PID 2>/dev/null
    echo -e "${GREEN}已停止${NC}"
    exit 0
}
trap cleanup INT TERM

# ── 后端 ──
echo -e "${CYAN}[1/2] 启动后端 (Flask + Socket.IO, 端口 5001)...${NC}"
cd "$BACKEND"
# 使用 llm-road conda 环境
PYTHON="$(conda info --base)/envs/llm-road/bin/python"
PIP="$(conda info --base)/envs/llm-road/bin/pip"
$PIP install -r requirements.txt -q 2>/dev/null || true
$PYTHON app.py &
BACKEND_PID=$!
sleep 2

if ! kill -0 $BACKEND_PID 2>/dev/null; then
    echo -e "${RED}后端启动失败，请检查 backend/.env 配置${NC}"
    exit 1
fi
echo -e "${GREEN}后端已启动 (PID: $BACKEND_PID)${NC}"

# ── 前端 ──
echo -e "${CYAN}[2/2] 启动前端 (Vite, 端口 3000)...${NC}"
cd "$FRONTEND"
# 自动安装依赖（如需要）
if [ ! -d "node_modules" ]; then
    echo "  安装前端依赖..."
    npm install
fi
npx vite --port 3000 &
FRONTEND_PID=$!
sleep 3

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  闽路通 v2.0 已就绪${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "  前端: ${CYAN}http://localhost:3000/${NC}"
echo -e "  后端: ${CYAN}http://localhost:5001/${NC}"
echo -e "  按 ${RED}Ctrl+C${NC} 停止所有服务"
echo -e "${GREEN}========================================${NC}"

# 自动打开浏览器
if command -v open &>/dev/null; then
    sleep 1
    open http://localhost:3000/
elif command -v xdg-open &>/dev/null; then
    sleep 1
    xdg-open http://localhost:3000/
fi

# 等待
wait $BACKEND_PID $FRONTEND_PID
