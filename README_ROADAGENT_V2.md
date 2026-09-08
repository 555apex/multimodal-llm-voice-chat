# Road Agent v2 更新与使用说明

分支：`roadagent-v2` · 发布日期：2026-09-09。

本版本直接取自 DGX 当前部署源码，不是本机旧副本。应用来源为 `/home/whtc/workspace/projects/road-agent-dgx`，模型运维源码来源为 `/home/whtc/workspace/projects/model-serving`；业务合并基准为 `61e64d4`。发布到 GitHub 不会重建容器或改变正在运行的服务。

## 本版包含什么

| 范围 | 本版内容 |
| --- | --- |
| 交通查询 | MySQL 作为交通事实源；交通态势、通行能力、跨区域联系、车型特征及 OD 联系倾向 |
| 设施预警 | 设施异常清单、健康报告、重点关注和处置入口 |
| 应急工作流 | 事件自动分类及更正、版本化预案、三级上报/复核/决策、资源分配与归还、过程留痕 |
| 本地大模型 | Qwen3.8-27B-FP8，非思考模式、无外部 API Key、Java HTTP/1.1；保留 Qwen3.6 回滚配置 |
| 本地语音 | faster-whisper small / CPU / int8；Qwen3-TTS 0.6B / Serena / GPU BF16；离线加载及 MP3 输出 |
| 数字人 | “路智通”三姿态、六状态、音频振幅驱动嘴型、连续姿态过渡；主页面与独立演示页共用组件 |
| 稳定性修复 | 前端旧响应竞态、跨块 CRLF SSE 解析、提前断流提示、旧调度生成结果保护 |
| 运维 | ARM64 容器、现有公网鉴权配置模板、模型校验/候选验证/切换/回滚、发布保护和验收脚本 |

OD 是基于路线与卡口流量的**相对联系倾向**，不代表真实单车起终点、行驶方向或净流入流出。模型切换仅在服务端完成，前端没有模型切换控件。

## 拉取此版本

```bash
git clone --branch roadagent-v2 --single-branch https://github.com/555apex/multimodal-llm-voice-chat.git
cd multimodal-llm-voice-chat
```

源码内 `model-serving/` 是独立模型服务的发布副本。现有 DGX 上它的实际运行目录仍为 `/home/whtc/workspace/projects/model-serving`，不要直接把嵌套目录当成已配置好的生产模型服务启动。

## 已配置 DGX 的日常使用

以下命令用于现有部署，不是新服务器的一键初始化流程：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack preflight
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
```

DGX 浏览器访问 `http://127.0.0.1:18080`；数字人演示页为 `http://127.0.0.1:18080/digital-human-demo.html`。

Windows 远程查看：

```powershell
ssh -i "$env:USERPROFILE\.ssh\id_ed25519_dgx_spark" -N -L 18080:127.0.0.1:18080 whtc@100.119.145.78
```

保持该终端打开，然后在本机浏览器访问 `http://127.0.0.1:18080`。按 `Ctrl+C` 只关闭隧道，不会关闭 DGX 服务。

关闭应用和模型：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack down
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack stop
```

## 后续如何切换模型

无需修改 Java 或前端。模型定义维护在 `model-serving/configs/models.yaml`；当前运行配置分别保存在 DGX 模型服务 `.env` 和 Road Agent `deploy/dgx/.env`。两者的模型名称必须一致。

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack switch qwen38
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack model-reload --model qwen3.8-27b-fp8
deploy/dgx/dgx-stack smoke
```

回滚时分别改用 `switch qwen36` 和 `model-reload --model qwen3.6-35b-a3b-nvfp4`。候选验证、权重下载、配置位置和注意事项见 [完整切换指南](Qwen3.8_DGX部署与模型切换指南.md)。

## 发布检查

- 发布前逐文件对照 DGX 导出快照；应用及模型运维源码均来自服务器。仅调整发布文档、忽略规则及脚本换行/执行权限，不改业务逻辑。
- 2026-09-09 在本次导出的前端源码上重新运行：21个测试文件、101项测试全部通过，TypeScript 与 Vite 双入口构建通过。
- Qwen3.8 上线记录：100次顺序结构化请求、四类业务各20轮、Agent/交通/本地语音验收，以及1800秒稳定性检查通过。
- Java/Speech 沿用9月8日同版本源码的验收记录；Qwen 切换检查中 Java 176项通过、17项依赖外部服务的集成测试跳过，Speech 6项通过。不将历史 Qwen3.6 集成测试视为 Qwen3.8 重测结果。
- 详情见 [Qwen3.8 验收记录](docs/QWEN38_DGX_ACCEPTANCE.md)、[业务合并记录](docs/DGX_MERGE_20260908.md) 和 [本次发布来源](docs/ROADAGENT_V2_RELEASE.md)。

## 不包含的内容与部署边界

真实 `.env`、数据库/公网登录密码、私钥、模型权重、临时音频、数据库备份、容器镜像、`.releases/` 运行记录和构建缓存不上传。只提供配置示例；不要把真实凭据填入 `.env.example`。

新机器还需要单独准备模型文件、数据库权限、私密环境配置和相应发布条件。`release_ops.py` 是现有 DGX 的固定发布保护工具，部分操作依赖服务器 `.releases/merge-20260908-61e64d4`；不能在新机器上盲目执行其备份、切换或回滚命令。不要对现有共享数据库重复执行初始化、重置或 Demo 数据脚本。

原 README 的 DeepSeek/Edge-TTS 内容及根目录 `configs/models.yaml` 是保留的历史业务说明；本版实际模型以本页、Qwen3.8 指南及服务器运行 `.env` 为准。
