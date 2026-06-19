# 基于两模态LLM的语音聊天系统（Qwen适配版）

一个支持文本和语音两种输入模态的智能聊天系统，具备语音识别、大模型推理、语音合成和卡通人物口型动画。

**本分支** (`version/qwen-v1`) 将原版 mimo-v2.5 系列模型替换为阿里云 DashScope + 本地 Whisper 方案。

## 功能特性

- 🎤 **语音输入**：点击麦克风按钮录音，自动识别为文本
- ⌨️ **文本输入**：键盘输入文字消息
- 🤖 **智能对话**：基于 Qwen-plus 大模型，角色为「闽路通」福建省公路交通智能助手
- 🔊 **语音朗读**：自动朗读回复内容（默认开启，Qwen-TTS Cherry 音色）
- 👤 **卡通人物**：带口型动画的虚拟形象
- 🎛️ **音量控制**：可调节朗读音量

## 技术栈

| 组件 | 原版 (master) | Qwen适配版 (本分支) |
|------|--------------|-------------------|
| 前端 | Vue 3 + Vite + Socket.IO Client | 不变 |
| 后端 | Python Flask + Flask-SocketIO | 不变 |
| ASR | mimo-v2.5-asr（云端） | **faster-whisper small（本地）** |
| TTS | mimo-v2.5-tts（云端） | **Qwen-TTS (DashScope)** |
| LLM | mimo-v2.5-pro（云端） | **Qwen-plus (DashScope OpenAI兼容)** |

## 快速开始

### 1. 环境要求

- Node.js >= 16.x
- Python >= 3.9
- 阿里云 DashScope API Key

### 2. 安装依赖

**前端：**
```bash
cd frontend
npm install
```

**后端：**
```bash
cd backend
pip install -r requirements.txt
```

### 3. 配置 API Key

复制 `backend/.env.example` 为 `backend/.env`，填入你的 DashScope API Key：

```bash
cp backend/.env.example backend/.env
```

编辑 `.env` 文件：
```
DASHSCOPE_API_KEY=your_api_key_here
```

可选配置：
```
DASHSCOPE_LLM_MODEL=qwen-plus   # LLM 模型，默认 qwen-plus
WHISPER_MODEL_SIZE=small         # Whisper 模型大小，默认 small
PORT=5000                        # 服务端口
```

### 4. 启动服务

**启动后端：**
```bash
cd backend
python app.py
```

**启动前端：**
```bash
cd frontend
npm run dev
```

### 5. 访问应用

打开浏览器访问 `http://localhost:3000`

## 项目结构

```
基于两模态llm的语音聊天系统/
├── frontend/                  # 前端 Vue 项目
│   ├── src/
│   │   ├── components/        # Vue 组件
│   │   ├── composables/       # 组合式函数
│   │   ├── stores/            # 状态管理
│   │   └── assets/            # 静态资源
│   └── package.json
│
├── backend/                   # 后端 Flask 项目
│   ├── api/                   # WebSocket 处理
│   ├── services/              # ASR/LLM/TTS 服务
│   │   ├── asr_service.py     # faster-whisper 本地推理
│   │   ├── llm_service.py     # DashScope Qwen-plus (OpenAI兼容)
│   │   └── tts_service.py     # DashScope Qwen-TTS
│   ├── config.py              # 配置文件
│   └── app.py                 # 主应用入口
│
├── audio_wav/                 # 音频文件存储
│   ├── user_query/            # 用户语音输入
│   └── llm_answer/            # LLM 语音输出
│
└── assets/                    # 资源文件
    └── avatar/                # 卡通人物素材
```

## 与原版的主要差异

1. **LLM 切换**：mimo-v2.5-pro → DashScope Qwen-plus，内置「闽路通」交通助手 System Prompt
2. **ASR 切换**：mimo-v2.5-asr 云端API → faster-whisper small 本地推理（无需网络，隐私性好）
3. **TTS 切换**：mimo-v2.5-tts → DashScope Qwen-TTS（Cherry 音色）
4. **流式输出已修复**：LLM 回复支持流式逐字展示

## 使用说明

1. **文本对话**：在输入框输入文字，点击"发送"或按回车键
2. **语音对话**：点击麦克风按钮开始录音，再次点击停止录音
3. **音量调节**：拖动音量滑块调整朗读音量
4. **朗读开关**：点击"朗读"按钮开启/关闭自动朗读

## 注意事项

- 需要有效的阿里云 DashScope API Key
- 首次启动后端会自动下载 faster-whisper small 模型（约 2GB）
- 首次使用需允许浏览器访问麦克风权限
- 建议使用 Chrome 或 Edge 浏览器

## 许可证

MIT License
