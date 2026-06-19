# v1.0.0 - 初始版本发布

## 🎉 基于两模态LLM的语音聊天系统

首个正式版本发布！包含三个独立版本的技术方案对比测试。

---

## ✨ 新功能

### 🚗 道路交通信息智能问答助手
- 新增 **Skills模块**，定义"交通小智"助手身份
- 系统提示词支持路况查询、态势研判、调度辅助、数据问询
- 开场白设计，展示核心功能和使用示例
- 预留6个功能端口调用接口（`[CALL:功能名称]`）
- 道路交通知识库增强检索配置

### 🎙️ 语音交互
- 语音识别（ASR）：支持FunASR本地模型
- 语音合成（TTS）：支持edge-tts快速模式 / ChatTTS本地模式
- 流式TTS：边生成边朗读，提升用户体验
- 文本预处理：智能转换"-"和"~"为"到"

### 🤖 LLM对话
- 支持DeepSeek API（默认）
- 兼容mimo等OpenAI格式API
- 流式输出，实时显示回复

### 🎨 UI界面
- 交警卡通人物形象（SVG矢量图）
- 说话时嘴巴动画效果
- 思考/说话/空闲三种状态切换
- 响应式布局设计

---

## 📦 三个版本方案

| 版本 | ASR | TTS | 特点 |
|------|-----|-----|------|
| version_api | mimo API | mimo API | 无需GPU，依赖网络 |
| version_funasr_chattts | FunASR (本地) | edge-tts/ChatTTS | 中文最佳，推荐使用 |
| version_whisper_cosyvoice | Whisper (本地) | CosyVoice (本地) | 多语言，高音质 |

---

## 🔧 技术改进

### 安全性
- SECRET_KEY使用随机生成
- 调试模式默认关闭
- API Key不再泄露到日志

### 性能优化
- TTS响应时间从55秒降至3秒（使用edge-tts）
- 内存管理优化，限制对话历史长度
- 模型加载重试机制

### 代码质量
- 完整的日志系统（logging模块）
- 错误处理改进
- 代码审计和修复

---

## 📚 文档

- `版本说明.md` - 项目概述和使用说明
- `Git管理方法.md` - Git使用指南
- `代码审计报告.md` - 代码问题分析
- `代码修复总结.md` - 修复记录
- `开发日志.md` - 详细开发记录

---

## 🚀 快速开始

```bash
# 克隆项目
git clone https://github.com/555apex/multimodal-llm-voice-chat.git
cd multimodal-llm-voice-chat

# 进入版本2（推荐）
cd version_funasr_chattts/backend

# 安装依赖
pip install -r requirements.txt
pip install edge-tts funasr modelscope

# 配置API Key
cp .env.example .env
# 编辑 .env 文件

# 启动后端
python app.py

# 启动前端（新终端）
cd ../frontend
npm install
npm run dev
```

访问 http://localhost:3000

---

## ⚠️ 已知问题

- ChatTTS首次加载较慢（建议使用edge-tts）
- PyTorch与RTX 5070 Ti的CUDA兼容性警告（不影响功能）
- 需要NVIDIA GPU支持（建议8GB+显存）

---

## 📝 后续计划

- [ ] 接入实时路况数据API
- [ ] 实现功能端口实际调用
- [ ] 添加更多TTS语音选项
- [ ] 支持自定义说话人
- [ ] 多语言支持

---

**发布时间**: 2026年6月18日
**维护者**: 555apex
