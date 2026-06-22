# 闽路通 · 全链路数据架构文档

> 以「用户语音问"厦门市路况怎么样"」为例，逐步追踪数据形态变化。

---

## 流程图（总览）

```
用户: 🎤 "厦门市路况怎么样"
  │
  ├─[1]─► ASR: base64 WAV → "厦门市路况怎么样"
  │
  ├─[2]─► 消息队列: messages.append({role:'user', content:'...'})
  │
  ├─[3]─► 构建 LLM 请求: system_prompt + messages + tools
  │         │
  │         ▼
  │       千问 API (DashScope, stream=true)
  │         │
  │         ▼ SSE流解析
  │         │
  │         ├── 文本路径: delta.content → yield chunk
  │         │
  │         └── 工具路径: delta.tool_calls → 累积 → _pending_tool_calls
  │              │
  ├─[4]──────────▼─────────────────────────────────────
  │   _execute_tool_call("query_traffic", {area:"厦门市"})
  │     │
  │     ├── _resolve_location("厦门市") → (24.48,118.09, '市')
  │     │
  │     ├── _district_sample("厦门市")
  │     │     │
  │     │     ├── district API → 6区县中心 (思明/湖里/集美/海沧/同安/翔安)
  │     │     ├── 6 × circle API → 6个原始JSON
  │     │     └── 合并去重 → 60+条道路
  │     │
  │     └── 输出内部格式 result
  │
  ├─[5]─► 数据分叉
  │        ├── lightweight(result) → 去polyline + _focus + _guidance → 给千问
  │        └── frontend_data(result) → 保留polyline + center + radius → 给前端
  │
  ├─[6a]─► 千问第二轮 (收到tool result) → 流式输出文本
  │          ├── text_chunk 事件 → 前端实时显示
  │          └── text_complete 事件 → 最终文本
  │
  ├─[6b]─► traffic_data 事件 → 前端 Leaflet 渲染地图
  │
  └─[7]─► TTS: 句子级切分 → 千问TTS合成 → audio 事件 → 浏览器播放
```

---

## Step 1: 语音输入 → ASR

```
前端录制: MediaRecorder → WAV blob → base64编码
  ↓ Socket.IO emit('message', {type:'audio', content:'UklGRiQAAABXQV...'})
```

**后端接收** (`websocket.py:process_audio_message`):

```python
# 输入
audio_data = "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA="

# ASR 处理
recognized_text = asr_service.recognize(audio_data)  # 本地 faster-whisper small

# 输出: "厦门市路况怎么样"
```

```json
// emit('asr_result')
{"content": "厦门市路况怎么样"}
```

---

## Step 2: 构建 LLM 请求

**对话历史** (client_histories[sid]):

```json
[{"role": "user", "content": "厦门市路况怎么样"}]
```

**System Prompt** (精简后 22 行):

```
你是「闽路通」——福建省公路交通智能助手。

## 角色
你服务于福建省全域...你拥有实时路况查询等专业工具...
**所有交通数据以工具查询结果为唯一真实来源，严禁编造路况信息。**

## 范围
仅服务福建省内交通业务...

## 行为
- 涉及路况...必须先调用对应工具获取数据...
- 工具返回数据中如含 _guidance 字段，按其指示组织信息...

## 输出格式
回复末尾附```json代码块标注地理位置...
```

**组装后的 API 请求体**:

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
            "area": {
              "type": "string",
              "description": "用户查询的原始地点表述，严禁截断简化。例：'厦门市政府附近'→传'厦门市政府'（不是'厦门市'）；'福州站周边'→传'福州站'（不是'福州市'）..."
            }
          },
          "required": ["area"]
        }
      }
    }
  ],
  "stream": true,
  "temperature": 1.0,
  "max_tokens": 4096
}
```

---

## Step 3: 千问流式返回 → 检测 tool_calls

SSE 流中逐行返回的数据：

```
data: {"choices":[{"index":0,"delta":{"content":null},"finish_reason":null}]}

data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_123","type":"function","function":{"name":"query_traffic","arguments":"{\"area\":"}}]}}]}

data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"厦门市\"}"}}]}}]}

data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

data: [DONE]
```

**`_stream_chat` 中的累积结果** (`self._pending_tool_calls`):

```python
[{
    'id': 'call_123',
    'type': 'function',
    'function': {
        'name': 'query_traffic',
        'arguments': '{"area": "厦门市"}'
    }
}]
```

