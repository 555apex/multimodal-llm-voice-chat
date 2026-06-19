<template>
  <div class="traffic-map" ref="mapContainer"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import L from 'leaflet'
import { dataSource } from '../data/roadDataSource.js'

const props = defineProps({
  highlight: { type: Array, default: () => [] },
  realtimeTraffic: { type: Array, default: () => [] },
  trafficCenter: { type: Array, default: null },
  trafficRadius: { type: Number, default: 0 },
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible'])

const mapContainer = ref(null)
let map = null
let congestionLayer = null
let markerLayer = null
let polylineLayer = null
let deviceLayer = null
let highlightGroup = null
let realtimeLayer = null
let initialized = false

const congestionColors = { high: '#e74c3c', medium: '#f39c12', low: '#27ae60' }

onMounted(() => {
  nextTick(initMap)
})

onUnmounted(() => {
  if (map) { map.remove(); map = null }
})

const initMap = () => {
  if (!mapContainer.value) return
  if (initialized) return
  initialized = true

  const center = dataSource.getProvinceCenter()

  map = L.map(mapContainer.value, {
    zoomControl: true,
    attributionControl: true,
  }).setView(center, 8)

  // 高德瓦片（GCJ-02 坐标系，与 API polyline 天然对齐）
  const tileSources = [
    {
      url: 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}',
      attr: '© 高德地图',
      maxZoom: 18,
      subdomains: ['1', '2', '3', '4'],
    },
    {
      url: 'https://wprd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}',
      attr: '© 高德地图',
      maxZoom: 18,
      subdomains: ['1', '2', '3', '4'],
    },
  ]

  let tileIndex = 0
  const tileConfig = tileSources[0]
  const tileLayer = L.tileLayer(tileConfig.url, {
    maxZoom: tileConfig.maxZoom,
    updateWhenIdle: false,
    updateWhenZooming: false,
    attribution: tileConfig.attr,
    subdomains: tileConfig.subdomains || 'abc',
    errorTileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII='
  }).addTo(map)

  tileLayer.on('tileerror', () => {
    if (tileIndex < tileSources.length - 1) {
      tileIndex++
      const next = tileSources[tileIndex]
      tileLayer.setUrl(next.url)
      tileLayer.options.attribution = next.attr
      tileLayer.options.maxZoom = next.maxZoom
      tileLayer.options.subdomains = next.subdomains || 'abc'
      console.log('瓦片源切换至:', next.url)
    }
  })

  highlightGroup = L.layerGroup().addTo(map)

  // 容器尺寸稳定后校正
  setTimeout(() => { if (map) map.invalidateSize() }, 300)
}

const drawAllLayers = () => {
  if (!map) return

  if (congestionLayer) map.removeLayer(congestionLayer)
  if (markerLayer) map.removeLayer(markerLayer)
  if (polylineLayer) map.removeLayer(polylineLayer)
  if (deviceLayer) map.removeLayer(deviceLayer)

  congestionLayer = L.layerGroup().addTo(map)
  markerLayer = L.layerGroup().addTo(map)
  polylineLayer = L.layerGroup().addTo(map)
  deviceLayer = L.layerGroup().addTo(map)

  const congestionSegments = dataSource.getCongestionSegments()
  const opacityMap = { high: 0.55, medium: 0.3, low: 0.15 }
  const radiusMap = { high: 16, medium: 10, low: 6 }

  congestionSegments.forEach(seg => {
    const color = congestionColors[seg.level] || '#f39c12'
    const weight = seg.level === 'high' ? 5 : seg.level === 'medium' ? 3 : 2

    L.polyline(seg.latlngs, { color, weight, opacity: 0.7 })
      .bindPopup(`<b>${seg.name}</b><br>${seg.desc}<br>速度: ${seg.speed}<br>延误: ${seg.delay}`)
      .addTo(polylineLayer)

    seg.latlngs.forEach(([lat, lng]) => {
      L.circleMarker([lat, lng], {
        radius: radiusMap[seg.level] || 8,
        color: 'transparent', fillColor: color,
        fillOpacity: opacityMap[seg.level] || 0.2,
        interactive: false
      }).addTo(congestionLayer)
    })
  })

  dataSource.getConstructionSites().forEach(site => {
    L.marker([site.lat, site.lng], {
      icon: L.divIcon({ html: '🚧', iconSize: [28, 28], iconAnchor: [14, 14], popupAnchor: [0, -14], className: 'custom-marker' })
    }).bindPopup(`<b>${site.name}</b><br>${site.desc}<br><i>${site.impact}</i>`).addTo(markerLayer)
  })

  dataSource.getHazardPoints().forEach(h => {
    const emoji = h.riskLevel.includes('橙') ? '🔴' : h.riskLevel.includes('黄') ? '🟡' : '🔵'
    L.marker([h.lat, h.lng], {
      icon: L.divIcon({ html: emoji, iconSize: [24, 24], iconAnchor: [12, 12], popupAnchor: [0, -12], className: 'custom-marker' })
    }).bindPopup(`<b>${h.name}</b><br>类型: ${h.type}<br>风险: ${h.riskLevel}<br>${h.desc}<br>监测: ${h.monitorStatus}`).addTo(markerLayer)
  })

  dataSource.getDeviceStatus().forEach(dev => {
    L.marker([dev.lat, dev.lng], {
      icon: L.divIcon({ html: dev.online ? '🟢' : '⚫', iconSize: [16, 16], iconAnchor: [8, 8], popupAnchor: [0, -8], className: 'custom-marker' })
    }).bindPopup(`<b>${dev.name}</b><br>类型: ${dev.type}<br>${dev.online ? '✅ 在线' : '❌ 离线'}<br>${dev.readings}`).addTo(deviceLayer)
  })
}

// 地图可见性变化 → 重校尺寸
watch(() => props.visible, (v) => {
  if (v && map) {
    nextTick(() => {
      setTimeout(() => map.invalidateSize(), 200)
    })
  }
})

// 控制图层可见性
const toggleBaseLayers = (show) => {
  if (!map) return
  const layers = [congestionLayer, polylineLayer, markerLayer, deviceLayer]
  layers.forEach(l => {
    if (l) show ? map.addLayer(l) : map.removeLayer(l)
  })
}

// LLM 高亮 → 匹配路名到 mock 数据，绘制实际道路线条
watch(() => props.highlight, (items) => {
  if (!map) return
  if (highlightGroup) highlightGroup.clearLayers()
  if (!items || items.length === 0) return

  if (!props.visible) emit('update:visible', true)

  const allSegments = dataSource.getCongestionSegments()
  const allSites = dataSource.getConstructionSites()
  const allHazards = dataSource.getHazardPoints()
  const allDevices = dataSource.getDeviceStatus()

  const matchedLats = []
  const matchedLngs = []
  const typeColors = { congestion: '#e74c3c', construction: '#f39c12', hazard: '#c0392b', device: '#2980b9' }

  items.forEach(item => {
    if (!item.lat || !item.lng) return
    const color = typeColors[item.type] || '#e74c3c'
    let drawn = false

    // 尝试匹配路段名 → 绘制真实道路线条
    if (item.type === 'congestion') {
      const seg = allSegments.find(s => item.name.includes(s.name) || s.name.includes(item.name))
      if (seg) {
        L.polyline(seg.latlngs, { color, weight: 6, opacity: 0.85 })
          .bindPopup(`<b>${seg.name}</b><br>${seg.desc}<br>速度: ${seg.speed}`)
          .addTo(highlightGroup)
        seg.latlngs.forEach(([lat, lng]) => { matchedLats.push(lat); matchedLngs.push(lng) })
        drawn = true
      }
    }

    // 匹配施工点
    if (item.type === 'construction') {
      const site = allSites.find(s => item.name.includes(s.name) || s.name.includes(item.name))
      if (site) {
        L.circleMarker([site.lat, site.lng], { radius: 10, color, fillColor: color, fillOpacity: 0.5, weight: 3 })
          .bindPopup(`<b>${site.name}</b><br>${site.desc}`).addTo(highlightGroup)
        matchedLats.push(site.lat); matchedLngs.push(site.lng)
        drawn = true
      }
    }

    // 匹配灾害点
    if (item.type === 'hazard') {
      const h = allHazards.find(h => item.name.includes(h.name) || h.name.includes(item.name))
      if (h) {
        L.circleMarker([h.lat, h.lng], { radius: 10, color, fillColor: color, fillOpacity: 0.5, weight: 3 })
          .bindPopup(`<b>${h.name}</b><br>${h.desc}`).addTo(highlightGroup)
        matchedLats.push(h.lat); matchedLngs.push(h.lng)
        drawn = true
      }
    }

    // 匹配设备
    if (item.type === 'device') {
      const dev = allDevices.find(d => item.name.includes(d.name) || d.name.includes(item.name))
      if (dev) {
        L.circleMarker([dev.lat, dev.lng], { radius: 8, color, fillColor: color, fillOpacity: 0.5, weight: 2 })
          .bindPopup(`<b>${dev.name}</b><br>${dev.readings}`).addTo(highlightGroup)
        matchedLats.push(dev.lat); matchedLngs.push(dev.lng)
        drawn = true
      }
    }

    // 未匹配 → 用 LLM 给的坐标画标记
    if (!drawn) {
      L.circleMarker([item.lat, item.lng], { radius: 10, color, fillColor: color, fillOpacity: 0.4, weight: 3 })
        .bindPopup(`<b>${item.name}</b><br>类型: ${item.type}`).addTo(highlightGroup)
      matchedLats.push(item.lat); matchedLngs.push(item.lng)
    }
  })

  // 缩放：仅当没有 traffic_data 时才用 highlight 坐标（兜底）
  // 有 traffic_data 时缩放由 realtimeTraffic watch 负责，highlight 只画标记
  if (matchedLats.length > 0 && (!props.realtimeTraffic || props.realtimeTraffic.length === 0)) {
    const minLat = Math.min(...matchedLats), maxLat = Math.max(...matchedLats)
    const minLng = Math.min(...matchedLngs), maxLng = Math.max(...matchedLngs)
    const bounds = L.latLngBounds([[minLat, minLng], [maxLat, maxLng]])

    nextTick(() => {
      setTimeout(() => {
        if (!map) return
        map.invalidateSize()
        map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15, animate: true, duration: 0.6 })
      }, 80)
    })
  }
}, { deep: true })

