# 🚔 闽路通 (MinLuTong) — 多模态语音交通助手 v2.0

> 基于大语言模型的福建省实时路况智能语音助手 —— 你说，它查，地图实时渲染。

## ✨ 核心功能

| 功能 | 说明 |
|---|---|
| 🎤 **语音输入** | 浏览器录音 → 本地 ASR（FunASR Paraformer）→ 中文识别 |
| 🧠 **智能对话** | 大模型自动理解意图，支持 Function Calling 自动调用路况查询工具 |
| 🗺️ **实时路况** | 对接高德地图 API，查询福建省9市实时交通拥堵状况，地图多色渲染 |
| 🔊 **语音播报** | TTS 引擎将回复转为语音，逐句流式播放（edge-tts / ChatTTS） |
| ✍️ **Markdown 渲染** | 回复支持富文本表格、KaTeX 数学公式、代码块高亮 |
| 🎭 **角色化形象** | 卡通交警 SVG 形象，根据状态（空闲/思考/说话）动态切换动画 |

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────────────┐
│                      浏览器前端 (:3000)                    │
│  Vue 3 + Pinia + Socket.IO + Leaflet (高德GCJ-02瓦片)     │
│  语音录入 → WAV → base64 → WebSocket → 后端               │
│  接收：文本流 + 音频流 + 地图数据 ← WebSocket ← 后端       │
└──────────────────────┬───────────────────────────────────┘
                       │  Socket.IO (WebSocket)
┌──────────────────────▼───────────────────────────────────┐
│                   Flask 后端 (:5001)                       │
│                                                          │
│  ASR 服务          LLM 服务            TTS 服务           │
│  (FunASR)    →    (DeepSeek/     →    (edge-tts/         │
│  语音→文字         Qwen/OpenAI)         ChatTTS)          │
│                      │                 文字→语音           │
│                      ▼                                    │
│              技能插件 (Skills)                             │
│          TrafficStatusSkill                               │
│          高德地图 API (4端点)                               │
│          · 地理编码  · POI搜索                             │
│          · 行政区划  · 实时路况圈                           │
└──────────────────────────────────────────────────────────┘
```

## 📁 项目结构

```
multimodal-llm-voice-chat-qwen-v2/
├── README.md                         # 本文件
├── CHANGELOG.md                      # 更新日志
├── Agent-process.md                  # 全链路数据架构文档
├── Interface-architecture.md         # 接口规范文档
├── start.sh                          # 一键启动脚本
├── assets/                           # 静态资源
│   └── avatar/fig1.jpg
├── backend/                          # Python 后端
│   ├── app.py                        # Flask + SocketIO 入口
│   ├── config.py                     # 配置中心（读取 .env）
│   ├── requirements.txt              # Python 依赖
│   ├── .env.example                  # 环境变量模板
│   ├── test_api.py                   # LLM API 连通性测试
│   ├── api/
│   │   └── websocket.py              # Socket.IO 事件处理（核心编排）
│   ├── services/
│   │   ├── asr_service.py            # 语音识别服务
│   │   ├── llm_service.py            # 大模型对话 + Function Calling
│   │   └── tts_service.py            # 语音合成服务
│   └── skills/
│       ├── base.py                   # 技能插件基类
│       └── traffic_status.py         # 高德实时路况查询技能
└── frontend/                         # Vue 3 前端
    ├── index.html
    ├── package.json
    ├── vite.config.js                # Vite 配置（代理 WebSocket）
    ├── public/
    │   └── avatar/police.svg         # 卡通交警 SVG
    └── src/
        ├── main.js                   # 入口
        ├── App.vue                   # 根组件（双栏布局）
        ├── stores/chatStore.js       # Pinia 状态管理 + Socket.IO
        ├── components/
        │   ├── ChatWindow.vue        # 对话窗口（Markdown 渲染）
        │   ├── InputArea.vue         # 文本/语音输入栏
        │   ├── TrafficMap.vue        # Leaflet 地图（路况覆盖）
        │   ├── CharacterAvatar.vue   # 角色动画形象
        │   └── AudioControls.vue     # 音量/自动朗读控制
        └── data/
            ├── mockRoadData.json     # 模拟路况数据
            └── roadDataSource.js     # 数据源抽象层
