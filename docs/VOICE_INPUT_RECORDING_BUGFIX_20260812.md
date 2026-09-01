# 语音录音无法停止与无法识别问题修复日志

## 基本信息

- 日期：2026-08-12
- 分支：`version/roadagent-v1`
- 影响范围：Vue 前端语音输入组件
- 结论：已修复并通过自动测试和生产构建

> 本文首先记录 2026-08-12 原页面的故障现场。当前版本已将对话区从 `App.vue` 迁移到 `AgentDrawer.vue`，但仍保留同一条回归规则：录音开始后，即使父组件状态变化，停止和取消操作也必须始终可用。

## 问题表现

语音服务已经成功启动，并且健康检查返回：

```json
{
  "status": "UP",
  "asrAvailable": true,
  "ttsAvailable": true,
  "asrModel": "small",
  "ttsVoice": "zh-CN-XiaoxiaoNeural"
}
```

浏览器也已获得麦克风权限，麦克风可以在微信等其他应用中正常使用。但在项目页面中开始录音后出现以下现象：

1. 录音计时正常增长；
2. 红色方形按钮无法点击；
3. 只能点击“取消”；
4. 没有进入“识别中”状态；
5. 识别文本没有插入输入框。

## 复现步骤

1. 启动 Docker 语音服务、Java 后端和 Vite 前端；
2. 确认 `GET /api/v1/speech/capabilities` 返回 ASR 可用；
3. 在浏览器中允许页面访问麦克风；
4. 点击麦克风按钮开始录音；
5. 说出任意一句话；
6. 尝试点击红色方形按钮结束录音。

修复前，步骤 6 的按钮处于禁用状态，因此录音不会停止，也不会向后端提交音频。

## 排查证据

排查期间确认：

- Docker 容器状态为 `healthy`；
- `8091` 端口正常监听；
- faster-whisper 模型已经就绪；
- TTS 请求能够成功完成；
- 语音服务日志中没有出现 `POST /v1/asr/transcriptions`。

这说明故障发生在浏览器录音结束和上传之前，而不是麦克风硬件、Docker、Whisper 模型或 Java/Python ASR 接口内部。

## 根因

`VoiceInputButton` 开始录音后会触发：

```text
recordingChanged(true)
```

父组件 `App.vue` 收到事件后将 `recording` 设置为 `true`。修复前，父组件又通过下面的表达式把同一个录音控件禁用：

```vue
<VoiceInputButton :disabled="running || recording" />
```

因此实际状态变化为：

```text
点击麦克风
→ MediaRecorder 开始录音
→ 子组件发出 recordingChanged(true)
→ 父组件 recording 变为 true
→ 父组件把录音按钮 disabled
→ 用户无法点击该按钮执行 stopRecording()
→ MediaRecorder 不触发 onstop
→ completeRecording() 不执行
→ 音频不上传到 ASR
```

“取消”按钮是录音按钮之外的独立控件，因此仍然可以点击，这与现场表现一致。

## 修复方案

### 1. 原页面不再因正在录音而禁用录音控件

文件：`frontend/src/App.vue`

```diff
- :disabled="running || recording"
+ :disabled="running"
```

`recording` 状态仍用于禁用消息发送按钮，避免录音期间发送输入内容；它不再禁用负责停止录音的控件本身。

当前版本中父组件已经迁移到 `frontend/src/components/AgentDrawer.vue`。父组件仍会用 `recording` 禁止发送消息，子组件则只在空闲状态应用外部 `disabled`，因此不会再次形成状态回路。

### 2. 子组件只在空闲状态应用外部禁用条件

文件：`frontend/src/components/VoiceInputButton.vue`

```diff
- :disabled="disabled || (!available && status === 'idle')"
+ :disabled="status === 'idle' && (disabled || !available)"
```

进入 `recording` 或 `transcribing` 状态后，停止录音和取消识别操作必须始终可用，不能因为父组件状态更新而失去控制。

### 3. 明确显示停止操作

修复前红色按钮只显示方形图标 `■`，用户不容易确认它的用途。当前版本录音期间提供独立操作：

```text
停止并识别    取消
```

相关文件：

- `frontend/src/components/VoiceInputButton.vue`
- `frontend/src/styles.css`

关闭 AI 抽屉或切换到“应急处置”时，`AgentDrawer` 会把语音表面标记为非活动状态；正在进行的录音、识别或朗读会被取消，避免隐藏界面继续占用麦克风或扬声器。

### 4. 增加回归测试

文件：`frontend/src/components/VoiceInputButton.test.ts`

新增测试覆盖以下场景：

1. 开始录音；
2. 模拟父组件将 `disabled` 更新为 `true`；
3. 确认停止按钮仍未被禁用；
4. 点击停止；
5. 确认录音数据提交给 `transcribeSpeech()`。

测试名称：

```text
keeps the stop control enabled while recording even if the parent becomes disabled
```

## 修改文件

本次修复只涉及以下文件：

```text
frontend/src/App.vue
frontend/src/components/VoiceInputButton.vue
frontend/src/components/VoiceInputButton.test.ts
frontend/src/styles.css
docs/VOICE_INPUT_RECORDING_BUGFIX_20260812.md
```

本地工作区中的 `mvnw.cmd` 换行符变化与本问题无关，不应混入本次修复提交。

## 验证结果

执行：

```powershell
cd frontend
npm.cmd test -- --run
npm.cmd run build
```

结果：

```text
Test Files  12 passed (12)
Tests       30 passed (30)
TypeScript  passed
Vite build  passed
```

人工复测结果：

1. 点击麦克风后录音正常开始；
2. 红色按钮显示“停止”且可以点击；
3. 点击后页面进入“识别中”；
4. 浏览器向 Java 后端提交 multipart 音频；
5. Java 后端调用语音容器的 `/v1/asr/transcriptions`；
6. 识别结果插入当前输入框，但不自动发送；
7. “取消”仍会放弃当前录音且不会调用 ASR。

## Contributor 验收建议

拉取修复后执行：

```powershell
cd frontend
npm.cmd ci
npm.cmd test -- --run
npm.cmd run dev
```

浏览器访问 `http://localhost:5173`，然后验证：

1. 打开“路智通 AI 助手”，点击麦克风并说“福建省目前整体交通态势如何”；
2. 点击“停止并识别”；
3. 确认显示“识别中”；
4. 确认识别文本插入输入框；
5. 确认不会自动发送问题；
6. 查看语音容器日志，确认出现：

```text
POST /v1/asr/transcriptions HTTP/1.1
```

日志命令：

```powershell
docker compose -f compose.speech.yml logs --tail 50 speech-service
```

如果页面仍显示旧按钮，使用 `Ctrl+F5` 强制刷新，或重启 Vite 开发服务器。

## 安全说明

本日志不包含模型 API Key、数据库密码或其他本地凭据。当前交通数据来自 MySQL，不再需要高德 API Key。提交前仍应通过 `git diff --cached` 检查暂存内容，避免误提交本地环境文件。
