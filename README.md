# 福建应急交通 Agent

这是一个不依赖 LangChain 的教学型 Agent 项目，当前已经打通三条纵向闭环：

```text
交通问答：自然语言 → 识别路况、通行能力、区域交通压力、车型出行特征或城市OD七日统计 → MySQL只读事实 → Java确定性统计 → 模型摘要与可视化结果
应急调度：MySQL 异常事件 → AI提出受限资源需求 → Java按库存和九市距离分配 → 三级上报通告 → 归还资源 → 全程留痕
语音交互：浏览器录音 → Java语音接口 → Docker内faster-whisper识别；回答摘要 → Edge-TTS分段合成 → 浏览器播放
```

DeepSeek 负责意图理解和应急方案生成；Java 负责交通事实回答、Skill 白名单、参数校验、Tool 调用、审批和状态转换。模型不能直接创建工单、修改业务状态或改写交通事实。

本版新增城市 OD 七日卡口统计：可选择福建 1—9 个城市，查看城市流量对比及关键路线的分车型流量，路线表默认展示 10 条并支持展开。这里的 OD 是项目约定的城市卡口并集统计，不是车辆真实起终点或城市间净流入流出分析。

首次协作按下面的顺序即可跑通，测试不是启动前置条件：

1. 准备第 1 节环境，按第 2 节克隆指定分支。
2. 按第 3 节申请自己的 DeepSeek Key，向负责人获取数据库配置，并复制本地配置文件。
3. 请负责人确认第 4 节数据库结构、权限和 OD 字段数据；使用共享库的协作者不要自行批量执行 SQL。
4. 按第 5 节启动 Java 后端和 Vue 前端；需要录音/朗读时再启动 Docker 语音服务。
5. 在 AI 抽屉输入“福州和厦门的OD情况如何？”，查看摘要和两张统计表；数据不足时先请负责人确认对应城市数据。

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

本版本不会在应用启动时自动建表或修改表结构。“建表/迁移脚本”是由数据库负责人对目标 schema 执行一次的 SQL。项目负责人应确保共享数据库依次执行过：

```text
docs/sql/20260728_emergency_dispatch.sql
docs/sql/20260819_three_level_emergency_workflow.sql
docs/sql/20260820_emergency_resource_dispatch.sql
docs/sql/20260820_seed_demo_emergency_resources.sql
docs/sql/20260820_level3_provincial_decision_metadata.sql
```

前两个脚本创建基础工单与三级工作流；第三个增加事件城市、资源需求/缺口快照，并创建资源库存与占用流水表；第四个仅插入不存在的 `resource_id`，生成 108 条福建九市虚构 Demo 资源，不覆盖已人工修改的库存；第五个只把既有三级表的数据库注释统一为“省级决策”，不修改状态编码或业务数据。可用 `docs/sql/20260820_verify_emergency_resources.sql` 只读核验数量与一致性。在 IDEA 右侧数据库工具中执行时，必须先选中 `ROADAGENT_DB_URL` 指向的同一个 schema，执行后再同步表列表。

协作者连接共享数据库时不需要重复执行此脚本，也不需要 `CREATE`、`ALTER` 或 `DROP` 权限。正常运行至少需要：

- 连接共享数据库的权限；
- 对 `w_road_network_status`、`w_congestion_detection_result`、`w_highway_network` 和 `w_road_capacity` 的 `SELECT` 权限；
- 对 `w_transport_hubs`、`w_region_code` 和 `w_vehicletravelpatternanalyzer` 的 `SELECT` 权限；
- 对 `w_abnormal_event` 的 `SELECT`、`UPDATE` 权限；
- 对 `w_emergency_dispatch_order` 的 `SELECT`、`INSERT`、`UPDATE` 权限；
- 对 `w_emergency_dispatch_workflow`、`w_emergency_professional_review`、`w_emergency_command_decision` 的 `SELECT`、`INSERT`、`UPDATE` 权限；
- 对 `w_emergency_resource` 的 `SELECT`、`UPDATE` 权限；
- 对 `w_emergency_resource_allocation` 的 `SELECT`、`INSERT`、`UPDATE` 权限；
- 对只增不改的 `w_emergency_dispatch_action_log` 只授予 `SELECT`、`INSERT` 权限。

