import asyncio
import re
import tempfile
import os
import logging
from config import Config

logger = logging.getLogger(__name__)


class TTSService:
    """语音合成服务（Edge-TTS 本地引擎）"""

    def __init__(self):
        self.voice = Config.EDGE_TTS_VOICE
        self.rate = Config.EDGE_TTS_RATE
        self.pitch = Config.EDGE_TTS_PITCH

    def synthesize(self, text):
        """合成语音，返回本地 MP3 文件路径"""
        clean = self.clean_text(text)
        if not clean:
            return None
        return asyncio.run(self._synthesize(clean))

    async def _synthesize(self, text):
        """Edge-TTS 流式合成 → 本地 MP3 文件"""
        import edge_tts
        communicate = edge_tts.Communicate(
            text,
            self.voice,
            rate=self.rate,
            pitch=self.pitch,
        )
        audio_data = bytearray()
        async for chunk in communicate.stream():
            if chunk['type'] == 'audio':
                audio_data.extend(chunk['data'])

        if not audio_data:
            return None

        fd, path = tempfile.mkstemp(suffix='.mp3')
        os.close(fd)
        with open(path, 'wb') as f:
            f.write(audio_data)
        return path

    @staticmethod
    def clean_text(text):
        """清理文本：去掉 emoji、markdown 符号，让 TTS 朗读自然"""
        if not text:
            return None

        # 去掉 JSON 代码块
        text = re.sub(r'```json[\s\S]*?```', '', text)
        text = re.sub(r'\{[^{}"]*"[^"]+"\s*:\s*\[[\s\S]*?\]\s*\}', '', text)

        # 去掉 emoji 和特殊符号
        text = re.sub(
            r'[^一-鿿'
            r'㐀-䶿'
            r'豈-﫿'
            r'　-〿'
            r'＀-￯'
            r'\w'
            r'\s'
            r'.,!?;:，。！？；：、'
            r'「」『』【】《》""''…—～'
            r'（）()\[\]{}<>'
            r'/%\-+=$&@#'
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
