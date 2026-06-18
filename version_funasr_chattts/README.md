# 基于两模态LLM的语音聊天系统 - 本地部署版 (FunASR + ChatTTS)

## 版本特点

- ✅ **ASR 本地部署**：使用 FunASR (Paraformer-zh)，中文识别效果优秀
- ✅ **TTS 本地部署**：使用 ChatTTS，对话自然度高
- ✅ **低延迟**：本地推理，响应速度快
- ✅ **隐私安全**：语音数据不离开本地

## 硬件要求

| 组件 | 最低配置 | 推荐配置 |
|------|----------|----------|
| GPU | NVIDIA 8GB 显存 | NVIDIA 16GB+ 显存 |
| 内存 | 16GB | 32GB |
| 硬盘 | 10GB 可用空间 | 20GB+ |

## 快速开始

### 1. 环境要求

- Python >= 3.9
- Node.js >= 16.x
- NVIDIA GPU (支持 CUDA)
- mimo 开放平台 API Key

### 2. 安装后端依赖

```bash
cd backend

# 创建虚拟环境 (推荐)
python -m venv venv
venv\Scripts\activate

# 安装 PyTorch (CUDA 12.1)
pip install torch torchaudio --index-url https://download.pytorch.org/whl/cu121

# 安装其他依赖
pip install -r requirements.txt
```

### 3. 安装前端依赖

```bash
cd frontend
npm install
```

### 4. 配置 API Key

```bash
cp backend/.env.example backend/.env
# 编辑 .env 文件，填入 MIMO_API_KEY
```

### 5. 启动服务

**启动后端：**
```bash
cd backend
python app.py
```

首次启动会自动下载模型：
- FunASR Paraformer-zh (~1GB)
- ChatTTS (~2-3GB)

**启动前端：**
```bash
cd frontend
npm run dev
```

### 6. 访问应用

打开浏览器访问 `http://localhost:3000`

---

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Vue 3)                          │
└─────────────────────────┬───────────────────────────────┘
                          │ WebSocket
┌─────────────────────────┴───────────────────────────────┐
│                    后端 (Flask)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │ FunASR      │  │ mimo API    │  │ ChatTTS     │     │
│  │ (本地GPU)   │  │ (云端)      │  │ (本地GPU)   │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
└─────────────────────────────────────────────────────────┘
```

---

## 模型说明

### ASR - FunASR (Paraformer-zh)

- **模型**：`iic/speech_paraformer-large-vad-punc_asr_nat-zh-cn-16k-common-vocab8404-pytorch`
- **特点**：中文识别效果优秀，支持标点和断句
- **显存**：~1GB
- **文档**：https://github.com/modelscope/FunASR

### TTS - ChatTTS

- **模型**：`2Noise/ChatTTS`
- **特点**：对话场景优化，自然度高，支持笑声/停顿
- **显存**：~2-3GB
- **文档**：https://github.com/2noise/ChatTTS

---

## 常见问题

### Q: 首次启动很慢？
A: 首次启动需要下载模型文件，总共约 3-4GB，请耐心等待。

### Q: 提示 CUDA 不可用？
A: 请确保安装了正确版本的 PyTorch，并且 NVIDIA 驱动正常。

### Q: 显存不足？
A: 可以尝试关闭其他占用显存的程序，或使用 CPU 模式（会慢很多）。

---

## 许可证

MIT License
