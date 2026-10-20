# RoadAgent：交通问答、应急调度与语音交互

部署分支：**`roadagent-v2`**。2026-10-20核对DGX：提交`d5b4d50769ed4afaba4cbc961f99acbe1eeb14b5`，包含大屏白名单回调、带引号命令识别及中性能力说明；保留此前RAG补充回答、青绿色界面和nginx预检配置。GitHub同时保留迁移文档及部署工具，提交号与DGX基线可能不同。源码一致不等于构建出的镜像与现网镜像字节一致。

RoadAgent提供交通数据查询、设施预警、应急方案生成与三级审批、知识库RAG、ASR和流式TTS。Java负责事实统计、状态与库存约束，LLM负责意图和文本生成。默认数据库不自动初始化。

## 1 先选择运行方式

| 场景 | 建议方式 | 需要准备 |
|---|---|---|
| 普通Linux电脑学习/开发 | Java + Vue；调用获授权模型API | JDK17、Node22.12+、MySQL副本或经授权开发库；没有GPU也能运行文字链路 |
| Linux昇腾服务器 | 本文第3–8节；CANN/torch_npu/vLLM Ascend | 准确NPU SKU、OS/驱动/固件/CANN兼容组合；禁止使用CUDA镜像代替 |
| Linux NVIDIA服务器 | 本文第3–8节；CUDA运行时 | GPU型号/显存、驱动和NVIDIA Container Toolkit、受支持的CUDA推理镜像 |

两种服务器均先使用CPU ASR与CPU BGE，再验证硬件加速的LLM/TTS。支持`aarch64`和`x86_64`主机上的原生构建路径，但具体依赖、镜像和模型仍须在目标架构实测；DGX ARM64镜像不能直接用于x86_64。现网NVFP4不能直接用于昇腾，也不能因另一台设备使用NVIDIA就推定它支持现有NVFP4模型。

**迁移目标是隔离并行验证。** 保留DGX业务入口，所有测试写入指向本地副本。本仓库包含代码、配置模板、工具和验收样本；不包含模型、数据库、真实密钥或厂商驱动，不提供未经验证的全平台一键安装。

## 2 获取与固定源码

```bash
git clone --branch roadagent-v2 --single-branch \
  https://github.com/555apex/multimodal-llm-voice-chat.git
cd multimodal-llm-voice-chat
git status --short --branch
git rev-parse HEAD
```

已有副本且工作区干净时：`git pull --ff-only origin roadagent-v2`。有修改先保留并核对，不用`reset --hard`覆盖。将完整提交号记入交接记录，后续更新先备份目标数据，再重建镜像、联调和验收。`master`及`version/*`属于其他历史线，不能据其README判断当前DGX版本。

## 3 目标Linux主机准备

```bash
uname -m
cat /etc/os-release
python3 --version
docker version
docker compose version
df -hT /srv
lsblk -o NAME,TYPE,SIZE,FSTYPE,MOUNTPOINTS,MODEL
# 昇腾执行 npu-smi info；NVIDIA执行 nvidia-smi
```

主机工具：Python3.12+、Git、Bash、Docker Engine/Compose V2、GPG、OpenSSH、curl、GNU tar/sha256sum。JDK/Node在容器构建时由Dockerfile准备；开发方式见第9节。Ubuntu24.04可用系统包准备Git/Python/GPG等；openEuler及其他厂商系统按已确认的OS仓库或厂商包安装，先核对Python版本。不要把未知OS当Ubuntu，不执行未核实的驱动下载命令。

昇腾先确认OS/内核、8卡健康、驱动/固件/CANN、torch_npu和vLLM Ascend的厂商组合。NVIDIA先确认驱动支持推理镜像中的CUDA、GPU计算能力和显存，并按[官方容器工具指南](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)安装/核验容器工具。磁盘先识别现有阵列与挂载，不格式化或重建。

确认数据卷后，在新机管理员会话执行：

```bash
sudo -i
install -d -m 700 /opt/roadagent-migration
# 以下SRC指刚才clone的仓库绝对路径；不要填写DGX现网目录
SRC='/填写目标机仓库绝对路径'
cp -a "$SRC/deploy/site" /opt/roadagent-migration/package
cd /opt/roadagent-migration/package
python3 resources/scripts/02_init_site.py --accelerator ascend
# NVIDIA机器将上一命令参数改为 --accelerator nvidia；两者只选一次
export SITE_ENV=/opt/roadagent-migration/site.env
install -d -m 700 /srv/roadagent-migration/incoming
```

初始化不覆盖已有`site.env`；密钥在目标机生成。源码构建机可以在目标服务器，也可以是其可访问的同架构内网构建主机。

## 4 现场直接拉取/构建镜像

先编辑`$SITE_ENV`：填写与源8.0.45匹配的MySQL镜像、审核过的LLM/TTS镜像；将前后端改为本地构建标签，例如`roadagent-site-backend:source-reviewed`、`roadagent-site-frontend:source-reviewed`。其余引用参考模板；必须验证目标架构的manifest、来源和许可。目标端需要能访问GitHub、容器仓库、Maven/npm/Python依赖源，或经授权的内网镜像。

