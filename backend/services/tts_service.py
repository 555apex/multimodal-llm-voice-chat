import requests
import re
from config import Config


class TTSService:
    """语音合成服务（DashScope Qwen-TTS）"""

    def __init__(self):
        self.api_key = Config.DASHSCOPE_API_KEY
        self.api_url = Config.DASHSCOPE_TTS_URL
        self.model = Config.DASHSCOPE_TTS_MODEL
        self.voice = Config.DASHSCOPE_TTS_VOICE

    def synthesize(self, text):
        """合成语音，返回音频 URL"""
        if not self.api_key:
            print('错误: 未配置 DASHSCOPE_API_KEY')
            return None

        clean = self.clean_text(text)
        if not clean:
            return None

        try:
            resp = requests.post(
                self.api_url,
                headers={
                    'Authorization': f'Bearer {self.api_key}',
                    'Content-Type': 'application/json'
                },
                json={
                    'model': self.model,
                    'input': {
                        'text': clean,
                        'voice': self.voice,
                        'language_type': 'Chinese'
                    }
                },
                timeout=90
            )

            if resp.status_code != 200:
                print(f'TTS 请求失败: {resp.status_code} - {resp.text[:200]}')
                return None

            result = resp.json()
            return result.get('output', {}).get('audio', {}).get('url', '')

        except requests.exceptions.Timeout:
            print('TTS 请求超时')
        except Exception as e:
            print(f'TTS 合成出错: {e}')
            return None

    @staticmethod
    def clean_text(text):
        """清理文本：去掉 emoji、markdown 符号，让 TTS 朗读自然"""
        if not text:
            return None

        # 先去掉 JSON 代码块和裸 JSON（避免 TTS 朗读）
        text = re.sub(r'```json[\s\S]*?```', '', text)
        text = re.sub(r'\{[^{}"]*"[^"]+"\s*:\s*\[[\s\S]*?\]\s*\}', '', text)

        # 去掉 emoji 和特殊符号，只保留：
        # - 中文字符 (CJK)
        # - 英文字母/数字/标点
        # - 中文标点
        # - 换行和空格
        text = re.sub(
            r'[^一-鿿'       # CJK 基本汉字
            r'㐀-䶿'          # CJK 扩展 A
            r'豈-﫿'          # CJK 兼容汉字
            r'　-〿'          # CJK 标点符号
            r'＀-￯'          # 全角字母/符号
            r'\w'                     # 字母数字下划线
            r'\s'                     # 空白字符
            r'.,!?;:，。！？；：、'    # 常用中英文标点
            r'「」『』【】《》""''…—～'  # 书名号等
            r'（）()\[\]{}<>'         # 括号
            r'/%\-+=$&@#'            # 常用符号
            r']+',
            '', text
        )

        # 去掉 markdown 格式
        text = re.sub(r'\*\*([^*]+)\*\*', r'\1', text)
        text = re.sub(r'\* ', '', text)
        text = re.sub(r'^#+\s*', '', text, flags=re.M)
        text = re.sub(r'^- ', '', text, flags=re.M)
        text = text.replace('*', '')
        text = text.replace('`', '')

        # 去掉多余空格和空行
        text = re.sub(r'\n{2,}', '\n', text)
        text = re.sub(r' {2,}', ' ', text)
        text = text.strip()

        return text if text else None
