# 闽路通 — 福建省公路交通智能语音助手（v2）

基于 LLM Agent + 高德实时路况 + 语音交互的福建省公路交通管理辅助系统。

## 架构

```
用户语音/文字
  → faster-whisper ASR（本地）
  → Flask + Socket.IO
  → Qwen3.7-Plus（DashScope, function calling）
  → 高德交通态势 API（实时路况）
  → Qwen-TTS（DashScope API）
  → Vue3 + Leaflet 前端（地图可视化）
```

## v1 → v2 演进

| | v1 | **v2** |
|------|-----|-----|
| **路况** | 静态 mock 数据 | **高德实时 API（circle 多点采样）** |
| **Agent** | 无 | **Function Calling（LLM 自主判断调 API）** |
| **地图** | WGS-84 瓦片，坐标偏移 | **高德 GCJ-02 瓦片，polyline 精准对齐** |
| **Prompt** | 86 行硬编码道路列表 | 22 行 + `_guidance` 指令动态组织 |
| **Skill** | 无 | **BaseSkill 插件化架构** |

## v2 核心特性

- **弹性区域查询**：一个 area 参数覆盖城市/区县/POI/道路四种粒度，自适应半径
- **城市级多点采样**：区县中心采样 + 去重合并，保证沿海城市覆盖
- **地图分层渲染**：mock 底色（施工/灾害/设备）+ API 实时路况线 + LLM 高亮标记
- **TTS 流式分句**：句末标点切分，首句 <20 字合并，2 并行 worker
- **Skill 级 `_focus`**：自动识别桥梁/隧道/高速/主干道，分组输出
- **Skill 级 `_guidance`**：数据组织指令随业务走，不依赖 System Prompt

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
  api/websocket.py           WebSocket 事件 + TTS 调度
  services/
    llm_service.py           LLM 对话 + Function Calling
    asr_service.py           本地 faster-whisper
    tts_service.py           Qwen-TTS（DashScope API）
  skills/
    base.py                  Skill 基类
    traffic_status.py        路况查询（高德 circle + 区县采样 + _focus _guidance）
    traffic_assistant.py     人设配置文件（旧版）
frontend/
  src/
    stores/chatStore.js      状态管理
    components/
      TrafficMap.vue         地图渲染（mock 匹配 + 实时路况）
      ChatWindow.vue         对话
      InputArea.vue          语音/文字输入
```
