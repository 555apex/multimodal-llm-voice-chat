# Road Agent DGX 公网使用文档

## 1. 数据库使用方式

公网环境直接连接 Road Agent 原数据库，不再使用独立公网测试数据库。公网 Backend 使用自动生成的专用数据库账号，而不是原 `.env` 中权限过大的管理账号。

权限和写入边界如下：

- 7 张交通表只授予 `SELECT`；现有交通适配器同时使用只读事务，不具备新增、修改或删除交通数据的数据库权限。
- 8 张异常事件和应急业务表授予 `SELECT、INSERT、UPDATE、DELETE`，不授予建库、建表、授权或其他管理权限。
- 公网用户只能通过 Road Agent 已有业务接口操作，不能执行任意 SQL；MySQL 端口不向宿主机或公网发布。

公网环境支持现有全部业务能力：

- 交通态势、通行能力、区域压力和车型规律查询；
- Agent 流式问答；
- 浏览器语音识别和 Serena 语音合成；
- 生成应急调度方案；
- 确认无需派单；
- 一级审批上报、二级专业复核、三级省级决策；
- 流程历史查看和已调度资源释放。

## 2. 原数据库安全备份

公网切换前已完成一次原数据库可写业务表的一致性备份：

```text
/home/whtc/workspace/backups/road-agent-db/public-shared-db-before-enable-20260904
```

备份包含以下 8 张应急业务表的数据和结构：

```text
w_abnormal_event
w_emergency_dispatch_order
w_emergency_dispatch_workflow
w_emergency_professional_review
w_emergency_command_decision
w_emergency_dispatch_action_log
w_emergency_resource
w_emergency_resource_allocation
```

同时保存行数、字段、外键、镜像信息和 SHA-256 校验清单。备份文件权限为 `600`，目录权限为 `700`。

需要再次备份时执行：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack db-backup public-manual-backup-001
```

验证已有备份：

```bash
cd /home/whtc/workspace/backups/road-agent-db/public-shared-db-before-enable-20260904
sha256sum --check SHA256SUMS
```

仅在确实需要恢复时，先停止 Road Agent，再执行带备份编号确认的恢复命令：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack down
deploy/dgx/dgx-stack db-restore public-shared-db-before-enable-20260904 \
  --confirm=public-shared-db-before-enable-20260904
```

恢复会覆盖上述应急业务表，应先确认当前新增数据是否仍需保留。

## 3. 设置固定公网账号密码

登录 DGX 后进入项目目录：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack public-auth-set
```

按提示输入固定账号和密码：

```text
Public username:
Public password (at least 12 characters):
Confirm public password:
```

账号可使用 3～64 位英文字母、数字、点、下划线或连字符；密码至少 12 个字符。密码不会明文保存，只有 Nginx Basic Auth 哈希写入：

```text
/home/whtc/.config/road-agent/public.htpasswd
```

不要把明文密码写入项目 `.env`、Git、脚本或文档。公网关闭时可直接重新执行 `public-auth-set`；如果公网已经启用，请依次执行 `public-off`、`public-auth-set`、`public-on`，确保 Frontend 重新挂载新的密码哈希。

从 Windows 直接设置：

```powershell
ssh -t -i C:\Users\Lenovo\.ssh\id_ed25519_dgx_spark `
  whtc@100.119.145.78 `
  "cd /home/whtc/workspace/projects/road-agent-dgx && deploy/dgx/dgx-stack public-auth-set"
```

## 4. 启用公网访问

设置账号密码后在 DGX 执行：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack public-on
```

该命令会自动生成一个随机的原库受限账号 `roadagent_public_app`，按“交通只读、应急读写”重新核对并设置表级权限，然后启动本地 Qwen、ASR/TTS、公网专用 Backend、带登录保护的 Frontend，并通过 Tailscale Funnel 发布 HTTPS 地址。

公网后端的模型调用超时单独设为 180 秒，以适配原库较大的交通数据摘要请求；Frontend 代理超时为 300 秒。

受限数据库凭据保存在项目外，部署代码不会覆盖：

```text
/home/whtc/.config/road-agent/public-original-db.env
```

该文件权限为 `600`，密码由脚本随机生成，不需要手工设置。它使用原数据库地址和 schema，但不是独立数据库。

公网地址：

```text
https://spark-8a8d.taile1b178.ts.net/
```

公网只发布绑定在 `127.0.0.1:18081` 的 Frontend。数据库、Java Backend、Speech 和 Qwen 均不会直接暴露到公网。

查看状态：

```bash
deploy/dgx/dgx-stack public-status
```

正常状态应显示：

```text
Database mode: original database (traffic reads; emergency workflow reads/writes)
```

独立验证数据库授权：

```bash
deploy/dgx/dgx-stack public-db-access-verify
deploy/dgx/dgx-stack public-db-access-grants
```

验证过程会确认所有交通表可查询但不可修改，并在事务内实测应急事件表的新增、查询、修改和删除；临时验证记录最终回滚，不会保留。

## 5. 公网用户使用方法

1. 使用 Chrome、Edge 或 Safari 打开公网地址。
2. 输入管理员设置的固定公网账号和密码。
3. 在交通问答页面测试 Agent、交通查询、语音输入和语音播报。
4. 在应急调度页面测试生成方案、不派单、一级审批、专业复核、省级决策和资源释放。
5. 首次使用语音输入时允许网站访问麦克风。

测试者不需要下载代码、Java、Node.js、模型文件、Docker 或 Tailscale，只需要浏览器、网络和登录凭据。

公网测试产生的应急事件状态、工单、审批、决策和资源占用会真实写入原数据库。不要再执行原来的 `public-db-reset`；该命令已移除，避免误清理原库。需要回退时使用经过校验的数据库备份。

## 6. 停止和排障

临时停止全部 Road Agent 服务：

```bash
deploy/dgx/dgx-stack down
```

永久关闭当前公网入口但保留账号哈希和数据库备份：

```bash
deploy/dgx/dgx-stack public-off
```

常用排障命令：

```bash
deploy/dgx/dgx-stack public-status
deploy/dgx/dgx-stack logs public-backend
docker logs --tail 200 road-agent-dgx-public-frontend
deploy/dgx/dgx-stack public-db-access-verify
deploy/dgx/dgx-stack db-verify
```

## 7. 离线边界

- 原数据库是外部共享 MySQL，因此 DGX 需要能够连接该数据库，完整业务才能运行。
- 外部用户访问公网地址时，DGX 和用户双方都必须联网；DGX 断开 Wi-Fi 和宽带后，Tailscale Funnel 公网入口不可用。
- Qwen、ASR、TTS 和应用镜像位于 DGX 本地，但这不消除原数据库和公网访问的网络依赖。
