import base64
import tempfile
import os
from config import Config


class ASRService:
    """语音识别服务（本地 faster-whisper）"""

    def __init__(self):
        self.model_size = Config.WHISPER_MODEL_SIZE
        self._model = None  # 延迟加载，首次调用时才下载模型

    def _load_model(self):
        """延迟加载模型（首次调用时自动下载到本地缓存）"""
        if self._model is not None:
            return self._model

        print(f'正在加载 faster-whisper 模型（{self.model_size}）...')
        try:
            from faster_whisper import WhisperModel
            # 使用 M5 芯片的 CPU 推理（faster-whisper 会利用 CTranslate2 优化）
            self._model = WhisperModel(
                self.model_size,
                device='cpu',
                compute_type='int8'  # int8 在 Apple Silicon 上性能和精度平衡
            )
            print('faster-whisper 模型加载完成')
        except ImportError:
            raise ImportError(
                '请安装 faster-whisper: pip install faster-whisper'
            )
        return self._model

    def recognize(self, audio_base64, audio_format='wav', language='zh'):
        """
        识别 base64 编码的音频为文本

        Args:
            audio_base64: base64 编码的音频数据
            audio_format: 音频格式 (wav)
            language: 语言提示 (zh/en/auto)

        Returns:
            识别出的文本，失败返回 None
        """
        if not audio_base64:
            print('错误: 音频数据为空')
            return None

        tmp_path = None
        try:
            # 将 base64 解码为 WAV 字节写入临时文件
            audio_bytes = base64.b64decode(audio_base64)
            tmp_path = tempfile.mktemp(suffix='.wav')
            with open(tmp_path, 'wb') as f:
                f.write(audio_bytes)

            # 加载模型并推理
            model = self._load_model()
            segments, info = model.transcribe(
                tmp_path,
                language=language if language != 'auto' else None,
                beam_size=5,
                vad_filter=True  # 过滤静音段
            )

            # 拼接所有识别片段
            text_parts = [seg.text.strip() for seg in segments]
            result = ''.join(text_parts)

            if result:
                print(f'[ASR] 识别结果: {result[:100]}')
                return result
            else:
                print('[ASR] 未识别到语音内容')
                return None

        except Exception as e:
            print(f'ASR 识别出错: {e}')
            return None

        finally:
            # 清理临时文件
            if tmp_path and os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
