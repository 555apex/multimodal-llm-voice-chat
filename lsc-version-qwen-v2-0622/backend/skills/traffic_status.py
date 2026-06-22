import requests
from urllib.parse import quote

from .base import BaseSkill

# 福建省九地市（城市→坐标+半径映射，地理编码未命中时的已知回退）
FUJIAN_CITIES = {
    '福州市': {'lat': 26.07, 'lng': 119.30, 'radius': 15000},
    '厦门市': {'lat': 24.48, 'lng': 118.09, 'radius': 15000},
    '泉州市': {'lat': 24.87, 'lng': 118.67, 'radius': 12000},
    '漳州市': {'lat': 24.51, 'lng': 117.65, 'radius': 12000},
    '龙岩市': {'lat': 25.08, 'lng': 117.02, 'radius': 10000},
    '三明市': {'lat': 26.26, 'lng': 117.63, 'radius': 10000},
    '南平市': {'lat': 26.64, 'lng': 118.18, 'radius': 10000},
    '宁德市': {'lat': 26.67, 'lng': 119.55, 'radius': 10000},
    '莆田市': {'lat': 25.45, 'lng': 119.01, 'radius': 10000},
}

FUJIAN_CITY_NAMES = list(FUJIAN_CITIES.keys())

STATUS_MAP = {'1': '畅通', '2': '缓行', '3': '拥堵', '4': '严重拥堵'}
LEVEL_MAP = {'1': 'low', '2': 'medium', '3': 'high', '4': 'high'}

# geocoding level → 路况查询半径（米）
LEVEL_RADIUS = {
    '省': 30000, '市': 25000,
    '区县': 8000, '乡镇': 5000,
    '兴趣点': 2000, '地名地址': 2000,
    '公交地铁站点': 1500, '交通服务相关': 2000,
    '商务住宅': 2000, '科教文化服务': 2000,
    '道路': 1500, '门牌号': 1000,
}

# 城市矩形缓存（行政边界不变，一次查询永久有效）
_rect_cache = {}


