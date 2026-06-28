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
  trafficBounds: { type: Array, default: null },
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

// 高亮拥堵/缓行路段（数据从 API + LLM 综合）
const hlOutline = { '严重拥堵': '#7f0000', '拥堵': '#7f0000', '缓行': '#7f4d00' }
const hlColors = { '严重拥堵': '#c0392b', '拥堵': '#e74c3c', '缓行': '#e67e22' }

watch(() => props.highlight, (items) => {
  if (!map) return
  if (highlightGroup) highlightGroup.clearLayers()
  if (!items || items.length === 0) return

  if (!props.visible) emit('update:visible', true)

  items.forEach(item => {
    const polyline = item.polyline
    if (!polyline || polyline.length === 0) return
    const color = hlColors[item.status] || '#e74c3c'
    const outline = hlOutline[item.status] || '#7f0000'

    // 深色描边层（稍宽） → 彩色层（稍窄） = 描边效果
    L.polyline(polyline, { color: outline, weight: 9, opacity: 0.6 }).addTo(highlightGroup)
    L.polyline(polyline, { color, weight: 6, opacity: 1.0 })
      .bindPopup(`<b>${item.name}</b><br>${item.status} · ${item.speed}${item.direction ? ' · ' + item.direction : ''}`)
      .addTo(highlightGroup)
  })
}, { deep: true })

// 实时路况渲染（统一着色：绿/黄/红）
const statusColor = { '畅通': '#27ae60', '缓行': '#e67e22', '拥堵': '#e74c3c', '严重拥堵': '#c0392b' }

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

    L.polyline(polyline, {
      color, weight: 4, opacity: 0.85,
    })
      .bindPopup(`<b>${road.name}</b><br>状态: ${road.status}<br>速度: ${road.speed}<br>方向: ${road.direction}`)
      .addTo(realtimeLayer)

    polyline.forEach(([lat, lng]) => { allLats.push(lat); allLngs.push(lng) })
  })

  // 缩放：层级优先级：bounds(城市级) > center+radius(POI级) > polyline范围(兜底)
  if (props.trafficBounds) {
    const bounds = L.latLngBounds(props.trafficBounds)
    nextTick(() => {
      setTimeout(() => {
        if (!map) return
        map.invalidateSize()
        map.fitBounds(bounds, { padding: [20, 20], maxZoom: 13, animate: true, duration: 0.6 })
      }, 80)
    })
  } else if (props.trafficCenter && props.trafficRadius) {
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