如果协作者完全没有数据库连接或上述读写权限，后端将无法查询告警或保存工单，项目的数据库应急调度功能不能运行。当前工作流使用：

- `w_abnormal_event`：保存异常事件；
- `w_emergency_dispatch_order`：按版本保存模型生成的应急调度方案和当时事件快照；
- `w_emergency_dispatch_workflow`：每个事件一条稳定的三级流程主记录；
- `w_emergency_professional_review`：二级市交通应急办的结构化专业会商记录；
- `w_emergency_command_decision`：三级省级决策及最终不可修改通告快照；
- `w_emergency_dispatch_action_log`：只增不改的操作流水和幂等留痕；
- `w_emergency_resource`：福建九市城市级聚合库存，是正式调度的唯一资源事实来源；
- `w_emergency_resource_allocation`：按方案版本保存软占用、已调度和已释放流水及来源城市快照。

路况业务读取 `w_road_network_status`、`w_congestion_detection_result` 和 `w_highway_network`，通行能力业务读取 `w_road_capacity`；区域交通压力业务读取 `w_transport_hubs` 并使用 `w_region_code` 映射城市，车型出行特征业务读取 `w_vehicletravelpatternanalyzer`。本期明确不使用 `w_checkpoint_info`，运行时也不使用 `w_transport_hubs.temp_1`。

城市 OD 七日统计同样只读取 `w_transport_hubs` 和 `w_region_code`，不需要新增业务表。请负责人确认以下现有字段及数据已准备好；仅有连接账号、但没有这些字段或有效数据，不能跑通 OD 查询：

| 数据 | 要求 |
|---|---|
| `w_transport_hubs.checkpoint_no`、`region_code` | 所选范围内卡口编号唯一且非空；城市代码对应福建地级市，并在 `w_region_code.code/name` 中有有效映射 |
| `daily_avg_flow`、`average_speed` | 日均流量为非负整数，均速为非负数；0 是有效数据，缺失值不能用 0 冒充 |
| `temp_2` | 七日总流量，非负整数字符串，例如 `151` |
| `temp_3` | 七日分车型 JSON，例如 `{"car":75,"bus":40,"truck":36}`；三个值都必须是非负整数，仅查询城市流量表时不读取此字段 |
| `route_code`、`route_name` | 通道统计按路线编号分组，编号不能为空，同一路线名称不得冲突 |
| `del_flag`、`update_time`、`create_time` | 有效记录的删除标记为 `N`、`0` 或 `NULL`；更新时间用于展示数据时间，不代表七日区间的起止日期 |

不要求九市数据全部齐备。部分城市无数据时显示缺失提示，其余城市仍可展示；全部无数据返回 `OD_ANALYSIS_NOT_FOUND`。字段非法或重复卡口返回 `OD_ANALYSIS_DATA_INVALID`，连接/表结构问题可能返回 `OD_ANALYSIS_DATA_UNAVAILABLE`。请联系负责人核对数据和权限，不要用重置脚本排查。

以下两个文件仅保留作历史方案参考，**不属于本版迁移，不要为启动项目执行**：

- [20260902_city_od_connection_result.sql](docs/sql/20260902_city_od_connection_result.sql)
- [20260902_route_city_mapping.sql](docs/sql/20260902_route_city_mapping.sql)

它们包含建表和插入/覆盖数据操作；当前 OD 实现既不读取也不要求创建对应的表。不要对 `docs/sql/` 执行通配符批量导入。

关键状态约定：

- `event_status=0`：三级流程尚未办结，一级、二级、三级流转期间始终保持 `0`；
- `event_status=1`：三级最终批准并生成通告；
- `event_status=2`：已确认无需调度；
- `del_flag=N`、`0` 或 `NULL`：有效数据；`del_flag=Y` 或 `1`：逻辑删除。

`w_abnormal_event.id` 是 `BIGINT`，后端会以字符串形式返回给前端，避免 JavaScript 精度丢失。正式生成工单前，事件必须有 `event_city_code/event_city_name`；福清、闽侯和平潭在本期统一归入福州调度范围。若需要在 IDEA 中查看共享数据库，应单独创建 MySQL 数据源并选择连接参数中的 schema；刷新表列表只会刷新 IDEA 缓存，不会改变后端连接。