> **注意**: arguments 是分片到达的 (`"{\"area\":"` → `"\"厦门市\"}"`)，代码中手动拼接为完整 JSON。

---

## Step 4: 执行工具调用（高德 API 全景）

### 4.0 谁在调用高德 API

```
千问 (LLM)
  │  决定需要查路况 → 输出 tool_call
  │  {"name":"query_traffic", "arguments":{"area":"厦门市"}}
  │
  ▼
llm_service._execute_tool_call()
  │  根据 tool_call['function']['name'] 找到对应 Skill
  │  skill = self._skill_map["query_traffic"]   → TrafficStatusSkill
  │
  ▼
TrafficStatusSkill.execute({"area": "厦门市"})
  │  内部调用链:
  │  _resolve_location → _geocode / _search_poi / _get_city_center
  │  _district_sample  → _get_district_centers → 多个 _call_traffic
  │  _format           → 结构化输出
  │
  ▼
高德 API（4个端点）
  ├── /v3/geocode/geo     地理编码
  ├── /v3/place/text       POI 搜索（兜底）
  ├── /v3/config/district  行政区划
  └── /v3/traffic/status/circle  路况查询
```

### 4.1 调用条件决策树

千问决定调工具的条件是**用户问题涉及路况、施工、灾害、设备等交通业务**——这是由 system prompt 和 tool description 共同引导的。

一旦千问输出 tool_call，后端进入以下决策链：

```
execute(area)
  │
  ├─ area 在 FUJIAN_CITIES 字典中? (九地市精确匹配)
  │   YES → _get_city_center(city)     【调 行政区划 API】
  │        → 返回 (center, 25000m, '市')
  │        → level='市' → _district_sample(city)
  │           │
  │           └─ _get_district_centers(city)  【调 行政区划 API, subdistrict=1】
  │              → 获得 N 个区县中心坐标 (全部在陆地)
  │              → 每个中心: _call_traffic(lat, lng, radius) 【调 路况 Circle API】
  │              → 合并去重 → 返回 50~100 条道路
  │
  ├─ area 不在字典中 → 地理编码: _geocode(area)
  │   │
  │   ├─【调 地理编码 API】无 city 限定 → 过滤 province="福建省"
  │   ├─【调 地理编码 API】逐一用福建九地市限定
  │   ├─ _pick_best(候选) → 得出 (lat, lng, level, city)
  │   │
  │   ├─ level='市' 但 area 不是已知城市名?
  │   │   YES → 怀疑 POI 误判 →【调 POI 搜索 API】兜底
  │   │   NO  → 触发 _district_sample
  │   │
  │   └─ level ≠ '市'
  │       → 半径 = _radius_for_level(level)
  │          (兴趣点=2000m, 区县=8000m, 道路=1500m, ...)
  │       →【调 路况 Circle API】单点查询
  │
  └─ 统一输出: {'area', 'roads', 'summary', 'center', 'query_radius'}
```

**关键决策点**:
- 「是城市名还是具体地点」由 `area in FUJIAN_CITIES` 一级判断，`level='市'+area 不在字典` 二级兜底
- 城市级自动走区县多点采样，非城市走单点圆圈查询
- 半径随 `level` 自适应：千问不需要理解坐标系或半径，Skill 内部解决

---

### 4.2 高德 API ①: 地理编码 (Geocode)

**用途**: 把中文地名 → 经纬度 + 地点类型

```
GET https://restapi.amap.com/v3/geocode/geo?key=KEY&address=福州站&city=福州
```

**完整请求参数**:

| 参数 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `key` | 是 | Web服务 API Key | `bfdc34...` |
| `address` | 是 | 地点描述 | `"福州站"` `"思明区"` |
| `city` | 否 | 限定城市，提高准确率 | `"福州"` (不带"市") |

**成功响应** (福州站):

```json
{
  "status": "1",
  "info": "OK",
  "infocode": "10000",
  "count": "1",
  "geocodes": [{
    "formatted_address": "福建省福州市晋安区福州站",
    "country": "中国",
    "province": "福建省",
    "citycode": "0591",
    "city": "福州市",
    "district": "晋安区",
    "adcode": "350111",
    "location": "119.320571,26.113972",
    "level": "兴趣点"
  }]
}
```

**响应字段说明**:

