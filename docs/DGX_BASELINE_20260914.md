# DGX `roadagent-v2` 部署源码基线（2026-09-14）

本文件记录从 DGX 当前工作目录整理到 GitHub `roadagent-v2` 分支的源码基线，便于后续开发和部署追溯。该提交只保存脱敏源码、测试、配置示例和部署模板，不包含运行时秘密或可重新生成的产物。

## 基线范围

- 后端部署基线：`road-agent-dgx-backend:business-20260913`。
- 内网前端已验证镜像：`road-agent-dgx-frontend:business-0914`。
- 大模型：DGX 本地 Qwen3.6 兼容 OpenAI 接口服务，具体非秘密模型参数见 [`../configs/models.yaml`](../configs/models.yaml)。
- 语音：faster-whisper small（CPU/int8）与 Qwen3-TTS；模型目录通过部署环境只读挂载，不进入 Git。
- 本次新增验证修复：前端不再收到 `answer.speech` 时提前朗读摘要，而是在 `run.completed` 后自动朗读完整 `message.content`；对应多段文本回归测试已纳入。

## 本次源码变化概览

- 补充交通问题的查询范围识别和确定性趋势表达，并同步前端结果字段与展示测试。
- 调整语音文本规范化、分段与播放衔接，保留完整回答自动朗读行为。
- 强化一级应急方案的资源可行性约束及其单元、集成测试。

## 明确排除项

- `deploy/dgx/.env`、数据库地址/账号/密码、API Key、Basic Auth 凭据及其他本地环境文件；
- `/home/whtc/models` 下的模型权重、模型缓存和编译缓存；
- 日志、临时音频、`.releases/`、`target/`、`frontend/node_modules/`、`frontend/dist/` 等运行或构建产物；
- 容器、Docker 卷、数据库数据以及 SakuraFrp 的运行状态。

协作者应复制仓库中的 `.env.example` 文件并在本地填写负责人单独提供的数据库信息；真实配置不得提交到 Git。部署命令和环境变量模板见 [`../deploy/dgx/README.md`](../deploy/dgx/README.md) 与 [`../deploy/dgx/.env.example`](../deploy/dgx/.env.example)。

## 已有验证

DGX 当前源码的前端验证结果为 26 个测试文件、137 项测试全部通过，`vue-tsc` 和 Vite production build 通过。本次基线整理未操作或重启任何容器、数据库、模型服务或 SakuraFrp，也未重复执行 Docker 验收。
