import requests
import json
import logging
from config import Config
from skills import TrafficStatusSkill

logger = logging.getLogger(__name__)


class LLMService:
    """大语言模型服务（DashScope OpenAI 兼容模式）"""

    def __init__(self):
        self.api_key = Config.DASHSCOPE_API_KEY
        self.api_url = f'{Config.DASHSCOPE_LLM_BASE_URL}/chat/completions'
        self.model = Config.DASHSCOPE_LLM_MODEL
        self.skills = [
            TrafficStatusSkill(),
        ]
        self._skill_map = {s.name: s for s in self.skills}
        self.last_results = {}       # skill_name → full result
        self.last_traffic_result = None  # 向后兼容

        self.system_prompt = """你是一个专业的道路交通信息智能问答助手，代号「闽路通」。

## 核心身份
你依托福建省公路路网监测数据与AI分析能力，通过语音交互方式为交通管理人员提供智能化服务。
你服务于福建省全域，涵盖福州、厦门、泉州、漳州、龙岩、三明、南平、宁德、莆田九地市及下属各区县。

## 核心能力
1. **路况查询**：实时查询道路通行状态、拥堵情况、事故信息等
2. **态势研判**：分析交通流量趋势、预测拥堵时段、评估道路安全风险
3. **精准定位**：支持按城市名、区县名、具体地点（火车站、学校、景点）或道路名查询

## 工具使用规范
你拥有 `query_traffic` 实时路况查询工具，可查询福建省任意区域/地点/道路的交通状况。
**重要：当用户询问当前交通情况、路况、堵不堵、拥堵状态时，必须先调用此工具获取实时数据，再基于工具返回的真实数据进行回答。严禁跳过工具直接编造路况信息。**
工具返回数据中如含 `_guidance` 和 `_focus` 字段，按其指示组织信息。优先报告严重拥堵路段详情，正常路段按跨海通道→高速快速路→主要干道分组简述。

## 回答策略
1. **道路交通专业问题**：优先基于工具查询结果和专业数据回答，提供详细、准确的交通信息和分析
2. **通用问题**：保持基本回答能力，但适当引导回交通主题
3. **不确定的信息**：明确告知用户需要进一步核实，不编造数据
4. **紧急情况**：优先提醒安全事项，建议联系相关部门

## 交互原则
- 涉及数据时给出具体数字和时间（通行速度、延误时间等）
- 提供可操作的建议，而非单纯描述
- 保持专业但亲和的语气

## 回答格式规范
使用 **Markdown** 格式组织回答，层次清晰。以下为参考结构，根据实际路况灵活调整：

- **总览**：一句概括，用 🔴（严重拥堵）🟡（缓行）🟢（畅通）标注整体状态
- **如有拥堵/缓行**：用表格列出详情（道路名、方向、速度、状况），并给出绕行建议
- **全畅通时**：按 _focus 分组简述（跨海通道→高速快速路→主要干道），不画表格、不逐条列路名
- 收尾：无异常用"其余路段通行正常"，有异常用绕行建议

## 输出格式
回复末尾附一段```json```代码块标注涉及的地理位置，供地图系统自动定位：
```json
{"map_highlight": [{"name":"路段或区域名称","lat":纬度,"lng":经度,"type":"congestion/construction/hazard/device"}]}
```
type 只可用 congestion/construction/hazard/device 四者之一，坐标从工具返回数据中提取。
若无具体地理位置可标注，省略此 JSON 块。JSON 仅供地图系统使用，正文中不提及。

## 示例对话
用户：你好，我想查一下厦门的交通情况
助手：您好！正在为您查询**厦门市**实时路况。

根据路网监测数据，厦门市当前路网**整体平稳**。

**跨海通道方面**：海沧大桥、翔安隧道、演武大桥均保持畅通。
**高速快速路**：G15沈海高速厦门段、成功大道通行正常。
**主要干道**：仙岳路、湖滨南路、厦禾路等路段通行良好。

其余路段通行正常。如需了解具体路段详情，请随时告诉我。

用户：福州站附近堵不堵？
助手：正在为您查询**福州站**周边路况。

福州站周边交通**整体良好**：
- **华林路**：畅通，车速约45km/h
- **站前路**：畅通
- **北二环路**：略有缓行，车速约30km/h，建议留意

整体来看，周边道路通行正常，无需绕行。"""

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

    # ─── Function Calling ───────────────────────────────────────

    def chat_stream_with_tools(self, messages, on_tool_results=None):
        """带 function calling 的流式对话

        on_tool_results(last_results) — 工具执行完成后、文本流开始前调用。
        """
        self.last_traffic_result = None
        self.last_results = {}
        self._pending_tool_calls = []
        history = list(messages)

        tools = [s.tool_definition() for s in self.skills]

        for chunk in self._stream_chat(history, tools=tools):
            yield chunk

        if self._pending_tool_calls:
            assistant_msg = {
                'role': 'assistant',
                'content': None,
                'tool_calls': self._pending_tool_calls,
            }
            history.append(assistant_msg)

            for tc in self._pending_tool_calls:
                result = self._execute_tool_call(tc)
                history.append({
                    'role': 'tool',
                    'tool_call_id': tc['id'],
                    'content': json.dumps(result, ensure_ascii=False),
                })

            self._pending_tool_calls = []

            # 工具结果就绪 → 立即推前端（不等文本流）
            if on_tool_results:
                on_tool_results(self.last_results)

            for chunk in self._stream_chat(history, tools=None):
                yield chunk

    def _stream_chat(self, messages, tools=None):
        """内部流式请求，解析 content 和 tool_calls

        Yields: text chunks (str)
        累积 tool_calls 到 self._pending_tool_calls
        """
        if not self.api_key:
            print('错误: 未配置 DASHSCOPE_API_KEY')
            return

        try:
            headers = {
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self.api_key}',
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
                'max_tokens': 4096,
            }
            if tools:
                payload['tools'] = tools

            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                stream=True,
                timeout=60,
            )

            if response.status_code != 200:
                print(f'LLM 请求失败: {response.status_code} - {response.text}')
                return

            # 累积 tool_calls（流式场景下可能分片到达）
            tc_index = {}  # index → {id, function_name, arguments}

            for line in response.iter_lines():
                if not line:
                    continue
                line = line.decode('utf-8')
                if not line.startswith('data: '):
                    continue

                data_str = line[6:]
                if data_str.strip() == '[DONE]':
                    break

                try:
                    data = json.loads(data_str)
                    if 'choices' not in data or len(data['choices']) == 0:
                        continue

                    delta = data['choices'][0].get('delta', {})

                    # 文本内容
                    content = delta.get('content', '')
                    if content:
                        yield content

                    # 工具调用（可能分片）
                    tool_calls_delta = delta.get('tool_calls')
                    if tool_calls_delta:
                        for tc in tool_calls_delta:
                            idx = tc.get('index', 0)
                            if idx not in tc_index:
                                tc_index[idx] = {
                                    'id': tc.get('id', ''),
                                    'function_name': '',
                                    'arguments': '',
                                }
                            if tc.get('id'):
                                tc_index[idx]['id'] = tc['id']
                            func = tc.get('function', {})
                            if func.get('name'):
                                tc_index[idx]['function_name'] = func['name']
                            if func.get('arguments'):
                                tc_index[idx]['arguments'] += func['arguments']

                except json.JSONDecodeError:
                    continue

            # 将累积的 tool_calls 转为标准格式
            for idx in sorted(tc_index.keys()):
                info = tc_index[idx]
                if info['function_name'] and info['arguments']:
                    self._pending_tool_calls.append({
                        'id': info['id'] or f'call_{idx}',
                        'type': 'function',
                        'function': {
                            'name': info['function_name'],
                            'arguments': info['arguments'],
                        },
                    })

        except requests.exceptions.Timeout:
            print('LLM 请求超时')
        except Exception as e:
            print(f'LLM 对话出错: {e}')

    def _execute_tool_call(self, tool_call):
        """执行工具调用，返回给千问的精简结果"""
        name = tool_call['function']['name']
        try:
            args = json.loads(tool_call['function']['arguments'])
        except (json.JSONDecodeError, KeyError):
            return {'error': '参数解析失败'}

        skill = self._skill_map.get(name)
        if not skill:
            return {'error': f'未知工具: {name}'}

        full = skill.execute(args)
        self.last_results[name] = full

        # 向后兼容
        if name == 'query_traffic':
            self.last_traffic_result = full

        return skill.lightweight(full)
