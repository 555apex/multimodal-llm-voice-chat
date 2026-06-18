import base64
import tempfile
import os
import asyncio
import logging
from config import Config

logger = logging.getLogger(__name__)

# 设置HuggingFace镜像源（解决国内下载问题）
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'

class TTSService:
    """语音合成服务 (edge-tts / ChatTTS)"""

    def __init__(self):
        self.model = None
        self.device = Config.TTS_DEVICE
        self.model_dir = Config.TTS_MODEL_DIR
        self.sample_rate = Config.AUDIO_SAMPLE_RATE  # 使用配置的采样率
        self._initialized = False
        self._use_chattts = False  # 默认使用edge-tts（更快）

    def _init_model(self):
        """延迟初始化模型"""
        if self._initialized:
            return

        # 优先使用edge-tts（更快）
        if not self._use_chattts:
            try:
                import edge_tts
                logger.info('使用 edge-tts 作为 TTS 引擎（快速模式）')
                self._initialized = True
                return
            except ImportError:
                logger.warning('edge-tts 未安装，尝试使用 ChatTTS')
                self._use_chattts = True

        # 使用ChatTTS（本地离线，较慢）
        if self._use_chattts:
            try:
                import ChatTTS

                logger.info(f'正在加载 TTS 模型: ChatTTS')
                logger.info(f'模型缓存目录: {self.model_dir}')
                logger.info(f'使用设备: {self.device}')

                # 确保模型目录存在
                os.makedirs(self.model_dir, exist_ok=True)

                self.model = ChatTTS.Chat()
                self.model.load(compile=False, device=self.device, source='huggingface')

                self._initialized = True
                logger.info('TTS 模型加载完成!')
                return

            except Exception as e:
                logger.error(f'ChatTTS 加载失败: {e}')
                import traceback
                logger.debug(traceback.format_exc())
                # 回退到edge-tts
                try:
                    import edge_tts
                    logger.info('回退使用 edge-tts')
                    self._use_chattts = False
                    self._initialized = True
                    return
                except ImportError:
                    raise

        raise Exception('ChatTTS 和 edge-tts 都无法使用')

    def _preprocess_text(self, text):
        """预处理文本，保留有意义的符号"""
        import re

        # 将数字之间的连字符"-"替换为"到"，表示范围
        # 例如：5-6 -> 5到6, 15-20 -> 15到20
        text = re.sub(r'(\d+)\s*-\s*(\d+)', r'\1到\2', text)

        # 将数字之间的波浪号"~"替换为"到"，表示范围
        # 例如：5~6 -> 5到6, 100~200 -> 100到200
        text = re.sub(r'(\d+)\s*~\s*(\d+)', r'\1到\2', text)

        return text

    async def _synthesize_edge_tts(self, text):
        """使用edge-tts合成语音"""
        import edge_tts

        # 预处理文本
        processed_text = self._preprocess_text(text)
        logger.info(f'TTS 预处理: "{text[:30]}..." -> "{processed_text[:30]}..."')

        # 使用中文语音
        voice = "zh-CN-XiaoxiaoNeural"  # 女声
        # voice = "zh-CN-YunxiNeural"  # 男声

        communicate = edge_tts.Communicate(processed_text, voice)
        audio_data = b""
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio_data += chunk["data"]

        return audio_data

    def _synthesize_chattts(self, text):
        """使用ChatTTS合成语音"""
        import ChatTTS
        import torch
        import soundfile as sf
        import numpy as np

        # 生成随机说话人嵌入
        rand_spk = self.model.sample_random_speaker()

        # 设置推理参数
        params_infer_code = ChatTTS.Chat.InferCodeParams(
            spk_emb=rand_spk,
            temperature=0.3,
            top_P=0.7,
            top_K=20,
        )

        # 设置文本处理参数
        params_refine_text = ChatTTS.Chat.RefineTextParams(
            prompt='[oral_2][laugh_0][break_6]',
        )

        # 生成语音
        wavs = self.model.infer(
            [text],
            params_infer_code=params_infer_code,
            params_refine_text=params_refine_text,
        )

        if wavs and len(wavs) > 0:
            # 获取音频数据
            audio_data = wavs[0]

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

            sf.write(temp_file, audio_int16, self.sample_rate)

            # 读取并编码为 base64
            with open(temp_file, 'rb') as f:
                audio_bytes = f.read()

            # 清理临时文件
            try:
                os.unlink(temp_file)
            except OSError as e:
                logger.warning(f'清理临时文件失败: {e}')

            return audio_bytes

        return None

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
            if not self._use_chattts:
                # 使用edge-tts（快速模式）
                logger.info(f'使用 edge-tts 合成: {text[:30]}...')
                audio_bytes = asyncio.run(self._synthesize_edge_tts(text))
            else:
                # 使用ChatTTS（本地模式）
                logger.info(f'使用 ChatTTS 合成: {text[:30]}...')
                audio_bytes = self._synthesize_chattts(text)

            if audio_bytes:
                logger.info(f'TTS 合成完成，音频大小: {len(audio_bytes)} bytes')
                return base64.b64encode(audio_bytes).decode('utf-8')

        except Exception as e:
            logger.error(f'TTS 合成出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())

        return None