class TrafficStatusSkill(BaseSkill):
    name = 'query_traffic'
    description = (
        '查询福建省任意区域/地点/道路的实时交通路况。'
        '支持城市名、区县名、具体地点（火车站、学校、景点等）和道路名。'
        '当用户询问当前路况、堵不堵、通行状态、交通情况时使用此工具。'
    )
    parameters = {
        'type': 'object',
        'properties': {
            'area': {
                'type': 'string',
                'description': (
                    '用户查询的原始地点表述，严禁截断简化。'
                    '例："厦门市政府附近"→传"厦门市政府"（不是"厦门市"）；'
                    '"福州站周边"→传"福州站"（不是"福州市"）；'
                    '"厦门市路况"→才传"厦门市"。'
                    '支持城市名、区县名、具体地点、道路名。'
                ),
            }
        },
        'required': ['area'],
    }

    def __init__(self):
        from config import Config
        self.api_key = Config.GAODE_API_KEY
        self.traffic_url = 'https://restapi.amap.com/v3/traffic/status/circle'
        self.geocode_url = 'https://restapi.amap.com/v3/geocode/geo'
        self.district_url = 'https://restapi.amap.com/v3/config/district'

    # ── 主入口 ──────────────────────────────────────────

    def execute(self, args: dict) -> dict:
        area = args.get('area', '').strip()
        if not area:
            return {'error': '未指定区域', 'area': '', 'summary': '请提供查询区域'}

        lat, lng, radius, label, level = self._resolve_location(area)
        if lat is None:
            return {
                'error': '地理编码失败',
                'area': area,
                'summary': f'未能在福建省找到「{area}」的位置信息，请尝试输入更具体的地名',
            }

        # 城市级 → 下属区县中心采样
        if level == '市':
            result = self._district_sample(label)
            if result:
                return result

        data = self._call_traffic(lat, lng, radius)
        if not data:
            return {
                'error': '无路况数据',
                'area': area,
                'summary': (
                    f'「{area}」周边暂未覆盖实时路况监测数据。'
                    f'该区域可能为新建区域、偏远郊区或监测设备尚未部署。'
                    f'请告知用户可尝试查询附近主要城区（如区县中心）的交通情况。'
                ),
            }
        result = self._format(data, label)
        result['center'] = [lat, lng]
        result['query_radius'] = radius
        return result

    # ── 位置解析：城市字典 → 地理编码 ─────────────────

    def _resolve_location(self, area):
        """返回 (lat, lng, radius, display_label, level)"""
        # 1）已知城市名 → 行政区划中心 + 25km 半径（避免偏心导致的覆盖率不足）
        if area in FUJIAN_CITIES:
            center = self._get_city_center(area)
            if center:
                return center[0], center[1], 25000, area, '市'
            c = FUJIAN_CITIES[area]
            return c['lat'], c['lng'], c['radius'], area, '市'

        # 2）地理编码
        geo = self._geocode(area)
        if not geo:
            return None, None, None, area, None

        lat, lng = geo['location']
        level = geo['level']

        # 地级市 → 大半径覆盖
        if level == '市':
            center = self._get_city_center(area)
            if center:
                return center[0], center[1], 25000, geo.get('formatted_address', area), level
            radius = 25000
        else:
            radius = self._radius_for_level(level)

        label = geo.get('city', area) if area in FUJIAN_CITIES else (geo.get('formatted_address', area))
        return lat, lng, radius, label, level

    # ── 地理编码（福建优先）─────────────────────────────

    def _geocode(self, address):
        """地理编码，返回 {location:(lat,lng), level, city, formatted_address} 或 None"""
        if not self.api_key:
            return None

        all_candidates = []

        # 策略 A：直接查，过滤福建省结果
        candidates = self._call_geocode(address, city=None)
        all_candidates.extend(g for g in candidates if g.get('province') == '福建省')

        # 策略 B：逐一用福建九地市作为 city 限定再查
        for city in FUJIAN_CITY_NAMES:
            candidates = self._call_geocode(address, city=city)
            all_candidates.extend(candidates)

        if not all_candidates:
            return None

        best = self._pick_best(all_candidates, address)

        # 地理编码误判防护：返回 level=市 但地址不是单纯城市名 → 可能是 POI
        # 例："厦门市政府" geocode 返回 level=市（把"市"当城市标识）
        if best['level'] == '市' and address not in FUJIAN_CITIES:
            poi = self._search_poi(address)
            if poi:
                return poi

        return best

    def _search_poi(self, address):
        """POI 搜索兜底，返回 {location, level, city, formatted_address} 或 None"""
        params = {
            'key': self.api_key,
            'keywords': address,
            'extensions': 'base',
            'offset': '1',
        }
        try:
            resp = requests.get(
                'https://restapi.amap.com/v3/place/text',
                params=params, timeout=5,
            )
            data = resp.json()
            pois = data.get('pois', [])
            if not pois:
                return None

            p = pois[0]
            lng, lat = p['location'].split(',')
            level = '兴趣点'  # POI 搜索返回的都是具体地点
            return {
                'location': (float(lat), float(lng)),
                'level': level,
                'city': p.get('cityname', ''),
                'formatted_address': p.get('address', p['name']),
            }
        except Exception:
            return None

    def _call_geocode(self, address, city=None):
        params = {'key': self.api_key, 'address': address}
        if city:
            params['city'] = city
        try:
            resp = requests.get(self.geocode_url, params=params, timeout=5)
            data = resp.json()
            if data.get('status') == '1':
                return data.get('geocodes', [])
        except Exception:
            pass
        return []

    @staticmethod
    def _pick_best(candidates, address):
        """从候选中选最优：九地市主城优先，再按 level 具体度排"""
        known_cities = set(FUJIAN_CITIES.keys())
        level_order = {k: i for i, k in enumerate([
            '道路', '门牌号', '兴趣点', '公交地铁站点', '商务住宅',
            '科教文化服务', '交通服务相关', '地名地址', '乡镇', '区县', '市', '省',
        ])}
        city_rank = {c: i for i, c in enumerate(FUJIAN_CITY_NAMES)}
        # 排序：(0=主城/1=非主城, 主城内越靠前越优先, level具体度)
        candidates.sort(key=lambda g: (
            0 if g.get('city', '') in known_cities else 1,
            city_rank.get(g.get('city', ''), 99),
            level_order.get(g.get('level', ''), 99),
        ))

        best = candidates[0]
        lng, lat = best['location'].split(',')
        return {
            'location': (float(lat), float(lng)),
            'level': best.get('level', '兴趣点'),
            'city': best.get('city', ''),
            'formatted_address': best.get('formatted_address', address),
        }

    @staticmethod
    def _radius_for_level(level):
        return LEVEL_RADIUS.get(level, 3000)

    # ── 路况查询 ────────────────────────────────────────

    def _call_traffic(self, lat, lng, radius):
        if not self.api_key:
            return None
        params = {
            'key': self.api_key,
            'location': f'{lng},{lat}',
            'radius': radius,
            'extensions': 'all',
        }
        try:
            resp = requests.get(self.traffic_url, params=params, timeout=10)
            data = resp.json()
            if data.get('status') != '1':
                return None
            return data
        except Exception:
            return None

    def _get_city_center(self, city_name):
        """获取高德行政区划的城市中心坐标 (lat, lng)，含缓存"""
        global _rect_cache
        key = f'center_{city_name}'
        if key in _rect_cache:
            return _rect_cache[key]

        params = {
            'key': self.api_key,
            'keywords': city_name,
            'extensions': 'base',
            'subdistrict': '0',
        }
        try:
            resp = requests.get(self.district_url, params=params, timeout=5)
            data = resp.json()
            districts = data.get('districts', [])
            if not districts:
                return None

            center_str = districts[0].get('center', '')
            if not center_str:
                return None

            lng, lat = center_str.split(',')
            center = (float(lat), float(lng))
            _rect_cache[key] = center
            print(f'[CityCenter] {city_name}: {center}')
            return center
        except Exception as e:
            print(f'[CityCenter] {city_name} 失败: {e}')
            return None

    def _get_city_boundary(self, city_name):
        """获取城市行政区划矩形 (minLng, minLat, maxLng, maxLat)，含缓存"""
        global _rect_cache
        key = f'boundary_{city_name}'
        if key in _rect_cache:
            return _rect_cache[key]

        params = {
            'key': self.api_key,
            'keywords': city_name,
            'extensions': 'all',
            'subdistrict': '0',
        }
        try:
            resp = requests.get(self.district_url, params=params, timeout=5)
            data = resp.json()
            districts = data.get('districts', [])
            if not districts:
                return None
            polyline = districts[0].get('polyline', '')
            if not polyline:
                return None
            lngs, lats = [], []
            for segment in polyline.split('|'):
                for pair in segment.split(';'):
                    parts = pair.split(',')
                    if len(parts) >= 2:
                        lngs.append(float(parts[0]))
                        lats.append(float(parts[1]))
            if not lngs:
                return None
            rect = (min(lngs), min(lats), max(lngs), max(lats))
            _rect_cache[key] = rect
            return rect
        except Exception:
            return None

    # ── 城市级区县采样（普适所有城市，采样点保证在陆地） ──

    def _get_district_centers(self, city_name):
        """获取下属区县中心坐标 [(lat, lng, name), ...]，含缓存"""
        global _rect_cache
        key = f'districts_{city_name}'
        if key in _rect_cache:
            return _rect_cache[key]

        params = {
            'key': self.api_key,
            'keywords': city_name,
            'extensions': 'base',
            'subdistrict': '1',
        }
        try:
            resp = requests.get(self.district_url, params=params, timeout=5)
            data = resp.json()
            districts = data.get('districts', [])
            if not districts:
                return None

            centers = []
            for dist in districts[0].get('districts', []):
                center_str = dist.get('center', '')
                if center_str:
                    lng, lat = center_str.split(',')
                    centers.append((float(lat), float(lng), dist['name']))

            _rect_cache[key] = centers
            print(f'[Districts] {city_name}: {len(centers)} 区县')
            return centers if centers else None
        except Exception as e:
            print(f'[Districts] {city_name} 失败: {e}')
            return None

    def _district_sample(self, city_name):
        """下属区县中心多点采样 → 合并去重 → 返回统一格式"""
        centers = self._get_district_centers(city_name)
        if not centers:
            return None

        # 半径自适应：区县越多，单点半径越小
        radius = max(8000, min(15000, 60000 // len(centers)))

        seen = set()
        all_roads = []
        active_lats = []  # 有路数据的区县中心坐标
        active_lngs = []

        for lat, lng, dist_name in centers:
            data = self._call_traffic(lat, lng, radius)
            if not data:
                continue
            has_roads = False
            for r in data.get('trafficinfo', {}).get('roads', []):
                has_roads = True
                name = r.get('name', '')
                if name and name not in seen:
                    seen.add(name)
                    polyline = self._parse_polyline(r.get('polyline', ''))
                    all_roads.append({
                        'name': name,
                        'status': STATUS_MAP.get(r.get('status', ''), '未知'),
                        'level': LEVEL_MAP.get(r.get('status', ''), 'low'),
                        'speed': r.get('speed', '') + ' km/h' if r.get('speed') else '未知',
                        'direction': r.get('direction', ''),
                        'polyline': polyline,
                    })
            if has_roads:
                active_lats.append(lat)
                active_lngs.append(lng)

        if not all_roads:
            return None

        status_count = {}
        for rd in all_roads:
            st = rd['status']
            status_count[st] = status_count.get(st, 0) + 1
        parts = [f'{v} 条{k}' for k, v in status_count.items()]
        summary = f"共 {len(all_roads)} 条道路" + ('，其中 ' + '、'.join(parts) if parts else '')

        # 视图范围：只框住有路数据的区县中心 + 采样半径 padding
        bounds = None
        if active_lats:
            pad = radius / 111000  # 采样半径 → 度数 padding
            bounds = [
                [min(active_lats) - pad, min(active_lngs) - pad],
                [max(active_lats) + pad, max(active_lngs) + pad],
            ]

        print(f'[DistrictSample] {city_name}: {len(centers)} 区县采样 r={radius}m '
              f'→ {len(all_roads)} 条道路, active={len(active_lats)} 区县（去重后）')
        return {
            'area': city_name, 'roads': all_roads, 'summary': summary, 'total': len(all_roads),
            'bounds': bounds,
        }

    # ── 格式化 ──────────────────────────────────────────

    def _format(self, data, label):
        roads = []
        for r in data.get('trafficinfo', {}).get('roads', []):
            polyline = self._parse_polyline(r.get('polyline', ''))
            roads.append({
                'name': r.get('name', ''),
                'status': STATUS_MAP.get(r.get('status', ''), '未知'),
                'level': LEVEL_MAP.get(r.get('status', ''), 'low'),
                'speed': r.get('speed', '') + ' km/h' if r.get('speed') else '未知',
                'direction': r.get('direction', ''),
                'polyline': polyline,
            })

        status_count = {}
        for rd in roads:
            st = rd['status']
            status_count[st] = status_count.get(st, 0) + 1
        parts = [f'{v} 条{k}' for k, v in status_count.items()]
        summary = f"共 {len(roads)} 条道路" + ('，其中 ' + '、'.join(parts) if parts else '')

        return {'area': label, 'roads': roads, 'summary': summary, 'total': len(roads)}

    # ── 千问 / 前端 数据切片 ─────────────────────────────

    def lightweight(self, result: dict) -> dict:
        light = super().lightweight(result)
        roads = result.get('roads', [])
        light['_focus'] = self._build_focus(roads)
        light['_guidance'] = (
            '1. 先给一句总览，用 🔴🟡🟢 标注整体状态。'
            '2. 如果有拥堵或缓行路段 → 用表格列出（道路|方向|速度|状况），逐条展开。'
            '3. 然后按 _focus 分组简述：跨海通道→高速快速路→主要干道。'
            '   有异常报异常，全畅通则一句话带过（如"跨海通道均畅通"）。'
            '4. 全畅通时不画表格、不逐条列路名，用 _focus 分组简述即可。'
            '5. 如有拥堵可给绕行建议，无拥堵则"其余路段通行正常"收尾。'
        )
        return light

    # ── 关键基础设施识别 ──────────────────────────────────

    @staticmethod
    def _build_focus(roads):
        """从道路列表中自动识别跨海通道、高速快速路、主要干道"""
        bridges = []   # 桥/隧/通道
        highways = []  # 高速/快速路/大道
        arterials = [] # 主要干道（路/街）

        bridge_kw = ('桥', '隧道', '通道')
        highway_kw = ('高速', '快速路', '大道', 'G15', 'G25', 'G70', 'G76', 'G319', 'G324', 'G205')
        road_kw = ('路', '街')

        for rd in roads:
            name = rd['name']
            if any(k in name for k in bridge_kw):
                bridges.append(name)
            elif any(k in name for k in highway_kw):
                highways.append(name)
            elif any(k in name for k in road_kw):
                arterials.append(name)

        focus = {}
        if bridges:
            focus['跨海通道/桥梁隧道'] = sorted(set(bridges))
        if highways:
            focus['高速/快速路/主干大道'] = sorted(set(highways))
        if arterials:
            focus['主要道路'] = sorted(set(arterials))[:8]  # 最多 8 条，避免太长

        return focus

    # ── 工具 ────────────────────────────────────────────

    @staticmethod
    def _parse_polyline(polyline_str):
        if not polyline_str:
            return []
        points = []
        for pair in polyline_str.split(';'):
            if ',' in pair:
                lng, lat = pair.split(',')
                points.append([float(lat), float(lng)])
        return points
