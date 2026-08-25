# DGX Spark deployment

目标主机：`spark-8a8d` / ARM64 / NVIDIA GB10
远端目录：`/home/whtc/workspace/projects/road-agent-dgx`

## 1. 从 Windows 上传

在迁移副本根目录执行：

```powershell
.\deploy\dgx\deploy.ps1
```

脚本会排除 Git、缓存、构建产物和本地密钥，把项目上传到固定远端目录；随后从原项目的 `config/api-test.ps1` 读取现有 MySQL/高德配置，通过 SSH 标准输入生成权限为 `600` 的远端 `.env`。密钥不会进入归档或终端输出。

## 2. DGX 预检和模型下载

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack preflight
deploy/dgx/dgx-stack download-models
```

固定模型：

- `Systran/faster-whisper-small@536b0662742c02347bc0e980a01041f333bce120`
- `Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice@f3d1af06e4eaefac12b1ffa6726f9eef674a6f02`

下载脚本为每个文件计算 SHA-256，并生成 `.deployment-manifest.json`。运行容器只读挂载这些目录并启用 Hugging Face/Transformers 离线模式。

## 3. 构建与启动

```bash
deploy/dgx/dgx-stack build
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
```

`up` 会保证 `dgx-ai` 网络存在、切换现有 model-serving 到 `daily` Qwen profile、把 Qwen 附加到共享网络，然后启动 Backend、Speech 和 Frontend。

DGX 当前直连 Docker Hub 会超时，Java、Node、Nginx 和测试 Python 基础镜像通过 `dockerproxy.net` 代理拉取；Speech GPU 运行镜像仍固定为 NGC digest。

## 4. 验证

```bash
deploy/dgx/dgx-stack smoke
deploy/dgx/dgx-stack smoke --model-iterations 20 --with-tts --with-traffic --with-agent
```

生产稳定性验收使用：

```bash
deploy/dgx/dgx-stack smoke --model-iterations 100 --with-tts --with-traffic --with-agent
deploy/dgx/dgx-stack smoke --business-structured-iterations 20
deploy/dgx/dgx-stack smoke --tts-concurrency 2 --test-speech-limits
```

浏览器录音的 ASR 验收在 HTTPS 页面中完成，固定语句为“福州五四路现在拥堵吗”。

## 5. Tailscale HTTPS

```bash
deploy/dgx/dgx-stack serve
```

该命令只在 tailnet 内将 HTTPS 443 反向代理到 `127.0.0.1:18080`。如果 tailnet 尚未启用 HTTPS，按照 Tailscale 返回的授权链接完成一次管理员确认后重试。

## 6. 日志与回滚

```bash
deploy/dgx/dgx-stack logs
deploy/dgx/dgx-stack logs speech-service
deploy/dgx/dgx-stack down
deploy/dgx/dgx-stack serve-off
```

`down` 不停止现有 Qwen/Open WebUI，不删除模型，不修改共享 MySQL。
