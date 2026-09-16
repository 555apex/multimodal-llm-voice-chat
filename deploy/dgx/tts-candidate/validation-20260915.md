# Qwen3-TTS 1.7B validation — 2026-09-15

## 候选模型短周期门控（发布前）

- Runtime: vLLM-Omni 0.28.0, image manifest `sha256:6f8be103eaf0055448cf7578cfd621405fd669079d4361bd58896326b2bf722a`.
- Model: Qwen3-TTS 1.7B CustomVoice, Serena, explicit Chinese, 24 kHz mono PCM.
- Workload: 10 single-user requests and 10 dual-user pairs while Qwen3.6 completed 10 local business prompts.
- First audio P95: 0.223 seconds.
- Mean synthesis RTF: 0.743.
- Maximum synthesis RTF: 0.836; two dual-user requests exceeded the nominal 0.8 gate.
- Simulated starvation after a 0.7-second initial buffer: 0 ms.
- Stability: 30/30 TTS requests and 10/10 LLM requests completed without crash or timeout.

The user approved this near-threshold result for release. CosyVoice3 was not
continued after the Qwen selection. Raw JSON evidence is stored on DGX under
`/home/whtc/backups/roadagent/tts-20260915/validation/`.

Note: the pre-release numbers above were measured against the raw vLLM-Omni
candidate endpoint. They do not include the speech-service text normalization
and semantic segmentation that shipped with the release.

## 发布后验收（2026-09-15 晚，经公网中继 dgx-relay 执行）

发布范围为语音服务、内网 backend/frontend、公网 backend/frontend。
Qwen3.6 LLM、ASR、MySQL、公网代理均未重启（Qwen 容器 StartedAt 保持 2026-09-14T02:07Z）。

### 端到端流式播报（经语音服务真实接口 `/v1/tts/speech/stream`）

预热后 6 个业务样本（含 G205、K123+456、31.495 mm、80%、日期时间、Markdown、控制符）：

| 样本 | 首包 | RTF | 断流停顿 |
|---|---:|---:|---:|
| road-number | 1.245s | 0.426 | 0 ms |
| units | 1.228s | 0.426 | 0 ms |
| datetime | 1.237s | 0.425 | 0 ms |
| markdown | 1.215s | 0.431 | 0 ms |
| garbage | 1.210s | 0.419 | 0 ms |
| summary（150 字全文） | 1.243s | 0.415 | 0 ms |

- 首包 P95（预热后）≈ **1.24 秒**，远优于 3 秒门槛。
- RTF 均值 **0.42**，全部低于 0.8 门槛。
- 按 0.7 秒初始缓冲建模，断流停顿 **0 ms**。
- 冷启动首请求（容器重建后第一发）首包 19.8 秒，第二发起即收敛至 ~1.2 秒；属模型冷加载，非链路缺陷。

### 双用户并发（10 组，同时发起 2 路）

- RTF 均值 **0.404**，最大值 **0.422**，全部低于 0.8 门槛。
- 20 个并发请求无串音、无重复、无漏读、无崩溃。
- 明显优于发布前候选直连测得的 0.836/0.820，原因是通过语音服务后文本被清洗并按语义分段，
  单次合成片段更短更干净。

### 文本规范化与语义分段

- `G205`、`K123+456`、`31.495 mm`、`80%`、`2026年9月15日 09:30` 全部原样保留，未被截断。
- 控制字符（U+0007）、替换字符（U+FFFD）、方括号、竖线、花括号、波浪线均被清除或转为空格。
- Markdown 链接保留可读文案并丢弃 URL；代码块转为句号。
- 150 字长文本正确切分为 3 段，切分点落在标点处而非标识符中间。

### 修复：Markdown 标记漏读（本次发布内修复）

发布后验收发现强调符号在 CJK 语境下未被清除，会被朗读为“星号”：

| 输入 | 修复前 | 修复后 |
|---|---|---|
| `执行**应急预案**。` | `执行*应急预案*` | `执行应急预案。` |
| `__加粗__完成。` | `__加粗__完成。` | `加粗完成。` |
| `*斜体*完成。` | `斜体*完成。` | `斜体完成。` |
| `# 标题` | `# 标题` | `标题` |
| `> 引用内容。` | `> 引用内容。` | `引用内容。` |

根因：原单条正则使用 `(?<!\w)` 后视断言，而 Python 正则中 `\w` 匹配 CJK 字符，
导致 `**` 紧跟中文时断言失败。修复方式为拆分强/弱强调两条规则、补标题/分隔线/引用块规则，
并新增一条兜底清除 `* _ \` ~` 的语句。新增 4 项回归测试，`test_text_processing.py` 全部 7 项通过。

### 结论

Qwen3-TTS 1.7B + vLLM-Omni 正式作为生产语音引擎上线，发布后各项指标均达标或优于门槛。
回滚镜像保留：`road-agent-dgx-speech:tts-20260915`（含修复前代码）、
`road-agent-dgx-{backend,frontend,speech}:before-tts-20260915`（发布前生产版本）。

## 增量发布 r2（2026-09-15 晚）

Markdown 修复通过增量镜像 `road-agent-dgx-speech:tts-20260915-r2`
（`sha256:0f6f080d98ed51d5839aab49b5bc3d33cec22582649b72c2a604758652df489a`）上线，
仅重建 `speech-service` 容器，其余服务未动。

### 为什么用增量方式构建

