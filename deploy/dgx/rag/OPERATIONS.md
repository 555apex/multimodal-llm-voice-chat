# DGX RoadAgent RAG 使用与运维手册

适用环境：2026-09-23 上线的 DGX `roadagent-v2` + WeKnora 0.6.3（提交 `7ddac0385fc7ec78b30da0a6f9bc13da7d672517`）。本文面向知识库管理员、技术人员和 RoadAgent 前端用户。命令在 DGX 上以 `whtc` 用户执行，除非特别注明。

## 1. 先分清三个界面与模型

| 用途 | 实际入口或组件 | 影响范围 |
| --- | --- | --- |
| WeKnora 管理界面 | DGX `127.0.0.1:18082`，必须经 SSH 隧道访问 | 管理 WeKnora 模型、知识库、文档及其自身的聊天功能 |
| RoadAgent 前端 | 现有内部/公网入口 | 用户提问；知识问答调用 WeKnora 检索，显示答案和来源 |
| 模型服务 | `rag-model:8001`（Docker 私有网络）、现有 Qwen 服务 | BGE 负责 embedding/rerank；RoadAgent 的最终回答仍由其独立配置的 Qwen 生成 |

当前 WeKnora 的 BGE 模型是 `bge-m3-ft-final`（embedding，1024 维）和 `bge-reranker-v2-m3-ft-final`。BGE 服务不提供聊天接口。RoadAgent 后端只调用 WeKnora 的 hybrid-search；**在 WeKnora 中切换聊天模型，不会自动切换 RoadAgent 的回答模型**。RoadAgent 的模型端点/名称由 DGX 生产环境文件中的 `ROADAGENT_MODEL_ENDPOINT`、`ROADAGENT_MODEL_NAME` 管理。

WeKnora app 当前只接入 `roadagent-rag` Docker 网络，不能直接通过服务名访问 `dgx-ai` 网络上的 Qwen。若要让 WeKnora 自身的聊天功能使用 DGX 本地 Qwen，需先由技术人员设计并验证网络接入；仅在 UI 中填写 `http://qwen:8000/v1` 不足以保证连通。

## 2. 打开 WeKnora 管理界面

在**自己的 Windows 电脑**打开 PowerShell，确保电脑与 DGX 在同一 Tailscale 网络，执行：

```powershell
ssh -N -L 18082:127.0.0.1:18082 whtc@100.119.145.78
```

如果需要指定已授权的 DGX 私钥，加上 `-i "<你的 DGX 私钥绝对路径>"`。保持此窗口运行，然后在**这台 Windows 电脑**的浏览器打开 `http://127.0.0.1:18082/`，用已有 WeKnora 账号登录。部署已关闭新用户自行注册；没有账号或权限时联系管理员，不要在项目文档中记录密码。

若本机 18082 被占用，只改隧道左侧端口，例如 `ssh -N -L 18084:127.0.0.1:18082 whtc@100.119.145.78`，浏览器改开 `http://127.0.0.1:18084/`。**不要**访问 `http://100.119.145.78:18082/`，也不要为 WeKnora 开 Tailscale Funnel/公网端口；服务有意只绑定 DGX 回环地址。SSH 失败时先确认 `tailscale ping 100.119.145.78`、DGX SSH 授权以及本机端口占用。

DGX 上的只读状态检查：

```bash
docker ps --format '{{.Names}}  {{.Status}}  {{.Ports}}' | grep -E 'weknora|rag-model|road-agent-dgx-(backend|public-backend)'
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18082/
docker exec roadagent-rag-candidate-weknora-app-1 curl -fsS http://127.0.0.1:8080/health
```

界面地址返回 200、app 健康检查通过时，再排查登录或浏览器问题。容器名保留了迁移时的 `candidate` 前缀，但它是当前 RoadAgent 生产后端正在使用的 WeKnora 栈；不要误当成可随意删除的测试环境。

## 3. 技术人员：调试模型配置