| 字段 | 说明 | 对我们的价值 |
|------|------|------------|
| `location` | `"lng,lat"` 格式 | → 解析为查询中心坐标 |
| `level` | 地点类型标识 | → 决定查询半径（市=25km, 区县=8km, 兴趣点=2km） |
| `province` | 省份 | → 过滤福建省外结果 |
| `city` | 所属地级市 | → 优先级排序（九地市主城优先） |
| `formatted_address` | 完整地址 | → 回传给千问作为区域标签 |

**level 枚举值与系统半径映射**:

| level 值 | 含义 | 系统半径 | 例子 |
|----------|------|---------|------|
| `省` | 省级 | 30km | "福建省" |
| `市` | 地级市 | → 触发区县采样 | "厦门市" |
| `区县` | 区/县/县级市 | 8km | "思明区"、"晋江市" |
| `乡镇` | 乡镇/街道 | 5km | "鼓浪屿街道" |
| `兴趣点` | POI | 2km | "福州站"、"厦门大学" |
| `公交地铁站点` | 交通站点 | 1.5km | "厦门站(地铁站)" |
| `道路` | 道路名 | 1.5km | "成功大道"、"嘉禾路" |
| `门牌号` | 精确地址 | 1km | "湖滨北路61号" |

**系统中的调用策略** (traffic_status._geocode):

```
策略A: geocode(address, city=None)
  → 过滤候选中 province="福建省" 的结果

策略B: 逐一 geocode(address, city=福州/厦门/泉州/...)
  → 用九地市分别限定后查询，收集所有候选

_pick_best():
  → 排序优先级: (九地市主城 > 非主城, 主城内按福州/厦门/泉州优先, level更具体优先)
  → 返回最优候选

防误判:
  如果最佳候选 level='市' 且 address 不在 FUJIAN_CITIES 中
  → 说明 geocode 把 "厦门市政府" 的 "市" 误当城市标识
  → 触发 POI 搜索 API 兜底 (见 4.3)
```

---

### 4.3 高德 API ②: POI 搜索 (Place Search) — 兜底

**用途**: 地理编码无法正确识别时（如"厦门市政府"被误判为城市），用 POI 搜索纠正。

```
GET https://restapi.amap.com/v3/place/text?key=KEY&keywords=厦门市政府&offset=1&extensions=base
```

**完整请求参数**:

| 参数 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `key` | 是 | Web服务 API Key | `bfdc34...` |
| `keywords` | 是 | 搜索关键词 | `"厦门市政府"` |
| `offset` | 否 | 每页条数 | `1` (只要第1条) |
| `extensions` | 否 | `base` 不含详情 | `"base"` |

**成功响应**:

```json
{
  "status": "1",
  "info": "OK",
  "count": "97",
  "pois": [{
    "id": "B0FFFZ1234",
    "name": "厦门市人民政府",
    "type": "政府机构及社会团体;政府机关;地市级政府及事业单位",
    "typecode": "130100",
    "address": "湖滨北路61号",
    "location": "118.088910,24.479627",
    "pname": "福建省",
    "cityname": "厦门市",
    "adname": "思明区"
  }]
}
```

**字段映射**:

| POI 返回字段 | → 内部格式 | 说明 |
|-------------|-----------|------|
| `name` | → `formatted_address` | POI 名称，更准确 |
| `type` | → `level="兴趣点"` | POI 搜出来的必然是具体地点 |
| `location` | → `(lat, lng)` | 解析同 geocode |
| `cityname` | → `city` | 所属城市 |
| `address` | → `formatted_address` 备选 | 结构化地址 |

**触发条件**: 仅当 geocode 返回 `level='市'` 且 `address not in FUJIAN_CITIES` 时触发。
例如: "厦门市政府", "福州市公安局", "泉州动车站" 这类「城市名+修饰词」的表述。

---

### 4.4 高德 API ③: 行政区划 (District)

**用途**: 获取城市中心坐标 + 下属区县列表（用于城市级多点采样）。

**A. 获取城市中心**:

```
GET https://restapi.amap.com/v3/config/district?key=KEY&keywords=厦门市&subdistrict=0&extensions=base
```

```json
{
  "status": "1",
  "districts": [{
    "name": "厦门市",
    "center": "118.08891,24.479627",
    "level": "city"
  }]
}
```

取 `districts[0].center` → 解析为 `(lat, lng)` → 缓存

**B. 获取下属区县**:

```
GET https://restapi.amap.com/v3/config/district?key=KEY&keywords=厦门市&subdistrict=1&extensions=base
```

| 参数 | 说明 |
|------|------|
| `subdistrict=0` | 只返回城市自身 |
| `subdistrict=1` | 返回城市 + 下一级行政区（区/县） |

```json
{
  "status": "1",
  "districts": [{
    "name": "厦门市",
    "center": "118.08891,24.479627",
    "level": "city",
    "districts": [
      {"name": "思明区", "center": "118.082745,24.445676", "level": "district"},
      {"name": "湖里区", "center": "118.146825,24.512858", "level": "district"},
      {"name": "集美区", "center": "118.097407,24.575976", "level": "district"},
      {"name": "海沧区", "center": "118.032883,24.484688", "level": "district"},
      {"name": "同安区", "center": "118.150823,24.723299", "level": "district"},
      {"name": "翔安区", "center": "118.247911,24.618583", "level": "district"}
    ]
  }]
}
```

**采样半径自适应**:
```python
radius = max(8000, min(15000, 60000 // len(centers)))
# 厦门 6 区县 → 10000m/点 → 6 × 10km圈
# 福州 13 区县 → 8000m/点 → 13 × 8km圈 (8000 是下限)
# 莆田 5 区县 → 12000m/点 → 5 × 12km圈
```

**为什么不用矩形网格**: 厦门等沿海城市行政边界包含大片海域，矩形网格中心容易落入海中 → 返回空数据。区县中心是官方行政区中心，天然在陆地上。

---

### 4.5 高德 API ④: 路况查询 (Traffic Status Circle)

**用途**: 这是核心 API——给定一个经纬度和半径，返回圈内所有道路的实时交通状态。

```
GET https://restapi.amap.com/v3/traffic/status/circle?key=KEY&location=118.082745,24.445676&radius=10000&extensions=all
```

**完整请求参数**:

| 参数 | 必填 | 说明 | 示例 |
|------|------|------|------|
| `key` | 是 | Web服务 API Key | `bfdc34...` |
| `location` | 是 | 中心点 `"lng,lat"` (经度在前!) | `"118.082745,24.445676"` |
| `radius` | 是 | 查询半径(米) | `10000` |
| `extensions` | 是 | `"all"` 返回详细道路信息 | `"all"` |

**成功响应原始 JSON**:

```json
{
  "status": "1",
  "info": "OK",
  "infocode": "10000",
  "trafficinfo": {
    "description": "畅通",
    "evaluation": {
      "expedite": "85%",
      "congested": "10%",
      "blocked": "3%",
      "unknown": "2%"
    },
    "roads": [
      {
        "name": "成功大道",
        "status": "1",
        "direction": "从南向北",
        "angle": "180",
        "speed": "55",
        "lcodes": "350203-0012,350203-0013,350203-0014",
        "polyline": "118.133502,24.485469;118.134000,24.486000;118.134500,24.487000"
      },
      {
        "name": "嘉禾路",
        "status": "3",
        "direction": "南北双向",
        "angle": "90",
        "speed": "20",
        "lcodes": "350203-0020,350203-0021",
        "polyline": "118.120000,24.490000;118.125000,24.492000;118.130000,24.493000"
      }
    ]
  }
}
```

**roads[] 字段详解**:

| 原始字段 | 类型 | 示例 | 系统处理 |
|---------|------|------|---------|
| `name` | string | `"成功大道"` | → 直接使用 |
| `status` | "1"\|"2"\|"3"\|"4" | `"3"` | → `STATUS_MAP['3']` = `"拥堵"` |
| `speed` | string | `"20"` | → 拼单位: `"20 km/h"` |
| `direction` | string | `"南北双向"` | → 直接使用 |
| `angle` | string | `"90"` | 未使用（车行角度） |
| `lcodes` | string | `"350203-0012,..."` | 未使用（路段ID，可做去重） |
| `polyline` | string | `"118.13,24.48;..."` | → `_parse_polyline()` → `[[24.48,118.13],...]` |

**status 和 level 映射表**:

| 高德 status | 含义 | 内部 status | 内部 level | 地图颜色 | 地图线宽 |
|------------|------|------------|-----------|---------|---------|
| `"1"` | 畅通 | `"畅通"` | `"low"` | `#27ae60` 绿 | 3px |
| `"2"` | 缓行 | `"缓行"` | `"medium"` | `#f39c12` 橙 | 4px |
| `"3"` | 拥堵 | `"拥堵"` | `"high"` | `#e74c3c` 红 | 6px |
| `"4"` | 严重拥堵 | `"严重拥堵"` | `"high"` | `#c0392b` 深红 | 7px |

