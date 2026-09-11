# 福建应急交通 Agent

> **DGX 合并部署版本（业务基准 `53498de`）**：DGX 继续使用本地 Qwen3.6、Qwen3-TTS 和 faster-whisper small，并保留数字人动作、口型、公网入口和登录方式。数据库切换只通过受保护的运行环境文件完成，禁止执行演示数据重置脚本。

## 2026-09-10 应急界面与简明预案升级

- 应急页面的“流程记录”改为“下发通告”。左侧按资源是否仍在调度中选择“未办结 / 已办结”；无需调度记录也归入已办结。这是资源归还视角，不改变三级审批的终态。
- 通告先展示摘要，点击加载详情。后台轮询不锁定归还按钮；归还仍需原因和二次确认，失败保留输入及错误，同一请求重试复用幂等键。
- 16类简明预案按约350字编写，新模板填充后校验300–400字；旧工单、返工预案版本和正式通告快照不改写。
- 数据库负责人暂停所有后端实例的方案生成任务后，在共享库**统一执行一次** [简明预案发布脚本](docs/sql/20260910_publish_compact_response_plans.sql)，再重启更新后的后端。脚本继承当前资源基线、发布下一版本，可重复核验；不重置任何事件或库存。需要临时表及存储过程相关权限。普通协作者不要执行该脚本。
- 本次交付只修改代码和脚本，未执行共享库发布、未打开浏览器。若尚未发布SQL，数据库仍使用旧预案内容；界面修复只需更新前后端。

这是一个不依赖 LangChain 的教学型 Agent 项目，当前已经打通三条纵向闭环：

```text
交通问答：自然语言 → 识别路况、通行能力、跨区域交通联系、车型出行特征或城市目的地联系倾向 → MySQL只读事实 → Java确定性统计 → 模型摘要与可视化结果
设施预警：w_realtime_abnormal异常快照 → Java确定性汇总 → 预警清单/健康报告/重点关注 → 确认、消除或忽略回写
应急调度：w_lw_incident应急事件 → 规则/模型自动分类 → 版本化预案填充 → Java按库存和数据库城市坐标分配 → 三级上报通告 → 全程留痕
语音交互：浏览器录音 → Java语音接口 → Docker内faster-whisper识别；回答摘要 → Edge-TTS分段合成 → 浏览器播放
```

DeepSeek 负责意图理解和应急方案生成；Java 负责交通事实回答、Skill 白名单、参数校验、Tool 调用、审批和状态转换。模型不能直接创建工单、修改业务状态或改写交通事实。

需求1-7基于跨市路线与同路线卡口7日流量，提供单城市目的地联系倾向排名和多城市联系倾向矩阵。该指标用于表达相对联系结构，不代表真实单车起终点、行驶方向或净流入流出。

首次协作按下面的顺序即可跑通，测试不是启动前置条件：

1. 准备第 1 节环境，按第 2 节克隆指定分支。
2. 按第 3 节申请自己的 DeepSeek Key，向负责人获取数据库配置，并复制本地配置文件。
3. 请负责人确认第 4 节数据库结构、权限以及跨市路线与卡口流量数据；使用共享库的协作者不要自行批量执行 SQL。
4. 按第 5 节启动 Java 后端和 Vue 前端；需要录音/朗读时再启动 Docker 语音服务。
5. 在 AI 抽屉输入“福州的出行主要联系哪些城市？”，查看摘要和目的地联系倾向排名。

## 1. 环境要求

- Git；
- JDK 17；
- Node.js 20.19+（20.x）或 22.12+，以锁文件中 Vite 的要求 `^20.19.0 || >=22.12.0` 为准；旧版 Node 20 或 Node 21 不满足要求；
- Docker Desktop，或 Docker Engine + Docker Compose v2，仅语音功能需要；
- 能访问项目负责人共享的 MySQL 8 数据库；
- 无需安装全局 Maven。仓库已包含 Maven Wrapper 3.3.4，并固定 Maven 3.9.16。

语音服务的 Python 3.11、faster-whisper 和 Edge-TTS 全部安装在 Docker 镜像中。本机不需要安装 Python、Conda、FFmpeg 或相关依赖；不启动语音容器时，文字问答、交通查询和应急调度仍可正常使用。

当前只支持 `small + CPU + int8`。不要把 `SPEECH_ASR_DEVICE` 改成 `cuda`：当前镜像没有安装 CUDA、cuBLAS 或 cuDNN。faster-whisper 官方基准中，small/int8 的 ASR 进程约使用 1.5 GB 内存；为容器和依赖预留额外空间，建议 Docker 至少可使用 4 GB 内存。

此前语音版在 Apple Silicon/ARM64 上的实测记录（本次 OD 更新未重测）：运行镜像约 725 MB，`small` 模型卷约 467 MB，模型就绪后的容器空闲内存约 337 MiB；复用已有模型卷时约 0.5 秒完成模型加载。推理时占用会升高，不同 Docker 版本和 CPU 架构也会有差异。首次构建还需要保存基础镜像和构建缓存，建议至少预留 2 GB 可用磁盘空间。

## 2. 拉取指定版本

```bash
git clone --branch version/roadagent-v1 --single-branch https://github.com/555apex/multimodal-llm-voice-chat.git
cd multimodal-llm-voice-chat
```

如果已经克隆过仓库：

```bash
git fetch origin
git switch version/roadagent-v1
git pull --ff-only origin version/roadagent-v1
```

### 2.1 已部署 `61e64d4` 版本服务器的快速修复与升级

服务器如果当前运行的是提交 `61e64d4`，本版需要重点修复下面两组问题：

| 业务 | 旧版问题 | 本版修改方法 |
|---|---|---|
| 基础设施数据预警 | `w_realtime_abnormal.id` 是 MySQL `BIGINT`，旧接口把它作为 JSON 数字返回，超过 JavaScript 安全整数范围后浏览器会舍入，处置请求可能找不到记录；逻辑删除记录也可能进入列表、统计或更新；轮询旧响应还可能覆盖用户刚切换的筛选，提交失败时表单输入可能丢失 | Java REST、OpenAPI 和前端统一把告警 ID 当十进制字符串；所有设施查询、统计、详情和条件更新增加 `del_flag='N'` 过滤；前端以请求版本丢弃过期响应，处置表单打开时锁定筛选切换，并在表单内保留失败信息和原输入 |
| 应急工作流 | 模型生成方案期间若 Java 进程、模型服务或网络中断，数据库可能长期停留在 `GENERATING/REVISING`，页面只有“生成中”而没有恢复入口 | 前端发现同一方案版本超过120秒未更新时只自动恢复一次，并提供“恢复生成”按钮；仍调用原生成接口，由后端已有的超时认领、条件更新和乐观锁复用原方案版本并写入 `GENERATION_RETRIED` 审计，不新增工单或公共接口 |

上述两项2026-09-09修复本身不需要执行 SQL，也不需要重建或重启语音 Docker 容器。设施修复必须同时更新 Java 后端和前端，不能只复制某一个页面文件；应急修复同样应整体拉取本分支，避免前后端恢复时限和并发语义不一致。当前分支又增加了2026-09-10简明预案数据库版本，更新到分支最新提交后还必须按第4.1节确认数据库是否已由负责人统一发布；普通协作者仍不得自行执行 SQL。

更新前先进入服务器上的项目目录，确认分支、提交和工作区：

