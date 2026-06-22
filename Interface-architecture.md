# 闽路通 · 接口与数据格式规范

> 本文档面向甲方技术对接：定义系统中每一个数据交互环节的通讯协议、数据格式和示例。
> 明确哪些是外部 API 标准（不可改）、哪些是系统内部格式（甲方可替换）、哪些是自定义事件格式。

---

## 1. 系统边界总览

```
┌──────────────────────────────────────────────────────────────────┐
│                        前端 (Vue3 + Leaflet)                       │
│  浏览器录音 → MediaRecorder API                                   │
│  地图渲染   → L.polyline([[lat,lng],...])                          │
│  语音播放   → new Audio(url)                                      │
└────────────┬──────────────────────────────────────┬──────────────┘
             │ Socket.IO (WebSocket)                │
             │ 全部事件 payload: JSON                │
             │                                      │
┌────────────▼──────────────────────────────────────▼──────────────┐
│                      后端 (Flask + Socket.IO)                     │
│                                                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐    │
│  │ ASR      │  │ LLM      │  │ TTS      │  │ Skill         │    │
│  │ faster-  │  │ DashScope│  │ DashScope│  │ 高德 API      │    │
│  │ whisper  │  │ 千问     │  │ Qwen-TTS │  │ (4个端点)     │    │
│  └──────────┘  └──────────┘  └──────────┘  └───────────────┘    │
│       ↑             ↑             ↑              ↑               │
└───────┼─────────────┼─────────────┼──────────────┼───────────────┘
        │             │             │              │
   本地模型       HTTP POST     HTTP POST     HTTP GET
   (无网络)      JSON body     JSON body     Query String
                 流式 SSE        JSON 响应      JSON 响应
```

---

## 2. 前端 ↔ 后端 (Socket.IO 事件)

**协议**: Socket.IO over WebSocket
**地址**: `ws://host:5001/socket.io/`
**所有 payload**: **JSON**

### 2.1 前端 → 后端

#### 文字消息

```json
{
  "type": "text",
  "content": "厦门市路况怎么样",
  "auto_read": true,
  "volume": 0.8
}
```

#### 语音消息

```json
{
  "type": "audio",
  "content": "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=",
  "format": "wav",
  "auto_read": true,
  "volume": 0.8
}
```

| 字段          | 类型                     | 说明                         |
| ------------- | ------------------------ | ---------------------------- |
| `type`      | `"text"` / `"audio"` | 消息类型                     |
| `content`   | string                   | 文本内容 / base64 编码的 WAV |
| `format`    | string                   | 仅 audio:`"wav"`           |
| `auto_read` | bool                     | 是否启用 TTS 朗读            |
| `volume`    | number                   | 0.0~1.0                      |

### 2.2 后端 → 前端

#### 流式文本 (逐 chunk)

```json
{"content": "厦门市路网", "is_final": false}
{"content": "整体平稳", "is_final": false}
```

#### 文本完成

```json
{
  "content": "厦门市路网整体平稳。跨海通道方面：海沧大桥、翔安隧道均畅通。其余路段通行正常。"
}
```

#### 路况地图数据

```json
{
  "city": "厦门市",
  "summary": "共 68 条道路，其中拥堵 2 条、缓行 12 条、畅通 54 条",
  "center": [24.479627, 118.08891],
  "query_radius": 30000,
  "roads": [
    {
      "name": "成功大道",
      "status": "畅通",
      "level": "low",
      "speed": "55 km/h",
      "direction": "从南向北",
      "polyline": [[24.485469, 118.133502], [24.4860, 118.1340]]
    }
  ]
}
```

| 字段                  | 类型                                | 说明                           |
| --------------------- | ----------------------------------- | ------------------------------ |
| `city`              | string                              | 区域标签                       |
| `summary`           | string                              | 中文统计摘要                   |
| `center`            | [lat, lng]                          | 查询中心 (latitude first!)     |
| `query_radius`      | int                                 | 查询半径(米)，用于地图聚焦缩放 |
| `roads[].name`      | string                              | 道路名称                       |
| `roads[].status`    | `"畅通"/"缓行"/"拥堵"/"严重拥堵"` | 路况状态                       |
| `roads[].level`     | `"low"/"medium"/"high"`           | 拥堵等级                       |
| `roads[].speed`     | string                              | 速度描述                       |
| `roads[].direction` | string                              | 方向描述                       |
| `roads[].polyline`  | [[lat,lng], ...]                    | 道路坐标串                     |

