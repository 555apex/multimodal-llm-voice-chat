# 交通小智 — 福建省道路交通智能语音助手（v3）

基于 LLM Agent + 实时路况 + 语音交互的福建省道路交通管理辅助系统。

## 架构

```
用户语音/文字
  → faster-whisper ASR（本地）
  → Flask + Socket.IO
  → Qwen3.7-Plus（DashScope, function calling）
  → 高德交通态势 API（矩形/圆形，实时路况）
  → Edge-TTS（微软免费引擎）
  → Vue3 + Leaflet 前端（地图可视化）
```

## v1 → v2 → v3 演进

| | v1 | v2 | **v3** |
|------|-----|-----|-----|
| **LLM** | DashScope Qwen | Qwen3.7 + Function Calling | ← |
| **ASR** | faster-whisper | ← | ← |
| **TTS** | Qwen-TTS API | ← | **Edge-TTS（本地）** |
| **路况** | mock 数据 | 高德 circle API | **矩形 API + 城市级要道过滤** |
| **地图** | 静态 WGS-84 | 高德 GCJ-02 瓦片 | **统一着色 + LLM 路名联动** |
| **Prompt** | 86 行硬编码 | `_guidance` 指令 | **`_key_facts` 事实摘要 + 数据保真约束** |
| **Skill** | 无 | BaseSkill + 路况 | **+ tts_strategy（可扩展）** |

## v3 核心特性

### 数据保真（LLM 不造假）
- `_key_facts` 机器生成事实摘要（如 `共30条。拥堵2条（镇海路、思明南路）；缓行5条…`）
- System Prompt 硬约束：状态必须与数据一致，禁止美化弱化
- temperature 降至 0.3

### 地图-文本联动
- 高亮数据从 API 提取（非 LLM 生成），polyline 精准对齐
- LLM 回复中提到的路名自动匹配 → 补充高亮
- 统一着色：畅通绿/缓行橙/拥堵红/严重拥堵深红

### 智能要道过滤
- 城市级查询自动过滤巷弄小道，只显示桥/高速/大道/路/街
- POI 级查询保留全部道路
- 规则驱动，不依赖 LLM 判断

### TTS 流式优化
- Edge-TTS 替代 Qwen-TTS：免费、低延迟（~200ms/句）、流式合成
- 动态分句阈值：后面有缓冲 → 6 字即发；后面空的 → 20 字才发
- 4 并行 worker，段间无间隔
- Markdown 表格行自动转自然语句

### 可扩展 Skill 架构
- Skill 自带 `tts_strategy`（朗读策略），新增业务只需配一份描述
- `build_highlights()` 从 API 数据生成地图高亮

## 启动

```bash
# 需要 conda 环境 llm-road
bash start.sh
```

- 前端：`http://localhost:3000`
- 后端：`http://localhost:5001`

## 目录

```
backend/
  app.py                     Flask + Socket.IO
  config.py                  环境变量
  api/websocket.py           WebSocket 事件 + TTS 调度 + LLM路名匹配
  services/
    llm_service.py           LLM 对话 + Function Calling + System Prompt
    asr_service.py           本地 faster-whisper
    tts_service.py           Edge-TTS
  skills/
    base.py                  Skill 基类
    traffic_status.py        路况查询（高德矩形/circle + 要道过滤 + TTS策略）
frontend/
  src/
    stores/chatStore.js      状态管理
    components/
      TrafficMap.vue         地图渲染
      ChatWindow.vue         对话
      InputArea.vue          语音/文字输入
```
