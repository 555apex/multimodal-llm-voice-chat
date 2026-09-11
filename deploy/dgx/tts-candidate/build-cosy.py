import pathlib,subprocess,json
r=pathlib.Path('/home/whtc/workspace/projects/road-agent-dgx/.releases/speech-20260910')
base=json.loads(subprocess.check_output(['docker','image','inspect','road-agent-dgx-speech:before-speech-20260910']))[0]['Id']
subprocess.run(['docker','tag',base,'road-agent-speech-base:speech-20260910'],check=True)
base='road-agent-speech-base:speech-20260910'
dockerfile='''ARG BASE
FROM ${BASE}
USER root
RUN PIP_CONFIG_FILE=/dev/null PIP_EXTRA_INDEX_URL= PIP_DISABLE_PIP_VERSION_CHECK=1 pip install --index-url https://mirrors.aliyun.com/pypi/simple --timeout 30 --retries 2 --no-cache-dir --no-build-isolation --no-deps torchaudio==2.8.0 conformer==0.3.2 diffusers==0.29.0 HyperPyYAML==1.2.3 inflect==7.3.1 lightning==2.2.4 pytorch-lightning==2.2.4 torchmetrics==1.7.4 omegaconf==2.3.0 openai-whisper==20231117 x-transformers==2.11.24 modelscope==1.20.0 antlr4-python3-runtime==4.9.3 ruamel.yaml==0.18.15 ruamel.yaml.clib==0.2.12 typeguard==4.4.4 more-itertools==10.7.0 tiktoken==0.9.0 hydra-core==1.3.2 typeguard==4.4.4 einx==0.3.0 frozendict==2.4.6 gdown==5.1.0 PySocks==1.7.1 pyrootutils==1.0.4 wget==3.2 loguru==0.7.3 pyworld==0.3.4
COPY cosyvoice /opt/cosyvoice
ENV PYTHONPATH=/opt/cosyvoice:/opt/cosyvoice/third_party/Matcha-TTS
RUN python -c "import torch, torchaudio; print(torch.__version__, torchaudio.__version__); from cosyvoice.cli.cosyvoice import CosyVoice3"
'''
(r/'Dockerfile.cosy').write_text(dockerfile)
subprocess.run(['docker','build','--network','host','--build-arg','BASE='+base,'-f','Dockerfile.cosy','-t','road-agent-cosy:speech-20260910','.'],cwd=r,check=True)