```bash
git status --short --branch
git rev-parse HEAD
git fetch origin
git log --oneline --decorate HEAD..origin/version/roadagent-v1
```

如果 `git status --short` 有输出，先停止更新并确认这些改动是谁维护的；不要用 `git reset --hard` 或强制拉取覆盖服务器配置。工作区干净时，为当前提交建立一个不会改动文件的本地备份分支，再执行快进更新：

```bash
git branch backup/roadagent-server-before-fixes HEAD
git switch version/roadagent-v1
git pull --ff-only origin version/roadagent-v1
```

本地配置 `config/api-test.env`、`config/api-test.ps1` 和根目录 `.env` 都被 Git 忽略，更新后仍要确认文件存在且权限合适，不要打印、提交或发送其中的密钥。随后重新构建后端和前端：

```bash
./mvnw package -DskipTests
cd frontend
npm ci
npm run build
```

Windows 后端构建使用 `.\mvnw.cmd package -DskipTests`。如果服务器已有与当前 `frontend/package-lock.json` 完全一致的依赖缓存，可以只运行 `npm run build`；干净部署优先使用 `npm ci`。构建后沿用服务器现有的 systemd、Nginx、容器编排或启动脚本替换产物并重启，先检查现有部署方式，不要凭空创建第二套 Java 进程或覆盖 Nginx 配置。

可以把下面的工作计划直接交给服务器上的 Codex 执行：

```text
目标：把服务器上基于提交61e64d4的roadagent-v1安全更新到
origin/version/roadagent-v1最新版本，修复设施预警和应急生成中断问题。

约束：
1. 先只读检查项目路径、当前分支、HEAD、git status以及服务器实际采用的
   Java和前端部署方式；不得显示或修改API Key、数据库密码和.env内容。
2. 工作区不干净、当前分支不是version/roadagent-v1、远程历史不能快进，
   或部署方式无法确认时停止并报告，不使用reset --hard、force push或覆盖配置。
3. 不执行docs/sql下的脚本，不修改共享数据库，不重建语音Docker镜像。

执行步骤：
1. 确认当前版本包含基线61e64d4，并为当前HEAD建立本地backup分支。
2. git fetch origin，再用git pull --ff-only更新version/roadagent-v1。
3. 核对设施修复：告警ID从Java响应到TypeScript均为string；
   MysqlFacilityAlertRepository所有读取、统计和更新均过滤逻辑删除；
   facility store能丢弃过期请求，并在处置失败时保留表单。
4. 核对应急修复：emergency store只对超过120秒的同一方案版本自动恢复一次；
   EmergencyAlertCard提供人工恢复按钮；恢复继续使用原generate接口和原方案版本。
5. 用仓库Maven Wrapper执行./mvnw package -DskipTests；进入frontend执行
   npm ci和npm run build。任一步失败都停止，不部署半成品。
6. 按服务器原有方式替换后端JAR和前端dist并重启；不要启动重复端口进程。
7. 只读检查GET /api/v1/facility-alerts、facility-alerts/health-report和
   emergency-workflows/inbox；确认返回的alertId带引号且逻辑删除告警不出现。
   在页面确认设施筛选不会被旧轮询覆盖、失败表单仍保留，以及生成中卡片有
   “恢复生成”按钮。不要为验收故意修改共享数据库状态。
8. 报告更新前后提交、构建结果、重启对象和检查结果；失败时保留现场，
   可切回之前创建的backup分支重新构建，禁止删除真实配置或数据库数据。
```

如果更新后要回退，切换到上述 `backup/roadagent-server-before-fixes` 分支并用同样的 Maven/前端构建流程重新生成产物即可；不要通过删除数据库数据回退代码。确认新版本稳定后，备份分支可以继续保留一段时间。

### 2.2 当前“数据库更改”版本的协作者拉取流程

第一次在本地运行的协作者直接按第2节克隆最新分支；已经有项目的协作者使用 `git pull --ff-only`。代码拉取完成后不要扫描执行 `docs/sql/`：先向负责人确认共享库的简明预案是否已经发布。负责人会单独提供 `ROADAGENT_DB_URL`、`ROADAGENT_DB_USERNAME`、`ROADAGENT_DB_PASSWORD`，以及校园网、VPN或IP白名单要求；这些信息只能写入第3节所述的本地忽略配置。

数据库尚未发布时，新代码仍可构建和启动，也能继续读取原已发布预案，但新生成工单仍使用旧版长预案；“下发通告”、车型历史日期和容量查询不要求新增表字段。数据库负责人完成第4.1节后，所有连接同一共享库的协作者会统一读到简明预案，不需要分别执行迁移。

以下内容不会上传到 GitHub：

- 根目录 `.env`：协作者自己的 Docker 语音容器参数；
- `config/api-test.env`、`config/api-test.ps1`：真实 API 密钥和 MySQL 密码；
- `.idea/`：每位协作者自己的 IDEA 配置和数据库工具连接；
- `target/`、`node_modules/`、`frontend/dist/`：可重新生成的构建产物和依赖；
- Docker 命名卷中的 faster-whisper 模型，以及请求期间使用的临时音频；
- MySQL 中的真实表数据。

仓库会保留 Maven Wrapper、`package-lock.json`、配置示例和数据库变更脚本，因此协作者不需要复制项目负责人的本地工程目录。

## 3. 准备 API 和共享数据库配置

运行项目需要一个兼容 OpenAI 协议的模型 API 和一个共享 MySQL 数据库。交通数据不再请求地图 API，启动不需要地图 Key。

