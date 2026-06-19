/**
 * ========================================
 *  福建省路面交通数据源抽象层
 * ========================================
 *
 * 设计目标：屏蔽数据来源差异，前端组件只依赖 RoadDataSource 接口。
 * 甲方提供真实数据时，只需新增一个数据源实现类，无需修改任何 UI 代码。
 *
 * 支持的数据接入形式：
 *   1. 本地 JSON 文件  → JsonFileDataSource / MockDataSource
 *   2. REST API         → ApiDataSource (GET/POST)
 *   3. WebSocket 推送   → WebSocketDataSource (实时)
 *   4. CSV / Excel      → CsvDataSource (需引入解析库)
 *   5. 混合数据源       → HybridDataSource (多源聚合)
 *
 * 数据格式要求（甲方数据需符合以下任一结构）：
 *
 *   格式 A — 扁平数组（推荐）:
 *   [
 *     { "name":"G15福州段", "lat":26.07, "lng":119.30,
 *       "level":"high", "speed":"30-50km/h", "desc":"..." },
 *     ...
 *   ]
 *
 *   格式 B — 嵌套对象（上游系统的标准格式）:
 *   {
 *     "congestion_segments": [...],
 *     "construction_sites": [...],
 *     "hazard_points": [...],
 *     "device_status": [...]
 *   }
 *
 *   格式 C — GeoJSON（GIS 系统导出）:
 *   {
 *     "type": "FeatureCollection",
 *     "features": [
 *       { "type":"Feature", "geometry":{...}, "properties":{...} }
 *     ]
 *   }
 */

import mockData from './mockRoadData.json'

// ── 抽象接口 ──

export class RoadDataSource {
  /** @returns {Array<{id, name, latlngs, level, speed, delay, desc}>} */
  getCongestionSegments() { return [] }

  /** @returns {Array<{id, name, lat, lng, desc, impact}>} */
  getConstructionSites() { return [] }

  /** @returns {Array<{id, name, lat, lng, type, riskLevel, desc, monitorStatus}>} */
  getHazardPoints() { return [] }

  /** @returns {Array<{id, name, lat, lng, type, online, readings}>} */
  getDeviceStatus() { return [] }

  /** @returns {[lat, lng]} 地图默认中心点 */
  getProvinceCenter() { return [26.07, 119.30] }
}

// ── 实现 1：Mock 数据（Demo 阶段使用）──

export class MockDataSource extends RoadDataSource {
  getCongestionSegments() { return mockData.congestion_segments }
  getConstructionSites() { return mockData.construction_sites }
  getHazardPoints() { return mockData.hazard_points }
  getDeviceStatus() { return mockData.device_status }
  getProvinceCenter() { return mockData.province_center }
}

// ── 实现 2：REST API 数据源 ──
//
// 当甲方提供 HTTP API 时启用。示例：
//
//   export class ApiDataSource extends RoadDataSource {
//     constructor(baseUrl) { super(); this.baseUrl = baseUrl }
//
//     async getCongestionSegments() {
//       const r = await fetch(`${this.baseUrl}/traffic/congestion`)
//       return r.json()
//     }
//     async getConstructionSites() {
//       const r = await fetch(`${this.baseUrl}/traffic/construction`)
//       return r.json()
//     }
//     // ... 其余方法类似
//   }
//
// 切换方式：修改文件末尾的 dataSource 实例即可

// ── 实现 3：WebSocket 实时推送 ──
//
// 当甲方有实时数据推送时启用。示例：
//
//   export class WebSocketDataSource extends RoadDataSource {
//     constructor(wsUrl) {
//       super()
//       this.ws = new WebSocket(wsUrl)
//       this.congestion = []
//       this.ws.onmessage = (e) => {
//         const msg = JSON.parse(e.data)
//         if (msg.type === 'congestion_update') this.congestion = msg.data
//       }
//     }
//     getCongestionSegments() { return this.congestion }
//   }

// ── 实现 4：GeoJSON 格式适配 ──
//
// 如果甲方数据是 GIS 标准 GeoJSON 格式：
//
//   export class GeoJsonDataSource extends RoadDataSource {
//     constructor(geoJsonUrl) { ... }
//     async load() {
//       const r = await fetch(this.url); const geo = await r.json()
//       this.congestion = geo.features
//         .filter(f => f.properties.type === 'congestion')
//         .map(f => ({
//           name: f.properties.name,
//           latlngs: f.geometry.coordinates.map(([lng,lat]) => [lat,lng]),
//           level: f.properties.level,
//           ...
//         }))
//     }
//   }

// ── 实现 5：混合数据源（多个来源聚合）──
//
//   export class HybridDataSource extends RoadDataSource {
//     constructor(sources) { this.sources = sources }
//     getCongestionSegments() {
//       return this.sources.flatMap(s => s.getCongestionSegments())
//     }
//   }

// ── 当前使用的数据源 ──
//    切换数据源只需改这一行：
//    - Demo  → new MockDataSource()
//    - API   → new ApiDataSource('https://甲方服务器/api')
//    - 本地  → new JsonFileDataSource(require('./realData.json'))
//    - WebSocket → new WebSocketDataSource('wss://甲方服务器/ws')

export const dataSource = new MockDataSource()