**Polyline 转换细节**:

```python
# 高德 raw: "lng,lat;lng,lat;..."  (GCJ-02, 经度在前)
# 示例:      "118.133502,24.485469;118.134000,24.486000"

# _parse_polyline() 处理:
def _parse_polyline(polyline_str):
    points = []
    for pair in polyline_str.split(';'):        # 按 ; 切段
        lng, lat = pair.split(',')              # 拆经纬度
        points.append([float(lat), float(lng)])  # Leaflet 格式: [纬度, 经度]
    return points

# 输出: [[24.485469, 118.133502], [24.486000, 118.134000]]
#       ↑ lat在前, Leaflet 直接可用
```

**方法整理**: 系统中共有 3 处调此 API

| 场景 | 调用位置 | 半径 | 调用次数 |
|------|---------|------|---------|
| 城市级查询 | `_district_sample()` → 逐区县中心 | 自适应 (8~15km) | N 次 (N = 区县数) |
| 区县/POI/道路查询 | `execute()` → 直接 | 1.5~8km | 1 次 |
| 城市采样兜底 | `_district_sample` 返回 None → `execute()` 单点 | 25km | 1 次 |

**API 隐式限制**:
- 每次请求约返回 10~30 条道路（离中心最近的）
- 无分页参数，无法获取更多
- 这是城市级必须用区县多点采样的根本原因——单点 25km 圈也只能返回 ~30 条，海沧、集美、翔安等远区被遗漏
- 更新频率约 2 分钟

---

### 4.6 合并去重输出 (内部标准格式)

6个区县 → 6次 circle API → 合并 → 按 `name` 字段去重 → 内部标准格式:

```json
{
  "area": "厦门市",
  "total": 68,
  "summary": "共 68 条道路，其中严重拥堵 2 条、拥堵 5 条、缓行 12 条、畅通 49 条",
  "roads": [
    {
      "name": "成功大道",
      "status": "畅通",
      "level": "low",
      "speed": "55 km/h",
      "direction": "从南向北",
      "polyline": [[24.485469, 118.133502], [24.4860, 118.1340], [24.4870, 118.1345]]
    },
    {
      "name": "海沧大桥",
      "status": "畅通",
      "level": "low",
      "speed": "60 km/h",
      "direction": "东西向",
      "polyline": [[24.50, 118.02], [24.52, 118.05]]
    }
  ],
  "center": [24.479627, 118.08891],
  "query_radius": 30000
}
```

> `query_radius=30000` 而非 10000: 城市级多中心采样没有单一查询半径，这里设为 30km 用于前端地图缩放，让用户看到全市范围的视口。

---

## Step 5: 数据分叉

### 5a: lightweight() → 给千问的精简版

**转换逻辑** (base.py):

```python
# 每条 road 去掉 polyline（千问不需要坐标数据，节约token）
{ k: v for k, v in r.items() if k != 'polyline' }
```

**再附加 _focus 和 _guidance** (traffic_status.py):

```python
light['_focus'] = self._build_focus(roads)
# {
#   "跨海通道/桥梁隧道": ["海沧大桥", "翔安隧道", "演武大桥"],
#   "高速/快速路/主干大道": ["G15沈海高速厦门段", "成功大道"],
#   "主要道路": ["湖滨南路", "厦禾路", "嘉禾路", "莲前东路", ...]
# }

light['_guidance'] = (
    '1. 一句总览。'
    '2. 有拥堵/缓行 → 逐个详情。'
    '3. 按 _focus 分组简述（每组一句话，不逐条展开）。'
    '4. "其余路段通行正常"概括。'
    '全部畅通时也不逐条列出小路。'
)
```

**发给千问的 tool result 消息**:

```json
{
  "role": "tool",
  "tool_call_id": "call_123",
  "content": "{\"area\":\"厦门市\",\"total\":68,\"summary\":\"共 68 条道路，其中严重拥堵 2 条...\",\"roads\":[{\"name\":\"成功大道\",\"status\":\"畅通\",\"level\":\"low\",\"speed\":\"55 km/h\",\"direction\":\"从南向北\"},{\"name\":\"嘉禾路\",\"status\":\"缓行\",\"level\":\"medium\",\"speed\":\"35 km/h\",\"direction\":\"东西向\"},...],\"_focus\":{\"跨海通道/桥梁隧道\":[\"海沧大桥\",\"翔安隧道\",\"演武大桥\"],\"高速/快速路/主干大道\":[\"G15沈海高速厦门段\",\"成功大道\"],\"主要道路\":[\"湖滨南路\",\"厦禾路\",...]},\"_guidance\":\"1. 一句总览。2. 有拥堵/缓行→逐个详情。3. 按_focus分组简述。4. \\\"其余路段通行正常\\\"概括。\"}"
}
```

> **关键**: `content` 是 JSON 字符串，千问看到的是结构化的道路数据 + `_focus`(基础设施分层) + `_guidance`(回答组织指令)。68条路都发给千问了，但 _guidance 指示它不要逐条列出。

### 5b: frontend_data() → 给前端的完整版

```python
# 原封不动返回 result，保留 polyline
```

**Socket.IO emit('traffic_data')** (websocket.py:emit_frontend):

```json
{
  "city": "厦门市",
  "summary": "共 68 条道路，其中严重拥堵 2 条、拥堵 5 条、缓行 12 条、畅通 49 条",
  "center": [24.479627, 118.08891],
  "query_radius": 30000,
  "roads": [
    {
      "name": "成功大道",
      "status": "畅通",
      "level": "low",
      "speed": "55 km/h",
      "direction": "从南向北",
      "polyline": [[24.485469, 118.133502], [24.4860, 118.1340], [24.4870, 118.1345]]
    }
  ]
}
```

> **关键**: 这个事件在千问第二轮文本流**开始之前**就已发出。地图先绘制，文字随后流出。

---

## Step 6: 千问生成回答（第二轮）

### 6a: 千问收到完整 tool result 后，开始流式输出文本

SSE 流返回:

```
data: {"choices":[{"index":0,"delta":{"content":"厦门市路网"},"finish_reason":null}]}
data: {"choices":[{"index":0,"delta":{"content":"整体平稳"},"finish_reason":null}]}
data: {"choices":[{"index":0,"delta":{"content":"，存在2"},"finish_reason":null}]}
...
data: {"choices":[{"index":0,"delta":{"content":"处严重拥堵"},"finish_reason":null}]}
...
data: {"choices":[{"index":0,"delta":{"content":"\n```json\n{\"map_highlight\":[{\"name\":\"嘉禾路\",\"lat\":24.49,\"lng\":118.12,\"type\":\"congestion\"}]}\n```"},"finish_reason":"stop"}]}
data: [DONE]
```

### 6b: 后端逐 chunk 处理

**text_chunk 事件** (每个 chunk 即时发送):

```json
{"content": "厦门市路网", "is_final": false}
{"content": "整体平稳", "is_final": false}
...
```

> **json_started 检测**: 当 `full_response` 中包含 '```json' 时，后续 chunk 不再发送 text_chunk（前端不显示 JSON 代码块）

**text_complete 事件** (流结束，去掉 JSON 块后的纯净文本):

```json
{
  "content": "厦门市路网整体平稳，存在2处严重拥堵。\n\n跨海通道方面：海沧大桥、翔安隧道、演武大桥均畅通。\n高速快速路方面：G15沈海高速厦门段、成功大道通行正常。\n主要干道方面：大部分路段通行正常。\n\n严重拥堵路段：\n- 嘉禾路，速度仅20km/h，南北双向拥堵。\n- 仙岳路，速度15km/h，东西向严重拥堵。\n\n其余路段通行正常。"
}
```

---

## Step 7: TTS 语音合成

### 7a: 句子级切分

`websocket.py` 的文本循环中的切句逻辑:

```
pending 累积过程:
  "厦门市路网整体平稳。"          → 完整句 → send_sentence("厦门市路网整体平稳。")
  "跨海通道方面：海沧大桥、"      → 不完整，继续等
  "翔安隧道、演武大桥均畅通。"    → 完整句 → send_sentence("跨海通道方面：海沧大桥、翔安隧道、演武大桥均畅通。")
  ...
```

### 7b: 首句合并防短句

```python
# 如果第一句不足 20 字 → 不单独发，等下一句合并
# 例: "厦门路况如下：" (7字) → 攒起来，等"整体平稳..."到达后合并发送
```

### 7c: TTS 请求 → 千问 TTS API