| 配置 | 由谁准备 | 要求 |
|---|---|---|
| `ROADAGENT_MODEL_API_KEY` | 协作者 | 在[DeepSeek 开放平台](https://platform.deepseek.com/api_keys)创建 API Key，并确认账号有可用余额和调用额度 |
| `ROADAGENT_DB_URL` | 项目负责人 | 完整 JDBC URL，包含数据库 IP/域名、端口、schema 和连接参数 |
| `ROADAGENT_DB_USERNAME` | 项目负责人 | 共享数据库账号 |
| `ROADAGENT_DB_PASSWORD` | 项目负责人 | 共享数据库密码 |

负责人还需要告知协作者数据库是否要求校园网、VPN 或 IP 白名单，并确认第 4 节所列的表结构和读写权限已经准备好。真实 IP、账号、密码和任何 API Key 都不能写入 README、示例文件或 Java 配置。

### 3.1 macOS / Linux

在项目根目录执行：

```bash
cp config/api-test.env.example config/api-test.env
```

然后把模型 API Key 和负责人提供的数据库信息填入本地 `config/api-test.env`，每次新开终端后执行：

```bash
source config/api-test.env
```

`config/api-test.env` 已被 Git 忽略，不能提交。

### 3.2 Windows PowerShell

在项目根目录执行：

```powershell
Copy-Item config/api-test.ps1.example config/api-test.ps1
```

填写自己申请的模型 API Key 和负责人提供的数据库信息后，每次新开 PowerShell 执行：

```powershell
. .\config\api-test.ps1
```

`config/api-test.ps1` 同样已被 Git 忽略，不能提交。PowerShell 配置只对当前终端会话生效。

### 3.3 IDEA

通过 IDEA 启动时，在 `Run → Edit Configurations → RoadAgentApplication → Environment variables` 中填写同样的环境变量。IDEA 右侧数据库工具中的数据源只供查看和执行 SQL，不会自动成为 Spring Boot 的运行时数据源。

如果使用兼容 OpenAI 协议的其他模型，可额外修改：

```bash
export ROADAGENT_MODEL_ENDPOINT=http://服务器地址/v1/chat/completions
export ROADAGENT_MODEL_NAME=服务器模型名
export ROADAGENT_MODEL_AUTH_ENABLED=false
```

Java 默认通过 `http://localhost:8091` 访问语音容器。如需显式配置或临时关闭语音入口，可设置：

```bash
export ROADAGENT_SPEECH_ENABLED=true
export ROADAGENT_SPEECH_SERVICE_URL=http://localhost:8091
```

### 3.4 Docker 语音配置

Java 配置和 Docker 配置是两套独立配置，不能互相替代：

| 本地文件 | 谁读取 | 主要内容 |
|---|---|---|
| `config/api-test.env` / `config/api-test.ps1` | 当前终端中的 Java 后端 | MySQL、DeepSeek，以及 `ROADAGENT_SPEECH_*` |
| 根目录 `.env` | `docker compose` | ASR 模型、CPU计算方式、下载源和 Edge-TTS 音色等 `SPEECH_*` |

Docker 语音服务已有可直接运行的默认值。需要查看或修改时，在项目根目录执行：

```bash
cp .env.example .env
```

Windows PowerShell 使用：

```powershell
Copy-Item .env.example .env
```

`.env` 不需要 `source`，Docker Compose 会自动读取；Java 不会读取它。`.env` 已被 Git 忽略，不能提交。使用第三方模型镜像前，应自行确认镜像来源和网络策略。

如果模型协议不同，应新增 `ChatModelPort` 适配器，Agent、Skill、Tool 和前端接口不需要修改。

## 4. 共享数据库要求

本版本不会在应用启动时自动建表或修改表结构。普通协作者连接负责人已经准备好的共享库时，**不需要执行任何 SQL**；只需填写第3节的数据库连接信息。以下操作只由数据库负责人进行，执行前必须备份并确认目标是项目 Demo schema。

如果是从空白 Demo 库建立完整历史结构，先按顺序执行基础脚本：

```text
docs/sql/20260728_emergency_dispatch.sql
docs/sql/20260819_three_level_emergency_workflow.sql
docs/sql/20260820_emergency_resource_dispatch.sql
docs/sql/20260820_seed_demo_emergency_resources.sql
docs/sql/20260820_level3_provincial_decision_metadata.sql
```

如果共享库已经具备上述基础结构，则不要重复初始化资源，直接按顺序执行本版升级脚本：

```text
docs/sql/20260904_lw_incident_emergency_migration.sql
docs/sql/20260904_fix_incident_id_collation.sql
docs/sql/20260904_seed_lw_incident_emergency_demo.sql
docs/sql/20260904_extend_emergency_resource_types.sql
docs/sql/20260904_emergency_response_plan_and_resource_coordinates.sql
docs/sql/20260904_seed_emergency_response_plans_v1.sql
docs/sql/20260904_festival_data.sql
docs/sql/20260904_verify_lw_incident_emergency.sql
docs/sql/20260904_verify_response_plans_and_coordinates.sql
docs/sql/20260904_verify_festival_data.sql
```

本版脚本依次完成 `w_lw_incident` 事件迁移、事件编号排序规则统一、Demo事件整理、16类资源适用关系、版本化预案与资源坐标、16类v1预案和节假日/模拟活动数据。三个 `verify_*.sql` 只读核验对应结果。`seed_lw_incident_emergency_demo.sql`、`extend_emergency_resource_types.sql` 和 `festival_data.sql` 都会写入或更新Demo数据；如果负责人只升级表结构而不需要仓库自带Demo数据，应先审阅脚本并按实际环境取舍，不能机械执行整段清单。协作者不要对 `docs/sql/` 批量导入。

### 4.1 2026-09-10“数据库更改”：发布简明预案

本次数据库变更只操作现有 `w_emergency_response_plan` 的预案版本数据，不创建新的业务表或字段。脚本要求当前库已经完成前述2026-09-04迁移，并且16种事件类型各有一个 `plan_status=1` 的已发布版本。脚本在事务中把旧发布版转为历史版，为每类事件插入 `MAX(plan_version)+1` 的简明版本，继承 `required_facts` 和 `resource_baseline`，并写入 `source_document='compact-20260910'`。旧工单、工作流、通告快照、事件、库存和资源占用均不修改。

普通协作者只需连接负责人准备好的共享库，**不要执行本节 SQL**。数据库负责人执行时按以下顺序操作：

1. 确认目标连接是项目共享 Demo schema，不是生产库或其他项目库，并创建可恢复的数据库快照或至少备份 `w_emergency_response_plan`；
2. 暂停所有连接该库且可能生成或返工应急方案的后端实例，避免发布切换期间产生跨版本工单；
3. 用只读查询确认当前有16种事件类型、每类恰好一个已发布版本，并确认是否已经存在 `compact-20260910`；
4. 使用负责人批准的数据库客户端执行 `docs/sql/20260910_publish_compact_response_plans.sql`，不要把密码写在命令行、README或终端共享记录中；
5. 执行账号需要具备该脚本涉及的 `SELECT`、`INSERT`、`UPDATE`、临时表以及创建、调用和删除存储过程的权限；权限不足时由数据库管理员提供一次性迁移账号，不扩大普通应用账号权限；
6. 核验成功后再启动更新后的 Java 后端；失败时脚本会回滚数据变更，应保留错误现场并由负责人处理，不执行重置脚本。

执行前可使用以下只读查询检查基线：

```sql
SELECT event_type, COUNT(*) AS active_count
FROM w_emergency_response_plan
WHERE plan_status = 1
GROUP BY event_type
ORDER BY event_type;

SELECT COUNT(DISTINCT event_type) AS compact_type_count
FROM w_emergency_response_plan
WHERE source_document = 'compact-20260910';
```

第一条应返回16行且每行 `active_count=1`。第二条返回16表示该版本已经发布，不要再次人工改状态；直接执行下面的发布后核验即可。负责人统一执行：

```text
docs/sql/20260910_publish_compact_response_plans.sql
```

发布后使用只读查询确认结果：

```sql
SELECT
  COUNT(*) AS active_count,
  COUNT(DISTINCT event_type) AS active_type_count,
  SUM(source_document = 'compact-20260910') AS compact_active_count,
  SUM(CHAR_LENGTH(REPLACE(REPLACE(rescue_plan_template, CHAR(10), ''), CHAR(13), ''))
      BETWEEN 300 AND 400) AS compact_length_valid_count
FROM w_emergency_response_plan
WHERE plan_status = 1;

SELECT event_type, plan_version, plan_status, source_document, activation_time, content_hash
FROM w_emergency_response_plan
WHERE plan_status = 1
ORDER BY event_type;
```

四个计数都应为16，明细应全部带有 `compact-20260910` 标记。脚本包含重复发布保护；即使如此，也只应由负责人统一执行一次，后续人员只做只读核验。

数据库回退不能通过删除工单、事件或库存实现。需要回退时先停止方案生成，由负责人根据执行前备份恢复预案表，或在事务中重新发布指定旧版本，并在恢复后确认每种事件仍只有一个 `plan_status=1`。仓库不提供自动回退脚本，避免协作者在共享库误切版本。

`docs/sql/20260819_reset_demo_events.sql` 是旧 Demo 的永久重置脚本，不创建备份且面向旧 `w_abnormal_event`；当前应用已经切换到 `w_lw_incident`，普通协作者和本版升级均不应执行它。

协作者连接共享数据库时不需要重复执行此脚本，也不需要 `CREATE`、`ALTER` 或 `DROP` 权限。正常运行至少需要：

- 连接共享数据库的权限；
- 对 `w_road_network_status`、`w_congestion_detection_result`、`w_highway_network` 和 `w_road_capacity` 的 `SELECT` 权限；
- 对 `w_transport_hubs`、`w_region_code` 和 `w_vehicletravelpatternanalyzer` 的 `SELECT` 权限；
- 对 `w_festival_data` 的 `SELECT` 权限；
- 对 `w_realtime_abnormal` 的 `SELECT`、`UPDATE(status, remark)` 权限；
- 对 `w_lw_incident` 的 `SELECT`、`UPDATE` 权限；
- 对 `w_emergency_dispatch_order` 的 `SELECT`、`INSERT`、`UPDATE` 权限；
- 对 `w_emergency_dispatch_workflow`、`w_emergency_professional_review`、`w_emergency_command_decision` 的 `SELECT`、`INSERT`、`UPDATE` 权限；
- 对 `w_emergency_resource` 的 `SELECT`、`UPDATE` 权限；
- 对 `w_emergency_response_plan` 的 `SELECT` 权限；
- 对 `w_emergency_resource_allocation` 的 `SELECT`、`INSERT`、`UPDATE` 权限；
- 对只增不改的 `w_emergency_dispatch_action_log`、`w_emergency_event_classification` 只授予 `SELECT`、`INSERT` 权限。

如果协作者完全没有数据库连接或上述读写权限，后端将无法查询告警或保存工单，项目的数据库应急调度功能不能运行。当前工作流使用：

- `w_realtime_abnormal`：设施预警的唯一数据源；项目读取异常值和阈值快照，处置时仅更新 `status/remark`；
- 设施告警 `id` 是 MySQL `BIGINT`，接口以十进制字符串返回，前端不得转换为 JavaScript `number`；所有查询和处置均忽略 `del_flag='Y'` 的逻辑删除记录；
- `w_lw_incident`：唯一事件数据源，业务使用 `c_no`；
- `w_emergency_event_classification`：规则、模型和人工分类的只增不改留痕；
- `w_emergency_dispatch_order`：按版本保存模型生成的应急调度方案和当时事件快照；
- `w_emergency_response_plan`：按事件类型保存已发布预案版本、固定模板和资源基线；
- `w_emergency_dispatch_workflow`：每个事件一条稳定的三级流程主记录；
- `w_emergency_professional_review`：二级市交通应急办的结构化专业会商记录；
- `w_emergency_command_decision`：三级省级决策及最终不可修改通告快照；
- `w_emergency_dispatch_action_log`：只增不改的操作流水和幂等留痕；
- `w_emergency_resource`：福建九市城市级聚合库存，是正式调度的唯一资源事实来源；
- `w_emergency_resource_allocation`：按方案版本保存软占用、已调度和已释放流水及来源城市快照。

路况业务读取 `w_road_network_status`、`w_congestion_detection_result` 和 `w_highway_network`，并按交通快照时间读取 `w_festival_data` 作为节假日及重大活动背景；通行能力业务读取 `w_road_capacity`；跨区域交通联系业务以 `w_highway_network` 的路线起终点城市建立无方向城市对，再按 `route_code` 汇总 `w_transport_hubs` 卡口流量，车型出行特征业务读取 `w_vehicletravelpatternanalyzer`。本期明确不使用 `w_checkpoint_info`；需求1-5不使用 `w_transport_hubs.region_code` 和 `temp_1`。

城市目的地联系倾向直接复用1-5的 `w_highway_network` 与 `w_transport_hubs`，不读取卡口 `region_code`，也不需要新增业务表。请负责人确认以下数据已准备好：

| 数据 | 要求 |
|---|---|
| `w_highway_network.route_code/route_name` | 活动路线编号唯一，名称非空 |
| `w_highway_network.start_place/end_place` | 可映射为福建九市；起终城市不同的路线才纳入跨市联系 |
| `w_transport_hubs.checkpoint_no/route_code` | 卡口编号唯一，路线编号能匹配活动跨市路线 |
| `w_transport_hubs.temp_2` | 卡口7日流量，必须是非负整数；同路线先对所有卡口求均值，作为路线代表联系强度 |
| `del_flag`、`update_time`、`create_time` | 有效记录的删除标记为 `N`、`0` 或 `NULL`；卡口最新更新时间用于展示数据时间 |

统计不要求每个城市对都存在直连路线；矩阵中无直接联系的单元格留空。全部无可用跨市联系时返回 `OD_ANALYSIS_NOT_FOUND`。

以下两个文件仅保留作历史方案参考，**不属于本版迁移，不要为启动项目执行**：

- [20260902_city_od_connection_result.sql](docs/sql/20260902_city_od_connection_result.sql)
- [20260902_route_city_mapping.sql](docs/sql/20260902_route_city_mapping.sql)

它们包含建表和插入/覆盖数据操作；当前 OD 实现既不读取也不要求创建对应的表。不要对 `docs/sql/` 执行通配符批量导入。

应急事件筛选与状态约定：

- 只处理 `c_type='4' AND status='1' AND completed=0 AND deleted=0` 的记录；
- `event_type` 为空时由后台每5秒按规则优先、模型兜底分类，失败留痕后退避重试；
- 三级批准或确认无需调度后回写 `status='2', completed=1`；
- 无需调度原因保存在工作流 `terminal_reason` 和动作流水，不污染贴源事件表。
- 模型调用期间若服务或网络中断，生成状态超过120秒后由可见页面自动安全恢复一次；一级卡片也保留“恢复生成”按钮。多实例同时恢复由数据库条件更新和工作流乐观锁去重。

`c_no` 是全链路字符串ID，`w_lw_incident.id` 仅保留给贴源表自身使用。展示和模型使用的事件事实字段范围止于 `route_name`，`post`、处置人员和管理单位字段不进入领域对象、模型提示或前端响应；`status/completed/deleted` 仅作为系统待办过滤及完成状态回写字段。`content` 中带有明确“联系人/联系电话”等标签的片段也会在适配层清理，贴源原文保持不变。城市不回写事件表，依次从来源、所属单位、县区地点、清理后的描述和经纬度推导，并冻结到工单快照。`w_abnormal_event` 只保留一个版本周期供回滚，应用不再读取。

16类事件的人工审阅源稿位于 `docs/福建公路应急事件分类与处置预案草案.docx`。运行时不读取Word，只读取 `w_emergency_response_plan` 中的唯一已发布版本。模型只填充受控变量、在预案基线中调整资源需求并给出有限补充建议；Java保留模板主体、补齐必选资源并将预案快照冻结到工单和最终通告。

预案后续修订不覆盖已发布行：先插入更高的 `plan_version` 草稿，审阅后在同一事务中将旧版设为 `plan_status=2`、新版设为 `plan_status=1` 并填写 `activation_time`。唯一约束保证每类事件最多只有一个已发布版本；历史工单继续使用生成时快照。

## 5. 启动项目

完整语音版由三个本地进程组成，启动顺序建议保持如下：

```text
Docker语音服务 :8091 → Java后端 :8080 → Vite前端 :5173 → 浏览器麦克风/扬声器
```

只使用文字功能时，可以跳过语音容器，直接启动后端和前端。

### 5.1 语音容器

先启动 Docker Desktop 或 Docker Engine。确认下面两条命令都成功后，再在项目根目录启动语音服务：

```bash
docker version
docker compose version
```

```bash
docker compose -f compose.speech.yml up --build -d
docker compose -f compose.speech.yml ps
docker compose -f compose.speech.yml logs -f speech-service
```

`logs -f` 会持续显示日志，按 `Ctrl+C` 只退出日志查看，不会停止容器。第一次启动会构建 Python 镜像并下载 faster-whisper `small` 模型，下载和载入期间容器会显示为 `starting` 或未就绪。可另开终端分别检查存活和就绪状态：

```bash
curl http://localhost:8091/health/live
curl http://localhost:8091/health/ready
```

`/health/live` 返回 `status: UP` 表示进程已启动；`/health/ready` 返回 `status: UP`、`asrAvailable: true` 和 `ttsAvailable: true` 后，页面语音功能才可用。

模型保存在 Docker 命名卷中。停止并删除容器、但保留已下载模型，使用：

```bash
docker compose -f compose.speech.yml down
```

普通 `down` 后，下次启动会复用模型。不要随意执行 `docker compose -f compose.speech.yml down -v`，因为 `-v` 会同时删除模型卷，下次必须重新下载。Java 后端不会因语音容器离线而启动失败。

### 5.2 后端

仓库中的 Maven Wrapper 已固定使用 Maven 3.9.16：macOS/Linux 使用 `mvnw`，Windows 使用 `mvnw.cmd`，版本和下载地址保存在 `.mvn/wrapper/maven-wrapper.properties`。当前 Wrapper 采用官方 `distributionType=only-script`，因此不需要也不会包含 `maven-wrapper.jar`；这不是文件缺失。首次运行时会从 Maven Central 下载 Maven 和项目依赖，协作者需要能够访问 `repo.maven.apache.org`。

首次拉取项目或 `pom.xml` 发生变化后，在 IDEA 中执行一次 Maven Reload；普通启动不需要每次都刷新 Maven。

在已经执行 `source config/api-test.env` 的终端中运行：

```bash
./mvnw package -DskipTests
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
```

Windows PowerShell 使用：

```powershell
. .\config\api-test.ps1
.\mvnw.cmd package -DskipTests
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
```

首次执行会下载 Maven 和 Java 依赖到用户缓存，不会安装全局 Maven。

后端启动成功后可检查：

```bash
curl http://localhost:8080/api/v1/emergency-events/pending/next
curl 'http://localhost:8080/api/v1/emergency-workflows/inbox?stage=LEVEL_1'
curl 'http://localhost:8080/api/v1/facility-alerts?status=PENDING&page=0&size=20'
curl http://localhost:8080/api/v1/facility-alerts/health-report
curl http://localhost:8080/api/v1/speech/capabilities
```

如果共享数据库中存在满足 `c_type='4'、status='1'、completed=0、deleted=0` 且已经完成 `event_type` 分类的事件，接口会返回最早的一条事件、已有最新工单以及待处理总数。

后端默认每5秒尝试分类一条尚无 `event_type` 的应急事件。规则无法确定类型时会调用配置好的模型；如果协作者只调试无需自动分类的功能，可在启动后端前设置 `ROADAGENT_EVENT_CLASSIFICATION_ENABLED=false`。关闭分类不会影响已经完成分类的事件、交通问答或设施预警。

### 5.3 前端

另开一个终端，先进入克隆后的项目根目录，再执行：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到 `http://localhost:8080`，因此必须先保证后端已启动。

页面可见时每 5 秒查询一次三级待办。“应急处置”页签可切换一级、二级和三级 Demo 视角，并显示各级数量；这只是功能演示，不代表已实现登录、真实身份或防越权。应急流程不会强制打断交通问答。

首次点击麦克风时，浏览器会请求录音权限，请选择允许。本机使用 `http://localhost:5173` 即可；通过其他域名或 IP 访问时必须配置 HTTPS，否则浏览器通常不会开放麦克风。

### 5.4 跑通后的语音验收

按顺序检查以下行为：

1. `GET http://localhost:8091/health/ready` 返回 ASR、TTS 均可用；
2. `GET http://localhost:8080/api/v1/speech/capabilities` 返回同样的能力状态，证明 Java 已连通容器；
3. 点击麦克风，说“福建省目前整体交通态势如何”，再点击“停止并识别”；识别结果应插入输入框当前光标处，但不会自动发送；“取消”会丢弃本次录音；
4. 手动发送问题，等待回答完成；点击单条回答的“朗读”，检查播放、暂停、继续和重播；
5. 打开“语音回答”后再提一个问题，只应自动朗读之后完成的新回答；
6. 停止语音容器并刷新页面，语音按钮应显示不可用，但文字问答和应急调度仍能继续。

## 6. 当前能力

### 6.1 交通问答

- 同一个交通 Skill 支持路线目录、四类路况、三类通行能力、三类跨区域交通联系、四类车型出行特征和两类城市目的地联系分析，共 17 种查询；模型只负责意图选择与摘要，所有统计均由 Java 完成；
- 全省总览使用 `w_road_network_status` 的路线整体均速和五级状态，不对路段速度二次平均；
- 异常榜单仅使用 `w_congestion_detection_result` 中 `status>=20` 的路段，按状态、`severity`、均速排序展示前 10 条；
- 城市间查询用 `w_highway_network.start_place/end_place` 双向精确匹配福建九市；单路线编号精确匹配优先，名称统一连接符和空格后精确匹配；
- 五级状态保留数据库定义：10 畅通、20 轻度拥堵、30 中度拥堵、40 重度拥堵、50 堵塞；
- 普通当前路况查询不再强制附加趋势；拥堵、异常、未来或缓解类问法才会给出未来 1–2 小时定性趋势，且可只返回趋势文字；趋势仅限“基本稳定、持续拥堵、可能加剧、逐渐缓解、局部分化”，不是精确交通预测模型；
- 趋势研判以数据库 `status` 为权威状态，均速和 `severity` 仅作辅助；不输出未来具体速度、流量、概率或解除时间，也不推测事故、施工、天气等未验证原因；当 `status>=20` 且交通快照时间、城市或路线范围命中 `w_festival_data` 时，Java会增加“可能受某节假日或重大活动叠加影响”的谨慎原因提示；
- `w_festival_data` 采用按请求即时只读，不进入30秒交通快照等待。2026年使用国务院正式放假区间，2027—2028只维护法规确定的法定日期；模拟演出和赛事以 `DEMO` 标记，可通过 `enabled=0` 停用。城市活动未配置受影响路线时不会被归因到无关的全省或单路线查询；
- 通行能力总览直接采用 `w_road_capacity.avg_previous_hour` 作为项目定义的实际通行能力（辆/小时）、`design_flow` 作为设计通行能力、`utilization_perc` 作为实际/设计利用率，Java 和模型均不重新计算这些数值；
- 通行能力采用高利用率瓶颈口径：利用率 `<=0.20` 为正常，`0.20<利用率<=0.30` 为瓶颈，`>0.30` 为严重瓶颈；利用率越高表示通行能力压力越大；
- 瓶颈路线按利用率、实际通行能力降序稳定排序，默认展示前 10 条；当前容量表是一条路线一条记录，因此只称“瓶颈路线”，不虚构路段位置；
- 路况与通行能力快照每 5 秒检查一次；每次通过 MySQL 只读一致性事务获得一个原子视图，不再要求读取前后数据库停止写入。后端冷启动会立即发布首份合法数据；运行期发现变化时继续使用上一已发布快照，候选内容连续稳定 30 秒后再原子切换。区域压力、车型分析及 OD 采用按请求读取，不经过这段稳定等待；
- 路况与通行能力业务表允许只覆盖当前批次有数据的部分活动路线，有多少条展示多少条；仍会拒绝重复路线、非活动路线、跨表名称冲突和非法数值。`w_road_capacity` 使用独立内存快照，其更新异常不会影响已有路况查询；
- Java 先完成状态映射、统计、排序、截断和当前预警事实；路况模型据此生成当前态势摘要和 1 句未来 1–2 小时定性趋势，容量模型生成专业研判。模型首次只出现JSON、句数、标点或趋势时间写法偏差时会静默修复一次；摘要不再依赖“表格”等固定词或固定句式。模型引用结构化事实之外的数字时改用Java事实摘要，真正的模型服务失败仍不发布文字、表格或语音半成品；
- 路况研判内容覆盖总体结论、状态分布、重点路线或路段、可执行的通行建议和短时定性趋势；趋势只进入已有摘要与语音，不增加表格预测列；
- 会话最多保留 20 条消息，闲置 60 分钟后失效；
- 保留 `POST /api/v1/traffic/queries`，使用与 Agent 相同的 MySQL 查询和模型摘要流程。

### 6.2 跨区域交通联系与车型出行特征

- 产品内置标准问法会先经过Java确定性识别，再进入对应交通服务，不依赖模型首次猜测。覆盖全省路况、拥堵异常、两市路况、G/S路线、通行能力总览、瓶颈排行、三市及以上城市对/跨市路线以及福州或厦门车型分析；例如“福州、宁德、南平三市的跨区域交通联系如何”“这些城市的主要跨市通道有哪些”等。模糊追问和依赖上下文的问题仍由模型规划；
- 跨区域交通联系以 `w_highway_network.start_place/end_place` 形成无方向城市对，只保留福建九市之间且起终点不同的路线，再关联同 `route_code` 的卡口。未指定城市时分析九市；指定范围时要求3至9市，并只纳入起终点均位于所选范围的路线；
- 综合查询依次展示城市对交通联系压力 Top5 和重要跨市路线 Top10；专项问题只返回相应表格。排名以 `w_transport_hubs.temp_2` 的7日总流量为主、`daily_avg_flow` 为辅助；卡口数据仅用于路线级聚合，不对外展示卡口排名；
- 结果用于识别哪些城市对、路线和卡口承担较高的跨区域交通压力，不区分方向，不推断真实OD、净流入净流出、车辆来源或途经城市。单市或两市的区域联系问法会追问补充至至少三个城市；两城市当前路况仍进入 `CITY_PAIR`；
- 区域和路线日总流量均为范围内对应卡口 `daily_avg_flow` 之和，路线均速为对应卡口 `average_speed` 算术平均值，不构造额外压力指数；
- 城市表解读仍优先使用模型内容；模型遗漏某个城市、返回重复代码或解读格式不合格时，Java按已统计的活跃卡口事实补齐安全解读，不再因此中断整次回答；
- 车型出行特征单份结果按福州或厦门生成；同时询问福州和厦门时，Agent依次执行两次并在同一回答中保留两张独立结果卡。未指定日期时按当天语境读取该城市最新有效记录；询问昨天、前天或明确年月日时，按 `create_time` 日期筛选后再以 `create_time DESC, id DESC` 选择当天最新记录；
- `car/bus/truck` 分别展示为小型客车、中型客车和大型货车。24小时折线不再因 `result2` 时间键与记录日期不同而丢弃有效点：优先采用查询日期的数据，否则采用所选记录内最新可用的分时日期；缺失时间点补0并在摘要和警告中明确“补0不代表实际无车”。工作日5天合计为 `result1-result3`，周末2天合计直接使用 `result3`；
- 前端使用固定 ECharts 模板绘制车型占比饼图、24小时折线图和工作日/周末柱状图；模型不生成图表配置，语音只朗读3–5句总结，不朗读表格和图表。

### 6.2.1 城市目的地联系倾向（需求1-7）

本业务与1-5复用同一套跨市路线—卡口事实，但展示相对的目的地联系结构，不重复1-5的绝对压力排名。

- 数据源为 `w_highway_network` 的起终城市和 `w_transport_hubs.temp_2`；不使用 `region_code`、`w_route_city_mapping` 或 `w_city_od_connection_result`。
- 同一路线先对其卡口7日流量求均值，一个城市对的联系强度是所有相关路线代表值之和；避免因某条路线卡口较多而重复放大。
- `OD_DESTINATION_TENDENCY`要求单城市，返回其与各关联城市的路线数、7日联系强度和联系倾向占比；占比分母始终是该城市在全省跨市网络中的联系强度总和。
- `OD_CONNECTION_MATRIX`用于两市、多市或全省；为空时默认九市。矩阵只缩小展示范围，不重新计算分母，因此同一城市的倾向值在不同查询范围中保持一致。
- 摘要用“目的地联系倾向”和“跨市出行联系”表达，不宣称真实车辆去向、方向性或OD概率。语音仅朗读摘要，不朗读整张矩阵。
- 问法：“福州的出行主要联系哪些城市？”、“分析福州的目的地联系倾向”、“福州和厦门的OD联系如何？”、“分析福州、厦门、泉州的城市联系矩阵”。
- 1-5回答“哪些城市对和跨市路线的绝对压力较大”；1-7回答“某城市的相对目的地联系结构”。“福州到厦门拥堵吗”仍进入实时路况查询。

### 6.3 设施健康预警

- AI抽屉第三个功能“设施预警”默认展示待确认清单，页面可见时每5秒刷新，也可切换处理中、已结束和告警等级；
- 清单显示设施名称、异常指标、实际值/状态值、阈值快照、等级、采集/触发时间、处理状态和最新备注；
- 健康报告和重点关注对象只基于 `status IN (1,2)` 的活动记录，由Java按最高风险、告警数量和持续时间确定性汇总，不调用大模型；
- 处理流程严格为 `1 待确认 → 2 处理中 → 3 已结束`，结束时必须选择“已消除”或“已忽略”；更新带原状态条件，并用带处理类型前缀的最新说明覆盖 `remark`；
- `w_threshold_config → w_monitoring_data → w_realtime_abnormal` 的阈值配置、历史对比与异常识别由上游系统负责；本功能不重算阈值，发现表内实际值与阈值快照不一致时显示“源数据待核验”；
- 当前表不含设施地点/类型和气象关联字段，因此本期以 `facility_name` 作为识别信息，不生成防台防汛专项名单。

### 6.4 数据库应急调度

- 页面可见时每 5 秒查询三级待办，每级都按事件发生时间最早优先；
- 只处理 `w_lw_incident` 中的应急待办；`event_type` 缺失时后台自动分类，前端显示待分类和失败数量；
- “应急处置”页签独立于聊天运行状态，用户可在问答与工单流程之间自主切换；
- 模型只能填充已发布预案变量，并从预案资源基线和数据库白名单交集中提出需求；不能编造资源 ID、来源城市、距离或库存；
- 模型契约要求 `rescuePlan` 返回单个文本字符串；若兼容模型返回由纯文本章节组成的JSON对象，Core会确定性合并为方案正文，数组、嵌套对象等异常结构仍会拒绝；
- Java 先使用事件同城库存，不足时按九市中心直线估算距离从近到远补足，跨市调度保留来源城市最低库存；
- 全省仍不足时工单明确显示缺口，不把缺口伪装成已调度资源；带缺口方案可继续上报，但二、三级必须补充协调依据和批示；
- 一级现场席位可要求AI返工、确认上报二级，或在尚未生成工单时二次确认“无需调度”；
- 一级可人工更正16类事件之一；已生成方案会留存旧版、释放资源并按新类型重新生成；
- 工单生成时库存由 `available` 转为 `reserved`；任一级退回时释放旧版占用并按新库存重新匹配；三级批准后转为 `dispatched`；
- 二级市交通应急办填写事件等级、资源可行性、影响研判、协同要求和专业意见；带缺口时必须选择“有缺口但可执行”并填协调要求；
- 三级省级决策席位可退回一级重新生成，或批准并生成确定性、不可修改的正式通告快照；
- 三级批准前 `w_lw_incident.status='1', completed=0`；最终通告或无需调度与本地工作流在同一事务中回写为 `status='2', completed=1`；
- 任一级退回都必须填写意见，旧版本保留为 `REJECTED`，模型生成下一版本并重新走完三级；
- 工作流、市级专业复核、省级决策和每次动作都持久化，“流程记录”可查看已通告/无需调度事件的时间线；
- 已通告记录支持输入原因、二次确认后整单归还已调度资源；最终通告快照和事件状态不因归还而改变；
- 选择“不生成”必须填写原因并二次确认，原因保存在工作流和动作流水；
- 模型失败会保存 `FAILED` 记录，事件保持待处理并允许重试；
- 当前资源是虚构 Demo 数据，不代表真实保障能力；九市中心经纬度保存在资源表，Java用Haversine公式计算城市级直线距离，仅用于资源排序，不代表道路里程、到达时间或路线；
- 聊天识别到应急调度意图时只引导用户使用“应急处置”页签，不创建无数据库来源的正式工单。

### 6.5 语音输入与回答朗读

- 输入框麦克风按钮支持开始、停止和取消录音；最长 60 秒、最大 10 MB，识别文字插入当前光标位置，不会自动发送；
- 浏览器优先使用 WebM/Opus，按能力回退到 MP4/AAC 或 Ogg/Opus；非 `localhost` 部署需要 HTTPS 才能稳定获取麦克风权限；
- “语音回答”每次打开页面默认关闭；打开后只自动朗读之后完成的助手回答，关闭会立即停止并清空播放队列；
- 每条已完成的助手消息均有独立的播放、暂停、继续和重播按钮，同一时刻只播放一条；
- 中文按自然标点分段，播放当前段时预合成下一段，以降低首段等待和段间停顿；
- 交通查询只朗读通过校验的研判摘要，不朗读表格数据；一般摘要为 3–5 句，OD 摘要允许句数差异；
- 应急告警卡和正式调度工单不会自动朗读；Edge-TTS 不需要 API Key，但必须联网，并会把待朗读文本发送到 Microsoft 在线语音服务；
- 语音容器不可用、ASR/TTS 失败或被用户取消时，只影响语音功能，不影响已有文字和其他业务流程。

## 7. 工程模块

| 模块 | 作用 | 主要内容 |
|---|---|---|
| `road-agent-domain` | 纯业务对象和规则 | 交通领域、设施预警、事件分类、版本化预案、资源库存/分配/缺口、三级流程和通告快照 |
| `road-agent-application` | 模块间稳定契约 | UseCase、Port、命令、交通/设施/应急结果和 Agent 事件 |
| `road-agent-core` | Agent 和业务工作流 | 意图规划、交通统计、设施健康汇总与状态机、事件分类、预案填充、三级状态机和最终通告事务编排 |
| `road-agent-adapters` | 外部能力实现 | MySQL 交通/设施/事件/预案/资源/三级流程、城市坐标距离、DeepSeek和事务适配器 |
| `road-agent-interface` | HTTP 边界 | REST、SSE、请求响应 DTO 和错误转换 |
| `road-agent-boot` | 统一装配 | Spring Boot 启动、配置和具体实现选择 |
| `frontend` | 指挥大屏与交互 | AI抽屉、ECharts车型图表、OD联系矩阵、设施预警、三级待办、会商表、通告和时间线 |
| `speech-service` | Docker语音服务 | FastAPI、faster-whisper、Edge-TTS、健康检查和无外部依赖的替身测试 |

依赖方向：

```text
interface / core / adapters → application → domain
boot → 装配全部模块
```

外部平台 DTO 不进入核心层。未来接入甲方交通、资源或工单接口时，新增对应 Port 的 Adapter 即可。

## 8. 主要接口

```text
POST /api/v1/conversations/{conversationId}/messages/stream
GET  /api/v1/emergency-events/pending/next
POST /api/v1/emergency-events/{eventId}/dispatches
POST /api/v1/emergency-events/{eventId}/no-dispatch
POST /api/v1/emergency-events/{eventId}/classification-retries
GET  /api/v1/dispatches/{planId}
POST /api/v1/dispatches/{planId}/approvals
GET  /api/v1/emergency-workflows/inbox?stage=LEVEL_1|LEVEL_2|LEVEL_3
POST /api/v1/emergency-workflows/{workflowId}/level-1-decisions
POST /api/v1/emergency-workflows/{workflowId}/event-type-corrections
POST /api/v1/emergency-workflows/{workflowId}/professional-reviews
POST /api/v1/emergency-workflows/{workflowId}/command-decisions
POST /api/v1/emergency-workflows/{workflowId}/resource-releases
GET  /api/v1/emergency-workflows/{workflowId}
GET  /api/v1/emergency-workflows/notices?completionStatus=PENDING&page=0&size=20
GET  /api/v1/emergency-workflows/history
GET  /api/v1/facility-alerts?status=PENDING&alarmLevel=EMERGENCY&page=0&size=20
GET  /api/v1/facility-alerts/health-report
GET  /api/v1/facility-alerts/focus?limit=10
POST /api/v1/facility-alerts/{alertId}/status-transitions
POST /api/v1/traffic/queries
GET  /api/v1/speech/capabilities
POST /api/v1/speech/transcriptions
POST /api/v1/speech/syntheses
```

流式入口返回 SSE 事件，包括运行阶段、意图、Skill、Tool、文字增量、独立朗读文本 `answer.speech`、业务结果、审批要求和失败信息。接口契约见 [contracts/openapi/traffic-api.yaml](contracts/openapi/traffic-api.yaml)。

本版沿用 `POST /api/v1/traffic/queries`，没有新增独立 OD URL。类型为 `OD_DESTINATION_TENDENCY` 和 `OD_CONNECTION_MATRIX`，结果分别使用 `odDestinationRows` 和 `odMatrixRows`。例如请求体：

```json
{"queryType":"OD_DESTINATION_TENDENCY","selectedCities":["福州"]}
```

车型历史日期同样使用该接口，例如查询厦门2026年9月9日当天最新记录：

```json
{"queryType":"VEHICLE_PATTERN_OVERVIEW","analysisCity":"厦门","analysisDate":"2026-09-09"}
```

此接口也会调用模型生成摘要，需要有效的模型配置，并非仅验证数据库连接的健康接口。

## 9. 推荐阅读顺序

1. `AgentController`：自然语言请求如何进入后端；
2. `AgentRuntime`：规划、选择 Skill、执行和记忆如何串联；
3. `IntentPlanner` 与 `SkillRegistry`：模型选择和 Java 白名单的边界；
4. `HighwayTrafficSkill`、`UnifiedTrafficQueryService` 与 `OdTrafficService`：17 种交通查询如何分流，目的地联系倾向如何按跨市路线确定性统计；
5. `EmergencyWorkflowController`、`DispatchApplicationService` 与 `NoticePanel.vue`：三级状态机、版本返工、通告分页、资源归还、幂等和最终通告事务；
6. `MysqlHighwayTrafficSnapshotSource`、`InMemoryHighwayTrafficSnapshotCache` 与 Port：一致性读取、完整性校验和原子发布；
7. `EmergencyResourceAllocator`、`MysqlEmergencyResourceRepository`、`MysqlResourceAllocationRepository` 与 `FujianCityDistanceAdapter`：受限资源需求如何转成库存占用、跨市调度和缺口；
8. `AbnormalEventRepository`、`EmergencyEventClassificationService`、`MysqlEmergencyResponsePlanRepository` 与 `SpringUnitOfWork`：事件贴源、自动分类、版本化预案和事务边界；
9. `FacilityAlertController`、`FacilityAlertService` 与 `MysqlFacilityAlertRepository`：设施预警查询、健康汇总和并发安全状态流转；
10. `VehiclePatternCharts.vue` 与 `TrafficResultPanel.vue`：ECharts车型图表和OD联系矩阵如何绘制；
11. `OpenAiCompatibleChatModelAdapter`：结构化输出和流式输出如何实现；
12. `SpeechController`、`SpeechApplicationService` 与 `PythonSpeechServiceAdapter`：Java 如何隔离语音容器故障；
13. `speech-service/app` 与前端 `speech` Store：识别、自然分段、预合成和播放取消；
14. [语音录音停止与识别问题修复日志](docs/VOICE_INPUT_RECORDING_BUGFIX_20260812.md)：历史故障、当前抽屉结构下的修复和回归验证；
15. [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md)：三人后续练习任务。

## 10. 可选开发验证（不是启动步骤）

以下命令供后续开发按改动范围选用，协作者首次启动无需全部执行。2026-09-09修复运行了前端51项、后端62项定向测试；2026-09-10“数据库更改”版本整理运行了通告、预案、车型日期和容量相关的前端33项、后端76项定向测试。两次均没有运行Docker、真实数据库或真实模型，本次也没有执行共享库发布脚本和浏览器验收。不要把历史验收记录当作每次推送的测试结果。

未配置外部环境开关时，测试使用本地替身；设置 `ROADAGENT_DB_URL` 后会启用真实 MySQL 集成测试，设置 `ROADAGENT_MODEL_LIVE_TEST=true` 后会启用真实模型测试并可能产生 API 费用。仅在明确需要时使用相应配置，数据库测试应使用负责人认可的测试环境：

```bash
./mvnw test
(cd frontend && npm test -- --run)
(cd frontend && npm run build)
docker compose -f compose.speech.yml --profile test run --rm speech-tests
```

Python 测试使用替身 ASR/TTS，不下载模型，也不会访问 Edge-TTS。若后续修改语音镜像或语音链路，再按需要检查 CPU 架构兼容、模型加载及复用、道路名称识别和只朗读摘要等行为；普通文档整理不需要重复执行。

## 11. 常见问题

### IDEA 右侧看不到新表

确认 IDEA 数据源连接的是 `ROADAGENT_DB_URL` 中的同一台 MySQL 和同一个 schema，然后对该 schema 执行 `Synchronize` 或刷新。`.idea/` 不会上传，所以协作者需要各自新建数据源。

### 有待处理事件但前端没有红色告警

依次确认：

1. 后端已在 `8080` 端口启动；
2. `GET /api/v1/emergency-workflows/inbox?stage=LEVEL_1` 能返回事件和三级数量；
3. 事件满足 `c_type='4' AND status='1' AND completed=0 AND deleted=0`，且 `event_type` 已分类；
4. 前端已在 `5173` 端口启动，浏览器页面处于可见状态；
5. 前后端终端中没有数据库连接或代理错误。

### 设施预警页没有数据或无法处置

先调用 `GET /api/v1/facility-alerts?status=PENDING&page=0&size=20`。如果返回空列表，请负责人确认 `w_realtime_abnormal` 中是否存在相应状态的数据；本项目不会从 `w_threshold_config` 或 `w_monitoring_data` 重新生成异常。能够查看但无法更新时，检查账号是否拥有 `UPDATE(status, remark)` 权限。状态只允许“待确认→处理中→已结束”，结束时还必须选择“已消除”或“已忽略”并填写备注。

### 是否每次都要 Maven Reload 或运行测试

不需要。首次拉取或 `pom.xml` 变化后执行 Maven Reload；开发测试按改动范围选择，不是每次启动的前置条件。首次启动按第 5 节构建后端和安装前端依赖；后端源码有更新时重新打包，前端锁文件变化时重新执行 `npm ci`。

### 语音按钮不可用或容器一直未就绪

先执行 `docker version`。如果看不到 Server 信息，说明 Docker 引擎尚未启动；先打开 Docker Desktop 或启动 Docker Engine。然后执行 `docker compose -f compose.speech.yml ps` 和 `docker compose -f compose.speech.yml logs -f speech-service`。首次启动通常是在下载或载入模型；确认 Docker 能访问模型下载地址，并给 Docker 至少 4 GB 可用内存。`GET /api/v1/speech/capabilities` 会反映 Java 当前探测到的 ASR/TTS 状态，容器恢复后刷新页面即可。

如果日志提示 `8091` 端口已被占用，先停止占用该端口的旧进程或旧容器，再重新启动。本项目把语音端口绑定到 `127.0.0.1`，不要为了协作调试直接改成 `0.0.0.0` 暴露到局域网。

如果模型日志长时间停在 Hugging Face 下载且健康状态一直为 `starting`，可以在项目根目录本地 `.env` 中添加下面两行，再重新执行 `docker compose -f compose.speech.yml up --build -d`：

```dotenv
SPEECH_HF_ENDPOINT=https://hf-mirror.com
SPEECH_ASR_MODEL_BASE_URL=https://hf-mirror.com/Systran/faster-whisper-small/resolve/main
```

第二项会启用容器内可恢复的标准 HTTP 下载，网络中断时从模型卷里的 `.part` 文件继续，不重复下载已经完成的部分。`.env` 已被 Git 忽略，这些配置只影响语音容器；协作者所在网络可正常访问 Hugging Face 时无需设置。

如果只有 TTS 失败，检查容器能否访问互联网；Edge-TTS 依赖 Microsoft 在线语音服务。麦克风无权限时，检查浏览器站点权限；远程部署需使用 HTTPS，本机 `http://localhost` 可直接调试。

### 当前尚未实现的部分

当前已实现 MySQL Demo 资源库存、占用、跨市分配和归还，但尚未接入甲方真实资源接口。真实登录鉴权与角色防越权、外部工单/通知平台、知识库以及基于实时地理数据的交互地图也尚未实现；首页福建地图目前是静态指挥大屏背景，不代表实时 GIS 图层。