1. 登录 WeKnora 后，进入 **设置 → 模型管理**。该版本按“对话、Embedding、Rerank”等类型筛选；查看模型至少需要相应空间的查看权限，新增/编辑/删除模型需要管理员权限。
2. 对 Embedding/Rerank，在“远程 API”模型编辑抽屉核对模型名称、供应商/API 类型、Base URL 和连通性。当前 BGE 的 Base URL 是 Docker 内部的 `http://rag-model:8001/v1`；模型 ID 分别为 `bge-m3-ft-final`、`bge-reranker-v2-m3-ft-final`，embedding 维度为 **1024**。在抽屉使用“测试/检查 API”功能；不要把浏览器的 `127.0.0.1`、DGX 的 `8001` 公网地址或 Qwen 的 `127.0.0.1:8001` 填成 BGE 地址。模型 API Key 属于受保护凭据，不要粘到工单、截图或 Git。
3. 要检查某个知识库实际绑定的模型，进入 **知识库 → 选择知识库 → 设置 → 模型配置**。这里的对话模型主要用于 WeKnora 的知识库摘要/自身聊天；Embedding 用于该库的检索索引。已有文档且启用 RAG 的知识库，界面会锁定 Embedding 选择，避免旧向量与新模型混用。
4. 更换 Embedding 时先备份数据库和原始文档，在新知识库验证模型维度、重新解析/建立全量索引和召回质量，再迁移 RoadAgent 指向的知识库 ID。不要直接改数据库中的模型 ID，不能只改模型名称或维度却保留旧向量。更改 Rerank 模型后也要用代表性问题比较检索结果与时延。
5. 修改 WeKnora 的“对话模型”只影响 WeKnora 自身使用该模型的流程。若要修改 RoadAgent 最终回答模型，需走 RoadAgent 独立的模型配置、候选验证和回退流程；不要仅改 WeKnora UI 就预期 RoadAgent 答案变化。

排错顺序：先确认 `rag-model`、`weknora-app` 容器健康；再在 WeKnora 模型管理中测试连接；再确认知识库模型绑定、文档解析状态；最后用 RoadAgent 问答验证端到端结果。查看日志时不要复制完整环境变量或 API Key：

```bash
docker logs --tail 100 roadagent-rag-candidate-rag-model-1
docker logs --tail 100 roadagent-rag-candidate-weknora-app-1
docker logs --tail 100 road-agent-dgx-backend
```

## 4. 技术人员：增删知识库文档

### 维护现有知识库

在 WeKnora 的“知识库”列表选择目标库，进入文档页上传 PDF/DOC/DOCX；按页面提示确认解析参数，等待状态成为“完成”，再抽查分块内容并用相关问题测试召回。上传失败时查看单文件错误、解析日志和模型服务健康。对已入库文件，可在文档操作菜单选择重新解析；删除前确认不是仍需引用的法规/标准，删除后复测问答来源。

当前 RoadAgent 检索以下两个业务知识库：

| 知识库 | ID |
| --- | --- |
| 公路业务标准规范 | `f9ba113d-bd68-4443-9d9e-dc29beca9c99` |
| 公路政策法规 | `8f1a0973-5eef-4f7a-9f86-cdabd3abcf26` |

向这两个库增删文档，**无需改 RoadAgent 的知识库 ID 配置**；完成索引后立即通过检索可见。迁移保留了 57 份原始文档，但源库实际仅索引了 20 份，二者不是“迁移丢了 37 份”：其余文件是重建索引的兜底，不应未经筛选整批导入。当前 docreader 支持这批 PDF/DOC/DOCX；此部署未安装可选的 WebKit 浏览器，不应将网页抓取能力视为已验收。

### 新建、停用或删除知识库

新建库时选择与现有索引兼容的 Embedding，上传少量样本文档，确认解析完成和检索效果。**新库不会仅因在 WeKnora 中创建就自动出现在 RoadAgent 中。**需要技术人员同时完成以下变更：

