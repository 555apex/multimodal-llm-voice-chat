import base64
import tempfile
import os
import logging
from config import Config

logger = logging.getLogger(__name__)

class ASRService:
    """语音识别服务 (FunASR Paraformer-zh)"""

    def __init__(self):
        self.model = None
        self.device = Config.ASR_DEVICE
        self.model_name = Config.ASR_MODEL
        self.model_dir = Config.ASR_MODEL_DIR
        self._initialized = False

    def _init_model(self):
        """延迟初始化模型（带重试机制）"""
        if self._initialized:
            return

        max_retries = 3
        for attempt in range(max_retries):
            try:
                from funasr import AutoModel

                logger.info(f'正在加载 ASR 模型: {self.model_name}')
                logger.info(f'模型缓存目录: {self.model_dir}')
                logger.info(f'使用设备: {self.device}')

                # 确保模型目录存在
                os.makedirs(self.model_dir, exist_ok=True)

                self.model = AutoModel(
                    model=self.model_name,
                    device=self.device,
                    model_revision="v2.0.4",
                )

                self._initialized = True
                logger.info('ASR 模型加载完成!')
                return

            except Exception as e:
                logger.warning(f'ASR 模型加载失败 (尝试 {attempt + 1}/{max_retries}): {e}')
                if attempt == max_retries - 1:
                    logger.error(f'ASR 模型加载最终失败: {e}')
                    import traceback
                    logger.debug(traceback.format_exc())
                    raise

    def recognize(self, audio_base64, audio_format='wav'):
        """
        识别语音为文本

        Args:
            audio_base64: base64 编码的音频数据
            audio_format: 音频格式 (wav, mp3 等)

        Returns:
            识别出的文本，失败返回 None
        """
        # 初始化模型
        self._init_model()

        temp_file = None
        try:
            # 解码 base64 音频
            audio_bytes = base64.b64decode(audio_base64)

            # 保存到临时文件
            with tempfile.NamedTemporaryFile(suffix=f'.{audio_format}', delete=False) as f:
                f.write(audio_bytes)
                temp_file = f.name

            # 使用 FunASR 进行识别
            result = self.model.generate(
                input=temp_file,
                batch_size_s=300,
            )

            # 提取识别结果
            if result and len(result) > 0:
                text = result[0].get('text', '')
                return text.strip() if text else None

        except Exception as e:
            logger.error(f'ASR 识别出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())

        finally:
            # 清理临时文件
            if temp_file and os.path.exists(temp_file):
                try:
                    os.unlink(temp_file)
                except OSError as e:
                    logger.warning(f'清理临时文件失败: {e}')

        return None