```bash
vi "$SITE_ENV"
python3 resources/scripts/prepare_online_images.py \
  --source-root "$SRC" --env-file "$SITE_ENV"
# 审阅输出计划后执行；此步骤下载和构建到目标机，不下载到项目作者电脑
python3 resources/scripts/prepare_online_images.py \
  --source-root "$SRC" --env-file "$SITE_ENV" --execute
```

该工具拉取公共基础及已审核推理镜像，在本机构建Java后端、Vue前端、CPU语音网关和CPU BGE，并记录image ID。构建失败不启动半成品；模型加载、兼容性和业务质量需要另验。现网已有前后端镜像标签属于DGX本地镜像，不能假定公开仓库能拉取同名标签。

## 5 目标机直接获取权重和数据

### 5.1 官方原始权重

在目标机单独下载环境安装`huggingface_hub`并保存解析到的依赖版本。ASR仓库为`Systran/faster-whisper-small`；TTS为`Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice`。先核对官方仓库许可及准确的40位revision。LLM仓库及原始精度需根据目标运行时支持矩阵选定，不猜测名称或将NVFP4改名。

```bash
python3 -m venv /opt/roadagent-migration/download-venv
/opt/roadagent-migration/download-venv/bin/python -m pip install huggingface_hub
/opt/roadagent-migration/download-venv/bin/python -m pip freeze \
  > /opt/roadagent-migration/download-requirements.lock
PY=/opt/roadagent-migration/download-venv/bin/python
$PY resources/scripts/download_model.py --repo Systran/faster-whisper-small \
  --revision '填写已审查40位commit' \
  --directory /srv/roadagent-migration/models/Systran--faster-whisper-small
$PY resources/scripts/download_model.py --repo Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice \
  --revision '填写已审查40位commit' \
  --directory /srv/roadagent-migration/models/Qwen--Qwen3-TTS-12Hz-1.7B-CustomVoice
# LLM同样调用download_model.py，填写经审查的模型仓库、revision和专用目录
```

工具支持同一revision中断后重试，下载完成后生成本地SHA256SUMS；校验通过后仍需核对发布方、许可证、模型身份和接口，不能只凭本地哈希证明来源真实性。无公网时改用授权内网模型仓库；不可把通用BGE替换成项目微调权重后继续复用旧索引。

### 5.2 定制BGE与业务副本

项目两套BGE是定制权重，GitHub不含权重。目标服务器能够SSH到DGX时可直接获取这两个目录，数据不经过作者电脑或U盘：

```bash
DGX_HOST='填写授权的DGX用户名@Tailscale地址或局域网地址'
# 先通过可信渠道核对并登记DGX主机指纹，配置工程师自己的认证
python3 resources/scripts/export_resources.py --host "$DGX_HOST" --kind bge \
  --destination /srv/roadagent-migration/incoming/models-bge.tar
python3 resources/scripts/unpack.py \
  --archive /srv/roadagent-migration/incoming/models-bge.tar \
  --destination /srv/roadagent-migration/models
```

MySQL和知识库必须做一致性逻辑备份，不能从GitHub安装出真实业务数据，也不能复制运行中的数据库目录。参照[源端导出与目标恢复](DATA_MIGRATION.md)进行源端维护窗口、加密和网络传输。需要同时获取MySQL、PostgreSQL、原文、WeKnora AES密钥和模型绑定；密码另交。所有恢复/写入指向目标副本，数据库端口不发布到宿主机。

## 6 昇腾/NVIDIA模型运行配置

| 项目 | 昇腾 | NVIDIA |
|---|---|---|
| 现场检查 | `npu-smi info`及厂商拓扑/健康记录 | `nvidia-smi`及容器GPU检查 |
| 运行时 | 厂商匹配的CANN、torch_npu、vLLM Ascend/Omni | 受支持CUDA、容器工具、vLLM/Omni |
| 审查配置 | `compose.npu.yaml` | `compose.nvidia.yaml` |
| 初始化参数 | `--accelerator ascend` | `--accelerator nvidia` |
| 卡数 | 本次AT3500目标为8卡 | 按实际设备及显存预算填写 |

```bash
# 昇腾；NVIDIA改为compose.nvidia.yaml.example和compose.nvidia.yaml
cp resources/configs/compose.npu.yaml.example /opt/roadagent-migration/compose.npu.yaml
vi /opt/roadagent-migration/compose.npu.yaml
python3 resources/scripts/image_locks.py --env-file "$SITE_ENV" \
  --output /opt/roadagent-migration/image-locks.json
vi /opt/roadagent-migration/target-profile.json
```

两个模板都故意含`REPLACE`，不能原样运行。工程师必须填写已验证的完整启动命令、设备映射、驱动挂载、卡/显存分配、并行度和模型目录；LLM服务名`qwen`，端口8000；TTS服务名`tts`，端口8091。LLM要求OpenAI聊天、SSE和JSON业务输出，TTS要求现有网关兼容的Omni接口、Serena/Chinese、24kHz单声道PCM和取消语义。接口不同先适配并测试，不能只改URL。