`docs/sql/20260819_reset_demo_events.sql` 只用于当前 Demo 数据的永久重置，不依赖固定事件条数。脚本会锁定执行时 `w_abnormal_event` 中实际存在的事件，删除关联资源占用、工单和三级历史，将资源库存恢复为初始可用状态，并恢复 `event_status=0`、清空无需调度原因；事件原始创建信息保持不变。脚本不创建备份，普通协作者不需要执行；未经数据库负责人明确确认，不得在共享验收或生产数据上执行。

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
curl http://localhost:8080/api/v1/speech/capabilities
```

如果共享数据库中存在 `event_status=0` 且未逻辑删除的事件，接口会返回最早的一条事件、已有最新工单以及待处理总数。

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

- 同一个交通 Skill 支持四类路况、三类通行能力、四类区域交通压力、四类车型出行特征和三类城市 OD 七日统计，共 18 种查询；模型只负责意图选择与摘要，所有统计均由 Java 完成；
- 全省总览使用 `w_road_network_status` 的路线整体均速和五级状态，不对路段速度二次平均；
- 异常榜单仅使用 `w_congestion_detection_result` 中 `status>=20` 的路段，按状态、`severity`、均速排序展示前 10 条；
- 城市间查询用 `w_highway_network.start_place/end_place` 双向精确匹配福建九市；单路线编号精确匹配优先，名称统一连接符和空格后精确匹配；
- 五级状态保留数据库定义：10 畅通、20 轻度拥堵、30 中度拥堵、40 重度拥堵、50 堵塞；
- 四类路况查询都会在当前态势摘要后给出未来 1–2 小时的定性趋势，趋势限定为“基本稳定、持续拥堵、可能加剧、逐渐缓解、局部分化”；该结果只依据当前 `status`、`uniform_speed`、`severity` 与交通常识作启发式研判，不是精确交通预测模型；
- 趋势研判以数据库 `status` 为权威状态，均速和 `severity` 仅作辅助；不输出未来具体速度、流量、概率或解除时间，也不推测事故、施工、天气等拥堵原因；
- 通行能力总览直接采用 `w_road_capacity.avg_previous_hour` 作为项目定义的实际通行能力（辆/小时）、`design_flow` 作为设计通行能力、`utilization_perc` 作为实际/设计利用率，Java 和模型均不重新计算这些数值；
- 通行能力采用三级项目口径：利用率 `>=0.80` 为正常，`0.30<利用率<0.80` 为瓶颈，`<=0.30` 为严重瓶颈；数据库中的 0 是有效值并判定为严重瓶颈；
- 瓶颈路线按利用率、实际通行能力升序稳定排序，默认展示前 10 条；当前容量表是一条路线一条记录，因此只称“瓶颈路线”，不虚构路段位置；
- 路况与通行能力快照每 5 秒检查一次；每次通过 MySQL 只读一致性事务获得一个原子视图，不再要求读取前后数据库停止写入。后端冷启动会立即发布首份合法数据；运行期发现变化时继续使用上一已发布快照，候选内容连续稳定 30 秒后再原子切换。区域压力、车型分析及 OD 采用按请求读取，不经过这段稳定等待；
- 路况与通行能力业务表允许只覆盖当前批次有数据的部分活动路线，有多少条展示多少条；仍会拒绝重复路线、非活动路线、跨表名称冲突和非法数值。`w_road_capacity` 使用独立内存快照，其更新异常不会影响已有路况查询；
- Java 先完成状态映射、统计、排序、截断和当前预警事实；路况模型据此生成当前态势摘要和 1 句未来 1–2 小时定性趋势，容量模型生成专业研判。模型首次只出现JSON、句数、标点或趋势时间写法偏差时会静默修复一次；摘要不再依赖“表格”等固定词或固定句式。模型引用结构化事实之外的数字时改用Java事实摘要，真正的模型服务失败仍不发布文字、表格或语音半成品；
- 路况研判内容覆盖总体结论、状态分布、重点路线或路段、可执行的通行建议和短时定性趋势；趋势只进入已有摘要与语音，不增加表格预测列；
- 会话最多保留 20 条消息，闲置 60 分钟后失效；
- 保留 `POST /api/v1/traffic/queries`，使用与 Agent 相同的 MySQL 查询和模型摘要流程。

### 6.2 区域交通压力与车型出行特征

- 产品内置标准问法会先经过Java确定性识别，再进入对应交通服务，不依赖模型首次猜测。覆盖全省路况、拥堵异常、两市路况、G/S路线、通行能力总览、瓶颈排行、区域卡口/城市/路线压力以及福州或厦门车型分析；例如“福建省各国省道通行能力利用率怎么样”“宁德到福州交通情况”“G104当前通行情况”“福州市的交通运输特征如何”等。模糊追问和依赖上下文的问题仍由模型规划；
- 区域交通压力按 `w_transport_hubs.region_code` 筛选范围：不指定城市统计全省，指定一个城市统计该市，指定两个城市统计两市卡口并集；该口径不计算城市OD流量，也不将卡口流量描述成某一方向的车辆数；
- 综合查询依次展示日均流量 Top20 卡口、日总流量 Top5 城市和 Top10 路线；明确询问卡口、城市或路线时只返回相应表格。活跃卡口固定为 `daily_avg_flow>100`，交通枢纽占比为城市活跃卡口数除以当前查询范围全部卡口数；
- 区域和路线日总流量均为范围内对应卡口 `daily_avg_flow` 之和，路线均速为对应卡口 `average_speed` 算术平均值，不构造额外压力指数；
- 城市表解读仍优先使用模型内容；模型遗漏某个城市、返回重复代码或解读格式不合格时，Java按已统计的活跃卡口事实补齐安全解读，不再因此中断整次回答；
- 车型出行特征目前按单城市分析福州或厦门，每次执行 `create_time DESC, id DESC` 选取该城市最新有效记录；`result1` 为一周三车型总量，`result2` 为24小时数据，`result3` 为周末两天总量；
- `car/bus/truck` 分别展示为小型客车、中型客车和大型货车。24小时缺失时间点补0，工作日5天合计为 `result1-result3`，周末2天合计直接使用 `result3`；
- 前端使用固定 ECharts 模板绘制车型占比饼图、24小时折线图和工作日/周末柱状图；模型不生成图表配置，语音只朗读3–5句总结，不朗读表格和图表。

### 6.2.1 城市OD七日统计（需求1-7）

本业务采用项目约定的“城市卡口并集统计”口径，与1-5使用同一数据表但采用不同字段、意图和展示；不表示车辆真实起讫点、方向、净流入净流出或去重出行量。

- 唯一业务表为 `w_transport_hubs`，以 `region_code` 和 `w_region_code` 映射城市。独立只读一致性事务，不等待30秒，不校验九市/48条路线齐全，不使用 `w_highway_network`、`w_route_city_mapping`、`w_city_od_connection_result`。同一事务只保证读取视图一致，不保证协作者分次提交的业务批次绝对完整。
- `OD_OVERVIEW`返回两张表；`OD_CITY_FLOW`仅返回“城市区域流量不平衡”；`OD_KEY_CHANNELS`仅返回“城市交通关键OD通道”。REST和SSE均增加 `odCityFlowRows、odChannelRows、periodDays、missingRegions`，旧字段保持兼容。
- `selectedCities`允许1至9个福建地级市；为空默认全省；两市或多市按卡口并集，不求共同路线。部分城市无数据时返回其他城市及提示；全部无数据为 `OD_ANALYSIS_NOT_FOUND`，不能填0代替无数据。
- 城市表：卡口数为全部有效卡口数量；7日总量为 `SUM(temp_2)`，日均流量为 `SUM(daily_avg_flow)`，均速为包含有效0值的卡口均速算术平均（两位小数）。不沿用1-5的活跃阈值或Top5。
- 通道表：在所选城市范围内按 `route_code` 汇总 `temp_2` 和 `temp_3.car/bus/truck`；车型映射为小型客车、中型客车、大型货车，均为7天总量。两表按7日总量降序、代码升序排列；后端返回所有路线，前端默认显示10条，可展开全部。
- `temp_2`为非负整数字符串，`temp_3`示例为 `{"car":75,"bus":40,"truck":36}`。必要值非法、重复卡口等返回 `OD_ANALYSIS_DATA_INVALID`；校验局限查询范围。仅查城市表不读取或校验车型JSON。车型合计与总量不一致仅提示，不修正源值。
- 不强制7日总量等于日均流量乘7；`periodDays=7`来自数据库字段口径，`acquiredAt`是所选记录的最新更新时间，不作为历史统计起止日期。历史日期请求先追问是否改查最新7天数据。
- 摘要由Java事实驱动模型生成，允许句数及措辞差异；新增数值、方向/真实OD或原因结论会改用事实摘要。模型超时、空响应或JSON解析失败时不发布文本、表格或语音半成品。语音仅朗读摘要；本业务不生成1–2小时拥堵趋势。
- 问法：“福州和厦门的OD情况如何？”、“分析福州、厦门、泉州的OD情况。”、“福建各城市近7天流量分布是否均衡？”、“福州和厦门有哪些关键OD通道，各车型流量多少？”。
- “交通压力/卡口排名/区域交通联系”继续进入1-5；“OD通道的车型流量”优先进入1-7，不能误入1-6；“福州到厦门拥堵吗”仍是路况查询。上下文中的增减城市、切换表格由规划器结合历史解析。
- 旧的两份 `docs/sql/20260902_*.sql` 是此前方案草稿，本业务不会执行，也不要求创建对应表。共享数据库只读测试：配置数据库环境变量后执行 `./mvnw test -Dtest=OdTrafficReadOnlyIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`，不启动应用或写入数据库。

### 6.3 数据库应急调度

- 页面可见时每 5 秒查询三级待办，每级都按事件发生时间最早优先；
- “应急处置”页签独立于聊天运行状态，用户可在问答与工单流程之间自主切换；
- 模型只能从数据库资源类型白名单中提出需求数量和用途，不能编造资源 ID、来源城市、距离或库存；
- 模型契约要求 `rescuePlan` 返回单个文本字符串；若兼容模型返回由纯文本章节组成的JSON对象，Core会确定性合并为方案正文，数组、嵌套对象等异常结构仍会拒绝；
- Java 先使用事件同城库存，不足时按九市中心直线估算距离从近到远补足，跨市调度保留来源城市最低库存；
- 全省仍不足时工单明确显示缺口，不把缺口伪装成已调度资源；带缺口方案可继续上报，但二、三级必须补充协调依据和批示；
- 一级现场席位可要求AI返工、确认上报二级，或在尚未生成工单时二次确认“无需调度”；
- 工单生成时库存由 `available` 转为 `reserved`；任一级退回时释放旧版占用并按新库存重新匹配；三级批准后转为 `dispatched`；
- 二级市交通应急办填写事件等级、资源可行性、影响研判、协同要求和专业意见；带缺口时必须选择“有缺口但可执行”并填协调要求；
- 三级省级决策席位可退回一级重新生成，或批准并生成确定性、不可修改的正式通告快照；
- 三级批准前 `event_status` 始终为 `0`；只有最终通告与事件、方案、决策和流水在同一事务内办结后才改为 `1`；
- 任一级退回都必须填写意见，旧版本保留为 `REJECTED`，模型生成下一版本并重新走完三级；
- 工作流、市级专业复核、省级决策和每次动作都持久化，“流程记录”可查看已通告/无需调度事件的时间线；
- 已通告记录支持输入原因、二次确认后整单归还已调度资源；最终通告快照和事件状态不因归还而改变；
- 选择“不生成”必须填写原因并二次确认，事件状态改为 `2`；
- 模型失败会保存 `FAILED` 记录，事件保持待处理并允许重试；
- 当前 108 条资源是虚构 Demo 数据，不代表真实保障能力；城市距离仅用于资源排序，不代表道路里程、到达时间或路线；
- 聊天识别到应急调度意图时只引导用户使用“应急处置”页签，不创建无数据库来源的正式工单。

### 6.4 语音输入与回答朗读

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
| `road-agent-domain` | 纯业务对象和规则 | 交通领域、事件、资源库存/分配/缺口、版本化方案、三级流程和通告快照 |
| `road-agent-application` | 模块间稳定契约 | UseCase、Port、命令、结果、Agent 事件 |
| `road-agent-core` | Agent 和业务工作流 | 意图规划、Skill 注册、交通 Skill、三级状态机、返工和最终通告事务编排 |
| `road-agent-adapters` | 外部能力实现 | MySQL 交通快照/事件/方案/应急资源/三级流程、九市距离、DeepSeek、事务适配器 |
| `road-agent-interface` | HTTP 边界 | REST、SSE、请求响应 DTO 和错误转换 |
| `road-agent-boot` | 统一装配 | Spring Boot 启动、配置和具体实现选择 |
| `frontend` | 对话界面 | 数字人、流式消息、交通卡片、三级待办、会商表、通告和时间线 |
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
GET  /api/v1/dispatches/{planId}
POST /api/v1/dispatches/{planId}/approvals
GET  /api/v1/emergency-workflows/inbox?stage=LEVEL_1|LEVEL_2|LEVEL_3
POST /api/v1/emergency-workflows/{workflowId}/level-1-decisions
POST /api/v1/emergency-workflows/{workflowId}/professional-reviews
POST /api/v1/emergency-workflows/{workflowId}/command-decisions
POST /api/v1/emergency-workflows/{workflowId}/resource-releases
GET  /api/v1/emergency-workflows/{workflowId}
GET  /api/v1/emergency-workflows/history
POST /api/v1/traffic/queries
GET  /api/v1/speech/capabilities
POST /api/v1/speech/transcriptions
POST /api/v1/speech/syntheses
```