```

## 🚀 快速开始

### 环境要求

- **Python** ≥ 3.8
- **Node.js** ≥ 18
- **CUDA**（可选，用于 FunASR / ChatTTS GPU 加速）

### 1. 克隆项目

```bash
git clone https://github.com/555apex/multimodal-llm-voice-chat.git
cd multimodal-llm-voice-chat
```

### 2. 后端配置

```bash
cd backend

# 创建虚拟环境（推荐）
python -m venv venv
source venv/bin/activate   # Linux/Mac
# venv\Scripts\activate    # Windows

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env 填入你的 API Key
```

**.env 关键配置：**

```env
# LLM 提供商: deepseek / dashscope / openai
LLM_PROVIDER=deepseek
LLM_API_KEY=your-api-key-here
LLM_MODEL=deepseek-chat

# 高德地图 API Key (必填)
GAODE_API_KEY=your-gaode-api-key

# TTS 引擎: edge-tts (在线) / chattts (本地GPU)
TTS_ENGINE=edge-tts

# ASR 设备: cuda / cpu
ASR_DEVICE=cpu
```

### 3. 前端配置

```bash
cd frontend

npm install
```

### 4. 启动

**方式一：一键启动**

```bash
bash start.sh
```

**方式二：分别启动**

```bash
# 终端1 - 后端
cd backend && python app.py

# 终端2 - 前端
cd frontend && npx vite --port 3000
```

浏览器打开 `http://localhost:3000`，即可使用。

## 🔌 API / 接口

所有通信通过 **Socket.IO**（WebSocket）进行，主要事件：

| 事件 | 方向 | 说明 |
|---|---|---|
| `message` | 前端→后端 | 发送文本 `{type:"text", content}` 或语音 `{type:"audio", content:base64}` |
| `text_chunk` | 后端→前端 | LLM 流式文本片段 |
| `text_complete` | 后端→前端 | 回复完成（已过滤 JSON 块） |
| `audio` | 后端→前端 | TTS 语音片段 `data:audio/wav;base64` |
| `asr_result` | 后端→前端 | 语音识别结果 |
| `traffic_data` | 后端→前端 | 实时路况数据（路径坐标 + 路况摘要） |
| `greeting` | 后端→前端 | 欢迎语 |
| `error` | 后端→前端 | 错误信息 |

REST 接口：`GET /health` — 健康检查

## 🗺️ 路况查询机制

1. 用户语音/文字询问路况（如「厦门市路况怎么样」）
2. LLM 自动调用 `query_traffic` 工具函数
3. 后端通过高德 API 查询：
   - **城市级**：查询所有行政区，去重合并
   - **单点**：POI/区县/道路，按半径查询
4. 路况数据**分叉**：
   - **轻量版**（无 polyline）→ 给 LLM 生成自然语言回复
   - **完整版**（含 polyline）→ 直接推送前端地图渲染
5. 地图色标：🟢 畅通 → 🟠 缓行 → 🔴 拥堵 → 🔴 严重拥堵

## 🔧 配置参考

完整配置项见 `backend/config.py`：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `LLM_PROVIDER` | `deepseek` | LLM 提供商 |
| `LLM_API_BASE_URL` | `https://api.deepseek.com` | API 地址 |
| `LLM_MODEL` | `deepseek-chat` | 模型名称 |
| `GAODE_API_KEY` | — | 高德 Web 服务 API Key |
| `TTS_ENGINE` | `edge-tts` | edge-tts（在线）/ chattts（本地） |
| `ASR_MODEL` | `iic/speech_paraformer-large-...` | FunASR 模型 |
| `ASR_DEVICE` | `cuda` | cuda / cpu |
| `PORT` | `5001` | 后端端口 |

## 📄 许可证

本项目仅供学习和研究使用。

---

**Made with ❤️ for Fujian Traffic** 🚗