#### TTS 音频

```json
{"url": "https://dashscope.aliyuncs.com/api/v1/.../audio.wav", "index": 0, "char_pos": 0}
{"url": "https://dashscope.aliyuncs.com/api/v1/.../audio.wav", "index": 1, "char_pos": 23}
```

| 字段         | 说明                                                   |
| ------------ | ------------------------------------------------------ |
| `url`      | 千问 TTS 返回的临时音频 URL                            |
| `index`    | 播放序号 (→ 前端按序播放)                             |
| `char_pos` | 当前句在完整文本中的起始字符位置 (→ 聊天窗口高亮同步) |

#### ASR 识别结果

```json
{"content": "厦门市路况怎么样"}
```

#### 错误

```json
{"content": "语音识别失败，请重试"}
```

---

## 3. LLM API (DashScope 千问)

**协议**: HTTP POST, **流式 SSE**
**地址**: `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
**鉴权**: `Authorization: Bearer sk-xxx`

### 3.1 请求格式 (JSON)

```json
{
  "model": "qwen3.7-plus",
  "messages": [
    {"role": "system", "content": "你是「闽路通」——福建省公路交通智能助手..."},
    {"role": "user", "content": "厦门市路况怎么样"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "query_traffic",
        "description": "查询福建省任意区域/地点/道路的实时交通路况...",
        "parameters": {
          "type": "object",
          "properties": {
            "area": {"type": "string", "description": "查询区域..."}
          },
          "required": ["area"]
        }
      }
    }
  ],
  "stream": true,
  "temperature": 1.0,
  "top_p": 0.95,
  "max_tokens": 4096
}
```

### 3.2 响应格式 (SSE 文本流)

```
HTTP 200
Content-Type: text/event-stream

data: {"choices":[{"index":0,"delta":{"content":"厦门市"},"finish_reason":null}]}

data: {"choices":[{"index":0,"delta":{"content":"路网"},"finish_reason":null}]}

data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

**格式规则**:

- 每行格式: `data: <JSON>\n\n`
- 文本响应: `delta.content` 为非空字符串
- 工具调用: `delta.tool_calls` 出现，逐片返回 `function.name` 和 `function.arguments`
- 结束标记: `data: [DONE]`

### 3.3 工具调用时的 SSE 响应片段

```
data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"query_traffic","arguments":"{\"area\":"}}]}}]}

data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"厦门市\"}"}}]}}]}

data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}
data: [DONE]
```

**注意**: `arguments` 是 JSON 字符串的分片 (`"{\"area\":"` → `"\"厦门市\"}"`)，需要逐片拼接再 `json.loads()`。

### 3.4 工具结果回传 (第二轮请求)

```json
{
  "model": "qwen3.7-plus",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "厦门市路况怎么样"},
    {
      "role": "assistant",
      "content": null,
      "tool_calls": [
        {"id": "call_abc", "type": "function", "function": {"name": "query_traffic", "arguments": "{\"area\":\"厦门市\"}"}}
      ]
    },
    {
      "role": "tool",
      "tool_call_id": "call_abc",
      "content": "{\"area\":\"厦门市\",\"total\":68,\"roads\":[{\"name\":\"成功大道\",\"status\":\"畅通\",...}],\"_focus\":{...},\"_guidance\":\"...\"}"
    }
  ],
  "stream": true
}
```

**第二轮请求不传 `tools`**，防止模型再次触发工具调用导致循环。

---

## 4. 高德地图 API (4个端点)

**协议**: HTTP GET
**鉴权**: Query String 参数 `key=xxx`

### 4.1 路况查询 (核心)

```
GET https://restapi.amap.com/v3/traffic/status/circle?key=KEY&location=118.082745,24.445676&radius=10000&extensions=all
```

