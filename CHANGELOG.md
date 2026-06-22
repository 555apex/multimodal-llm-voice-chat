# 闽路通 · 开发日志

## v2.0 — 高德实时路况 + Agent 智能化（2026-06-19）

### 架构概览

```
用户语音/文字
  → faster-whisper ASR（本地）
  → Flask + Socket.IO
  → Qwen3.7-Plus（DashScope, function calling）
  → 高德交通态势 API（实时路况）
  → Qwen-TTS（语音合成）
  → Vue3 + Leaflet 前端（地图可视化）
```

### 核心新增

#### 1. 高德实时路况集成
- 新增 `skills/` 插件化架构（BaseSkill 基类）
- 接入高德交通态势 API，分钟级路况更新
- **弹性区域查询**：一个 `area` 参数覆盖城市/区县/POI/道路四种粒度
  - 城市级：行政区划下属区县多点采样（保证沿海城市不落入海域）
  - 区县级：8km 圈
  - 兴趣点：1.5~2km 圈
  - 道路：1.5km 圈
- 地理编码 + POI 搜索双引擎，解决「厦门市政府」被误判为城市的问题
- 自适应半径：区县数量多时自动缩小单点半径

#### 2. Agent Function Calling
- Qwen 模型支持 tool calling，自动判断何时调高德 API
- 工具结果就绪后**立即推送前端地图**（不等文本流结束）
- 地图缩放锁定在查询圈边界（不被长 polyline 拖大）
- Skill 级 `_focus` 关键基础设施自动识别（桥梁/隧道/高速/主干道分层）
- Skill 级 `_guidance` 数据组织指令，业务优先级跟数据走不跟 prompt 走
- System prompt 从 86 行瘦身到 22 行，去除所有硬编码道路数据

#### 3. 地图视觉升级
- 高德 GCJ-02 瓦片（webrd + wprd 双源），与 API polyline 天然对齐
- 废弃 CartoDB/OSM WGS-84 瓦片，解决坐标系偏移
- 海洋蓝色 CSS 底色兜底瓦片空白区

#### 4. TTS 流式重构
- 重写为句子级流式：完整句即发，不做缓冲混合
- 首句最小长度合并（<20 字攒到下一句）
- Set 去重，消除旧版复杂状态机导致的重复播报

### 项目结构

```
backend/
  app.py                    Flask + Socket.IO 入口
  config.py                 环境变量配置
  api/websocket.py          Socket.IO 事件处理（流式对话 + TTS 调度）
  services/
    llm_service.py          千问 DashScope 流式对话 + function calling
    asr_service.py          本地 faster-whisper 语音识别
    tts_service.py          千问 TTS 语音合成
  skills/                   ⭐ 新增 Skill 插件化架构
    base.py                 基类（tool_definition / execute / lightweight / frontend_data）
    traffic_status.py       路况查询 Skill（地理编码 + POI 搜索 + 区县采样）
frontend/
  src/
    stores/chatStore.js     Pinia 状态管理（消息、地图数据、TTS 队列）
    components/
      TrafficMap.vue         Leaflet 地图（高德瓦片 + 实时 polyline 渲染）
      ChatWindow.vue         对话窗口
      InputArea.vue          语音/文字输入
      AudioControls.vue      TTS 开关与音量
      CharacterAvatar.vue    卡通形象
    data/
      mockRoadData.json      静态模拟数据（施工/灾害/设备）
      roadDataSource.js      数据源抽象层
```

### v1.0 初始版本（2026-06-19）

- 基于两模态 LLM 的语音聊天系统
- 三套技术方案：mimo ASR/TTS、FunASR+ChatTTS、Whisper+CosyVoice
- 最终选定：DashScope Qwen + faster-whisper + Qwen-TTS
- Leaflet 地图 + 静态 mock 路况数据
- 硬编码 system prompt（86 行道路列表 + 坐标参考）
- Vue3 + Pinia + Socket.IO 前后端架构