`speech-service/Dockerfile` 的依赖层需要联网安装约 100 MB wheel，而本 DGX 到
PyPI 镜像源的实际速率仅 12–16 kB/s：`transformers` 12 MB 耗时 15 分 22 秒，
`onnxruntime` 21.3 MB 预计约 29 分钟，全量重建需数小时。

应用代码位于 `Dockerfile` 的**最后一个 COPY 层**，因此改用
`speech-service/Dockerfile.delta`——以已验证镜像 `tts-20260915` 为基底，只替换 `/app/app`：

```
ARG SPEECH_DELTA_BASE=road-agent-dgx-speech:tts-20260915
FROM ${SPEECH_DELTA_BASE}
COPY --chown=speech:speech app ./app
```

构建耗时 0.1 秒。该方式不仅更快，还使依赖集与已验证镜像**完全一致**，杜绝版本漂移。
**依赖需要变更时必须回到主 `Dockerfile` 全量构建。**

### 增量镜像校验

- `/app/app/text_processing.py` MD5 = `711c39183c5b48cecd57892cabc2f7df`，与修复版一致。
- 依赖版本与 `tts-20260915` 完全相同：fastapi 0.139.2、starlette 1.6.0、uvicorn 0.51.0、
  httpx 0.28.1、faster-whisper 1.2.1、qwen-tts 0.1.1。
- 镜像内直接调用 `normalize_speech_text`，5 个用例全部输出正确。

### 发布后验证

服务端日志给出清洗生效的直接证据：

```
TTS completed engine=vllm-omni raw_chars=41 clean_chars=12 replacements=2 elapsed_ms=1481
TTS stream completed engine=vllm-omni raw_chars=21 clean_chars=21 replacements=0 \
  first_chunk_ms=1228 elapsed_ms=2388 chunks=4
```

输入 `执行**应急预案**并查看[详情](https://example.com/a)。` 共 41 字符，
清洗后 12 字符（`执行应急预案并查看详情。`），替换计数 2。日志只记录长度与计数，
不落业务全文，符合计划要求。

实测 4 个易错样本：首包 1.202–1.244 秒，RTF 0.422–0.441，全部通过。
Qwen3.6 容器 StartedAt 保持 2026-09-14T02:07Z，未重启。

### 部署配置漂移修复

发现 `deploy/dgx/.env` 中 `ROADAGENT_SPEECH_IMAGE` 仍指向 `tts-20260915`（修复前），
与实际运行的 r2 不一致——若他人执行 `docker compose up -d speech-service`
会把 Markdown 修复回退。已将其更新为 `road-agent-dgx-speech:tts-20260915-r2`，
原 `.env` 备份于 `/home/whtc/backups/roadagent/tts-20260915/switch/env.before-r2`。

### 回滚方式

```
export ROADAGENT_SPEECH_IMAGE=road-agent-dgx-speech:tts-20260915
docker compose --env-file deploy/dgx/.env \
  --project-directory /home/whtc/workspace/projects/road-agent-dgx \
  -f deploy/dgx/compose.yaml up -d --no-deps --force-recreate speech-service
```

同时需把 `.env` 中该变量改回，避免下次普通 `up` 再次漂移。

## 链路行为验证（取消 / 槽位 / ASR）

### A. 客户端中途断连

对 `G205` 长文本发起流式合成，读到 204800 字节 SSE（约 3.4 秒处，中途）后主动断开连接。

- 断连后 **1 秒内** `ttsQueue` 回到 `active=0, waiting=0`，槽位正常释放。
- 无残留推理任务，未出现「旧音频恢复」现象。

服务端在 `main.py` 中对每个分块检查 `request.is_disconnected()`，断连即置取消标志并
`return`，同时 `finally` 释放信号量；队列在任务结束后对已取消请求抛 499，**不返回残缺音频**。

### B. 显式 `DELETE` 取消

带 `X-Speech-Request-Id` 发起合成，1.2 秒后调用 `DELETE /v1/tts/requests/{id}`。

- `DELETE` 返回 **204**。
- 原请求以 **HTTP 499** 在 1.23 秒终止（全量合成约需 4 秒）。
- 槽位随即释放，`ttsQueue` 归零。
- 未返回部分音频——即使引擎已产出部分 PCM，队列也会以 499 拒绝。

### C. ASR 状态（回环测试）

语音容器重建后 ASR 仍可用。用 TTS 合成音频再送入 ASR 转录，结果如下：

| 样本 | 朗读内容 | 识别结果 | 判定 |
|---|---|---|---|
| 纯中文 | 前方路口禁止左转，请按照指示通行。 | 前方路口禁止左转，请按照指示通行 | 完全正确 |
| 数值单位 | 降雨量三十一点四九五毫米，预警比例百分之八十。 | 降雨量31.495毫米，预警比例80% | 准确，中文数字正确还原为阿拉伯数字 |
| 字母编码 | G205国道发生交通事故。 | 即二零五过到，发生交通事故 | 字母数字编码识别失败 |

**ASR 功能正常，识别质量良好**，唯一短板是 `G205`、`K123+456` 这类字母＋数字的路网编码，
faster-whisper small 会将其转写为中文数字（`G205` → `即二零五`）。

这是小模型的既有能力边界，**非本次变更引入**：
本次只改动了 TTS 侧的文本清洗与服务链路，ASR 引擎、模型与配置均未触碰。
由于属于**输入侧**问题，且不在本计划的 TTS 范围内，未做改动，建议另行评估
（可选方向：在 ASR 输出后加一层面向路网编码的别名纠正，例如 `即二零五` → `G205`）。