// 实时路况渲染（高德 API 返回的精确 polyline）
const statusColor = { '畅通': '#27ae60', '缓行': '#f39c12', '拥堵': '#e74c3c', '严重拥堵': '#c0392b' }
const statusWeight = { '畅通': 3, '缓行': 4, '拥堵': 6, '严重拥堵': 7 }

watch(() => props.realtimeTraffic, (roads) => {
  if (!map) return
  if (realtimeLayer) realtimeLayer.clearLayers()
  if (!realtimeLayer) realtimeLayer = L.layerGroup().addTo(map)
  if (!roads || roads.length === 0) return

  if (!props.visible) emit('update:visible', true)

  const allLats = []
  const allLngs = []

  roads.forEach(road => {
    const polyline = road.polyline
    if (!polyline || polyline.length < 2) return

    const color = statusColor[road.status] || '#7f8c8d'
    const weight = statusWeight[road.status] || 3

    L.polyline(polyline, {
      color, weight, opacity: 0.8,
    })
      .bindPopup(`<b>${road.name}</b><br>状态: ${road.status}<br>速度: ${road.speed}<br>方向: ${road.direction}`)
      .addTo(realtimeLayer)

    polyline.forEach(([lat, lng]) => { allLats.push(lat); allLngs.push(lng) })
  })

  // 缩放：优先用 center+radius（精确对应查询范围），否则用 polyline 范围兜底
  if (props.trafficCenter && props.trafficRadius) {
    const [clat, clng] = props.trafficCenter
    const half = props.trafficRadius / 111000  // 米 → 度（近似）
    const bounds = L.latLngBounds([[clat - half, clng - half], [clat + half, clng + half]])
    nextTick(() => {
      setTimeout(() => {
        if (!map) return
        map.invalidateSize()
        map.fitBounds(bounds, { padding: [20, 20], maxZoom: 15, animate: true, duration: 0.6 })
      }, 80)
    })
  } else if (allLats.length > 0) {
    const minLat = Math.min(...allLats), maxLat = Math.max(...allLats)
    const minLng = Math.min(...allLngs), maxLng = Math.max(...allLngs)
    const bounds = L.latLngBounds([[minLat, minLng], [maxLat, maxLng]])

    nextTick(() => {
      setTimeout(() => {
        if (!map) return
        map.invalidateSize()
        map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15, animate: true, duration: 0.6 })
      }, 80)
    })
  }
}, { deep: true })
</script>

<style scoped>
.traffic-map {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
  background: #a3c8db;
}

:deep(.custom-marker) {
  background: none !important;
  border: none !important;
}

:deep(.leaflet-container) {
  width: 100% !important;
  height: 100% !important;
}
</style>
