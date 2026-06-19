import requests
import json
from config import Config


class LLMService:
    """大语言模型服务（DashScope OpenAI 兼容模式）"""

    def __init__(self):
        self.api_key = Config.DASHSCOPE_API_KEY
        self.api_url = f'{Config.DASHSCOPE_LLM_BASE_URL}/chat/completions'
        self.model = Config.DASHSCOPE_LLM_MODEL

        self.system_prompt = """你是「闽路通」——福建省普通公路交通智能助手。

## 服务范围
你服务于福建省全域，涵盖福州、厦门、泉州、漳州、龙岩、三明、南平、宁德、莆田九地市，
以及下属各区县。用户询问省内任何市/区/县的交通情况，你都能回答。

## 范围限定
- 若询问福建省外（如广东、浙江、江西等）的交通 → 礼貌说明仅提供福建省内服务
- 若询问与交通完全无关的话题（如美食、旅游攻略等）→ 引导回交通业务

## 当前可查询数据

### 福州
高速/快速路：G15沈海高速福州段、G70福银高速福州至闽侯段、福州绕城高速
城区干道：五四路、五一路、鼓屏路、华林路、杨桥路、西二环、乌山路、湖东路、八一七路、东街、台江路、六一路、福马路、金山大道

### 厦门
跨海通道：海沧大桥、翔安隧道、厦漳跨海大桥、演武大桥
城区干道：成功大道、厦禾路、湖滨南路、湖滨北路、鹭江道、嘉禾路(G324)、环岛南路、莲前东路、莲前西路、湖里大道、仙岳路、吕岭路、金尚路

### 泉州
高速/国道：G15沈海高速泉州段、G324国道泉州段
城区干道：温陵路、刺桐路、丰泽街、泉秀街、田安路、坪山路、晋江机场周边

### 漳州
高速：G15沈海高速漳州段、G76厦蓉高速漳州段
城区干道：胜利路、水仙大街、南昌路、延安北路、丹霞路、新浦路

### 龙岩
高速/国道：G76厦蓉高速龙岩段、G319国道龙岩段
城区干道：龙岩大道、解放路、华莲路

### 三明
高速/国道：G25长深高速三明段、G205国道三明段
城区干道：列东街、新市路、麒麟山路、劲松路

### 南平
高速：G25长深高速南平段、G70福银高速南平段
城区干道：中山路、八一路、滨江路、马坑路

### 宁德
高速：G15沈海高速宁德段、G1514宁上高速
城区干道：蕉城路、闽东路、福宁路、鹤峰路

### 莆田
高速：G15沈海高速莆田段
城区干道：胜利路、学园路、荔城大道、东圳路、文献路

### 施工与灾害
施工：G15福州段路面维修、G70南平段桥梁加固、G319龙岩段边坡防护、S203三明段路面改造、厦门湖滨北路路面翻新、泉州刺桐路管网改造
灾害隐患：武夷山滑坡点、三明泥石流点、宁德屏南边坡点、龙岩长汀塌方点
监测设备：福州绕城气象站、厦门海沧车检器、武夷山视频监控、南平延平气象站

## 福建省各地市坐标参考（用于生成 map_highlight 的经纬度）
- 福州市 26.07/119.30 | 厦门市 24.48/118.09 | 泉州市 24.87/118.67
- 漳州市 24.51/117.65 | 龙岩市 25.08/117.02 | 三明市 26.26/117.63
- 南平市 26.64/118.18 | 宁德市 26.67/119.55 | 莆田市 25.45/119.01
- 厦门思明区 24.45/118.08 | 厦门湖里区 24.51/118.10 | 厦门海沧区 24.48/118.03
- 厦门集美区 24.57/118.10 | 厦门同安区 24.72/118.15 | 厦门翔安区 24.62/118.25
- 福州鼓楼区 26.08/119.30 | 福州台江区 26.06/119.31 | 福州仓山区 26.04/119.32
- 福州晋安区 26.08/119.33 | 福州马尾区 25.99/119.47

## 回答要求
- 交通运输行业专业术语，简洁明了
- 路段说明道路名称（含编号）、方向
- 拥堵标注通行速度和延误时间
- 施工标注内容和预计完工时间
- 回复末尾附一段```json```代码块标注涉及的地理坐标，供地图系统自动定位：
```json
{"map_highlight": [{"name":"路段或区域名称","lat":纬度,"lng":经度,"type":"congestion/construction/hazard/device"}]}
```
务必严格使用上述 JSON 格式，type 只用这四个值之一。若无具体地理位置可标注，省略此 JSON 块。
以上 JSON 仅供地图系统使用，你在正文中不需要提及或解释它。"""

    def chat_stream(self, messages):
        """
        流式对话

        Args:
            messages: 对话历史列表，格式: [{'role': 'user'/'assistant', 'content': '...'}]

        Yields:
            生成的文本片段
        """
        if not self.api_key:
            print('错误: 未配置 DASHSCOPE_API_KEY')
            return

        try:
            headers = {
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self.api_key}'
            }

            api_messages = [
                {'role': 'system', 'content': self.system_prompt}
            ] + messages

            payload = {
                'model': self.model,
                'messages': api_messages,
                'stream': True,
                'temperature': 1.0,
                'top_p': 0.95,
                'max_tokens': 4096
            }

            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                stream=True,
                timeout=60
            )

            if response.status_code != 200:
                print(f'LLM 请求失败: {response.status_code} - {response.text}')
                return

            for line in response.iter_lines():
                if line:
                    line = line.decode('utf-8')

                    if line.startswith('data: '):
                        data_str = line[6:]

                        if data_str.strip() == '[DONE]':
                            break

                        try:
                            data = json.loads(data_str)
                            if 'choices' in data and len(data['choices']) > 0:
                                delta = data['choices'][0].get('delta', {})
                                content = delta.get('content', '')
                                if content:
                                    yield content
                        except json.JSONDecodeError:
                            continue

        except requests.exceptions.Timeout:
            print('LLM 请求超时')
        except Exception as e:
            print(f'LLM 对话出错: {e}')

    def chat(self, messages):
        """非流式对话"""
        if not self.api_key:
            print('错误: 未配置 DASHSCOPE_API_KEY')
            return None

        try:
            headers = {
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self.api_key}'
            }

            api_messages = [
                {'role': 'system', 'content': self.system_prompt}
            ] + messages

            payload = {
                'model': self.model,
                'messages': api_messages,
                'stream': False,
                'temperature': 1.0,
                'top_p': 0.95,
                'max_tokens': 4096
            }

            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=60
            )

            if response.status_code == 200:
                result = response.json()
                if 'choices' in result and len(result['choices']) > 0:
                    return result['choices'][0]['message']['content']
            else:
                print(f'LLM 请求失败: {response.status_code} - {response.text}')

        except Exception as e:
            print(f'LLM 对话出错: {e}')

        return None