流式入口返回 SSE 事件，包括运行阶段、意图、Skill、Tool、文字增量、独立朗读文本 `answer.speech`、业务结果、审批要求和失败信息。接口契约见 [contracts/openapi/traffic-api.yaml](contracts/openapi/traffic-api.yaml)。

本版沿用 `POST /api/v1/traffic/queries`，没有新增独立 OD URL。新增类型为 `OD_OVERVIEW`、`OD_CITY_FLOW`、`OD_KEY_CHANNELS`；OD 的 `selectedCities` 支持 1—9 市或留空查全省，其他类型的城市范围限制不变。REST 和 SSE 交通结果增加 `odCityFlowRows`、`odChannelRows`、`periodDays`、`missingRegions`。例如请求体：

```json
{"queryType":"OD_OVERVIEW","selectedCities":["福州","厦门"]}
```

此接口也会调用模型生成摘要，需要有效的模型配置，并非仅验证数据库连接的健康接口。

## 9. 推荐阅读顺序

1. `AgentController`：自然语言请求如何进入后端；
2. `AgentRuntime`：规划、选择 Skill、执行和记忆如何串联；
3. `IntentPlanner` 与 `SkillRegistry`：模型选择和 Java 白名单的边界；
4. `HighwayTrafficSkill`、`UnifiedTrafficQueryService` 与 `OdTrafficService`：18 种交通查询如何分流，新增 OD 如何按城市汇总；
5. `EmergencyWorkflowController` 与 `DispatchApplicationService`：三级状态机、版本返工、幂等和最终通告事务；
6. `MysqlHighwayTrafficSnapshotSource`、`InMemoryHighwayTrafficSnapshotCache` 与 Port：一致性读取、完整性校验和原子发布；
7. `EmergencyResourceAllocator`、`MysqlEmergencyResourceRepository`、`MysqlResourceAllocationRepository` 与 `FujianCityDistanceAdapter`：受限资源需求如何转成库存占用、跨市调度和缺口；
8. `AbnormalEventRepository`、`MysqlDispatchRepository`、`MysqlEmergencyWorkflowRepository` 与 `SpringUnitOfWork`：MySQL 持久化和事务边界；
9. `OpenAiCompatibleChatModelAdapter`：结构化输出和流式输出如何实现；
10. `SpeechController`、`SpeechApplicationService` 与 `PythonSpeechServiceAdapter`：Java 如何隔离语音容器故障；
11. `speech-service/app` 与前端 `speech` Store：识别、自然分段、预合成和播放取消；
12. [语音录音停止与识别问题修复日志](docs/VOICE_INPUT_RECORDING_BUGFIX_20260812.md)：历史故障、当前抽屉结构下的修复和回归验证；
13. [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md)：三人后续练习任务。

## 10. 可选开发验证（不是启动步骤）

以下命令供后续开发按改动范围选用，协作者首次启动无需全部执行，本次版本整理也未重跑这些测试或构建。不要把历史验收记录当作每次推送的测试结果。

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
3. 事件满足 `event_status=0 AND (del_flag IS NULL OR del_flag IN ('N', '0'))`；
4. 前端已在 `5173` 端口启动，浏览器页面处于可见状态；
5. 前后端终端中没有数据库连接或代理错误。

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