```python
# tts_service.synthesize("厦门市路网整体平稳。")
#   → POST https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
#   → 返回: {"output":{"audio":{"url":"https://dashscope.aliyuncs.com/..."}}}
```

### 7d: Audio 事件发送给前端

```json
{"url": "https://dashscope.aliyuncs.com/...", "index": 0, "char_pos": 0}
{"url": "https://dashscope.aliyuncs.com/...", "index": 1, "char_pos": 23}
```

> `char_pos` 是当前句在完整文本中的起始字符位置，前端用它在 ChatWindow 中高亮正在朗读的文字。

---

## Step 8: 前端渲染

### 8a: 地图渲染 (TrafficMap.vue)

```
收到 traffic_data 事件
  → trafficRoads = data.roads (含 polyline)
  → trafficCenter = data.center [24.48, 118.09]
  → trafficRadius = data.query_radius = 30000

watch(realtimeTraffic):
  每条 road:
    L.polyline(road.polyline, {
      color: statusColor[road.status],   # 畅通=绿, 缓行=橙, 拥堵=红, 严重拥堵=深红
      weight: statusWeight[road.status],  # 3/4/6/7
      opacity: 0.8
    }).bindPopup(`<b>${road.name}</b><br>状态: ${road.status}<br>速度: ${road.speed}`)

  缩放:
    center + query_radius → half = 30000/111000 = 0.27°
    → fitBounds([[24.21, 117.82], [24.75, 118.36]])
    → 视口锁在城市区域，不被长 polyline 拖大
```

### 8b: 文字渲染 (ChatWindow.vue)

```
收到 text_chunk:
  实时追加到当前 assistant 消息末尾 → 流式打字效果

收到 text_complete:
  用纯净文本覆盖 → 最终显示
  parseMapHighlight(text) → 解析 ```json 中的 map_highlight 数组
```

### 8c: 语音播放 (AudioControls)

```
收到 audio:
  加入 audioQueue，按 index 排序
  → tryPlayNext() → new Audio(url) → play()
  → onended → tryPlayNext() → 下一句自动播放
```

---

## 附录: 完整数据结构速查表

### 千问 API (DashScope) 兼容格式

| 层级      | 字段                                                  | 示例值                                                   |
| --------- | ----------------------------------------------------- | -------------------------------------------------------- |
| 请求      | `model`                                             | `"qwen3.7-plus"`                                       |
| 请求      | `messages[].role`                                   | `"system"` / `"user"` / `"assistant"` / `"tool"` |
| 请求      | `tools[].function.name`                             | `"query_traffic"`                                      |
| 请求      | `stream`                                            | `true`                                                 |
| 响应(SSE) | `choices[0].delta.content`                          | `"厦门市"`                                             |
| 响应(SSE) | `choices[0].delta.tool_calls[0].function.name`      | `"query_traffic"`                                      |
| 响应(SSE) | `choices[0].delta.tool_calls[0].function.arguments` | `"{\"area\":\"厦门市\"}"`                              |
| 响应(SSE) | `choices[0].finish_reason`                          | `"stop"` / `"tool_calls"`                            |

### Socket.IO 事件一览

| 方向       | 事件名            | payload 关键字段                                      |
| ---------- | ----------------- | ----------------------------------------------------- |
| 前端→后端 | `message`       | `{type:"text"/"audio", content, auto_read, volume}` |
| 后端→前端 | `text_chunk`    | `{content, is_final:false}`                         |
| 后端→前端 | `text_complete` | `{content}` (无 JSON 块)                            |
| 后端→前端 | `traffic_data`  | `{city, summary, center, query_radius, roads[]}`    |
| 后端→前端 | `audio`         | `{url, index, char_pos}`                            |
| 后端→前端 | `asr_result`    | `{content}`                                         |
| 后端→前端 | `error`         | `{content}`                                         |

### 坐标转换链

```
高德 circle API 输入: "lng,lat"     (经度在前)
     ↓
高德 polyline 输出: "lng,lat;lng,lat;..."  (GCJ-02)
     ↓ _parse_polyline()
内部格式 road.polyline: [[lat,lng], [lat,lng], ...]  (纬度在前, Leaflet 惯例)
     ↓
前端 L.polyline(): 直接用 (Leaflet 接受 [lat,lng])
     ↓
高德瓦片: GCJ-02  →  天然对齐, 无偏移 ✓
```
