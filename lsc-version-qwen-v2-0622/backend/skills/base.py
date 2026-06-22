class BaseSkill:
    """Skill 基类 — 每个 Skill 对应一个千问可调用的工具"""

    # 子类需覆写
    name: str = ''
    description: str = ''
    parameters: dict = {}

    def tool_definition(self) -> dict:
        """返回 OpenAI 兼容的 tool 定义"""
        return {
            'type': 'function',
            'function': {
                'name': self.name,
                'description': self.description,
                'parameters': self.parameters,
            },
        }

    def execute(self, args: dict) -> dict:
        """执行工具逻辑，返回完整结果"""
        raise NotImplementedError

    def lightweight(self, result: dict) -> dict:
        """给千问的精简版：去掉 polyline 等大字段"""
        if 'roads' in result:
            light_roads = []
            for r in result['roads']:
                light_roads.append({
                    k: v for k, v in r.items() if k != 'polyline'
                })
            return {**result, 'roads': light_roads}
        return result

    def frontend_data(self, result: dict) -> dict:
        """给前端地图的版本：保留完整 polyline"""
        return result
