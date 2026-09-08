# roadagent-v2 发布来源与核验

发布时间：2026-09-09（Asia/Shanghai）。分支：`roadagent-v2`。

## 源码来源

| 内容 | DGX 来源 | 文件数 | 导出压缩包 SHA-256 |
| --- | --- | ---: | --- |
| Road Agent | `/home/whtc/workspace/projects/road-agent-dgx` | 550 | `5ec770fd3a03064bf96bead5065333849814c48540dc520ebed2dbbd5873ac8c` |
| 模型运维 | `/home/whtc/workspace/projects/model-serving` | 21 | `c43e0c625efe89a70c93a01ea9fca986fc516e701381d1a46165446759346e6a` |

两个来源共571个文件，在发布文档调整前逐文件 SHA-256 对照通过，无缺失或差异。模型运维源码置于本分支 `model-serving/`；实际 DGX 仍使用原独立目录。原本机项目工作树不做覆盖。

业务基准 `61e64d4d282c004ac681560329f938f69a7b59e6` 来自服务器既有合并记录；DGX 部署目录本身没有 `.git`，因此本次采用直接导出与逐文件校验，不伪称服务器存在新的 Git 提交。发布分支继承本地迁移历史，再按 DGX 快照更新源文件，并移除服务器已不再使用的5个旧区域类型。

## 本次发布允许的非业务调整

- 新增更新 README、发布日志及本记录。
- 主 README 更新当前模型说明，模型运维 README 修正仓库内文档链接。
- `.gitignore` 增加运行数据排除项；`.gitattributes` 固定 Linux 脚本换行；Git 索引保留可执行脚本权限。
- 不修改 Java、Vue/TypeScript、Speech、业务接口、SQL 或模型运维脚本内容。

## 验证

- DGX 导出前端：`npm ci --ignore-scripts --no-audit --no-fund`、`npm test`、`npm run build`；21个文件101项测试通过，TS/Vite构建成功。
- 模型运维测试在本次嵌套发布布局下7项通过、1项因原测试要求相邻独立 Road Agent 目录而跳过；原 DGX 独立目录布局此前8项均通过。没有为发布更改测试或模型脚本实现。
- 既有模型上线证据随 `docs/qwen38-evidence/` 收录，完整边界见 `QWEN38_DGX_ACCEPTANCE.md`。
- 发布前扫描运行环境文件、私钥/令牌模式和字面凭据；配置示例及运行期生成密码的脚本不属于真实凭据。
- 没有上传 `.env`、认证文件、权重、数据库数据/备份、运行发布目录、音频或构建产物。
- 本次是 GitHub 源码分支发布，不创建 GitHub Release 标签，不执行服务器构建、重启、迁移或回滚。