| 参数           | 类型   | 示例               | 说明                                           |
| -------------- | ------ | ------------------ | ---------------------------------------------- |
| `location`   | string | `"118.08,24.48"` | ⚠️**经度在前、纬度在后** `"lng,lat"` |
| `radius`     | int    | `10000`          | 查询半径(米)                                   |
| `extensions` | string | `"all"`          | 必须 `"all"`，返回详细道路                   |

**响应 (JSON)**:

```json
{
  "status": "1",
  "info": "OK",
  "trafficinfo": {
    "description": "畅通",
    "evaluation": {"expedite": "85%", "congested": "10%", "blocked": "3%", "unknown": "2%"},
    "roads": [
      {
        "name": "成功大道",
        "status": "1",
        "direction": "从南向北",
        "angle": "180",
        "speed": "55",
        "lcodes": "350203-0012,350203-0013",
        "polyline": "118.133502,24.485469;118.134000,24.486000"
      }
    ]
  }
}
```

| roads 字段    | 类型                | 示例                      | 含义                                 |
| ------------- | ------------------- | ------------------------- | ------------------------------------ |
| `name`      | string              | `"成功大道"`            | 道路名称                             |
| `status`    | `"1"/"2"/"3"/"4"` | `"1"`                   | 畅通 / 缓行 / 拥堵 / 严重拥堵        |
| `speed`     | string              | `"55"`                  | 通行速度 (km/h)，可能为空            |
| `direction` | string              | `"从南向北"`            | 方向描述                             |
| `polyline`  | string              | `"lng,lat;lng,lat;..."` | ⚠️**经度在前**，分号分隔各点 |

### 4.2 地理编码 (地名→坐标)

```
GET https://restapi.amap.com/v3/geocode/geo?key=KEY&address=福州站&city=福州
```

| 参数        | 类型   | 必填 | 示例                      |
| ----------- | ------ | ---- | ------------------------- |
| `address` | string | 是   | `"福州站"`              |
| `city`    | string | 否   | `"福州"` (不含"市"后缀) |

**响应 (JSON)**:

```json
{
  "status": "1",
  "geocodes": [{
    "formatted_address": "福建省福州市晋安区福州站",
    "province": "福建省",
    "city": "福州市",
    "district": "晋安区",
    "location": "119.320571,26.113972",
    "level": "兴趣点"
  }]
}
```

| 字段         | 含义                                                              |
| ------------ | ----------------------------------------------------------------- |
| `level`    | 地点类型:`"省"/"市"/"区县"/"乡镇"/"兴趣点"/"道路"/"门牌号"/...` |
| `location` | `"lng,lat"` (经度在前)                                          |
| `province` | 省份 (用于过滤非福建结果)                                         |
| `city`     | 所属地级市                                                        |

### 4.3 POI 搜索 (地理编码兜底)

```
GET https://restapi.amap.com/v3/place/text?key=KEY&keywords=厦门市政府&offset=1&extensions=base
```

**响应 (JSON)**:

```json
{
  "status": "1",
  "count": "97",
  "pois": [{
    "name": "厦门市人民政府",
    "type": "政府机构及社会团体;政府机关;地市级政府及事业单位",
    "address": "湖滨北路61号",
    "location": "118.088910,24.479627",
    "pname": "福建省",
    "cityname": "厦门市",
    "adname": "思明区"
  }]
}
```

### 4.4 行政区划

```
GET https://restapi.amap.com/v3/config/district?key=KEY&keywords=厦门市&subdistrict=1&extensions=base
```

| 参数            | 类型   | 说明                               |
| --------------- | ------ | ---------------------------------- |
| `keywords`    | string | 城市名，如 `"厦门市"`            |
| `subdistrict` | int    | `0`=仅城市自身, `1`=含下属区县 |
| `extensions`  | string | `"base"` 不含边界 polyline       |

**响应 (JSON)**:

```json
{
  "status": "1",
  "districts": [{
    "name": "厦门市",
    "center": "118.08891,24.479627",
    "level": "city",
    "districts": [
      {"name": "思明区", "center": "118.082745,24.445676", "level": "district"},
      {"name": "湖里区", "center": "118.146825,24.512858", "level": "district"}
    ]
  }]
}
```

