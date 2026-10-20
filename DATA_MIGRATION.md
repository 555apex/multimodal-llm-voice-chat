# 业务与知识库副本：网络导出及恢复

在目标机执行，`DGX_HOST`为操作者有权限访问的源SSH地址。先核对源指纹、认证和备份维护窗口。以下操作不涉及U盘。

```bash
DGX_HOST='填写授权的DGX用户@地址'
scp resources/scripts/export_data_dgx.py "${DGX_HOST}:/tmp/export_data_dgx.py"
ssh -t "$DGX_HOST"
```

在DGX交互终端：暂停知识库写入和变更，等待解析结束；MySQL无并发DDL。密码现场输入，不进命令参数/截图。完成后另存备份实际路径、大小和SHA256，再退出SSH。

```bash
umask 077
read -rsp '备份密码: ' BACKUP_SECRET; printf '\n'
printf '%s' "$BACKUP_SECRET" > /home/whtc/site-backup-passphrase
unset BACKUP_SECRET
python3 /tmp/export_data_dgx.py \
  --out-root /home/whtc/workspace/backups/roadagent-site-export \
  --passphrase-file /home/whtc/site-backup-passphrase --knowledge-writes-paused
exit
```

回到目标机，使用实际输出，不把占位值当作已存在文件：

```bash
python3 resources/scripts/transfer.py receive --host "$DGX_HOST" \
  --remote-path '/填写DGX实际备份路径.tar.gpg' \
  --sha256 '填写发送方64位SHA256' \
  --destination /srv/roadagent-migration/incoming/site-data.tar.gpg
umask 077
gpg --output /opt/roadagent-migration/site-data.tar \
  --decrypt /srv/roadagent-migration/incoming/site-data.tar.gpg
python3 resources/scripts/unpack.py \
  --archive /opt/roadagent-migration/site-data.tar \
  --destination /opt/roadagent-migration/source-backup
export SITE_ENV=/opt/roadagent-migration/site.env
python3 resources/scripts/restore_config.py \
  --backup-dir /opt/roadagent-migration/source-backup --env-file "$SITE_ENV"
bash resources/scripts/site.sh db-up
# 等MySQL/PostgreSQL健康后执行；非空库拒绝覆盖
python3 resources/scripts/05_restore_databases.py \
  --backup-dir /opt/roadagent-migration/source-backup \
  --env-file "$SITE_ENV" --confirm-project roadagent-fz-pilot
python3 resources/scripts/unpack.py \
  --archive /opt/roadagent-migration/source-backup/knowledge-files.tar \
  --destination /srv/roadagent-migration/files
```

核对MySQL源/目标8.0.45及字符集排序规则、表/记录、知识库ID与模型绑定、原文件数量/权限及索引。密钥缺失或文件不对应则停止，不用随机AES密钥代替。恢复测试与备份恢复演练尚需现场完成。具体恢复原理及厂商信息由负责人/工程师补录，不在GitHub存明文凭据或业务备份。
