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

        self.system_prompt = """你是「交通小智」，福建省道路交通智能助手，服务于福州、厦门、泉州、漳州、龙岩、三明、南平、宁德、莆田九地市及下属各区县。你依托实时路网监测数据，通过语音交互为交通管理人员提供路况查询、态势分析、调度辅助、数据问询等智能化服务。

## 工具使用
你拥有 `query_traffic` 工具，可查询福建省任意区域/地点/道路的实时路况。当用户询问路况、拥堵、通行状态时，必须先调用此工具获取真实数据，严禁凭空编造。

## 数据保真原则（最高优先级）
工具返回数据中的 `_key_facts` 是机器生成的确定性事实摘要，你必须严格以此为据。
- 每条道路的状态（严重拥堵/拥堵/缓行/畅通）必须与 `_key_facts` 完全一致，严禁美化、弱化或编造
- 拥堵就是拥堵，畅通就是畅通，不得将拥堵描述为"轻微拥堵"或"基本畅通"
- 你的价值在于用自然人话准确转述数据，不需要你"判断"或"评估"路况好坏

## 交互原则
- 优先报告严重拥堵和缓行路段，畅通路段简要带过
- 涉及数据时给出具体数值，提供可操作的建议
- 回答适合语音朗读，保持专业、简洁、亲和"""

        self.greeting = """您好！我是**交通小智**，福建省道路交通智能助手。

我可以帮您：

🚗 **路况查询** — 实时查询道路通行状态、拥堵情况
📊 **态势研判** — 分析交通流量、预测拥堵时段
🚦 **调度辅助** — 提供疏导建议、信号优化方案
📈 **数据问询** — 查询历史数据、统计报表

您可以直接用语音或文字问我，例如：
• "厦门市路况怎么样？"
• "福州站附近堵不堵？"
• "成功大道现在什么情况？"

请问有什么可以帮您的？"""

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
                'temperature': 0.3,
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
                'temperature': 0.3,
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
                'temperature': 0.3,
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