| 字段                   | 格式          | 说明                        |
| ---------------------- | ------------- | --------------------------- |
| `center`             | `"lng,lat"` | 行政区官方中心点 (经度在前) |
| `districts[].center` | `"lng,lat"` | 下属区县中心 (经度在前)     |

---

## 5. TTS API (DashScope 千问 TTS)

**协议**: HTTP POST (JSON)
**地址**: `https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`
**鉴权**: `Authorization: Bearer sk-xxx`

### 5.1 请求

```json
{
  "model": "qwen-tts-2025-05-22",
  "input": {
    "text": "厦门市路网整体平稳。"
  },
  "parameters": {
    "voice": "Cherry",
    "language_type": "Chinese"
  }
}
```

### 5.2 响应

```json
{
  "output": {
    "audio": {
      "url": "https://dashscope.aliyuncs.com/api/v1/temp/audio/xxxxx.wav"
    }
  }
}
```

`url` 是临时链接，直接传给前端浏览器播放。前端不做二次处理。

---

## 6. 坐标体系对照

| 场景                   | 坐标系 | 格式                       | 示例                                |
| ---------------------- | ------ | -------------------------- | ----------------------------------- |
| 高德 API 输入参数      | GCJ-02 | `"lng,lat"` str          | `"118.08,24.48"`                  |
| 高德 API polyline 返回 | GCJ-02 | `"lng,lat;lng,lat"` str  | `"118.13,24.48;118.14,24.49"`     |
| 系统内部 road.polyline | GCJ-02 | `[[lat,lng], ...]` array | `[[24.48,118.13],[24.49,118.14]]` |
| Socket.IO traffic_data | GCJ-02 | `[[lat,lng], ...]` array | 同内部格式                          |
| Leaflet L.polyline()   | GCJ-02 | `[lat,lng]` pairs        | 直接使用内部格式                    |
| 高德地图瓦片           | GCJ-02 | —                         | 天然对齐                            |

**关键转换点**:

```
高德返回 "lng,lat;lng,lat"
        ↓ _parse_polyline()
内部格式 [[lat,lng],[lat,lng]]
        ↓ 直接传递
前端渲染 [[lat,lng],[lat,lng]]
```

**如果甲方提供 WGS-84 (GPS原始) 坐标**: 需用 `coordtransform` 等库转为 GCJ-02 后才能叠加显示在高德瓦片上。偏移量 100~700m。

---

## 7. 甲方数据对接切入点

甲方若要在任意环节替换数据源，只需在标注的位置提供符合格式的接口：

### 7.1 替换路况数据源

**现状**: TrafficStatusSkill 调用高德 Circle API
**甲方提供**: HTTP API，输入查询区域名 → 输出内部标准格式

```json
// 甲方需提供的 API 请求格式
GET /api/traffic?area=厦门市

// 甲方需返回的响应格式
{
  "area": "厦门市",
  "roads": [
    {
      "name": "成功大道",
      "status": "拥堵",
      "level": "high",
      "speed": "20 km/h",
      "direction": "南北双向",
      "polyline": [[24.48, 118.13], [24.49, 118.14]]
    }
  ]
}
```

**status 枚举**: `"畅通" / "缓行" / "拥堵" / "严重拥堵"` (必须用中文)
**level 枚举**: `"low" / "medium" / "high"`
**坐标系**: GCJ-02
**polyline 格式**: `[[lat, lng], ...]` (纬度在前)

### 7.2 替换施工/灾害/设备数据源

**现状**: 静态 JSON (`mockRoadData.json`)，尚未接入 API
**甲方提供**: 对应 HTTP API → 新建 Skill 类即可

### 7.3 替换 LLM

**现状**: DashScope 千问 (OpenAI 兼容格式)
**甲方模型**: 只需兼容 OpenAI Chat Completions API 格式 (messages + tools + stream)，即可直接替换。

### 7.4 替换 TTS

**现状**: DashScope Qwen-TTS
**甲方 TTS**: 需提供 `synthesize(text) → audio_url` 接口。

### 7.5 替换 ASR

**现状**: 本地 faster-whisper (WAV base64 → 文本)
**甲方 ASR**: 需提供 `recognize(audio_base64) → text` 接口。
