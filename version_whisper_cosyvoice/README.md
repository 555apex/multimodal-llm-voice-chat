# 基于两模态LLM的语音聊天系统 - 本地部署版 (Whisper + CosyVoice)

## 版本特点

- ✅ **ASR 本地部署**：使用 OpenAI Whisper (large-v3)，多语言识别效果优秀
- ✅ **TTS 本地部署**：使用 CosyVoice，音质高、多语言支持
- ✅ **多语言支持**：支持中英文及多种语言
- ✅ **高音质**：CosyVoice 生成的语音质量高

## 硬件要求

| 组件 | 最低配置 | 推荐配置 |
|------|----------|----------|
| GPU | NVIDIA 8GB 显存 | NVIDIA 16GB+ 显存 |
| 内存 | 16GB | 32GB |
| 硬盘 | 15GB 可用空间 | 30GB+ |

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

# 安装 Whisper
pip install openai-whisper

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
- Whisper large-v3 (~3GB)
- CosyVoice (~4-5GB)

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
│  │ Whisper     │  │ mimo API    │  │ CosyVoice   │     │
│  │ (本地GPU)   │  │ (云端)      │  │ (本地GPU)   │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
└─────────────────────────────────────────────────────────┘
```

---

## 模型说明

### ASR - Whisper (large-v3)

- **模型**：`large-v3`
- **特点**：多语言支持，识别准确率高
- **显存**：~3GB
- **支持语言**：99 种语言
- **文档**：https://github.com/openai/whisper

### TTS - CosyVoice

- **模型**：`CosyVoice-300M`
- **特点**：高音质、多语言支持
- **显存**：~4-5GB
- **支持语言**：中英文
- **文档**://github.com/FunAudioLLM/CosyVoice

---

## 与方案1的对比

| 特性 | 方案1 (FunASR+ChatTTS) | 本方案 (Whisper+CosyVoice) |
|------|------------------------|---------------------------|
| ASR 中文效果 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| ASR 多语言 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| TTS 自然度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| TTS 音质 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 显存占用 | ~4GB | ~8GB |
| 推理速度 | ⚡ 快 | 中等 |

---

## 常见问题

### Q: 首次启动很慢？
A: 首次启动需要下载模型文件，总共约 7-8GB，请耐心等待。

### Q: 显存不足？
A: Whisper 和 CosyVoice 都支持 CPU 模式，但会慢很多。可以尝试：
1. 关闭其他占用显存的程序
2. 使用 Whisper 的 smaller 模型（如 medium）

### Q: 如何切换 Whisper 模型大小？
A: 在 `backend/config.py` 中修改 `ASR_MODEL`，可选：`tiny`, `base`, `small`, `medium`, `large-v3`

---

## 许可证

MIT License