1. 为 RoadAgent 的受限 WeKnora API Key 增加新库的检索权限，或轮换为只授权所需知识库、只允许检索的新 Key。
2. 在 DGX `/home/whtc/workspace/projects/road-agent-dgx/deploy/dgx/.env`（权限 `0600`）中，将新库 ID 加入逗号分隔的 `ROADAGENT_RAG_KB_IDS`；若轮换 Key，也同步更新 `ROADAGENT_RAG_API_KEY`。不要将该文件提交 Git。
3. 只重建内部和公网 RoadAgent 后端，使环境变量生效；随后分别验收问答和来源。不要重启 Qwen、TTS 或删除 RAG 数据卷。命令在 DGX 上执行：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
docker compose --project-directory "$PWD" --env-file deploy/dgx/.env \
  -f deploy/dgx/compose.yaml -f deploy/dgx/compose.public.yaml \
  --profile public up -d --no-deps backend public-backend
```

若要停用/删除已接入 RoadAgent 的库，先从 `ROADAGENT_RAG_KB_IDS` 和 API Key 授权范围移除、重启并验收后端，再在 WeKnora 中删除；否则跨库检索可能因不存在或无权限的 ID 整体报错。删除库前先生成可恢复备份，并确认文档和知识库 ID 的影响范围。

## 5. 普通用户：在 RoadAgent 前端使用 RAG

打开**现有 RoadAgent 页面**（公网入口仍要求原有认证），在聊天输入框直接提知识类问题，例如：

> 根据《公路养护工程管理办法》，养护工程设计有哪些要求？

系统识别为“知识问答”后，在两个已接入的业务知识库中检索片段，再由 RoadAgent 的 Qwen 根据片段生成答案。答案下方会出现“参考来源”面板，展示命中片段数量和去重后的文件名称；例如验收时出现《公路养护工程管理办法》.pdf。来源名称用于核对依据，**目前不是可点击的原文预览链接**；需要核对原文时请知识库管理员在 WeKnora 中查看文档。

再如：

> 《中华人民共和国突发事件应对法》对预警有哪些规定？请指出依据来自哪个文件。

若检索没有足够依据，应缩小问题、注明法规名称或联系管理员补充文档；不要把无来源的回答当成法规条文。交通实时查询或应急调度问题走其他技能，通常不会出现 RAG 来源面板。普通 RoadAgent 前端**没有上传/删除知识库文档入口**，这些操作在 WeKnora 管理界面完成。

前端的技术链路为 `KNOWLEDGE_QA → WeKnora hybrid-search → Qwen 根据检索片段回答 → SSE result.rag → 参考来源面板`。`result.rag` 只给前端 `hitCount` 和来源名称，不应包含内部知识库 ID、API Key 或磁盘路径。

## 6. 验收与故障定位

在 DGX 上可用一个无凭据的内部入口做 SSE 冒烟测试（仅测试，不会新增知识库文档）：

```bash
curl -sS -N -H 'Accept: text/event-stream' \
  -H 'Content-Type: application/json' \
  -d '{"message":"公路养护工程设计应注意什么？"}' \
  http://127.0.0.1:18080/api/v1/conversations/rag-ops-check/messages/stream
```

预期出现 `intent.recognized` 的 `KNOWLEDGE_QA`、`result.rag`（含 `hitCount`、`sources`）和 `run.completed`。公网入口要通过原有认证访问；未认证访问返回 401 是预期结果。若出现 `run.failed`，记录其错误码和 `traceId`，不要记录密钥。特别检查 WeKnora app、BGE 服务、PostgreSQL、Qwen、TTS 是否健康；新库报 HTTP 错误时先核对库 ID 与 API Key 授权范围。

已保留的恢复资料位于 `/home/whtc/workspace/projects/road-agent-rag/restore-points/`；生产切换前的环境文件备份在其 `production-pre-rag-20260923/` 子目录，权限 `0600`。恢复前先确认目标和当前数据变化，勿直接覆盖正在使用的数据库卷。WeKnora UI、模型密钥和 RoadAgent 环境文件均不应公开或提交版本库。
