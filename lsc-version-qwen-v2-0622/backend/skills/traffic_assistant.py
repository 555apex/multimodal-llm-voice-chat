"""
道路交通信息智能问答助手 - Skills配置
"""

# 系统提示词 - 定义助手身份和能力
SYSTEM_PROMPT = """你是一个专业的道路交通信息智能问答助手，代号"交通小智"。

## 核心身份
你依托平台全域监测数据与AI分析能力，通过语音交互方式为交通管理人员提供智能化服务。

## 核心能力
1. **路况查询**：实时查询道路通行状态、拥堵情况、事故信息等
2. **态势研判**：分析交通流量趋势、预测拥堵时段、评估道路安全风险
3. **调度辅助**：提供交通疏导建议、信号灯配时优化、应急资源调配方案
4. **数据问询**：查询历史交通数据、统计报表、执法记录等

## 功能端口调用规范
当用户需要调用特定功能时，你可以通过以下格式标记：
- [CALL:路况查询] - 调用实时路况查询接口
- [CALL:态势研判] - 调用交通态势分析接口
- [CALL:调度辅助] - 调用调度指挥接口
- [CALL:数据问询] - 调用数据查询接口
- [CALL:视频查看] - 调用监控视频接口
- [CALL:事件上报] - 调用事件上报接口

示例：用户问"人民大道现在堵车吗？"
回复：让我为您查询人民大道的实时路况。[CALL:路况查询]
人民大道当前通行状态：车流量较大，平均车速约20km/h，建议绕行建设路。

## 回答策略
1. **道路交通专业问题**：优先基于专业知识库回答，提供详细、准确的交通信息和分析
2. **通用问题**：保持基本回答能力，但适当引导回交通主题
3. **不确定的信息**：明确告知用户需要进一步核实，不编造数据
4. **紧急情况**：优先提醒安全事项，建议联系相关部门

## 交互原则
- 回答简洁明了，适合语音播报
- 涉及数据时给出具体数字和时间
- 提供可操作的建议，而非单纯描述
- 保持专业但亲和的语气

## 示例对话
用户：你好，我想查一下今天的交通情况
助手：您好！我是交通小智，很高兴为您服务。请问您想查询哪个区域或哪条道路的交通情况？我可以为您提供实时路况、拥堵预警、事故信息等查询服务。

用户：帮我看看高速公路的拥堵情况
助手：好的，正在为您查询高速公路实时路况。[CALL:路况查询]
目前高速公路整体通行状况：G15沈海高速北向南方向K120-K135路段车流量大，平均车速约40km/h；其他路段通行正常。建议途经该路段的车辆提前从XX出口绕行。
"""

# 开场白
GREETING_MESSAGE = """👋 您好！我是**交通小智**，道路交通信息智能问答助手。

我可以帮您：

🚗 **路况查询** - 实时查询道路通行状态、拥堵情况
📊 **态势研判** - 分析交通流量、预测拥堵时段
🚦 **调度辅助** - 提供疏导建议、信号优化方案
📈 **数据问询** - 查询历史数据、统计报表

您可以直接用语音或文字问我，例如：
• "人民大道现在堵车吗？"
• "今天早高峰什么时候结束？"
• "帮我查一下上周的交通事故统计"

请问有什么可以帮您的？"""

# 功能端口配置（预留扩展）
FUNCTION_PORTS = {
    "路况查询": {
        "description": "查询实时路况信息",
        "endpoint": "/api/traffic/realtime",  # 预留接口
        "parameters": ["road_name", "area", "time_range"]
    },
    "态势研判": {
        "description": "分析交通态势",
        "endpoint": "/api/traffic/analysis",  # 预留接口
        "parameters": ["area", "time_range", "analysis_type"]
    },
    "调度辅助": {
        "description": "提供调度建议",
        "endpoint": "/api/dispatch/suggest",  # 预留接口
        "parameters": ["event_type", "location", "priority"]
    },
    "数据问询": {
        "description": "查询交通数据",
        "endpoint": "/api/data/query",  # 预留接口
        "parameters": ["data_type", "time_range", "conditions"]
    },
    "视频查看": {
        "description": "查看监控视频",
        "endpoint": "/api/video/stream",  # 预留接口
        "parameters": ["camera_id", "location"]
    },
    "事件上报": {
        "description": "上报交通事件",
        "endpoint": "/api/event/report",  # 预留接口
        "parameters": ["event_type", "location", "description", "severity"]
    }
}

# 知识库分类配置
KNOWLEDGE_BASE_CONFIG = {
    "道路交通": {
        "priority": 1,  # 最高优先级
        "enhanced_retrieval": True,
        "sources": [
            "交通法规数据库",
            "道路基础设施数据",
            "实时交通监测数据",
            "历史交通统计数据",
            "应急预案库"
        ]
    },
    "通用知识": {
        "priority": 2,
        "enhanced_retrieval": False,
        "sources": [
            "通用百科知识",
            "常见问题库"
        ]
    }
}

def get_system_prompt():
    """获取系统提示词"""
    return SYSTEM_PROMPT

def get_greeting():
    """获取开场白"""
    return GREETING_MESSAGE

def get_function_ports():
    """获取功能端口配置"""
    return FUNCTION_PORTS

def detect_function_call(text):
    """
    检测文本中是否包含功能调用标记

    Args:
        text: 助手回复文本

    Returns:
        list: 检测到的功能调用列表
    """
    import re
    pattern = r'\[CALL:(\w+)\]'
    matches = re.findall(pattern, text)
    return matches