`target-profile.json`填写审核人/时间、OS/架构、驱动、兼容材料、镜像ref/ID和LLM revision、格式、SHA256SUMS摘要、TTS实测依据；昇腾补NPU/CANN，NVIDIA补`gpu_model`、实际`gpu_count`，并填写`accelerator_compose_sha256`。昇腾可继续使用`npu_compose_sha256`。全部核验后才设`approved=true`。模板`gpus: all`只是待修改草案，必须先分配LLM/TTS资源，不能视为两服务均可占满显存。

## 7 恢复与启动顺序

先按[数据迁移步骤](DATA_MIGRATION.md)恢复配置、数据库和知识库文件，核对143张业务表（以本次备份记录为准）、知识库ID、1024维模型及索引对应。恢复密钥之前不要启动WeKnora。

```bash
cd /opt/roadagent-migration/package
export SITE_ENV=/opt/roadagent-migration/site.env
bash resources/scripts/site.sh rag-up
python3 resources/scripts/04_runtime_gate.py --env-file "$SITE_ENV"
bash resources/scripts/site.sh accelerator-up
bash resources/scripts/site.sh app-up
bash resources/scripts/site.sh all-status
curl -fsS http://127.0.0.1:18080/api/v1/speech/capabilities
```

`db-up → 恢复 → rag-up → accelerator-up → app-up`逐层放行。门槛检查通过仅说明配置校验通过，不代表模型性能通过。第一次启动保持模型离线模式，避免服务暗中下载其他权重。

## 8 访问、验收和运维

工程师笔记本执行`ssh -N -L 18080:127.0.0.1:18080 -L 18082:127.0.0.1:18082 用户@服务器`，访问`http://localhost:18080`及知识库管理入口18082；localhost/受信任HTTPS用于麦克风测试。SSH可以通过局域网或Tailscale，主机指纹和认证必须核实。

验收依次覆盖：交通事实/设施预警、隔离库应急写入与库存守恒、RAG来源、真实录音ASR、TTS流式播放与取消、双路及混合负载、重启、备份恢复和24小时观察。使用`resources/scripts/06_acceptance.py`、`cancel_probe.py`和`samples`中的人工记录；工作流写入需显式确认本地项目。质量与同负载DGX基线比较，不能把接口200当作迁移完成。

```bash
bash resources/scripts/site.sh logs backend speech-service qwen tts
bash resources/scripts/site.sh all-status
df -h /srv/roadagent-migration
# 故障时仅停目标项目，保留数据；stop不删除卷
bash resources/scripts/site.sh stop
```

备份用`07_backup_site.py`，密码文件权限0600，备份存独立存储并演练恢复。禁止运行DGX专用旧发布脚本或`docs/sql`中的重置/种子脚本来初始化生产数据。

## 9 普通电脑开发与使用

准备Git、JDK17、Node22.12+；使用Maven Wrapper，不需要全局Maven。先由负责人准备开发MySQL/数据副本和可访问模型API。Linux：

```bash
cp config/api-test.env.example config/api-test.env
chmod 600 config/api-test.env
vi config/api-test.env
source config/api-test.env
./mvnw -pl road-agent-boot -am package -DskipTests
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
# 另开终端
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

填写`ROADAGENT_DB_URL/USERNAME/PASSWORD`、`ROADAGENT_MODEL_ENDPOINT`（完整`/v1/chat/completions`路径）、`ROADAGENT_MODEL_NAME`及认证参数。语音网关与RAG按需要配置`ROADAGENT_SPEECH_*`和`ROADAGENT_RAG_*`；没有相应服务先关闭入口。浏览器以npm输出端口为准。Windows使用`mvnw.cmd`及`config/api-test.ps1.example`，Java前端开发方式相同。

页面可进行交通问答、设施预警查看、应急流程和知识问答；录音前授权麦克风，朗读和取消分别验收。测试写入仅在开发库执行，普通协作者不重复发布SQL。模型API和数据库配置必须进入忽略文件，不提交GitHub。

## 10 网络和资源受限时

优先级：**目标机直连官方/授权仓库 → 可访问的内网镜像/制品服务器 → DGX授权网络获取私有权重与加密备份 → U盘或经批准网盘（最后备选）**。Tailscale仅解决网络到达，不自动提供公网出口；GitHub能访问不等于容器、模型和依赖仓库均可访问。

GitHub可以帮助工程师远程获取最新代码、安装步骤、配置与校验工具；要完成全链路部署，仍需硬件支持组合、模型来源、数据副本、访问权限和现场验收。CPU BGE/ASR及两类加速器模板是移植工具，本次未在福州新机实测，不能称为已认证部署成品。

参考（2026-10-20核对）：[vLLM Ascend模型支持](https://docs.vllm.ai/projects/ascend/en/latest/user_guide/support_matrix/supported_models.html)、[NVIDIA容器工具](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)、[Tailscale普通SSH](https://tailscale.com/docs/reference/ssh-over-tailscale)、[Tailscale SSH](https://tailscale.com/docs/features/tailscale-ssh)。
