# DGX RoadAgent 修复发布记录（2026-09-09）

业务修复及 Qwen3.6 已部署到内外网。TTS 加速候选未通过双用户性能门槛，没有发布该候选镜像。按用户最新要求取消 15 分钟运行观察。

## 发布范围与审阅结论

- 基于部署基线 4b120ffb1b93718de4a6b88c29d96b9af94e6f23，分支 fix/dgx-latency-20260909；原始业务仓库未改动。
- 设施告警 ID 在 JSON、TypeScript 和提交链路统一为十进制字符串，保留数据库 BIGINT。操作错误与查询错误分离，失败保留列表和表单；防重复提交、过期响应、末页空页回退均保留。
- 恢复 Qwen3.6-35B-A3B NVFP4；FlashInfer、Marlin、MTP（3 个推测 token）、0.40 显存预算、2 路请求、65536 上下文；服务默认及业务请求均关闭 thinking。生产容器增加 72 GiB 内存上限，禁止同时运行两套 LLM。
- 应急模型只补充稳定编号字段和资源建议，由程序填充并校验版本化预案；普通解释预算 512 tokens，方案预算 1536 tokens；截断与结构不合法不能发布。内外网模型超时统一 180 秒、一次格式修复、陈旧任务与代理上限 600 秒。
- 保留领取时版本及生成状态的成功、失败落库校验。前端生成期间独立轮询，显示等待时长；断开后读取最终状态，避免重复生成。
- 常用交通查询先显示数据库结果及摘要，再补充经事实校验的增量解读。趋势查询保留原结构化校验路径。数字人持续基础呼吸，将设施和应急处理状态接入动画信号，减少高频页面持久化更新。
- 默认自动播报完整句摘要，保留手动全文朗读。新增 PCM/AudioWorklet 和流式接口为能力协商后的可选路径；生产语音服务仍为原 MP3 实现，ttsStreamingAvailable=false。

## 实测与适用范围

| 检查 | 结果 |
| --- | --- |
| 应急完整生成 P95，20 次单人 | 10.61 秒 |
| 应急完整生成 P95，20 次双人 | 15.64 秒 |
| 常用问答首批有效业务结果 P95，20 次单人 | 1.47 秒 |
| 常用问答首批有效业务结果 P95，20 次双人 | 2.15 秒 |
| 40 次业务查询增量解读 | 全部正常完成，无补充解读失败提示 |
| 真实部署页面操作最大帧间隔 | 104.7 毫秒，无页面脚本错误 |
| 模拟错误、提交与长生成 UI 回归最大帧间隔 | 64.3 毫秒，扫描位置持续变化 |
| 前端单测 / 生产构建 | 110 项通过 / 通过 |
| Maven verify | 全模块通过；默认禁用的外部集成测试保持原条件，真实写库验收另用隔离库 |
| 语音服务单测 | 10 项通过 |
| 真实内网及公网后端 | 六类查询、设施三视图、应急待办、本地 TTS→音频→ASR 回识别均通过 |

写库验收全部使用 road_agent_fix_20260909。包含两个会在 JavaScript 数值解析后碰撞的告警 ID、连续确认与关闭、三级审批、二级及省级退回重生成、事件类型更正、分类重试、无需调度、资源归还、双后端竞争领取及客户端断开后成功结果。旧验收脚本已针对重复基准测试产生的多版本计划和已有工作流修正样本选取；没有放松当前版本与库存断言。

应急基准镜像与最终发布镜像的 304 个非交通类字节码一致；最后修改仅涉及 7 个交通类，已在最终镜像重新完成 40 次业务查询及全工作流验收。镜像对照见 emergency-image-equivalence.json。

性能样本来自本机预热后的隔离业务测试，不代表所有事件文本和混合语音负载的保证。实际完整生成均远低于 60 秒；本轮未通过人为延迟在生产构造超过 60 秒的成功请求。浏览器录制使用本机 Edge，经 SSH 访问真实 DGX 页面；模拟错误和 PCM 控制测试单独标注，不能当作生产流式语音性能。

## 未达标的语音候选

faster-qwen3-tts 0.3.2、Torch CUDA Graph、两独立进程、Serena；尝试过分块调优、独立 MPS 及融合解码原型。单用户无 MPS 实测首块约 1.1 秒、RTF 约 0.57；双用户短摘要 RTF 约 1.13。MPS/融合原型双用户短摘要 RTF 仍约 1.03–1.04，部分样本缓冲耗尽约 0.32 秒，未满足 RTF≤0.8 和停顿≤300毫秒的联合门槛。

因此生产保留原 TTS 镜像、Qwen3-TTS 0.6B CustomVoice 和 Serena；ASR 保留 faster-whisper small CPU/int8。**本轮没有达成“摘要显示后 P95≤5 秒出声、双用户连续播报”的验收目标。**候选实现仅以未批准的可选代码保留，未以其他硬件成绩替代本机验收。相关试验记录见 speech-benchmark*.json；流式语音音质未完成正式听测。

## 上线核验与运行记录

- 发布核验时间：2026/9/9 13:53:22（北京时间）；后端镜像 sha256:038fac0e00215d9ae0c37ccb4bf4a164626ea1039f9ec1209e424e87c66d9a07。
- 15 份原数字人素材逐文件 SHA-256 一致；权重只读挂载、离线环境、Serena、ASR CPU/int8、内外网模型名称与超时已核验。
- 公网继续 https://spark-8a8d.taile1b178.ts.net/ → Funnel HTTPS → 127.0.0.1:18081；登录文件摘要不变，未登录请求返回预期 401。
- 按用户要求停止观察；保留 5 次健康快照，最后快照距开放约 206 秒。没有将其标为已完成 15 分钟验收。
- 准备期间曾因两套 LLM 共驻导致主机内存耗尽并重启；随后恢复原服务，改为单模型顺序切换并加内存上限。最终生产只运行 Qwen3.6 一套 LLM。
- 本轮未重置或初始化生产数据；最终库存守恒检查通过。isolated QA 容器及加速语音候选已停止。

## 备份与回滚

DGX 备份目录：/home/whtc/workspace/projects/road-agent-dgx/.releases/fix-20260909/baseline/

包含修改前源码 source.tar.gz、模型服务 model-serving.tar.gz、容器检查信息与原镜像标签、runtime.env、登录与数据库权限配置、full.sql、维护期 maintenance-full.sql、维护期表计数与 SHA-256；初始备份已在隔离库恢复并核对 140 张表。含凭据文件只保留 DGX，未提交仓库。

回滚前先停止入口，再恢复本次涉及的配置和服务；**不恢复数据库、不清除上线后产生的有效业务数据**：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
python3 .releases/fix-20260909/source/deploy/dgx/fix_release.py rollback
# 待原模型和两个后端健康后核验：
curl --fail http://127.0.0.1:8001/v1/models
docker exec road-agent-dgx-backend curl --fail http://127.0.0.1:8080/api/v1/speech/capabilities
docker exec road-agent-dgx-public-backend curl --fail http://127.0.0.1:8080/api/v1/speech/capabilities
docker compose --env-file deploy/dgx/.env --project-directory "$PWD" -f deploy/dgx/compose.yaml -f deploy/dgx/compose.public.yaml --profile public up -d --no-deps --no-build --force-recreate frontend public-frontend
```

禁止使用旧 release_ops.py 的 reset_flows/cutover 初始化流程回滚本次发布。回滚脚本已作结构审阅，未实际回滚已通过验收的最终生产版本。

浏览器录制与截图保存在本机 audit/fix-20260909/browser-live-trace.zip 和 live-*.png；各项原始 JSON 结果随本文归档。
