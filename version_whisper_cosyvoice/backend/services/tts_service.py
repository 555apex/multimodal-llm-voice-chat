import base64
import tempfile
import os
import numpy as np
import logging
from config import Config

logger = logging.getLogger(__name__)

class TTSService:
    """语音合成服务 (CosyVoice)"""

    def __init__(self):
        self.model = None
        self.device = Config.TTS_DEVICE
        self.model_name = Config.TTS_MODEL
        self.model_dir = Config.TTS_MODEL_DIR
        self.sample_rate = Config.AUDIO_SAMPLE_RATE  # 使用配置的采样率
        self._initialized = False

    def _init_model(self):
        """延迟初始化模型（带重试机制）"""
        if self._initialized:
            return

        max_retries = 3
        for attempt in range(max_retries):
            try:
                # 注意：CosyVoice 的安装和使用可能需要额外步骤
                # 请参考官方文档：https://github.com/FunAudioLLM/CosyVoice
                from cosyvoice.cli.cosyvoice import CosyVoice

                logger.info(f'正在加载 TTS 模型: {self.model_name}')
                logger.info(f'模型缓存目录: {self.model_dir}')
                logger.info(f'使用设备: {self.device}')

                # 确保模型目录存在
                os.makedirs(self.model_dir, exist_ok=True)

                # 设置模型路径
                model_path = os.path.join(self.model_dir, self.model_name)

                # 如果模型目录不存在，使用默认路径（会自动下载）
                if os.path.exists(model_path):
                    self.model = CosyVoice(model_path)
                else:
                    logger.warning(f'模型目录不存在，将使用默认路径下载: {model_path}')
                    self.model = CosyVoice(self.model_name)

                self._initialized = True
                logger.info('TTS 模型加载完成!')
                return

            except ImportError as e:
                logger.error(f'TTS 模型加载失败: 请确保已安装 CosyVoice')
                logger.error(f'安装方法: pip install cosyvoice')
                logger.error(f'或参考: https://github.com/FunAudioLLM/CosyVoice')
                raise
            except Exception as e:
                logger.warning(f'TTS 模型加载失败 (尝试 {attempt + 1}/{max_retries}): {e}')
                if attempt == max_retries - 1:
                    logger.error(f'TTS 模型加载最终失败: {e}')
                    import traceback
                    logger.debug(traceback.format_exc())
                    raise

    def synthesize(self, text):
        """
        将文本合成为语音

        Args:
            text: 要合成的文本

        Returns:
            base64 编码的音频数据，失败返回 None
        """
        if not text or not text.strip():
            return None

        # 初始化模型
        self._init_model()

        try:
            import soundfile as sf

            # 使用 CosyVoice 生成语音
            # 默认使用 instruct 模式
            output = self.model.inference_sft(text, '默认', stream=False)

            if output and 'tts_speech' in output:
                audio_data = output['tts_speech']

                # 如果是 torch.Tensor，转换为 numpy
                if hasattr(audio_data, 'numpy'):
                    audio_data = audio_data.numpy()

                # 确保是一维数组
                if len(audio_data.shape) > 1:
                    audio_data = audio_data.squeeze()

                # 归一化到 [-1, 1]
                if audio_data.max() > 1.0 or audio_data.min() < -1.0:
                    audio_data = audio_data / max(abs(audio_data.max()), abs(audio_data.min()))

                # 转换为 int16
                audio_int16 = (audio_data * 32767).astype(np.int16)

                # 保存到临时文件
                with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
                    temp_file = f.name

                sf.write(temp_file, audio_int16, self.sample_rate)  # 使用配置的采样率

                # 读取并编码为 base64
                with open(temp_file, 'rb') as f:
                    audio_bytes = f.read()

                # 清理临时文件
                try:
                    os.unlink(temp_file)
                except OSError as e:
                    logger.warning(f'清理临时文件失败: {e}')

                return base64.b64encode(audio_bytes).decode('utf-8')

        except Exception as e:
            logger.error(f'TTS 合成出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())

        return None
